package com.liskovsoft.smartyoutubetv2.mobile.stats;

/**
 * Pure logic: maps the package that installed the app to a coarse, fixed bucket for the opt-in
 * stats. The raw package name is never sent. No Android dependencies — unit-tested.
 */
public final class InstallSource {
    public static final String FDROID = "fdroid";
    public static final String OBTAINIUM = "obtainium";
    public static final String PLAY = "play";
    /** Android's own installer or no installer: a downloaded APK (GitHub, browser, file manager). */
    public static final String SIDELOAD = "sideload";
    /** The app's own updater installed the current version, so the original source is not known. */
    public static final String SELF = "self";
    public static final String OTHER = "other";

    private InstallSource() {}

    public static String bucket(String installer, String ownPackage) {
        if (installer == null || installer.isEmpty()) {
            return SIDELOAD;
        }
        if (installer.equals(ownPackage)) {
            return SELF;
        }
        switch (installer) {
            case "org.fdroid.fdroid":
            case "org.fdroid.basic":
            case "com.machiav3lli.fdroid": // Neo Store
            case "eu.bubu1.fdroidclassic":
                return FDROID;
            case "dev.imranr.obtainium":
            case "dev.imranr.obtainium.fdroid":
                return OBTAINIUM;
            case "com.android.vending":
                return PLAY;
            case "com.google.android.packageinstaller":
            case "com.android.packageinstaller":
            case "com.samsung.android.packageinstaller":
                return SIDELOAD;
            default:
                return OTHER;
        }
    }
}
