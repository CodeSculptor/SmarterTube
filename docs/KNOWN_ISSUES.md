# SmarterTube Known Issues

Tracked issues for the current beta. Keep this honest and current — it is part of the release
checklist and what makes a beta release trustworthy.

Current release: `v0.4.2-beta.8+st31.94`  ·  Upstream SmartTube base: `31.94`  ·  Channel: beta

## Status / classification

- SmarterTube is **beta**, not stable. Previous `31.xx-mobile-1.x` releases were labelled as
  full releases prematurely; they are superseded by the beta reset (see
  [VERSIONING.md](VERSIONING.md)).
- Many feature-matrix rows are **Unknown** (not re-verified on a device this cycle). Unknowns
  for core flows must be resolved before any 1.0 release.

## Background playback + lockscreen controls (added beta.2)

- **Background audio** is now on by default (BACKGROUND_MODE_SOUND). Locking the phone keeps
  audio playing. Users can change this in player settings.
- **Lockscreen media controls** appear on the Android lockscreen and notification shade, showing
  the current video title and author. Controls update as you navigate between videos.
- **Seeking from the lockscreen** is not supported (the MediaSession does not expose a seek
  action through the notification compact view). This may be added in a later release.

## Known functional gaps

- **Comments are read-only.** Posting comments is blocked on innertube authentication + PoToken
  work; the TV client has no composer. Not planned for the beta line.
- **Notification bell / inbox is not implemented.** The upstream notifications source was dead;
  upload alerts are delivered via a subscriptions-feed poll instead.
- **No in-app APK install.** "Check for updates" detects a newer release and opens the GitHub
  asset/release URL; the user installs the APK manually.
- **Casting / Chromecast is not implemented.**

## Settings UI (mobile-friendly inputs)

