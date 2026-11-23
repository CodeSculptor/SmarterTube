package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded, de-duplicated message list for the live chat panel. Oldest first; once {@code max}
 * messages are held the oldest is dropped, so a stream that runs for hours cannot grow memory.
 * Pure Java (no Android types) so it unit-tests on the JVM.
 */
public final class LiveChatBuffer {
    /** {@link #add} result: the message was ignored (blank or an id already in the buffer). */
    public static final int IGNORED = -1;

    private final int mMax;
    private final List<LiveChatMessage> mItems = new ArrayList<>();
    private final Set<String> mIds = new HashSet<>();

    public LiveChatBuffer(int max) {
        mMax = Math.max(1, max);
    }

    /**
     * Appends a message.
     *
     * @return the number of oldest messages dropped to make room (0 or 1), or {@link #IGNORED}
     */
    public int add(LiveChatMessage message) {
        if (message == null || message.text.trim().isEmpty()) {
            return IGNORED;
        }
        if (message.id != null && mIds.contains(message.id)) {
            return IGNORED;
        }

        int dropped = 0;
        if (mItems.size() >= mMax) {
            LiveChatMessage oldest = mItems.remove(0);
            if (oldest.id != null) {
                mIds.remove(oldest.id);
            }
            dropped = 1;
        }

        mItems.add(message);
        if (message.id != null) {
            mIds.add(message.id);
        }
        return dropped;
    }

    public int size() {
        return mItems.size();
    }

    public LiveChatMessage get(int index) {
        return mItems.get(index);
    }

    public void clear() {
        mItems.clear();
        mIds.clear();
    }
}
