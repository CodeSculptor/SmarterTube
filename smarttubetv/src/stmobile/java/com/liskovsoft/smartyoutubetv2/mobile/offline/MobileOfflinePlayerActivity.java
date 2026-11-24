package com.liskovsoft.smartyoutubetv2.mobile.offline;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.io.File;

/**
 * Plays a saved video straight from its file with a plain ExoPlayer view. Deliberately separate from
 * the streaming player: that one always asks the network for stream info before it plays, so it
 * cannot start a video with no connection. Pauses when the app goes to the background; resumes from
 * the last position next time.
 */
public class MobileOfflinePlayerActivity extends AppCompatActivity {
    public static final String EXTRA_VIDEO_ID = "video_id";
    private static final long END_MARGIN_MS = 5_000;

    private SimpleExoPlayer mPlayer;
    private PlayerView mView;
    private OfflineStore mStore;
    private SavedVideo mMeta;
    private boolean mResumeWhenBack;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mStore = OfflinePrefs.store(this);

        String videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        mMeta = videoId != null ? mStore.get(videoId) : null;
        File file = mMeta != null ? mStore.videoFile(videoId) : null;
        if (mMeta == null || file == null || !file.exists() || file.length() == 0) {
            Toast.makeText(this, R.string.mobile_offline_damaged, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mView = new PlayerView(this);
        mView.setBackgroundColor(0xFF000000);
        mView.setKeepContentOnPlayerReset(true);
        setContentView(mView);
        hideSystemBars();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mMeta == null) return;
        if (mPlayer == null) {
            mPlayer = ExoPlayerFactory.newSimpleInstance(this);
            mView.setPlayer(mPlayer);
            mPlayer.addListener(new Player.EventListener() {
                @Override
                public void onPlayerError(ExoPlaybackException error) {
                    Toast.makeText(MobileOfflinePlayerActivity.this, R.string.mobile_offline_damaged, Toast.LENGTH_LONG).show();
                }
            });
            DefaultDataSourceFactory factory = new DefaultDataSourceFactory(this, Util.getUserAgent(this, "SmarterTube"));
            mPlayer.prepare(new ExtractorMediaSource.Factory(factory)
                    .createMediaSource(Uri.fromFile(mStore.videoFile(mMeta.videoId))));
            long resume = mMeta.positionMs;
            long duration = mMeta.durationSec * 1000;
            if (resume > 0 && (duration <= 0 || resume < duration - END_MARGIN_MS)) {
                mPlayer.seekTo(resume);
            }
            mPlayer.setPlayWhenReady(true);
        } else if (mResumeWhenBack) {
            mPlayer.setPlayWhenReady(true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mPlayer != null) {
            mResumeWhenBack = mPlayer.getPlayWhenReady();
            mPlayer.setPlayWhenReady(false);
            savePosition();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPlayer != null) {
            savePosition();
            mPlayer.release();
            mPlayer = null;
        }
    }

    private void savePosition() {
        if (mPlayer == null || mMeta == null) return;
        long pos = mPlayer.getCurrentPosition();
        long dur = mPlayer.getDuration();
        mMeta.positionMs = (dur > 0 && pos >= dur - END_MARGIN_MS) ? 0 : Math.max(0, pos);
        try {
            mStore.put(mMeta);
        } catch (java.io.IOException ignored) {
            // position is a convenience
        }
    }

    private void hideSystemBars() {
        mView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
