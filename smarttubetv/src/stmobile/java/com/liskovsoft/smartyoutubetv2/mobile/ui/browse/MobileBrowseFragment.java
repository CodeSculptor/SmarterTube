package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Native portrait Home screen. Implements {@link BrowseView} and is driven by the
 * existing {@link BrowsePresenter} unchanged — all section/video-group loading logic is
 * reused from the TV code; only the view layer is new.
 *
 * ROW sections (Home, Trending, Music) render as stacked horizontal shelves; GRID
 * sections (Subscriptions, History, ...) render as a 2-column grid. The drawer menu
 * opens with an edge-swipe or the toolbar button.
 */
public class MobileBrowseFragment extends Fragment implements BrowseView {
    private BrowsePresenter mPresenter;
    private DrawerLayout mDrawer;
    private RecyclerView mContentList;
    private ProgressBar mProgressBar;
    private TextView mEmptyMessage;
    private View mEmptyContainer;
    private Button mEmptyButton;
    private TextView mToolbarTitle;
    private SectionAdapter mSectionAdapter;
    private VideoCardAdapter mGridAdapter;
    private ShelfAdapter mShelfAdapter;
    private boolean mProgressShowing;
    private int mGridCardWidth;
    private int mShelfCardWidth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.mobile_browse_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mDrawer = view.findViewById(R.id.drawer_layout);
        mContentList = view.findViewById(R.id.content_list);
        mProgressBar = view.findViewById(R.id.progress_bar);
        mEmptyMessage = view.findViewById(R.id.empty_message);
        mEmptyContainer = view.findViewById(R.id.empty_container);
        mEmptyButton = view.findViewById(R.id.empty_button);
        mToolbarTitle = view.findViewById(R.id.toolbar_title);

        view.findViewById(R.id.btn_menu).setOnClickListener(v -> {
            if (mDrawer.isDrawerOpen(GravityCompat.START)) {
                mDrawer.closeDrawer(GravityCompat.START);
            } else {
                mDrawer.openDrawer(GravityCompat.START);
            }
        });

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        mGridCardWidth = screenWidth / 2;
        mShelfCardWidth = (int) (screenWidth * 0.42f);

        mSectionAdapter = new SectionAdapter(this::onSectionPicked);
        RecyclerView sectionList = view.findViewById(R.id.section_list);
        sectionList.setLayoutManager(new LinearLayoutManager(getContext()));
        sectionList.setAdapter(mSectionAdapter);

        // Drop the UI below the status bar (MotherActivity runs TV-style fullscreen).
        int statusBarHeight = getStatusBarHeight();
        view.findViewById(R.id.content_root).setPadding(0, statusBarHeight, 0, 0);
        sectionList.setPadding(sectionList.getPaddingLeft(), sectionList.getPaddingTop() + statusBarHeight,
                sectionList.getPaddingRight(), sectionList.getPaddingBottom());
        // Open the menu with an on-screen right-swipe — the left screen edge belongs to
        // the system Back gesture and must not be used for this. See mSwipeToOpenMenu.
        mContentList.addOnItemTouchListener(mSwipeToOpenMenu);

