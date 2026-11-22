package com.liskovsoft.smartyoutubetv2.mobile.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Posts status-bar / lock-screen notifications for new subscription uploads (Part 2 — push).
 *
 * New uploads are bundled under an Android notification group: one child per video (tap opens
 * that video) plus a group summary. stmobile-only; reuses the same {@link NotificationChannel} /
 * {@link NotificationCompat} primitives as {@code common}'s notification code, but lives in the
 * flavor so shared code stays untouched.
 *
 * Tapping a child routes through {@link SplashActivity} with a {@code watch?v=} VIEW intent — the
 * existing external-intent path ({@code SplashPresenter} → {@code IntentExtractor.extractVideoId})
 * opens the video in the phone player, so no new routing code is needed.
 */
public final class UploadNotifier {
    private static final String CHANNEL_ID = "uploads";
    private static final String GROUP_KEY = "com.liskovsoft.smartyoutubetv2.mobile.UPLOADS";
    /** Fixed id for the group-summary notification (kept clear of any per-video id). */
    private static final int SUMMARY_ID = 1;

    private UploadNotifier() {}

    /** Post a grouped notification for {@code newItems}. No-op on empty input. */
    public static void notifyNewUploads(Context context, List<MediaItem> newItems) {
        if (newItems == null || newItems.isEmpty()) {
            return;
        }

        ensureChannel(context);
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);

        List<CharSequence> lines = new ArrayList<>();
        int posted = 0;

        for (MediaItem item : newItems) {
            String videoId = item.getVideoId();
            if (TextUtils.isEmpty(videoId)) {
                continue;
            }

            String title = !TextUtils.isEmpty(item.getTitle())
                    ? item.getTitle() : context.getString(R.string.mobile_uploads_new_video);
            CharSequence channel = item.getSecondTitle() != null ? item.getSecondTitle() : item.getAuthor();

            int id = notificationId(videoId);
            Notification child = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_small)
                    .setContentTitle(title)
                    .setContentText(channel)
                    .setAutoCancel(true)
                    .setGroup(GROUP_KEY)
                    .setContentIntent(videoIntent(context, videoId, id))
                    .build();

            safeNotify(manager, id, child);
            lines.add(TextUtils.isEmpty(channel) ? title : title + " — " + channel);
            posted++;
        }

        if (posted == 0) {
            return;
        }

        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        for (CharSequence line : lines) {
            style.addLine(line);
        }

        Notification summary = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_small)
                .setContentTitle(context.getString(R.string.mobile_uploads_summary_title))
                .setContentText(context.getResources().getQuantityString(R.plurals.mobile_uploads_summary_text, posted, posted))
                .setStyle(style)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(appIntent(context))
                .build();

        safeNotify(manager, SUMMARY_ID, summary);
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.mobile_notifications_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /** Tap → open this specific video through the existing Splash external-intent path. */
    private static PendingIntent videoIntent(Context context, String videoId, int requestCode) {
        Intent intent = new Intent(context, SplashActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("https://www.youtube.com/watch?v=" + videoId))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, requestCode, intent, pendingFlags());
    }

    /** Tap on the summary → just open the app. */
    private static PendingIntent appIntent(Context context) {
        Intent intent = new Intent(context, SplashActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, SUMMARY_ID, intent, pendingFlags());
    }

    private static int pendingFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    /** Stable, non-negative per-video id that never collides with {@link #SUMMARY_ID}. */
    private static int notificationId(String videoId) {
        int id = videoId.hashCode() & 0x7fffffff;
        return id == SUMMARY_ID ? id + 1 : id;
    }

    private static void safeNotify(NotificationManagerCompat manager, int id, Notification notification) {
        try {
            // On Android 13+ this silently does nothing without POST_NOTIFICATIONS — guarded, not crashing.
            manager.notify(id, notification);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }
}
