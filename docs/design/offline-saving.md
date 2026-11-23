# Offline saving (design, awaiting approval)

Status: **PHASE 1, design only. No code written.** Branch `claude/feat-offline-saving`.
Goal: let a user save a video (or just its audio) to the phone for personal offline viewing, and play
it later through the same player with no network.

Everything marked **[verified in code]** was read in this repo or in the MediaServiceCore checkout.
Everything marked **[spike]** is an assumption about YouTube's server behaviour that cannot be settled
by reading code and must be proved on a device before milestone 1 is called done.

---

## 0. Findings: what exists today

**No offline/save code exists in the app.** [verified in code]
- `grep` for DownloadManager/offline/cache finds only: `ApkDownloader` (stmobile, downloads the update
  APK with the system `DownloadManager` into public Downloads), `MediaStoreFile.kt` (common, a
  `Documents/<pkg>/` MediaStore wrapper used for backups), and upstream's backup/bridge dialogs.
- No `SimpleCache`/`CacheDataSource` is used by the player. The bundled ExoPlayer fork
  (`exoplayer-amzn-2.10.6`) does contain `offline/` (`DownloadManager`, `ProgressiveDownloader`,
  `DownloadService`) and `upstream/cache/`, but they are unused and are written for 2.10-era
  progressive/DASH/HLS manifests, not for YouTube's signed per-itag URLs or SABR.
- WorkManager 2.9.1 (`androidx.work`) is already an `stmobileImplementation` dependency
  (`NotificationPollWorker`). OkHttp 3.12.13 is present. No ffmpeg, no mp4parser, no Media3.
- stmobile already runs one foreground service (`MediaNotificationService`, tied to the MediaSession),
  declares `ACCESS_NETWORK_STATE` and (via main manifest) `POST_NOTIFICATIONS`.
- `minSdk` is 17 (`SharedModules/constants.gradle`); stmobile `targetSdk` is 33.

**How the player gets sources today** [verified in code]
`VideoLoaderController.onFormatInfo` picks, in order: unplayable message; DASH from the format list
(`player.openDash`, optionally merged with an HLS manifest); **SABR** (`player.openSabr`) when the
first adaptive format is `FORMAT_TYPE_SABR`; live DASH/HLS; and last a **progressive "url list"**
(`openUrlList`, "always LQ"). `ExoMediaSourceFactory` builds each; for progressive URLs it already
uses `ExtractorMediaSource`, and `ExoPlayerController` already uses `MergingMediaSource` to play two
sources in sync.

**What MediaServiceCore returns** [verified in code]
`MediaFormat` has `getFormatType()` = `DASH` (0, has a direct `getUrl()`), `REGULAR` (1, muxed
progressive, "url formats"), or `SABR` (2). `YouTubeMediaFormat` marks a format SABR when
`VideoFormat.isBroken()`, meaning it has no `url`, `cipher` or `signatureCipher`; the player then
streams through a single `serverAbrStreamingUrl` and a protobuf SABR session, not per-format URLs.
`MediaItemFormatInfo` exposes `containsSabrFormats()`, `containsDashFormats()`, `containsUrlFormats()`,
`containsHlsUrl()`, `isLive()`, `isLiveContent()`, `isUnplayable()`. `VideoInfoService` walks a client
list (`WEB_EMBED, ANDROID_VR, ANDROID_REEL, TV, WEB, WEB_SAFARI, IOS, GEO, MWEB, TV_LEGACY, ...`); the
code comments say ANDROID_VR/ANDROID_REEL "don't require pot and cipher", i.e. they tend to hand out
plain direct URLs, while WEB-family clients can come back SABR-only. Which client wins for a given
video varies and is decided inside MediaServiceCore (not our code).

**Consequence:** the app already has the three data shapes we must handle, and the format type tells
us at runtime, per video, whether a plain-file save is possible. We do not have to guess.

---

