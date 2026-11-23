package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.data.ChatItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Read-only live chat for the phone player. Portrait shows it as a panel under the video (inside
 * the below-video panel); landscape / full-screen shows it as a translucent side overlay. Both
 * lists share one bounded message buffer + adapter; each has its own scroll policy (follow new
 * messages until the user scrolls away, then a "jump to latest" pill).
 *
 * The engine side is the shared ChatController: it hands us a {@link ChatReceiver} through
 * MobilePlaybackFragment.setChatReceiver and drops it (null) when chat is turned off or the player
 * is released. Nothing here posts messages. All buffer/adapter work happens on the UI thread.
 */
final class MobileLiveChat {
    /** Messages kept in memory; older ones are dropped. */
    static final int MAX_MESSAGES = 150;

    private final LiveChatBuffer mBuffer = new LiveChatBuffer(MAX_MESSAGES);
    private final ChatAdapter mAdapter = new ChatAdapter();
    private final ChatList mPortrait = new ChatList();
    private final ChatList mOverlay = new ChatList();

    private ChatReceiver mReceiver;
    private boolean mSuspended;
    private boolean mBound;
    private boolean mPortraitPanel;
    private boolean mOverlayPanel;

    /** Resolve the views from the playback activity. Safe to call repeatedly. */
    void bind(Activity activity) {
        if (mBound || activity == null) {
            return;
        }
        View portrait = activity.findViewById(R.id.mobile_live_chat_portrait);
        View overlay = activity.findViewById(R.id.mobile_live_chat_landscape);
        if (portrait == null || overlay == null) {
            return;
        }
        mPortrait.attach(portrait);
        mOverlay.attach(overlay);
        mBound = true;
        applyVisibility();
    }

    /** Whether a chat session is currently attached. */
    boolean isActive() {
        return mReceiver != null;
    }

    /** ChatController hands over a new receiver, or null when the chat was closed. */
    void setReceiver(ChatReceiver receiver) {
        detach();
        clearMessages();
        mReceiver = receiver;
        attach();
        applyVisibility();
        refresh();
    }

    /**
     * Where the chat may be shown right now. {@code portraitPanel}: the below-video panel is on
     * screen; {@code overlay}: landscape / full-screen; {@code suspended}: the player is minimised
     * or in PIP, so stop collecting messages and drop the buffer until it is back.
     */
    void layout(boolean portraitPanel, boolean overlay, boolean suspended) {
        mPortraitPanel = portraitPanel;
        mOverlayPanel = overlay;
        if (suspended != mSuspended) {
            mSuspended = suspended;
            if (suspended) {
                detach();
                clearMessages();
            } else {
                attach();
            }
        }
        applyVisibility();
        refresh();
    }

    /** Leaving the player: stop listening and free the messages. */
    void release() {
        detach();
        mReceiver = null;
        clearMessages();
        applyVisibility();
    }

    private void clearMessages() {
        mBuffer.clear();
        mAdapter.notifyDataSetChanged();
        mPortrait.policy.reset();
        mOverlay.policy.reset();
    }

    private void attach() {
        if (mReceiver != null && !mSuspended) {
            final ChatReceiver receiver = mReceiver;
            receiver.setCallback(item -> post(receiver, item));
        }
    }

    private void detach() {
        if (mReceiver != null) {
            mReceiver.setCallback(null);
        }
    }

    /** Engine thread -> UI thread. */
    private void post(ChatReceiver source, ChatItem item) {
        View host = mPortrait.root != null ? mPortrait.root : mOverlay.root;
        if (host == null || item == null) {
            return;
        }
        final LiveChatMessage message = new LiveChatMessage(
                item.getId(), item.getAuthorName(), item.getMessage(), item.getAuthorPhoto());
        host.post(() -> {
            // Dropped (chat closed, replaced, or minimised) between the engine thread and now.
            if (source != mReceiver || mSuspended) {
                return;
            }
            int dropped = mBuffer.add(message);
            if (dropped == LiveChatBuffer.IGNORED) {
                return;
            }
            if (dropped > 0) {
                mAdapter.notifyItemRemoved(0);
            }
            mAdapter.notifyItemInserted(mBuffer.size() - 1);
            mPortrait.onAdded();
            mOverlay.onAdded();
            refresh();
        });
    }

