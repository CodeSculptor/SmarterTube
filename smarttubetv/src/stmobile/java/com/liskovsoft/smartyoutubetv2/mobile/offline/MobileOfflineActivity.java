package com.liskovsoft.smartyoutubetv2.mobile.offline;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.mobile.ui.base.SystemBarInsets;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/** "Saved videos": the offline library, with live progress for downloads in flight. */
public class MobileOfflineActivity extends AppCompatActivity {
    private static final long REFRESH_MS = 1000;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final List<SavedVideo> mItems = new ArrayList<>();
    private OfflineStore mStore;
    private RowAdapter mAdapter;
    private TextView mUsage;
    private View mEmpty;

    private final Runnable mRefresh = new Runnable() {
        @Override
        public void run() {
            refresh();
            mHandler.postDelayed(this, REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_offline_activity);
        SystemBarInsets.apply(this);
        mStore = OfflinePrefs.store(this);

        mUsage = findViewById(R.id.offline_usage);
        mEmpty = findViewById(R.id.offline_empty);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_delete_all).setOnClickListener(v -> confirmDeleteAll());

        CheckBox wifi = findViewById(R.id.offline_wifi_only);
        wifi.setChecked(OfflinePrefs.isWifiOnly(this));
        wifi.setOnCheckedChangeListener((b, checked) -> OfflinePrefs.setWifiOnly(this, checked));

        RecyclerView list = findViewById(R.id.offline_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new RowAdapter();
        list.setAdapter(mAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.post(mRefresh);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mRefresh);
    }

    private void refresh() {
        List<SavedVideo> fresh = mStore.list();
        mItems.clear();
        mItems.addAll(fresh);
        mAdapter.notifyDataSetChanged();
        mEmpty.setVisibility(mItems.isEmpty() ? View.VISIBLE : View.GONE);
        mUsage.setText(getString(R.string.mobile_offline_usage,
                OfflineFormat.bytes(mStore.totalUsedBytes()),
                OfflineFormat.bytes(OfflinePrefs.freeBytes(this))));
    }

    private void onRowClick(SavedVideo v) {
        if (v.isDone()) {
            Intent intent = new Intent(this, MobileOfflinePlayerActivity.class);
            intent.putExtra(MobileOfflinePlayerActivity.EXTRA_VIDEO_ID, v.videoId);
            startActivity(intent);
        } else if (SavedVideo.PAUSED.equals(v.state) || SavedVideo.FAILED.equals(v.state)) {
            OfflineDownloads.resume(this, v.videoId);
        } else {
            OfflineDownloads.pause(this, v.videoId);
        }
        refresh();
    }

    private void confirmDelete(SavedVideo v) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.mobile_offline_delete_one, v.title != null ? v.title : v.videoId))
                .setPositiveButton(R.string.mobile_offline_delete, (d, w) -> {
                    OfflineDownloads.delete(this, v.videoId);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDeleteAll() {
        if (mItems.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setMessage(R.string.mobile_offline_delete_all_confirm)
                .setPositiveButton(R.string.mobile_offline_delete, (d, w) -> {
                    OfflineDownloads.deleteAll(this);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String statusText(SavedVideo v) {
        long onDisk = mStore.bytesOnDisk(v.videoId);
        switch (v.state) {
            case SavedVideo.DONE: {
                StringBuilder sb = new StringBuilder();
                if (v.qualityLabel != null) sb.append(v.qualityLabel).append(" · ");
                sb.append(OfflineFormat.bytes(v.totalBytes > 0 ? v.totalBytes : onDisk));
                String dur = OfflineFormat.duration(v.durationSec);
                if (!dur.isEmpty()) sb.append(" · ").append(dur);
                return sb.toString();
            }
            case SavedVideo.DOWNLOADING:
                return getString(R.string.mobile_offline_status_saving, OfflineDownloadPlan.percent(onDisk, v.totalBytes),
                        OfflineFormat.bytes(onDisk), OfflineFormat.bytes(v.totalBytes));
            case SavedVideo.PAUSED:
                return getString(R.string.mobile_offline_status_paused, OfflineFormat.bytes(onDisk));
            case SavedVideo.FAILED:
                return getString(R.string.mobile_offline_status_failed, v.reason != null ? v.reason : "");
            default:
                return getString(R.string.mobile_offline_status_waiting);
        }
    }

    private final class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.mobile_offline_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            SavedVideo v = mItems.get(position);
            h.title.setText(v.title != null ? v.title : v.videoId);
            h.status.setText(statusText(v));
            boolean inFlight = SavedVideo.DOWNLOADING.equals(v.state);
            h.progress.setVisibility(inFlight ? View.VISIBLE : View.GONE);
            if (inFlight) h.progress.setProgress(OfflineDownloadPlan.percent(mStore.bytesOnDisk(v.videoId), v.totalBytes));

            Bitmap bmp = null;
            java.io.File thumb = mStore.thumbFile(v.videoId);
            if (thumb.exists()) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2;
                bmp = BitmapFactory.decodeFile(thumb.getAbsolutePath(), o);
            }
            h.thumb.setImageBitmap(bmp);

            h.itemView.setOnClickListener(x -> onRowClick(v));
            h.itemView.setOnLongClickListener(x -> {
                confirmDelete(v);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView title;
            final TextView status;
            final ProgressBar progress;

            Holder(View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.offline_thumb);
                title = itemView.findViewById(R.id.offline_title);
                status = itemView.findViewById(R.id.offline_status);
                progress = itemView.findViewById(R.id.offline_progress);
            }
        }
    }
}
