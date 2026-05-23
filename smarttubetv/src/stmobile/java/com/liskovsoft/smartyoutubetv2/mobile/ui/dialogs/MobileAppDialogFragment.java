package com.liskovsoft.smartyoutubetv2.mobile.ui.dialogs;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.List;

/**
 * Phone-native settings dialog. Implements {@link AppDialogView} and is driven by the
 * existing {@link AppDialogPresenter} unchanged — category list, radio/check/switch state
 * and the on-select callbacks are all reused from the TV code; only the view layer is new.
 *
 * Categories are rendered flat in one vertical list: each non-null category title becomes
 * a section header followed by its option rows. There is no nested back stack — back
 * always finishes the dialog (the underlying TV {@code AppDialogPresenter} drives a fresh
 * dialog instance per category on the TV build's nested screens; on phone the same flow
 * just opens a new {@code MobileAppDialogActivity}).
 */
public class MobileAppDialogFragment extends Fragment implements AppDialogView {
    private AppDialogPresenter mPresenter;
    private TextView mTitleView;
    private RecyclerView mList;
    private MobileAppDialogAdapter mAdapter;
    private boolean mIsTransparent;
    private boolean mIsOverlay;
    private boolean mIsPaused;
    private int mId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.mobile_app_dialog_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mTitleView = view.findViewById(R.id.dialog_title);
        mList = view.findViewById(R.id.options_list);
        mList.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new MobileAppDialogAdapter();
        mList.setAdapter(mAdapter);

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        mPresenter = AppDialogPresenter.instance(getContext());
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsPaused = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsPaused = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPresenter != null && mPresenter.getView() == this) {
            mPresenter.onViewDestroyed();
        }
    }

    /** Called by the activity when it is finishing, to fire the presenter's onFinish callbacks. */
    void onFinishCallback() {
        if (mPresenter != null) {
            mPresenter.onFinish();
        }
    }

    // ----- AppDialogView -----

    @Override
    public void show(List<OptionCategory> categories, CharSequence title, boolean isExpandable,
                     boolean isTransparent, boolean isOverlay, int id) {
        mIsTransparent = isTransparent;
        mIsOverlay = isOverlay;
        mId = id;

        CharSequence resolvedTitle = title;
        // Expandable single-category: drop the wrapping title and use the category's name
        // so the screen jumps straight into the options (matches TV behavior).
        if (isExpandable && categories != null && categories.size() == 1
                && categories.get(0).title != null) {
            resolvedTitle = categories.get(0).title;
        }
        if (mTitleView != null) {
            mTitleView.setText(TextUtils.isEmpty(resolvedTitle) ? "" : resolvedTitle);
        }

        if (mAdapter != null) {
            mAdapter.setCategories(categories, isExpandable);
        }
    }

    @Override
    public void finish() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    @Override
    public void goBack() {
        // No nested back stack; goBack == finish.
        finish();
    }

    @Override
    public void clearBackstack() {
        // No nested back stack to clear.
    }

    @Override
    public boolean canGoBack() {
        return false;
    }

    @Override
    public boolean isShown() {
        return isVisible() && getUserVisibleHint();
    }

    @Override
    public boolean isTransparent() {
        return mIsTransparent;
    }

    @Override
    public boolean isOverlay() {
        return mIsOverlay;
    }

    @Override
    public boolean isPaused() {
        return mIsPaused;
    }

    @Override
    public int getViewId() {
        return mId;
    }
}
