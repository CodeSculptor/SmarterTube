package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.SuggestionsController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.RemoteControlService;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.other.VideoPlayerGlue;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.disposables.Disposable;

/**
 * Phone player fragment: adds the upright/portrait layout on top of the shared
 * {@link PlaybackFragment}. Swapped in via the stmobile override of {@code fragment_playback.xml}
 * (the activity looks the fragment up by tag and only checks {@code instanceof PlaybackFragment},
 * so the TV code is untouched).
 *
 * In portrait with a regular (non-Shorts) video the player is constrained to a 16:9 strip at the
 * top and a below-video panel (title + description + channel/stats + up-next) fills the rest; in
 * landscape, for Shorts, and in PiP the player stays full-screen exactly like the TV build. While
 * in the strip the control rows are trimmed to the core actions ({@link
 * VideoPlayerGlue#setCompactControls}) — the full set returns in landscape. The up-next list
 * mirrors the same {@link VideoGroup}s the Leanback suggestions rows receive, and a row tap goes
 * through the standard {@link PlaybackPresenter#onSuggestionItemClicked(Video)} path.
 */
public class MobilePlaybackFragment extends PlaybackFragment {
    private ConstraintLayout mRoot;
    private LinearLayout mPanel;
    private TextView mTitleView;
    private TextView mExpandView;
    private TextView mDescriptionView;
    private TextView mChannelView;
    private TextView mSubsView;
    private TextView mViewsView;
    private RecyclerView mUpNextList;
    private UpNextRowAdapter mUpNextAdapter;
    private boolean mStripMode;
    /** Applied layout: 0 = full-screen, 1 = regular 16:9 strip, 2 = Shorts 9:16 strip. */
    private int mLayoutState;
    private String mLastVideoId;
    private VideoPlayerGlue mLastGlue;
    private boolean mLastCompact;

    // Shorts-specific chrome (action rail + back button + info bar below video).
    private View mShortsActionRail;
    private ImageButton mShortsBackBtn;
    private LinearLayout mShortsInfoBar;
    private TextView mShortsTitleView;
    private TextView mShortsChannelView;
    // Portrait meta-block like/dislike buttons (TextViews showing thumb + count) and channel avatar.
    private TextView mPortraitLikeBtn;
    private TextView mPortraitDislikeBtn;
    private ImageView mPortraitAvatarView;
    // Independent metadata fetch for the avatar — must NOT use the shared
    // MediaServiceManager singleton, whose single disposable is constantly disposed by the
    // player's own metadata loads (which would cancel our avatar request before it resolves).
    private Disposable mAvatarAction;
    private String mAvatarVideoId;

    // Action rail buttons — resolved lazily from within the included layout.
    private ImageView mShortsLikeBtn;
    private TextView mShortsLikeCount;
    private ImageView mShortsDislikeBtn;
    private ImageView mShortsCommentsBtn;
    private TextView mShortsCommentsCount;
    private ImageView mShortsChannelBtn;

    // Centred play/pause indicator (Shorts only — visual only, not a button).
    private ImageView mShortsPlayPauseBtn;

    // Bottom navigation bar and "You" profile sheet (Shorts only).
    private View mShortsNavBar;
    private View mShortsProfileScrim;
    private LinearLayout mShortsProfileSheet;

    // "Save to playlist" panel (Shorts only).
    private View mShortsPlaylistScrim;
    private LinearLayout mShortsPlaylistSheet;
    private ScrollView mShortsPlaylistScroll;
    private LinearLayout mShortsPlaylistList;
    private TextView mShortsPlaylistEmpty;
    private Disposable mPlaylistsAction;
    private String mPlaylistsVideoId;

    // Full-page swipe pager: all views that translate together as one Shorts page.
    // Nav bar is intentionally excluded — it stays fixed at the bottom like YT Shorts.
    private View[] mVideoPageViews;
    // Filmstrip posters: full-screen thumbnails of the prev/next Short, parked one screen above
    // / below and slid in with the drag so the adjacent video is visible while scrolling.
    private ImageView mShortsNextPoster;
    private ImageView mShortsPrevPoster;
    // +1 = swipe-up / next, -1 = swipe-down / prev, 0 = no pending animation.
    private int mLastSwipeDirection;

    // Raw touch drag tracking for the Shorts pager (ACTION_DOWN/MOVE/UP in interceptPlayerTouch).
    private float   mSwipeRawStartY;
    private float   mSwipeRawStartX;
    private long    mTouchDownTime;
    private boolean mShortsSwipeDragging;
    private int     mDragThresholdPx; // initialised to 15dp in initShortsViews

    // While committing a swipe, the incoming poster stays on screen covering the (loading) surface
    // until the new video is actually rendering — eliminates the black flash. A timeout is the
    // safety net in case the play signal never arrives (e.g. unplayable video).
    private boolean mAwaitingShortsFrame;
    private boolean mAwaitingShortsLoop;  // true while covering a PLAYBACK_MODE_ONE loop-restart
    private String  mSwipeFromVideoId;
    private static final int SHORTS_FRAME_POLL_MS = 50;
    private static final int SHORTS_FRAME_TIMEOUT_MS = 2500;
    // Tint applied to the rail like/dislike icon when active (YouTube blue).
    private static final int SHORTS_ACTIVE_TINT = 0xFF3EA6FF;

    // Held so we can re-send the lockscreen notification when the video changes.
    private MediaSessionCompat.Token mSessionToken;

    // Auto-hide for the Shorts overlay chrome (rail + back button).
    private final Handler mChromeHandler = new Handler();
    // Hides the Shorts chrome (action rail + back button + seek bar). Only ever scheduled when
    // the auto-hide setting is on, so firing always means "hide now".
    private final Runnable mHideShortsChr = () -> setShortsChrome(false);

    /**
     * Suggestion groups in arrival order. In strip mode the Leanback suggestion rows are kept out
     * of the rows adapter entirely (they render inside the 16:9 strip and their cards remain
     * hit-testable under the video), so this cache replays them into the rows when the player
     * returns to full-screen.
     */
    private final List<VideoGroup> mSuggestionGroups = new ArrayList<>();

    @Override
    public void onResume() {
        super.onResume();
        // Returning from another screen (e.g. the channel page opened from a Short) can leave the
        // Leanback overlay re-shown and the chrome stale. Invalidate the cached layout state so
        // applyMobileLayout does a full re-apply (ratio + control/decor visibility + chrome)
        // instead of early-returning because the state looks unchanged.
        mLayoutState = -1;
        applyMobileLayout();
    }

