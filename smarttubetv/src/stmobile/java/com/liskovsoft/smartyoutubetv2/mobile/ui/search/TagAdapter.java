package com.liskovsoft.smartyoutubetv2.mobile.ui.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/** Search-suggestion list: one tappable row per query suggestion. */
class TagAdapter extends RecyclerView.Adapter<TagAdapter.ViewHolder> {
    interface OnTagAction {
        void onTag(Tag tag);
    }

    private final OnTagAction mClick;
    private final OnTagAction mLongClick;
    private final List<Tag> mTags = new ArrayList<>();

    TagAdapter(OnTagAction click, OnTagAction longClick) {
        mClick = click;
        mLongClick = longClick;
    }

    void setTags(List<Tag> tags) {
        mTags.clear();
        if (tags != null) {
            mTags.addAll(tags);
        }
        notifyDataSetChanged();
    }

    void clear() {
        mTags.clear();
        notifyDataSetChanged();
    }

    void remove(Tag tag) {
        if (tag != null && mTags.remove(tag)) {
            notifyDataSetChanged();
        }
    }

    @Override
    public int getItemCount() {
        return mTags.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.mobile_tag_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Tag tag = mTags.get(position);
        holder.text.setText(tag.tag);
        holder.itemView.setOnClickListener(v -> {
            if (mClick != null) {
                mClick.onTag(tag);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (mLongClick == null) {
                return false;
            }
            mLongClick.onTag(tag);
            return true;
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView text;

        ViewHolder(View itemView) {
            super(itemView);
            text = (TextView) itemView;
        }
    }
}
