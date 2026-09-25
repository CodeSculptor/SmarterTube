package com.liskovsoft.smartyoutubetv2.mobile.ui.channeluploads;

import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.mobile.ui.browse.VideoCardAdapter;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Native portrait Channel-uploads screen. Implements {@link ChannelUploadsView} and is
 * driven by the existing {@link ChannelUploadsPresenter} unchanged. One continuous
 * 2-column grid of videos with scroll-to-bottom pagination — no shelves (the upstream
 * model is a single uploads stream, not multiple rows).
 */
public class MobileChannelUploadsFragment extends Fragment implements ChannelUploadsView {
    private ChannelUploadsPresenter mPresenter;
    private boolean mFragmentCreated = true;
    private RecyclerView mGrid;
    private SwipeRefreshLayout mSwipeRefresh;
    private ProgressBar mProgressBar;
    private TextView mTitleView;
    private VideoCardAdapter mAdapter;
    private boolean mProgressShowing;
    private boolean mSwipeRefreshing;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.mobile_channel_uploads_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mGrid = view.findViewById(R.id.uploads_grid);
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mProgressBar = view.findViewById(R.id.progress_bar);
        mTitleView = view.findViewById(R.id.uploads_title);

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        int span = getResources().getInteger(R.integer.mobile_grid_span);
        int cardWidth = getResources().getDisplayMetrics().widthPixels / span;
        mAdapter = new VideoCardAdapter(cardWidth, mVideoClick, mVideoLongClick);
        mGrid.setLayoutManager(new GridLayoutManager(getContext(), span));
        mGrid.setAdapter(mAdapter);
        mGrid.addOnScrollListener(mScrollListener);

        mSwipeRefresh.setColorSchemeResources(R.color.brand_accent);
        mSwipeRefresh.setProgressBackgroundColorSchemeResource(R.color.mobile_surface);
        mSwipeRefresh.setOnRefreshListener(() -> {
            if (mPresenter != null) {
                mSwipeRefreshing = true;
                mPresenter.refresh();
            } else {
                stopSwipeRefresh();
            }
        });

        mPresenter = ChannelUploadsPresenter.instance(getContext());
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Back from the player: the presenter pushes changed percent-watched as ACTION_SYNC.
        // Skip the first resume (right after creation) so it doesn't consume the changes
        // before the screen underneath gets them (same guard as MobileSearchFragment).
        if (!mFragmentCreated && mPresenter != null) {
            mPresenter.onViewResumed();
        }
        mFragmentCreated = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPresenter != null) {
            mPresenter.onViewDestroyed();
        }
    }

    // ----- ChannelUploadsView -----

    @Override
    public void update(VideoGroup group) {
        if (group == null || mAdapter == null) {
            return;
        }
        switch (group.getAction()) {
            case VideoGroup.ACTION_REPLACE:
                mAdapter.clear();
                if (!group.isEmpty()) {
                    mAdapter.setVideos(group.getVideos());
                }
                break;
            case VideoGroup.ACTION_REMOVE:
            case VideoGroup.ACTION_REMOVE_AUTHOR:
                mAdapter.remove(group.getVideos());
                break;
            case VideoGroup.ACTION_SYNC:
                // Percent-watched refresh (e.g. back from the player): redraw the watched bars.
                mAdapter.sync(group.getVideos());
                break;
            default: // ACTION_APPEND / ACTION_PREPEND
                if (!group.isEmpty()) {
                    // A continuation carries the full cumulative list, so replace.
                    mAdapter.setVideos(group.getVideos());
                }
                break;
        }
        if (mTitleView != null && TextUtils.isEmpty(mTitleView.getText())
                && !TextUtils.isEmpty(group.getTitle())) {
            mTitleView.setText(group.getTitle());
        }
    }

    @Override
    public void showProgressBar(boolean show) {
        mProgressShowing = show;
        // While pull-to-refresh is active the SwipeRefreshLayout spinner stands in for
        // the centre bar; don't show both.
        if (mProgressBar != null) {
            mProgressBar.setVisibility(show && !mSwipeRefreshing ? View.VISIBLE : View.GONE);
        }
        if (!show) {
            stopSwipeRefresh();
        }
    }

    private void stopSwipeRefresh() {
        mSwipeRefreshing = false;
        if (mSwipeRefresh != null) {
            mSwipeRefresh.setRefreshing(false);
        }
    }

    @Override
    public void clear() {
        if (mAdapter != null) {
            mAdapter.clear();
        }
    }

    // ----- callbacks -----

    // Hosting activity declares configChanges="orientation|..." so it is NOT recreated on
    // rotation; re-read the grid span (values-sw600dp-land widens it) and resize cards.
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int span = getResources().getInteger(R.integer.mobile_grid_span);
        if (mGrid != null && mGrid.getLayoutManager() instanceof GridLayoutManager) {
            ((GridLayoutManager) mGrid.getLayoutManager()).setSpanCount(span);
        }
        if (mAdapter != null) {
            mAdapter.setCardWidth(getResources().getDisplayMetrics().widthPixels / span);
        }
    }

    private final VideoCardAdapter.OnVideoAction mVideoClick = video -> {
        if (mPresenter != null) {
            mPresenter.onVideoItemSelected(video);
            mPresenter.onVideoItemClicked(video);
        }
    };

    private final VideoCardAdapter.OnVideoAction mVideoLongClick = video -> {
        if (mPresenter != null) {
            mPresenter.onVideoItemLongClicked(video);
        }
    };

    private final RecyclerView.OnScrollListener mScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            if (dy <= 0 || mProgressShowing || mAdapter == null) {
                return;
            }
            RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
            if (!(lm instanceof GridLayoutManager)) {
                return;
            }
            int lastVisible = ((GridLayoutManager) lm).findLastVisibleItemPosition();
            if (lastVisible >= mAdapter.getItemCount() - 4) {
                Video last = mAdapter.getLast();
                if (last != null && mPresenter != null) {
                    mPresenter.onScrollEnd(last);
                }
            }
        }
    };
}
