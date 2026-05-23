package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Vertical list of shelves. Each shelf is a titled, horizontally-scrolling row of video
 * cards — used for ROW-type sections such as Home, Trending and Music, and for the
 * native Channel screen.
 *
 * Per-shelf horizontal pagination is opt-in: when a non-null {@link OnShelfScrollEnd}
 * is supplied, each shelf fires the callback with its current last video as the user
 * nears the right edge — letting the host fragment call {@code presenter.onScrollEnd}.
 */
public class ShelfAdapter extends RecyclerView.Adapter<ShelfAdapter.ViewHolder> {
    public interface OnShelfScrollEnd {
        void onShelfScrollEnd(Video lastVideo);
    }

    private final int mCardWidth;
    private final VideoCardAdapter.OnVideoAction mClick;
    private final VideoCardAdapter.OnVideoAction mLongClick;
    private final OnShelfScrollEnd mScrollEnd;
    private final List<Integer> mIds = new ArrayList<>();
    private final List<String> mTitles = new ArrayList<>();
    private final List<VideoCardAdapter> mAdapters = new ArrayList<>();
    // The source VideoGroup per shelf — kept so the scroll-end callback can pick the
    // last video off the *group*, where the Video↔Group back-reference is guaranteed
    // (the cards' raw Video objects sometimes lose it on Home's mixed-source shelves).
    private final List<VideoGroup> mGroups = new ArrayList<>();
    private final RecyclerView.RecycledViewPool mPool = new RecyclerView.RecycledViewPool();

    public ShelfAdapter(int cardWidth, VideoCardAdapter.OnVideoAction click,
                        VideoCardAdapter.OnVideoAction longClick) {
        this(cardWidth, click, longClick, null);
    }

    public ShelfAdapter(int cardWidth, VideoCardAdapter.OnVideoAction click,
                        VideoCardAdapter.OnVideoAction longClick, OnShelfScrollEnd scrollEnd) {
        mCardWidth = cardWidth;
        mClick = click;
        mLongClick = longClick;
        mScrollEnd = scrollEnd;
    }

    /**
     * A continuation of an existing group carries the full cumulative video list, so an
     * existing shelf is simply replaced; an unseen group id becomes a new shelf.
     */
    public void appendGroup(VideoGroup group) {
        int idx = mIds.indexOf(group.getId());
        if (idx >= 0) {
            mAdapters.get(idx).setVideos(group.getVideos());
            mGroups.set(idx, group);
            notifyItemChanged(idx);
        } else {
            VideoCardAdapter adapter = new VideoCardAdapter(mCardWidth, mClick, mLongClick);
            adapter.setVideos(group.getVideos());
            mIds.add(group.getId());
            mTitles.add(group.getTitle() != null ? group.getTitle() : "");
            mAdapters.add(adapter);
            mGroups.add(group);
            notifyItemInserted(mAdapters.size() - 1);
        }
    }

    public void removeVideos(List<Video> videos) {
        for (VideoCardAdapter adapter : mAdapters) {
            adapter.remove(videos);
        }
    }

    public void clear() {
        mIds.clear();
        mTitles.clear();
        mAdapters.clear();
        mGroups.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mAdapters.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.mobile_shelf, parent, false);
        ViewHolder holder = new ViewHolder(view);
        holder.list.setLayoutManager(
                new LinearLayoutManager(parent.getContext(), LinearLayoutManager.HORIZONTAL, false));
        holder.list.setRecycledViewPool(mPool);
        if (mScrollEnd != null) {
            holder.list.addOnScrollListener(new HorizontalScrollEndListener(holder));
        }
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.title.setText(mTitles.get(position));
        holder.list.setAdapter(mAdapters.get(position));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final RecyclerView list;

        ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.shelf_title);
            list = itemView.findViewById(R.id.shelf_list);
        }
    }

    /**
     * Fires the shelf-scroll-end callback when the horizontal list nears its right edge.
     * The host fragment calls {@code presenter.onScrollEnd(lastVideo)}; the presenter
     * de-dupes overlapping fetches, so calling on every near-edge scroll is safe.
     */
    private class HorizontalScrollEndListener extends RecyclerView.OnScrollListener {
        private final ViewHolder mHolder;
        // Throttle within the same item-count plateau — fire once per shelf size so we
        // don't spam the presenter while a continuation is still in flight. The presenter
        // ultimately de-dupes, but skipping the redundant calls keeps logs sane.
        private int mLastFiredAt = -1;

        HorizontalScrollEndListener(ViewHolder holder) {
            mHolder = holder;
        }

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            if (dx <= 0) {
                return;
            }
            RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
            if (!(lm instanceof LinearLayoutManager)) {
                return;
            }
            VideoCardAdapter adapter = (VideoCardAdapter) recyclerView.getAdapter();
            if (adapter == null || adapter.getItemCount() == 0) {
                return;
            }
            int lastVisible = ((LinearLayoutManager) lm).findLastVisibleItemPosition();
            if (lastVisible < adapter.getItemCount() - 4) {
                return;
            }
            int position = mHolder.getAdapterPosition();
            if (position < 0 || position >= mGroups.size()) {
                return;
            }
            VideoGroup group = mGroups.get(position);
            List<Video> videos = group != null ? group.getVideos() : null;
            if (videos == null || videos.isEmpty()) {
                return;
            }
            Video last = videos.get(videos.size() - 1);
            if (mLastFiredAt == adapter.getItemCount()) {
                return;
            }
            mLastFiredAt = adapter.getItemCount();
            if (last.getGroup() == null) {
                // The presenter looks up the source group off the Video itself; reattach
                // it so continuation lookup works on shelves whose items came in detached.
                last.setGroup(group);
            }
            mScrollEnd.onShelfScrollEnd(last);
        }
    }
}
