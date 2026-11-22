package com.liskovsoft.smartyoutubetv2.mobile.ui.dialogs;

import android.os.SystemClock;

/**
 * Hand-off from a Settings-search result to the settings dialog it opens: which category / option
 * to expand, scroll to and briefly highlight. Set just before the section is opened; consumed by
 * the first dialog that actually contains the target (see MobileAppDialogAdapter), and dropped if
 * none shows up soon, so a stale request can never hit an unrelated dialog later.
 */
public final class SettingsSearchTarget {
    private static final long EXPIRY_MS = 6000;
    private static SettingsSearchTarget sPending;

    final String category;
    final String optionTitle;
    private final long createdAt = SystemClock.elapsedRealtime();

    private SettingsSearchTarget(String category, String optionTitle) {
        this.category = category == null ? "" : category;
        this.optionTitle = optionTitle == null ? "" : optionTitle;
    }

    /** category / optionTitle may be empty: both empty = just open the section. */
    public static void set(String category, String optionTitle) {
        sPending = (category == null || category.isEmpty()) && (optionTitle == null || optionTitle.isEmpty())
                ? null : new SettingsSearchTarget(category, optionTitle);
    }

    static SettingsSearchTarget peek() {
        if (sPending != null && SystemClock.elapsedRealtime() - sPending.createdAt > EXPIRY_MS) {
            sPending = null;
        }
        return sPending;
    }

    static void clear() {
        sPending = null;
    }
}
