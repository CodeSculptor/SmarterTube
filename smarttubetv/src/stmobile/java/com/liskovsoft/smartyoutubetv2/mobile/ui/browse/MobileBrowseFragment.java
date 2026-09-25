package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.mobile.notifications.NotificationPollWorker;
import com.liskovsoft.smartyoutubetv2.mobile.ui.about.MobileAboutActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.prefs.MobileNotificationPrefs;
import com.liskovsoft.smartyoutubetv2.mobile.ui.prefs.MobileThemePrefs;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Native portrait Home screen. Implements {@link BrowseView} and is driven by the
 * existing {@link BrowsePresenter} unchanged — all section/video-group loading logic is
 * reused from the TV code; only the view layer is new.
 *
 * ROW sections (Home, Trending, Music) render as stacked horizontal shelves; GRID
 * sections (Subscriptions, History, ...) render as a 2-column grid. The drawer menu
 * opens with an edge-swipe or the toolbar button.
 */
public class MobileBrowseFragment extends Fragment implements BrowseView, MediaServiceManager.AccountChangeListener {
    private BrowsePresenter mPresenter;
    private DrawerLayout mDrawer;
    private SwipeRefreshLayout mSwipeRefresh;
    private RecyclerView mContentList;
    private ProgressBar mProgressBar;
    private TextView mEmptyMessage;
    private View mEmptyContainer;
    private Button mEmptyButton;
    private TextView mToolbarTitle;
    private ImageView mAccountView;
    private SectionAdapter mSectionAdapter;
    private VideoCardAdapter mGridAdapter;
    private ShelfAdapter mShelfAdapter;
    private SettingsItemAdapter mSettingsAdapter;
    private boolean mProgressShowing;
    private boolean mSwipeRefreshing;
    private boolean mSectionSelected;
    private int mGridCardWidth;
    private int mGridSpan;
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
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
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

        view.findViewById(R.id.btn_search).setOnClickListener(v ->
                SearchPresenter.instance(getContext()).startSearch(null));

        // Account switcher — mirrors SmartTube TV's title-bar orb (NavigateTitleView):
        // tap cycles to the next account, long-press opens account management.
        mAccountView = view.findViewById(R.id.btn_account);
        mAccountView.setOnClickListener(v ->
                AccountSelectionPresenter.instance(getContext()).nextAccountOrDialog());
        mAccountView.setOnLongClickListener(v -> {
            AccountSettingsPresenter.instance(getContext()).show();
            return true;
        });
        MediaServiceManager.instance().addAccountListener(this);
        updateAccountIcon();

        view.findViewById(R.id.drawer_about).setOnClickListener(v -> {
            if (mDrawer != null) {
                mDrawer.closeDrawer(GravityCompat.START);
            }
            startActivity(new Intent(getContext(), MobileAboutActivity.class));
        });

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        mGridSpan = getResources().getInteger(R.integer.mobile_grid_span);
        mGridCardWidth = screenWidth / mGridSpan;
        mShelfCardWidth = (int) (screenWidth * 0.42f);

        mSectionAdapter = new SectionAdapter(this::onSectionPicked);
        RecyclerView sectionList = view.findViewById(R.id.section_list);
        sectionList.setLayoutManager(new LinearLayoutManager(getContext()));
        sectionList.setAdapter(mSectionAdapter);

        // Open the menu with an on-screen right-swipe — the left screen edge belongs to
        // the system Back gesture and must not be used for this. See mSwipeToOpenMenu.
        mContentList.addOnItemTouchListener(mSwipeToOpenMenu);