## 1. What can be saved (and what cannot)

| Case | Saveable? | How we know | UI |
|---|---|---|---|
| Adaptive DASH formats with direct URLs (separate video-only + audio-only itags) | **Yes** | `containsDashFormats()` and a format with non-null `getUrl()` | Normal Save |
| Muxed progressive (`containsUrlFormats()`, usually <=360p) | **Yes** (fallback only) | `FORMAT_TYPE_REGULAR` | Save, labelled "Low quality only" |
| **SABR-only** (`containsSabrFormats()`, no per-format URL) | **No in v1** | first adaptive format is `FORMAT_TYPE_SABR` | Menu item stays visible but disabled-looking; tapping explains: "This video can't be saved right now." |
| **Live** or live-in-progress, premieres not started | **No** | `isLive()` | "Live streams can't be saved." |
| Finished live-stream recordings | Treated like any other video (case 1/3), since `isLive()` is false after the stream ends | same checks | Normal |
| DRM / paid movies/rentals / members-only needing licence | **No** | `isUnplayable()`, or only HLS/encrypted formats; no plain-URL formats | "This video can't be saved." |
| Age-restricted needing sign-in | Same rule as normal: saveable only if direct URLs come back | | Same |
| Shorts | **Yes if direct URLs exist**, same path as a regular video | | Save item in Shorts "You" sheet is deferred past M2 |

Rules:
- The Save entry is **decided at tap time** from a fresh `getFormatInfoObserve`, not from the card, because
  the answer is per-video and per-moment. Cards never show a "saveable" badge.
- We **never** try to work around SABR, signing, DRM or PoToken protections. If MediaServiceCore does
  not return a plain URL for the format we need, the answer is "not saveable". (A future SABR downloader
  would need a separate decision and a legal read; out of scope.)
- Expected real-world hit rate is unknown. **[spike]**: sample ~30 popular + long-tail videos through
  the app's real client chain and record how often a saveable adaptive/progressive result comes back.
  If it is under ~70% the feature is much less useful than it sounds and the owner should know before
  M2.
- Direct URLs expire (the `expire` param, typically ~6 h) and may require the same IP/`n`-param
  handling as playback. We fetch the URL list **at download start and again on every resume**, never
  persist URLs. **[spike]**: confirm a ranged GET with a normal OkHttp client (no player) is not
  throttled or 403'd (MediaServiceCore already does `n`-param unthrottling; download may still need
  chunked ranged requests of ~1-10 MB to avoid throttling).

Legal framing: personal, temporary offline copy of content the user could otherwise watch, stored in
the app's private area, not shared or exported (see section 3 and 7). We say this plainly and make no
claim that it is "allowed everywhere"; YouTube's terms restrict downloading outside its own offline
feature, so the owner should read the Decisions list (D1) before shipping.

---

## 2. Download engine

Options:

| | Pros | Cons |
|---|---|---|
| **A. System `DownloadManager`** | Zero code for notification/retry/resume; exists on API 9+ | Cannot re-sign expired URLs on retry; no per-file progress we can show in-app cleanly; the file is visible/shared storage; cannot download two files as one "item" with a merged state; Wi-Fi-only works but pause/resume UX is the system's |
| **B. WorkManager `CoroutineWorker`/`Worker` + OkHttp ranged GETs** | Survives process death; constraints give "Wi-Fi only" (`NetworkType.UNMETERED`) and "network connected" for free; already a dependency; works down to API 14 | Needs a foreground notification for long jobs (API 31+ expedited/foreground rules); progress and pause are ours to build; 2.9.1 on Android 14 needs `foregroundServiceType` handling |
| **C. Own foreground `Service`** (like `MediaNotificationService`) | Full control of pause/resume/notification actions | We re-implement scheduling, constraints and restart; Android 14 FGS type `dataSync` rules and 6 h cap apply anyway |

