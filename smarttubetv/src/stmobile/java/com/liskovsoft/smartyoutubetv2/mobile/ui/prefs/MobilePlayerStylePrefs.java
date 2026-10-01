package com.liskovsoft.smartyoutubetv2.mobile.ui.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Phone "Player style" setting (#46): which controls the regular (non-Shorts) player shows and
 * what a tap on the video does.
 * <ul>
 *   <li>{@link Style#CLASSIC}: upstream SmartTube's Leanback control rows; a tap shows/hides them.</li>
 *   <li>{@link Style#MODERN}: phone-style controls (ModernPlayerChrome): top bar, big centred
 *   previous / play-pause / next, time + fullscreen, seek bar, landscape action row, settings sheet.
 *   A tap shows/hides them.</li>
 *   <li>{@link Style#TAP_TO_PAUSE}: the Modern controls, but a tap on the video plays/pauses; the
 *   controls show while paused and hide on play.</li>
 * </ul>
 * Shorts keep their own tap-to-pause player regardless of this setting.
 */
public final class MobilePlayerStylePrefs {
    private static final String PREFS_NAME = "mobile_player_style_prefs";
    private static final String KEY_STYLE = "style";
    private static final String KEY_SWIPE_GESTURES = "swipe_gestures";
    private static final String KEY_SWIPE_BRIGHTNESS = "swipe_brightness";

    public enum Style {
        CLASSIC, MODERN, TAP_TO_PAUSE;

        /** Phone-style controls replace the Leanback control rows. */
        public boolean isModern() {
            return this != CLASSIC;
        }

        /** A tap on the video plays/pauses instead of showing/hiding the controls. */
        public boolean tapTogglesPlayback() {
            return this == TAP_TO_PAUSE;
        }
    }

    private MobilePlayerStylePrefs() {}

    /** Whether a style has been chosen yet (by the user, or by the one-time install default). */
    public static boolean isSet(Context context) {
        return prefs(context).contains(KEY_STYLE);
    }

    public static Style getStyle(Context context) {
        String raw = prefs(context).getString(KEY_STYLE, Style.MODERN.name());
        try {
            return Style.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return Style.MODERN;
        }
    }

    public static void setStyle(Context context, Style style) {
        prefs(context).edit().putString(KEY_STYLE, style.name()).apply();
    }

    /**
     * Swipe gestures in the landscape player (#48): volume on the right, brightness on the left.
     * Until the user chooses, on for the Modern styles and off for Classic (pure SmartTube).
     */
    public static boolean isSwipeGesturesEnabled(Context context) {
        SharedPreferences prefs = prefs(context);
        return prefs.contains(KEY_SWIPE_GESTURES)
                ? prefs.getBoolean(KEY_SWIPE_GESTURES, false)
                : getStyle(context).isModern();
    }

    public static void setSwipeGesturesEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SWIPE_GESTURES, enabled).apply();
    }

    /** Whether the left-half brightness swipe is active (the right-half volume swipe is separate). */
    public static boolean isBrightnessSwipeEnabled(Context context) {
        return prefs(context).getBoolean(KEY_SWIPE_BRIGHTNESS, true);
    }

    public static void setBrightnessSwipeEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SWIPE_BRIGHTNESS, enabled).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