    @Override
    protected void onMediaSessionCreated(MediaSessionCompat session) {
        mSessionToken = session != null ? session.getSessionToken() : null;
        Context ctx = getContext();
        if (ctx == null) return;
        Intent intent = new Intent(ctx, RemoteControlService.class);
        if (mSessionToken != null) {
            intent.putExtra(RemoteControlService.EXTRA_SESSION_TOKEN, mSessionToken);
            ContextCompat.startForegroundService(ctx, intent);
        } else {
            ctx.startService(intent); // revert to plain notification; service stays for remote control
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mChromeHandler.removeCallbacks(mHideShortsChr);
        mChromeHandler.removeCallbacks(mShortsFramePoll);
        mChromeHandler.removeCallbacks(mShortsFrameTimeout);
        mChromeHandler.removeCallbacks(mShortsLoopPoll);
        mChromeHandler.removeCallbacks(mShortsLoopTimeout);
        mAwaitingShortsFrame = false;
        mAwaitingShortsLoop = false;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyMobileLayout();
    }

    @Override
    public void onPIPChanged(boolean isInPIP) {
        super.onPIPChanged(isInPIP);
        applyMobileLayout();
    }

    @Override
    public void setVideo(Video video) {
        super.setVideo(video);

        if (initPanelViews()) {
            bindHeader(video);
        }
        applyMobileLayout();

        // Keep the lockscreen notification header in sync with the current video.
        // The MediaSession persists across video changes, so onMediaSessionCreated isn't
        // re-fired — re-send the intent here so RemoteControlService rebuilds from the
        // new PlaybackPresenter.getVideo() value.
        if (mSessionToken != null && video != null) {
            Context ctx = getContext();
            if (ctx != null) {
                Intent intent = new Intent(ctx, RemoteControlService.class);
                intent.putExtra(RemoteControlService.EXTRA_SESSION_TOKEN, mSessionToken);
                ContextCompat.startForegroundService(ctx, intent);
            }
        }

        // Committed Short arrived: the exit animation parked the incoming poster at screen-centre
        // showing this video's thumbnail. Snap the live surface back to centre *underneath* the
        // poster and keep the poster up — it covers the load until the video actually renders
        // (see awaitShortsFrame), so there's no black flash.
        if (mLayoutState == 2 && mLastSwipeDirection != 0) {
            resetPageViewsToZero();
            ImageView keep = mLastSwipeDirection > 0 ? mShortsNextPoster : mShortsPrevPoster;
            ImageView drop = mLastSwipeDirection > 0 ? mShortsPrevPoster : mShortsNextPoster;
            if (drop != null) {
                drop.animate().cancel();
                drop.setVisibility(View.INVISIBLE);
                drop.setImageDrawable(null);
            }
            if (keep != null) {
                keep.animate().cancel();
                keep.setTranslationY(0); // hold centred, covering the loading surface
            }
            mLastSwipeDirection = 0;
        }
    }

    private void bindHeader(Video video) {
        if (video == null) {
            return;
        }

        // New video: start with the description collapsed (but don't collapse on the periodic
        // metadata refreshes of the same video).
        if (!TextUtils.equals(mLastVideoId, video.videoId)) {
            mLastVideoId = video.videoId;
            setDescriptionExpanded(false);
            loadAvatar(video);
        }

        mTitleView.setText(video.getTitle() != null ? video.getTitle() : "");
        mDescriptionView.setText(video.description != null ? video.description : "");
        mExpandView.setVisibility(TextUtils.isEmpty(video.description) ? View.GONE : View.VISIBLE);

        mChannelView.setText(video.getAuthor() != null ? video.getAuthor() : "");
        mSubsView.setText(video.subscriberCount != null ? video.subscriberCount : "");
        mViewsView.setText(extractViews(video));
        if (mPortraitLikeBtn != null) {
            mPortraitLikeBtn.setText(video.likeCount != null
                    ? Helpers.THUMB_UP + " " + video.likeCount : Helpers.THUMB_UP);
        }
        if (mPortraitDislikeBtn != null) {
            mPortraitDislikeBtn.setText(video.dislikeCount != null
                    ? Helpers.THUMB_DOWN + " " + video.dislikeCount : Helpers.THUMB_DOWN);
        }

        bindShortsChrome(video);
    }

    /**
     * Fetch the channel avatar for the portrait meta block. Uses an independent subscription on the
     * media service so it survives the player's own constant metadata loads (the shared
     * {@link MediaServiceManager} singleton disposes its single in-flight request on every call).
     */
    private void loadAvatar(Video video) {
        if (video == null || video.videoId == null) {
            return;
        }

        // Clear the stale portrait avatar while the new one loads. The Shorts rail channel button
        // keeps its default icon until the avatar arrives (it's always a visible action button).
        if (mPortraitAvatarView != null) mPortraitAvatarView.setVisibility(View.GONE);
        if (mShortsChannelBtn != null) mShortsChannelBtn.setImageResource(R.drawable.ic_shorts_channel);
        mAvatarVideoId = video.videoId;
        RxHelper.disposeActions(mAvatarAction);

        MediaItemService itemService = YouTubeServiceManager.instance().getMediaItemService();
        if (itemService == null) {
            return;
        }

        final String fetchId = video.videoId;
        mAvatarAction = RxHelper.execute(
                video.mediaItem != null
                        ? itemService.getMetadataObserve(video.mediaItem)
                        : itemService.getMetadataObserve(video.videoId, video.getPlaylistId(),
                                video.playlistIndex, video.playlistParams),
                (MediaItemMetadata metadata) -> {
                    if (!isAdded() || !TextUtils.equals(fetchId, mAvatarVideoId)) return;
                    String url = metadata != null ? metadata.getAuthorImageUrl() : null;
                    if (url == null || url.isEmpty()) return;
                    if (mPortraitAvatarView != null) {
                        Glide.with(MobilePlaybackFragment.this).load(url).circleCrop()
                                .into(mPortraitAvatarView);
                        mPortraitAvatarView.setVisibility(View.VISIBLE);
                    }
                    if (mShortsChannelBtn != null) {
                        // The Shorts rail channel button doubles as the channel avatar.
                        Glide.with(MobilePlaybackFragment.this).load(url).circleCrop()
                                .into(mShortsChannelBtn);
                    }
                },
                error -> {});
    }

    private void bindShortsChrome(Video video) {
        if (mShortsTitleView != null) {
            mShortsTitleView.setText(video.getTitle() != null ? video.getTitle() : "");
        }
        if (mShortsChannelView != null) {
            mShortsChannelView.setText(video.getAuthor() != null ? video.getAuthor() : "");
        }
        if (mShortsLikeCount != null) {
            mShortsLikeCount.setText(video.likeCount != null ? video.likeCount : "");
        }
        // Reflect the like/dislike state if metadata is already known (async updates come through
        // the setButtonState override).
        syncLikeDislikeTint();
    }

    /** Tint the rail like/dislike icons to match the current button state. */
    private void syncLikeDislikeTint() {
        boolean liked    = getButtonState(R.id.action_thumbs_up)   == PlayerUI.BUTTON_ON;
        boolean disliked = getButtonState(R.id.action_thumbs_down) == PlayerUI.BUTTON_ON;
        if (mShortsLikeBtn    != null) tintRailButton(mShortsLikeBtn,    liked);
        if (mShortsDislikeBtn != null) tintRailButton(mShortsDislikeBtn, disliked);
        tintPortraitButton(mPortraitLikeBtn,    liked);
        tintPortraitButton(mPortraitDislikeBtn, disliked);
    }

    private void tintPortraitButton(TextView btn, boolean active) {
        if (btn != null) btn.setTextColor(active ? SHORTS_ACTIVE_TINT : 0xFFAAAAAA);
    }

    private void tintRailButton(ImageView btn, boolean active) {
        if (active) {
            btn.setColorFilter(SHORTS_ACTIVE_TINT);
        } else {
            btn.clearColorFilter();
        }
    }

    private int currentButtonState(int buttonId) {
        return getButtonState(buttonId) == PlayerUI.BUTTON_ON ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF;
    }

    /** Mirror Leanback button-state changes (metadata load + click toggles) onto the rail icons. */
    @Override
    public void setButtonState(int buttonId, int buttonState) {
        super.setButtonState(buttonId, buttonState);
        boolean active = buttonState == PlayerUI.BUTTON_ON;
        if (buttonId == R.id.action_thumbs_up) {
            if (mShortsLikeBtn    != null) tintRailButton(mShortsLikeBtn,    active);
            tintPortraitButton(mPortraitLikeBtn, active);
        } else if (buttonId == R.id.action_thumbs_down) {
            if (mShortsDislikeBtn    != null) tintRailButton(mShortsDislikeBtn,    active);
            tintPortraitButton(mPortraitDislikeBtn, active);
        }
    }

    /** Views/date: the first non-author segment of "Author • views • date". */
    private String extractViews(Video video) {
        String second = Helpers.toString(video.getSecondTitle());
        String author = video.getAuthor();
        if (second != null) {
            for (String segment : second.split(Video.TERTIARY_TEXT_DELIM)) {
                segment = segment.trim();
                if (!segment.isEmpty() && (author == null || !segment.equals(author.trim()))) {
                    return segment;
                }
            }
        }
        return "";
    }

    private void setDescriptionExpanded(boolean expanded) {
        if (mDescriptionView != null) {
            mDescriptionView.setVisibility(expanded ? View.VISIBLE : View.GONE);
            mExpandView.setText(expanded ? "▴" : "▾");
        }
    }

    @Override
    public void updateSuggestions(VideoGroup group) {
        // In strip mode the rows stay empty — see mSuggestionGroups.
        if (!mStripMode) {
            super.updateSuggestions(group);
        }

        if (group == null || group.isEmpty() || group.getAction() == VideoGroup.ACTION_SYNC) {
            return; // SYNC = metadata refresh of existing items; the rows don't show live counters
        }

        mSuggestionGroups.add(group);

        // Chapters and Shorts shelves are rows-only content — the panel is a plain up-next list.
        if (group.isShorts() || group.isChapters()) {
            return;
        }

        if (initPanelViews()) {
            if (group.getAction() == VideoGroup.ACTION_REPLACE) {
                mUpNextAdapter.clear();
            }
            mUpNextAdapter.appendVideos(filterCurrent(group.getVideos()));
        }
    }

    @Override
    public void removeSuggestions(VideoGroup group) {
        super.removeSuggestions(group);

        if (group != null) {
            mSuggestionGroups.remove(group);
            if (initPanelViews()) {
                mUpNextAdapter.remove(group.getVideos());
            }
        }
    }

    @Override
    public void clearSuggestions() {
        super.clearSuggestions();

        mSuggestionGroups.clear();
        if (initPanelViews()) {
            mUpNextAdapter.clear();
        }
    }

    /** The suggestions usually lead with the video that is already playing — skip it. */
    private List<Video> filterCurrent(List<Video> videos) {
        Video current = PlaybackPresenter.instance(getContext()).getVideo();
        if (videos == null || current == null || current.videoId == null) {
            return videos;
        }

        List<Video> result = new ArrayList<>(videos.size());
        for (Video video : videos) {
            if (video != null && !current.videoId.equals(video.videoId)) {
                result.add(video);
            }
        }
        return result;
    }

    @Override
    public void onDispatchTouchEvent(MotionEvent event) {
        // Touches below the 16:9 player strip belong to the up-next panel — ignore them.
        View playerView = getView();
        if (mStripMode && playerView != null && event.getY() > playerView.getBottom()) {
            return;
        }
        // In Shorts mode all touch is managed by interceptPlayerTouch (drag pager + tap-toggle).
        // Returning here prevents Leanback's double-tap seek from firing in Shorts.
        if (mLayoutState == 2) {
            return;
        }
        super.onDispatchTouchEvent(event);
    }

    /**
     * Touch fix for the faded overlay: Leanback "hides" the controls by fading them out, leaving
     * every button VISIBLE and hit-testable (fine on TV — no touchscreen). A tap on the supposedly
     * empty video could press an invisible control. In Shorts mode this also drives the vertical
     * pager drag and the tap-to-toggle play/pause behavior.
     * Called from the activity's dispatchTouchEvent; returns true to consume the event.
     */
    public boolean interceptPlayerTouch(MotionEvent event) {
        View playerView = getView();
        if (playerView == null) return false;

        // Shorts: all touch is managed here (drag pager + tap-to-toggle).
        if (mLayoutState == 2) {
            return handleShortsTouchEvent(event);
        }

        // Non-Shorts: original phantom-tap guard.
        if (isOverlayShown()) return false;
        if (event.getY() > playerView.getBottom()) return false;
        onDispatchTouchEvent(event); // overlay tickle + double-tap seek
        return true;
    }

    private boolean handleShortsTouchEvent(MotionEvent event) {
        // Profile sheet open: taps on the sheet reach its row / handle listeners; a tap anywhere
        // else dismisses it. The pager and play-toggle stay disabled while the sheet is up.
        if (mShortsProfileSheet != null && mShortsProfileSheet.getVisibility() == View.VISIBLE) {
            if (viewContainsRaw(mShortsProfileSheet, event.getRawX(), event.getRawY())) {
                return false; // let the sheet handle it
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                closeShortsProfileSheet();
            }
            return true; // consume everything outside the sheet
        }

        // Same treatment for the "Save to playlist" panel.
        if (mShortsPlaylistSheet != null && mShortsPlaylistSheet.getVisibility() == View.VISIBLE) {
            if (viewContainsRaw(mShortsPlaylistSheet, event.getRawX(), event.getRawY())) {
                return false; // let the sheet handle it (including its internal scroll)
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                closeShortsPlaylistSheet();
            }
            return true; // consume everything outside the sheet
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mSwipeRawStartY = event.getY();
                mSwipeRawStartX = event.getX();
                mTouchDownTime = android.os.SystemClock.uptimeMillis();
                mShortsSwipeDragging = false;
                return false; // let DOWN propagate so button OnClickListeners still work

            case MotionEvent.ACTION_MOVE: {
                float dy = event.getY() - mSwipeRawStartY;
                if (!mShortsSwipeDragging && Math.abs(dy) > mDragThresholdPx) {
                    mShortsSwipeDragging = true;
                    mChromeHandler.removeCallbacks(mHideShortsChr);
                    setShortsChrome(false);
                    // Abandon any in-flight transition from a previous swipe before starting fresh.
                    mAwaitingShortsFrame = false;
                    mChromeHandler.removeCallbacks(mShortsFramePoll);
                    mChromeHandler.removeCallbacks(mShortsFrameTimeout);
                    prepareShortsPosters(); // load prev/next thumbnails for the filmstrip
                }
                if (mShortsSwipeDragging) {
                    setShortsPageTranslation(dy);
                    return true;
                }
                return false;
            }

            case MotionEvent.ACTION_UP: {
                if (mShortsSwipeDragging) {
                    mShortsSwipeDragging = false;
                    onShortsSwipeReleased(event.getY() - mSwipeRawStartY);
                    return true;
                }
                float dY = Math.abs(event.getY() - mSwipeRawStartY);
                float dX = Math.abs(event.getX() - mSwipeRawStartX);
                long dur = android.os.SystemClock.uptimeMillis() - mTouchDownTime;
                if (dY < mDragThresholdPx && dX < mDragThresholdPx && dur < 350) {
                    if (touchHitsButton(event.getRawX(), event.getRawY())) {
                        return false; // let normal dispatch fire the button's onClick
                    }
                    onShortsTap();
                    revealShortsChrome();
                    return true;
                }
                return false;
            }

            case MotionEvent.ACTION_CANCEL:
                if (mShortsSwipeDragging) {
                    mShortsSwipeDragging = false;
                    animateShortsPageTo(0, this::hideShortsPosters);
                }
                return false;
        }
        return false;
    }

    private boolean touchHitsButton(float rawX, float rawY) {
        return viewContainsRaw(mShortsActionRail, rawX, rawY)
            || viewContainsRaw(mShortsBackBtn, rawX, rawY)
            || viewContainsRaw(mShortsNavBar, rawX, rawY)
            || (mShortsProfileSheet != null
                && mShortsProfileSheet.getVisibility() == View.VISIBLE
                && viewContainsRaw(mShortsProfileSheet, rawX, rawY))
            || (mShortsPlaylistSheet != null
                && mShortsPlaylistSheet.getVisibility() == View.VISIBLE
                && viewContainsRaw(mShortsPlaylistSheet, rawX, rawY));
    }

    private boolean viewContainsRaw(View v, float rawX, float rawY) {
        if (v == null || v.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return rawX >= loc[0] && rawX <= loc[0] + v.getWidth()
            && rawY >= loc[1] && rawY <= loc[1] + v.getHeight();
    }

    /** Re-evaluate and apply strip vs full-screen layout. Safe to call at any time. */
    public void applyMobileLayout() {
        if (!initPanelViews()) {
            return;
        }

        boolean portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        Video video = PlaybackPresenter.instance(getContext()).getVideo();
        boolean isShorts = video != null && video.isShorts;
        boolean inPip = isInPipMode();

        // Strip mode now also covers Shorts: the player becomes a top-aligned aspect-ratio strip
        // instead of a vertically-centered full-screen surface. Regular videos get a 16:9 strip with
        // the up-next panel below; a Short gets a 9:16 strip with the Shorts info bar + action rail.
        boolean strip = portrait && !inPip && video != null;
        boolean showPanel = strip && !isShorts;
        String ratio = isShorts ? "H,9:16" : "H,16:9";

        syncCompactControls(strip);

        applyOverlayDecorVisibility(strip);

        // Keyed on the 3-value state (not just the boolean) so a regular<->Shorts switch — both of
        // which are "strip" — still re-applies the new dimension ratio.
        int layoutState = !strip ? 0 : (isShorts ? 2 : 1);
        if (layoutState == mLayoutState) {
            return;
        }
        mLayoutState = layoutState;
        mStripMode = strip;

        // Hide auto-hide chrome and dismiss any open sheet immediately on any layout change.
        mChromeHandler.removeCallbacks(mHideShortsChr);
        setShortsChrome(false);
        closeShortsProfileSheet();
        closeShortsPlaylistSheet();
        // Reset any in-progress swipe animation / pending frame wait.
        mAwaitingShortsFrame = false;
        mChromeHandler.removeCallbacks(mShortsFramePoll);
        mChromeHandler.removeCallbacks(mShortsFrameTimeout);
        setShortsPageTranslation(0);
        hideShortsPosters();

        // Show/hide the Shorts info bar vs. the regular panel.
        if (mShortsInfoBar != null) {
            mShortsInfoBar.setVisibility(isShorts && strip ? View.VISIBLE : View.GONE);
        }
        if (mShortsNavBar != null) {
            mShortsNavBar.setVisibility(strip ? View.VISIBLE : View.GONE);
        }

        // Leanback suggestion rows: removed while in the strip (they draw inside the small video
        // area and stay clickable underneath it), replayed from the cache on return to full-screen.
        if (strip) {
            super.clearSuggestions();
        } else {
            for (VideoGroup group : mSuggestionGroups) {
                super.updateSuggestions(group);
            }
        }

        ConstraintSet set = new ConstraintSet();
        set.clone(mRoot);
        if (strip) {
            set.clear(R.id.playback_controls_fragment, ConstraintSet.BOTTOM);
            set.setDimensionRatio(R.id.playback_controls_fragment, ratio);
            set.setVisibility(R.id.mobile_below_video_panel, showPanel ? View.VISIBLE : View.GONE);
            set.setVisibility(R.id.mobile_shorts_info_bar, isShorts ? View.VISIBLE : View.GONE);
            set.setVisibility(R.id.mobile_shorts_nav_bar, View.VISIBLE);
        } else {
            set.connect(R.id.playback_controls_fragment, ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            set.setDimensionRatio(R.id.playback_controls_fragment, null);
            set.setVisibility(R.id.mobile_below_video_panel, View.GONE);
            set.setVisibility(R.id.mobile_shorts_info_bar, View.GONE);
            set.setVisibility(R.id.mobile_shorts_nav_bar, View.GONE);
        }
        set.applyTo(mRoot);

        // Non-Shorts: make sure the Leanback control row is visible (a prior Shorts auto-hide
        // may have left it INVISIBLE). In Shorts, setShortsChrome owns the control row.
        if (layoutState != 2) setShortsControlsVisible(true);

        // Shorts shows only the seek bar over full-bleed video — kill the Leanback dim scrim
        // (BG_LIGHT, set at fragment creation) so the video isn't darkened. Restore it for
        // regular/full-screen playback.
        setBackgroundType(layoutState == 2 ? BG_NONE : BG_LIGHT);

        if (layoutState == 2) {
            // The overlay was hidden by onStart()/createPlayerGlue() before mLayoutState reached
            // 2. Bring it to its rest position once (and lazy-inflate the rows); the guard in
            // hideControlsOverlay then keeps it shown for the whole Shorts session. From here the
            // seek bar is shown/hidden by setShortsChrome (direct view visibility), never via
            // Leanback's translate animation — which was leaving it stuck near the top.
            showControlsOverlay(false);
            revealShortsChrome();
        }
    }

    @Override
    public void showControlsOverlay(boolean runAnimation) {
        super.showControlsOverlay(runAnimation);
        // The overlay row views are created lazily on the first reveal — re-apply here so the
        // strip tweaks land no matter when the views appear.
        applyOverlayDecorVisibility(mStripMode);
        // Non-Shorts: ensure the control row is visible after the lazy inflate. In Shorts the
        // control row (transport buttons + seek bar + time) is owned by setShortsChrome.
        if (mLayoutState != 2) setShortsControlsVisible(true);
        // Back button follows the player controls on all non-Shorts pages.
        if (mShortsBackBtn != null && mLayoutState != 2) mShortsBackBtn.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideControlsOverlay(boolean runAnimation) {
        // In Shorts the Leanback overlay stays shown for the whole session: its only visible
        // part is the seek bar, which we show/hide directly via setShortsChrome. Blocking the
        // Leanback hide here (used by e.g. PlayerUIController's auto-hide) avoids the translate
        // animation that left the seek bar stuck near the top of the screen.
        if (mLayoutState == 2) return;
        if (mShortsBackBtn != null) mShortsBackBtn.setVisibility(View.INVISIBLE);
        super.hideControlsOverlay(runAnimation);
    }

    @Override
    protected void setBackgroundResource(int resId) {
        // Skip the overlay scrim in Shorts — only the seek bar shows, and the dim would just
        // darken the video. Belt-and-suspenders with BG_NONE in applyMobileLayout, since
        // PlaybackFragment.updatePlayerBackground re-applies this drawable on every show.
        if (mLayoutState == 2) return;
        super.setBackgroundResource(resId);
    }

    /**
     * Hide the Leanback transport buttons (play/pause/skip/time) while keeping the seek bar
     * visible for Shorts. Controls are inflated lazily — null-checks are intentional; this is
     * also called from showControlsOverlay() to catch the first inflate.
     */
    /**
     * Toggle the whole Leanback control row (transport buttons + seek bar + time readout). In
     * Shorts this is driven by setShortsChrome so the controls auto-hide together with the action
     * rail; toggling the row's view visibility directly avoids Leanback's translate animation
     * (which left the seek bar stuck near the top). INVISIBLE, not GONE, keeps the row's layout
     * slot so nothing reflows when it comes back.
     */
    private void setShortsControlsVisible(boolean visible) {
        if (getView() == null) return;
        View transportRow = getView().findViewById(R.id.transport_row);
        if (transportRow != null) {
            transportRow.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    /**
     * Show or hide the full Shorts overlay chrome: action rail + back button + the entire Leanback
     * control row (play/pause, skip, CC, seek bar, time). They all reveal and auto-hide together.
     */
    private void setShortsChrome(boolean visible) {
        int vis = visible ? View.VISIBLE : View.INVISIBLE;
        if (mShortsActionRail != null) mShortsActionRail.setVisibility(vis);
        // Back button is always-visible in regular portrait; only auto-hide it in Shorts.
        if (mShortsBackBtn != null && mLayoutState == 2) mShortsBackBtn.setVisibility(vis);
        if (mLayoutState == 2) setShortsControlsVisible(visible);
    }

    /**
     * Reveal the Shorts overlay chrome, then auto-hide after 3 seconds — but only when the player
     * UI auto-hide setting is enabled. When it's set to "Never" (timeout 0) the chrome stays put.
     * No-op outside Shorts.
     */
    private void revealShortsChrome() {
        if (mLayoutState != 2) return;
        // A Short→Short swipe rebuilds the glue with everything visible and skips applyMobileLayout
        // (same layout state), so re-hide the strip decor (title/quality/date) on every reveal.
        applyOverlayDecorVisibility(true);
        setShortsChrome(true);
        mChromeHandler.removeCallbacks(mHideShortsChr);
        if (PlayerData.instance(getContext()).getUiHideTimeoutSec() > 0) {
            mChromeHandler.postDelayed(mHideShortsChr, 3000);
        }
    }

    /** Tap on the video area: toggle play/pause and flash the indicator icon. */
    private void onShortsTap() {
        boolean wasPlaying = isPlaying();
        // setPlayWhenReady drives the ExoPlayer engine directly (same path the landscape
        // transport play/pause button uses) — onPlayClicked/onPauseClicked only notify
        // listeners and don't actually toggle the engine.
        setPlayWhenReady(!wasPlaying);
        showShortsPlayPauseIcon(!wasPlaying);
    }

    /** Flash the play/pause indicator icon briefly, then fade it out. */
    private void showShortsPlayPauseIcon(boolean isPlay) {
        if (mShortsPlayPauseBtn == null) return;
        mShortsPlayPauseBtn.setImageResource(
                isPlay ? R.drawable.ic_shorts_play : R.drawable.ic_shorts_pause);
        mShortsPlayPauseBtn.setAlpha(1f);
        mShortsPlayPauseBtn.setVisibility(View.VISIBLE);
        mShortsPlayPauseBtn.animate().cancel();
        mShortsPlayPauseBtn.animate()
                .alpha(0f).setDuration(600).setStartDelay(600)
                .withEndAction(() -> {
                    mShortsPlayPauseBtn.setVisibility(View.INVISIBLE);
                    mShortsPlayPauseBtn.setAlpha(1f);
                }).start();
    }

    /** Navigate to a browse section and close the Shorts player. */
    private void navigateToSection(int sectionType) {
        BrowsePresenter.instance(getContext()).selectSection(sectionType);
        requireActivity().onBackPressed();
    }

    /** Slide the "You" profile sheet up from the bottom. */
    private void openShortsProfileSheet() {
        if (mShortsProfileSheet == null || mShortsProfileScrim == null) return;
        if (mShortsProfileSheet.getVisibility() == View.VISIBLE) return;

        // Populate account header.
        try {
            Account account = YouTubeServiceManager.instance().getSignInService().getSelectedAccount();
            TextView nameView = mShortsProfileSheet.findViewById(R.id.profile_name);
            TextView emailView = mShortsProfileSheet.findViewById(R.id.profile_email);
            ImageView avatarView = mShortsProfileSheet.findViewById(R.id.profile_avatar);
            if (account != null) {
                if (nameView != null)
                    nameView.setText(account.getName() != null ? account.getName() : "Account");
                if (emailView != null)
                    emailView.setText(account.getEmail() != null ? account.getEmail() : "");
                if (avatarView != null && account.getAvatarImageUrl() != null) {
                    Glide.with(this).load(account.getAvatarImageUrl()).circleCrop().into(avatarView);
                }
            }
        } catch (Exception ignored) {}

        mShortsProfileScrim.setAlpha(0f);
        mShortsProfileScrim.setVisibility(View.VISIBLE);
        mShortsProfileScrim.animate().alpha(1f).setDuration(200).start();

        mShortsProfileSheet.setVisibility(View.VISIBLE);
        mShortsProfileSheet.post(() -> {
            int h = mShortsProfileSheet.getHeight();
            if (h > 0) {
                mShortsProfileSheet.setTranslationY(h);
                mShortsProfileSheet.animate().translationY(0).setDuration(250).start();
            }
        });
    }

    /** Slide the "You" profile sheet back down and hide it. */
    private void closeShortsProfileSheet() {
        if (mShortsProfileSheet == null || mShortsProfileScrim == null) return;
        if (mShortsProfileSheet.getVisibility() != View.VISIBLE) return;

        mShortsProfileScrim.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> mShortsProfileScrim.setVisibility(View.GONE)).start();

        int h = mShortsProfileSheet.getHeight();
        mShortsProfileSheet.animate().translationY(h > 0 ? h : 500).setDuration(200)
                .withEndAction(() -> {
                    mShortsProfileSheet.setVisibility(View.GONE);
                    mShortsProfileSheet.setTranslationY(0);
                }).start();
    }

    /**
     * Slide the "Save to playlist" panel up from the bottom for the video currently on screen,
     * fetching its playlist membership. Mirrors {@link #openShortsProfileSheet()}; the row list
     * height is capped in {@link #capShortsPlaylistScrollHeight()} once the rows are populated.
     */
    private void openShortsPlaylistSheet() {
        if (mShortsPlaylistSheet == null || mShortsPlaylistScrim == null) return;
        if (mShortsPlaylistSheet.getVisibility() == View.VISIBLE) return;

        closeShortsProfileSheet();

        Video video = PlaybackPresenter.instance(getContext()).getVideo();
        if (video == null || video.videoId == null) return;

        if (mShortsPlaylistList != null) mShortsPlaylistList.removeAllViews();
        if (mShortsPlaylistEmpty != null) mShortsPlaylistEmpty.setVisibility(View.GONE);
        if (mShortsPlaylistScroll != null) {
            ViewGroup.LayoutParams params = mShortsPlaylistScroll.getLayoutParams();
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            mShortsPlaylistScroll.setLayoutParams(params);
        }

        mShortsPlaylistScrim.setAlpha(0f);
        mShortsPlaylistScrim.setVisibility(View.VISIBLE);
        mShortsPlaylistScrim.animate().alpha(1f).setDuration(200).start();

        mShortsPlaylistSheet.setVisibility(View.VISIBLE);
        mShortsPlaylistSheet.post(() -> {
            int h = mShortsPlaylistSheet.getHeight();
            if (h > 0) {
                mShortsPlaylistSheet.setTranslationY(h);
                mShortsPlaylistSheet.animate().translationY(0).setDuration(250).start();
            }
        });

        mPlaylistsVideoId = video.videoId;
        RxHelper.disposeActions(mPlaylistsAction);
        MediaItemService itemService = YouTubeServiceManager.instance().getMediaItemService();
        if (itemService == null) return;

        final String fetchId = video.videoId;
        mPlaylistsAction = RxHelper.execute(
                itemService.getPlaylistsInfoObserve(fetchId),
                (List<PlaylistInfo> playlistInfos) -> {
                    if (!isAdded() || !TextUtils.equals(fetchId, mPlaylistsVideoId)) return;
                    populateShortsPlaylistRows(video, playlistInfos);
                },
                error -> {});
    }

    /** Slide the "Save to playlist" panel back down and hide it. */
    private void closeShortsPlaylistSheet() {
        if (mShortsPlaylistSheet == null || mShortsPlaylistScrim == null) return;
        if (mShortsPlaylistSheet.getVisibility() != View.VISIBLE) return;

        mShortsPlaylistScrim.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> mShortsPlaylistScrim.setVisibility(View.GONE)).start();

        int h = mShortsPlaylistSheet.getHeight();
        mShortsPlaylistSheet.animate().translationY(h > 0 ? h : 500).setDuration(200)
                .withEndAction(() -> {
                    mShortsPlaylistSheet.setVisibility(View.GONE);
                    mShortsPlaylistSheet.setTranslationY(0);
                }).start();
    }

    /** Build one checkbox row per playlist and cap the panel's height once populated. */
    private void populateShortsPlaylistRows(Video video, List<PlaylistInfo> playlistInfos) {
        if (mShortsPlaylistList == null) return;
        mShortsPlaylistList.removeAllViews();

        if (playlistInfos == null || playlistInfos.isEmpty()) {
            if (mShortsPlaylistEmpty != null) mShortsPlaylistEmpty.setVisibility(View.VISIBLE);
            return;
        }
        if (mShortsPlaylistEmpty != null) mShortsPlaylistEmpty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (PlaylistInfo info : playlistInfos) {
            View row = inflater.inflate(R.layout.mobile_shorts_playlist_row, mShortsPlaylistList, false);
            CheckBox checkBox = row.findViewById(R.id.playlist_row_checkbox);
            TextView title = row.findViewById(R.id.playlist_row_title);
            if (title != null) title.setText(info.getTitle());
            if (checkBox != null) checkBox.setChecked(info.isSelected());
            row.setOnClickListener(v -> {
                boolean newState = checkBox == null || !checkBox.isChecked();
                if (checkBox != null) checkBox.setChecked(newState);
                AppDialogUtil.toggleVideoInPlaylist(getContext(), video, null, info, newState);
            });
            mShortsPlaylistList.addView(row);
        }

        mShortsPlaylistSheet.post(this::capShortsPlaylistScrollHeight);
    }

    /**
     * Caps the panel to the space between the bottom of the video and the top of the nav bar, so
     * it never grows up over the video. In the regular portrait strip (state 1) the video is a
     * 16:9 box at the top, leaving a tall below-video area; in Shorts (state 2) the video fills the
     * screen, so the top boundary falls back to the screen top (the panel may overlay the video,
     * matching the "You" sheet). Once the row list would exceed the cap it scrolls internally
     * instead of pushing the panel taller.
     */
    private void capShortsPlaylistScrollHeight() {
        if (mShortsPlaylistSheet == null || mShortsPlaylistScroll == null || mShortsNavBar == null) return;

        // nav bar top and video bottom are both measured relative to the shared parent (mRoot).
        int navBarTop = mShortsNavBar.getTop();

        int topBoundary = 0; // Shorts: allow the panel up to the screen top.
        if (mLayoutState == 1) {
            View videoView = getView();
            if (videoView != null) {
                topBoundary = videoView.getBottom(); // regular portrait: keep it below the video.
            }
        }

        int availableForSheet = navBarTop - topBoundary;
        int fixedChrome = mShortsPlaylistSheet.getHeight() - mShortsPlaylistScroll.getHeight();
        int availableForScroll = availableForSheet - fixedChrome;

        ViewGroup.LayoutParams params = mShortsPlaylistScroll.getLayoutParams();
        if (availableForScroll > 0 && mShortsPlaylistScroll.getHeight() > availableForScroll) {
            params.height = availableForScroll;
        } else {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        mShortsPlaylistScroll.setLayoutParams(params);
    }

    /** Full-screen height used as the page stride and poster park distance. */
    private int shortsPageHeight() {
        if (mRoot != null && mRoot.getHeight() > 0) return mRoot.getHeight();
        return getView() != null ? getView().getHeight() : 0;
    }

    /**
     * Apply a logical drag offset {@code d} to the whole pager (null-safe):
     * the current page sits at {@code d}, the next poster one screen below at {@code screenH + d},
     * the previous poster one screen above at {@code -screenH + d}.
     */
    private void setShortsPageTranslation(float d) {
        int screenH = shortsPageHeight();
        if (mVideoPageViews != null) {
            for (View v : mVideoPageViews) {
                if (v == null) continue;
                v.animate().cancel(); // a snap must win over any in-flight commit animation
                v.setTranslationY(d);
            }
        }
        if (mShortsNextPoster != null) {
            mShortsNextPoster.animate().cancel();
            mShortsNextPoster.setTranslationY(screenH + d);
        }
        if (mShortsPrevPoster != null) {
            mShortsPrevPoster.animate().cancel();
            mShortsPrevPoster.setTranslationY(-screenH + d);
        }
    }

    /** Animate the whole pager to logical drag offset {@code d}. Callback fires once, at the end. */
    private void animateShortsPageTo(float d, Runnable onEnd) {
        int screenH = shortsPageHeight();
        boolean firedCallback = false;
        if (mVideoPageViews != null) {
            for (View v : mVideoPageViews) {
                if (v == null) continue;
                v.animate().cancel();
                android.view.ViewPropertyAnimator anim = v.animate().translationY(d).setDuration(250);
                if (onEnd != null && !firedCallback) {
                    anim.withEndAction(onEnd);
                    firedCallback = true;
                }
                anim.start();
            }
        }
        animatePoster(mShortsNextPoster, screenH + d);
        animatePoster(mShortsPrevPoster, -screenH + d);
    }

    private void animatePoster(View poster, float target) {
        if (poster == null) return;
        poster.animate().cancel();
        poster.animate().translationY(target).setDuration(250).start();
    }

    /** Load the prev/next Short thumbnails and reveal the posters for the duration of a drag. */
    private void prepareShortsPosters() {
        SuggestionsController sc =
                PlaybackPresenter.instance(getContext()).getController(SuggestionsController.class);
        loadPoster(mShortsNextPoster, sc != null ? sc.getNext() : null);
        loadPoster(mShortsPrevPoster, sc != null ? sc.getPrevious() : null);
    }

    private void loadPoster(ImageView poster, Video video) {
        if (poster == null) return;
        String url = video != null ? video.getCardImageUrl() : null;
        if (url != null) {
            poster.animate().cancel();
            poster.setAlpha(1f);
            poster.setVisibility(View.VISIBLE);
            Glide.with(this).load(url).into(poster);
        } else {
            poster.setVisibility(View.INVISIBLE);
            poster.setImageDrawable(null);
        }
    }

    private void hideShortsPosters() {
        if (mShortsNextPoster != null) {
            mShortsNextPoster.setVisibility(View.INVISIBLE);
            mShortsNextPoster.setImageDrawable(null);
        }
        if (mShortsPrevPoster != null) {
            mShortsPrevPoster.setVisibility(View.INVISIBLE);
            mShortsPrevPoster.setImageDrawable(null);
        }
    }

    /** On finger release, commit to next/prev or spring back based on drag distance. */
    private void onShortsSwipeReleased(float dy) {
        int screenH = shortsPageHeight();
        float thresh = screenH * 0.15f;
        if (dy < -thresh) {
            mLastSwipeDirection = 1;
            animateShortsPageTo(-screenH, null); // current page exits up, next poster fills screen
            commitShortsNavigation(true);
        } else if (dy > thresh) {
            mLastSwipeDirection = -1;
            animateShortsPageTo(screenH, null); // current page exits down, prev poster fills screen
            commitShortsNavigation(false);
        } else {
            // Below threshold: spring back and drop the posters once we're home.
            animateShortsPageTo(0, this::hideShortsPosters);
        }
    }

    private void commitShortsNavigation(boolean next) {
        Video current = PlaybackPresenter.instance(getContext()).getVideo();
        mSwipeFromVideoId = current != null ? current.videoId : null;
        if (next) {
            PlaybackPresenter.instance(getContext()).onNextClicked();
        } else {
            PlaybackPresenter.instance(getContext()).onPreviousClicked();
        }
        awaitShortsFrame();
    }

    /** Snap the page views (video surface + chrome) back to centre — posters are left untouched. */
    private void resetPageViewsToZero() {
        if (mVideoPageViews == null) return;
        for (View v : mVideoPageViews) {
            if (v == null) continue;
            v.animate().cancel();
            v.setTranslationY(0);
        }
    }

    /**
     * Keep the incoming poster covering the loading surface until the new Short is actually
     * playing (ExoPlayer reports isPlaying only once the first frame is rendered), then reveal
     * the live video. A timeout guards against a play signal that never comes.
     */
    private void awaitShortsFrame() {
        mAwaitingShortsFrame = true;
        mChromeHandler.removeCallbacks(mShortsFramePoll);
        mChromeHandler.removeCallbacks(mShortsFrameTimeout);
        mChromeHandler.postDelayed(mShortsFramePoll, SHORTS_FRAME_POLL_MS);
        mChromeHandler.postDelayed(mShortsFrameTimeout, SHORTS_FRAME_TIMEOUT_MS);
    }

    private final Runnable mShortsFramePoll = new Runnable() {
        @Override
        public void run() {
            if (!mAwaitingShortsFrame) return;
            Video v = PlaybackPresenter.instance(getContext()).getVideo();
            boolean switched = v != null && v.videoId != null
                    && !v.videoId.equals(mSwipeFromVideoId);
            if (switched && isPlaying()) {
                finishShortsTransition();
            } else {
                mChromeHandler.postDelayed(this, SHORTS_FRAME_POLL_MS);
            }
        }
    };

    private final Runnable mShortsFrameTimeout = this::finishShortsTransition;

    // Covers the black frame at position 0 that occurs when a Short loops via PLAYBACK_MODE_ONE.
    // In 31.94 upstream changed setPositionMs(100) → setPositionMs(0); the seek briefly blanks
    // the surface. We intercept setPositionMs, show a poster, and remove it once rendering resumes.
    @Override
    public void setPositionMs(long positionMs) {
        if (mLayoutState == 2 && positionMs < 500L
                && !mAwaitingShortsFrame && !mAwaitingShortsLoop) {
            Video current = PlaybackPresenter.instance(getContext()).getVideo();
            loadPoster(mShortsNextPoster, current);
            awaitShortsLoopFrame();
        }
        super.setPositionMs(positionMs);
    }

    private void awaitShortsLoopFrame() {
        mAwaitingShortsLoop = true;
        mChromeHandler.removeCallbacks(mShortsLoopPoll);
        mChromeHandler.removeCallbacks(mShortsLoopTimeout);
        mChromeHandler.postDelayed(mShortsLoopPoll, SHORTS_FRAME_POLL_MS);
        mChromeHandler.postDelayed(mShortsLoopTimeout, SHORTS_FRAME_TIMEOUT_MS);
    }

    private final Runnable mShortsLoopPoll = new Runnable() {
        @Override
        public void run() {
            if (!mAwaitingShortsLoop) return;
            if (isPlaying() && getPositionMs() > 50) {
                finishShortsLoop();
            } else {
                mChromeHandler.postDelayed(this, SHORTS_FRAME_POLL_MS);
            }
        }
    };

    private final Runnable mShortsLoopTimeout = this::finishShortsLoop;

    private void finishShortsLoop() {
        mAwaitingShortsLoop = false;
        mChromeHandler.removeCallbacks(mShortsLoopPoll);
        mChromeHandler.removeCallbacks(mShortsLoopTimeout);
        fadeOutPoster(mShortsNextPoster);
    }

    /** New Short is rendering (or timed out): fade the covering poster away and warm the next one. */
    private void finishShortsTransition() {
        mAwaitingShortsFrame = false;
        mChromeHandler.removeCallbacks(mShortsFramePoll);
        mChromeHandler.removeCallbacks(mShortsFrameTimeout);
        fadeOutPoster(mShortsNextPoster);
        fadeOutPoster(mShortsPrevPoster);
        // The swipe hid the chrome + seek bar; bring them back for the freshly-loaded Short and
        // restart the auto-hide cycle (no-op if the overlay isn't in Shorts mode anymore).
        revealShortsChrome();
        warmNextShort();
    }

    private void fadeOutPoster(ImageView poster) {
        if (poster == null || poster.getVisibility() != View.VISIBLE) return;
        poster.animate().cancel();
        poster.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            poster.setVisibility(View.INVISIBLE);
            poster.setImageDrawable(null);
            poster.setAlpha(1f);
        }).start();
    }

    /**
     * Pre-fetch the next Short's format info so the following swipe loads faster. The format-info
     * cache is single-slot, so we only warm the most-likely direction (next).
     */
    private void warmNextShort() {
        SuggestionsController sc =
                PlaybackPresenter.instance(getContext()).getController(SuggestionsController.class);
        Video next = sc != null ? sc.getNext() : null;
        if (next != null) {
            MediaServiceManager.instance().loadFormatInfo(next, info -> {});
        }
    }

    /**
     * The overlay's metadata block (title/views/date over the video) duplicates the header below
     * the strip, and the quality badge + clock collide with the buttons in the narrow strip —
     * hide all three there; landscape full-screen keeps them (no header exists).
     */
    private void applyOverlayDecorVisibility(boolean strip) {
        if (getActivity() == null) {
            return;
        }
        for (int id : new int[]{R.id.controls_card, R.id.quality_info, R.id.date_time}) {
            View view = getActivity().findViewById(id);
            if (view != null) {
                view.setVisibility(strip ? View.GONE : View.VISIBLE);
            }
        }
    }

    /**
     * Trim/restore the control rows for the current mode. The glue is recreated with the full
     * action set on every engine init, so re-apply whenever the instance or the mode changes.
     */
    private void syncCompactControls(boolean compact) {
        VideoPlayerGlue glue = getPlayerGlue();
        if (glue != null && (glue != mLastGlue || compact != mLastCompact)) {
            glue.setCompactControls(compact);
            mLastGlue = glue;
            mLastCompact = compact;
        }
    }

    private boolean isInPipMode() {
        Activity activity = getActivity();
        return VERSION.SDK_INT >= VERSION_CODES.N && activity != null && activity.isInPictureInPictureMode();
    }

    /**
     * The panel views are siblings of this fragment in the activity layout and don't exist yet
     * while the fragment itself is being inflated — resolve them lazily.
     */
    private boolean initPanelViews() {
        if (mPanel != null) {
            return true;
        }

        Activity activity = getActivity();
        if (activity == null) {
            return false;
        }

        mRoot = activity.findViewById(R.id.mobile_playback_root);
        mPanel = activity.findViewById(R.id.mobile_below_video_panel);
        mTitleView = activity.findViewById(R.id.mobile_video_title);
        mExpandView = activity.findViewById(R.id.mobile_video_expand);
        mDescriptionView = activity.findViewById(R.id.mobile_video_description);
        mChannelView = activity.findViewById(R.id.mobile_video_channel);
        mSubsView = activity.findViewById(R.id.mobile_video_subs);
        mViewsView = activity.findViewById(R.id.mobile_video_views);
        mUpNextList = activity.findViewById(R.id.mobile_up_next_list);

        if (mPanel == null || mRoot == null) {
            mPanel = null;
            return false;
        }

        View titleRow = activity.findViewById(R.id.mobile_video_title_row);
        titleRow.setOnClickListener(v ->
                setDescriptionExpanded(mDescriptionView.getVisibility() != View.VISIBLE));
        // Long descriptions scroll inside the text view instead of pushing the list away.
        mDescriptionView.setMovementMethod(new android.text.method.ScrollingMovementMethod());
        mDescriptionView.setOnClickListener(v -> setDescriptionExpanded(false));

        mUpNextAdapter = new UpNextRowAdapter(
                video -> PlaybackPresenter.instance(getContext()).onSuggestionItemClicked(video));
        mUpNextList.setLayoutManager(new LinearLayoutManager(getContext()));
        mUpNextList.setAdapter(mUpNextAdapter);

        View channelRow = activity.findViewById(R.id.mobile_video_channel_row);
        if (channelRow != null) {
            channelRow.setOnClickListener(v -> {
                Video current = PlaybackPresenter.instance(getContext()).getVideo();
                if (current != null) ChannelPresenter.instance(getContext()).openChannel(current);
            });
        }

        mPortraitAvatarView = activity.findViewById(R.id.mobile_video_channel_avatar);
        mPortraitLikeBtn = activity.findViewById(R.id.mobile_video_like_btn);
        mPortraitDislikeBtn = activity.findViewById(R.id.mobile_video_dislike_btn);
        if (mPortraitLikeBtn != null) {
            mPortraitLikeBtn.setOnClickListener(v ->
                    PlaybackPresenter.instance(getContext())
                            .onButtonClicked(R.id.action_thumbs_up,
                                    currentButtonState(R.id.action_thumbs_up)));
        }
        if (mPortraitDislikeBtn != null) {
            mPortraitDislikeBtn.setOnClickListener(v ->
                    PlaybackPresenter.instance(getContext())
                            .onButtonClicked(R.id.action_thumbs_down,
                                    currentButtonState(R.id.action_thumbs_down)));
        }

        initShortsViews(activity);

        return true;
    }

    private void initShortsViews(Activity activity) {
        mShortsActionRail = activity.findViewById(R.id.mobile_shorts_action_rail);
        mShortsBackBtn = activity.findViewById(R.id.mobile_shorts_back_btn);
        mShortsInfoBar = activity.findViewById(R.id.mobile_shorts_info_bar);
        mShortsTitleView = activity.findViewById(R.id.shorts_bar_title);
        mShortsChannelView = activity.findViewById(R.id.shorts_bar_channel);

        if (mShortsActionRail != null) {
            mShortsLikeBtn = mShortsActionRail.findViewById(R.id.mobile_shorts_like_btn);
            mShortsLikeCount = mShortsActionRail.findViewById(R.id.mobile_shorts_like_count);
            mShortsDislikeBtn = mShortsActionRail.findViewById(R.id.mobile_shorts_dislike_btn);
            mShortsCommentsBtn = mShortsActionRail.findViewById(R.id.mobile_shorts_comments_btn);
            mShortsCommentsCount = mShortsActionRail.findViewById(R.id.mobile_shorts_comments_count);
            mShortsChannelBtn = mShortsActionRail.findViewById(R.id.mobile_shorts_channel_btn);

            if (mShortsLikeBtn != null) {
                // Pass the CURRENT state so the controller toggles correctly (BUTTON_ON -> remove).
                mShortsLikeBtn.setOnClickListener(v ->
                        PlaybackPresenter.instance(getContext())
                                .onButtonClicked(R.id.action_thumbs_up, currentButtonState(R.id.action_thumbs_up)));
            }
            if (mShortsDislikeBtn != null) {
                mShortsDislikeBtn.setOnClickListener(v ->
                        PlaybackPresenter.instance(getContext())
                                .onButtonClicked(R.id.action_thumbs_down, currentButtonState(R.id.action_thumbs_down)));
            }
            if (mShortsCommentsBtn != null) {
                // Comments are triggered via the chat action (CommentsController listens for action_chat).
                mShortsCommentsBtn.setOnClickListener(v ->
                        PlaybackPresenter.instance(getContext()).onButtonClicked(R.id.action_chat, 0));
            }
            if (mShortsChannelBtn != null) {
                mShortsChannelBtn.setOnClickListener(v ->
                        PlaybackPresenter.instance(getContext()).onButtonClicked(R.id.action_channel, 0));
            }
        }

        // Back button: dismiss an open sheet if any, otherwise exit the player.
        if (mShortsBackBtn != null) {
            mShortsBackBtn.setOnClickListener(v -> {
                if (mShortsProfileSheet != null
                        && mShortsProfileSheet.getVisibility() == View.VISIBLE) {
                    closeShortsProfileSheet();
                } else if (mShortsPlaylistSheet != null
                        && mShortsPlaylistSheet.getVisibility() == View.VISIBLE) {
                    closeShortsPlaylistSheet();
                } else {
                    requireActivity().onBackPressed();
                }
            });
        }

        // Centred play/pause indicator (ImageView — visual only, not tappable directly).
        mShortsPlayPauseBtn = activity.findViewById(R.id.mobile_shorts_play_pause_btn);

        // Bottom navigation bar.
        mShortsNavBar = activity.findViewById(R.id.mobile_shorts_nav_bar);
        if (mShortsNavBar != null) {
            // Ensure nav bar draws on top of the profile sheet during slide-up animation.
            mShortsNavBar.bringToFront();
            mShortsNavBar.findViewById(R.id.shorts_nav_home).setOnClickListener(v ->
                    navigateToSection(MediaGroup.TYPE_HOME));
            mShortsNavBar.findViewById(R.id.shorts_nav_shorts).setOnClickListener(v ->
                    navigateToSection(MediaGroup.TYPE_SHORTS));
            mShortsNavBar.findViewById(R.id.shorts_nav_subscriptions).setOnClickListener(v ->
                    navigateToSection(MediaGroup.TYPE_SUBSCRIPTIONS));
            mShortsNavBar.findViewById(R.id.shorts_nav_playlists).setOnClickListener(v -> {
                if (mShortsPlaylistSheet != null
                        && mShortsPlaylistSheet.getVisibility() == View.VISIBLE) {
                    closeShortsPlaylistSheet();
                } else {
                    openShortsPlaylistSheet();
                }
            });
            mShortsNavBar.findViewById(R.id.shorts_nav_you).setOnClickListener(v -> {
                if (mShortsProfileSheet != null
                        && mShortsProfileSheet.getVisibility() == View.VISIBLE) {
                    closeShortsProfileSheet();
                } else {
                    openShortsProfileSheet();
                }
            });
        }

        // "You" profile sheet and scrim.
        mShortsProfileScrim = activity.findViewById(R.id.mobile_shorts_profile_scrim);
        mShortsProfileSheet = activity.findViewById(R.id.mobile_shorts_profile_sheet);
        if (mShortsProfileScrim != null) {
            mShortsProfileScrim.setOnClickListener(v -> closeShortsProfileSheet());
        }
        if (mShortsProfileSheet != null) {
            mShortsProfileSheet.findViewById(R.id.profile_sheet_handle).setOnClickListener(v ->
                    closeShortsProfileSheet());
            mShortsProfileSheet.findViewById(R.id.profile_row_channel).setOnClickListener(v -> {
                closeShortsProfileSheet();
                PlaybackPresenter.instance(getContext()).onButtonClicked(R.id.action_channel, 0);
            });
            mShortsProfileSheet.findViewById(R.id.profile_row_history).setOnClickListener(v ->
                    navigateToSection(MediaGroup.TYPE_HISTORY));
            mShortsProfileSheet.findViewById(R.id.profile_row_playlists).setOnClickListener(v ->
                    navigateToSection(MediaGroup.TYPE_USER_PLAYLISTS));
            mShortsProfileSheet.findViewById(R.id.profile_row_settings).setOnClickListener(v ->
                    navigateToSection(MediaGroup.TYPE_SETTINGS));
        }

        // "Save to playlist" panel and scrim.
        mShortsPlaylistScrim = activity.findViewById(R.id.mobile_shorts_playlist_scrim);
        mShortsPlaylistSheet = activity.findViewById(R.id.mobile_shorts_playlist_sheet);
        if (mShortsPlaylistScrim != null) {
            mShortsPlaylistScrim.setOnClickListener(v -> closeShortsPlaylistSheet());
        }
        if (mShortsPlaylistSheet != null) {
            mShortsPlaylistSheet.findViewById(R.id.playlist_sheet_handle).setOnClickListener(v ->
                    closeShortsPlaylistSheet());
            mShortsPlaylistScroll = mShortsPlaylistSheet.findViewById(R.id.playlist_sheet_scroll);
            mShortsPlaylistList = mShortsPlaylistSheet.findViewById(R.id.playlist_sheet_list);
            mShortsPlaylistEmpty = mShortsPlaylistSheet.findViewById(R.id.playlist_sheet_empty);
        }

        // Filmstrip posters for the swipe pager.
        mShortsNextPoster = activity.findViewById(R.id.mobile_shorts_next_poster);
        mShortsPrevPoster = activity.findViewById(R.id.mobile_shorts_prev_poster);

        // Drag threshold: 15dp before a MOVE is classified as a page drag (not a tap).
        mDragThresholdPx = (int) (15 * getResources().getDisplayMetrics().density);

        // All views that translate together as one Shorts page.
        // Nav bar is excluded — it stays fixed at the bottom of the screen.
        mVideoPageViews = new View[]{
            getView(),          // video surface + Leanback overlay
            mShortsActionRail,
            mShortsBackBtn,
            mShortsPlayPauseBtn,
            mShortsInfoBar
        };
    }
}
