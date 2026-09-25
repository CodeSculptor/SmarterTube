package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.smartyoutubetv2.mobile.notifications.NotificationPollWorker;
import com.liskovsoft.smartyoutubetv2.mobile.stats.StatsDialogs;
import com.liskovsoft.smartyoutubetv2.mobile.ui.base.MobileActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.prefs.MobileNotificationPrefs;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.liskovsoft.smartyoutubetv2.mobile.update.LaunchUpdateNotices;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Host for the native phone Home screen. Replaces the TV BrowseActivity for the
 * stmobile flavor (wired in {@link com.liskovsoft.smartyoutubetv2.mobile.ui.main.MobileApplication}).
 */
public class MobileBrowseActivity extends MobileActivity {
    private static final int REQ_POST_NOTIFICATIONS = 1001;
    /** Re-ask for a revoked notification permission at most once per process, not on every screen. */
    private static boolean sPermissionRechecked;
    /** Next launch dialog, held while the system notification-permission dialog is up. */
    @Nullable
    private Runnable mAfterPermission;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_browse_activity);

        if (getSupportFragmentManager().findFragmentById(R.id.mobile_browse_root) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.mobile_browse_root, new MobileBrowseFragment())
                    .commit();
        }

        // One-time launch dialogs, one after another - never stacked.
        LaunchDialogQueue dialogs = new LaunchDialogQueue(this);
        if (savedInstanceState == null) { // fresh start, not rotation/recreate
            dialogs.add(this::checkUploadNotifications);
            // Opt-in anonymous stats: one-time consent prompt, or offer to send last run's crash.
            dialogs.add(done -> StatsDialogs.maybeShowOnHome(this, done));
        }
        // update-available notice + one-time "What's new"
        dialogs.add(done -> LaunchUpdateNotices.onHomeCreated(this, done));
        dialogs.start();
    }

    /**
     * Upload notifications are opt-in and off by default, so a fresh install (including the
     * package-id rename, which reinstalled everyone) silently never polls. Ask once, when signed in
     * (the poll needs an account). If they're on but the permission was revoked, ask for it again.
     */
    private void checkUploadNotifications(Runnable done) {
        if (MobileNotificationPrefs.isEnabled(this)) {
            if (!sPermissionRechecked) {
                sPermissionRechecked = true;
                requestPostNotificationsPermission(done);
            } else {
                done.run();
            }
            return;
        }

        if (MobileNotificationPrefs.wasPrompted(this) || !isSignedIn()) {
            done.run();
            return;
        }

        MobileNotificationPrefs.setPrompted(this);
        new AlertDialog.Builder(this)
                .setTitle(R.string.mobile_notifications_prompt_title)
                .setMessage(R.string.mobile_notifications_prompt_message)
                .setPositiveButton(R.string.mobile_notifications_prompt_yes, (dialog, which) -> {
                    MobileNotificationPrefs.setEnabled(this, true);
                    NotificationPollWorker.schedule(this);
                })
                .setNegativeButton(R.string.mobile_notifications_prompt_no, null)
                .setOnDismissListener(d -> {
                    if (MobileNotificationPrefs.isEnabled(this)) {
                        requestPostNotificationsPermission(done);
                    } else {
                        done.run();
                    }
                })
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
        requestPostNotificationsPermission(null);
    }

    /** As above; {@code then} runs once the system permission dialog is answered (or right away if none is shown). */
    private void requestPostNotificationsPermission(@Nullable Runnable then) {
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            if (then != null) {
                then.run();
            }
            return;
        }
        mAfterPermission = then;
        ActivityCompat.requestPermissions(
                this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_POST_NOTIFICATIONS && mAfterPermission != null) {
            Runnable then = mAfterPermission;
            mAfterPermission = null;
            then.run();
        }
    }
}
