package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

/**
 * Groups chat items into poll "batches" for debug logging: the engine delivers each poll response's
 * items back to back, then sleeps for the server's timeout. A gap longer than {@code gapMs} closes
 * the batch. Pure Java for JVM tests.
 */
public final class LiveChatBatchCounter {
    private final long mGapMs;
    private long mLastMs = -1;
    private int mCount;

    public LiveChatBatchCounter(long gapMs) {
        mGapMs = gapMs;
    }

    /**
     * Records an item arriving at {@code nowMs}.
     *
     * @return the size of the batch that just ended, or 0 if the current batch continues
     */
    public synchronized int onItem(long nowMs) {
        int finished = 0;
        if (mLastMs >= 0 && nowMs - mLastMs > mGapMs) {
            finished = mCount;
            mCount = 0;
        }
        mCount++;
        mLastMs = nowMs;
        return finished;
    }

    public synchronized void reset() {
        mLastMs = -1;
        mCount = 0;
    }
}
