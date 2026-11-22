package com.liskovsoft.smartyoutubetv2.mobile.ui.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Persistence for the in-app Theme setting (System / Light / Dark) and a single
 * entry point for applying the current choice to {@link AppCompatDelegate}.
 *
 * Default is {@link Mode#SYSTEM} (MODE_NIGHT_FOLLOW_SYSTEM). Apply on every
 * process start from {@code MobileApplication.onCreate} so the first activity
 * inflates with the correct mode (no recreate-flicker).
 */
public final class MobileThemePrefs {
    private static final String PREFS_NAME = "mobile_theme_prefs";
    private static final String KEY_MODE = "mode";

    public enum Mode {
        SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
        DARK(AppCompatDelegate.MODE_NIGHT_YES);

        public final int delegateMode;

        Mode(int delegateMode) {
            this.delegateMode = delegateMode;
        }
    }

    private static final String KEY_PURE_BLACK = "pure_black";

    private MobileThemePrefs() {}

    /** True when dark mode should use true-black (AMOLED) surfaces. No effect in light mode. */
    public static boolean isPureBlack(Context context) {
        return prefs(context).getBoolean(KEY_PURE_BLACK, false);
    }

    public static void setPureBlack(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PURE_BLACK, enabled).apply();
    }

    public static Mode getMode(Context context) {
        String raw = prefs(context).getString(KEY_MODE, Mode.SYSTEM.name());
        try {
            return Mode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return Mode.SYSTEM;
        }
    }

    public static void setMode(Context context, Mode mode) {
        prefs(context).edit().putString(KEY_MODE, mode.name()).apply();
        apply(mode);
    }

    /** Apply the persisted mode now. Safe to call multiple times. */
    public static void apply(Context context) {
        apply(getMode(context));
    }

    private static void apply(Mode mode) {
        AppCompatDelegate.setDefaultNightMode(mode.delegateMode);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
