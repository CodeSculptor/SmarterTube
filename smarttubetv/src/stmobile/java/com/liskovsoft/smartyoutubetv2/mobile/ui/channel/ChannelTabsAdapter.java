package com.liskovsoft.smartyoutubetv2.mobile.ui.channel;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.mobile.ui.browse.VideoCardAdapter;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewPager2 adapter that turns the channel's emitted {@link VideoGroup}s into swipeable
 * tabs — each page is a vertical 2-column grid of one group's videos (the same grid the
 * Channel-uploads screen uses). Upstream already delivers the channel's content tabs
 * (Videos / Shorts / Live / Playlists …) as separate groups, so a tab is simply a group.
 *
 * State is held here, keyed by group id — mirroring
 * {@link com.liskovsoft.smartyoutubetv2.mobile.ui.browse.ShelfAdapter} — so the host
 * fragment can route the presenter's per-group continuations straight to the right page
 * without resorting to child fragments. A {@link TabLayoutMediator}-style title lookup is
 * exposed via {@link #getTitle(int)}.
 */
public class ChannelTabsAdapter extends RecyclerView.Adapter<ChannelTabsAdapter.ViewHolder> {
    public interface OnTabScrollEnd {
        void onTabScrollEnd(Video lastVideo);
    }

    private int mWidthPx;
    private int mSpan;
    private final VideoCardAdapter.OnVideoAction mClick;
    private final VideoCardAdapter.OnVideoAction mLongClick;
    private final OnTabScrollEnd mScrollEnd;
    private final List<Integer> mIds = new ArrayList<>();
    private final List<String> mTitles = new ArrayList<>();
    private final List<VideoCardAdapter> mAdapters = new ArrayList<>();
    // The source VideoGroup per tab — kept so the scroll-end callback can pick the last
    // video off the *group*, where the Video↔Group back-reference is guaranteed.
    private final List<VideoGroup> mGroups = new ArrayList<>();
    private final RecyclerView.RecycledViewPool mPool = new RecyclerView.RecycledViewPool();

    /**
     * @param widthPx the grid's width; each tab's card width is {@code widthPx / spanFor(tab)}
     * @param span the base column count ({@code R.integer.mobile_grid_span})
     */
    public ChannelTabsAdapter(int widthPx, int span, VideoCardAdapter.OnVideoAction click,
                              VideoCardAdapter.OnVideoAction longClick, OnTabScrollEnd scrollEnd) {
        mWidthPx = widthPx;
        mSpan = span;
        mClick = click;
        mLongClick = longClick;
        mScrollEnd = scrollEnd;
    }

    /**
     * A continuation of an existing group carries the full cumulative video list, so its
     * page's grid gets only the new tail appended (keeping scroll position); an unseen
     * group id becomes a new tab+page.
     */
    public void appendGroup(VideoGroup group) {
        int idx = mIds.indexOf(group.getId());
        if (idx >= 0) {
            VideoCardAdapter adapter = mAdapters.get(idx);
            List<Video> all = group.getVideos();
            int oldCount = adapter.getItemCount();
            if (all != null && all.size() > oldCount) {
                adapter.appendVideos(all.subList(oldCount, all.size()));
            } else if (all != null && all.size() < oldCount) {
                // Shrunk (a refresh, not a continuation) — full replace.
                adapter.setVideos(all);
            }
            mGroups.set(idx, group);
        } else {
            insertTab(mAdapters.size(), group, group.getTitle());
        }
    }

    private void insertTab(int position, VideoGroup group, String title) {
        position = Math.max(0, Math.min(position, mAdapters.size()));
        VideoCardAdapter adapter = new VideoCardAdapter(cardWidthFor(group), mClick, mLongClick);
        adapter.setAdaptiveStyles(true);
        adapter.setVerticalShorts(group.isShorts());
        adapter.setVideos(group.getVideos());
        mIds.add(position, group.getId());
        mTitles.add(position, title != null ? title : "");
        mAdapters.add(position, adapter);
        mGroups.add(position, group);
        notifyItemInserted(position);
    }

    /**
     * Swap the content of the tab at {@code index} for {@code group} (e.g. a re-sorted Videos
     * tab). The tab keeps its title; it takes the new group's id so continuations of the new
     * group keep landing on it.
     */
    public void replaceTab(int index, VideoGroup group) {
        if (index < 0 || index >= mAdapters.size()) {
            return;
        }
        mIds.set(index, group.getId());
        mGroups.set(index, group);
        VideoCardAdapter adapter = mAdapters.get(index);
        adapter.setCardWidth(cardWidthFor(group));
        adapter.setVerticalShorts(group.isShorts());
        adapter.setVideos(group.getVideos());
        notifyItemChanged(index);
    }

    /**
     * Put {@code group} at {@code position} as its own tab titled {@code title}: replaces the
     * tab with the same id if there is one (a repeated in-channel search), else inserts it.
     * Returns the tab's index.
     */
    public int putTab(int position, VideoGroup group, String title) {
        int idx = mIds.indexOf(group.getId());
        if (idx >= 0) {
            mTitles.set(idx, title != null ? title : "");
            replaceTab(idx, group);
            return idx;
        }
        insertTab(position, group, title);
        return Math.max(0, Math.min(position, mAdapters.size() - 1));
    }

    public void removeTabById(int id) {
        int idx = mIds.indexOf(id);
        if (idx < 0) {
            return;
        }
        mIds.remove(idx);
        mTitles.remove(idx);
        mAdapters.remove(idx);
        mGroups.remove(idx);
        notifyItemRemoved(idx);
    }

    public int indexOfId(int id) {
        return mIds.indexOf(id);
    }

    public int getId(int position) {
        return position >= 0 && position < mIds.size() ? mIds.get(position) : -1;
    }

    /** Re-flow every page after a rotation: new base span and grid width. */
    public void setSize(int widthPx, int span) {
        if (mWidthPx == widthPx && mSpan == span) {
            return;
        }
        mWidthPx = widthPx;
        mSpan = span;
        for (int i = 0; i < mAdapters.size(); i++) {
            mAdapters.get(i).setCardWidth(cardWidthFor(mGroups.get(i)));
        }
        notifyItemRangeChanged(0, mAdapters.size());
    }

    /** Shorts tabs get half again as many (narrower, 9:16) columns, like the YouTube app. */
    private int spanFor(VideoGroup group) {
        return group != null && group.isShorts() ? Math.max(3, mSpan * 3 / 2) : mSpan;
    }

    private int cardWidthFor(VideoGroup group) {
        return mWidthPx / spanFor(group);
    }

    public void removeVideos(List<Video> videos) {
        for (VideoCardAdapter adapter : mAdapters) {
            adapter.remove(videos);
        }
    }

    /** Forward a presenter ACTION_SYNC (percent-watched refresh) to every nested card adapter. */
    public void syncVideos(List<Video> videos) {
        for (VideoCardAdapter adapter : mAdapters) {
            adapter.sync(videos);
        }
    }

    public String getTitle(int position) {
        return position >= 0 && position < mTitles.size() ? mTitles.get(position) : "";
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
                .inflate(R.layout.mobile_channel_tab_page, parent, false);
        ViewHolder holder = new ViewHolder(view);
        holder.grid.setLayoutManager(new GridLayoutManager(parent.getContext(), mSpan));
        holder.grid.setRecycledViewPool(mPool);
        if (mScrollEnd != null) {
            holder.grid.addOnScrollListener(new VerticalScrollEndListener(holder));
        }
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Pages are recycled across tabs, so the column count is per bind, not per holder.
        ((GridLayoutManager) holder.grid.getLayoutManager()).setSpanCount(spanFor(mGroups.get(position)));
        holder.grid.setAdapter(mAdapters.get(position));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final RecyclerView grid;

        ViewHolder(View itemView) {
            super(itemView);
            grid = (RecyclerView) itemView;
        }
    }

    /**
     * Fires the tab-scroll-end callback as the grid nears its bottom. The host fragment
     * calls {@code presenter.onScrollEnd(lastVideo)}; the presenter de-dupes overlapping
     * fetches, so the once-per-plateau throttle here is just to keep logs sane.
     */
    private class VerticalScrollEndListener extends RecyclerView.OnScrollListener {
        private final ViewHolder mHolder;
        private int mLastFiredAt = -1;

        VerticalScrollEndListener(ViewHolder holder) {
            mHolder = holder;
        }

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            if (dy <= 0) {
                return;
            }
            RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
            if (!(lm instanceof GridLayoutManager)) {
                return;
            }
            VideoCardAdapter adapter = (VideoCardAdapter) recyclerView.getAdapter();
            if (adapter == null || adapter.getItemCount() == 0) {
                return;
            }
            int lastVisible = ((GridLayoutManager) lm).findLastVisibleItemPosition();
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
            if (mLastFiredAt == adapter.getItemCount()) {
                return;
            }
            mLastFiredAt = adapter.getItemCount();
            Video last = videos.get(videos.size() - 1);
            if (last.getGroup() == null) {
                // The presenter looks up the source group off the Video itself; reattach
                // it so continuation lookup works on items that came in detached.
                last.setGroup(group);
            }
            mScrollEnd.onTabScrollEnd(last);
        }
    }
}
