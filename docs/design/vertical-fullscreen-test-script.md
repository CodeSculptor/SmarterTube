# Vertical-video full-screen: device test script (IMPLEMENTED-UNVERIFIED)

Feature: a non-Shorts vertical video (9:16 upload opened as a normal video) goes full-screen
**upright** from portrait instead of forcing landscape. Logic: `VerticalFullscreenPolicy`;
state lives in `MobilePlaybackFragment` (`mUprightFullscreen`), button/Back in `MobilePlaybackActivity`.

1. Open a vertical (9:16) video as a normal video (not from the Shorts shelf), phone portrait. Strip with panel below, as before.
2. Tap the full-screen button (Modern) / swipe up on the video: the screen stays portrait, strip and panel go away, picture fills the screen. No rotation.
3. Tap the button again: back to the 16:9 strip + panel, still portrait.
4. Re-enter, press Back: leaves upright full-screen only (video keeps playing, no minimise).  Press Back again: mini-player minimise as before.
5. Re-enter, rotate the phone to landscape (auto-rotate on): landscape full-screen. Rotate back: strip returns (upright mode ended by the rotation).
6. Auto-rotate off: step 2 and 3 still work; the orientation lock is never touched.
7. Classic style: in upright full-screen the exit button appears at the end of the control row and exits upright mode.
8. Normal 16:9 video, portrait: full-screen button forces landscape exactly as before; button again returns to portrait.
9. Shorts (from the Shorts shelf): Shorts layout unchanged, no full-screen difference.
10. Upright full-screen on, swipe to / open another video: new video starts in the strip.
11. Upright full-screen on, minimise (Back after step 4, or the minimise gesture), tap the mini-player bar: player expands; no crash, no stuck layout.
12. Upright full-screen on, then PiP/background and return: layout is the strip or upright, never a half state.
