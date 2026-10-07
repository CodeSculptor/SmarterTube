# SmarterTube Release Process

SmarterTube is currently treated as a beta-quality phone/tablet fork of SmartTube. Releases must be managed as product releases for SmarterTube, not merely as snapshots of upstream SmartTube.

The goal of this process is to keep releases understandable, testable, and reversible.

## Principles

1. A release should have one primary purpose.
2. Upstream syncs, native UI rewrites, bug fixes, and new features should not be mixed unless there is a clear reason.
3. Release quality is determined by the SmarterTube feature matrix and release checklist, not by whether the app builds.
4. SmartTube upstream beta builds should not normally be used as the base for public SmarterTube beta or stable releases.
5. Claude Code or any other coding agent may assist, but it must not decide release scope, version numbers, or release readiness.

## Release Channels

### Alpha

Alpha releases are for experimental work.

Use alpha releases for:

- risky upstream rebases
- major native UI rewrites
- layout experiments
- new features that have not been fully tested
- upstream SmartTube beta/head tracking

Example:

```text
v0.5.0-alpha.1+st31.96
```

### Beta

Beta releases are for public testing.

A beta release may contain known issues, but those issues must be documented.

Use beta releases when:

- core flows mostly work
- known issues are listed
- release notes are clear
- updater compatibility has been checked
- the build is intended for ordinary testers

Example:

```text
v0.4.1-beta.2+st31.93
```

### Stable

Stable releases should only be used when the phone/tablet product has passed the release checklist.

Do not publish a stable release while there are unknowns in the feature matrix for core user flows.

Example:

```text
v1.0.0+st32.10
```

## Branch Model

Use a branch structure that separates upstream syncs from product work.

```text
main
  Releasable branch. No direct experimental work.

develop
  Integration branch for SmarterTube phone/tablet work.

upstream-sync/st31.94
  One branch per upstream SmartTube merge.

feature/native-channel-tabs
feature/player-menu-audit
feature/tablet-layout-pass
fix/portrait-toolbar-overlap
```

## Release Flow

### 1. Upstream Sync

Use a dedicated branch.

```text
upstream-sync/st31.94
```

This branch should only contain the upstream merge and conflict resolution.

Do not include unrelated SmarterTube UI changes in an upstream sync branch.

Required output from the agent or developer:

```text
- Upstream source/tag/commit merged
- Conflicts encountered
- Files changed under shared/core modules
- Files changed under SmarterTube mobile UI modules
- Submodule changes
- Manual smoke tests required
```

### 2. Feature or Fix Work

Use a focused issue and branch.

```text
feature/player-menu-audit
fix/portrait-toolbar-overlap
```

Each branch should map to one issue where practical.

Required output:

```text
- Files changed
- Behaviour changed
- Test steps
- Risks
- Checklist rows affected
```

### 3. Integration

Merge focused branches into `develop` only after they build and have basic manual test notes.

Avoid merging multiple risky changes at once.

### 4. Release Candidate Branch

Create a release branch from `develop`.

```text
release/v0.4.1-beta.1
```

Only release-blocking fixes should go into this branch.

### 5. Final Checks

Before tagging:

```text
[ ] Version number follows VERSIONING.md
[ ] Upstream base is documented
[ ] Check for updates still works
[ ] APK assets are named consistently
[ ] ABI/universal APK selection works
[ ] Feature matrix updated
[ ] Known issues updated
[ ] Release checklist completed
[ ] Release notes drafted
```

### 6. Tag and Release

Tag format:

```text
v<smartertube-version>+st<smarttube-base>
```

Example:

```text
v0.4.1-beta.1+st31.93
```

Release title example:

```text
SmarterTube v0.4.1-beta.1, based on SmartTube 31.93
```

Release notes must include:

```text
- SmarterTube version
- SmartTube upstream base
- Release channel
- Main purpose of release
- User-visible changes
- Known issues
- Update compatibility notes
- APK asset guidance
```

## One Primary Change Rule

A release should include one primary kind of change:

```text
- upstream sync
- bug-fix batch
- UI feature work
- layout/orientation pass
- release-process change
```

If a release contains more than one primary change type, the release notes must explain why.

## Upstream Tracking Policy

Use two upstream tracks.