**Recommendation: B, with a worker that runs as a foreground (`setForegroundAsync`) job**, one queue
job processing items sequentially, backed by a small on-disk state file per item (see section 3).
Reasons: constraints and persistence are exactly what a "download this later when I'm on Wi-Fi"
feature needs; we already ship and test WorkManager; and it keeps the code in `stmobile/` only.

Behaviour:
- **Per-item state machine**: `QUEUED -> RESOLVING -> DOWNLOADING(video) -> DOWNLOADING(audio) -> DONE`
  plus `PAUSED`, `FAILED(reason)`. Stored as JSON next to the files.
- **Resume**: each stream is written to `<name>.part`; on resume we send `Range: bytes=<size>-`. If
  the server returns 403/410 (expired URL) we re-resolve the format info once and continue with the
  fresh URL for the same itag (itag and expected content length must match, otherwise restart that
  stream).
- **Retry**: exponential backoff via WorkManager (`BackoffPolicy.EXPONENTIAL`, 30 s base), max 5
  attempts then `FAILED` with a "Retry" action.
- **Notification**: one ongoing notification "Saving N videos, 42%" with Pause/Cancel actions; a
  separate completion notification "Saved: <title>" (tap opens the library). Channel
  `offline_downloads`, low importance (no sound). Uses the existing `POST_NOTIFICATIONS`; if the user
  declined it the download still runs (foreground notification is allowed) and the library is the
  source of truth.
- **Settings**: "Download over Wi-Fi only" (default **on**), "Default quality" (see section 4), "Where
  to save" is not offered in v1 (private only).
- **Network loss**: the worker's connected-constraint stops the job on loss and WorkManager reschedules
  it on return; the half-written `.part` is kept. This is independent of, and does not touch,
  `NetworkResumeHelper`/`NetworkResumePolicy` which only concern the *player*. The one interplay:
  **while a saved item is playing from a local file, the network-resume helper must not show
  "Waiting for connection..."** (the player never needed the network). Plan: `NetworkResumeHelper`
  checks "current source is local" (a flag set by the offline playback path) and stays idle. That is a
  3-line stmobile change in `MobilePlaybackFragment` wiring, no `common/` edit.
- **Concurrency**: one download at a time in v1 (video then audio sequentially). Parallel items are a
  later optimisation; they complicate pause semantics and rate-limit risk.
- Storage low check: before starting, require `contentLength * 1.1 + 100 MB` free; otherwise fail with
  "Not enough space".

Old Android: WorkManager supports API 14+. `ForegroundInfo`/notification channels are guarded by
`Build.VERSION`. Foreground service type for API 34+ is irrelevant while targetSdk is 33, but when
targetSdk is raised we must declare `dataSync` (note in the risks).

---

## 3. Storage

**Recommendation: app-private external files dir (`getExternalFilesDir("offline")`), falling back to
internal `getFilesDir()/offline` if external is unavailable.**

| Option | Pros | Cons |
|---|---|---|
| App-private (`getExternalFilesDir`) | No permission on any API 17-36; no scoped-storage work; files are removed on uninstall; not exposed to other apps (a clear stance for section 7); direct `File` access so ExoPlayer reads them with no MediaStore/SAF glue | Not user-browsable; lost on uninstall or "Clear data"; lives in the app's storage quota (counts toward "App size") |
| Public (`Downloads/`, MediaStore) | Survives uninstall; user can copy files out | Scoped-storage code on 10+, `WRITE_EXTERNAL_STORAGE` on <=9, partial `.part` files visible; exposes saved video as exportable files, which is the opposite of the "personal offline use" positioning |

Layout:
```
offline/<videoId>/
  meta.json        title, author, channelId, duration, thumbnail file name, chosen itags, sizes, createdAt, state
  video.mp4|webm   (or progressive.mp4)
  audio.m4a|webm
  thumb.jpg        downloaded once at save time (Glide cache is not durable)
  *.part           in-flight
```
- `meta.json` is the library's index (listing = scan of `offline/*/meta.json`); no database, so no
  migration risk and the feature has no hooks into upstream's data classes.
