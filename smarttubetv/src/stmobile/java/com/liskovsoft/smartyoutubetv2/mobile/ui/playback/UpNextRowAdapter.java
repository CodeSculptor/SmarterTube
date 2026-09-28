package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.mobile.ui.browse.VideoCardAdapter;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact up-next list for the portrait player panel: small 16:9 thumbnail left, title and
 * "Channel • views • date" meta right (YouTube "Next" style — full-size cards read like extra
 * playing videos under the strip).
 *
 * If the video has chapters, the list leads with a labelled horizontal "Chapters" strip (tap =
 * seek, through the same suggestion click the TV chapters row uses).
 */
public class UpNextRowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_VIDEO = 0;
    private static final int TYPE_CHAPTERS = 1;

    public interface OnVideo {
        void onVideo(Video video);
    }

    private final List<Video> mVideos = new ArrayList<>();
    private final List<Video> mChapters = new ArrayList<>();
    private final OnVideo mClick;
    private final OnVideo mLongClick;

    public UpNextRowAdapter(OnVideo click, OnVideo longClick) {
        mClick = click;
        mLongClick = longClick;
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
        notifyItemRangeInserted(offset() + start, fresh.size());
    }

    /** The current video's chapters (replaces any previous set; empty/null removes the strip). */
    public void setChapters(List<Video> chapters) {
        boolean had = !mChapters.isEmpty();
        mChapters.clear();
        if (chapters != null) {
            mChapters.addAll(chapters);
        }
        boolean has = !mChapters.isEmpty();
        if (had && has) {
            notifyItemChanged(0);
        } else if (had) {
            notifyItemRemoved(0);
        } else if (has) {
            notifyItemInserted(0);
        }
    }

    /** Adapter position of the first video row (the chapters strip, if any, comes first). */
    private int offset() {
        return mChapters.isEmpty() ? 0 : 1;
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
        if (videos == null) {
            return;
        }
        boolean changed = mVideos.removeAll(videos);
        if (!mChapters.isEmpty() && !videos.isEmpty() && videos.get(0) != null && videos.get(0).isChapter) {
            mChapters.clear();
            changed = true;
        }
        if (changed) {
            notifyDataSetChanged();
        }
    }

    /** Presenter ACTION_SYNC: refresh percent-watched on matching rows in place. */
    public void sync(List<Video> changed) {
        if (changed == null) {
            return;
        }
        for (Video video : changed) {
            for (int i = 0; i < mVideos.size(); i++) {
                Video origin = mVideos.get(i);
                if (origin.equals(video)) {
                    origin.sync(video);
                    notifyItemChanged(offset() + i);
                }
            }
        }
    }

    public void clear() {
        mVideos.clear();
        mChapters.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return offset() + mVideos.size();
    }

    @Override
    public int getItemViewType(int position) {
        return position < offset() ? TYPE_CHAPTERS : TYPE_VIDEO;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_CHAPTERS) {
            return new ChaptersHolder(inflater.inflate(R.layout.mobile_up_next_chapters, parent, false));
        }
        return new Holder(inflater.inflate(R.layout.mobile_up_next_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ChaptersHolder) {
            ((ChaptersHolder) holder).bind(mChapters, mClick);
            return;
        }

        Holder h = (Holder) holder;
        Video video = mVideos.get(position - offset());

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

        VideoCardAdapter.bindWatchedBar(h.progress, h.duration, video);

        h.itemView.setOnClickListener(v -> {
            if (mClick != null) {
                mClick.onVideo(video);
            }
        });
        // Long-press opens the video context menu, as on Home thumbnails (VideoCardAdapter).
        h.itemView.setOnLongClickListener(v -> {
            if (mLongClick != null) {
                mLongClick.onVideo(video);
            }
            return true;
        });
    }

    /** The "Chapters" label + horizontal strip of chapter cards. */
    static class ChaptersHolder extends RecyclerView.ViewHolder {
        final ChapterCardAdapter adapter = new ChapterCardAdapter();

        ChaptersHolder(View v) {
            super(v);
            RecyclerView list = v.findViewById(R.id.chapters_list);
            list.setLayoutManager(new LinearLayoutManager(v.getContext(), LinearLayoutManager.HORIZONTAL, false));
            list.setAdapter(adapter);
        }

        void bind(List<Video> chapters, OnVideo click) {
            adapter.set(chapters, click);
        }
    }

    static class ChapterCardAdapter extends RecyclerView.Adapter<ChapterCardAdapter.CardHolder> {
        private final List<Video> mItems = new ArrayList<>();
        private OnVideo mClick;

        void set(List<Video> items, OnVideo click) {
            mItems.clear();
            mItems.addAll(items);
            mClick = click;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        @NonNull
        @Override
        public CardHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new CardHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.mobile_up_next_chapter_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull CardHolder h, int position) {
            Video chapter = mItems.get(position);
            h.title.setText(chapter.getTitle() != null ? chapter.getTitle() : "");
            h.time.setText(chapter.badge != null ? chapter.badge : "");
            Glide.with(h.itemView.getContext())
                    .load(chapter.getCardImageUrl())
                    .into(h.thumb);
            h.itemView.setOnClickListener(v -> {
                if (mClick != null) {
                    mClick.onVideo(chapter);
                }
            });
        }

        static class CardHolder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView time;
            final TextView title;

            CardHolder(View v) {
                super(v);
                thumb = v.findViewById(R.id.chapter_thumb);
                time = v.findViewById(R.id.chapter_time);
                title = v.findViewById(R.id.chapter_title);
            }
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView duration;
        final ProgressBar progress;
        final TextView title;
        final TextView subtitle;

        Holder(View v) {
            super(v);
            thumb = v.findViewById(R.id.row_thumb);
            duration = v.findViewById(R.id.row_duration);
            progress = v.findViewById(R.id.row_progress);
            title = v.findViewById(R.id.row_title);
            subtitle = v.findViewById(R.id.row_subtitle);
        }
    }
}
