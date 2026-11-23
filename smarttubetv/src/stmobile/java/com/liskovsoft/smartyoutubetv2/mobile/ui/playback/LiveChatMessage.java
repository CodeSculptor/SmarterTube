package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

/** One live-chat line, detached from the engine's ChatItem so the buffer logic stays JVM-testable. */
public final class LiveChatMessage {
    public final String id;
    public final String author;
    public final String text;
    public final String photoUrl;

    public LiveChatMessage(String id, String author, String text, String photoUrl) {
        this.id = id;
        this.author = author != null ? author : "";
        this.text = text != null ? text : "";
        this.photoUrl = photoUrl;
    }
}
