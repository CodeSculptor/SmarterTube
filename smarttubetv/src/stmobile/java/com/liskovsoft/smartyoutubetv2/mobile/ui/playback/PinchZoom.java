package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.Locale;

/**
 * Pinch to zoom in the landscape player (#85), like the YouTube app: spreading two fingers zooms
 * the video smoothly into the point between them, and moving the fingers while they're down moves
 * that point (pans). Pinching back in returns to the whole picture. The video view is scaled and
 * moved as a view transform, clamped so the picture never pulls away from a screen edge it covers;
 * a short label shows the zoom level.
 *
 * A pinch starts at the second finger. Everything the first finger already started (a held swipe,
 * the tap-to-show path) is cancelled by the caller, and the rest of the gesture, up to the last
 * finger lifting, belongs to the pinch.
 */
final class PinchZoom {
    /** The video view to zoom (null while the player has none). */
    interface Target {
        View video();
    }

    private static final float MAX_SCALE = 5f;
    private static final float SNAP_TO_FIT = 1.05f; // ending within 5% of the whole picture snaps back to it
    private static final int LABEL_HIDE_MS = 900;

    private final Target mTarget;
    private final TextView mLabel;
    private final ScaleGestureDetector mDetector;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHideLabel;

    private boolean mActive;
    private View mVideo;
    private float mScale = 1f;
    private float mLeft, mTop; // the zoomed picture's top-left corner, in the video's parent
    private float mFocusX, mFocusY; // last pinch focus, in the video's parent

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
                View video = mTarget.video();
                // A mirrored picture (Settings > Player > flip) already owns the view's scale.
                if (video == null || video.getWidth() == 0 || video.getScaleX() < 0) {
                    return false;
                }
                if (video != mVideo || mScale == 1f) {
                    mVideo = video;
                    mScale = 1f;
                    mLeft = video.getLeft();
                    mTop = video.getTop();
                }
                video.setPivotX(0);
                video.setPivotY(0);
                mFocusX = toParentX(detector.getFocusX());
                mFocusY = toParentY(detector.getFocusY());
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float fx = toParentX(detector.getFocusX());
                float fy = toParentY(detector.getFocusY());
                // Pan: the picture follows the fingers.
                mLeft += fx - mFocusX;
                mTop += fy - mFocusY;
                mFocusX = fx;
                mFocusY = fy;
                // Zoom around the focus: the point under the fingers stays under them.
                float scale = Math.max(1f, Math.min(MAX_SCALE, mScale * detector.getScaleFactor()));
                float ratio = scale / mScale;
                mLeft = fx - (fx - mLeft) * ratio;
                mTop = fy - (fy - mTop) * ratio;
                mScale = scale;
                apply();
                showLabel(String.format(Locale.US, "%d%%", Math.round(mScale * 100)));
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                if (mScale < SNAP_TO_FIT) {
                    reset(true);
                }
            }
        });
        // No quick-scale (double tap and drag): double tap already seeks.
        mDetector.setQuickScaleEnabled(false);
    }

    /** Whether a pinch owns the current touch sequence. */
    boolean isActive() {
        return mActive;
    }

    /** Whether the video is zoomed in (its view transform belongs to the pinch). */
    boolean isZoomed() {
        return mScale > 1f;
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

    /** Back to the whole picture; {@code showLabel} says "Original" (a pinch out ended there). */
    void reset(boolean showLabel) {
        mActive = false;
        mScale = 1f;
        View video = mVideo;
        if (video != null) {
            video.setScaleX(1f);
            video.setScaleY(1f);
            video.setTranslationX(0f);
            video.setTranslationY(0f);
            if (Build.VERSION.SDK_INT >= 28) {
                video.resetPivot();
            } else {
                video.setPivotX(video.getWidth() / 2f);
                video.setPivotY(video.getHeight() / 2f);
            }
        }
        mHandler.removeCallbacks(mHideLabel);
        if (showLabel) {
            showLabel(mLabel.getContext().getString(R.string.mobile_zoom_fit));
        } else {
            mLabel.setVisibility(View.GONE);
        }
    }

    /** Scale and move the video view, clamped so it never pulls away from an edge it covers. */
    private void apply() {
        View video = mVideo;
        View parent = video != null ? (View) video.getParent() : null;
        if (parent == null) return;
        mLeft = clamp(mLeft, video.getWidth() * mScale, parent.getWidth());
        mTop = clamp(mTop, video.getHeight() * mScale, parent.getHeight());
        video.setScaleX(mScale);
        video.setScaleY(mScale);
        video.setTranslationX(mLeft - video.getLeft());
        video.setTranslationY(mTop - video.getTop());
    }

    /** Wider than the screen: no gap at either edge. Narrower: centred, as at 100%. */
    private static float clamp(float start, float size, float screen) {
        if (size <= screen) {
            return (screen - size) / 2f;
        }
        return Math.max(screen - size, Math.min(0f, start));
    }

    // Touch events arrive in window coordinates; the transform works in the video's parent.
    private float toParentX(float x) {
        return x - parentOrigin()[0];
    }

    private float toParentY(float y) {
        return y - parentOrigin()[1];
    }

    private final int[] mOrigin = new int[2];

    private int[] parentOrigin() {
        View parent = mVideo != null ? (View) mVideo.getParent() : null;
        if (parent != null) {
            parent.getLocationInWindow(mOrigin);
        } else {
            mOrigin[0] = mOrigin[1] = 0;
        }
        return mOrigin;
    }

    private void showLabel(String text) {
        mLabel.setText(text);
        mLabel.setVisibility(View.VISIBLE);
        mLabel.bringToFront(); // above the Leanback overlay, like the swipe indicator
        mHandler.removeCallbacks(mHideLabel);
        mHandler.postDelayed(mHideLabel, LABEL_HIDE_MS);
    }
}
