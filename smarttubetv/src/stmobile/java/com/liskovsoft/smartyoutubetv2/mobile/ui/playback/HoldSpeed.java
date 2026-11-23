package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Press and hold on the video to play at 2x while held (phone, non-Shorts). Watches touches without
 * consuming them until the hold fires; then it asks the host to cancel whatever the finger had
 * started (tap, swipe, seek) and consumes the rest of the gesture. Releasing restores the exact
 * previous speed.
 */
final class HoldSpeed {
    interface Host {
        float getSpeed();
        void setSpeed(float speed);
        /** The hold fired: cancel anything the first touch started. */
        void onHoldStarted(MotionEvent down);
    }

    private final Host mHost;
    private final HoldSpeedPolicy mPolicy = new HoldSpeedPolicy();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final int mSlop;
    private final int mTimeoutMs;
    private final TextView mIndicator;
    private MotionEvent mDown;
    private boolean mPending;

    private final Runnable mFire = this::fire;

    private void fire() {
        mPending = false;
        if (mDown == null) return;
        float target = mPolicy.begin(mHost.getSpeed());
        if (Float.isNaN(target)) return;
        mHost.setSpeed(target);
        mIndicator.setVisibility(View.VISIBLE);
        mHost.onHoldStarted(mDown);
    }

    private HoldSpeed(Activity activity, Host host) {
        mHost = host;
        ViewConfiguration vc = ViewConfiguration.get(activity);
        mSlop = vc.getScaledTouchSlop();
        mTimeoutMs = ViewConfiguration.getLongPressTimeout();
        float d = activity.getResources().getDisplayMetrics().density;
        mIndicator = new TextView(activity);
        mIndicator.setText("2x \u25B6\u25B6");
        mIndicator.setTextColor(0xFFFFFFFF);
        mIndicator.setTextSize(14);
        mIndicator.setPadding((int) (12 * d), (int) (6 * d), (int) (12 * d), (int) (6 * d));
        mIndicator.setBackgroundColor(0x99000000);
        mIndicator.setVisibility(View.GONE);
        mIndicator.setClickable(false);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = (int) (72 * d);
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content != null) {
            content.addView(mIndicator, lp);
        }
    }

    static HoldSpeed create(Activity activity, Host host) {
        return new HoldSpeed(activity, host);
    }

    boolean isHolding() {
        return mPolicy.isHolding();
    }

    /**
     * @param canStart whether a DOWN at this point may begin a hold (checked on DOWN only)
     * @return true if the event was consumed (a hold is active)
     */
    boolean handle(MotionEvent e, boolean canStart) {
        int action = e.getActionMasked();
        if (mPolicy.isHolding()) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                release();
            }
            return true;
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                cancelPending();
                if (canStart) {
                    mDown = MotionEvent.obtain(e);
                    mPending = true;
                    mHandler.postDelayed(mFire, mTimeoutMs);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mPending && mDown != null && HoldSpeedPolicy.movedTooFar(
                        e.getX() - mDown.getX(), e.getY() - mDown.getY(), mSlop)) {
                    cancelPending();
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelPending();
                break;
            default:
                break;
        }
        return false;
    }

    /** Restores the previous speed and hides the indicator; safe to call any time. */
    void release() {
        cancelPending();
        float restore = mPolicy.end();
        if (!Float.isNaN(restore)) {
            mHost.setSpeed(restore);
        }
        mIndicator.setVisibility(View.GONE);
    }

    private void cancelPending() {
        mHandler.removeCallbacks(mFire);
        mPending = false;
        if (mDown != null) {
            mDown.recycle();
            mDown = null;
        }
    }
}
