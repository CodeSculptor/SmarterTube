package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LiveChatBatchCounterTest {
    @Test
    public void closesBatchOnGap() {
        LiveChatBatchCounter c = new LiveChatBatchCounter(500);
        assertEquals(0, c.onItem(0));
        assertEquals(0, c.onItem(10));
        assertEquals(0, c.onItem(20));
        assertEquals(3, c.onItem(5_000)); // new poll: previous batch had 3
        assertEquals(1, c.onItem(10_000));
    }

    @Test
    public void resetStartsOver() {
        LiveChatBatchCounter c = new LiveChatBatchCounter(500);
        c.onItem(0);
        c.reset();
        assertEquals(0, c.onItem(9_000));
    }
}
