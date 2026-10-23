package com.liskovsoft.smartyoutubetv2.mobile.ui.base;

import android.app.Activity;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;

/**
 * Keeps upstream's Fullscreen mode (bars hidden) in force on phone screens (#47).
 *
 * Upstream hides the status and navigation bars once, from MotherActivity.onResume. On a Redmi 9
 * (MIUI, Android 11) the bars came back over the landscape player and stayed, and the first tap
 * on a newly opened Settings screen did nothing (it only brought the bars back). Nothing hid
 * them again: rotation doesn't recreate the player (configChanges), and closing the notification
 * shade or a dialog returns focus without a resume.
 *
 * So the bars are hidden again on resume, whenever the window gets focus back, and after the
 * player rotates. The swipe-to-peek behaviour is set before hiding (upstream sets it after), so a
 * tap on the screen is never read as "show the bars".
 *
 * Upstream also asks for the cutout area by editing the window attributes in place, which only
 * reaches the window manager when something else updates the window; it is pushed explicitly
 * here so a hidden status bar doesn't leave an empty strip under the notch.
 */
public final class ImmersiveBars {
    private ImmersiveBars() {
    }

    public static void apply(Activity activity) {
        if (activity == null || activity.isFinishing()
                || !GeneralData.instance(activity).isFullscreenModeEnabled()) {
            return;
        }

        // Upstream's own flags (and the legacy sticky-immersive path below API 30).
        Helpers.makeActivityFullscreen2(activity);

        if (Build.VERSION.SDK_INT < 30) {
            return;
        }

        Window window = activity.getWindow();
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());

        // Same condition as upstream's (API 30+ with a cutout).
        if (activity.getDisplay() != null && activity.getDisplay().getCutout() != null) {
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attrs);
        }
    }
}
