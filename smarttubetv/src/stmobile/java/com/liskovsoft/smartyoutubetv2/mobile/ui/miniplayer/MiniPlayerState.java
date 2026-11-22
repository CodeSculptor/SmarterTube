package com.liskovsoft.smartyoutubetv2.mobile.ui.miniplayer;

/** Process-wide "the player is minimised to the bar" flag (#96). Derived live, never persisted. */
public final class MiniPlayerState {
    private static volatile boolean sMinimised;

    private MiniPlayerState() {}

    public static boolean isMinimised() {
        return sMinimised;
    }

    public static void setMinimised(boolean minimised) {
        sMinimised = minimised;
    }
}
