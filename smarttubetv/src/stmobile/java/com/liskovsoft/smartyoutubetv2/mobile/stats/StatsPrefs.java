package com.liskovsoft.smartyoutubetv2.mobile.stats;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Flavor-local persistence for the opt-in anonymous stats (usage heartbeat + crash reports).
 *
 * Holds the consent choice and the heartbeat's "last sent" markers. Nothing here identifies the
 * install: there is deliberately no install id — see {@link Heartbeat} for how counts are derived
 * without one. stmobile-only, so shared {@code common} code stays untouched.
 */
public final class StatsPrefs {
    private static final String PREFS_NAME = "mobile_stats_prefs";
    private static final String KEY_CONSENT = "consent";
    private static final String KEY_LAST_DAY = "last_day";
    private static final String KEY_LAST_WEEK = "last_week";
    private static final String KEY_LAST_MONTH = "last_month";

    public enum Consent {
        /** Never asked — the one-time prompt is still due. Nothing is sent. */
        UNASKED,
        ON,
        OFF
    }

    private StatsPrefs() {}

    public static Consent getConsent(Context context) {
        String raw = prefs(context).getString(KEY_CONSENT, null);
        if (raw != null) {
            try {
                return Consent.valueOf(raw);
            } catch (IllegalArgumentException ignored) {
                // Unknown value from a future build: treat as not asked.
            }
        }
        return Consent.UNASKED;
    }

    public static void setConsent(Context context, Consent consent) {
        prefs(context).edit().putString(KEY_CONSENT, consent.name()).apply();
    }

    public static boolean isEnabled(Context context) {
        return getConsent(context) == Consent.ON;
    }

    /** Last UTC epoch-day / week / month a heartbeat was sent, or -1 if never. */
    public static Heartbeat.State getHeartbeatState(Context context) {
        SharedPreferences p = prefs(context);
        return new Heartbeat.State(
                p.getLong(KEY_LAST_DAY, -1), p.getLong(KEY_LAST_WEEK, -1), p.getLong(KEY_LAST_MONTH, -1));
    }

    public static void saveHeartbeatState(Context context, Heartbeat.State state) {
        prefs(context).edit()
                .putLong(KEY_LAST_DAY, state.lastDay)
                .putLong(KEY_LAST_WEEK, state.lastWeek)
                .putLong(KEY_LAST_MONTH, state.lastMonth)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
