package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LiveChatBufferTest {
    private static LiveChatMessage msg(String id, String text) {
        return new LiveChatMessage(id, "a", text, null);
    }

    @Test
    public void appendsInOrder() {
        LiveChatBuffer b = new LiveChatBuffer(5);
        assertEquals(0, b.add(msg("1", "x")));
        assertEquals(0, b.add(msg("2", "y")));
        assertEquals(2, b.size());
        assertEquals("1", b.get(0).id);
        assertEquals("2", b.get(1).id);
    }

    @Test
    public void dropsOldestAtCapacity() {
        LiveChatBuffer b = new LiveChatBuffer(3);
        for (int i = 0; i < 3; i++) b.add(msg("" + i, "m" + i));
        assertEquals(1, b.add(msg("3", "m3")));
        assertEquals(3, b.size());
        assertEquals("1", b.get(0).id);
        assertEquals("3", b.get(2).id);
    }

    @Test
    public void staysBoundedOverLongStream() {
        LiveChatBuffer b = new LiveChatBuffer(150);
        for (int i = 0; i < 10_000; i++) b.add(msg("" + i, "m"));
        assertEquals(150, b.size());
        assertEquals("9850", b.get(0).id);
    }

    @Test
    public void ignoresDuplicatesAndBlank() {
        LiveChatBuffer b = new LiveChatBuffer(5);
        b.add(msg("1", "x"));
        assertEquals(LiveChatBuffer.IGNORED, b.add(msg("1", "again")));
        assertEquals(LiveChatBuffer.IGNORED, b.add(msg("2", "   ")));
        assertEquals(LiveChatBuffer.IGNORED, b.add(null));
        assertEquals(1, b.size());
    }

    @Test
    public void droppedIdCanReturn() {
        LiveChatBuffer b = new LiveChatBuffer(2);
        b.add(msg("1", "x"));
        b.add(msg("2", "y"));
        b.add(msg("3", "z")); // drops "1"
        assertEquals(1, b.add(msg("1", "x")));
    }

    @Test
    public void clearEmpties() {
        LiveChatBuffer b = new LiveChatBuffer(2);
        b.add(msg("1", "x"));
        b.clear();
        assertEquals(0, b.size());
        assertEquals(0, b.add(msg("1", "x")));
    }
}
