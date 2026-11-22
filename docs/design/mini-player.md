# In-app mini-player (issue #96) — design

Status: **Design approved (Option C, default ON, leave Recents card). PHASE 2 SPIKE FAILED statically, see section 12. Stopped; awaiting decision.**
Branch: `claude/feat-mini-player` (cut from master `32f8da722`, 32.81 line).

## 1. Goal and user need

Pressing Back in the phone player today stops the video and drops back to the previous screen
(issue #23 behaviour, `MobilePlaybackActivity.onBackPressed → finishReally()`). People who start a
video and want to keep listening/watching while they look for the next one have to leave it
running in PiP or lose it.

Wanted: Back **minimises** the player into a bar docked at the bottom of Home / Search / Channel
screens. Playback continues. Tap the bar to expand, tap close to stop.

Non-goals (v1): a live video thumbnail inside the bar (audio + poster only), mini-player for
Shorts, restoring playback after process death, any TV-build change.

## 2. What the code does today (facts that constrain the design)

- The ExoPlayer engine is **owned by the player fragment**: `PlaybackFragment.createPlayerObjects()`
  / `releasePlayer()` (smarttubetv/src/main). `onDestroy()` calls `releasePlayer()`
  unconditionally. So the engine cannot outlive `MobilePlaybackActivity`; the only way to keep it
  playing is to keep the activity **alive but stopped** with `blockEngine(true)` — exactly what PiP
  and screen-off background audio already do (`maybeReleasePlayer()` no-ops while blocked).
- `PlaybackPresenter` is a process singleton (`getView()`, `isPlaying()`, `isEngineBlocked()`,
  `forceFinish()`, `getVideo()`), usable from any activity. It is the state source for a bar.
- `MediaNotificationService` + MediaSession are created from the fragment
  (`onMediaSessionCreated`) and are driven by the live engine, so they keep working unchanged
  while the player is parked. The notification's content intent already re-opens the player
  (`createActivityIntent`), i.e. "bring the parked player forward" is an existing, working path.
- `ViewManager.startActivity()` always adds `FLAG_ACTIVITY_NEW_TASK`. The phone manifest puts every
  activity in one task (Home `singleTask`, others `singleTop`; player `singleTop`).
- PiP: Back enters PiP only when Background mode = PiP (`BACKGROUND_MODE_PIP`) or already in PiP;
  dismissing the PiP window is detected in `onPictureInPictureModeChanged` and ends playback.
- minSdk is 21 (`stmobile` flavor inherits the project minSdk).

## 3. Architecture options considered

### Option A — player activity parked in the same task (rejected)
Back would need to put Home *above* the parked player in one task
(`FLAG_ACTIVITY_REORDER_TO_FRONT`). Home/Search are `singleTask`, whose clear-top behaviour
likely destroys the activities above it (our player) — unverified and fragile. Even if it worked,
the stack is `[Home, Player, Channel]`: Back on Channel would **reveal the expanded player**, the
same channel↔player loop #23 fixed. Rejected.

### Option B — move the engine into a service (rejected for this wave)
The "proper" architecture, but it means pulling `createPlayerObjects`, the glue and surface
handling out of the shared `PlaybackFragment`. Large, upstream-merge-hostile, and every future
upstream player change would conflict. Rejected.

### Option C — player in its own task, parked with `moveTaskToBack` (RECOMMENDED)
Give `MobilePlaybackActivity` its own `android:taskAffinity` (stmobile manifest only). Because
`ViewManager` already launches with `NEW_TASK`, opening a video from Home puts the player in its
own task with no code change. Minimising = engine blocked + `moveTaskToBack(true)` on the player
task, which reveals the **Home/Search/Channel task untouched underneath**. There is no shared
back-stack, so the loop problem cannot occur and no `ViewManager` re-ordering is needed.
Expanding = bring the player task to the front (the same intent the media notification uses →
`onNewIntent`/`singleTop`). Engine lifetime is the activity's lifetime, same as PiP today.

Costs/unknowns of C (each is a Phase-2 day-one spike, see §10):
1. Dialog/Settings/Channel activities started *from the player* join the player's task (they
   inherit the caller's task unless `singleTask`). `MobileAppDialogActivity` etc. must not be
   `singleTask` with default affinity or they would be pulled into the Home task. To check
   per activity.
2. Recents shows a second card for the player. Default: leave it (swiping it away stops playback,
   as the bar close and the notification also do). Owner call: `excludeFromRecents`?
3. If the player was the *only* thing launched (share-link / deep link, so no Home task exists),
   `moveTaskToBack` would go to the launcher. Handle: if there is no Home activity in
   `ViewManager`'s stack, start Home first (`startView(BrowseView)`), then `moveTaskToBack`.

**Fallback if C fails the spike:** the existing behaviour (Back stops and closes). The feature is
behind a setting and a policy check, so a failed spike ships nothing harmful.

## 4. Components (all `stmobile`, new files unless noted)

| Piece | Where | Role |
|---|---|---|
| `MiniPlayerPolicy` | new, pure Java | `shouldMinimise(...)`: pref on, not Shorts, not in PiP, background mode ≠ PiP, engine initialised, a video is loaded. **JVM unit-tested.** |
| `MiniPlayerBar` | new view + layout | Poster, title, channel, thin progress line, play/pause, close. Reads `PlaybackPresenter`; refresh by a 500 ms handler poll while the host activity is resumed (no new listener plumbing in common). |
| `MiniPlayerHost` | new, attached from `MobileActivity.onPostCreate` | Re-parents the content view into a vertical container `[content, bar]` so the bar takes layout space (no per-screen bottom padding). Opt-in per activity (`hostsMiniPlayer()`: true for Home/Search/Channel/Channel uploads; false for Settings/dialogs/sign-in). Coordinates with `SystemBarInsets` (order checked in the spike). |
| `MobilePlaybackActivity` | edit | `onBackPressed`: if policy says minimise → `minimise()`, else current behaviour. `minimise()`: `blockEngine(true)`, hide overlay, `getViewManager().removeTop(this)` (as PiP does), ensure Home exists, `moveTaskToBack(true)`. |
| `MobilePlaybackFragment` | **small edit only** | A few hooks: `isMinimisable()`, a `mMinimised` flag so the PiP-dismiss logic is not confused; the existing shared `onResume` `blockEngine(false)` already clears the block on expand. No restructuring of the 2.5k-line class; the bar logic lives outside it. |
| Setting | `MobilePlayerStylePrefs` (+ Settings row) | "Mini-player" on/off, default **on**. Off = today's Back behaviour. |
| Manifest | `stmobile/AndroidManifest.xml` | `taskAffinity` on the player activity. |
| `docs/FEATURE_MATRIX.md` | edit | New row; update the Back-behaviour wording. |

**`common/` changes: none planned.** Everything needed (`PlaybackPresenter` accessors,
`ViewManager.removeTop`, `blockEngine`) already exists. Any common/ edit raises upstream-merge
cost; if the spike shows one is unavoidable I will stop and ask rather than make it.

## 5. Behaviour spec

- **Back in player** (portrait or landscape, regular video, Mini-player on, background mode ≠ PiP):
  minimise; playback continues. Background mode = PiP keeps today's PiP-on-Back.
- **Shorts:** Back closes as today (no bar). A Short opened while a bar is up replaces the
  minimised video (new intent, normal path).
- **Bar visible on** Home, Search, Channel, Channel uploads while a video is loaded and the engine
  is alive. Hidden when playback ended with no next, engine released, or setting off.
- **Tap bar / poster / title:** expand (player task to front, controls hidden as on a normal open).
- **Play/pause:** `setPlayWhenReady` through `PlaybackPresenter.getView()`.
- **Close (X):** `blockEngine(false)` + `finishReally()` (the PiP-dismiss pattern), which releases
  the engine and removes the notification via the existing session-null path.
- **Open another video from Home/Search while minimised:** normal open; the player task comes
  forward with the new video (`onNewIntent`, already handled).
- **Back on Home with a video minimised:** unchanged (leaves the app); playback continues under
  the media notification as background audio does today. Reopening the app shows the bar again.
- **Autoplay / queue advance while minimised:** continues; bar title follows `getVideo()`.
- **Screen off while minimised:** same as today's background audio (engine already blocked).
- **Audio focus / notification / MediaSession:** unchanged, engine-driven. Notification play/pause
  controls the same session the bar controls.

## 6. State restore

Rotation: Home/Search are portrait-locked (tablets may rotate); the bar is rebuilt in
`onPostCreate`/`onResume` from presenter state, no saved state. Player rotation untouched.
Activity destroyed by the system while parked → engine released in `onDestroy`; the bar sees
`isEngineInitialized()==false` and hides. Process death → nothing resumes (position is already
saved to history by existing code). No persisted "minimised" flag; it is derived from live state.

## 7. Risks

1. Task-affinity side effects (§3 C items 1–3) — the central risk; spike first.
2. `SystemBarInsets` / immersive-bars interplay with the re-parented content (bar must sit above
   the navigation bar, not under it).
3. Parked activity killed by the OS under memory pressure — mitigated by the foreground
   `MediaNotificationService`; failure mode is the bar disappearing, not a crash.
4. Behaviour change on Back (default on). Mitigated by the setting and a "What's new" note.
5. Parallel tracks edit Home/Search layouts: hosting at the window-content level (not in their
   layouts) keeps merge conflicts near zero. I own structural changes to `MobilePlaybackFragment`
   this wave; v1 deliberately keeps them tiny.
6. Android 5/6 (minSdk 21): the player releases in `onPause` there, but the blocked engine guards
   it; verify on the API 24 AVD, and treat API 21–23 as unverified unless an AVD is available.
   Wherever the policy says no (or the spike fails) the bar never appears and Back behaves
   exactly as today.

## 8. Test plan

Unit (JVM, runs locally): `MiniPlayerPolicyTest` — matrix of pref on/off, Shorts, PiP, background
mode PiP, engine not initialised, no video.
Build: `assembleStmobileDebug` compiles (JDK 17 on PATH, ANDROID_SDK_ROOT set; no `-x strip`).
Device script (numbered, written at the end of Phase 2 for the parent): Back minimises and the
bar shows the right title; audio continues; tap bar expands at the same position; close stops
audio and removes the notification; open another video with a bar up; Channel → Back never
reveals the player; Shorts Back closes; PiP setting still PiPs; screen-off keeps playing;
notification pause/play matches bar state; setting off restores old Back; Recents shows the
expected cards; Android 7 AVD smoke.

## 9. Docs

FEATURE_MATRIX rows (Mini-player; adjust the Back note). Release-note line under "What's new".

## 10. Phase 2 order of work (after approval)

1. Spike (go/no-go for Option C): taskAffinity + minimise + expand; check dialogs/channel opened
   from the player and the Home-missing case. If it fails, report back instead of building more.
2. `MiniPlayerPolicy` + tests, setting.
3. `MiniPlayerBar` + `MiniPlayerHost`; wire Home/Search/Channel/Channel uploads.
4. Back/close/expand wiring, FEATURE_MATRIX, debug build, device script.
   Status: IMPLEMENTED-UNVERIFIED.

## 11. Decisions wanted from the owner

- Approve Option C (own task) as the architecture.
- Default for the Mini-player setting: on (recommended) or off.
- Recents: leave the player's extra card (recommended) or `excludeFromRecents`.
- v1 bar shows poster + title + play/pause + close (no live video). OK?

## 12. Spike result (2026-11-22): Option C fails as designed

Static check of the launch paths (no device used):

- `ViewManager.startActivity()` builds `new Intent(mContext, cls)` with `mContext` = application
  context and always `FLAG_ACTIVITY_NEW_TASK`. A `NEW_TASK` launch is routed by **taskAffinity**,
  not by the caller's task.
- Everything the player opens goes through it: `AppDialogPresenter` -> `startView(AppDialogView)`
  (gear sheet, description, comments, settings dialogs), `ChannelPresenter`, `SearchPresenter`.
- With a distinct affinity on `MobilePlaybackActivity`, all of those would open in the **Home
  task** (default affinity), not the player's. Result: the gear/comment/description dialogs open
  over Home instead of over the video; the player task goes behind, its activity stops (engine
  released unless blocked); Back from a dialog or channel opened from a Short lands on Home, not
  the video. That breaks core player flows, so the affinity approach cannot ship as written.

Ways forward (each needs an owner decision; none attempted):

1. **Small `common/` change**: in `ViewManager.startActivity`, launch from the foreground
   activity (no `NEW_TASK`) when one exists, so child screens inherit the caller's task. Makes
   Option C work, but touches shared code used by every screen (upstream merge cost, wide
   regression surface).
2. **Same task, Option A with a guard**: park the player and bring Home forward with
   `REORDER_TO_FRONT`, and make the parked player re-minimise itself if revealed by Back. Needs a
   device spike; hacky.
3. **Engine in a service (Option B)**: the clean answer, largest cost.
4. Drop the feature, or ship only a bar that works while the player is expanded (no value).
