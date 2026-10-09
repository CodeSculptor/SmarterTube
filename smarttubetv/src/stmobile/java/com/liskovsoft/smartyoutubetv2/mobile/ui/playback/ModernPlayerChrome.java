package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.mobile.ui.browse.MobileSettingsActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.other.VideoPlayerGlue;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.previewtimebar.StoryboardManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Phone-style player controls for regular videos (#46, the Modern and Tap to pause player styles).
 *
 * The Leanback control rows stay alive but hidden (their action set, state and auto-hide timer keep
 * working); this class draws its own chrome over the video and routes every button through the
 * same glue actions the Leanback buttons use, so behaviour is identical to Classic. Its visibility
 * follows one source of truth, the Leanback overlay state ({@link MobilePlaybackFragment#isOverlayShown()}),
 * checked on every show/hide and on a short poll, so no code path can leave the two out of step.
 *
 * Portrait: top bar (close, CC, settings), centre previous / play-pause / next, time + fullscreen,
 * seek bar along the bottom edge. Landscape adds the title and channel, and an action row (like,
 * dislike, comments, save, share, more) with a More videos button. Everything else the player can
 * do is in the settings sheet, built from the user's own player-button list, so nothing needs a
 * rotation to reach.
 *
 * The seek bar matches the Classic transport row: SponsorBlock and chapter marks
 * ({@link ModernSeekBar}), and while dragging a preview bubble above the thumb with the storyboard
 * frame (upstream's {@link StoryboardManager}), the chapter title and the time.
 */
final class ModernPlayerChrome {
    private static final int POLL_MS = 200;
    private static final int SEEK_MAX = 1000;
    private static final int ACTIVE_TINT = 0xFF3EA6FF; // same "on" tint as the Shorts action rail
    private static final int SHEET_MAX_WIDTH_DP = 560;
    private static final int SHEET_ROW_HEIGHT_DP = 52;
    private static final int PREVIEW_WIDTH_DP = 160;
    private static final int PREVIEW_WIDTH_LANDSCAPE_DP = 208;

    private final MobilePlaybackFragment mHost;
    private final Activity mActivity;
    private final View mRoot;
    private final View mTitleBlock;
    private final TextView mTitle;
    private final TextView mChannel;
    private final ImageButton mCc;
    private boolean mResumeAfterSettings;
    private final ImageButton mPlayPause;
    private final TextView mTime;
    private final ImageButton mFullscreen;
    private final ModernSeekBar mSeek;
    private final View mTopBar;
    private final View mCentre;
    private final View mBottom;
    private final View mPreview;
    private final ImageView mPreviewImage;
    private final TextView mPreviewChapter;
    private final TextView mPreviewTime;
    private final StoryboardManager mStoryboard;
    private int mPreviewIndex = -1;
    private final View mActions;
    private final ImageButton mLike;
    private final ImageButton mDislike;

    private final View mSheet;
    private final View mSheetCard;
    private final TextView mSheetTitle;
    private final RecyclerView mSheetList;
    private boolean mSheetShowsUpNext;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mPoll = new Runnable() {
        @Override
        public void run() {
            sync();
            mHandler.postDelayed(this, POLL_MS);
        }
    };
    private boolean mLandscape;
    private List<Action> mMoreActions = new ArrayList<>();
    private boolean mUserSeeking;
    private androidx.core.graphics.Insets mCutoutBands = androidx.core.graphics.Insets.NONE;

    /** Returns null if the chrome views aren't in the player layout. */
    static ModernPlayerChrome create(MobilePlaybackFragment host, Activity activity) {
        return activity.findViewById(R.id.mobile_modern_chrome) != null
                && activity.findViewById(R.id.mobile_modern_sheet) != null
                ? new ModernPlayerChrome(host, activity) : null;
    }

    private ModernPlayerChrome(MobilePlaybackFragment host, Activity activity) {
        mHost = host;
        mActivity = activity;
        mRoot = activity.findViewById(R.id.mobile_modern_chrome);
        mTitleBlock = mRoot.findViewById(R.id.modern_title_block);
        mTitle = mRoot.findViewById(R.id.modern_title);
        mChannel = mRoot.findViewById(R.id.modern_channel);
        mCc = mRoot.findViewById(R.id.modern_cc);
        mPlayPause = mRoot.findViewById(R.id.modern_play_pause);
        mTime = mRoot.findViewById(R.id.modern_time);
        mFullscreen = mRoot.findViewById(R.id.modern_fullscreen);
        mSeek = mRoot.findViewById(R.id.modern_seek);
        mTopBar = mRoot.findViewById(R.id.modern_top_bar);
        mCentre = mRoot.findViewById(R.id.modern_centre);
        mBottom = mRoot.findViewById(R.id.modern_bottom);
        mPreview = mRoot.findViewById(R.id.modern_seek_preview);
        mPreviewImage = mRoot.findViewById(R.id.modern_seek_preview_image);
        mPreviewChapter = mRoot.findViewById(R.id.modern_seek_preview_chapter);
        mPreviewTime = mRoot.findViewById(R.id.modern_seek_preview_time);
        mStoryboard = new StoryboardManager(activity);
        mActions = mRoot.findViewById(R.id.modern_actions);
        mLike = mRoot.findViewById(R.id.modern_like);
        mDislike = mRoot.findViewById(R.id.modern_dislike);

        mSheet = activity.findViewById(R.id.mobile_modern_sheet);
        mSheetCard = mSheet.findViewById(R.id.modern_sheet_card);
        mSheetTitle = mSheet.findViewById(R.id.modern_sheet_title);
        mSheetList = mSheet.findViewById(R.id.modern_sheet_list);
        mSheetList.setLayoutManager(new LinearLayoutManager(activity));

        mRoot.findViewById(R.id.modern_collapse).setOnClickListener(v -> mActivity.onBackPressed());
        mCc.setOnClickListener(v -> clickAction(R.id.lb_control_closed_captioning));
        mCc.setOnLongClickListener(v -> longClickAction(R.id.lb_control_closed_captioning));
        mRoot.findViewById(R.id.modern_settings).setOnClickListener(v -> openActionsSheet());
        mRoot.findViewById(R.id.modern_more).setOnClickListener(v -> openVideoActionsSheet());
        mRoot.findViewById(R.id.modern_previous).setOnClickListener(v -> {
            VideoPlayerGlue glue = mHost.glue();
            if (glue != null) glue.previous();
            mHost.tickle();
        });
        mRoot.findViewById(R.id.modern_next).setOnClickListener(v -> {
            VideoPlayerGlue glue = mHost.glue();
            if (glue != null) glue.next();
            mHost.tickle();
        });
        mPlayPause.setOnClickListener(v -> {
            if (mHost.style().tapTogglesPlayback()) {
                mHost.togglePlaybackFromTap();
            } else {
                mHost.setPlayWhenReady(!mHost.getPlayWhenReady());
                mHost.tickle(); // restart the auto-hide timer
            }
            update();
        });
        mFullscreen.setOnClickListener(v -> {
            if (mActivity instanceof MobilePlaybackActivity) {
                ((MobilePlaybackActivity) mActivity).toggleFullscreen();
            }
        });
        mChannel.setOnClickListener(v -> clickAction(R.id.action_channel));
        mLike.setOnClickListener(v -> clickAction(R.id.action_thumbs_up));
        mDislike.setOnClickListener(v -> clickAction(R.id.action_thumbs_down));
        mRoot.findViewById(R.id.modern_comments).setOnClickListener(v -> clickAction(R.id.action_chat));
        mRoot.findViewById(R.id.modern_save).setOnClickListener(v -> clickAction(R.id.action_playlist_add));
        mRoot.findViewById(R.id.modern_share).setOnClickListener(v -> clickAction(R.id.action_share));
        mRoot.findViewById(R.id.modern_more_videos).setOnClickListener(v -> openUpNextSheet());

        mSeek.setMax(SEEK_MAX);
        mSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    long duration = mHost.getDurationMs();
                    mTime.setText(timeWithChapter(duration * progress / SEEK_MAX, duration));
                    updatePreview(progress);
                    mHost.tickle(); // keep the controls up while dragging
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mUserSeeking = true;
                showPreview(true);
                updatePreview(seekBar.getProgress());
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                long duration = mHost.getDurationMs();
                if (duration > 0) {
                    mHost.setPositionMs(duration * seekBar.getProgress() / SEEK_MAX);
                }
                mUserSeeking = false;
                showPreview(false);
                mHost.tickle();
            }
        });
    }

    // ---- Seek bar extras -----------------------------------------------------------------------

    /** Upstream's loadStoryboard(): the video and its length are known, fetch the storyboard. */
    void loadStoryboard(Video video, long durationMs) {
        mStoryboard.init(video, durationMs);
    }

    /** Upstream's setSeekBarSegments(): null clears (new video), a list adds marks. */
    void setSeekBarSegments(List<SeekBarSegment> segments) {
        mSeek.addSegments(segments);
    }

    private void showPreview(boolean show) {
        if (show) {
            // YouTube-style: the other controls step aside so the preview reads over the video.
            mTopBar.setVisibility(View.INVISIBLE);
            mCentre.setVisibility(View.INVISIBLE);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mPreview.getLayoutParams();
            lp.bottomMargin = mRoot.getHeight() - (mBottom.getTop() + mSeek.getTop());
            mPreview.setLayoutParams(lp);
            mPreviewIndex = -1;
            mPreviewImage.setImageDrawable(null);
            mPreviewImage.setVisibility(mStoryboard.getSeekPositions() != null ? View.VISIBLE : View.GONE);
            mPreview.setVisibility(View.VISIBLE);
        } else {
            mTopBar.setVisibility(View.VISIBLE);
            mCentre.setVisibility(View.VISIBLE);
            mPreview.setVisibility(View.GONE);
            mPreviewIndex = -1;
        }
    }

    private void updatePreview(int progress) {
        long duration = mHost.getDurationMs();
        long position = duration * progress / SEEK_MAX;
        mPreviewTime.setText(format(position));

        String chapter = chapterTitleAt(position);
        mPreviewChapter.setText(chapter != null ? chapter : "");
        mPreviewChapter.setVisibility(chapter != null ? View.VISIBLE : View.GONE);

        // Keep the bubble centred over the thumb, inside the chrome's (padded) bounds.
        int width = dp(mLandscape ? PREVIEW_WIDTH_LANDSCAPE_DP : PREVIEW_WIDTH_DP);
        int trackWidth = mSeek.getWidth() - mSeek.getPaddingLeft() - mSeek.getPaddingRight();
        float thumbX = mBottom.getLeft() + mSeek.getLeft() + mSeek.getPaddingLeft() + trackWidth * (float) progress / SEEK_MAX;
        float left = Math.max(mRoot.getPaddingLeft(),
                Math.min(thumbX - width / 2f, mRoot.getWidth() - mRoot.getPaddingRight() - width));
        mPreview.setTranslationX(left - mRoot.getPaddingLeft());

        long[] positions = mStoryboard.getSeekPositions();
        if (positions == null || duration <= 0) {
            return;
        }
        mPreviewImage.setVisibility(View.VISIBLE); // the storyboard may have arrived mid-drag
        int index = (int) Math.min(positions.length - 1, position * positions.length / duration);
        if (index == mPreviewIndex) {
            return;
        }
        mPreviewIndex = index;
        mStoryboard.getBitmap(index, bitmap -> {
            // Frames arrive async: drop ones for a position the finger has already left.
            if (mUserSeeking && index == mPreviewIndex) {
                mPreviewImage.setImageBitmap(bitmap);
            }
        });
    }

    /** "1:17 / 19:06 • Chapter title" (YouTube-style), or just the times without chapters. */
    private String timeWithChapter(long positionMs, long durationMs) {
        String chapter = chapterTitleAt(positionMs);
        return chapter != null ? formatTime(positionMs, durationMs) + "  •  " + chapter : formatTime(positionMs, durationMs);
    }

    /** Title of the chapter containing the position, or null if the video has no chapters. */
    private String chapterTitleAt(long positionMs) {
        String title = null;
        for (Video chapter : mHost.chapters()) {
            if (chapter.startTimeMs > positionMs) {
                break;
            }
            title = chapter.getTitle();
        }
        return title;
    }

    /** Start following the overlay state (player resumed). */
    void start() {
        mHandler.removeCallbacks(mPoll);
        mHandler.post(mPoll);
    }

    /** Pause, open the app's Settings screen over the player, and resume when the user comes back. */
    private void openAppSettings() {
        mResumeAfterSettings = mHost.getPlayWhenReady();
        mHost.setPlayWhenReady(false);
        MobileSettingsActivity.start(mActivity, true);
    }

    /** The host is in front again: resume a video that {@link #openAppSettings} paused. */
    void onHostResumed() {
        if (mResumeAfterSettings) {
            mResumeAfterSettings = false;
            mHost.setPlayWhenReady(true);
        }
    }

    /** Stop following (player paused / backgrounded). */
    void stop() {
        mHandler.removeCallbacks(mPoll);
    }

    /** Show the chrome exactly when the Leanback overlay is shown and the style/layout allow it. */
    void sync() {
        boolean show = mHost.style().isModern() && mHost.modernChromeAllowed() && mHost.isOverlayShown();
        if (!show && mSheet.getVisibility() == View.VISIBLE && !mHost.modernChromeAllowed()) {
            closeSheet(); // e.g. entering PIP or switching to a Short
        }
        if (!show && mPreview.getVisibility() == View.VISIBLE) {
            mUserSeeking = false; // hidden mid-drag (rotation, PIP): no stop-tracking callback follows
            showPreview(false);
        }
        mRoot.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            update();
        }
    }

    /** Portrait strip vs landscape full screen. */
    void applyOrientation(boolean landscape) {
        mLandscape = landscape;
        // Portrait keeps the title block's space (it pushes CC/settings to the right) but not its text.
        mTitleBlock.setVisibility(landscape ? View.VISIBLE : View.INVISIBLE);
        mActions.setVisibility(landscape ? View.VISIBLE : View.GONE);
        int previewWidth = dp(landscape ? PREVIEW_WIDTH_LANDSCAPE_DP : PREVIEW_WIDTH_DP);
        ViewGroup.LayoutParams imageLp = mPreviewImage.getLayoutParams();
        imageLp.width = previewWidth;
        imageLp.height = previewWidth * 9 / 16;
        mPreviewImage.setLayoutParams(imageLp);
        ViewGroup.LayoutParams chapterLp = mPreviewChapter.getLayoutParams();
        chapterLp.width = previewWidth;
        mPreviewChapter.setLayoutParams(chapterLp);
        applySidePadding();
        mFullscreen.setImageResource(landscape ? R.drawable.ic_modern_fullscreen_exit : R.drawable.ic_modern_fullscreen);
        if (mSheet.getVisibility() == View.VISIBLE) {
            closeSheet();
        }
    }

    /** Cutout bands to keep clear of (#47); only the landscape sides matter here. */
    void setCutoutBands(androidx.core.graphics.Insets bands) {
        mCutoutBands = bands;
        applySidePadding();
    }

    private void applySidePadding() {
        // Landscape runs edge to edge, so keep the controls off the edges, and off a notch band there.
        int left = mLandscape ? Math.max(dp(24), mCutoutBands.left) : 0;
        int right = mLandscape ? Math.max(dp(24), mCutoutBands.right) : 0;
        mRoot.setPadding(left, 0, right, 0);
    }

    void bindVideo(Video video) {
        mTitle.setText(video != null && video.getTitle() != null ? video.getTitle() : "");
        mChannel.setText(video != null && video.getAuthor() != null ? video.getAuthor() : "");
        if (mSheetShowsUpNext) {
            closeSheet(); // a video picked from More videos is now playing
        }
    }

    /** Refresh the play/pause icon, time, seek bar and toggle states. */
    void update() {
        if (mRoot.getVisibility() != View.VISIBLE) {
            return;
        }
        mPlayPause.setImageResource(mHost.getPlayWhenReady() ? R.drawable.ic_shorts_pause : R.drawable.ic_shorts_play);
        long duration = mHost.getDurationMs();
        if (!mUserSeeking) {
            long position = mHost.getPositionMs();
            mTime.setText(timeWithChapter(position, duration));
            mSeek.setProgress(duration > 0 ? (int) (position * SEEK_MAX / duration) : 0);
        }
        mCc.setAlpha(isOn(R.id.lb_control_closed_captioning) ? 1f : 0.6f);
        tint(mLike, isOn(R.id.action_thumbs_up));
        tint(mDislike, isOn(R.id.action_thumbs_down));
    }

    // ---- Sheet ---------------------------------------------------------------------------------

    boolean isSheetOpen() {
        return mSheet.getVisibility() == View.VISIBLE;
    }

    /** Touch routing while the sheet is open: the card handles its own touches; a tap outside closes. */
    boolean handleSheetTouch(MotionEvent event) {
        int[] loc = new int[2];
        mSheetCard.getLocationOnScreen(loc);
        boolean inside = event.getRawX() >= loc[0] && event.getRawX() <= loc[0] + mSheetCard.getWidth()
                && event.getRawY() >= loc[1] && event.getRawY() <= loc[1] + mSheetCard.getHeight();
        if (inside) {
            return false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            closeSheet();
        }
        return true;
    }

    /** Closes the sheet if it's open; returns whether it was (for Back). */
    boolean closeSheet() {
        if (mSheet.getVisibility() != View.VISIBLE) {
            return false;
        }
        mSheet.setVisibility(View.GONE);
        if (mSheetShowsUpNext) {
            // Hand the up-next adapter back to the portrait panel's list.
            mSheetList.setAdapter(null);
            RecyclerView panelList = mHost.upNextList();
            if (panelList != null) panelList.setAdapter(mHost.upNextAdapter());
            mSheetShowsUpNext = false;
        } else {
            mSheetList.setAdapter(null);
        }
        return true;
    }

    private void openActionsSheet() {
        List<Action> actions = new ArrayList<>();
        // The CC button is an on/off toggle once a track was chosen; its language picker is a long-press,
        // undiscoverable on a phone. This row (always present, independent of the "Setup player buttons"
        // list) opens the picker.
        Action subtitles = new Action(R.id.lb_control_closed_captioning, mActivity.getString(R.string.subtitle_category_title));
        subtitles.setIcon(mCc.getDrawable());
        actions.add(subtitles);
        List<Action> more = new ArrayList<>();
        for (Action action : rowActions()) {
            // AFR is a TV feature; the video actions have the landscape "..." button (portrait: the panel).
            if (isOnScreen(action) || action.getId() == R.id.action_afr || isVideoAction(action)) {
                continue;
            }
            (isRarelyUsed(action) ? more : actions).add(action);
        }
        // Like YouTube's sheet: the everyday settings first, the rest one level down.
        if (!more.isEmpty()) {
            Action moreRow = new Action(R.id.action_mobile_more, mActivity.getString(R.string.mobile_player_more));
            moreRow.setIcon(ContextCompat.getDrawable(mActivity, R.drawable.ic_modern_more));
            actions.add(moreRow);
            mMoreActions = more;
        }
        // The app's Settings screen, opened over the player so Back returns to this video.
        Action appSettings = new Action(R.id.action_mobile_app_settings, mActivity.getString(R.string.mobile_app_settings));
        appSettings.setIcon(ContextCompat.getDrawable(mActivity, R.drawable.ic_modern_settings));
        actions.add(appSettings);
        mSheetTitle.setText(R.string.mobile_player_settings);
        mSheetList.setAdapter(new ActionAdapter(actions));
        showSheet(Math.min(actions.size() * dp(SHEET_ROW_HEIGHT_DP), maxSheetListHeight()));
    }

    /** The landscape "..." button: things to do with this video, as opposed to the gear's player settings. */
    private void openVideoActionsSheet() {
        List<Action> actions = new ArrayList<>();
        for (Action action : rowActions()) {
            if (isVideoAction(action) && !isOnScreen(action)) {
                actions.add(action);
            }
        }
        if (actions.isEmpty()) {
            openActionsSheet(); // none of them enabled in "Setup player buttons"
            return;
        }
        mSheetTitle.setText(R.string.mobile_player_more);
        mSheetList.setAdapter(new ActionAdapter(actions));
        showSheet(Math.min(actions.size() * dp(SHEET_ROW_HEIGHT_DP), maxSheetListHeight()));
    }

    private void openMoreSheet() {
        mSheetTitle.setText(R.string.mobile_player_more);
        // First row leads back to the gear sheet this list was opened from.
        List<Action> rows = new ArrayList<>();
        rows.add(new Action(R.id.action_mobile_back, mActivity.getString(R.string.mobile_player_back_to_settings)));
        rows.addAll(mMoreActions);
        mSheetList.setAdapter(new ActionAdapter(rows));
        showSheet(Math.min(rows.size() * dp(SHEET_ROW_HEIGHT_DP), maxSheetListHeight()));
    }

    private void openUpNextSheet() {
        UpNextRowAdapter adapter = mHost.upNextAdapter();
        if (adapter == null) {
            return;
        }
        // One adapter, one list at a time: borrow it from the (hidden in landscape) portrait panel.
        RecyclerView panelList = mHost.upNextList();
        if (panelList != null) panelList.setAdapter(null);
        mSheetShowsUpNext = true;
        mSheetTitle.setText(R.string.mobile_player_more_videos);
        mSheetList.setAdapter(adapter);
        showSheet(maxSheetListHeight());
    }

    private void showSheet(int listHeight) {
        ViewGroup.LayoutParams listLp = mSheetList.getLayoutParams();
        listLp.height = listHeight;
        mSheetList.setLayoutParams(listLp);
        ViewGroup.LayoutParams cardLp = mSheetCard.getLayoutParams();
        int screenWidth = mActivity.getResources().getDisplayMetrics().widthPixels;
        cardLp.width = mLandscape ? Math.min(dp(SHEET_MAX_WIDTH_DP), screenWidth) : ViewGroup.LayoutParams.MATCH_PARENT;
        mSheetCard.setLayoutParams(cardLp);
        mSheetList.scrollToPosition(0);
        mSheet.setVisibility(View.VISIBLE);
        mSheet.bringToFront();
    }

    private int maxSheetListHeight() {
        View parent = (View) mSheet.getParent();
        int height = parent != null && parent.getHeight() > 0
                ? parent.getHeight() : mActivity.getResources().getDisplayMetrics().heightPixels;
        return (int) (height * (mLandscape ? 0.7f : 0.55f));
    }

    /** Actions shown as their own buttons in this orientation don't need a sheet row. */
    private boolean isOnScreen(Action action) {
        if (action instanceof PlaybackControlsRow.PlayPauseAction
                || action instanceof PlaybackControlsRow.SkipPreviousAction
                || action instanceof PlaybackControlsRow.SkipNextAction) {
            return true;
        }
        long id = action.getId();
        if (id == R.id.lb_control_closed_captioning) {
            return true;
        }
        // Like/dislike are on screen in both orientations (portrait: the panel under the video).
        if (id == R.id.action_thumbs_up || id == R.id.action_thumbs_down) {
            return true;
        }
        // Comments, save and share: the landscape action row, or the portrait panel under the video.
        // Channel: the landscape title block, or the portrait panel's channel row.
        if (id == R.id.action_chat || id == R.id.action_playlist_add || id == R.id.action_share
                || id == R.id.action_channel) {
            return true;
        }
        // Portrait only: the panel has Subscribe and Queue buttons, and the title opens the description.
        return !mLandscape && (id == R.id.action_subscribe || id == R.id.action_playback_queue
                || id == R.id.action_info);
    }

    private static boolean isVideoAction(Action action) {
        long id = action.getId();
        return id == R.id.action_subscribe || id == R.id.action_playback_queue || id == R.id.action_info;
    }

    /** Actions that go behind the sheet's "More" row. */
    private static boolean isRarelyUsed(Action action) {
        long id = action.getId();
        return id == R.id.action_video_zoom || id == R.id.action_seek_interval || id == R.id.action_sound_off
                || id == R.id.action_screen_dimming || id == R.id.action_pip || id == R.id.action_rotate
                || id == R.id.action_flip || id == R.id.action_video_stats || id == R.id.action_search;
    }

    // ---- Actions -------------------------------------------------------------------------------

    /** The glue's full action set (the user's "Setup player buttons" list), in row order. */
    private List<Action> rowActions() {
        List<Action> result = new ArrayList<>();
        VideoPlayerGlue glue = mHost.glue();
        if (glue == null || glue.getControlsRow() == null) {
            return result;
        }
        addActions(result, glue.getControlsRow().getPrimaryActionsAdapter());
        addActions(result, glue.getControlsRow().getSecondaryActionsAdapter());
        return result;
    }

    private static void addActions(List<Action> result, ObjectAdapter adapter) {
        if (!(adapter instanceof ArrayObjectAdapter)) {
            return;
        }
        ArrayObjectAdapter array = (ArrayObjectAdapter) adapter;
        for (int i = 0; i < array.size(); i++) {
            if (array.get(i) instanceof Action) {
                result.add((Action) array.get(i));
            }
        }
    }

    private Action findAction(int id) {
        for (Action action : rowActions()) {
            if (action.getId() == id) {
                return action;
            }
        }
        return null;
    }

    /**
     * Same path as tapping the Leanback button (multi-state actions step their state there). If the
     * user removed that button from their player-button list, fall back to the presenter directly.
     */
    private void clickAction(int id) {
        VideoPlayerGlue glue = mHost.glue();
        Action action = findAction(id);
        if (glue != null && action != null) {
            glue.onActionClicked(action);
        } else {
            PlaybackPresenter.instance(mActivity).onButtonClicked(id, isOn(id) ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
        }
        mHost.tickle();
        update();
    }

    private boolean longClickAction(int id) {
        VideoPlayerGlue glue = mHost.glue();
        Action action = findAction(id);
        if (glue != null && action != null) {
            return glue.onActionLongClicked(action);
        }
        return false;
    }

    private boolean isOn(int id) {
        return mHost.getButtonState(id) == PlayerUI.BUTTON_ON;
    }

    private static void tint(ImageView view, boolean active) {
        if (active) {
            view.setColorFilter(ACTIVE_TINT);
        } else {
            view.clearColorFilter();
        }
    }

    private int dp(int value) {
        return (int) (value * mActivity.getResources().getDisplayMetrics().density);
    }

    private static String formatTime(long positionMs, long durationMs) {
        return format(positionMs) + " / " + (durationMs > 0 ? format(durationMs) : "--:--");
    }

    private static String format(long ms) {
        long total = Math.max(0, ms / 1000);
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;
        return hours > 0
                ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(java.util.Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    /** Rows of the settings sheet: every player action not already on screen. */
    private final class ActionAdapter extends RecyclerView.Adapter<ActionAdapter.Holder> {
        private final List<Action> mItems;

        ActionAdapter(List<Action> items) {
            mItems = items;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.mobile_modern_sheet_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Action action = mItems.get(position);
            Drawable icon = action.getIcon();
            holder.icon.setImageDrawable(icon != null ? icon.getConstantState() != null
                    ? icon.getConstantState().newDrawable().mutate() : icon : null);
            CharSequence label = action.getLabel1() != null ? action.getLabel1() : action.getLabel2();
            holder.label.setText(label != null ? label : "");
            holder.itemView.setOnClickListener(v -> {
                if (action.getId() == R.id.action_mobile_back) {
                    openActionsSheet();
                    return;
                }
                if (action.getId() == R.id.action_mobile_more) {
                    openMoreSheet(); // swap the list in place
                    return;
                }
                closeSheet();
                if (action.getId() == R.id.lb_control_closed_captioning) {
                    // Subtitle language picker, not the on/off toggle.
                    PlaybackPresenter.instance(mActivity).onButtonLongClicked(R.id.lb_control_closed_captioning,
                            isOn(R.id.lb_control_closed_captioning) ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
                    return;
                }
                if (action.getId() == R.id.action_mobile_app_settings) {
                    openAppSettings();
                    return;
                }
                VideoPlayerGlue glue = mHost.glue();
                if (glue != null) glue.onActionClicked(action);
            });
            holder.itemView.setOnLongClickListener(v -> {
                closeSheet();
                VideoPlayerGlue glue = mHost.glue();
                return glue != null && glue.onActionLongClicked(action);
            });
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView label;

            Holder(View view) {
                super(view);
                icon = view.findViewById(R.id.modern_sheet_row_icon);
                label = view.findViewById(R.id.modern_sheet_row_label);
            }
        }
    }
}