```text
upstream-stable-track
  Tracks SmartTube stable releases.
  Used for SmarterTube beta and stable releases by default.

upstream-beta-track
  Tracks SmartTube beta/head.
  Used only for SmarterTube alpha releases or emergency beta hotfixes.
```

Default rule:

```text
Stable SmarterTube <= SmartTube stable
Beta SmarterTube   <= SmartTube stable by default
Alpha SmarterTube  may track SmartTube beta/head
```

Emergency exception:

```text
If YouTube breaks a core feature and upstream SmartTube beta fixes it,
a SmarterTube beta may temporarily use an upstream beta base.

The release notes must clearly say this.
```

Example wording:

```text
Emergency upstream beta base: st31.94-beta because it fixes playback failures caused by recent YouTube changes.
```

## Release Gates

### Gate A — Beta Reset

Purpose: make public status honest and understandable.

Target release:

```text
v0.4.0-beta.1+st31.93
```

Required:

```text
[ ] README says Beta, not Stable
[ ] Previous 31.xx-mobile-1.x releases are described as superseded/misclassified
[ ] VERSIONING.md added
[ ] RELEASE_PROCESS.md added
[ ] UPDATER_COMPATIBILITY.md added
[ ] Feature matrix added or started
[ ] Known issues added or updated
[ ] Check for updates compatibility reviewed
```

### Gate B — Feature and Button Audit

Purpose: classify every visible feature and button.

Target release:

```text
v0.4.1-beta.1+st31.93
```

Required:

```text
[ ] Home flow audited
[ ] Search flow audited
[ ] Player controls audited
[ ] Channel pages audited
[ ] Settings audited
[ ] Account/sign-in flow audited
[ ] Broken buttons either fixed or documented
```

### Gate C — Orientation and Layout Audit

Purpose: remove inappropriate TV/landscape UI assumptions from portrait/tablet layouts.

Target release:

```text
v0.4.2-beta.1+st<stable-base>
```

Required:

```text
[ ] Phone portrait checked
[ ] Phone landscape checked
[ ] Small tablet portrait checked
[ ] Small tablet landscape checked
[ ] Large tablet portrait checked
[ ] Large tablet landscape checked
[ ] No landscape-only controls appear in portrait without adaptation
```

### Gate D — Upstream Sync Discipline

Purpose: prove that upstream merging is repeatable.

Required:

```text
[ ] Upstream stable merge performed in dedicated branch
[ ] Conflicts documented
[ ] SmarterTube-specific changes preserved
[ ] Check for updates still works
[ ] Smoke tests completed
```

### Gate E — 1.0 Release Candidate

Do not start 1.0 release candidates until:

```text
[ ] Feature matrix has no unknowns for core flows
[ ] Core flows are Works, Partially works, Not implemented, or Not applicable
[ ] No core flow is merely untested
[ ] Known broken items are documented
[ ] At least one beta survives ordinary use without emergency hotfix
[ ] Update checking works with the final naming scheme
```

Example sequence:

```text
v1.0.0-rc.1+st32.10
v1.0.0-rc.2+st32.10
v1.0.0+st32.10
```

## Release Checklist

Use this before every public beta/stable release.

### Install and Upgrade

```text
[ ] Fresh install works
[ ] Upgrade from previous SmarterTube release works
[ ] App ID is correct
[ ] App can coexist with upstream SmartTube TV build if intended
[ ] ABI APKs install correctly
[ ] Universal APK installs correctly
```

### Update Checking

```text
[ ] Check for updates opens without crashing
[ ] Current release is not incorrectly detected as outdated
[ ] Newer compatible release is detected
[ ] Older release is ignored
[ ] Incompatible upstream SmartTube TV release is ignored
[ ] Correct APK asset is selected
[ ] Release notes link opens correctly
```

### Signed-Out Browsing

```text
[ ] Home opens
[ ] Search opens
[ ] Search suggestions work if implemented
[ ] Video playback works
[ ] Shorts playback works
[ ] Channel page opens
[ ] Channel uploads open
[ ] Settings opens
[ ] About opens
```

### Signed-In Browsing

