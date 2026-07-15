package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.MotionEvent;

import androidx.fragment.app.Fragment;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;

/**
 * Phone playback host for the stmobile flavor.
 *
 * Reuses the shared {@link PlaybackActivity} (the whole player — engine, controls, PIP —
 * is kept intact) and adds the phone behaviors: screen orientation by video type (Shorts lock
 * portrait, regular videos rotate freely for the upright strip layout) and touch-friendly
 * overlay handling (a tap on the faded player only reveals the controls — see
 * {@link MobilePlaybackFragment#interceptPlayerTouch}).
 *
 * Known limitation: orientation is decided when the activity is created / re-entered, so a
 * single player session that crosses a Short/regular-video boundary (e.g. autoplay) keeps
 * the first video's orientation until the activity is recreated.
 */
public class MobilePlaybackActivity extends PlaybackActivity {
    private MobilePlaybackFragment mMobileFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Fragment fragment =
                getSupportFragmentManager().findFragmentByTag(getString(R.string.playback_tag));
        if (fragment instanceof MobilePlaybackFragment) {
            mMobileFragment = (MobilePlaybackFragment) fragment;
        }

        applyOrientationForCurrentVideo();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        applyOrientationForCurrentVideo();
    }

    /**
     * Phone Back UX (issue #23): pressing Back leaves the video for good.
     *
     * The shared {@link PlaybackActivity#finish()} keeps the player engine alive on Back when
     * background audio is on (the phone default, {@code BACKGROUND_MODE_SOUND}) and navigates to
     * the player's "parent view". That left audio playing after Back and parked a still-running
     * player activity beneath the channel page — pressing Back on the channel then revealed the
     * live player again, a channel↔player loop. Here we instead stop playback and finish the
     * player ({@link #finishReally()}), so Back drops back to the previous screen with no lingering
     * audio and nothing to loop into.
     *
     * Left to the base class:
     * - PIP (either already in PIP, or the user picked {@code BACKGROUND_MODE_PIP}) — Back should
     *   enter/keep PIP, an explicit opt-in feature, not be hijacked into a stop.
     * - Home / lock-screen background audio — that runs through onUserLeaveHint/onStop, a different
     *   path that this override doesn't touch, so listening with the screen off still works.
     */
    @Override
    public void onBackPressed() {
        if (isInPipMode() || getPlayerData().getBackgroundMode() == PlayerData.BACKGROUND_MODE_PIP) {
            super.onBackPressed();
            return;
        }

        finishReally();
    }

    /**
     * PIP fix (#33): don't relaunch Home when the player drops into PIP.
     *
     * The shared base launches the parent activity here. On the phone, Home
     * ({@code MobileBrowseActivity}) is {@code singleTask} and already sits at the root of this
     * task, so it is revealed automatically the moment the player pins to a PIP window. Relaunching
     * it races with that task-pinning transition and can clear the player out of the task — the
     * player is destroyed, its engine released, and the pop-up shows Home instead of the video.
     *
     * We still do the one thing {@code startParentView} does that matters here: drop the player
     * from the ViewManager stack, so returning from PIP resolves correctly.
     */
    @Override
    protected void startParentViewOnPip() {
        getViewManager().removeTop(this);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // While the overlay is faded out its buttons are still hit-testable (Leanback hides by
        // alpha, not visibility) — consume the tap so it only reveals the controls instead of
        // pressing an invisible button.
        if (mMobileFragment != null && mMobileFragment.interceptPlayerTouch(event)) {
            return true;
        }

        return super.dispatchTouchEvent(event);
    }

    private void applyOrientationForCurrentVideo() {
        Video video = PlaybackPresenter.instance(this).getVideo();

        if (video != null && video.isShorts) {
            // Shorts are 9:16 — lock to portrait so they fill the screen.
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            // Regular videos rotate freely: landscape = full-screen, upright = 16:9 strip with
            // the up-next panel below (MobilePlaybackFragment.applyMobileLayout). The manifest
            // lists "orientation" in configChanges, so rotation never recreates the activity
            // (no rebuffer) — the fragment just re-applies constraints.
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        }
    }
}