    private void applyVisibility() {
        boolean on = mReceiver != null;
        mPortrait.setShown(on && mPortraitPanel);
        mOverlay.setShown(on && mOverlayPanel && !mPortraitPanel);
    }

    private void refresh() {
        boolean empty = mBuffer.size() == 0;
        mPortrait.refresh(empty);
        mOverlay.refresh(empty);
    }

    /** One RecyclerView + its scroll state. */
    private final class ChatList {
        final LiveChatScrollPolicy policy = new LiveChatScrollPolicy();
        View root;
        RecyclerView list;
        TextView empty;
        TextView jump;
        LinearLayoutManager layoutManager;
        boolean dragging;

        void attach(View container) {
            root = container;
            list = container.findViewById(R.id.live_chat_list);
            empty = container.findViewById(R.id.live_chat_empty);
            jump = container.findViewById(R.id.live_chat_jump);
            layoutManager = new LinearLayoutManager(container.getContext());
            list.setLayoutManager(layoutManager);
            list.setItemAnimator(null);
            list.setAdapter(mAdapter);
            list.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                    dragging = newState == RecyclerView.SCROLL_STATE_DRAGGING;
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        policy.onScrolled(isAtBottom(), false);
                        refreshJump();
                    }
                }

                @Override
                public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                    policy.onScrolled(isAtBottom(), dragging);
                    refreshJump();
                }
            });
            jump.setOnClickListener(v -> {
                policy.onJumpToLatest();
                scrollToEnd();
                refreshJump();
            });
        }

        void setShown(boolean shown) {
            if (root != null) {
                root.setVisibility(shown ? View.VISIBLE : View.GONE);
            }
        }

        void onAdded() {
            if (list != null && policy.onMessageAdded()) {
                scrollToEnd();
            }
        }

        void refresh(boolean noMessages) {
            if (empty != null) {
                empty.setVisibility(noMessages ? View.VISIBLE : View.GONE);
            }
            refreshJump();
        }

        void refreshJump() {
            if (jump == null) {
                return;
            }
            if (policy.isPaused()) {
                int n = policy.pendingCount();
                jump.setText(n > 0
                        ? jump.getContext().getString(R.string.mobile_live_chat_jump_count, n)
                        : jump.getContext().getString(R.string.mobile_live_chat_jump));
                jump.setVisibility(View.VISIBLE);
            } else {
                jump.setVisibility(View.GONE);
            }
        }

        boolean isAtBottom() {
            int count = mAdapter.getItemCount();
            return count == 0 || layoutManager.findLastVisibleItemPosition() >= count - 1;
        }

        void scrollToEnd() {
            int count = mAdapter.getItemCount();
            if (count > 0) {
                list.scrollToPosition(count - 1);
            }
        }
    }

    private final class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.Holder> {
        @Override
        public int getItemCount() {
            return mBuffer.size();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.mobile_live_chat_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            LiveChatMessage m = mBuffer.get(position);
            SpannableStringBuilder sb = new SpannableStringBuilder(m.author);
            sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append(": ").append(m.text);
            holder.text.setText(sb);
            if (m.photoUrl != null && !m.photoUrl.isEmpty()) {
                Glide.with(holder.avatar).load(m.photoUrl).circleCrop().into(holder.avatar);
            } else {
                holder.avatar.setImageDrawable(null);
            }
        }

        @Override
        public void onViewRecycled(@NonNull Holder holder) {
            Glide.with(holder.avatar).clear(holder.avatar);
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView avatar;
            final TextView text;

            Holder(View v) {
                super(v);
                avatar = v.findViewById(R.id.live_chat_avatar);
                text = v.findViewById(R.id.live_chat_text);
            }
        }
    }
}
