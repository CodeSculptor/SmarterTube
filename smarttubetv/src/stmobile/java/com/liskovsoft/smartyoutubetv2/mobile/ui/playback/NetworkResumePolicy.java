package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

/**
 * Pure decision/backoff logic for resuming playback after the network comes back. No Android
 * dependencies so it is unit-testable.
 *
 * Episode: network lost while the user wanted playback (playWhenReady) and the engine was alive,
 * OR an error/stall fix was skipped while offline (errorSkipped: the player is dead, even if the
 * user had paused). When the network returns we wait a grace period (upstream's own error fixer
 * may recover by itself), then, if the player is not playing (or the skipped error is still
 * pending), restart the engine (position is preserved by the engine restart). A paused user stays
 * paused: {@link #shouldPauseAfterRestart()}. Retries are capped with exponential backoff. An
 * episode only starts from a real connectivity loss, so errors that are not network related
 * (age restriction, unavailable video) never trigger a retry loop.
 */
final class NetworkResumePolicy {
    enum Action { NONE, SHOW_WAITING, SCHEDULE_CHECK, RESTART, SHOW_RECONNECTED, GIVE_UP }

    static final long GRACE_MS = 3_000;
    static final long BASE_BACKOFF_MS = 2_000;
    static final long MAX_BACKOFF_MS = 16_000;
    static final int MAX_ATTEMPTS = 4;

    private boolean mWaiting;
    private boolean mErrorEpisode;
    private boolean mPauseAfterRestart;
    private int mAttempts;

    boolean isWaiting() {
        return mWaiting;
    }

    int getAttempts() {
        return mAttempts;
    }

    /** True when the last restart was for a user who had paused: re-pause once the engine is back. */
    boolean shouldPauseAfterRestart() {
        return mPauseAfterRestart;
    }

    /** Backoff before the check that follows restart number {@code attempt} (0-based). */
    static long backoffMs(int attempt) {
        long delay = BASE_BACKOFF_MS << Math.min(Math.max(attempt, 0), 10);
        return Math.min(delay, MAX_BACKOFF_MS);
    }

    /** Connectivity lost (after debounce). */
    Action onNetworkLost(boolean engineActive, boolean playWhenReady, boolean errorSkipped) {
        if (!engineActive || !(playWhenReady || errorSkipped)) {
            reset();
            return Action.NONE;
        }
        mWaiting = true;
        mErrorEpisode = errorSkipped;
        mAttempts = 0;
        return Action.SHOW_WAITING;
    }

    /** Connectivity back. Caller schedules a check after {@link #GRACE_MS} when told to. */
    Action onNetworkAvailable(boolean engineActive, boolean errorSkipped) {
        if (!mWaiting && engineActive && errorSkipped) {
            // Error happened during the outage while the user was paused: no episode yet.
            mWaiting = true;
            mErrorEpisode = true;
            mAttempts = 0;
        }
        return mWaiting ? Action.SCHEDULE_CHECK : Action.NONE;
    }

    /** The user pressed play and a skipped error is pending: recover right away. */
    Action onPlayRequested(boolean engineActive, boolean errorSkipped, boolean networkUp) {
        if (!engineActive || !errorSkipped) {
            return Action.NONE;
        }
        if (!networkUp) {
            return Action.SHOW_WAITING;
        }
        if (!mWaiting) {
            mWaiting = true;
            mAttempts = 0;
        }
        mErrorEpisode = true;
        return Action.SCHEDULE_CHECK;
    }

    /**
     * Check after the grace period / backoff.
     *
     * @param engineActive   player engine exists (not closed/released)
     * @param playWhenReady  user still wants playback (not paused)
     * @param playing        player is actually playing
     * @param networkUp      connectivity is currently available
     * @param errorSkipped   an error fix was skipped while offline and nothing recovered it yet
     */
    Action onCheck(boolean engineActive, boolean playWhenReady, boolean playing, boolean networkUp, boolean errorSkipped) {
        if (!mWaiting) {
            return Action.NONE;
        }
        mErrorEpisode |= errorSkipped;
        if (!engineActive || !(playWhenReady || mErrorEpisode)) { // closed, or paused with a healthy player
            reset();
            return Action.NONE;
        }
        if (playing) {
            reset();
            return Action.SHOW_RECONNECTED;
        }
        if (mErrorEpisode && !errorSkipped) {
            // The skipped error got cleared (our restart, or something else reloaded the video).
            boolean restartedByUs = mAttempts > 0;
            reset();
            return restartedByUs ? Action.SHOW_RECONNECTED : Action.NONE;
        }
        if (!networkUp) {
            return Action.NONE; // dropped again; the next onNetworkAvailable re-schedules
        }
        if (mAttempts >= MAX_ATTEMPTS) {
            reset();
            return Action.GIVE_UP;
        }
        mAttempts++;
        mPauseAfterRestart = !playWhenReady;
        return Action.RESTART;
    }

    void reset() {
        mWaiting = false;
        mErrorEpisode = false;
        mAttempts = 0;
        // mPauseAfterRestart intentionally survives reset(): the helper consumes it after the restart.
    }

    void clearPauseAfterRestart() {
        mPauseAfterRestart = false;
    }
}
