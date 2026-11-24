package com.liskovsoft.smartyoutubetv2.mobile.offline;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.liskovsoft.smartyoutubetv2.tv.R;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Entry points: start / pause / resume / cancel a save, and the "Save offline" user action. */
public final class OfflineDownloads {
    private OfflineDownloads() {}

    private static String workName(String videoId) {
        return "offline_" + videoId;
    }

    /** Enqueue (or re-enqueue) the download. The constraint follows the Wi-Fi-only setting. */
    public static void enqueue(Context context, String videoId) {
        Context app = context.getApplicationContext();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(OfflinePrefs.isWifiOnly(app) ? NetworkType.UNMETERED : NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(OfflineDownloadWorker.class)
                .setConstraints(constraints)
                .setInputData(new Data.Builder().putString(OfflineDownloadWorker.KEY_VIDEO_ID, videoId).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(app).enqueueUniqueWork(workName(videoId), ExistingWorkPolicy.KEEP, request);
    }

    /** Stop the transfer but keep the partial file; the library shows it as Paused. */
    public static void pause(Context context, String videoId) {
        OfflineStore store = OfflinePrefs.store(context);
        SavedVideo meta = store.get(videoId);
        if (meta != null && !meta.isDone()) {
            meta.state = SavedVideo.PAUSED;
            try {
                store.put(meta);
            } catch (IOException ignored) {
                // worker will still stop
            }
        }
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(workName(videoId));
    }

    public static void resume(Context context, String videoId) {
        OfflineStore store = OfflinePrefs.store(context);
        SavedVideo meta = store.get(videoId);
        if (meta == null || meta.isDone()) return;
        meta.state = SavedVideo.QUEUED;
        meta.reason = null;
        try {
            store.put(meta);
        } catch (IOException ignored) {
            return;
        }
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(workName(videoId));
        enqueue(context, videoId);
    }

    /** Cancel any running transfer and delete everything saved for the video. */
    public static void delete(Context context, String videoId) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(workName(videoId));
        OfflinePrefs.store(context).delete(videoId);
    }

    public static void deleteAll(Context context) {
        OfflineStore store = OfflinePrefs.store(context);
        for (SavedVideo v : store.list()) {
            WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(workName(v.videoId));
        }
        store.deleteAll();
    }

    /**
     * The "Save offline" action. Shows the one-time notice first. Saveability itself is decided by
     * the worker from a fresh lookup; a refusal shows in the library with its reason.
     */
    public static void saveWithNotice(final Activity activity, final String videoId, final String title,
                                      final String author) {
        if (videoId == null || !OfflineSupport.isSupported()) {
            return;
        }
        if (!OfflinePrefs.isNoticeShown(activity)) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.mobile_offline_notice_title)
                    .setMessage(R.string.mobile_offline_notice_message)
                    .setPositiveButton(R.string.mobile_offline_notice_save, (d, w) -> {
                        OfflinePrefs.setNoticeShown(activity);
                        start(activity, videoId, title, author);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else {
            start(activity, videoId, title, author);
        }
    }

    private static void start(Context context, String videoId, String title, String author) {
        OfflineStore store = OfflinePrefs.store(context);
        SavedVideo existing = store.get(videoId);
        if (existing != null && existing.isDone()) {
            Toast.makeText(context, R.string.mobile_offline_already, Toast.LENGTH_SHORT).show();
            return;
        }
        SavedVideo v = existing != null ? existing : new SavedVideo();
        v.videoId = videoId;
        if (title != null) v.title = title;
        if (author != null) v.author = author;
        if (v.createdAt == 0) v.createdAt = System.currentTimeMillis();
        v.state = SavedVideo.QUEUED;
        v.reason = null;
        try {
            store.put(v);
        } catch (IOException e) {
            Toast.makeText(context, R.string.mobile_offline_cant_generic, Toast.LENGTH_LONG).show();
            return;
        }
        enqueue(context, videoId);
        Toast.makeText(context, OfflinePrefs.isWifiOnly(context)
                ? R.string.mobile_offline_started_wifi : R.string.mobile_offline_started, Toast.LENGTH_LONG).show();
    }
}
