package com.liskovsoft.smartyoutubetv2.mobile.ui.miniplayer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MiniPlayerPolicyTest {
    // enabled, hasVideo, isShorts, inPip, pipBackgroundMode, engineInitialized
    @Test
    public void minimisesRegularVideo() {
        assertTrue(MiniPlayerPolicy.shouldMinimise(true, true, false, false, false, true));
    }

    @Test
    public void settingOffKeepsOldBack() {
        assertFalse(MiniPlayerPolicy.shouldMinimise(false, true, false, false, false, true));
    }

    @Test
    public void shortsAreNotMinimised() {
        assertFalse(MiniPlayerPolicy.shouldMinimise(true, true, true, false, false, true));
    }

    @Test
    public void pipKeepsItsOwnBack() {
        assertFalse(MiniPlayerPolicy.shouldMinimise(true, true, false, true, false, true));
        assertFalse(MiniPlayerPolicy.shouldMinimise(true, true, false, false, true, true));
    }

    @Test
    public void needsVideoAndLiveEngine() {
        assertFalse(MiniPlayerPolicy.shouldMinimise(true, false, false, false, false, true));
        assertFalse(MiniPlayerPolicy.shouldMinimise(true, true, false, false, false, false));
    }

    @Test
    public void barShowsOnlyWhileMinimisedAndAlive() {
        assertTrue(MiniPlayerPolicy.shouldShowBar(true, true, true, true));
        assertFalse(MiniPlayerPolicy.shouldShowBar(false, true, true, true));
        assertFalse(MiniPlayerPolicy.shouldShowBar(true, false, true, true));
        assertFalse(MiniPlayerPolicy.shouldShowBar(true, true, false, true));
        assertFalse(MiniPlayerPolicy.shouldShowBar(true, true, true, false));
    }

    @Test
    public void staleWhenPlayerIsGone() {
        assertTrue(MiniPlayerPolicy.isStale(true, false, false));
        assertTrue(MiniPlayerPolicy.isStale(true, true, false));
        assertFalse(MiniPlayerPolicy.isStale(true, true, true));
        assertFalse(MiniPlayerPolicy.isStale(false, false, false));
    }
}