```text
[ ] Sign-in works
[ ] Sign-out works
[ ] Account switcher works if implemented
[ ] Subscriptions open
[ ] History opens
[ ] Playlists open
[ ] Notifications open, or show a sane unavailable state
[ ] Subscribe/unsubscribe works if implemented
[ ] Notification bell works if implemented
```

### Player

```text
[ ] Play/pause
[ ] Seek
[ ] Back behaviour
[ ] Resume behaviour
[ ] Fullscreen/orientation handling
[ ] Quality menu
[ ] Captions
[ ] Playback speed
[ ] SponsorBlock controls
[ ] Return YouTube Dislike display where supported
[ ] DeArrow behaviour where supported
```

### Layout and Orientation

```text
[ ] Phone portrait
[ ] Phone landscape
[ ] Small tablet portrait
[ ] Small tablet landscape
[ ] Large tablet portrait
[ ] Large tablet landscape
[ ] No TV-only layout is reused where it is impractical
[ ] Important controls are reachable by touch
[ ] Important text is readable
```

### Regression and Release Notes

```text
[ ] Upstream base documented
[ ] Release channel documented
[ ] Main purpose of release documented
[ ] Known issues updated
[ ] Feature matrix updated
[ ] Screenshots updated if UI changed significantly
[ ] APK names match expected updater rules
```

## Claude Code Operating Rules

Use prompts like this for normal issues:

```text
You are working on issue #X only.
Do not modify unrelated files.
Do not change version numbers.
Do not create a release.
Do not merge upstream.

Before coding, list the files you expect to touch.

After coding, provide:
- files changed
- behaviour changed
- test steps
- risks
- release checklist rows affected
```

Use prompts like this for upstream syncs:

```text
This task is upstream sync only.
Merge SmartTube tag/commit X into branch upstream-sync/stX.
Do not alter SmarterTube UI except to resolve conflicts.
Do not change release versioning.
Do not create a GitHub release.

After merging, list:
- conflicts
- files changed under shared/core modules
- files changed under SmarterTube mobile UI modules
- submodule changes
- manual tests required
```

Use prompts like this for update-checking work:

```text
Before changing release tag naming, find the existing update-checking implementation and document exactly how it discovers, parses, compares, and downloads releases.

Then update it to support the rules in docs/UPDATER_COMPATIBILITY.md.

Do not change release naming until updater tests pass.
```

## Agent Change Discipline

Any coding agent (including Claude Code) working in this repo must follow these rules. They are
mandatory, not advisory.

```text
- One issue per task. A change addresses a single issue and nothing else.
- No unrelated file changes. Do not "drive-by" reformat, rename, or refactor.
- No upstream sync mixed with feature/fix work. Upstream merges happen on their own
  upstream-sync/* branch and contain only the merge + conflict resolution.
- No release or version bump unless the issue explicitly asks for it.
- Every change updates the affected rows of docs/RELEASE_CHECKLIST.md and
  docs/FEATURE_MATRIX.md (and docs/KNOWN_ISSUES.md if behaviour is knowingly incomplete).
- Do not edit the SharedModules or MediaServiceCore submodules. They track upstream and must
  stay clean for mergeability. Fork-owned phone code lives in the stmobile flavor source sets.
  MediaServiceCore is pinned to upstream yuliskov/MediaServiceCore again (the fork pin was
  removed once upstream shipped the legacy-first fix, see docs/upstream-merge.md).
```

Every PR / change summary must include:

```text
- Files changed
- Behaviour changed (before -> after)
- Tests run (and their result)
- Risks / unresolved questions
- Checklist / feature-matrix rows affected
```

## Planned Release Roadmap

The generic Gates A–E above describe categories of work. The concrete near-term sequence is:

```text
Gate A  v0.4.0-beta.1+st31.93   Beta reset: honest status + versioning/updater foundation.
                                README says Beta, old 1.x explained as superseded, feature
                                matrix + release checklist + known issues added, updater made
                                scheme-aware. No new features. (THIS task.)

Gate B  v0.4.1-beta.1+st31.93   Reworked TikTok-style Shorts player (branch
                                inspiring-robinson-001d00), released under the new scheme.

Gate C  v0.4.2-beta.1+st31.xx   Upstream-stable sync discipline — only after proving the merge
                                path works from a stable SmartTube release.
```

Each ships as its own release with its own checklist run; work for one gate does not leak into
another.
