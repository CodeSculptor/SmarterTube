package com.liskovsoft.smartyoutubetv2.mobile.offline;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.tv.R;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

/**
 * Saves one video as a single muxed MP4: chunked ranged GETs appended to {@code video.mp4.part},
 * resumable by file length, re-resolving the signed URL when it expires. The work request carries
 * the network constraint (unmetered when "Wi-Fi only"), so WorkManager pauses it on a lost
 * connection and re-runs it on return; the .part file survives.
 */
public class OfflineDownloadWorker extends Worker {
    private static final String TAG = OfflineDownloadWorker.class.getSimpleName();
    static final String KEY_VIDEO_ID = "video_id";
    private static final String CHANNEL_ID = "offline_downloads";
    private static final int NOTIFICATION_ID_BASE = 9100;

    public OfflineDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String videoId = getInputData().getString(KEY_VIDEO_ID);
        Context context = getApplicationContext();
        OfflineStore store = OfflinePrefs.store(context);
        SavedVideo meta = videoId != null ? store.get(videoId) : null;
        if (meta == null) return Result.failure();
        if (meta.isDone()) return Result.success();
        if (SavedVideo.PAUSED.equals(meta.state)) return Result.success(); // user paused before we ran

        OkHttpClient client = OfflineResolver.newClient();
        OfflineResolver resolver = new OfflineResolver(client);
        try {
            setState(store, meta, SavedVideo.DOWNLOADING, null);
            OfflinePlayerResponse r = resolver.resolve(videoId);

            OfflinePlayerResponse.Saveability s = r.saveability();
            if (s != OfflinePlayerResponse.Saveability.SAVEABLE) {
                return fail(store, meta, reasonFor(context, s, r));
            }
            applyResolved(meta, r);

            File part = store.partFile(videoId);
            long done = part.exists() ? part.length() : 0;
            // A different stream than the one the partial file came from: start over.
            if (done > 0 && meta.totalBytes > 0 && r.contentLength > 0 && r.contentLength != meta.totalBytes) {
                part.delete();
                done = 0;
            }
            store.put(meta);
            fetchThumbnail(client, store, videoId, r.thumbnailUrl);

            if (!OfflineDownloadPlan.hasEnoughSpace(meta.totalBytes, done, OfflinePrefs.freeBytes(context))) {
                return fail(store, meta, context.getString(R.string.mobile_offline_no_space));
            }

            String url = r.url;
            boolean reResolved = false;
            long total = meta.totalBytes;
            long lastNotify = 0;
            setForegroundAsync(foreground(context, meta, OfflineDownloadPlan.percent(done, total)));

            while (total <= 0 || done < total) {
                if (isStopped()) return stopped(store, meta);
                long[] range = OfflineDownloadPlan.nextRange(done, total);
                Request req = new Request.Builder().url(url)
                        .header("User-Agent", OfflineResolver.userAgent())
                        .header("Range", "bytes=" + range[0] + "-" + range[1]).build();
                Response resp = client.newCall(req).execute();
                try {
                    int code = resp.code();
                    if (OfflineDownloadPlan.isExpiredUrl(code)) {
                        if (reResolved) return retryOrFail(store, meta, "HTTP " + code);
                        OfflinePlayerResponse again = resolver.resolve(videoId);
                        if (again.saveability() != OfflinePlayerResponse.Saveability.SAVEABLE
                                || again.itag != meta.itag) {
                            return fail(store, meta, reasonFor(context, again.saveability(), again));
                        }
                        url = again.url;
                        reResolved = true;
                        continue;
                    }
                    if (code == 416 && total > 0) break; // already complete
                    if (code != 206 && code != 200) return retryOrFail(store, meta, "HTTP " + code);
                    if (code == 200 && done > 0) return retryOrFail(store, meta, "range ignored");
                    long headerTotal = OfflineDownloadPlan.parseContentRangeTotal(resp.header("Content-Range"));
                    if (headerTotal > 0 && total <= 0) {
                        total = headerTotal;
                        meta.totalBytes = total;
                        store.put(meta);
                        if (!OfflineDownloadPlan.hasEnoughSpace(total, done, OfflinePrefs.freeBytes(context))) {
                            return fail(store, meta, context.getString(R.string.mobile_offline_no_space));
                        }
                    }
                    long written = append(resp, part);
                    if (written <= 0) return retryOrFail(store, meta, "empty chunk");
                    done += written;
                } finally {
                    resp.close();
                }

                long now = System.currentTimeMillis();
                if (now - lastNotify > 1000) {
                    lastNotify = now;
                    setForegroundAsync(foreground(context, meta, OfflineDownloadPlan.percent(done, total)));
                }
            }

            File finished = store.videoFile(videoId);
            finished.delete();
            if (!part.renameTo(finished)) return retryOrFail(store, meta, "rename failed");
            setState(store, meta, SavedVideo.DONE, null);
            notifyDone(context, meta);
            return Result.success();
        } catch (IOException e) {
            if (isStopped()) return stopped(store, meta);
            Log.e(TAG, "download %s failed: %s", videoId, e.getMessage());
            return retryOrFail(store, meta, e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (RuntimeException e) {
            Log.e(TAG, "download %s crashed: %s", videoId, e.getMessage());
            return fail(store, meta, "Unexpected error");
        }
    }

    // ----- helpers -----

    private static void applyResolved(SavedVideo meta, OfflinePlayerResponse r) {
        if (r.title != null && !r.title.isEmpty()) meta.title = r.title;
        if (r.author != null && !r.author.isEmpty()) meta.author = r.author;
        if (r.durationSec > 0) meta.durationSec = r.durationSec;
        meta.itag = r.itag;
        meta.qualityLabel = r.qualityLabel;
        if (r.contentLength > 0) meta.totalBytes = r.contentLength;
    }

    private static String reasonFor(Context c, OfflinePlayerResponse.Saveability s, OfflinePlayerResponse r) {
        switch (s) {
            case LIVE: return c.getString(R.string.mobile_offline_cant_live);
            case UNPLAYABLE: return c.getString(R.string.mobile_offline_cant_generic);
            default: return c.getString(R.string.mobile_offline_cant_generic);
        }
    }

    private static long append(Response resp, File part) throws IOException {
        if (resp.body() == null) return 0;
        InputStream in = resp.body().byteStream();
        RandomAccessFile raf = new RandomAccessFile(part, "rw");
        try {
            raf.seek(raf.length());
            byte[] buf = new byte[32 * 1024];
            long n = 0;
            int r;
            while ((r = in.read(buf)) > 0) {
                raf.write(buf, 0, r);
                n += r;
            }
            return n;
        } finally {
            raf.close();
        }
    }

    private static void fetchThumbnail(OkHttpClient client, OfflineStore store, String videoId, String url) {
        if (url == null || store.thumbFile(videoId).exists()) return;
        try {
            Response resp = client.newCall(new Request.Builder().url(url).build()).execute();
            try {
                if (resp.isSuccessful() && resp.body() != null) {
                    FileOutputStream out = new FileOutputStream(store.thumbFile(videoId));
                    try {
                        out.write(resp.body().bytes());
                    } finally {
                        out.close();
                    }
                }
            } finally {
                resp.close();
            }
        } catch (IOException ignored) {
            // a missing thumbnail is cosmetic
        }
    }

    private void setState(OfflineStore store, SavedVideo meta, String state, String reason) {
        meta.state = state;
        meta.reason = reason;
        try {
            store.put(meta);
        } catch (IOException e) {
            Log.e(TAG, "meta write failed: %s", e.getMessage());
        }
    }

    private Result fail(OfflineStore store, SavedVideo meta, String reason) {
        setState(store, meta, SavedVideo.FAILED, reason);
        return Result.failure();
    }

    private Result retryOrFail(OfflineStore store, SavedVideo meta, String reason) {
        if (OfflineDownloadPlan.shouldGiveUp(getRunAttemptCount() + 1)) {
            return fail(store, meta, reason);
        }
        setState(store, meta, SavedVideo.QUEUED, null);
        return Result.retry();
    }

    /** Stopped by constraints (network lost) or cancel: leave the part file, show as waiting. */
    private Result stopped(OfflineStore store, SavedVideo meta) {
        SavedVideo current = store.get(meta.videoId);
        if (current != null && SavedVideo.DOWNLOADING.equals(current.state)) {
            setState(store, current, SavedVideo.QUEUED, null);
        }
        return Result.retry();
    }

    // ----- notifications -----

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                        context.getString(R.string.mobile_offline_channel), NotificationManager.IMPORTANCE_LOW));
            }
        }
    }

    private ForegroundInfo foreground(Context context, SavedVideo meta, int percent) {
        ensureChannel(context);
        Notification n = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_small)
                .setContentTitle(context.getString(R.string.mobile_offline_saving))
                .setContentText(meta.title != null ? meta.title : meta.videoId)
                .setProgress(100, percent, percent == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(0, context.getString(R.string.mobile_offline_cancel),
                        androidx.work.WorkManager.getInstance(context).createCancelPendingIntent(getId()))
                .build();
        return new ForegroundInfo(NOTIFICATION_ID_BASE + Math.abs(meta.videoId.hashCode() % 1000), n);
    }

    private static void notifyDone(Context context, SavedVideo meta) {
        ensureChannel(context);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Notification n = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_small)
                .setContentTitle(context.getString(R.string.mobile_offline_saved))
                .setContentText(meta.title != null ? meta.title : meta.videoId)
                .setAutoCancel(true)
                .build();
        nm.notify(NOTIFICATION_ID_BASE + 1000 + Math.abs(meta.videoId.hashCode() % 1000), n);
    }
}
