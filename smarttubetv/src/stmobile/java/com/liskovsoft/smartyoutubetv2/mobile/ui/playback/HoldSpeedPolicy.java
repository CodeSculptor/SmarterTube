package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

/**
 * Pure logic for press-and-hold 2x: remembers the speed in force when the hold began so the exact
 * value comes back on release, whatever the user had chosen. No Android dependencies.
 */
final class HoldSpeedPolicy {
    static final float HOLD_SPEED = 2f;

    private static final float NONE = Float.NaN;
    private float mSaved = NONE;

    boolean isHolding() {
        return !Float.isNaN(mSaved);
    }

    /** Starts a hold. Returns the speed to play at, or NaN if the hold must not start. */
    float begin(float currentSpeed) {
        if (isHolding() || !(currentSpeed > 0f)) return NONE;
        mSaved = currentSpeed;
        return HOLD_SPEED;
    }

    /** Ends a hold. Returns the speed to restore, or NaN if no hold was active. */
    float end() {
        float saved = mSaved;
        mSaved = NONE;
        return saved;
    }

    /** Whether a touch may start a hold timer at all. */
    static boolean canWatch(boolean enabled, boolean shorts, boolean inPip, boolean playing,
                            boolean onVideoNotControl) {
        return enabled && !shorts && !inPip && playing && onVideoNotControl;
    }

    /** A finger that drifts further than the touch slop is a swipe or scroll, not a hold. */
    static boolean movedTooFar(float dx, float dy, float slop) {
        return dx * dx + dy * dy > slop * slop;
    }
}
