package com.liskovsoft.smartyoutubetv2.mobile.offline;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.StatFs;

import java.io.File;

/** Settings + one-time flags for offline saving, plus the storage root. */
public final class OfflinePrefs {
    private static final String PREFS = "mobile_offline_prefs";
    private static final String KEY_WIFI_ONLY = "wifi_only";
    private static final String KEY_NOTICE_SHOWN = "notice_shown";

    private OfflinePrefs() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isWifiOnly(Context c) { return prefs(c).getBoolean(KEY_WIFI_ONLY, true); }
    public static void setWifiOnly(Context c, boolean v) { prefs(c).edit().putBoolean(KEY_WIFI_ONLY, v).apply(); }
    public static boolean isNoticeShown(Context c) { return prefs(c).getBoolean(KEY_NOTICE_SHOWN, false); }
    public static void setNoticeShown(Context c) { prefs(c).edit().putBoolean(KEY_NOTICE_SHOWN, true).apply(); }

    /** App-private storage (no permission needed, removed on uninstall). */
    public static OfflineStore store(Context c) {
        Context app = c.getApplicationContext();
        File base = app.getExternalFilesDir("offline");
        if (base == null) base = new File(app.getFilesDir(), "offline");
        if (!base.exists()) base.mkdirs();
        return new OfflineStore(base);
    }

    public static long freeBytes(Context c) {
        try {
            StatFs fs = new StatFs(store(c).root().getAbsolutePath());
            if (android.os.Build.VERSION.SDK_INT >= 18) {
                return fs.getAvailableBytes();
            }
            return (long) fs.getAvailableBlocks() * fs.getBlockSize();
        } catch (Exception e) {
            return Long.MAX_VALUE; // unknown: don't block
        }
    }
}
