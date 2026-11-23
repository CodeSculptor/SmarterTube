package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import static com.liskovsoft.smartyoutubetv2.mobile.ui.playback.VerticalFullscreenPolicy.Action.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VerticalFullscreenPolicyTest {
    @Test public void detectsVertical() {
        assertTrue(VerticalFullscreenPolicy.isVertical(1080, 1920));
        assertTrue(VerticalFullscreenPolicy.isVertical(720, 800));
    }

    @Test public void landscapeSquareAndUnknownAreNotVertical() {
        assertFalse(VerticalFullscreenPolicy.isVertical(1920, 1080));
        assertFalse(VerticalFullscreenPolicy.isVertical(1080, 1080));
        assertFalse(VerticalFullscreenPolicy.isVertical(1000, 1050));
        assertFalse(VerticalFullscreenPolicy.isVertical(0, 0));
        assertFalse(VerticalFullscreenPolicy.isVertical(0, 1920));
    }

    @Test public void verticalVideoInPortraitGoesUprightFullscreen() {
        assertEquals(ENTER_PORTRAIT_FULL, VerticalFullscreenPolicy.onFullscreenButton(true, true, false));
    }

    @Test public void normalVideoInPortraitGoesLandscape() {
        assertEquals(ENTER_LANDSCAPE, VerticalFullscreenPolicy.onFullscreenButton(true, false, false));
    }

    @Test public void unknownSizeKeepsOldBehaviour() {
        assertEquals(ENTER_LANDSCAPE,
                VerticalFullscreenPolicy.onFullscreenButton(true, VerticalFullscreenPolicy.isVertical(0, 0), false));
    }

    @Test public void buttonAgainExitsUpright() {
        assertEquals(EXIT_PORTRAIT_FULL, VerticalFullscreenPolicy.onFullscreenButton(true, true, true));
    }

    @Test public void landscapeAlwaysExitsLandscape() {
        assertEquals(EXIT_LANDSCAPE, VerticalFullscreenPolicy.onFullscreenButton(false, true, false));
        assertEquals(EXIT_LANDSCAPE, VerticalFullscreenPolicy.onFullscreenButton(false, false, false));
    }

    @Test public void uprightEndsOnRotateShortsOrNonVertical() {
        assertTrue(VerticalFullscreenPolicy.stillValid(true, true, false, true));
        assertFalse(VerticalFullscreenPolicy.stillValid(true, false, false, true));
        assertFalse(VerticalFullscreenPolicy.stillValid(true, true, true, true));
        assertFalse(VerticalFullscreenPolicy.stillValid(true, true, false, false));
        assertFalse(VerticalFullscreenPolicy.stillValid(false, true, false, true));
    }
}