        mPresenter = BrowsePresenter.instance(getContext());
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    private void onSectionPicked(BrowseSection section) {
        if (mDrawer != null) {
            mDrawer.closeDrawer(GravityCompat.START);
        }
        selectSection(mSectionAdapter.indexOf(section), true);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mPresenter != null) {
            mPresenter.onViewResumed();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mPresenter != null) {
            mPresenter.onViewPaused();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPresenter != null) {
            mPresenter.onViewDestroyed();
        }
    }

    // ----- BrowseView -----

    @Override
    public void addSection(int index, BrowseSection section) {
        if (mSectionAdapter != null && section != null) {
            mSectionAdapter.add(index, section);
        }
    }

    @Override
    public void removeSection(BrowseSection section) {
        if (mSectionAdapter != null) {
            mSectionAdapter.remove(section);
        }
    }

    @Override
    public void removeAllSections() {
        if (mSectionAdapter != null) {
            mSectionAdapter.clear();
        }
    }

    @Override
    public void selectSection(int index, boolean focusOnContent) {
        if (mSectionAdapter == null || index < 0 || index >= mSectionAdapter.getItemCount()) {
            return;
        }
        BrowseSection section = mSectionAdapter.getItem(index);
        mSectionAdapter.setSelected(section);
        if (mToolbarTitle != null) {
            mToolbarTitle.setText(section.getTitle());
        }
        setupContentForType(section.getType());
        if (mPresenter != null) {
            mPresenter.onSectionFocused(section.getId());
        }
    }

    @Override
    public void updateSection(VideoGroup group) {
        if (group == null || mContentList == null) {
            return;
        }
        switch (group.getAction()) {
            case VideoGroup.ACTION_REPLACE:
                if (mShelfAdapter != null) mShelfAdapter.clear();
                if (mGridAdapter != null) mGridAdapter.clear();
                hideEmptyMessage();
                break;
            case VideoGroup.ACTION_REMOVE:
            case VideoGroup.ACTION_REMOVE_AUTHOR:
                if (mGridAdapter != null) mGridAdapter.remove(group.getVideos());
                if (mShelfAdapter != null) mShelfAdapter.removeVideos(group.getVideos());
                break;
            case VideoGroup.ACTION_SYNC:
                // Percent-watched markers only; not rendered natively yet.
                break;
            default: // ACTION_APPEND / ACTION_PREPEND
                if (group.isEmpty()) {
                    break;
                }
                hideEmptyMessage();
                if (mShelfAdapter != null) {
                    mShelfAdapter.appendGroup(group);
                } else if (mGridAdapter != null) {
                    // A continuation carries the full cumulative list, so replace.
                    mGridAdapter.setVideos(group.getVideos());
                }
                break;
        }
    }

    @Override
    public void updateSection(SettingsGroup group) {
        // Native portrait settings is a later phase (Phase 4). Placeholder for now.
        if (mShelfAdapter != null) mShelfAdapter.clear();
        if (mGridAdapter != null) mGridAdapter.clear();
        showEmptyMessage("Settings — native phone screen coming in a later update.");
    }

    @Override
    public void clearSection(BrowseSection section) {
        if (mShelfAdapter != null) mShelfAdapter.clear();
        if (mGridAdapter != null) mGridAdapter.clear();
    }

    @Override
    public void selectSectionItem(int index) {
        // Touch UI: no D-pad selection to restore.
    }

    @Override
    public void selectSectionItem(Video item) {
        // Touch UI: no D-pad selection to restore.
    }

    @Override
    public void showError(ErrorFragmentData data) {
        if (mEmptyContainer == null) {
            return;
        }
        mEmptyMessage.setText(data != null ? data.getMessage() : "");
        if (data != null && data.getActionText() != null) {
            mEmptyButton.setText(data.getActionText());
            mEmptyButton.setOnClickListener(v -> data.onAction());
            mEmptyButton.setVisibility(View.VISIBLE);
        } else {
            mEmptyButton.setVisibility(View.GONE);
        }
        mEmptyContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void showProgressBar(boolean show) {
        mProgressShowing = show;
        if (mProgressBar != null) {
            mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public boolean isProgressBarShowing() {
        return mProgressShowing;
    }

    @Override
    public void focusOnContent() {
        // Touch UI: no focus to move.
    }

    @Override
    public boolean isEmpty() {
        return (mGridAdapter == null || mGridAdapter.getItemCount() == 0)
                && (mShelfAdapter == null || mShelfAdapter.getItemCount() == 0);
    }

    // ----- helpers -----

    private void setupContentForType(int type) {
        hideEmptyMessage();
        mContentList.clearOnScrollListeners();
        if (type == BrowseSection.TYPE_ROW) {
            mShelfAdapter = new ShelfAdapter(mShelfCardWidth, mVideoClick, mVideoLongClick);
            mGridAdapter = null;
            mContentList.setLayoutManager(new LinearLayoutManager(getContext()));
            mContentList.setAdapter(mShelfAdapter);
        } else {
            mGridAdapter = new VideoCardAdapter(mGridCardWidth, mVideoClick, mVideoLongClick);
            mShelfAdapter = null;
            mContentList.setLayoutManager(new GridLayoutManager(getContext(), 2));
            mContentList.setAdapter(mGridAdapter);
            mContentList.addOnScrollListener(mGridScrollListener);
        }
    }

    private final VideoCardAdapter.OnVideoAction mVideoClick = video -> {
        if (mPresenter != null) {
            mPresenter.onVideoItemSelected(video);
            mPresenter.onVideoItemClicked(video);
        }
    };

    private final VideoCardAdapter.OnVideoAction mVideoLongClick = video -> {
        if (mPresenter != null) {
            mPresenter.onVideoItemLongClicked(video);
        }
    };

    private final RecyclerView.OnScrollListener mGridScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            if (dy <= 0 || mProgressShowing || mGridAdapter == null) {
                return;
            }
            RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
            if (!(lm instanceof GridLayoutManager)) {
                return;
            }
            int lastVisible = ((GridLayoutManager) lm).findLastVisibleItemPosition();
            if (lastVisible >= mGridAdapter.getItemCount() - 4) {
                Video last = mGridAdapter.getLast();
                if (last != null && mPresenter != null) {
                    mPresenter.onScrollEnd(last);
                }
            }
        }
    };

    private void showEmptyMessage(String message) {
        if (mEmptyContainer == null) {
            return;
        }
        mEmptyMessage.setText(message);
        mEmptyButton.setVisibility(View.GONE);
        mEmptyContainer.setVisibility(View.VISIBLE);
    }

    private void hideEmptyMessage() {
        if (mEmptyContainer != null) {
            mEmptyContainer.setVisibility(View.GONE);
        }
    }

    private int getStatusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    /**
     * Opens the drawer on an on-screen right-swipe. Registered on the outer content
     * RecyclerView, so it is consulted before the inner shelves: a shelf that can still
     * scroll toward its start keeps the gesture; a shelf already at its start (or a grid
     * section with no horizontal scroller) yields it to open the menu.
     */
    private final RecyclerView.OnItemTouchListener mSwipeToOpenMenu = new RecyclerView.OnItemTouchListener() {
        private float mDownX;
        private float mDownY;
        private boolean mDecided;
        private boolean mTriggered;

        @Override
        public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDownX = e.getX();
                    mDownY = e.getY();
                    mDecided = false;
                    mTriggered = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (mDecided) {
                        return mTriggered;
                    }
                    float dx = e.getX() - mDownX;
                    float dy = e.getY() - mDownY;
                    int slop = ViewConfiguration.get(rv.getContext()).getScaledTouchSlop();
                    if (Math.abs(dx) < slop * 2 && Math.abs(dy) < slop * 2) {
                        return false; // wait for a clear direction
                    }
                    mDecided = true;
                    if (dx > 0 && dx > Math.abs(dy) && shelfAtStart(rv, mDownX, mDownY)) {
                        mTriggered = true;
                        if (mDrawer != null) {
                            mDrawer.openDrawer(GravityCompat.START);
                        }
                    }
                    return mTriggered;
                default:
                    return false;
            }
        }

        @Override
        public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            // Gesture is fully handled in onInterceptTouchEvent.
        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            // no-op
        }
    };

    /**
     * True if a right-swipe at (x, y) should open the menu: either there is no horizontal
     * shelf under the finger (grid section or empty area), or the shelf there is already
     * at its start and cannot scroll further toward it.
     */
    private boolean shelfAtStart(RecyclerView contentList, float x, float y) {
        View child = contentList.findChildViewUnder(x, y);
        if (child == null) {
            return true;
        }
        View shelfList = child.findViewById(R.id.shelf_list);
        if (shelfList == null) {
            return true;
        }
        return !shelfList.canScrollHorizontally(-1);
    }
}
