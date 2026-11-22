package com.liskovsoft.smartyoutubetv2.mobile.ui.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/** stmobile-only prefs for channel avatars on feed cards: on/off switch (default on) and the channel-id to avatar-url cache. */
public final class MobileCardAvatarPrefs {
    private static final String PREFS_NAME = "mobile_card_avatar_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String URL_PREFIX = "u_";

    private MobileCardAvatarPrefs() {}

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static String getUrl(Context context, String channelId) {
        return prefs(context).getString(URL_PREFIX + channelId, null);
    }

    public static void putUrl(Context context, String channelId, String url) {
        prefs(context).edit().putString(URL_PREFIX + channelId, url).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
