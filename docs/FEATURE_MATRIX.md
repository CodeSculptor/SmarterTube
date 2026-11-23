# SmarterTube Feature Matrix

Status of visible phone/tablet features. This is a living document — update the affected rows
with every change (see [RELEASE_PROCESS.md](RELEASE_PROCESS.md) → Agent Change Discipline).

Status values:

- **Works** — verified working on a real device.
- **Partially works** — usable but with known gaps (see Notes / [KNOWN_ISSUES.md](KNOWN_ISSUES.md)).
- **Broken** — present but does not work.
- **Not implemented** — intentionally absent for now.
- **Not applicable** — does not apply to the phone/tablet product.
- **Unknown** — not yet verified on a device.

> **Unknown blocks a stable (1.0) release** for core user flows. Beta releases may ship with
> Unknown rows, but they must be listed here honestly rather than assumed to work. As of
> 2026-10-12 there are no Unknown rows left: long-standing ones were accepted on the strength of
> real-world use without reports.

Last reviewed for: `v0.5.0-beta.8+st32.56`. Rows re-verified on a device this cycle: Status bar
insets (#37), Player controls tap-to-hide (#39), Up-next long-press menu (#42), Settings screen
(nested dialogs, context-menu ticks, #43). Other rows are carried from prior shipped behaviour — treat anything not
explicitly re-tested as provisional and confirm against the release checklist before 1.0.

## Browsing & navigation

| Feature | Status | Notes |
|---|---|---|
| Portrait home (drawer navigation) | Works | Shipped since alpha; re-verify per checklist |
| Video duration badge on thumbnails | Works | YouTube-style length pill on the bottom-right of every thumbnail — browse grid, shelves, search, channel pages, and the portrait player's up-next list. Sourced from `Video.badge`; hidden for playlists/channels/Shorts. VERIFIED-ON-DEVICE |
| Watched progress bar on thumbnails | Works | YouTube-style red bar along the bottom of every thumbnail (browse grid, shelves, search, channel pages, history, portrait up-next); length = YouTube's server-side resume percentage (`Video.percentWatched`, local playback state as fallback), tiny progress rounded up to 1%, hidden when unwatched and on Shorts; duration badge lifts above it. Cards refresh on Back from the player via the presenter's `ACTION_SYNC`. VERIFIED-ON-DEVICE |
| Search (input + suggestions) | Works | Empty box lists recent searches; long-press one to remove it or clear history. After a search, a gear button in the toolbar opens upstream's filters (upload date, duration, type, features, sort by) and reloads the results (#92). VERIFIED-ON-DEVICE |
| Search results grid | Works | |
| Voice search | Works | Mic button in search toolbar (RecognizerIntent); falls back to keyboard if no recognizer. Verified on device (#8) |
| Channel avatars on feed cards | Implemented, unverified | YouTube-style row under the thumbnail: 40dp circular channel avatar left, title + author/stats right, on standard video cards. Home shelf cards are ~66% of screen width on phone portrait (about 1.5 visible, next peeks; 40% landscape, 42% tablet) so title and stats fit; phone portrait Home grid/Search is a single full-width column (landscape 2, tablet 3/4) (Home, Search, Subscriptions, shelves, channel grids); not on Shorts/playlist cards. Tapping it opens the channel. Avatars come from one independent metadata call per channel (keyed by channel id, or by author name when the feed item has no channel id) (de-duplicated, 2 at a time, delayed and dropped for cards scrolled past, failures not retried), cached in memory and on disk (channel id to URL, image via Glide), so warm starts make no calls. Slot stays hidden until resolved, so card height is unchanged. Off switch: Settings > Channel avatars on cards |
| Channel page (header/tabs) | Works | Identity header (circular avatar + subscriber count); native content tabs (Videos / Shorts / Live / Playlists, one swipeable 2-column grid per group); sort chips (Latest / Popular / Oldest) on the Videos tab; in-channel search (results open as their own tab); vertical 9:16 Shorts cards and playlist cards with a video-count badge |
| Channel uploads | Works | |
| Subscriptions feed | Works | Drives upload notifications |
| History | Works | In daily use across many beta releases; no reports of problems (accepted by owner 2026-10-12) |
| Playlists | Works | In daily use across many beta releases; no reports of problems (accepted by owner 2026-10-12) |
| Settings screen (portrait) | Works | Mobile-friendly inputs (#26, verified on device): categorical single-choice settings collapse to a value row + bottom-sheet picker; numeric ranges (speed, zoom, video/audio formats, seek interval, volume, auto-hide; format/zoom sliders restored in v0.6.1-beta.1, #44) use an inline slider that only drags from the thumb so list scrolling is unaffected; checkbox-heavy screens group into collapsible sections (long screens start collapsed with an "N ON" count, tap a header to expand). Nested dialogs return to the screen they were opened from (#43); ticking a Context menu item just enables it (no position popup; order via Context menu sorting, whose list refreshes only on reopening General). Settings is its own screen opened on top of what you were doing: from the side menu, Back returns to Home as you left it (no reload); from the Modern / Tap to pause player, gear > App settings pauses the video and Back returns to it. Classic style has a Full screen button and a Settings gear next to the title in the portrait panel (Modern / Tap to pause: swipe up on the portrait video goes full screen, #62), and the Shorts "You" sheet opens Settings over the Short. VERIFIED-ON-DEVICE |
| Settings search | Implemented, unverified | Search field at the top of the Settings screen finds individual settings by title, description, section/category name, or (for single-choice settings) an option label, e.g. "hindi" finds Language. Results show the section and current value; tapping one opens that section, expands its collapsed group, scrolls to the setting and flashes it (single-choice settings land on their value row / slider). Back returns to the search with your text kept. Index is built on first search by reading each section's options without showing them, and refreshed on return so values stay current. Not searched: Accounts, the phone-only rows (Theme, Player style, Swipe gestures, Notifications, Stats: found by title) and options inside nested pop-ups. IMPLEMENTED-UNVERIFIED |
| Theme picker (light/dark) | Works | In daily use across many beta releases; no reports of problems (accepted by owner 2026-10-12) |
| Pure black dark theme (Settings > Pure black dark theme) | Implemented, unverified | True-black (AMOLED) backgrounds in dark mode, works with Follow system; cards/sheets/chips/dialogs stay a shade above black so they remain distinct. Off by default; no effect in light mode |
| About screen | Works | |

## Account

| Feature | Status | Notes |
|---|---|---|
| Sign in (OAuth device-code) | Works | |
| Sign out | Works | |
| Account switcher | Works | In daily use across many beta releases; no reports of problems (accepted by owner 2026-10-12) |
| Subscribe / unsubscribe | Works | Channel-page pill; state resolved from first upload's metadata |
| Notification bell / inbox | Works (uploads) | YouTube inbox is dead; tab lists uploads found by the subscriptions-feed poll |
| Upload notifications (push) | Works | Subscriptions-feed poll, shipped 31.93-mobile-1.3 |
| Status bar insets | Works | Screens pad below system bars that are actually showing (#37, beta.8): with Fullscreen mode on (default) the bars are hidden and nothing changes; if the OS keeps the status bar visible (seen on a Pixel, Android 17) or Fullscreen mode is off, the top bar sits below it. Verified on device (no change on Samsung) and on an API 36 emulator with forced edge-to-edge; the Pixel case is confirmed by the emulator repro only |

## Player

| Feature | Status | Notes |
|---|---|---|
| Video playback (landscape) | Works | |
| Hold for 2x speed | Unknown | IMPLEMENTED-UNVERIFIED. Press and hold on the video (regular player, portrait strip or landscape; not Shorts, not PiP) plays at 2x with a "2x" chip at the top; release restores the exact previous speed (also if the player pauses mid-hold). Cancels the tap / swipe / double-tap the touch had started. Not started on visible controls or while paused. Setting: Settings > Hold for 2x speed (on by default). Code: `HoldSpeed` + `HoldSpeedPolicy` in stmobile/ui/playback. |
| Mini-player (#96) | Unknown | IMPLEMENTED-UNVERIFIED. Back in the player (regular videos) minimises it to a bar under Home / Search / Channel / Channel uploads instead of closing it: poster, title, channel, progress line, play/pause, close; tap the bar to expand. Setting: Settings > Mini-player (on by default; off = Back closes the video as before). Shorts keep their own Back behaviour. The mini-player wins over Background mode = PiP for Back (PiP is still entered with the Home key); with the setting off, Back enters PiP as before. The player runs in its own task (taskAffinity) so it can stay parked; `ViewManager` starts screens from the foreground activity via a phone-only hook so dialogs/channel opened from the player stay in that task. Design: [design/mini-player.md](design/mini-player.md) |
| Portrait player | Partially works | Channel avatar + tappable channel row; native like/dislike under views; verify comments |
| In-panel comments (portrait) | Partially works | Read-only; posting is blocked on auth/PoToken |
| Comments posting | Not implemented | Blocked on innertube auth + PoToken |
| Shorts playback | Works | TikTok-style UX: swipe pager, tap-to-pause, vertical action rail (like/dislike/comments/channel), auto-hide chrome, seek bar visible and auto-hides with chrome (#28, fixed beta.8); no Classic control rows over the rail in any Player style (#50, fixed v0.9.1-beta.1). VERIFIED-ON-DEVICE. On screens wider than 10:16 (tablets, foldables) the Short is fitted with side bars instead of overflowing the screen (#30, v0.6.0-beta.1; verified on a 1536x2048 emulator). |
| Save to playlist (portrait nav bar) | Works | "Playlists" tab in the portrait bottom nav bar opens a bottom-sheet checklist (same add/remove behaviour as the landscape player's playlist button) for the current video; the panel slides up above the nav bar, is capped to the area below the video (never overlaps it) and scrolls internally when the list is long. VERIFIED-ON-DEVICE |
| Play / pause / seek | Works | Play/pause icon stays in sync after rotating into landscape (rebuilt action re-synced to real playback state). Verified on device |
| Player controls show/hide | Works | Tap the video to show the controls; tap empty video while they're showing to hide them (#39, beta.8). Taps on buttons, the seek bar and suggestion cards, drags, and double-tap seek (controls hidden) are unaffected. VERIFIED-ON-DEVICE |
| Player style (Classic / Modern / Tap to pause) | Works | Settings > Player style (#46). **Classic**: upstream SmartTube control rows. **Modern** (default for new installs; upgrades keep Classic): phone-style controls over the video. Portrait has close, CC and settings at the top, big previous / play-pause / next in the middle, and time + fullscreen above a seek bar on the bottom edge. Landscape adds title/channel and a like / dislike / comments / save / share / more row with More videos (up-next sheet). The settings / more sheet lists every other player action from Setup player buttons. The fullscreen button forces landscape/portrait and hands back to auto-rotate once the phone is turned to match. **Tap to pause**: Modern controls, but a tap plays/pauses (controls stay up while paused) and a double tap seeks. VERIFIED-ON-DEVICE |
| Swipe gestures (landscape player) | Works | Settings > Swipe gestures (#48). With the controls hidden, a vertical swipe on the right half changes media volume and on the left half this window's brightness, with a small level pill. Taps, double-tap seek and Tap to pause are unchanged; not near the screen edges (system gestures), not in Shorts or the portrait strip. On by default in Modern / Tap to pause, off in Classic. VERIFIED-ON-DEVICE |
| Modern seek bar extras + chapters | Works | Modern / Tap to pause seek bar: dragging shows a preview bubble (storyboard frame per the Seek preview setting, chapter title, time); SponsorBlock colour markers (per your SponsorBlock settings) and chapter marks on the bar. Chapters also lead the up-next list (portrait panel and landscape More videos) as a labelled strip; tap = seek. The time line reads "1:17 / 19:06 • Chapter" for the current chapter. VERIFIED-ON-DEVICE |
| Portrait description (YouTube parity) | Works | Stats line under the title: views, upload age and first hashtag; expand for exact views, absolute upload date and up to three hashtags. URLs, #hashtags (opens a search) and timestamps (seek) are tappable in the description and in comments; tapping elsewhere still closes the description. YouTube video links in the description get a card (thumbnail, title, channel, age) below the text, max 3. Notification bell next to Subscribe once subscribed (All / Personalised / None). Not built: inline card placement and the Twitter/Facebook chips YouTube shows. VERIFIED-ON-DEVICE |
| Up-next list (portrait) | Works | Tap a row to play it; long-press opens the same video menu as Home thumbnails (#42, beta.8). VERIFIED-ON-DEVICE |
| Pop-up (PIP) mode | Works | Shows the playing video, including after visiting a channel from the player then returning (#33 fixed). The phone player skips relaunching Home on PIP entry (Home is already behind the pop-up), avoiding a task-clear race that used to destroy the player. Closing the pop-up window stops playback — the engine is released instead of leaking audio behind the closed window (#35). VERIFIED-ON-DEVICE |
| Quality menu | Works | In daily use across many beta releases; no reports of problems (accepted by owner 2026-10-12) |
| Captions | Works | CC button (Modern: top-right, next to settings) opens the Subtitles menu; picking a track (incl. auto-generated / translated) shows subtitles. Once a track is chosen the CC button is an on/off toggle (long-press reopens the menu); the Modern gear sheet also has a Subtitles row that always opens the language menu. Fixed in v0.9.2-beta.1: they used to crash the player with a playback error (#52) because the upstream web-client path gets empty caption files; videos now load via visionOS (MediaServiceCore fork pin, #53). VERIFIED-ON-DEVICE |
| Audio track (dubs / original) | Implemented | Modern gear sheet: an Audio track row, shown only when the video has more than one audio language, lists them (✓ marks the current one); picking one switches audio for that video only. The persisted preference stays in Video settings > Audio language. IMPLEMENTED-UNVERIFIED |
| Playback speed | Works | Opens as a translucent bottom-sheet card over the player (beta.8); video stays visible behind a dim scrim. The video keeps its full size behind the sheet in landscape (#29, fixed v0.6.0-beta.1: upstream's TV "resize video to fit dialog" zoom is turned off once on phones). VERIFIED-ON-DEVICE |
| SponsorBlock | Works | Upstream feature. Per-channel exclusion via long-press menu (enable in Settings > General > Context menu) and optional player button (Settings > Player > Setup player buttons; landscape only) — verified on device (#40). Also in the up-next long-press menu (#42, beta.8) |
| Return YouTube Dislike | Works | Upstream feature |
| DeArrow | Works | In daily use across many beta releases; no reports of problems (accepted by owner 2026-10-12) |
| Casting to Chromecast | Not supported (by design) | Needs Google Play Services (Cast SDK); upstream has none either. Receiver side: Settings > Remote control (#7) |

## Updates & distribution

| Feature | Status | Notes |
|---|---|---|
| Check for updates (phone) | Works | Gate A: scheme-aware, channel + ABI; see UPDATER_COMPATIBILITY.md |
| Update notice on launch + one-time "What's new" | Works | v0.8.0-beta.1; parsed from the release notes' "What's new" section |
| Upstream auto-update check on phone | Not applicable | Inert (phone versionCode ≫ upstream); fork uses its own checker |
| In-app APK install | Not implemented | Update opens the GitHub asset/release URL for manual install |
| Self-hosted F-Droid repo | Works | GitHub Pages, our own signed APKs; auto-published on release (fdroid/) |

## Layout & orientation

| Feature | Status | Notes |
|---|---|---|
| Phone portrait | Works | Primary target |
| Phone landscape | Works | #25 audit (v0.6.0-beta.1): only the player and its sheets/dialogs rotate on phones; browse, search, channel and settings are portrait-locked by design. Landscape player, controls and settings sheets verified on device (#29 fixed) |
| Tablet portrait | Works | #25 audit on a 1536x2048 sw768dp emulator (API 36): Home rows, side menu, search grid (3 columns), channel page + tabs, settings, portrait player strip + up-next, Shorts (fitted, #30). Light-theme side-menu icons fixed (tinted to the text colour) |
| Tablet landscape | Works | #25 audit (same emulator): Home rows, side menu (scrolls), search, channel, settings, full-screen player + settings sheet (#29 fixed). Channel grid re-flows to 4 columns on rotation, like Home (fixed) |
| TV / leanback interface | Not applicable | Phone/tablet product; use upstream SmartTube for TV |

## Platform

| Feature | Status | Notes |
|---|---|---|
| Up to 8K / 60fps / HDR | Works | Upstream capability |
| No Google Play Services required | Works | Upstream capability |
| No ads | Works | Upstream capability |
| Official F-Droid / IzzyOnDroid listing | Not applicable | IzzyOnDroid ruled out (AI-policy); official fdroiddata deferred (F-Droid key → no in-place upgrade). Ship via GitHub Releases + Obtainium + self-hosted F-Droid repo |
| Phone UI translations | Partially works | Phone-only strings are now all in string resources (nothing hardcoded; bottom bar/sheet labels reuse upstream's already-translated strings, uploads count is a proper plural). No `values-xx` folders yet: English fallback for the ~12 phone-only screens until the owner picks a translation workflow, see [design/translations.md](design/translations.md). IMPLEMENTED-UNVERIFIED |
