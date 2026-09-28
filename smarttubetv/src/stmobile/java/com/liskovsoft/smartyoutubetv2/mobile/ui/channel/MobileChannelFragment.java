package com.liskovsoft.smartyoutubetv2.mobile.ui.channel;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import android.app.AlertDialog;
import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import java.util.List;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.data.NotificationState;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.mobile.ui.browse.VideoCardAdapter;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

/**
 * Native portrait Channel screen. Implements {@link ChannelView} and is driven by the
 * existing {@link ChannelPresenter} unchanged — group loading, video clicks, sort options
 * and the menu dialog all flow through the presenter.
 *
 * Each content group the presenter emits (Videos / Shorts / Live / Playlists …) becomes a
 * tab: a {@link ViewPager2} page showing that group as a 2-column grid, with a Material
 * {@link TabLayout} strip linked by {@link TabLayoutMediator}. Per-tab pagination routes
 * back through {@code presenter.onScrollEnd}; see {@link ChannelTabsAdapter}.
 */
public class MobileChannelFragment extends Fragment implements ChannelView {
    private ChannelPresenter mPresenter;
    private boolean mFragmentCreated = true;
    private ViewPager2 mPager;
    private TabLayout mTabs;
    private TabLayoutMediator mTabsMediator;
    private SwipeRefreshLayout mSwipeRefresh;
    private ProgressBar mProgressBar;
    private TextView mTitleView;
    private ChannelTabsAdapter mTabsAdapter;
    private boolean mSwipeRefreshing;
    private TextView mSubscribeButton;
    private ImageView mBellButton;
    private View mHeaderView;
    private ImageView mAvatarView;
    private TextView mSubsHeaderView;
    private MediaItemService mItemService;
    private boolean mSubscribed;
    private boolean mSubscriptionResolved;
    private Disposable mSubscribeAction;
    private Disposable mHeaderAction;
    private List<NotificationState> mNotificationStates;
    // In-channel search: upstream ChannelPresenter.onSearchSubmit emits its result as an
    // ACTION_REPLACE with this id at position 0; it becomes its own "Search: ..." tab.
    private static final int SEARCH_GROUP_ID = 112;
    // Our re-sorted Videos tab (the id upstream's sort uses for its replacement row).
    private static final int SORT_GROUP_ID = 144;
    private View mSearchBar;
    private EditText mSearchInput;
    private String mSearchQuery;
    private View mSortScroll;
    private LinearLayout mSortChips;
    private List<MediaGroup> mSortOptions;
    private int mSortIdx;
    private int mSortTabId = -1;
    private Disposable mSortOptionsAction;
    private Disposable mSortAction;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.mobile_channel_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPager = view.findViewById(R.id.channel_pager);
        mTabs = view.findViewById(R.id.channel_tabs);
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mProgressBar = view.findViewById(R.id.progress_bar);
        mTitleView = view.findViewById(R.id.channel_title);
        mSubscribeButton = view.findViewById(R.id.btn_subscribe);
        mBellButton = (ImageView) view.findViewById(R.id.btn_bell);
        mHeaderView = view.findViewById(R.id.channel_header);
        mAvatarView = view.findViewById(R.id.channel_avatar);
        mSubsHeaderView = view.findViewById(R.id.channel_subs);

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        mSearchBar = view.findViewById(R.id.channel_search_bar);
        mSearchInput = view.findViewById(R.id.channel_search_input);
        mSortScroll = view.findViewById(R.id.channel_sort_scroll);
        mSortChips = view.findViewById(R.id.channel_sort_chips);
        setupSearch(view);

        int span = getResources().getInteger(R.integer.mobile_grid_span);
        mTabsAdapter = new ChannelTabsAdapter(getResources().getDisplayMetrics().widthPixels, span,
                mVideoClick, mVideoLongClick,
                last -> {
                    if (mPresenter != null && last != null) {
                        mPresenter.onScrollEnd(last);
                    }
                });
        mPager.setAdapter(mTabsAdapter);