- No quota imposed by us; show **"N videos, 1.2 GB used / 8 GB free"** in the library header. User
  deletes per item (swipe/long-press) or "Delete all" in the library menu. Deleting a playing item is
  blocked with a toast.
- Android 10+ scoped storage: not an issue for app-private dirs. Android 4.2-9 (API 17-28): same,
  `getExternalFilesDir` needs no permission from API 19; below 19 we fall back to internal.
- **Old Android decision**: the feature is fully available on all supported versions; there is nothing
  to hide on API 17-20. If M1 testing on the Phone_API24 AVD shows trouble, hide the entry points on
  <API 21 and ship notes saying so; the mini-player/live chat features set the precedent of per-API
  gating.
- Thumbnails/metadata: downloaded once (`thumb.jpg`, ~30 KB). Library cards render from local files so
  they work offline; the item is shown with the same card layout as Home using a local Video adapter.
- Backups: Android auto-backup should exclude `offline/` (add to `backup_rules`/`dataExtractionRules`)
  so a restore does not produce metadata without files. Check the current manifest `allowBackup` in M1.

---

## 4. Format choice and merging

**No ffmpeg, no remuxing.** Save the video-only and audio-only streams as two files and play them with
ExoPlayer's `MergingMediaSource` (already used in `ExoPlayerController`) of two `ExtractorMediaSource`s
on `file://` URIs. A/V sync is by timestamps in the containers, which is exactly how DASH playback
already works. Extra APK size: **0** (no new libraries). If a future need to produce a single shareable
`.mp4` appears, that is a separate, size-costly feature (mp4parser ~0.3 MB for same-codec mux; ffmpeg
kit tens of MB per ABI) and is explicitly out of scope.

Format rules:
- **Video+audio**: quality picker lists the distinct heights available from the direct-URL adaptive
  formats (e.g. 360/480/720/1080), each with an estimated size (`contentLength` summed for the chosen
  video + audio itag). Default = settings "Default quality" (default **720p**), capped at 1080p for v1.
  Codec preference: avc1 (H.264) + mp4a (AAC) in MP4 where available because they play on every
  device and the extractor handles them best; VP9/AV1 only if no AVC exists at that height.
- **Audio only**: best AAC (`m4a`) ~128 kbps, or opus/webm if that is all there is. ~2 MB per
  4 min. The audio file alone is the whole item (`audio` only in `meta.json`).
- **Fallback**: if there are no adaptive URLs but there is a muxed progressive format, save that single
  file and flag the item "Low quality".
- Skip: `isDrc()` variants, 4K/8K, HDR/HFR formats (size and decoder risk), storyboards.
- Subtitles: not saved in v1 (would need an external subtitle track plumbing); documented as a
  limitation. Chapters/SponsorBlock segments: the player already derives these from format info and
  metadata calls that need network. Offline we play without them in v1.
- Size estimate shown before starting; "cannot determine size" if `clen` missing (then we confirm
  after the HEAD).

---

## 5. Playback of saved items

New stmobile-only class `OfflinePlayback` that, instead of going through `MediaServiceManager`/format
info, builds the sources directly (two `ExtractorMediaSource` + `MergingMediaSource`, or one for audio
only / progressive) and hands them to the existing engine. `ExoPlayerController` currently exposes
`openUrlList/openDash/openSabr/openHls/openMerged`; there is no "open arbitrary MediaSource" public
entry, and adding one in `common/` is a small, upstream-mergeable addition (one method). The
alternative that avoids `common/` entirely is an **`openUrlList(List<String>)` with a `file://` URI**
for the single-file case, which already works today; the merged video+audio case needs the new method.
**Recommendation: add one tiny `openLocal(...)` method in `ExoPlayerController`, guarded to be a
no-op for TV, and keep everything else in stmobile.** (If the owner wants zero `common/` change, M1
ships audio-only + progressive only, which need no new method, and M2 revisits.)

