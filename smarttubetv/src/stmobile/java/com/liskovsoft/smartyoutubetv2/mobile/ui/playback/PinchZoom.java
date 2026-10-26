package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Pinch to zoom in the landscape player (#85), like the YouTube app: spread two fingers to zoom the
 * video to fill the screen (cropping the edges), pinch them together to go back to the whole
 * picture. The zoom is the player's own resize mode (Settings > Player > Video zoom uses the same
 * machinery), so nothing new is drawn; a short label says which mode is on.
 *
 * A pinch starts at the second finger. Everything the first finger already started (a held swipe,
 * the tap-to-show path) is cancelled by the caller, and the rest of the gesture, up to the last
 * finger lifting, belongs to the pinch.
 */
final class PinchZoom {
    /** The video's fit (false) / fill (true) state. */
    interface Target {
        boolean isFill();
        void setFill(boolean fill);
    }

    private static final float THRESHOLD = 1.15f; // spread / pinch this much before the mode flips
    private static final int LABEL_HIDE_MS = 900;

    private final Target mTarget;
    private final TextView mLabel;
    private final ScaleGestureDetector mDetector;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHideLabel;

    private boolean mActive;
    private boolean mFill;
    private float mScale;

    /** Returns null if the label isn't in the player layout. */
    static PinchZoom create(Activity activity, Target target) {
        View label = activity.findViewById(R.id.mobile_zoom_label);
        return label instanceof TextView ? new PinchZoom(activity, (TextView) label, target) : null;
    }

    private PinchZoom(Activity activity, TextView label, Target target) {
        mTarget = target;
        mLabel = label;
        mHideLabel = () -> mLabel.setVisibility(View.GONE);
        mDetector = new ScaleGestureDetector(activity, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                mScale = 1f;
                mFill = mTarget.isFill(); // Settings > Player > Video zoom may already fill
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                mScale *= detector.getScaleFactor();
                if (!mFill && mScale > THRESHOLD) {
                    apply(true);
                } else if (mFill && mScale < 1f / THRESHOLD) {
                    apply(false);
                }
                return true;
            }
        });
        // No quick-scale (double tap and drag): double tap already seeks.
        mDetector.setQuickScaleEnabled(false);
    }

    /** Whether a pinch owns the current touch sequence. */
    boolean isActive() {
        return mActive;
    }

    /**
     * Feed every event of a touch sequence in the landscape player. Returns true when this event
     * makes a second finger touch down, so a pinch begins and the caller must cancel what the
     * first finger started.
     */
    boolean watch(MotionEvent event) {
        mDetector.onTouchEvent(event);
        if (!mActive && event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() == 2) {
            mActive = true;
            return true;
        }
        return false;
    }

    /** The rest of an active pinch: always consumed, until the last finger lifts. */
    boolean handle(MotionEvent event) {
        mDetector.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mActive = false;
        }
        return true;
    }

    /** Drop any pinch in progress and its label (leaving the landscape player). */
    void reset() {
        mActive = false;
        mHandler.removeCallbacks(mHideLabel);
        mLabel.setVisibility(View.GONE);
    }

    private void apply(boolean fill) {
        mFill = fill;
        mScale = 1f; // the next flip needs a fresh spread / pinch from here
        mTarget.setFill(fill);
        mLabel.setText(fill ? R.string.mobile_zoom_fill : R.string.mobile_zoom_fit);
        mLabel.setVisibility(View.VISIBLE);
        mLabel.bringToFront(); // above the Leanback overlay, like the swipe indicator
        mHandler.removeCallbacks(mHideLabel);
        mHandler.postDelayed(mHideLabel, LABEL_HIDE_MS);
    }
}
