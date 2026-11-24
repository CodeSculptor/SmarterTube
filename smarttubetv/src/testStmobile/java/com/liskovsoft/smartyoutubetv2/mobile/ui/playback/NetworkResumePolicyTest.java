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
        assertEquals(SHOW_WAITING, p.onNetworkLost(true, true, false));
        assertTrue(p.isWaiting());
        assertEquals(SCHEDULE_CHECK, p.onNetworkAvailable(true, false));
    }

    @Test
    public void lossWhilePausedDoesNothing() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        assertEquals(NONE, p.onNetworkLost(true, false, false));
        assertFalse(p.isWaiting());
        assertEquals(NONE, p.onNetworkAvailable(true, false));
    }

    @Test
    public void lossWithPlayerClosedDoesNothing() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        assertEquals(NONE, p.onNetworkLost(false, true, false));
        assertEquals(NONE, p.onNetworkAvailable(true, false));
    }

    @Test
    public void pausedDuringOutageDoesNotResume() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        p.onNetworkLost(true, true, false);
        assertEquals(NONE, p.onCheck(true, false, false, true, false));
        assertFalse(p.isWaiting());
    }

    @Test
    public void closedDuringOutageDoesNotResume() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        p.onNetworkLost(true, true, false);
        assertEquals(NONE, p.onCheck(false, true, false, true, false));
        assertFalse(p.isWaiting());
    }

    @Test
    public void selfRecoveredShowsReconnectedOnce() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        p.onNetworkLost(true, true, false);
        assertEquals(SHOW_RECONNECTED, p.onCheck(true, true, true, true, false));
        assertEquals(NONE, p.onCheck(true, true, true, true, false));
    }

    @Test
    public void deadPlayerIsRestartedThenCappedWithGiveUp() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        p.onNetworkLost(true, true, false);
        for (int i = 0; i < NetworkResumePolicy.MAX_ATTEMPTS; i++) {
            assertEquals(RESTART, p.onCheck(true, true, false, true, false));
        }
        assertEquals(GIVE_UP, p.onCheck(true, true, false, true, false));
        assertFalse(p.isWaiting());
        assertEquals(NONE, p.onCheck(true, true, false, true, false)); // never loops afterwards
    }

    @Test
    public void restartThatWorksStopsRetrying() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        p.onNetworkLost(true, true, false);
        assertEquals(RESTART, p.onCheck(true, true, false, true, false));
        assertEquals(SHOW_RECONNECTED, p.onCheck(true, true, true, true, false));
        assertEquals(0, p.getAttempts());
    }

    @Test
    public void networkDroppingAgainWaitsWithoutBurningAttempts() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        p.onNetworkLost(true, true, false);
        assertEquals(NONE, p.onCheck(true, true, false, false, false));
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

    @Test
    public void errorSkippedWhilePausedRestartsAfterReturnAndStaysPaused() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        assertEquals(NONE, p.onNetworkLost(true, false, false)); // paused: no episode yet
        // error arrives during the outage; network returns
        assertEquals(SCHEDULE_CHECK, p.onNetworkAvailable(true, true));
        assertEquals(RESTART, p.onCheck(true, false, false, true, true));
        assertTrue(p.shouldPauseAfterRestart());
        // restart cleared the skipped flag
        assertEquals(SHOW_RECONNECTED, p.onCheck(true, false, false, true, false));
        assertFalse(p.isWaiting());
    }

    @Test
    public void errorSkippedWhilePlayingDoesNotRepause() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        assertEquals(SHOW_WAITING, p.onNetworkLost(true, true, true));
        p.onNetworkAvailable(true, true);
        assertEquals(RESTART, p.onCheck(true, true, false, true, true));
        assertFalse(p.shouldPauseAfterRestart());
    }

    @Test
    public void errorSkippedButPlayerClosedDoesNothing() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        assertEquals(NONE, p.onNetworkAvailable(false, true));
        assertEquals(NONE, p.onNetworkLost(false, false, true));
    }

    @Test
    public void errorSkippedRestartIsCapped() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        p.onNetworkAvailable(true, true);
        for (int i = 0; i < NetworkResumePolicy.MAX_ATTEMPTS; i++) {
            assertEquals(RESTART, p.onCheck(true, false, false, true, true));
        }
        assertEquals(GIVE_UP, p.onCheck(true, false, false, true, true));
        assertFalse(p.isWaiting());
    }

    @Test
    public void errorClearedByOthersEndsEpisodeQuietly() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        p.onNetworkAvailable(true, true);
        assertEquals(NONE, p.onCheck(true, false, false, true, false));
        assertFalse(p.isWaiting());
    }

    @Test
    public void playWhileOfflineWithDeadPlayerShowsWaiting() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        assertEquals(SHOW_WAITING, p.onPlayRequested(true, true, false));
        assertFalse(p.isWaiting());
    }

    @Test
    public void playWhileOnlineWithDeadPlayerRecoversAtOnce() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        assertEquals(SCHEDULE_CHECK, p.onPlayRequested(true, true, true));
        assertEquals(RESTART, p.onCheck(true, true, false, true, true));
        assertFalse(p.shouldPauseAfterRestart());
    }

    @Test
    public void playWithHealthyPlayerIsIgnored() {
        NetworkResumePolicy p = new NetworkResumePolicy();
        assertEquals(NONE, p.onPlayRequested(true, false, true));
    }
}
