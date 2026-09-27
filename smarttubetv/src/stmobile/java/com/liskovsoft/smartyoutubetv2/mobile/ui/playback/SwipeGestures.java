package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Swipe gestures in the landscape player (#48): a vertical swipe on the right half changes the
 * media volume, on the left half this window's brightness, with a small level indicator.
 *
 * The player shows its controls the moment a finger goes down (upstream's tickle), so a touch
 * that might become a swipe holds its DOWN back. If it turns into a vertical drag it's a swipe and
 * nothing else sees it; otherwise (a tap, a double tap, a sideways drag) the held DOWN and the
 * rest of the gesture are replayed through the normal touch path, with their original timestamps,
 * so tap-to-show, double-tap seek and Tap to pause behave exactly as before.
 */
final class SwipeGestures {
    /** Re-dispatches an event through the player's normal (non-swipe) touch handling. */
    interface Router {
        boolean route(MotionEvent event);
    }

    private static final int IDLE = 0;
    private static final int PENDING = 1;
    private static final int SWIPING = 2;
    private static final float FULL_RANGE_FRACTION = 0.8f; // swipe this share of the height for 0 → 100 %
    private static final float MIN_BRIGHTNESS = 0.01f;
    private static final int INDICATOR_HIDE_MS = 800;

    private final Activity mActivity;
    private final AudioManager mAudio;
    private final View mIndicator;
    private final ImageView mIndicatorIcon;
    private final ProgressBar mIndicatorLevel;
    private final int mSlop;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHideIndicator;

    private int mState = IDLE;
    private MotionEvent mDown;
    private boolean mVolume;
    private float mStartLevel;
    private int mAreaHeight;

    /** Returns null if the indicator isn't in the player layout. */
    static SwipeGestures create(Activity activity) {
        View indicator = activity.findViewById(R.id.mobile_swipe_indicator);
        return indicator != null ? new SwipeGestures(activity, indicator) : null;
    }

    private SwipeGestures(Activity activity, View indicator) {
        mActivity = activity;
        mAudio = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        mIndicator = indicator;
        mIndicatorIcon = indicator.findViewById(R.id.mobile_swipe_indicator_icon);
        mIndicatorLevel = indicator.findViewById(R.id.mobile_swipe_indicator_level);
        mSlop = ViewConfiguration.get(activity).getScaledTouchSlop() * 2;
        mHideIndicator = () -> mIndicator.setVisibility(View.GONE);
    }

    /** Whether a touch sequence is being held back or swiped. */
    boolean isActive() {
        return mState != IDLE;
    }

    /** A DOWN in the swipe area: hold it until the gesture shows what it is. */
    boolean start(MotionEvent down, View area) {
        reset();
        mDown = MotionEvent.obtain(down);
        mVolume = down.getX() >= area.getWidth() / 2f;
        mAreaHeight = area.getHeight();
        mState = PENDING;
        return true;
    }

    /** The rest of a held or swiping gesture. */
    boolean handle(MotionEvent event, Router router) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE: {
                if (mState == SWIPING) {
                    applyLevel(mStartLevel + (mDown.getY() - event.getY()) / (mAreaHeight * FULL_RANGE_FRACTION));
                    return true;
                }
                float dx = Math.abs(event.getX() - mDown.getX());
                float dy = Math.abs(event.getY() - mDown.getY());
                if (dy > mSlop && dy > dx) {
                    mState = SWIPING;
                    mStartLevel = currentLevel();
                    showIndicator(mStartLevel);
                    return true;
                }
                if (dx > mSlop) {
                    return replay(event, router); // a sideways drag: not ours
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (mState == SWIPING) {
                    reset();
                    mHandler.postDelayed(mHideIndicator, INDICATOR_HIDE_MS);
                    return true;
                }
                return replay(event, router); // a tap (or the second tap of a double tap)
            case MotionEvent.ACTION_POINTER_DOWN:
                if (mState == PENDING) {
                    return replay(event, router); // multi-touch isn't a swipe
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                boolean swiping = mState == SWIPING;
                reset();
                if (swiping) {
                    mHandler.postDelayed(mHideIndicator, INDICATOR_HIDE_MS);
                }
                return true;
            default:
                return true;
        }
    }

    /** Hand the held DOWN and this event to the normal touch path. */
    private boolean replay(MotionEvent event, Router router) {
        MotionEvent down = mDown;
        mDown = null;
        mState = IDLE;
        router.route(down);
        down.recycle();
        return router.route(event);
    }

    private void reset() {
        mState = IDLE;
        if (mDown != null) {
            mDown.recycle();
            mDown = null;
        }
    }

    private float currentLevel() {
        if (mVolume) {
            int max = mAudio != null ? mAudio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) : 0;
            return max > 0 ? (float) mAudio.getStreamVolume(AudioManager.STREAM_MUSIC) / max : 0f;
        }
        float window = mActivity.getWindow().getAttributes().screenBrightness;
        if (window >= 0) {
            return window;
        }
        // Following the system setting so far: start from it.
        int system = Settings.System.getInt(mActivity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
        return system / 255f;
    }

    private void applyLevel(float level) {
        level = Math.max(0f, Math.min(1f, level));
        if (mVolume) {
            if (mAudio != null && !mAudio.isVolumeFixed()) {
                int max = mAudio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int index = Math.round(level * max);
                if (index != mAudio.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                    mAudio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0); // our own indicator, no system UI
                }
                level = max > 0 ? (float) index / max : level;
            }
        } else {
            WindowManager.LayoutParams attrs = mActivity.getWindow().getAttributes();
            attrs.screenBrightness = Math.max(MIN_BRIGHTNESS, level);
            mActivity.getWindow().setAttributes(attrs);
        }
        showIndicator(level);
    }

    private void showIndicator(float level) {
        mHandler.removeCallbacks(mHideIndicator);
        mIndicatorIcon.setImageResource(mVolume ? R.drawable.ic_swipe_volume : R.drawable.ic_swipe_brightness);
        mIndicatorLevel.setProgress(Math.round(level * 100));
        mIndicator.setVisibility(View.VISIBLE);
        mIndicator.bringToFront();
    }
}
