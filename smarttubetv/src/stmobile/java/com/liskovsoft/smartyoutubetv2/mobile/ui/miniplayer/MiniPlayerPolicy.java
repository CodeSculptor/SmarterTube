package com.liskovsoft.smartyoutubetv2.mobile.ui.miniplayer;

/**
 * Pure decisions for the mini-player (#96), kept free of Android types so they unit-test on the JVM.
 */
public final class MiniPlayerPolicy {
    private MiniPlayerPolicy() {}

    /**
     * Whether Back in the player should minimise it to the bar instead of closing it.
     * Shorts are a vertical feed with their own UI and keep their Back behaviour; a player
     * already in a PiP window does too. The Background mode setting does NOT veto: with the
     * mini-player on, Back minimises even when Background mode is PiP (PiP stays reachable via
     * the Home key / leave hint).
     */
    public static boolean shouldMinimise(boolean enabled, boolean hasVideo, boolean isShorts,
                                         boolean inPip, boolean engineInitialized) {
        return enabled && hasVideo && !isShorts && !inPip && engineInitialized;
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
