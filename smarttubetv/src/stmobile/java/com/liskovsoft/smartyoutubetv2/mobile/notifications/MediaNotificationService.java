package com.liskovsoft.smartyoutubetv2.mobile.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.mobile.ui.playback.MobilePlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Foreground service behind the phone's media notification (shade, lockscreen and, on
 * Android 13+, the media carousel). It also keeps the process alive for background audio.
 *
 * The notification lives exactly as long as the player's MediaSession: {@link #start} when the
 * session is created (and again on every video change), {@link #stop} when it is released.
 * A notification left behind with a dead session token renders as a plain card with no controls.
 *
 * Tapping opens the player; the prev / play-pause / next actions route back here and go through
 * the session's transport controls, the same path a headset button takes. Android 13+ draws its
 * own controls and seek bar from the session, older versions use these actions.
 */
public class MediaNotificationService extends Service {
    private static final String TAG = MediaNotificationService.class.getSimpleName();
    private static final int NOTIFICATION_ID = MediaNotificationService.class.hashCode();
    private static final String CHANNEL_ID = "com.codesculptor.smartertube_media";
    private static final String EXTRA_SESSION_TOKEN = "media_session_token";
    private static final String ACTION_UPDATE = "smartertube.media.UPDATE";
    private static final String ACTION_PREVIOUS = "smartertube.media.PREVIOUS";
    private static final String ACTION_PLAY_PAUSE = "smartertube.media.PLAY_PAUSE";
    private static final String ACTION_NEXT = "smartertube.media.NEXT";
    private MediaControllerCompat mController;
    private boolean mLastPlaying;

    private final MediaControllerCompat.Callback mCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            // Position ticks also land here; only the play/pause icon depends on the state.
            if (isPlaying() != mLastPlaying) {
                postNotification();
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            postNotification();
        }

        @Override
        public void onSessionDestroyed() {
            stopSelfCompletely();
        }
    };

    /** Post (or refresh) the notification for the player's MediaSession. */
    public static void start(Context context, MediaSessionCompat.Token token) {
        Intent intent = new Intent(context, MediaNotificationService.class)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_SESSION_TOKEN, token);
        try {
            ContextCompat.startForegroundService(context, intent);
        } catch (Exception e) {
            // ForegroundServiceStartNotAllowedException: app already in the background (Android 12+)
            Log.e(TAG, "Can't start media notification: %s", e.getMessage());
        }
    }

    /** The MediaSession is gone: remove the notification and stop the service. */
    public static void stop(Context context) {
        context.stopService(new Intent(context, MediaNotificationService.class));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_UPDATE.equals(action)) {
            MediaSessionCompat.Token token = intent.getParcelableExtra(EXTRA_SESSION_TOKEN);
            if (token == null) {
                stopSelfCompletely();
                return START_NOT_STICKY;
            }
            bindController(token);
            try {
                startForeground(NOTIFICATION_ID, createNotification());
            } catch (Exception e) {
                // ForegroundServiceStartNotAllowedException (Android 12+)
                Log.e(TAG, "startForeground failed: %s", e.getMessage());
            }
        } else if (mController == null) {
            // Restarted without a session (process recreated) or a stale action: nothing to show.
            stopSelfCompletely();
        } else if (ACTION_PREVIOUS.equals(action)) {
            mController.getTransportControls().skipToPrevious();
        } else if (ACTION_NEXT.equals(action)) {
            mController.getTransportControls().skipToNext();
        } else if (ACTION_PLAY_PAUSE.equals(action)) {
            if (isPlaying()) {
                mController.getTransportControls().pause();
            } else {
                mController.getTransportControls().play();
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        unbindController();
        // Releasing the session fires a last state change that re-posts the card with notify();
        // that copy outlives the foreground service unless it's cancelled here.
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
        super.onDestroy();
    }

    private void bindController(MediaSessionCompat.Token token) {
        if (mController != null && token.equals(mController.getSessionToken())) {
            return;
        }

        unbindController();
        mController = new MediaControllerCompat(this, token);
        mController.registerCallback(mCallback);
    }

    private void unbindController() {
        if (mController != null) {
            mController.unregisterCallback(mCallback);
            mController = null;
        }
    }

    private void stopSelfCompletely() {
        unbindController();
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    private boolean isPlaying() {
        PlaybackStateCompat state = mController != null ? mController.getPlaybackState() : null;
        return state != null && (state.getState() == PlaybackStateCompat.STATE_PLAYING
                || state.getState() == PlaybackStateCompat.STATE_BUFFERING);
    }

    private void postNotification() {
        if (mController == null) {
            return;
        }

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, createNotification());
    }

    private Notification createNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }

        // The presenter's video is always current (its title fills in once details load); session
        // metadata is only rebuilt on player events. Then fall back to the app name.
        CharSequence title = null;
        CharSequence author = null;
        Video video = PlaybackPresenter.instance(getApplicationContext()).getVideo();
        if (video != null) {
            title = video.getTitleFull();
            author = video.getAuthor();
        }
        MediaMetadataCompat metadata = mController.getMetadata();
        if (title == null && metadata != null) {
            title = metadata.getText(MediaMetadataCompat.METADATA_KEY_TITLE);
            author = metadata.getText(MediaMetadataCompat.METADATA_KEY_ARTIST);
        }
        if (title == null) {
            title = getString(R.string.app_name);
        }

        mLastPlaying = isPlaying();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(getApplicationInfo().icon)
                .setContentTitle(title)
                .setContentText(author)
                .setContentIntent(createContentIntent())
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(android.R.drawable.ic_media_previous, getString(R.string.media_notification_previous),
                        createActionIntent(ACTION_PREVIOUS))
                .addAction(mLastPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        getString(mLastPlaying ? R.string.media_notification_pause : R.string.media_notification_play),
                        createActionIntent(ACTION_PLAY_PAUSE))
                .addAction(android.R.drawable.ic_media_next, getString(R.string.media_notification_next),
                        createActionIntent(ACTION_NEXT))
                .setStyle(new MediaStyle()
                        .setMediaSession(mController.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));

        return builder.build();
    }

    /** Brings the running player to the front (singleTop, so the existing instance is reused). */
    private PendingIntent createContentIntent() {
        PendingIntent sessionActivity = mController.getSessionActivity();
        if (sessionActivity != null) {
            return sessionActivity;
        }

        return createActivityIntent(this);
    }

    /** The player activity as a tap target; also set as the MediaSession's session activity. */
    public static PendingIntent createActivityIntent(Context context) {
        Intent intent = new Intent(context, MobilePlaybackActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 0, intent, pendingIntentFlags());
    }

    private PendingIntent createActionIntent(String action) {
        Intent intent = new Intent(this, MediaNotificationService.class).setAction(action);
        return PendingIntent.getService(this, action.hashCode(), intent, pendingIntentFlags());
    }

    private static int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
}
