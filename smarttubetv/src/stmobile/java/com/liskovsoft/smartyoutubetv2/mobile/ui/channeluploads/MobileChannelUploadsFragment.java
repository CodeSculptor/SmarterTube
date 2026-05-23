package com.liskovsoft.smartyoutubetv2.mobile.ui.channeluploads;

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
    private RecyclerView mGrid;
    private ProgressBar mProgressBar;
    private TextView mTitleView;
    private VideoCardAdapter mAdapter;
    private boolean mProgressShowing;

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
        mProgressBar = view.findViewById(R.id.progress_bar);
        mTitleView = view.findViewById(R.id.uploads_title);

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        int cardWidth = getResources().getDisplayMetrics().widthPixels / 2;
        mAdapter = new VideoCardAdapter(cardWidth, mVideoClick, mVideoLongClick);
        mGrid.setLayoutManager(new GridLayoutManager(getContext(), 2));
        mGrid.setAdapter(mAdapter);
        mGrid.addOnScrollListener(mScrollListener);

        mPresenter = ChannelUploadsPresenter.instance(getContext());
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
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
                // Percent-watched markers only; not rendered natively yet.
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
        if (mProgressBar != null) {
            mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void clear() {
        if (mAdapter != null) {
            mAdapter.clear();
        }
    }

    // ----- callbacks -----

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
