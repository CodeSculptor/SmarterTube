# Live chat panel: device test script (IMPLEMENTED-UNVERIFIED)

Needs a stream that is live right now with chat on.

**Finding one:** in the app, search "live" and tick the Live filter (search filter gear), or open
a 24/7 news channel's Live tab (e.g. a large news or lo-fi music channel). Open a stream whose
chat is busy (news, esports, gaming). Avoid "chat disabled" or members-only chats.

Setup: Settings > Player style: try Modern first, then Classic.

1. Open a live stream in portrait. The Comments card under the video reads **Live chat** with **Show** on the right.
2. Tap it. A chat panel appears under the video above the up-next list. First it says "Waiting for chat messages…", then messages with avatars scroll in. The card now says **Hide**.
3. Leave it for a minute: the newest message stays at the bottom without you touching it.
4. Drag the chat list down (towards older messages). Following stops, and a **Jump to latest** pill shows; after more messages arrive it reads "N new messages ↓".
5. Tap the pill: the list jumps to the newest message and following resumes. Repeat step 4 and instead scroll back to the bottom by hand: the pill disappears and following resumes.
6. Tap **Hide**: the panel disappears, up-next gets the space back. Tap **Show**: the chat starts again from empty.
7. With chat shown, rotate to landscape (or tap fullscreen). The chat appears as a translucent column on the right, clear of the seek bar. Scroll it and check the pill there too. Rotate back: the panel returns under the video.
8. In landscape, toggle chat with the chat/comments button (Modern: comments button in the action row; Classic: the chat button if enabled in Setup player buttons). Message flow starts and stops.
9. With chat shown, press Back to minimise to the mini-player. Chat is gone from the screen. Tap the mini-player to expand: chat resumes (history starts empty).
10. With chat shown, enter PIP (Home key): no chat in the PIP window. Return: chat resumes.
11. Leave the player (close). Open a different live stream: chat state should not carry over stale messages from the first stream.
12. Open a normal (non-live) video: the card says **Comments** with ›, and comments open as before.
13. Open a live stream that has chat turned off: the card is hidden (no Comments card).
14. Open an ended live stream (replay): expected NOT supported. Either no chat entry, or the panel stays on "Waiting for chat messages…". Report which.
15. Leave chat on for 10+ minutes on a busy stream: scrolling stays smooth and memory does not climb (list is capped at 150).

Also note: the chat preference is shared with the TV code (`isLiveChatEnabled`), so once turned on it re-opens automatically on the next live stream. Tap Hide to turn it off.
