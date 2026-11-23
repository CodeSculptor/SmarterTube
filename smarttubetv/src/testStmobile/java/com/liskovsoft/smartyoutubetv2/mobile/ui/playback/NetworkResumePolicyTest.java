package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import static com.liskovsoft.smartyoutubetv2.mobile.ui.playback.NetworkResumePolicy.Action.GIVE_UP;
import static com.liskovsoft.smartyoutubetv2.mobile.ui.playback.NetworkResumePolicy.Action.NONE;
import static com.liskovsoft.smartyoutubetv2.mobile.ui.playback.NetworkResumePolicy.Action.RESTART;
import static com.liskovsoft.smartyoutubetv2.mobile.ui.playback.NetworkResumePolicy.Action.SCHEDULE_CHECK;
import static com.liskovsoft.smartyoutubetv2.mobile.ui.playback.NetworkResumePolicy.Action.SHOW_RECONNECTED;
import static com.liskovsoft.smartyoutubetv2.mobile.ui.playback.NetworkResumePolicy.Action.SHOW_WAITING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NetworkResumePolicyTest {
    @Test
    public void lossWhilePlayingStartsEpisode() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        assertEquals(SHOW_WAITING, p.onNetworkLost(true, true));
        assertTrue(p.isWaiting());
        assertEquals(SCHEDULE_CHECK, p.onNetworkAvailable());
    }

    @Test
    public void lossWhilePausedDoesNothing() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        assertEquals(NONE, p.onNetworkLost(true, false));
        assertFalse(p.isWaiting());
        assertEquals(NONE, p.onNetworkAvailable());
    }

    @Test
    public void lossWithPlayerClosedDoesNothing() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        assertEquals(NONE, p.onNetworkLost(false, true));
        assertEquals(NONE, p.onNetworkAvailable());
    }

    @Test
    public void pausedDuringOutageDoesNotResume() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        p.onNetworkLost(true, true);
        assertEquals(NONE, p.onCheck(true, false, false, true));
        assertFalse(p.isWaiting());
    }

    @Test
    public void closedDuringOutageDoesNotResume() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        p.onNetworkLost(true, true);
        assertEquals(NONE, p.onCheck(false, true, false, true));
        assertFalse(p.isWaiting());
    }

    @Test
    public void selfRecoveredShowsReconnectedOnce() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        p.onNetworkLost(true, true);
        assertEquals(SHOW_RECONNECTED, p.onCheck(true, true, true, true));
        assertEquals(NONE, p.onCheck(true, true, true, true));
    }

    @Test
    public void deadPlayerIsRestartedThenCappedWithGiveUp() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        p.onNetworkLost(true, true);
        for (int i = 0; i < NetworkResumePolicy.MAX_ATTEMPTS; i++) {
            assertEquals(RESTART, p.onCheck(true, true, false, true));
        }
        assertEquals(GIVE_UP, p.onCheck(true, true, false, true));
        assertFalse(p.isWaiting());
        assertEquals(NONE, p.onCheck(true, true, false, true)); // never loops afterwards
    }

    @Test
    public void restartThatWorksStopsRetrying() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        p.onNetworkLost(true, true);
        assertEquals(RESTART, p.onCheck(true, true, false, true));
        assertEquals(SHOW_RECONNECTED, p.onCheck(true, true, true, true));
        assertEquals(0, p.getAttempts());
    }

    @Test
    public void networkDroppingAgainWaitsWithoutBurningAttempts() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        p.onNetworkLost(true, true);
        assertEquals(NONE, p.onCheck(true, true, false, false));
        assertEquals(0, p.getAttempts());
        assertTrue(p.isWaiting());
    }

    @Test
    public void backoffDoublesAndCaps() {
        assertEquals(2_000, NetworkResumePolicy.backoffMs(0));
        assertEquals(4_000, NetworkResumePolicy.backoffMs(1));
        assertEquals(8_000, NetworkResumePolicy.backoffMs(2));
        assertEquals(16_000, NetworkResumePolicy.backoffMs(3));
        assertEquals(16_000, NetworkResumePolicy.backoffMs(30));
    }
}