Behaviour:
- **Offline library screen** (see section 6) -> tap -> opens `MobilePlaybackActivity` with a `Video`
  flagged `isOfflineLocal` and the `videoId`; `VideoLoaderController` normally calls the network for
  format info, so the offline path must **short-circuit before `loadFormatInfo`**. This is the
  riskiest integration point: the minimal hook is a check at the top of the load path in
  `MobilePlaybackFragment` (stmobile) that, for flagged videos, calls `OfflinePlayback` and never
  triggers `VideoLoaderController.loadVideo`. Suggestions/up-next/comments/SponsorBlock are not loaded
  (all need network); the Modern chrome hides the Up-next and Comments cards in offline mode.
- **No network**: fully works; no spinner, no "Waiting for connection" (see section 2).
- **Resume position**: use the existing local state (`VideoStateService`, keyed by videoId) for
  position; that already persists locally. Offline we do not sync watch history to YouTube; positions
  are recorded locally and upstream's history sync will simply see them later or not at all (verify
  in M2, **[spike]**).
- **Mini-player** (`MiniPlayerState/Policy`): works if `OfflinePlayback` plays through the same
  engine/fragment; the mini bar needs only the title/thumbnail from `meta.json`. Verify minimise/
  restore with a local item in M2.
- **Background audio / media notification**: the same MediaSession path is used, so lock-screen
  controls work; audio-only items are the ideal background-listening case.
- **Missing/corrupt file**: on `EXPECTED/IO` error show "This saved video is damaged" and offer
  "Delete".
- Speed/pinch zoom/hold-2x work unchanged (engine features).

---

## 6. Entry points and settings

| Entry | Where | Notes |
|---|---|---|
| **Video menu item "Save offline"** | Long-press on any card (the existing `VideoMenuPresenter` menu, `MENU_ITEM_*` mapping) and the portrait Up-next rows | In common/ upstream menu: add through the stmobile menu hook if there is one, otherwise a single new menu entry + `MenuAction` (small `common/` change). Decide in M1 which is cheaper to merge. |
| **Gear sheet in the player** | Modern player gear sheet gets a "Save offline" row, shown only for non-live regular videos | stmobile-only (the sheet is ours) |
| Portrait player action row (Subscribe/Share/Save/Queue) | Defer; the row is already crowded | M3 option |
| **Library** | Recommendation: a **"Saved" entry in the existing side drawer / You page** rather than a new bottom-nav tab (the nav bar already holds several tabs, including Playlists; to be confirmed against the current layout in M1). Opens `MobileOfflineActivity` (list of cards, header with storage usage, overflow: "Delete all", "Settings") | Owner decision D4 |
| **Download queue/progress** | Same screen: top section "Downloading (2)" with progress bars and pause/cancel; below "Saved" | |
| **Settings** | Settings > "Offline saving": Wi-Fi only (default on), Default quality (default 720p), Audio-only by default (default off), Storage used + "Delete all saved videos", "About offline saving" (the notice from section 7) | |

Long-press menu wording when not saveable: item present but tapping shows the explanatory message
(section 1) rather than hiding it, so users understand why some videos have no option.

---

## 7. Public wording

Positioning: companion app, personal use, respect for creators (companion to SmartTube, not a replacement). No claims about "bypassing" anything. No names of other tools.

**README / FEATURE_MATRIX feature line** (once built):
> Save a video, or just its audio, to watch or listen later without a connection. Saved files stay in
> the app's private storage for your personal use and are removed if you uninstall. Some videos
> (live streams, protected or paid content, and some regular videos) can't be saved.

**FEATURE_MATRIX row (now):** "Offline saving | Designed, awaiting approval".
Later states: Implemented, unverified -> Works.

