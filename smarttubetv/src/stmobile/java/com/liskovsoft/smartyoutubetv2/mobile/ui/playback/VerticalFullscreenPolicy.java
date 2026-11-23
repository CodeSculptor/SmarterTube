package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

/**
 * Pure decision logic for the full-screen button with vertical (portrait) non-Shorts videos.
 * A vertical video goes full-screen upright (the strip and panel drop away, the picture fills the
 * screen) instead of being forced into landscape with black bars. Unknown frame size falls back
 * to the old behaviour (landscape).
 */
final class VerticalFullscreenPolicy {
    enum Action { ENTER_LANDSCAPE, ENTER_PORTRAIT_FULL, EXIT_PORTRAIT_FULL, EXIT_LANDSCAPE }

    /** Taller than wide by a clear margin; 0/unknown sizes are not vertical. */
    static boolean isVertical(int width, int height) {
        return width > 0 && height > 0 && height * 100L >= width * 110L;
    }

    static Action onFullscreenButton(boolean screenPortrait, boolean videoVertical, boolean inPortraitFull) {
        if (!screenPortrait) {
            return Action.EXIT_LANDSCAPE;
        }
        if (inPortraitFull) {
            return Action.EXIT_PORTRAIT_FULL;
        }
        return videoVertical ? Action.ENTER_PORTRAIT_FULL : Action.ENTER_LANDSCAPE;
    }

    /** Whether upright full-screen should stay on after a layout/size/orientation event. */
    static boolean stillValid(boolean portraitFull, boolean screenPortrait, boolean isShorts, boolean videoVertical) {
        return portraitFull && screenPortrait && !isShorts && videoVertical;
    }

    private VerticalFullscreenPolicy() {}
}
