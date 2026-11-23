package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

/**
 * Auto-scroll rule for the live chat list: follow new messages until the user drags away from the
 * bottom, then hold position and count what arrives until they return (or tap "jump to latest").
 * Programmatic scrolls never pause it. Pure Java for JVM tests.
 */
public final class LiveChatScrollPolicy {
    private boolean mPaused;
    private int mPending;

    /** The list was scrolled; {@code userDriven} is true while the user's finger is on it. */
    public void onScrolled(boolean atBottom, boolean userDriven) {
        if (atBottom) {
            mPaused = false;
            mPending = 0;
        } else if (userDriven) {
            mPaused = true;
        }
    }

    /**
     * A message was appended.
     *
     * @return true when the list should scroll to the newest message
     */
    public boolean onMessageAdded() {
        if (mPaused) {
            mPending++;
            return false;
        }
        return true;
    }

    /** The user asked to jump to the latest message. */
    public void onJumpToLatest() {
        mPaused = false;
        mPending = 0;
    }

    /** Messages that arrived while paused. */
    public int pendingCount() {
        return mPending;
    }

    public boolean isPaused() {
        return mPaused;
    }

    public void reset() {
        onJumpToLatest();
    }
}
