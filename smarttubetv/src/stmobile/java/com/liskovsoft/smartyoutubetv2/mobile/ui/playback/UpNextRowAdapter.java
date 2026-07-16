package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.text.TextUtils;
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
 * Compact up-next list for the portrait player panel: small 16:9 thumbnail left, title and
 * "Channel • views • date" meta right (YouTube "Next" style — full-size cards read like extra
 * playing videos under the strip).
 */
public class UpNextRowAdapter extends RecyclerView.Adapter<UpNextRowAdapter.Holder> {
    public interface OnVideo {
        void onVideo(Video video);
    }

    private final List<Video> mVideos = new ArrayList<>();
    private final OnVideo mClick;

    public UpNextRowAdapter(OnVideo click) {
        mClick = click;
    }

    public void appendVideos(List<Video> videos) {
        if (videos == null || videos.isEmpty()) {
            return;
        }
        // Several suggestion groups can carry the same video — keep the first occurrence only.
        List<Video> fresh = new ArrayList<>();
        for (Video video : videos) {
            if (video != null && !containsId(video.videoId)) {
                fresh.add(video);
            }
        }
        if (fresh.isEmpty()) {
            return;
        }
        int start = mVideos.size();
        mVideos.addAll(fresh);
        notifyItemRangeInserted(start, fresh.size());
    }

    private boolean containsId(String videoId) {
        if (videoId == null) {
            return false;
        }
        for (Video video : mVideos) {
            if (videoId.equals(video.videoId)) {
                return true;
            }
        }
        return false;
    }

    public void remove(List<Video> videos) {
        if (videos != null && mVideos.removeAll(videos)) {
            notifyDataSetChanged();
        }
    }

    public void clear() {
        mVideos.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mVideos.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.mobile_up_next_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Video video = mVideos.get(position);

        h.title.setText(video.getTitle() != null ? video.getTitle() : "");

        CharSequence subtitle = video.getSecondTitle(); // "Channel • views • date"
        h.subtitle.setText(TextUtils.isEmpty(subtitle)
                ? (video.getAuthor() != null ? video.getAuthor() : "") : subtitle);

        Glide.with(h.itemView.getContext())
                .load(video.getCardImageUrl())
                .into(h.thumb);

        // Duration/length badge overlaid on the thumbnail (matches the browse grid card).
        // Explicit GONE branch because rows are recycled.
        String badge = video.badge;
        if (!TextUtils.isEmpty(badge)) {
            h.duration.setText(badge);
            h.duration.setVisibility(View.VISIBLE);
        } else {
            h.duration.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> {
            if (mClick != null) {
                mClick.onVideo(video);
            }
        });
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView duration;
        final TextView title;
        final TextView subtitle;

        Holder(View v) {
            super(v);
            thumb = v.findViewById(R.id.row_thumb);
            duration = v.findViewById(R.id.row_duration);
            title = v.findViewById(R.id.row_title);
            subtitle = v.findViewById(R.id.row_subtitle);
        }
    }
}
