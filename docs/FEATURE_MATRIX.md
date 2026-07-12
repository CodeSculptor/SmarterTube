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
> Unknown rows, but they must be listed here honestly rather than assumed to work.

Last reviewed for: `v0.4.2-beta.8+st31.94`. Rows re-verified on a device this cycle: Shorts
playback (seek bar, auto-hide, swipe-to-next), Settings screen (bottom-sheet panel over player,
Back navigation). Other rows are carried from prior shipped behaviour — treat anything not
explicitly re-tested as provisional and confirm against the release checklist before 1.0.

## Browsing & navigation

| Feature | Status | Notes |
|---|---|---|
| Portrait home (drawer navigation) | Works | Shipped since alpha; re-verify per checklist |
| Search (input + suggestions) | Works | |
| Search results grid | Works | |
| Voice search | Works | Mic button in search toolbar (RecognizerIntent); falls back to keyboard if no recognizer. Verified on device (#8) |
| Channel page (header/tabs) | Works | Identity header (circular avatar + subscriber count); native content tabs (Videos / Shorts / Live / Playlists, one swipeable 2-column grid per group) |
| Channel uploads | Works | |
| Subscriptions feed | Works | Drives upload notifications |
| History | Unknown | Not re-verified this cycle |
| Playlists | Unknown | Not re-verified this cycle |
| Settings screen (portrait) | Works | Mobile-friendly inputs (#26, verified on device): categorical single-choice settings collapse to a value row + bottom-sheet picker; numeric ranges (speed, zoom, seek interval, volume, auto-hide) use an inline slider that only drags from the thumb so list scrolling is unaffected; checkbox-heavy screens group into collapsible sections (long screens start collapsed with an "N ON" count, tap a header to expand) |
| Theme picker (light/dark) | Unknown | Recent work; verify on device |
| About screen | Works | |

## Account

| Feature | Status | Notes |
|---|---|---|
| Sign in (OAuth device-code) | Works | |
| Sign out | Works | |
| Account switcher | Unknown | Verify if exposed in phone UI |
| Subscribe / unsubscribe | Works | Channel-page pill; state resolved from first upload's metadata |
| Notification bell / inbox | Not implemented | Upstream source was dead; pivoted to subscriptions feed |
| Upload notifications (push) | Works | Subscriptions-feed poll, shipped 31.93-mobile-1.3 |

## Player

| Feature | Status | Notes |
|---|---|---|
| Video playback (landscape) | Works | |
| Portrait player | Partially works | Channel avatar + tappable channel row; native like/dislike under views; verify comments |
| In-panel comments (portrait) | Partially works | Read-only; posting is blocked on auth/PoToken |
| Comments posting | Not implemented | Blocked on innertube auth + PoToken |
| Shorts playback | Works | TikTok-style UX: swipe pager, tap-to-pause, vertical action rail (like/dislike/comments/channel), auto-hide chrome, seek bar visible and auto-hides with chrome (#28, fixed beta.8). VERIFIED-ON-DEVICE. |
| Save to playlist (portrait nav bar) | Works | "Playlists" tab in the portrait bottom nav bar opens a bottom-sheet checklist (same add/remove behaviour as the landscape player's playlist button) for the current video; the panel slides up above the nav bar, is capped to the area below the video (never overlaps it) and scrolls internally when the list is long. VERIFIED-ON-DEVICE |
| Play / pause / seek | Works | Play/pause icon stays in sync after rotating into landscape (rebuilt action re-synced to real playback state). Verified on device |
| Quality menu | Unknown | |
| Captions | Unknown | |
| Playback speed | Works | Opens as a translucent bottom-sheet card over the player (beta.8); video stays visible behind a dim scrim. Known issue: player SurfaceView shrinks in landscape while the panel is open ([#29]) |
| SponsorBlock | Works | Upstream feature |
| Return YouTube Dislike | Works | Upstream feature |
| DeArrow | Unknown | Upstream feature; verify in phone UI |
| Casting / Chromecast | Not implemented | |

## Updates & distribution

| Feature | Status | Notes |
|---|---|---|
| Check for updates (phone) | Works | Gate A: scheme-aware, channel + ABI; see UPDATER_COMPATIBILITY.md |
| Upstream auto-update check on phone | Not applicable | Inert (phone versionCode ≫ upstream); fork uses its own checker |
| In-app APK install | Not implemented | Update opens the GitHub asset/release URL for manual install |
| Self-hosted F-Droid repo | Works | GitHub Pages, our own signed APKs; auto-published on release (fdroid/) |

## Layout & orientation

| Feature | Status | Notes |
|---|---|---|
| Phone portrait | Works | Primary target |
| Phone landscape | Unknown | Audit in Gate C |
| Tablet portrait | Unknown | Audit in Gate C |
| Tablet landscape | Unknown | Audit in Gate C |
| TV / leanback interface | Not applicable | Phone/tablet product; use upstream SmartTube for TV |

## Platform

| Feature | Status | Notes |
|---|---|---|
| Up to 8K / 60fps / HDR | Works | Upstream capability |
| No Google Play Services required | Works | Upstream capability |
| No ads | Works | Upstream capability |
| Official F-Droid / IzzyOnDroid listing | Not applicable | IzzyOnDroid ruled out (AI-policy); official fdroiddata deferred (F-Droid key → no in-place upgrade). Ship via GitHub Releases + Obtainium + self-hosted F-Droid repo |
