# SmarterTube

**A phone and tablet YouTube client for Android.**

SmarterTube is **not a patched YouTube app and not a wrapper** — it is a native Android phone/tablet UI built on SmartTube's existing YouTube client engine.

It is a fork of [SmartTube](https://github.com/yuliskov/SmartTube) by yuliskov. It adds a native portrait UI — drawer navigation, search, channel pages, settings, sign-in — on top of SmartTube's YouTube client engine, which is merged from upstream unchanged on every release. Upstream SmartTube is TV-only by design; this fork exists for phones and tablets.

> **Beta.** A native phone & tablet UI — Home, Search, Channel, Channel Uploads, Settings, sign-in, and playback — built on SmartTube's YouTube engine. Core flows work; some upstream features aren't surfaced in the phone UI yet, and landscape/tablet layouts are still being audited. Unlike app-modifiers, these are real native Android screens, not a repackaged YouTube APK.
>
> Earlier `31.xx-mobile-1.x` builds were published as full `1.x` releases too early. SmarterTube is now reset to a beta line with a clearer version scheme — see [Versioning & releases](#versioning--releases).

<p align="center">
  <img src="images/phone_browse.png" width="260" alt="Browsing music charts and trending videos"/>
  &nbsp;&nbsp;&nbsp;
  <img src="images/phone_search.png" width="260" alt="Search with voice button and results grid"/>
  &nbsp;&nbsp;&nbsp;
  <img src="images/phone_drawer.png" width="260" alt="Navigation drawer"/>
</p>

<p align="center">
  <img src="images/phone_player.png" width="640" alt="Video player with SponsorBlock, Return YouTube Dislike, and up to 1080/60"/>
</p>

---

## Relationship to SmartTube

SmarterTube is an **unofficial fork** of [yuliskov/SmartTube](https://github.com/yuliskov/SmartTube). The playback/client engine, ad blocking, SponsorBlock, Return YouTube Dislike and DeArrow integration — all the under-the-hood behaviour — come from upstream SmartTube, unchanged. This fork's job is to provide a native phone/tablet interface while keeping the upstream structure intact, so non-UI updates can be merged in regularly.

---

## Versioning & releases

SmarterTube is currently **beta**. It uses its own product version and records the upstream SmartTube base separately:

```text
v0.4.0-beta.1+st31.93
   |              |
   |              +-- upstream SmartTube base (metadata only)
   +-- SmarterTube product version (semver + release channel)
```

- The SmarterTube app version (`v0.4.0-beta.1`) is tracked **separately** from the upstream SmartTube base (`st31.93`).
- Earlier `31.xx-mobile-1.x` releases mixed those two numbers and were marked as full `1.x` releases prematurely. They are **superseded** by this beta reset and are treated as legacy by the updater.
- Public **beta/stable** releases normally track **SmartTube stable**. Upstream SmartTube **beta/head** is used only for SmarterTube **alpha** builds or emergency YouTube-breaking fixes (called out in the release notes).

Full policy: [docs/VERSIONING.md](docs/VERSIONING.md) · [docs/RELEASE_PROCESS.md](docs/RELEASE_PROCESS.md) · [docs/UPDATER_COMPATIBILITY.md](docs/UPDATER_COMPATIBILITY.md). Per-release status lives in [docs/FEATURE_MATRIX.md](docs/FEATURE_MATRIX.md) and [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md).

---

## Why this exists

Upstream SmartTube is built for Android TV — a leanback, 10-foot, D-pad interface. Phones and tablets need touch-native navigation instead. SmarterTube adds that UI while preserving upstream compatibility, so engine and feature updates keep flowing in from SmartTube.

| Project | Approach |
|---|---|
| SmartTube (upstream) | Android TV / leanback (10-foot) UI |
| SmarterTube (this fork) | Native phone/tablet touch UI on SmartTube's engine |
| App-patching tools | Patch or modify the official YouTube app itself |

---

## Download

[**GitHub Releases →**](https://github.com/CodeSculptor/SmarterTube/releases)

Pick the APK for your device:

| ABI | Who needs it |
|---|---|
| `arm64-v8a` | Most Android phones made after 2016 |
| `armeabi-v7a` | Older 32-bit devices |
| `x86` | Emulators |
| `universal` | Everything — larger file |

SmarterTube installs as `com.codesculptor.smartertube` and is **co-installable** with the upstream SmartTube TV build (`app.smarttube`). They do not conflict.

> **Upgrading from a build before this rename?** Versions up to `v0.4.2-beta.8` shipped under the old package `app.smarttube.mobile`. The new id is a separate app, so it installs *alongside* the old one rather than upgrading it — uninstall the old SmarterTube after installing this one. Settings and signed-in accounts do not carry over and need to be set up again (one-time).

### Auto-updates via Obtainium

SmarterTube is not on any app store. [Obtainium](https://github.com/ImranR98/Obtainium) installs and auto-updates apps straight from their GitHub Releases — no store and no central repository involved:

1. Install Obtainium (itself sideloaded from its own GitHub Releases).
2. **Add App** → paste `https://github.com/CodeSculptor/SmarterTube`.
3. Obtainium tracks each new release automatically; choose the `arm64-v8a` asset (or `universal`) when prompted.

This is the easiest way to stay current.

### Install via F-Droid (self-hosted repo)

SmarterTube runs its **own** F-Droid repository — the same APKs as GitHub Releases, signed with the same key, so installs upgrade in place. It is a self-hosted repo (not the official F-Droid index): you add it once in the F-Droid client and get auto-updates.

1. Install [F-Droid](https://f-droid.org/) (or a compatible client such as Droid-ify).
2. **Settings → Repositories → Add** this URL (fingerprint included so the client verifies it):

   ```
   https://codesculptor.github.io/SmarterTube/fdroid/repo?fingerprint=C7AE86B0A3291B5D7396411BE185C60B298C9D25012172DDFC859B25B540A46B
   ```

3. Refresh, search for **SmarterTube**, and install. Updates then arrive through F-Droid automatically.

> The repo serves SmarterTube's own builds only; it is not affiliated with the official F-Droid repository or with upstream SmartTube.

Official builds are published only on this GitHub Releases page (and the SmarterTube F-Droid repo above) unless another source is explicitly linked here.

### Verifying your download

Release APKs are named `SmarterTube-<version>-st<base>-<arch>.apk` (e.g. `SmarterTube-v0.4.0-beta.1-st31.93-arm64-v8a.apk`).

Every APK on the Releases page carries a **SHA-256 digest**, shown by GitHub next to the asset. After downloading, compare it against the file on your device:

```bash
# Linux/macOS
sha256sum SmarterTube-*.apk
# Windows (PowerShell)
Get-FileHash SmarterTube-*.apk -Algorithm SHA256
```

If the hash matches the one GitHub shows for that asset, the file is intact.

---

## What works

### Phone UI (this fork adds)
- Portrait home screen with drawer navigation (Home, Shorts, Kids, Sports, LIVE, Gaming, News, Music, Channels, Subscriptions, History, Playlists, My videos)
- Search with suggestions and results grid, plus voice search
- Channel pages and channel uploads
- Portrait settings screen
- Sign in / sign out — OAuth device-code flow via in-app browser tab. Switch between multiple accounts with one tap on the toolbar avatar (long-press for account management), or from Settings
- About screen (drawer footer)
- Playback: landscape for regular videos, portrait for Shorts (swipe up/down for the next one)
- Player styles: **Modern** phone-style controls, **Tap to pause**, or the **Classic** SmartTube controls (Settings > Player style)
- Landscape swipe gestures: swipe up/down on the right for volume, on the left for brightness
- Portrait player with up-next list and in-panel comments (read-only)
- Watched-progress bars on thumbnails, plus video length badges
- New-upload notifications from your subscriptions (opt-in; you're asked once after signing in)
- Update notice when a new release is out, and a short "What's new" after you update

### From upstream SmartTube (YouTube client engine, unchanged)
- SponsorBlock integration
- Return YouTube Dislike
- DeArrow
- Adjustable playback speed
- Up to 8K / 60fps / HDR
- No Google Play Services required
- No ads

---

## Known limitations & risks

SmarterTube is a **beta** release. A few realities are worth knowing before you install:

- **Not all upstream features are surfaced yet** — the phone UI covers the core journey (Home, Search, Channel, Settings, sign-in, playback); some upstream options remain reachable only through the settings screens, and a few aren't wired into the phone UI at all.
- **Upstream / YouTube breakage** — YouTube changes its private APIs without warning, which can break playback at any time. Fixes depend on upstream SmartTube's cadence, then a re-merge here.
- **Sideload only** — not on any app store. Install the APK yourself from Releases, or use [Obtainium](#auto-updates-via-obtainium) to install and auto-update directly from GitHub.
- **No guarantees** — this is an independent fork with no affiliation to Google/YouTube or to upstream SmartTube's author.

Specific gaps:

- **TV / leanback interface** — install [upstream SmartTube](https://github.com/yuliskov/SmartTube) for Android TV boxes and sticks.
- **Official F-Droid / IzzyOnDroid index** — not listed there; instead use GitHub Releases, [Obtainium](#auto-updates-via-obtainium), or SmarterTube's own [F-Droid repo](#install-via-f-droid-self-hosted-repo).
- **Casting to a Chromecast** — not supported, same as upstream SmartTube: sending to a Chromecast needs Google's Cast SDK, which requires Google Play Services, and SmartTube is deliberately built without them. The reverse works: under Settings > Remote control, the YouTube app on another device can link to SmarterTube and control playback.

---

## Community & support

- Telegram: https://t.me/SmarterTubeApp
- Discord: https://discord.gg/kVCkEWvEjt
- Bug reports: https://github.com/CodeSculptor/SmarterTube/issues

---

## Building

Requires JDK 17 and Android SDK.

```bash
# Debug
./gradlew :smarttubetv:assembleStmobileDebug

# Release (needs keystore.properties + smartertube-release.jks at repo root)
./gradlew :smarttubetv:assembleStmobileRelease
```

Output APKs land in `smarttubetv/build/outputs/apk/stmobile/`.

All phone-specific code lives under `smarttubetv/src/stmobile/` — no changes to `src/main` (TV code) except bug fixes that benefit both targets, which are submitted upstream.

---

## Upstream & maintenance

The YouTube client engine (MediaServiceCore, ExoPlayer, InnerTube API code) is upstream's work and is merged from [yuliskov/SmartTube](https://github.com/yuliskov/SmartTube) on every release. Bug fixes that apply to both the TV and phone targets are submitted upstream rather than kept here — see open PRs for current patches. (Code layout is described under [Building](#building).)

Licensed under [MIT](LICENSE), same as upstream.

---

## Privacy

See [PRIVACY.md](PRIVACY.md).
