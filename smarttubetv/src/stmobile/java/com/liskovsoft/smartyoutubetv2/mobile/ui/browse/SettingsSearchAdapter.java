package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/** Result rows of the Settings search: title, "Section > Category" and current value/description. */
class SettingsSearchAdapter extends RecyclerView.Adapter<SettingsSearchAdapter.ViewHolder> {
    interface OnResultClick {
        void onClick(SettingsSearchIndex.Entry entry);
    }

    private final List<SettingsSearchIndex.Entry> mResults = new ArrayList<>();
    private final OnResultClick mListener;

    SettingsSearchAdapter(OnResultClick listener) {
        mListener = listener;
    }

    void setResults(List<SettingsSearchIndex.Entry> results) {
        mResults.clear();
        mResults.addAll(results);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mResults.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.mobile_settings_search_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        SettingsSearchIndex.Entry e = mResults.get(position);
        h.title.setText(e.title);
        String crumb = e.breadcrumb();
        String detail = TextUtils.isEmpty(e.summary) || e.summary.equals(e.title) ? crumb
                : crumb.isEmpty() ? e.summary : crumb + "  ·  " + e.summary;
        h.detail.setText(detail);
        h.detail.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
        h.itemView.setOnClickListener(v -> mListener.onClick(e));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView detail;

        ViewHolder(View v) {
            super(v);
            title = v.findViewById(R.id.search_title);
            detail = v.findViewById(R.id.search_detail);
        }
    }
}