Reworked for touch ([#26], verified on device — Player, General, Auto Frame Rate, Subtitles, etc.):

- **Categorical single-choice settings** collapse to one value row that opens a bottom-sheet
  picker instead of a long inline radio list (e.g. Video buffer, Audio language, Network engine).
- **Numeric ranges** (playback speed, video zoom, seek interval, volume, auto-hide timeout) use an
  inline slider over the option list. The slider only drags from its thumb, so scrolling the list
  past a slider does not change its value.
- **Checkbox-heavy screens** (e.g. General) group each category under a collapsible section header.
  On long screens (3+ sections) the sections start collapsed — a folded checkbox section shows an
  "N ON" count — so the whole screen can be scanned at a glance; tap a header to expand its rows.
  Short screens stay expanded. (Phase C, verified on device.)
- Switch rows are unchanged.

[#26]: https://github.com/CodeSculptor/SmarterTube/issues/26

## Layout / orientation (to be audited in Gate C)

- Phone landscape and tablet portrait/landscape layouts are **unverified**. TV/leanback layout
  assumptions may leak into these orientations.

## Shorts

- The TikTok-style Shorts redesign shipped in Gate B (`v0.4.1-beta.1+st31.93`) and is
  **VERIFIED-ON-DEVICE**: swipe pager, tap-to-pause, vertical action rail (like/dislike/comments/
  channel), auto-hide chrome, seek bar above system gesture pill.
- Gate C fixed a loop regression: upstream 31.94 changed the repeat handler from
  `setPositionMs(100)` to `setPositionMs(0)`; the seek to 0ms briefly blanked the surface on
  loop. Fixed in stmobile by covering the seek with the current video's thumbnail poster.
  **VERIFIED-ON-DEVICE Gate C.**
- **Shorts seek bar now visible and auto-hides** — fixed in beta.8 ([#28]). The Leanback
  overlay was hidden for Shorts, blocking the seek bar; the fix keeps the overlay always-shown
  and controls the bar's visibility directly. Dim scrim removed for Shorts. Auto-hide honours
  the *Auto-hide UI* timeout setting; when the setting is off (0s) the seek bar stays visible.
  **VERIFIED-ON-DEVICE.**

## Channel page (native content tabs)

Native content tabs (Videos / Shorts / Live / Playlists — one swipeable 2-column grid per group)
are **VERIFIED-ON-DEVICE**. Remaining items:

- **Shorts / Playlists cards render with the landscape video-card layout.** Shorts are portrait
  and playlist cards differ, but every tab reuses the standard 16:9 card for now. Cosmetic; a
  per-card-type layout is a later refinement.
- **Sort chips (Latest / Popular / Oldest) are not wired.** Upstream exposes them separately from
  the content tabs; the native channel page does not surface them yet.

Resolved this release:

- **Channels list keeps its content after Back** — fixed in beta.7. Backing out of a channel into
  the subscriptions "Channels" list previously showed the previous channel's content until
  pull-to-refresh; the list now retains its own content ([#22], fixed).

[#22]: https://github.com/CodeSculptor/SmarterTube/issues/22

## Player

- **Back stops the video and leaves** (phone UX). With background audio on (the default), the
  shared player otherwise kept the engine alive on Back and navigated to the channel, leaving
  audio playing and looping Back between player and channel ([#23], fixed). Home / lock-screen
  background audio is unchanged; PIP mode still enters PIP on Back.
- **Back from a video opens a duplicate channel page (pre-existing).** Leaving the player with Back
  re-launches the channel as a fresh activity (shared `ViewManager.startParentView`), so returning
  from a video opened inside a channel needs one extra Back to leave the channel. Minor (no crash /
  no lingering audio); was masked in beta.6 by the audio loop that [#23] fixed. Tracked in [#24].
- **Landscape play/pause icon was out of sync after rotation** — fixed ([#27], verified on device).
  Rotating into landscape rebuilds the full transport control set mid-playback, creating a fresh
  play/pause action that defaulted to the PLAY icon; it is now re-synced to the real playback state
  on every rebuild, so the icon is correct from the moment landscape is entered.
- **Settings panel Back navigation fixed** — beta.8. Opening a settings panel (e.g. Video speed)
  during playback and pressing Back on the video previously re-opened the panel. Fixed by
  excluding the dialog activity from the ViewManager navigation stack. **VERIFIED-ON-DEVICE.**
- **Settings panels now show as a bottom-sheet card over the player.** Single-category settings
  dialogs (e.g. Video speed, Quality) open as a translucent bottom-sheet so the video stays
  visible behind a dim scrim. Tap outside the card or press Back to close without leaving the
  player. **VERIFIED-ON-DEVICE.**
- **Video shrinks in landscape when a settings panel is open** ([#29]). When the video is playing
  in landscape fullscreen and a settings panel (Video speed, Quality, etc.) is opened, the player
  SurfaceView resizes to fit a smaller window as the system nav bar re-appears for the dialog
  window. The video returns to full size when the panel is dismissed. Portrait playback is
  unaffected. Being tracked for a future fix; the feature otherwise works correctly in portrait.
- **Save to playlist from the portrait nav bar** — the "Playlists" tab in the portrait bottom nav
  bar opens a bottom-sheet checklist that adds/removes the current video to/from the user's
  playlists (same behaviour as the landscape player's playlist button). The panel slides up above
  the nav bar, is capped to the area below the video so it never overlaps it, and scrolls
  internally when the list is long. **VERIFIED-ON-DEVICE.**
- **Pop-up mode shows the channel page instead of the video** ([#33], open). After opening a
  channel from the player and pressing Back, entering pop-up (PiP) mode renders the channel page
  while only the audio reflects the playing video. Likely related to the same back-stack handling
  as [#24]. Portrait pop-up entry and a portrait video queue are requested separately ([#31], [#32]).

[#23]: https://github.com/CodeSculptor/SmarterTube/issues/23
[#24]: https://github.com/CodeSculptor/SmarterTube/issues/24
[#27]: https://github.com/CodeSculptor/SmarterTube/issues/27
[#28]: https://github.com/CodeSculptor/SmarterTube/issues/28
[#29]: https://github.com/CodeSculptor/SmarterTube/issues/29
[#31]: https://github.com/CodeSculptor/SmarterTube/issues/31
[#32]: https://github.com/CodeSculptor/SmarterTube/issues/32
[#33]: https://github.com/CodeSculptor/SmarterTube/issues/33

## Updater notes

- The phone "Check for updates" reads the fork's GitHub releases. Its manifest URL / release
  data only reflects releases that have actually been published; before the first beta-reset
  release exists, a check may report "up to date" against whatever is currently published.
- The upstream auto-update check is inert on the phone (the phone `versionCode` sits far above
  upstream SmartTube's), so it does not offer upstream TV APKs. See
  [UPDATER_COMPATIBILITY.md](UPDATER_COMPATIBILITY.md) → As-Built Updater Audit.

## TV functionality

- This is a phone/tablet companion to SmartTube, **not** a TV replacement. The TV/leanback
  interface is intentionally not part of this product; use upstream SmartTube on a TV.
