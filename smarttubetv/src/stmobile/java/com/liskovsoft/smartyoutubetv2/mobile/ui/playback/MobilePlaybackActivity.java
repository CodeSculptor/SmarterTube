package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;

/**
 * Phone playback host for the stmobile flavor.
 *
 * Reuses the shared {@link PlaybackActivity} (the whole player — engine, controls, PIP —
 * is kept intact) and only adds screen-orientation handling: the TV build is always
 * landscape, but on a phone a 9:16 Short must play portrait or it shows as a thin strip
 * boxed in by black bars. Orientation is chosen from the current video's type.
 *
 * Known limitation: orientation is decided when the activity is created / re-entered, so a
 * single player session that crosses a Short/regular-video boundary (e.g. autoplay) keeps
 * the first video's orientation until the activity is recreated.
 */
public class MobilePlaybackActivity extends PlaybackActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyOrientationForCurrentVideo();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        applyOrientationForCurrentVideo();
    }

    private void applyOrientationForCurrentVideo() {
        Video video = PlaybackPresenter.instance(this).getVideo();

        if (video != null && video.isShorts) {
            // Shorts are 9:16 — lock to portrait so they fill the screen.
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            // Regular videos are landscape; allow either landscape orientation.
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
    }
}
