package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.smartyoutubetv2.mobile.notifications.NotificationPollWorker;
import com.liskovsoft.smartyoutubetv2.mobile.ui.base.MobileActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.prefs.MobileNotificationPrefs;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Host for the native phone Home screen. Replaces the TV BrowseActivity for the
 * stmobile flavor (wired in {@link com.liskovsoft.smartyoutubetv2.mobile.ui.main.MobileApplication}).
 */
public class MobileBrowseActivity extends MobileActivity {
    private static final int REQ_POST_NOTIFICATIONS = 1001;
    /** Re-ask for a revoked notification permission at most once per process, not on every screen. */
    private static boolean sPermissionRechecked;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_browse_activity);

        if (getSupportFragmentManager().findFragmentById(R.id.mobile_browse_root) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.mobile_browse_root, new MobileBrowseFragment())
                    .commit();
        }

        if (savedInstanceState == null) {
            checkUploadNotifications();
        }
    }

    /**
     * Upload notifications are opt-in and off by default, so a fresh install (including the
     * package-id rename, which reinstalled everyone) silently never polls. Ask once, when signed in
     * (the poll needs an account). If they're on but the permission was revoked, ask for it again.
     */
    private void checkUploadNotifications() {
        if (MobileNotificationPrefs.isEnabled(this)) {
            if (!sPermissionRechecked) {
                sPermissionRechecked = true;
                requestPostNotificationsPermission();
            }
            return;
        }

        if (MobileNotificationPrefs.wasPrompted(this) || !isSignedIn()) {
            return;
        }

        MobileNotificationPrefs.setPrompted(this);
        new AlertDialog.Builder(this)
                .setTitle(R.string.mobile_notifications_prompt_title)
                .setMessage(R.string.mobile_notifications_prompt_message)
                .setPositiveButton(R.string.mobile_notifications_prompt_yes, (dialog, which) -> {
                    MobileNotificationPrefs.setEnabled(this, true);
                    NotificationPollWorker.schedule(this);
                    requestPostNotificationsPermission();
                })
                .setNegativeButton(R.string.mobile_notifications_prompt_no, null)
                .show();
    }

    private static boolean isSignedIn() {
        ServiceManager service = YouTubeServiceManager.instance();
        return service.getSignInService() != null && service.getSignInService().isSigned();
    }

    /**
     * Ask for the Android 13+ POST_NOTIFICATIONS runtime permission. No-op below API 33 (granted at
     * install) or when already granted. Called when the user turns on Upload notifications from
     * Settings — posting silently no-ops without it, so this is best-effort and never blocks the UI.
     */
    public void requestPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ActivityCompat.requestPermissions(
                this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
    }
}
