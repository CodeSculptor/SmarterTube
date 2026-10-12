package com.liskovsoft.smartyoutubetv2.mobile.ui.dialogs;

import com.liskovsoft.smartyoutubetv2.mobile.ui.base.TextLinks;
import android.graphics.PorterDuff;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Phone-native comments list. Renders {@link CommentItem}s YouTube-style (avatar, author, date,
 * message, like and reply counts). Replies expand inline, indented under their parent comment.
 * Note {@code isEmpty()} means "has no replies" (it's the controller's open-replies guard), so it
 * must NOT be used to filter rows — nested replies and reply-less top-level comments are all
 * isEmpty().
 */
public class MobileCommentsAdapter extends RecyclerView.Adapter<MobileCommentsAdapter.Holder> {
    public interface OnComment {
        void onComment(CommentItem item);
    }

    /** A row: either a top-level comment or an inline-expanded reply under one. */
    private static final class Entry {
        final CommentItem item;
        final boolean isReply;

        Entry(CommentItem item, boolean isReply) {
            this.item = item;
            this.isReply = isReply;
        }
    }

    private final List<Entry> mItems = new ArrayList<>();
    private final OnComment mReplyClick;
    private final OnComment mLikeClick;

    public MobileCommentsAdapter(OnComment replyClick, OnComment likeClick) {
        mReplyClick = replyClick;
        mLikeClick = likeClick;
    }

    /** Append a page of top-level comments. Returns the number actually added. */
    public int addComments(List<CommentItem> comments) {
        if (comments == null) {
            return 0;
        }
        int start = mItems.size();
        int added = 0;
        for (CommentItem item : comments) {
            if (hasContent(item)) {
                mItems.add(new Entry(item, false));
                added++;
            }
        }
        if (added > 0) {
            notifyItemRangeInserted(start, added);
        }
        return added;
    }

    /** Replace the whole list with top-level comments (backup restore). */
    public void setComments(List<CommentItem> comments) {
        mItems.clear();
        if (comments != null) {
            for (CommentItem item : comments) {
                if (hasContent(item)) {
                    mItems.add(new Entry(item, false));
                }
            }
        }
        notifyDataSetChanged();
    }

    /** Top-level comments only (expanded replies are re-fetched on demand after a restore). */
    public List<CommentItem> getComments() {
        List<CommentItem> result = new ArrayList<>();
        for (Entry entry : mItems) {
            if (!entry.isReply) {
                result.add(entry.item);
            }
        }
        return result;
    }

    /** Insert replies indented under their parent (after any already-expanded ones). */
    public int insertReplies(CommentItem parent, List<CommentItem> replies) {
        if (replies == null) {
            return 0;
        }
        int parentIndex = indexOf(parent);
        if (parentIndex < 0) {
            return 0;
        }
        // Skip past replies already inserted under this parent.
        int insertAt = parentIndex + 1;
        while (insertAt < mItems.size() && mItems.get(insertAt).isReply) {
            insertAt++;
        }
        int added = 0;
        for (CommentItem reply : replies) {
            if (hasContent(reply)) {
                mItems.add(insertAt + added, new Entry(reply, true));
                added++;
            }
        }
        if (added > 0) {
            notifyItemRangeInserted(insertAt, added);
        }
        return added;
    }

    public boolean hasExpandedReplies(CommentItem parent) {
        int parentIndex = indexOf(parent);
        return parentIndex >= 0 && parentIndex + 1 < mItems.size() && mItems.get(parentIndex + 1).isReply;
    }

    public void collapseReplies(CommentItem parent) {
        int parentIndex = indexOf(parent);
        if (parentIndex < 0) {
            return;
        }
        int count = 0;
        while (parentIndex + 1 + count < mItems.size() && mItems.get(parentIndex + 1 + count).isReply) {
            count++;
        }
        if (count > 0) {
            for (int i = 0; i < count; i++) {
                mItems.remove(parentIndex + 1);
            }
            notifyItemRangeRemoved(parentIndex + 1, count);
        }
    }

    /** Replace an item in place (e.g. after a like toggle) and refresh its row. */
    public void update(CommentItem item) {
        if (item == null) {
            return;
        }
        for (int i = 0; i < mItems.size(); i++) {
            Entry entry = mItems.get(i);
            if (Helpers.equals(entry.item.getId(), item.getId())) {
                mItems.set(i, new Entry(item, entry.isReply));
                notifyItemChanged(i);
                return;
            }
        }
    }