**Release notes `## What's new` line:** "Save for offline: long-press a video > Save offline to keep it
on your phone (video or audio only). Saved videos are in Saved. Wi-Fi only by default."

**First-use in-app notice** (one-time dialog, through `LaunchDialogQueue`, shown the first time the
user taps Save offline; stored in a pref):

> **Save for offline viewing**
> Saved videos are kept on this device for your own offline use. They're stored in the app's private
> storage, can't be shared from here, and are removed if you uninstall the app. Please respect
> creators: only save what you'd be fine to watch normally. Some videos can't be saved.
> [Save]  [Cancel]

Avoid: "download from YouTube", "ripper", "bypass", "no ads", "copy", anything implying circumvention
or redistribution. Mention creators-first only, not legal conclusions.

---

## 8. Milestones, tests, risks, size

### Milestones
- **M0 (spike, ~0.5 day, no UI):** a debug-only Java class that resolves format info for 30 videos and
  logs: format type, client, direct URL present, expiry, whether a 2 MB ranged OkHttp GET succeeds and
  at what speed. Gate: the hit-rate and throttling answers in section 1. **This decides whether we
  proceed at all.**
- **M1 (smallest useful slice, ~2 days):** **audio-only** and **single-file progressive** saves,
  one at a time, Wi-Fi-only constraint, WorkManager foreground job, private storage + `meta.json`,
  basic "Saved" screen (list, play, delete), playback via the existing `openUrlList(file://)` (no
  `common/` change), first-use notice, long-press menu entry. Audio-only first because it is small
  (cheap to test), exercises the whole pipeline, and gives immediate value (background listening).
- **M2 (~2-3 days):** video quality picker with video+audio merge (`openLocal`), thumbnails, resume
  positions, mini-player/background-audio checks, pause/resume/cancel UI, notification actions, storage
  header, settings, error states, "not saveable" messages, no network-resume interference.
- **M3 (polish, optional):** gear-sheet and Shorts entry points, parallel downloads, subtitles,
  auto-delete after watching, queue ordering.

### Size estimate
About 12-15 new stmobile classes (worker, resolver, storage/meta, notification,
library activity + adapter, offline playback, policy classes for unit tests, settings glue), ~1200-1600
lines for M1+M2, ~250 lines tests. APK size: **~0 MB** (no new libraries). Upstream-merge cost: low if
`common/` is limited to one menu entry and one `ExoPlayerController.openLocal` method (both
additive); zero if M1-only.

### Unit-testable pure logic (JVM, like `NetworkResumePolicy`)
Format selection (height/codec/audio choice, saveable decision from format-type + live flags), state
machine transitions, resume-range computation, free-space check, `meta.json` round-trip.

### Device test script (numbered, to run for M1+M2)
1. Install a build; Settings > Offline saving: confirm Wi-Fi only is on.
2. Long-press a normal 4 min video > Save offline. Expect first-use notice; tap Save. Expect an ongoing
   notification and the item in Saved with a progress bar.
3. Wait until done. Expect "Saved" notification; item shows duration, size and thumbnail.
4. Airplane mode ON. Open Saved > tap the item. Expect playback starts, no "Waiting for connection".
5. Seek, pause, change speed, rotate to landscape and back, hold-2x. Expect all work.
6. Press Home: expect background audio and a media notification; return and play.
7. Minimise to the mini-player (Back) and restore. Expect correct title and playback.
8. Close the player, reopen the item. Expect resume near the previous position.
9. Airplane mode OFF. Save an audio-only item (menu > Save audio only). Expect a small file, plays
   with the screen off.
