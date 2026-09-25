package com.liskovsoft.smartyoutubetv2.mobile.ui.dialogs;

import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver.Backup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Phone-native settings dialog. Implements {@link AppDialogView} and is driven by the
 * existing {@link AppDialogPresenter} unchanged — category list, radio/check/switch state
 * and the on-select callbacks are all reused from the TV code; only the view layer is new.
 *
 * Categories are rendered flat in one vertical list: each non-null category title becomes
 * a section header followed by its option rows.
 *
 * Nested dialogs (a setting that opens another dialog while this one is shown, e.g. General →
 * Context menu → "Position of …") arrive as another {@link #show} on this same fragment. Like the
 * TV {@code AppDialogFragment}'s child back stack, the previous dialog is pushed and restored by
 * Back, a tap outside the sheet, or the presenter's {@link #goBack}.
 */
public class MobileAppDialogFragment extends Fragment implements AppDialogView {
    /** A single-category sub-dialog this short (or shorter) is presented as a bottom-sheet card. */
    private static final int SHEET_MAX_ROWS = 5;

    private AppDialogPresenter mPresenter;
    private TextView mTitleView;
    private RecyclerView mList;
    private TextView mStatusView;
    private View mRootFrame;
    private LinearLayout mContent;
    private View mHandle;
    private View mHeader;
    private View mOptionsContainer;
    private MobileAppDialogAdapter mAdapter;
    private MobileCommentsAdapter mCommentsAdapter;
    private CommentsReceiver mCommentsReceiver;
    private CommentsReceiver mInlineReceiver;
    private CommentItem mPendingReplyParent;
    private CommentsState mParentComments;
    private CommentGroup mCurrentGroup;
    private boolean mLoadingMore;
    private boolean mIsTransparent;
    private boolean mIsOverlay;
    private boolean mIsPaused;
    private int mId;
    /** Options dialog currently on screen, and the ones it was opened from (nested dialogs). */
    private DialogState mCurrent;
    private final Deque<DialogState> mBackStack = new ArrayDeque<>();

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
        mStatusView = view.findViewById(R.id.options_status);
        mRootFrame = view.findViewById(R.id.dialog_root_frame);
        mContent = view.findViewById(R.id.dialog_content);
        mHandle = view.findViewById(R.id.sheet_handle);
        mHeader = view.findViewById(R.id.dialog_header);
        mOptionsContainer = view.findViewById(R.id.options_container);
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

        // Hand the loaded comments back to the controller (enables instant restore on reopen) and
        // clear the receiver's callback so it doesn't retain this fragment's views.
        if (mInlineReceiver != null) {
            mInlineReceiver.setCallback(null);
            mInlineReceiver = null;
        }

        if (mCommentsReceiver != null) {
            mCommentsReceiver.onFinish(new CommentsBackup(
                    mCommentsAdapter != null ? mCommentsAdapter.getComments() : null, mCurrentGroup));
            mCommentsReceiver = null;
        }

        // Closed while a replies thread was on screen: the top-level state still needs to reach
        // the controller's backup (only the top-level receiver's onFinish stores anything).
        if (mParentComments != null) {
            mParentComments.receiver.onFinish(
                    new CommentsBackup(mParentComments.items, mParentComments.currentGroup));
            mParentComments = null;
        }

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
        boolean isComments = categories != null && categories.size() == 1
                && categories.get(0).type == OptionCategory.TYPE_COMMENTS;
        if (!isComments) {
            DialogState next = new DialogState(categories, title, isExpandable, isTransparent, isOverlay, id);
            // A different options dialog opened on top of the current one is a nested level: keep
            // the current one (and its scroll position) to return to. The same dialog re-shown
            // (a refresh after a change) just replaces it.
            if (mCurrent != null && !TextUtils.equals(mCurrent.resolvedTitle(), next.resolvedTitle())) {
                mCurrent.listState = mList != null && mList.getLayoutManager() != null
                        ? mList.getLayoutManager().onSaveInstanceState() : null;
                mBackStack.push(mCurrent);
            }
            mCurrent = next;
        }
        render(categories, title, isExpandable, isTransparent, isOverlay, id);
    }

    private void render(List<OptionCategory> categories, CharSequence title, boolean isExpandable,
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

        // Comments arrive as a single TYPE_COMMENTS category carrying a CommentsReceiver. Render
        // them in a native YouTube-style list instead of the generic option rows.
        if (categories != null && categories.size() == 1
                && categories.get(0).type == OptionCategory.TYPE_COMMENTS) {
            setupComments(categories.get(0));
            return;
        }

        if (mAdapter != null) {
            mAdapter.setCategories(categories, isExpandable);
            // A focused sub-dialog (one expandable category) that renders to just a handful of
            // rows — e.g. the Video speed slider opened from the player — reads better as a bottom
            // card at thumb height than as a sparse full screen. Multi-category settings screens
            // (General, Player, …) keep the full-screen layout. Gate on the presenter's
            // isSheetDialog() — the same predicate the host activity uses to choose the translucent
            // window — so the card never renders over an opaque window; then refine by rendered row
            // count, which the presenter can't know (a numeric category collapses to one slider row).
            boolean sheet = mPresenter != null && mPresenter.isSheetDialog()
                    && mAdapter.getItemCount() > 0 && mAdapter.getItemCount() <= SHEET_MAX_ROWS;
            applyPresentation(sheet);
        }
    }

    /**
     * Swap between the full-screen dialog layout and a bottom-sheet card. The card layout is set
     * here (it depends on the categories, which only arrive in {@link #show}); the host activity
     * separately makes its window translucent for the same sub-dialogs (see
     * {@code MobileAppDialogActivity} + {@link AppDialogPresenter#isSheetDialog()}), so the scrim
     * painted on the root frame below dims the still-playing video instead of solid black.
     */
    private void applyPresentation(boolean sheet) {
        if (mContent == null || mRootFrame == null) {
            return;
        }

        FrameLayout.LayoutParams contentLp = (FrameLayout.LayoutParams) mContent.getLayoutParams();
        LinearLayout.LayoutParams listLp = (LinearLayout.LayoutParams) mOptionsContainer.getLayoutParams();

        if (sheet) {
            contentLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            contentLp.gravity = Gravity.BOTTOM;
            mContent.setBackgroundResource(R.drawable.mobile_sheet_bg);
            mContent.setClickable(true); // swallow taps on the card so they don't dismiss

            // Hug the content instead of stretching to fill the screen.
            listLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            listLp.weight = 0;

            mHandle.setVisibility(View.VISIBLE);
            mHeader.setBackgroundColor(0); // let the rounded card surface show through the top
            mList.setNestedScrollingEnabled(false);

            mRootFrame.setBackgroundResource(R.color.mobile_scrim);
            mRootFrame.setOnClickListener(v -> dismissSheet());
        } else {
            contentLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            contentLp.gravity = Gravity.TOP;
            mContent.setBackgroundResource(R.color.mobile_background);
            mContent.setClickable(false);

            listLp.height = 0;
            listLp.weight = 1;

            mHandle.setVisibility(View.GONE);
            mHeader.setBackgroundResource(R.color.mobile_primary);
            mList.setNestedScrollingEnabled(true);

            mRootFrame.setBackground(null);
            mRootFrame.setOnClickListener(null);
            mRootFrame.setClickable(false);
        }

        mContent.setLayoutParams(contentLp);
        mOptionsContainer.setLayoutParams(listLp);
    }

    /** Tap outside the bottom-sheet card: close it the same way Back does. */
    private void dismissSheet() {
        if (getActivity() != null) {
            getActivity().onBackPressed();
        }
    }

    // ----- comments -----

    private void setupComments(OptionCategory category) {
        OptionItem option = category.options != null && !category.options.isEmpty()
                ? category.options.get(0) : null;
        final CommentsReceiver receiver = option != null ? option.getCommentsReceiver() : null;
        if (receiver == null || mList == null) {
            return;
        }

        // A comments view is already active and a different receiver arrives: this is the nested
        // replies thread for the comment the user just tapped. Feed it into the existing list,
        // indented under its parent, instead of replacing the view.
        if (mCommentsReceiver != null && mCommentsReceiver != receiver && mCommentsAdapter != null
                && mPendingReplyParent != null) {
            CommentItem parent = mPendingReplyParent;
            mPendingReplyParent = null;
            attachInlineRepliesCallback(receiver, parent);
            receiver.onStart();
            return;
        }

        // Fallback for a nested thread without a known parent (shouldn't normally happen):
        // swap the whole list, with back restoring the top-level state.
        if (mCommentsReceiver != null && mCommentsReceiver != receiver && mCommentsAdapter != null) {
            mCommentsReceiver.setCallback(null);
            mParentComments = new CommentsState(mCommentsReceiver, mCommentsAdapter.getComments(), mCurrentGroup);
        }

        mCommentsReceiver = receiver;
        mCurrentGroup = null;
        mLoadingMore = false;

        // This comments screen is a separate opaque activity that stops the player. On stop the
        // player would release its engine, which fires CommentsController.onEngineReleased() and
        // disposes the in-flight comments request before it returns — leaving the list stuck on
        // "Loading". Block the engine first (this runs before the player's onStop): the request
        // survives and playback keeps going underneath, YouTube-style. The player clears the block
        // in its own onResume() when we return.
        PlaybackView player = PlaybackPresenter.instance(getContext()).getPlayer();
        if (player != null) {
            player.blockEngine(true);
        }

        mCommentsAdapter = createCommentsAdapter();
        mList.setAdapter(mCommentsAdapter);
        // setupComments re-runs on the live fragment when a replies thread opens — remove first
        // so the listener isn't registered twice.
        mList.removeOnScrollListener(mPaginationListener);
        mList.addOnScrollListener(mPaginationListener);

        showStatus(receiver.getLoadingMessage());

        attachCommentsCallback(receiver);

        receiver.onStart();
    }

    private MobileCommentsAdapter createCommentsAdapter() {
        return new MobileCommentsAdapter(
                item -> {
                    // Tap toggles the inline replies thread under the comment.
                    if (mCommentsAdapter.hasExpandedReplies(item)) {
                        mCommentsAdapter.collapseReplies(item);
                    } else {
                        mPendingReplyParent = item;
                        mCommentsReceiver.onCommentClicked(item);
                    }
                },
                item -> mCommentsReceiver.onCommentLongClicked(item));
    }

    /**
     * Drive a nested replies receiver into the existing list: arriving reply pages are inserted
     * indented under their parent comment (plus one eager continuation for short first pages,
     * mirroring the top-level behavior).
     */
    private void attachInlineRepliesCallback(final CommentsReceiver receiver, final CommentItem parent) {
        if (mInlineReceiver != null) {
            mInlineReceiver.setCallback(null);
        }
        mInlineReceiver = receiver;

        receiver.setCallback(new CommentsReceiver.Callback() {
            private boolean mEagerLoaded;

            @Override
            public void onCommentGroup(CommentGroup commentGroup) {
                if (mCommentsAdapter == null || commentGroup == null || commentGroup.getComments() == null) {
                    return;
                }

                int added = mCommentsAdapter.insertReplies(parent, commentGroup.getComments());

                if (!mEagerLoaded && added <= 10 && commentGroup.getNextCommentsKey() != null) {
                    mEagerLoaded = true;
                    receiver.onLoadMore(commentGroup);
                }
            }

            @Override
            public void onBackup(Backup backup) {
                // Replies have no backup path.
            }

            @Override
            public void onSync(CommentItem commentItem) {
                if (mCommentsAdapter != null) {
                    mCommentsAdapter.update(commentItem);
                }
            }
        });
    }

    private void attachCommentsCallback(final CommentsReceiver receiver) {
        receiver.setCallback(new CommentsReceiver.Callback() {
            @Override
            public void onCommentGroup(CommentGroup commentGroup) {
                mLoadingMore = false;

                if (commentGroup == null || commentGroup.getComments() == null) {
                    if (mCommentsAdapter.getItemCount() == 0) {
                        showStatus(receiver.getErrorMessage());
                    }
                    return;
                }

                mCommentsAdapter.addComments(commentGroup.getComments());
                mCurrentGroup = commentGroup;
                showStatus(mCommentsAdapter.getItemCount() == 0 ? receiver.getErrorMessage() : null);

                // Short replies list that still has a continuation: onLoadMore won't fire on a
                // list that doesn't scroll, so page it in eagerly (matches the TV behavior).
                if (mCommentsAdapter.getItemCount() <= 10 && commentGroup.getNextCommentsKey() != null) {
                    mLoadingMore = true;
                    receiver.onLoadMore(commentGroup);
                }
            }

            @Override
            public void onBackup(Backup backup) {
                if (backup instanceof CommentsBackup) {
                    CommentsBackup b = (CommentsBackup) backup;
                    mCommentsAdapter.setComments(b.items);
                    mCurrentGroup = b.currentGroup;
                    showStatus(mCommentsAdapter.getItemCount() == 0 ? receiver.getErrorMessage() : null);
                }
            }

            @Override
            public void onSync(CommentItem commentItem) {
                mCommentsAdapter.update(commentItem);
            }
        });
    }

    /** Restore the saved top-level comments after a nested replies thread (back press). */
    private void restoreParentComments() {
        CommentsState parent = mParentComments;
        mParentComments = null;

        if (mCommentsReceiver != null) {
            mCommentsReceiver.setCallback(null); // drop the replies receiver
        }

        mCommentsReceiver = parent.receiver;
        mCurrentGroup = parent.currentGroup;
        mLoadingMore = false;

        mCommentsAdapter = createCommentsAdapter();
        mCommentsAdapter.setComments(parent.items);
        mList.setAdapter(mCommentsAdapter);

        attachCommentsCallback(mCommentsReceiver);
        showStatus(mCommentsAdapter.getItemCount() == 0 ? mCommentsReceiver.getErrorMessage() : null);
    }

    private final RecyclerView.OnScrollListener mPaginationListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
            if (dy <= 0 || mCommentsReceiver == null || mLoadingMore
                    || mCurrentGroup == null || mCurrentGroup.getNextCommentsKey() == null) {
                return;
            }
            RecyclerView.LayoutManager lm = rv.getLayoutManager();
            if (lm instanceof LinearLayoutManager) {
                LinearLayoutManager llm = (LinearLayoutManager) lm;
                if (llm.findLastVisibleItemPosition() >= llm.getItemCount() - 3) {
                    mLoadingMore = true;
                    mCommentsReceiver.onLoadMore(mCurrentGroup);
                }
            }
        }
    };

    private void showStatus(String text) {
        if (mStatusView == null) {
            return;
        }
        if (TextUtils.isEmpty(text)) {
            mStatusView.setVisibility(View.GONE);
        } else {
            mStatusView.setText(text);
            mStatusView.setVisibility(View.VISIBLE);
        }
    }

    /** Snapshot of the loaded comments so reopening the same video restores instantly. */
    private static final class CommentsBackup implements Backup {
        final List<CommentItem> items;
        final CommentGroup currentGroup;

        CommentsBackup(List<CommentItem> items, CommentGroup currentGroup) {
            this.items = items;
            this.currentGroup = currentGroup;
        }
    }

    /** An options dialog as passed to {@link #show}, kept so a nested dialog can return to it. */
    private static final class DialogState {
        final List<OptionCategory> categories;
        final CharSequence title;
        final boolean isExpandable;
        final boolean isTransparent;
        final boolean isOverlay;
        final int id;
        Parcelable listState;

        DialogState(List<OptionCategory> categories, CharSequence title, boolean isExpandable,
                    boolean isTransparent, boolean isOverlay, int id) {
            this.categories = categories;
            this.title = title;
            this.isExpandable = isExpandable;
            this.isTransparent = isTransparent;
            this.isOverlay = isOverlay;
            this.id = id;
        }

        /** The title actually displayed (see {@link #render}): a lone expandable category's name. */
        CharSequence resolvedTitle() {
            if (isExpandable && categories != null && categories.size() == 1
                    && categories.get(0).title != null) {
                return categories.get(0).title;
            }
            return title;
        }
    }

    /** Saved top-level comments state while a nested replies thread is displayed. */
    private static final class CommentsState {
        final CommentsReceiver receiver;
        final List<CommentItem> items;
        final CommentGroup currentGroup;

        CommentsState(CommentsReceiver receiver, List<CommentItem> items, CommentGroup currentGroup) {
            this.receiver = receiver;
            this.items = items;
            this.currentGroup = currentGroup;
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
        if (mParentComments != null) {
            restoreParentComments();
        } else if (!mBackStack.isEmpty()) {
            restorePreviousDialog();
        } else {
            finish();
        }
    }

    /** Pop back to the dialog a nested one was opened from, at the same scroll position. */
    private void restorePreviousDialog() {
        DialogState previous = mBackStack.pop();
        mCurrent = previous;
        render(previous.categories, previous.title, previous.isExpandable,
                previous.isTransparent, previous.isOverlay, previous.id);
        if (previous.listState != null && mList != null && mList.getLayoutManager() != null) {
            mList.getLayoutManager().onRestoreInstanceState(previous.listState);
        }
    }

    @Override
    public void clearBackstack() {
        mParentComments = null;
        mBackStack.clear();
    }

    @Override
    public boolean canGoBack() {
        return mParentComments != null || !mBackStack.isEmpty();
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
