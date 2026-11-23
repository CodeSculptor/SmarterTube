package com.liskovsoft.smartyoutubetv2.mobile.ui.miniplayer;

import android.app.Activity;
import android.os.Build;

/**
 * A short cross-fade for minimise / expand (#96). The default task switch slides sideways, which
 * reads as the wrong direction when the player "goes down" to a bar. Best effort: the platform
 * may ignore a custom animation for a pure task move, in which case the default plays.
 * {@code overridePendingTransition} is deprecated from API 34, where
 * {@code overrideActivityTransition} replaces it.
 */
public final class MiniPlayerTransitions {
    private MiniPlayerTransitions() {}

    /** Call right after starting / moving to another task from {@code activity}. */
    @SuppressWarnings("deprecation")
    public static void fade(Activity activity, boolean opening) {
        int enter = android.R.anim.fade_in;
        int exit = android.R.anim.fade_out;
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                activity.overrideActivityTransition(
                        opening ? Activity.OVERRIDE_TRANSITION_OPEN : Activity.OVERRIDE_TRANSITION_CLOSE,
                        enter, exit);
            } else {
                activity.overridePendingTransition(enter, exit);
            }
        } catch (RuntimeException ignored) {
            // Cosmetic only.
        }
    }
}
