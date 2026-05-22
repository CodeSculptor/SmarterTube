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
 * cards — used for ROW-type sections such as Home, Trending and Music.
 */
class ShelfAdapter extends RecyclerView.Adapter<ShelfAdapter.ViewHolder> {
    private final int mCardWidth;
    private final VideoCardAdapter.OnVideoAction mClick;
    private final VideoCardAdapter.OnVideoAction mLongClick;
    private final List<Integer> mIds = new ArrayList<>();
    private final List<String> mTitles = new ArrayList<>();
    private final List<VideoCardAdapter> mAdapters = new ArrayList<>();
    private final RecyclerView.RecycledViewPool mPool = new RecyclerView.RecycledViewPool();

    ShelfAdapter(int cardWidth, VideoCardAdapter.OnVideoAction click, VideoCardAdapter.OnVideoAction longClick) {
        mCardWidth = cardWidth;
        mClick = click;
        mLongClick = longClick;
    }

    /**
     * A continuation of an existing group carries the full cumulative video list, so an
     * existing shelf is simply replaced; an unseen group id becomes a new shelf.
     */
    void appendGroup(VideoGroup group) {
        int idx = mIds.indexOf(group.getId());
        if (idx >= 0) {
            mAdapters.get(idx).setVideos(group.getVideos());
            notifyItemChanged(idx);
        } else {
            VideoCardAdapter adapter = new VideoCardAdapter(mCardWidth, mClick, mLongClick);
            adapter.setVideos(group.getVideos());
            mIds.add(group.getId());
            mTitles.add(group.getTitle() != null ? group.getTitle() : "");
            mAdapters.add(adapter);
            notifyItemInserted(mAdapters.size() - 1);
        }
    }

    void removeVideos(List<Video> videos) {
        for (VideoCardAdapter adapter : mAdapters) {
            adapter.remove(videos);
        }
    }

    void clear() {
        mIds.clear();
        mTitles.clear();
        mAdapters.clear();
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
}
