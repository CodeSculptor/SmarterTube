package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Drawer list of {@link BrowseSection}s (Home, Subscriptions, History, ...).
 */
class SectionAdapter extends RecyclerView.Adapter<SectionAdapter.ViewHolder> {
    interface OnSectionAction {
        void onSection(BrowseSection section);
    }

    private final OnSectionAction mClick;
    private final List<BrowseSection> mSections = new ArrayList<>();
    private int mSelectedId = -1;

    SectionAdapter(OnSectionAction click) {
        mClick = click;
    }

    void add(int index, BrowseSection section) {
        for (int i = 0; i < mSections.size(); i++) {
            if (mSections.get(i).getId() == section.getId()) {
                mSections.remove(i);
                break;
            }
        }
        if (index < 0 || index > mSections.size()) {
            mSections.add(section);
        } else {
            mSections.add(index, section);
        }
        notifyDataSetChanged();
    }

    void remove(BrowseSection section) {
        if (section == null) {
            return;
        }
        for (int i = 0; i < mSections.size(); i++) {
            if (mSections.get(i).getId() == section.getId()) {
                mSections.remove(i);
                notifyDataSetChanged();
                return;
            }
        }
    }

    void clear() {
        mSections.clear();
        notifyDataSetChanged();
    }

    BrowseSection getItem(int index) {
        return mSections.get(index);
    }

    int indexOf(BrowseSection section) {
        if (section == null) {
            return -1;
        }
        for (int i = 0; i < mSections.size(); i++) {
            if (mSections.get(i).getId() == section.getId()) {
                return i;
            }
        }
        return -1;
    }

    void setSelected(BrowseSection section) {
        mSelectedId = section != null ? section.getId() : -1;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mSections.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.mobile_section_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BrowseSection section = mSections.get(position);
        holder.title.setText(section.getTitle());

        if (section.getResId() > 0) {
            holder.icon.setImageResource(section.getResId());
            holder.icon.setVisibility(View.VISIBLE);
        } else {
            holder.icon.setVisibility(View.INVISIBLE);
        }

        boolean selected = section.getId() == mSelectedId;
        holder.itemView.setBackgroundColor(selected
                ? ContextCompat.getColor(holder.itemView.getContext(), R.color.mobile_drawer_selected)
                : Color.TRANSPARENT);

        holder.itemView.setOnClickListener(v -> {
            if (mClick != null) {
                mClick.onSection(section);
            }
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;

        ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.section_icon);
            title = itemView.findViewById(R.id.section_title);
        }
    }
}