        // TabLayoutMediator observes the adapter, so tabs appear as the presenter emits
        // groups. Show the strip only once there's more than one tab.
        mTabsMediator = new TabLayoutMediator(mTabs, mPager,
                (tab, position) -> tab.setText(mTabsAdapter.getTitle(position)));
        mTabsMediator.attach();
        mTabsAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                updateTabStripVisibility();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                updateTabStripVisibility();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                updateTabStripVisibility();
            }
        });

        mSwipeRefresh.setColorSchemeResources(R.color.brand_accent);
        mSwipeRefresh.setProgressBackgroundColorSchemeResource(R.color.mobile_surface);
        // Each page hosts a vertically-scrolling grid inside a horizontally-paging
        // ViewPager2; only let pull-to-refresh fire when the active grid is at the top.
        mSwipeRefresh.setOnChildScrollUpCallback((parent, child) -> {
            RecyclerView grid = currentPageGrid();
            return grid != null && grid.canScrollVertically(-1);
        });
        // SwipeRefreshLayout otherwise steals the horizontal drag and cancels the page
        // swipe (it only completes on a fast fling). Disable the refresh gesture while the
        // pager is dragging/settling so a slow swipe between tabs is honoured, then re-enable
        // it when the pager is idle so pull-to-refresh keeps working.
        mPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrollStateChanged(int state) {
                mSwipeRefresh.setEnabled(state == ViewPager2.SCROLL_STATE_IDLE);
            }

            @Override
            public void onPageSelected(int position) {
                updateSortChipsVisibility();
            }
        });
        mSwipeRefresh.setOnRefreshListener(() -> {
            // ChannelPresenter has no public refresh(); openChannel(id) is the reload path
            // (clears the view and re-fetches the channel rows).
            String channelId = mPresenter != null ? mPresenter.getChannelId() : null;
            if (channelId != null) {
                mSwipeRefreshing = true;
                mPresenter.openChannel(channelId);
            } else {
                stopSwipeRefresh();
            }
        });

        mPresenter = ChannelPresenter.instance(getContext());
        mPresenter.setView(this);

        Video channel = mPresenter.getChannel();
        if (channel != null && channel.getTitle() != null) {
            mTitleView.setText(channel.getTitle());
        }

        setupSubscribeButton(channel);

        mPresenter.onViewInitialized();
    }

    /**
     * Wires the title-bar Subscribe pill. Shown only when a channel id is resolvable.
     *
     * The initial label is a provisional guess from the channel {@link Video} (same heuristic as
     * the video long-press menu). A pure-channel item carries no videoId, so its true state isn't
     * known up front — once the channel's video shelves arrive we load metadata for the first
     * upload and refine the pill from the video metadata's subscription flag (the same source the
     * player uses). See {@link #maybeResolveSubscribedState(VideoGroup)}.
     */
    private void setupSubscribeButton(@Nullable Video channel) {
        if (mSubscribeButton == null) {
            return;
        }

        String channelId = mPresenter != null ? mPresenter.getChannelId() : null;
        if (channelId == null) {
            mSubscribeButton.setVisibility(View.GONE);
            return;
        }

        mItemService = YouTubeServiceManager.instance().getMediaItemService();
        mSubscribed = channel != null
                && (channel.isSubscribed || channel.belongsToSubscriptions() || channel.belongsToChannelUploads());

        updateSubscribeButton();
        mSubscribeButton.setVisibility(View.VISIBLE);
        mSubscribeButton.setOnClickListener(v -> toggleSubscribe());
    }

    private void updateSubscribeButton() {
        if (mSubscribeButton == null) {
            return;
        }

        mSubscribeButton.setText(mSubscribed ? R.string.subscribed_to_channel : R.string.action_subscribe);

        // setBackgroundResource can clobber the XML padding; capture and restore it.
        int left = mSubscribeButton.getPaddingLeft();
        int top = mSubscribeButton.getPaddingTop();
        int right = mSubscribeButton.getPaddingRight();
        int bottom = mSubscribeButton.getPaddingBottom();
        mSubscribeButton.setBackgroundResource(
                mSubscribed ? R.drawable.bg_subscribe_pill_active : R.drawable.bg_subscribe_pill);
        mSubscribeButton.setPadding(left, top, right, bottom);

        mSubscribeButton.setTextColor(getResources().getColor(
                mSubscribed ? R.color.mobile_text_secondary : R.color.brand_accent_on));
    }

    private void toggleSubscribe() {
        String channelId = mPresenter != null ? mPresenter.getChannelId() : null;
        if (channelId == null || mItemService == null) {
            return;
        }

        RxHelper.disposeActions(mSubscribeAction);

        Observable<Void> observable = mSubscribed
                ? mItemService.unsubscribeObserve(channelId) : mItemService.subscribeObserve(channelId);
        mSubscribeAction = RxHelper.execute(observable);

        mSubscribed = !mSubscribed;

        Video channel = mPresenter.getChannel();
        if (channel != null) {
            channel.isSubscribed = mSubscribed;
        }

        updateSubscribeButton();
        updateBellButton();
        MessageHelpers.showMessage(getContext(), getString(
                mSubscribed ? R.string.subscribed_to_channel : R.string.unsubscribed_from_channel));
    }

    private void updateBellButton() {
        if (mBellButton == null) {
            return;
        }
        boolean show = mSubscribed && mNotificationStates != null && !mNotificationStates.isEmpty();
        mBellButton.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            mBellButton.setOnClickListener(v -> showBellDialog());
        }
    }

    private void showBellDialog() {
        if (mNotificationStates == null || mNotificationStates.isEmpty() || getContext() == null) {
            return;
        }
        String[] titles = new String[mNotificationStates.size()];
        int checkedItem = -1;
        for (int i = 0; i < mNotificationStates.size(); i++) {
            NotificationState s = mNotificationStates.get(i);
            titles[i] = s.getTitle() != null ? s.getTitle() : "";
            if (s.isSelected()) {
                checkedItem = i;
            }
        }
        final int[] selected = {checkedItem};
        new AlertDialog.Builder(getContext())
                .setSingleChoiceItems(titles, checkedItem, (dialog, which) -> selected[0] = which)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (selected[0] >= 0 && selected[0] < mNotificationStates.size()) {
                        NotificationState state = mNotificationStates.get(selected[0]);
                        MediaServiceManager.instance().setNotificationState(state,
                                e -> MessageHelpers.showMessage(getContext(),
                                        "Couldn't update notification preference"));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Resolves the real subscription state once, from the channel's first loaded upload. A channel
     * video's metadata carries whether the signed-in user is subscribed (the player's source of
     * truth), so this corrects the provisional pill label without touching shared/common code.
     */
    private void maybeResolveSubscribedState(VideoGroup group) {
        if (mSubscriptionResolved || group == null || mSubscribeButton == null
                || mSubscribeButton.getVisibility() != View.VISIBLE) {
            return;
        }

        Video video = firstPlayable(group);
        if (video == null) {
            return;
        }

        mSubscriptionResolved = true;

        // Use an independent subscription on the media service rather than the shared
        // MediaServiceManager singleton: when this page is opened from an active player (e.g. a
        // Short), the player's own constant metadata loads dispose the singleton's single in-flight
        // request, so the header/subscription callback would never arrive.
        if (mItemService == null) {
            mItemService = YouTubeServiceManager.instance().getMediaItemService();
        }
        if (mItemService == null) {
            return;
        }

        RxHelper.disposeActions(mHeaderAction);
        mHeaderAction = RxHelper.execute(
                video.mediaItem != null
                        ? mItemService.getMetadataObserve(video.mediaItem)
                        : mItemService.getMetadataObserve(video.videoId, video.getPlaylistId(),
                                video.playlistIndex, video.playlistParams),
                (MediaItemMetadata metadata) -> {
                    if (!isAdded() || metadata == null) {
                        return;
                    }
                    mSubscribed = metadata.isSubscribed();
                    Video channel = mPresenter != null ? mPresenter.getChannel() : null;
                    if (channel != null) {
                        channel.isSubscribed = mSubscribed;
                    }
                    updateSubscribeButton();
                    List<NotificationState> states = metadata.getNotificationStates();
                    if (states != null && !states.isEmpty()) {
                        mNotificationStates = states;
                    }
                    updateBellButton();
                    bindHeader(metadata.getAuthorImageUrl(), metadata.getSubscriberCount());
                },
                error -> {});
    }

    private void bindHeader(String avatarUrl, String subscriberCount) {
        boolean hasAvatar = avatarUrl != null && !avatarUrl.isEmpty();
        boolean hasSubs = subscriberCount != null && !subscriberCount.isEmpty();
        if (!hasAvatar && !hasSubs) return;
        if (mAvatarView != null && hasAvatar) {
            Glide.with(this).load(avatarUrl).circleCrop().into(mAvatarView);
        }
        if (mSubsHeaderView != null) {
            mSubsHeaderView.setText(hasSubs ? subscriberCount : "");
        }
        if (mHeaderView != null) mHeaderView.setVisibility(View.VISIBLE);
    }

    private static Video firstPlayable(VideoGroup group) {
        if (group.getVideos() == null) {
            return null;
        }
        for (Video video : group.getVideos()) {
            if (video != null && video.videoId != null) {
                return video;
            }
        }
        return null;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Back from the player: the presenter pushes changed percent-watched as ACTION_SYNC.
        // Skip the first resume (right after creation) so it doesn't consume the changes
        // before the screen underneath gets them (same guard as MobileSearchFragment).
        if (!mFragmentCreated && mPresenter != null) {
            mPresenter.onViewResumed();
        }
        mFragmentCreated = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mTabsMediator != null) {
            mTabsMediator.detach();
        }
        RxHelper.disposeActions(mSubscribeAction, mHeaderAction, mSortOptionsAction, mSortAction);
        if (mPresenter != null) {
            mPresenter.onViewDestroyed();
        }
    }

    private void updateTabStripVisibility() {
        if (mTabs != null && mTabsAdapter != null) {
            mTabs.setVisibility(mTabsAdapter.getItemCount() > 1 ? View.VISIBLE : View.GONE);
        }
    }

    /** The grid {@link RecyclerView} of the page currently shown by the pager, or null. */
    private RecyclerView currentPageGrid() {
        if (mPager == null || !(mPager.getChildAt(0) instanceof RecyclerView)) {
            return null;
        }
        RecyclerView inner = (RecyclerView) mPager.getChildAt(0);
        RecyclerView.ViewHolder holder = inner.findViewHolderForAdapterPosition(mPager.getCurrentItem());
        return holder != null && holder.itemView instanceof RecyclerView
                ? (RecyclerView) holder.itemView : null;
    }

    // ----- ChannelView -----

    @Override
    public void update(VideoGroup group) {
        if (group == null || mTabsAdapter == null) {
            return;
        }
        switch (group.getAction()) {
            case VideoGroup.ACTION_REPLACE:
                if (group.getPosition() >= 0) {
                    // A positioned replace (upstream in-channel search; TV swaps one row at
                    // the top) becomes one tab at that position; the other tabs stay.
                    if (!group.isEmpty()) {
                        String title = group.getId() == SEARCH_GROUP_ID && mSearchQuery != null
                                ? getString(R.string.mobile_channel_search_tab, mSearchQuery) : group.getTitle();
                        int idx = mTabsAdapter.putTab(group.getPosition(), group, title);
                        mPager.setCurrentItem(idx, false);
                        scrollCurrentPageToTop();
                    }
                    break;
                }
                // A full replace wipes the tabs before appending.
                mTabsAdapter.clear();
                resetSortTab();
                if (!group.isEmpty()) {
                    appendTab(group);
                }
                break;
            case VideoGroup.ACTION_REMOVE:
            case VideoGroup.ACTION_REMOVE_AUTHOR:
                mTabsAdapter.removeVideos(group.getVideos());
                break;
            case VideoGroup.ACTION_SYNC:
                // Percent-watched refresh (e.g. back from the player): redraw the watched bars.
                mTabsAdapter.syncVideos(group.getVideos());
                break;
            default: // ACTION_APPEND / ACTION_PREPEND — a new group is a new tab, a
                     // continuation appends to its existing page.
                if (!group.isEmpty()) {
                    appendTab(group);
                }
                break;
        }
    }

    private void appendTab(VideoGroup group) {
        mTabsAdapter.appendGroup(group);
        maybeResolveSubscribedState(group);
        maybePickSortTab(group);
    }

    @Override
    public void setPosition(int index) {
        // Touch UI: no D-pad focus to restore. The in-channel search result is already
        // selected as its own tab in update() (upstream calls setPosition(1) after it).
    }

    @Override
    public void showProgressBar(boolean show) {
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
    public void clear() {
        if (mTabsAdapter != null) {
            mTabsAdapter.clear();
        }
        resetSortTab();
    }

    // ----- sort (Latest / Popular / Oldest) -----

    /**
     * The first plain-video tab (not Shorts, not playlists) is the channel's Videos tab, the
     * one YouTube's sort chips apply to. Pick it once, then fetch its sort options.
     */
    private void maybePickSortTab(VideoGroup group) {
        if (mSortTabId != -1 || group.isShorts() || firstPlayable(group) == null) {
            return;
        }
        if (VideoCardAdapter.styleOf(group.getVideos().get(0)) != VideoCardAdapter.STYLE_VIDEO) {
            return;
        }
        mSortTabId = group.getId();
        mSortIdx = 0;
        if (mSortOptions != null) {
            bindSortChips();
        } else {
            loadSortOptions();
        }
    }

    private void resetSortTab() {
        mSortTabId = -1;
        RxHelper.disposeActions(mSortAction);
        updateSortChipsVisibility();
    }

    private void loadSortOptions() {
        String channelId = mPresenter != null ? mPresenter.getChannelId() : null;
        ContentService service = YouTubeServiceManager.instance().getContentService();
        if (channelId == null || service == null || RxHelper.isAnyActionRunning(mSortOptionsAction)) {
            return;
        }
        mSortOptionsAction = RxHelper.execute(service.getChannelSortingOptionsObserve(channelId),
                (List<MediaGroup> options) -> {
                    if (!isAdded()) {
                        return;
                    }
                    mSortOptions = options;
                    bindSortChips();
                },
                error -> {});
    }

    private void bindSortChips() {
        if (mSortChips == null || getContext() == null) {
            return;
        }
        mSortChips.removeAllViews();
        if (mSortOptions != null) {
            float density = getResources().getDisplayMetrics().density;
            int padH = Math.round(12 * density);
            int padV = Math.round(6 * density);
            for (int i = 0; i < mSortOptions.size(); i++) {
                MediaGroup option = mSortOptions.get(i);
                if (option == null || TextUtils.isEmpty(option.getTitle())) {
                    continue;
                }
                final int idx = i;
                boolean selected = i == mSortIdx;
                TextView chip = new TextView(getContext());
                chip.setText(option.getTitle());
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                chip.setBackgroundResource(R.drawable.bg_sort_chip);
                chip.setPadding(padH, padV, padH, padV);
                chip.setSelected(selected);
                chip.setTextColor(getResources().getColor(
                        selected ? R.color.mobile_background : R.color.mobile_text_primary));
                chip.setOnClickListener(c -> onSortChipClicked(idx));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMarginEnd(Math.round(8 * density));
                mSortChips.addView(chip, lp);
            }
        }
        updateSortChipsVisibility();
    }

    private void onSortChipClicked(int idx) {
        ContentService service = YouTubeServiceManager.instance().getContentService();
        if (idx == mSortIdx || mSortOptions == null || idx >= mSortOptions.size() || service == null) {
            return;
        }
        mSortIdx = idx;
        bindSortChips();
        RxHelper.disposeActions(mSortAction);
        showProgressBar(true);
        // Same as upstream ChannelPresenter.onSearchSettingsClicked: each option is a
        // continuation that loads the Videos tab in that order.
        mSortAction = RxHelper.execute(service.continueGroupObserve(mSortOptions.get(idx)),
                (MediaGroup mediaGroup) -> {
                    showProgressBar(false);
                    int tab = mTabsAdapter != null ? mTabsAdapter.indexOfId(mSortTabId) : -1;
                    if (!isAdded() || mediaGroup == null || tab < 0) {
                        return;
                    }
                    VideoGroup sorted = VideoGroup.from(mediaGroup);
                    sorted.setId(SORT_GROUP_ID);
                    mTabsAdapter.replaceTab(tab, sorted);
                    mSortTabId = SORT_GROUP_ID;
                    scrollCurrentPageToTop();
                },
                error -> showProgressBar(false));
    }

    private void updateSortChipsVisibility() {
        if (mSortScroll == null) {
            return;
        }
        boolean show = mSortTabId != -1 && mSortChips != null && mSortChips.getChildCount() > 1
                && mPager != null && mTabsAdapter != null
                && mTabsAdapter.getId(mPager.getCurrentItem()) == mSortTabId;
        mSortScroll.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ----- in-channel search -----

    private void setupSearch(View root) {
        root.findViewById(R.id.btn_channel_search).setOnClickListener(v -> {
            if (mSearchBar.getVisibility() == View.VISIBLE) {
                submitSearch();
            } else {
                mSearchBar.setVisibility(View.VISIBLE);
                mSearchInput.requestFocus();
                showKeyboard(true);
            }
        });
        root.findViewById(R.id.btn_channel_search_close).setOnClickListener(v -> closeSearch());
        mSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN)) {
                submitSearch();
                return true;
            }
            return false;
        });
    }

    private void submitSearch() {
        String query = mSearchInput.getText() != null ? mSearchInput.getText().toString().trim() : "";
        if (query.isEmpty() || mPresenter == null) {
            return;
        }
        mSearchQuery = query;
        showKeyboard(false);
        mSearchInput.clearFocus();
        mPresenter.onSearchSubmit(query);
    }

    private void closeSearch() {
        showKeyboard(false);
        mSearchInput.setText("");
        mSearchInput.clearFocus();
        mSearchBar.setVisibility(View.GONE);
        mSearchQuery = null;
        if (mTabsAdapter != null) {
            mTabsAdapter.removeTabById(SEARCH_GROUP_ID);
        }
    }

    private void showKeyboard(boolean show) {
        InputMethodManager imm = getContext() != null
                ? (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE) : null;
        if (imm == null || mSearchInput == null) {
            return;
        }
        if (show) {
            imm.showSoftInput(mSearchInput, InputMethodManager.SHOW_IMPLICIT);
        } else {
            imm.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
        }
    }

    private void scrollCurrentPageToTop() {
        if (mPager == null) {
            return;
        }
        mPager.post(() -> {
            RecyclerView grid = currentPageGrid();
            if (grid != null) {
                grid.scrollToPosition(0);
            }
        });
    }

    // Hosting activity declares configChanges="orientation|..." so it is NOT recreated on
    // rotation; re-read the grid span (values-sw600dp-land widens it) and re-flow every tab.
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mTabsAdapter != null) {
            mTabsAdapter.setSize(getResources().getDisplayMetrics().widthPixels,
                    getResources().getInteger(R.integer.mobile_grid_span));
        }
        // The re-flow repopulates the tab strip, which scrolls back to the start; bring the
        // selected tab back into view.
        if (mTabs != null && mPager != null) {
            mTabs.post(() -> mTabs.setScrollPosition(mPager.getCurrentItem(), 0f, true));
        }
    }

    // ----- callbacks -----

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
}
