package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HoldSpeedPolicyTest {
    @Test
    public void restoresExactPreviousSpeed() {
        HoldSpeedPolicy p = new HoldSpeedPolicy();
        assertEquals(2f, p.begin(1.25f), 0f);
        assertTrue(p.isHolding());
        assertEquals(1.25f, p.end(), 0f);
        assertFalse(p.isHolding());
    }

    @Test
    public void secondBeginWhileHoldingDoesNotOverwriteSavedSpeed() {
        HoldSpeedPolicy p = new HoldSpeedPolicy();
        p.begin(0.75f);
        assertTrue(Float.isNaN(p.begin(2f)));
        assertEquals(0.75f, p.end(), 0f);
    }

    @Test
    public void invalidSpeedRefusesHold() {
        HoldSpeedPolicy p = new HoldSpeedPolicy();
        assertTrue(Float.isNaN(p.begin(-1f)));
        assertFalse(p.isHolding());
    }

    @Test
    public void endWithoutHoldIsNoop() {
        assertTrue(Float.isNaN(new HoldSpeedPolicy().end()));
    }

    @Test
    public void canWatchRules() {
        assertTrue(HoldSpeedPolicy.canWatch(true, false, false, true, true));
        assertFalse(HoldSpeedPolicy.canWatch(false, false, false, true, true));
        assertFalse(HoldSpeedPolicy.canWatch(true, true, false, true, true));
        assertFalse(HoldSpeedPolicy.canWatch(true, false, true, true, true));
        assertFalse(HoldSpeedPolicy.canWatch(true, false, false, false, true));
        assertFalse(HoldSpeedPolicy.canWatch(true, false, false, true, false));
    }

    @Test
    public void slop() {
        assertFalse(HoldSpeedPolicy.movedTooFar(3, 4, 5));
        assertTrue(HoldSpeedPolicy.movedTooFar(4, 4, 5));
    }
}