        mSwipeRefresh.setColorSchemeResources(R.color.brand_accent);
        mSwipeRefresh.setProgressBackgroundColorSchemeResource(R.color.mobile_surface);
        mSwipeRefresh.setOnRefreshListener(() -> {
            // Settings section has nothing to reload; otherwise re-fetch the current section.
            if (mPresenter == null || mSettingsAdapter != null) {
                stopSwipeRefresh();
                return;
            }
            mSwipeRefreshing = true;
            mPresenter.refresh(false);
        });

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
        MediaServiceManager.instance().removeAccountListener(this);
        if (mPresenter != null) {
            mPresenter.onViewDestroyed();
        }
    }

    @Override
    public void onAccountChanged(Account account) {
        updateAccountIcon();
    }

    /**
     * Load the signed-in account's circular avatar into the toolbar button — the touch-UI
     * counterpart of {@code NavigateTitleView.updateAccountIcon()}. Falls back to the default
     * account placeholder when no account is selected (or it has no avatar).
     */
    private void updateAccountIcon() {
        if (mAccountView == null) {
            return;
        }

        Account current = MediaServiceManager.instance().getSelectedAccount();

        if (current != null && current.getAvatarImageUrl() != null) {
            // A real photo: drop the placeholder's monochrome tint so it shows in full colour.
            mAccountView.setImageTintList(null);
            Glide.with(this)
                    .load(current.getAvatarImageUrl())
                    .placeholder(R.drawable.browse_title_account)
                    .circleCrop()
                    .into(mAccountView);
        } else {
            Glide.with(this).clear(mAccountView);
            // Re-apply the toolbar tint so the placeholder matches the other toolbar icons.
            mAccountView.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(mAccountView.getContext(), R.color.mobile_text_primary)));
            mAccountView.setImageResource(R.drawable.browse_title_account);
        }
    }

    // ----- BrowseView -----

    @Override
    public void addSection(int index, BrowseSection section) {
        if (mSectionAdapter != null && section != null) {
            mSectionAdapter.add(index, section);
            // Auto-open a default section so the user lands on videos rather than an empty
            // "pick a section" Home — notably after sign-in, where the section list is
            // rebuilt. Deferred via post() so a presenter-driven selection during the same
            // load wins (it sets mSectionSelected first); otherwise we open the first
            // section (the Home feed). Re-arms on removeAllSections (a full refresh).
            if (!mSectionSelected && mContentList != null) {
                mContentList.post(this::autoSelectDefaultSection);
            }
        }
    }

    private void autoSelectDefaultSection() {
        if (mSectionSelected || mSectionAdapter == null || mSectionAdapter.getItemCount() == 0) {
            return;
        }
        selectSection(0, false);
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
        // Section list is being rebuilt — re-arm the default-section auto-open.
        mSectionSelected = false;
    }

    @Override
    public void selectSection(int index, boolean focusOnContent) {
        if (mSectionAdapter == null || index < 0 || index >= mSectionAdapter.getItemCount()) {
            return;
        }
        BrowseSection section = mSectionAdapter.getItem(index);
        mSectionSelected = true;
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
                // Percent-watched refresh (e.g. back from the player): redraw the watched bars.
                if (mGridAdapter != null) mGridAdapter.sync(group.getVideos());
                if (mShelfAdapter != null) mShelfAdapter.syncVideos(group.getVideos());
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
        if (group == null || mContentList == null) {
            return;
        }
        hideEmptyMessage();
        mShelfAdapter = null;
        mGridAdapter = null;
        mSettingsAdapter = new SettingsItemAdapter(prependThemeRow(group.getItems()));
        mContentList.setLayoutManager(new LinearLayoutManager(getContext()));
        mContentList.setAdapter(mSettingsAdapter);
    }

    /**
     * Prepend a phone-only "Theme" row to the upstream Settings list. Upstream's
     * ColorScheme picker is TV-only (all 8 schemes are dark variants) and isn't
     * surfaced in the stmobile UI, so this is a dedicated Day-Night toggle that
     * drives {@link MobileThemePrefs}.
     */
    private List<SettingsItem> prependThemeRow(List<SettingsItem> upstreamItems) {
        Context context = getContext();
        List<SettingsItem> items = new ArrayList<>();
        if (context != null) {
            items.add(new SettingsItem(
                    context.getString(R.string.mobile_theme_title),
                    this::showThemePicker,
                    R.drawable.settings_theme));
            items.add(new SettingsItem(
                    context.getString(R.string.mobile_notifications_title),
                    this::showNotificationsToggle,
                    R.drawable.settings_notifications));
        }
        if (upstreamItems != null) {
            // Drop upstream's "About" row: it's the TV-oriented About panel (dead
            // "Check for updates", the meaningless ATV/Amazon "global search" bridge). The
            // phone build's own About screen in the drawer footer (MobileAboutActivity) is
            // the single About surface.
            String aboutTitle = context != null ? context.getString(R.string.settings_about) : null;
            for (SettingsItem item : upstreamItems) {
                if (aboutTitle != null && aboutTitle.equals(item.title)) {
                    continue;
                }
                items.add(item);
            }
        }
        return items;
    }

    private void showThemePicker() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        MobileThemePrefs.Mode[] modes = MobileThemePrefs.Mode.values();
        String[] labels = {
                context.getString(R.string.mobile_theme_option_system),
                context.getString(R.string.mobile_theme_option_light),
                context.getString(R.string.mobile_theme_option_dark),
        };
        MobileThemePrefs.Mode current = MobileThemePrefs.getMode(context);
        int checked = current.ordinal();
        new AlertDialog.Builder(context)
                .setTitle(R.string.mobile_theme_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    if (modes[which] == current) {
                        dialog.dismiss();
                        return;
                    }
                    MobileThemePrefs.setMode(context, modes[which]);
                    dialog.dismiss();
                    // MotherActivity is a FragmentActivity (not AppCompatActivity), so
                    // setDefaultNightMode does not auto-recreate. MobileActivity reads
                    // the pref in attachBaseContext, so a manual recreate() pulls in the
                    // new uiMode override.
                    if (getActivity() != null) {
                        getActivity().recreate();
                    }
                })
                .show();
    }

    /**
     * Phone-only "Upload notifications" on/off toggle (Part 2 — push). Drives
     * {@link MobileNotificationPrefs} and (re)schedules {@link NotificationPollWorker}. On enable,
     * asks for the Android 13+ POST_NOTIFICATIONS permission via the host activity.
     */
    private void showNotificationsToggle() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        String[] labels = {
                context.getString(R.string.mobile_notifications_option_off),
                context.getString(R.string.mobile_notifications_option_on),
        };
        boolean enabled = MobileNotificationPrefs.isEnabled(context);
        int checked = enabled ? 1 : 0;
        new AlertDialog.Builder(context)
                .setTitle(R.string.mobile_notifications_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    boolean turnOn = which == 1;
                    dialog.dismiss();
                    if (turnOn == enabled) {
                        return;
                    }
                    MobileNotificationPrefs.setEnabled(context, turnOn);
                    NotificationPollWorker.schedule(context);
                    if (turnOn && getActivity() instanceof MobileBrowseActivity) {
                        ((MobileBrowseActivity) getActivity()).requestPostNotificationsPermission();
                    }
                })
                .show();
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
        // While pull-to-refresh is active the SwipeRefreshLayout spinner stands in for
        // the centre bar; don't show both.
        if (mProgressBar != null) {
            mProgressBar.setVisibility(show && !mSwipeRefreshing ? View.VISIBLE : View.GONE);
        }
        if (!show) {
            stopSwipeRefresh();
        }
    }

    private void stopSwipeRefresh() {
        mSwipeRefreshing = false;
        if (mSwipeRefresh != null) {
            mSwipeRefresh.setRefreshing(false);
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
                && (mShelfAdapter == null || mShelfAdapter.getItemCount() == 0)
                && (mSettingsAdapter == null || mSettingsAdapter.getItemCount() == 0);
    }

    @Override
    public void updateBadge() {
        // Keep the toolbar account avatar in sync (the phone counterpart of the TV account orb).
        updateAccountIcon();
    }

    // ----- helpers -----

    private void setupContentForType(int type) {
        hideEmptyMessage();
        mContentList.clearOnScrollListeners();
        if (type == BrowseSection.TYPE_ROW) {
            mShelfAdapter = new ShelfAdapter(mShelfCardWidth, mVideoClick, mVideoLongClick,
                    mShelfScrollEnd);
            mGridAdapter = null;
            mSettingsAdapter = null;
            mContentList.setLayoutManager(new LinearLayoutManager(getContext()));
            mContentList.setAdapter(mShelfAdapter);
        } else if (type == BrowseSection.TYPE_SETTINGS_GRID) {
            // The adapter is supplied separately via updateSection(SettingsGroup).
            mShelfAdapter = null;
            mGridAdapter = null;
        } else {
            mGridAdapter = new VideoCardAdapter(mGridCardWidth, mVideoClick, mVideoLongClick);
            mShelfAdapter = null;
            mSettingsAdapter = null;
            mContentList.setLayoutManager(new GridLayoutManager(getContext(), mGridSpan));
            mContentList.setAdapter(mGridAdapter);
            mContentList.addOnScrollListener(mGridScrollListener);
        }
    }

    // Hosting activity declares configChanges="orientation|..." so it is NOT recreated on
    // rotation; re-read the grid span (values-sw600dp-land widens it) and resize cards.
    // Only the grid section uses a GridLayoutManager; shelves/settings are left as-is.
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mGridSpan = getResources().getInteger(R.integer.mobile_grid_span);
        mGridCardWidth = getResources().getDisplayMetrics().widthPixels / mGridSpan;
        if (mContentList != null && mContentList.getLayoutManager() instanceof GridLayoutManager) {
            ((GridLayoutManager) mContentList.getLayoutManager()).setSpanCount(mGridSpan);
        }
        if (mGridAdapter != null) {
            mGridAdapter.setCardWidth(mGridCardWidth);
        }
    }

    private final VideoCardAdapter.OnVideoAction mVideoClick = video -> {
        if (mPresenter == null) {
            return;
        }
        // On the "Channels" multi-grid section, onVideoItemSelected mirrors the TV
        // two-pane behavior: it replaces the grid with the tapped channel's uploads
        // (meant for the TV's right-hand pane). The phone has a single grid, so that
        // silently overwrites the channel list — and it stays overwritten after returning
        // from the channel page until a manual pull-to-refresh. Skip the selection step
        // here; onVideoItemClicked still opens the channel page. Every other section keeps
        // onVideoItemSelected so scroll-position memory works.
        if (!mPresenter.isMultiGridChannelUploadsSection()) {
            mPresenter.onVideoItemSelected(video);
        }
        mPresenter.onVideoItemClicked(video);
    };

    private final VideoCardAdapter.OnVideoAction mVideoLongClick = video -> {
        if (mPresenter != null) {
            mPresenter.onVideoItemLongClicked(video);
        }
    };

    private final ShelfAdapter.OnShelfScrollEnd mShelfScrollEnd = last -> {
        if (mPresenter != null && last != null) {
            mPresenter.onScrollEnd(last);
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
