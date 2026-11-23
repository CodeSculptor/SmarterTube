package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

/**
 * Pure decision/backoff logic for resuming playback after the network comes back. No Android
 * dependencies so it is unit-testable.
 *
 * Episode: network lost while the user wanted playback (playWhenReady) and the engine was alive ->
 * we are "waiting". When the network returns we wait a grace period (upstream's own error fixer
 * usually recovers by itself), then, if the player still is not playing and the user has not
 * paused, restart the engine (position is preserved by the engine restart). Retries are capped
 * with exponential backoff. Because an episode only starts from a real connectivity loss, errors
 * that are not network related (age restriction, unavailable video) never trigger a retry loop.
 */
final class NetworkResumePolicy {
    enum Action { NONE, SHOW_WAITING, SCHEDULE_CHECK, RESTART, SHOW_RECONNECTED, GIVE_UP }

    static final long GRACE_MS = 3_000;
    static final long BASE_BACKOFF_MS = 2_000;
    static final long MAX_BACKOFF_MS = 16_000;
    static final int MAX_ATTEMPTS = 4;

    private boolean mWaiting;
    private int mAttempts;

    boolean isWaiting() {
        return mWaiting;
    }

    int getAttempts() {
        return mAttempts;
    }

    /** Backoff before the check that follows restart number {@code attempt} (0-based). */
    static long backoffMs(int attempt) {
        long delay = BASE_BACKOFF_MS << Math.min(Math.max(attempt, 0), 10);
        return Math.min(delay, MAX_BACKOFF_MS);
    }

    /** Connectivity lost. */
    Action onNetworkLost(boolean engineActive, boolean playWhenReady) {
        if (!engineActive || !playWhenReady) {
            reset();
            return Action.NONE;
        }
        mWaiting = true;
        mAttempts = 0;
        return Action.SHOW_WAITING;
    }

    /** Connectivity back. Caller schedules a check after {@link #GRACE_MS} when told to. */
    Action onNetworkAvailable() {
        return mWaiting ? Action.SCHEDULE_CHECK : Action.NONE;
    }

    /**
     * Check after the grace period / backoff.
     *
     * @param engineActive   player engine exists (not closed/released)
     * @param playWhenReady  user still wants playback (not paused)
     * @param playing        player is actually playing
     * @param networkUp      connectivity is currently available
     */
    Action onCheck(boolean engineActive, boolean playWhenReady, boolean playing, boolean networkUp) {
        if (!mWaiting) {
            return Action.NONE;
        }
        if (!engineActive || !playWhenReady) { // closed or paused by the user meanwhile
            reset();
            return Action.NONE;
        }
        if (playing) {
            reset();
            return Action.SHOW_RECONNECTED;
        }
        if (!networkUp) {
            return Action.NONE; // dropped again; the next onNetworkAvailable re-schedules
        }
        if (mAttempts >= MAX_ATTEMPTS) {
            reset();
            return Action.GIVE_UP;
        }
        mAttempts++;
        return Action.RESTART;
    }

    void reset() {
        mWaiting = false;
        mAttempts = 0;
    }
}
