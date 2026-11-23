package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LiveChatScrollPolicyTest {
    @Test
    public void followsByDefault() {
        LiveChatScrollPolicy p = new LiveChatScrollPolicy();
        assertTrue(p.onMessageAdded());
        assertFalse(p.isPaused());
    }

    @Test
    public void userScrollAwayPausesAndCounts() {
        LiveChatScrollPolicy p = new LiveChatScrollPolicy();
        p.onScrolled(false, true);
        assertTrue(p.isPaused());
        assertFalse(p.onMessageAdded());
        assertFalse(p.onMessageAdded());
        assertEquals(2, p.pendingCount());
    }

    @Test
    public void programmaticScrollNeverPauses() {
        LiveChatScrollPolicy p = new LiveChatScrollPolicy();
        p.onScrolled(false, false);
        assertFalse(p.isPaused());
        assertTrue(p.onMessageAdded());
    }

    @Test
    public void returningToBottomResumes() {
        LiveChatScrollPolicy p = new LiveChatScrollPolicy();
        p.onScrolled(false, true);
        p.onMessageAdded();
        p.onScrolled(true, true);
        assertFalse(p.isPaused());
        assertEquals(0, p.pendingCount());
        assertTrue(p.onMessageAdded());
    }

    @Test
    public void jumpToLatestResumes() {
        LiveChatScrollPolicy p = new LiveChatScrollPolicy();
        p.onScrolled(false, true);
        p.onMessageAdded();
        p.onJumpToLatest();
        assertFalse(p.isPaused());
        assertEquals(0, p.pendingCount());
    }
}
