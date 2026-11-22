package com.liskovsoft.smartyoutubetv2.mobile.ui.miniplayer;

/**
 * Pure decisions for the mini-player (#96), kept free of Android types so they unit-test on the JVM.
 */
public final class MiniPlayerPolicy {
    private MiniPlayerPolicy() {}

    /**
     * Whether Back in the player should minimise it to the bar instead of closing it.
     * Shorts are a vertical feed with their own UI and PiP is an explicit user choice, so both
     * keep their current Back behaviour.
     */
    public static boolean shouldMinimise(boolean enabled, boolean hasVideo, boolean isShorts,
                                         boolean inPip, boolean pipBackgroundMode,
                                         boolean engineInitialized) {
        return enabled && hasVideo && !isShorts && !inPip && !pipBackgroundMode && engineInitialized;
    }

    /** Whether the bar should be visible on a browse screen. */
    public static boolean shouldShowBar(boolean minimised, boolean hasVideo, boolean hasView,
                                        boolean engineInitialized) {
        return minimised && hasVideo && hasView && engineInitialized;
    }

    /** The minimised flag is stale (the player is gone) and must be dropped. */
    public static boolean isStale(boolean minimised, boolean hasView, boolean engineInitialized) {
        return minimised && (!hasView || !engineInitialized);
    }
}
