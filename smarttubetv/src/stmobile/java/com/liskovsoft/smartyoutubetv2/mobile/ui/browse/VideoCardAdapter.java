package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Flat list of video cards. Used both for grid sections and (horizontally) inside shelves.
 * The card width is fixed per adapter; the thumbnail keeps a 16:9 ratio.
 */
class VideoCardAdapter extends RecyclerView.Adapter<VideoCardAdapter.ViewHolder> {
    interface OnVideoAction {
        void onVideo(Video video);
    }

    private final int mCardWidth;
    private final OnVideoAction mClick;
    private final OnVideoAction mLongClick;
    private final List<Video> mVideos = new ArrayList<>();

    VideoCardAdapter(int cardWidth, OnVideoAction click, OnVideoAction longClick) {
        mCardWidth = cardWidth;
        mClick = click;
        mLongClick = longClick;
    }

    void setVideos(List<Video> videos) {
        mVideos.clear();
        if (videos != null) {
            mVideos.addAll(videos);
        }
        notifyDataSetChanged();
    }

    void clear() {
        mVideos.clear();
        notifyDataSetChanged();
    }

    void remove(List<Video> videos) {
        if (videos != null && mVideos.removeAll(videos)) {
            notifyDataSetChanged();
        }
    }

    Video getLast() {
        return mVideos.isEmpty() ? null : mVideos.get(mVideos.size() - 1);
    }

    @Override
    public int getItemCount() {
        return mVideos.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.mobile_video_card, parent, false);
        if (view.getLayoutParams() != null) {
            view.getLayoutParams().width = mCardWidth;
        }
        ViewHolder holder = new ViewHolder(view);
        holder.thumb.getLayoutParams().height = mCardWidth * 9 / 16;
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Video video = mVideos.get(position);
        holder.title.setText(video.getTitle());
        String author = video.getAuthor();
        holder.author.setText(author != null ? author : "");

        Glide.with(holder.itemView.getContext())
                .load(video.getCardImageUrl())
                .into(holder.thumb);

        holder.itemView.setOnClickListener(v -> {
            if (mClick != null) {
                mClick.onVideo(video);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (mLongClick != null) {
                mLongClick.onVideo(video);
            }
            return true;
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView title;
        final TextView author;

        ViewHolder(View itemView) {
            super(itemView);
            thumb = itemView.findViewById(R.id.card_thumb);
            title = itemView.findViewById(R.id.card_title);
            author = itemView.findViewById(R.id.card_author);
        }
    }
}
