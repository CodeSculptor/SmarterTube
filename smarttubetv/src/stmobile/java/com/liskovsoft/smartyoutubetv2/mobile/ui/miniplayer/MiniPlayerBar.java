package com.liskovsoft.smartyoutubetv2.mobile.ui.miniplayer;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * The bar docked under a browse screen while the player is minimised (#96). It takes layout space
 * (the screen's content is re-parented into a vertical container above it) so no screen needs
 * bottom padding. State comes live from {@link PlaybackPresenter}; a 500 ms poll refreshes it
 * while the host activity is resumed.
 */
public final class MiniPlayerBar {
    private static final long POLL_MS = 500;

    private final Activity mActivity;
    private final View mRoot;
    private final ImageView mPoster;
    private final TextView mTitle;
    private final TextView mChannel;
    private final ProgressBar mProgress;
    private final ImageButton mPlayPause;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mPoll = this::poll;
    private String mPosterUrl;

    private MiniPlayerBar(Activity activity, View root) {
        mActivity = activity;
        mRoot = root;
        mPoster = root.findViewById(R.id.mini_player_poster);
        mTitle = root.findViewById(R.id.mini_player_title);
        mChannel = root.findViewById(R.id.mini_player_channel);
        mProgress = root.findViewById(R.id.mini_player_progress);
        mPlayPause = root.findViewById(R.id.mini_player_play_pause);
        root.setOnClickListener(v -> expand());
        mPlayPause.setOnClickListener(v -> togglePlayback());
        root.findViewById(R.id.mini_player_close).setOnClickListener(v -> close());
    }

    /** Re-parent the activity's content into [content, bar] and return the bar. */
    public static MiniPlayerBar attach(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) {
            return null;
        }
        View screen = content.getChildAt(0);
        content.removeView(screen);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(screen, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        View bar = LayoutInflater.from(activity).inflate(R.layout.mobile_mini_player, column, false);
        column.addView(bar);
        content.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new MiniPlayerBar(activity, bar);
    }

    public void onResume() {
        mHandler.removeCallbacks(mPoll);
        poll();
    }

    public void onPause() {
        mHandler.removeCallbacks(mPoll);
    }

    private void poll() {
        refresh();
        mHandler.postDelayed(mPoll, POLL_MS);
    }

    private void refresh() {
        PlaybackPresenter presenter = PlaybackPresenter.instance(mActivity);
        PlaybackView view = presenter.getView();
        Video video = presenter.getVideo();
        boolean initialized = view != null && view.isEngineInitialized();
        boolean minimised = MiniPlayerState.isMinimised();

        if (MiniPlayerPolicy.isStale(minimised, view != null, initialized)) {
            MiniPlayerState.setMinimised(false);
            minimised = false;
        }
        if (!MiniPlayerPolicy.shouldShowBar(minimised, video != null, view != null, initialized)) {
            mRoot.setVisibility(View.GONE);
            return;
        }

        mRoot.setVisibility(View.VISIBLE);
        String title = video.getTitleFull() != null ? video.getTitleFull() : video.getTitle();
        mTitle.setText(title != null ? title : "");
        mChannel.setText(video.getAuthor() != null ? video.getAuthor() : "");
        mPlayPause.setImageResource(view.getPlayWhenReady() ? R.drawable.ic_mini_pause : R.drawable.ic_mini_play);
        long duration = view.getDurationMs();
        mProgress.setProgress(duration > 0 ? (int) (1000L * view.getPositionMs() / duration) : 0);

        String url = video.cardImageUrl != null ? video.cardImageUrl : video.bgImageUrl;
        if (url != null && !url.equals(mPosterUrl)) {
            mPosterUrl = url;
            Glide.with(mActivity).load(url).centerCrop().into(mPoster);
        }
    }

    private void expand() {
        // The player task is brought forward; the player clears the minimised flag when it resumes.
        MiniPlayerTasks.expandPlayerSliding(mActivity);
    }

    private void togglePlayback() {
        PlaybackView view = PlaybackPresenter.instance(mActivity).getView();
        if (view != null) {
            view.setPlayWhenReady(!view.getPlayWhenReady());
            refresh();
        }
    }

    private void close() {
        PlaybackView view = PlaybackPresenter.instance(mActivity).getView();
        MiniPlayerState.setMinimised(false);
        mRoot.setVisibility(View.GONE);
        if (view != null) {
            // Same pattern as dismissing PiP: unblock so the engine is released, then finish.
            view.blockEngine(false);
            view.finishReally();
        }
        // finishReally() pops the view stack as if this screen's parent were closing; put this
        // screen back as the top.
        ViewManager.instance(mActivity).addTop(mActivity);
    }
}
