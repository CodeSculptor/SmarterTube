---
name: upstream-sync
description: >-
  Merge new upstream SmartTube (yuliskov/SmartTube) commits into the SmarterTube phone-port fork,
  build/verify, push to master, and (only when asked) cut a signed release. Use whenever the user
  says an upstream update/release needs merging or syncing, mentions "upstream" alongside "merge"
  or "sync", or asks to cut/ship/release a new fork version. This is the manual path — the repo
  also has a GH Actions bot that auto-merges clean syncs — so start by checking whether the bot
  already handled it before doing anything by hand.
---

# Upstream sync (merge + optional release)

Merge `yuliskov/SmartTube` into this fork's `master`, keeping the phone-port seams intact, then
optionally cut a release. This has been done by hand ~4 times across sessions; this skill is that
recipe, not a discovery process — follow it directly instead of re-deriving each step.

**Read first, don't re-derive:** `memory/rebase_runbook.md` (the phone-port integration points and
past interface drift), `memory/versioning_scheme.md` (version scheme + current shipped state),
`memory/release_signing.md` (`release.ps1` mechanics), `memory/build_environment.md` (JDK/SDK,
never `-x strip`), `memory/git_workflow.md` (push-only-master), `memory/upstream_automation.md`
(what the bot does and why it sometimes doesn't).

**Standing rules that apply throughout:** edit only `smarttubetv/src/stmobile/` without asking;
surface any `common/`/`src/main` (shared TV) edit to the user before making it; stage files by
explicit path, never `git add -A`/`.`; always pass `--repo CodeSculptor/SmarterTube` to `gh`; run
gradle from PowerShell with `$env:ANDROID_SDK_ROOT`/`$env:ANDROID_HOME` set inline in a fresh
worktree; `git submodule update --init --recursive` first in a fresh worktree (no submodules by
default).

---

## 0. Check whether this is even manual work

```
gh pr list --repo CodeSculptor/SmarterTube
gh issue list --repo CodeSculptor/SmarterTube --label upstream-sync
```

If a clean-merge PR is open, it either already auto-merged (nothing to do — just confirm master
moved) or is waiting on `validate` (check CI, don't re-merge by hand). If a conflict issue is open,
that's exactly what this skill resolves — proceed. If neither, the bot's 6h poll just hasn't run
yet or the batch arrived after its last check — proceed manually, no need to wait for it.

## 1. Confirm worktree state, then fetch upstream

```
git status --porcelain                    # must be clean before starting
git fetch origin master --quiet && git rev-parse --short HEAD  # confirm == origin/master
git fetch upstream --tags
git merge-base HEAD upstream/master
git rev-list --count HEAD..upstream/master
git tag --sort=-creatordate | head -5      # latest upstream tag
```

If HEAD isn't at `origin/master`, stop and reconcile first — this flow assumes it fast-forwards.

## 2. Preview conflicts read-only before touching the tree

```
git merge-tree --write-tree HEAD upstream/master
```

Exit 0 = clean merge, no conflicts. Non-zero + `CONFLICT` lines = note which files, then look at
*why* before merging:

```
git diff --stat <merge-base>..upstream/master        # what upstream touched
git diff --stat <merge-base>..HEAD -- common/ smarttubetv/build.gradle smarttubetv/multidex-keep.pro
```

Cross-reference the two lists. Two recurring shapes, both benign so far:

- **`README.md`** — the fork keeps its own; upstream always wants its TV one. Every single sync has
  hit this. Resolution is always `git checkout --ours README.md`.
- **A shared file the fork also touched** (seen so far: `AppDialogUtil.java`, `BrowsePresenter.java`,
  `build.gradle`) — read *both* diffs before resolving. If they touch genuinely different aspects of
  the same block (e.g. fork extracted a helper lambda, upstream improved a checkbox condition inside
  that same lambda), **combine both sides** rather than picking one — don't just take "ours" or
  "theirs" reflexively. If the shared file is one of the 3 integration points in
  `memory/rebase_runbook.md` or a previously-drifted class (`BrowseView`, `SignInView`,
  `ViewManager`, `PlaybackFragment`, `MainApplication`), read the resolved result carefully — that's
  where a bad auto-merge would hide.

A build.gradle "conflict" on the version lines shouldn't happen: the fork's version lives in the
isolated `stmobile` flavor block (`versionCode`/`versionName` near the bottom), upstream only edits
`defaultConfig` (top) — they're non-overlapping regions. If they ever do collide, something
structural changed upstream; stop and look before resolving.

## 3. Merge

```
git merge --no-ff upstream/master -m "Merge upstream SmartTube <ver> (<short-sha>) into phone port"
```

Resolve conflicts per step 2's analysis, `git add <explicit paths>`, then `git commit --no-edit`
(no conflicts) or a plain `git commit` (after manual resolution — the merge commit message is
already staged).

**Verify the version seam survived:**
```
grep -n "versionCode\|versionName" smarttubetv/build.gradle | head -8
```
`defaultConfig` should show upstream's new numbers; the `stmobile` flavor block near the bottom
should be **unchanged** (still the last-shipped version) — that's expected, it gets bumped by the
release step, not the merge.

## 4. Submodules + build

```
git submodule update --init --recursive
```
(PowerShell, inline SDK env, never `-x strip`):
```
$env:ANDROID_SDK_ROOT="C:\Users\steph\AppData\Local\Android\Sdk"; $env:ANDROID_HOME=$env:ANDROID_SDK_ROOT
.\gradlew.bat :smarttubetv:assembleStmobileDebug
```
A green build is the real semantic-drift check — the untouched `stmobile` source compiling clean
against the new upstream interfaces is stronger evidence than reading diffs. If it fails, that's
real interface drift; fix in `stmobile` (or ask the user before touching shared code).

## 5. Device smoke test

Install the debug APK and check Home/search/playback/back-nav on device (`R3GL3069JMZ`; "not
found" = unplugged, ask the user to replug; "unauthorized" = ask them to tap Allow on the phone).
Debug→debug is a plain `install -r` (no wipe). Skip this step **only if the user explicitly says
to skip it** — and if skipped, say so plainly in the final report and in the memory note (don't let
an unverified merge read as verified later).

## 6. Push the merge

```
git push origin HEAD:refs/heads/master
```
Direct push, no PR (this repo's push-only-master model — see `memory/git_workflow.md`). Then
confirm CI:
```
gh run list --repo CodeSculptor/SmarterTube --branch master --limit 3
```
Watch "stmobile validate" to green (`gh run watch <id> --exit-status`) before calling this step
done.

## 7. Optional: cut a release (only when the user asks)

`release.ps1` handles the signed-build/tag/publish mechanics, but it does **not** touch the
versionCode-history comment or `docs/KNOWN_ISSUES.md` — do those first, in a separate commit, from
a clean tree:

1. Add a new `// <code+1> = <next version> (<one-line summary>).` comment line above the current
   `versionCode`/`versionName` pair in the `stmobile` flavor block (`smarttubetv/build.gradle`) —
   don't bump the actual values, `release.ps1` does that.
2. Bump the `Current release:` / `Upstream SmartTube base:` header in `docs/KNOWN_ISSUES.md` to
   match the version you're about to cut. Use the upstream engine version from `defaultConfig`
   (step 3) for the `+st<engine>` suffix and the base — not the tag name if they've drifted apart
   (a merge can land one commit past the latest tag).
3. Commit (`docs(release): prep <version> — KNOWN_ISSUES header + versionCode history`), push to
   master. Tree must be clean before the next step.
4. Run the release from that clean master-tip checkout:
   ```
   $env:ANDROID_SDK_ROOT="C:\Users\steph\AppData\Local\Android\Sdk"; $env:ANDROID_HOME=$env:ANDROID_SDK_ROOT
   .\release.ps1 -VersionName <vX.Y.Z-channel.N+stEE.EE> -Prerelease
   ```
   It bumps the flavor block, builds all 4 ABIs, verifies the signing cert
   (`50fdb412c6e3b683bbd03f9f7a69c40f436b7769810310e30ec96a91259b98a2`), commits, pushes, tags, and
   publishes the GH release — in that order, so a build that doesn't pass never reaches master.
5. Verify the release listing (`gh release view <tag> --json isPrerelease,assets`) and wait for
   "stmobile validate" green on the release commit.
6. **Check the F-Droid publish actually fired and succeeded** — don't assume:
   ```
   gh run list --repo CodeSculptor/SmarterTube --workflow fdroid-publish.yml --limit 3
   ```
   It's `release`-triggered; that trigger silently no-op'd for over a month once before (see
   `memory/distribution_channels.md` for the deployment-branch-policy gotcha that caused it) — a
   fast/empty-looking run or a `failure` conclusion means it didn't publish, not that it did.

## 8. Close the loop

- If this sync included a bug fix or feature (not just the mechanical upstream bump), update
  `docs/FEATURE_MATRIX.md` / `docs/KNOWN_ISSUES.md` for it in the same commit, and file/close a
  GitHub issue per this repo's "issues are the source of truth" convention.
- Update `memory/versioning_scheme.md` (shipped-state entry) and the one-line pointer in
  `memory/MEMORY.md` — this is what lets the *next* session skip re-deriving all of this. State
  honestly what was and wasn't verified (build-only vs on-device).
