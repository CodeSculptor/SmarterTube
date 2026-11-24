package com.liskovsoft.smartyoutubetv2.mobile.offline;

import android.os.Build;

/** Minimum-API gate for offline saving: hidden entirely below Android 5.0 (API 21). */
public final class OfflineSupport {
    public static final int MIN_SDK = 21;

    private OfflineSupport() {}

    public static boolean isSupported() {
        return isSupported(Build.VERSION.SDK_INT);
    }

    static boolean isSupported(int sdkInt) {
        return sdkInt >= MIN_SDK;
    }
}