10. Start a large save (>= 1080p, 20 min). Toggle Wi-Fi off mid-way. Expect the job to pause, then
    resume on reconnect from the same byte (check the size doesn't reset).
11. Switch to mobile data only: expect "Waiting for Wi-Fi" status; turn off the Wi-Fi-only setting and
    expect it to continue.
12. Force-stop the app mid-download, reopen: expect the job to resume.
13. Wait >6 h or edit the stored time (debug): resume should re-resolve the URL, not fail.
14. Try a live stream, a paid/members video, and a known SABR-only video: expect the explanatory
    messages and no download started.
15. Fill storage (or fake low space): expect "Not enough space".
16. Delete a saved video from the library; confirm the folder is gone and storage numbers drop.
    Try to delete the playing item: expect it is blocked.
17. Corrupt a file via `adb shell` (truncate): expect "damaged" message with Delete.
18. Android 7 (Phone_API24 AVD): steps 2-5, 10.
19. Tablet (Tablet_API36 AVD): library layout and steps 2-4.
20. Uninstall and reinstall: confirm saved items are gone (documented behaviour).
21. Regression: normal online playback, Shorts, mini-player, live chat and network-resume still work
    (steps 1, 8 of the network-resume script).

### Risks
1. **Direct-URL availability (biggest).** If most videos arrive as SABR-only, the feature helps few
   people. Mitigated by the M0 spike. **Expiry/throttling** of direct URLs when fetched outside the
   player is unproven.
2. **Terms of service / policy.** Downloading outside YouTube's own offline feature is against
   YouTube's terms. The app is a companion client, not a store distribution, but the owner should
   consciously accept this (D1). Wording stays strictly "personal offline use".
3. **Upstream drift**: MediaServiceCore format selection changes often (SABR rollout); our code
   depends on `MediaFormat.getFormatType()/getUrl()` only. Keep that surface minimal and covered by
   tests.
4. **Playback hook** into the loader flow (section 5): skipping network calls without breaking
   `VideoLoaderController` assumptions (history reporting, suggestions, SponsorBlock). Mitigation:
   bypass at the fragment, never call the loader for local items, and don't report history to
   YouTube.
5. **Storage growth** and low-memory devices: mitigated by the per-item size estimate, a free-space
   check, and visible usage numbers.
6. **Android 14+ foreground-service rules** when targetSdk rises (declare `dataSync`; 6 h cap).
7. **Old devices (API 17-20)**: untested; fallback to hiding the entry points.
8. **Clean-room**: design uses only our code, upstream SmartTube/ExoPlayer and platform docs.

---

## Decisions needed from the owner

Recommendations first.

- **D1. Go/no-go on shipping any download feature given YouTube's terms?** Recommend **Yes, as
  "personal offline use, private storage, no export, no circumvention"** and with the notice in
  section 7. (No = stop here.)
- **D2. Run the M0 spike first and only continue if at least ~70% of sampled videos are saveable?**
  Recommend **Yes** (half a day, no UI).
- **D3. Scope of the first slice: A) audio-only + progressive only (no `common/` change), or B) full
  video quality picker with merge (adds one small `common/` method).** Recommend **A** for M1, then B
  as M2.
- **D4. Where does the library live: A) in the side drawer / You page ("Saved"), or B) a new bottom-nav
  tab?** Recommend **A**.
- **D5. Storage: A) app-private only (removed on uninstall, not exportable), or B) also offer public
  Downloads?** Recommend **A**.
- **D6. Default "Wi-Fi only" on?** Recommend **Yes**.
- **D7. Default quality 720p, capped at 1080p in v1?** Recommend **Yes**.
- **D8. Hide the feature on Android 4.2-4.4 (API 17-20) if testing shows trouble?** Recommend **Yes,
  decide after the M1 emulator run**.
- **D9. Accept two small `common/` additions (a menu entry and `openLocal`) for upstream-merge cost?**
  Recommend **Yes after M1**, since both are additive and tiny; otherwise stay with D3-A.
- **D10. Offline mode hides Up-next/Comments/SponsorBlock, and saves no subtitles in v1?** Recommend
  **Yes**.