    private int indexOf(CommentItem item) {
        if (item == null) {
            return -1;
        }
        for (int i = 0; i < mItems.size(); i++) {
            if (!mItems.get(i).isReply && Helpers.equals(mItems.get(i).item.getId(), item.getId())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Some items in a comments response carry no renderable fields at all (ghost rows whose
     * renderer sits at a JSON path the parser doesn't read). Skip those; do NOT use
     * {@code isEmpty()} here — it means "has no replies", which describes every nested reply.
     */
    private static boolean hasContent(CommentItem item) {
        return item != null && (item.getMessage() != null || item.getAuthorName() != null);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.mobile_comment_item, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry entry = mItems.get(position);
        CommentItem item = entry.item;

        // Replies indent under their parent (base row padding is 16dp in the item layout).
        float density = h.itemView.getResources().getDisplayMetrics().density;
        h.itemView.setPaddingRelative((int) ((entry.isReply ? 60 : 16) * density),
                h.itemView.getPaddingTop(), (int) (16 * density), h.itemView.getPaddingBottom());

        String author = item.getAuthorName() != null ? item.getAuthorName() : "";
        String date = item.getPublishedDate();
        h.authorDate.setText(TextUtils.isEmpty(date) ? author : author + "  ·  " + date);
        h.message.setText(TextLinks.linkify(h.itemView.getContext(), item.getMessage(), null));
        // Links open on tap; any other tap falls through to the row (reply thread).
        final android.text.style.ClickableSpan[] down = new android.text.style.ClickableSpan[1];
        h.message.setOnTouchListener((v, ev) -> {
            if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                down[0] = TextLinks.spanAt(h.message, ev);
                return down[0] != null;
            }
            if (down[0] == null) {
                return false;
            }
            if (ev.getActionMasked() == android.view.MotionEvent.ACTION_UP
                    && down[0] == TextLinks.spanAt(h.message, ev)) {
                down[0].onClick(h.message);
            }
            if (ev.getActionMasked() == android.view.MotionEvent.ACTION_UP
                    || ev.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) {
                down[0] = null;
            }
            return true;
        });

        String likeCount = item.getLikeCount();
        h.likeCount.setText(likeCount != null ? likeCount : "");

        int likeTint = ContextCompat.getColor(h.itemView.getContext(),
                item.isLiked() ? R.color.mobile_accent : R.color.mobile_text_secondary);
        h.likeIcon.setColorFilter(likeTint, PorterDuff.Mode.SRC_IN);
        h.likeCount.setTextColor(likeTint);

        String replyCount = item.getReplyCount();
        boolean hasReplies = !TextUtils.isEmpty(replyCount) && item.getNestedCommentsKey() != null;
        if (hasReplies) {
            h.replyCount.setVisibility(View.VISIBLE);
            h.replyCount.setText(replyCount);
        } else {
            h.replyCount.setVisibility(View.GONE);
        }

        // Avatar background (set in the item layout) shows through as the placeholder until the
        // circle-cropped photo loads.
        Glide.with(h.itemView.getContext())
                .load(item.getAuthorPhoto())
                .circleCrop()
                .into(h.avatar);

        h.itemView.setOnClickListener(v -> {
            if (mReplyClick != null && hasReplies && !entry.isReply) {
                mReplyClick.onComment(item);
            }
        });

        // The like toggle goes through the same key as the replies thread — without it the
        // request can't be built (replies themselves have no key).
        View.OnClickListener likeListener = item.getNestedCommentsKey() == null ? null : v -> {
            if (mLikeClick != null) {
                mLikeClick.onComment(item);
            }
        };
        h.likeIcon.setOnClickListener(likeListener);
        h.likeCount.setOnClickListener(likeListener);
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView avatar;
        final TextView authorDate;
        final TextView message;
        final ImageView likeIcon;
        final TextView likeCount;
        final TextView replyCount;

        Holder(View v) {
            super(v);
            avatar = v.findViewById(R.id.comment_avatar);
            authorDate = v.findViewById(R.id.comment_author_date);
            message = v.findViewById(R.id.comment_message);
            likeIcon = v.findViewById(R.id.comment_like_icon);
            likeCount = v.findViewById(R.id.comment_like_count);
            replyCount = v.findViewById(R.id.comment_reply_count);
        }
    }
}
