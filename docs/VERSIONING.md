# SmarterTube Versioning

SmarterTube uses its own product version and records the upstream SmartTube base separately.

Do not use the SmartTube upstream version as the SmarterTube product version.

## Version Format

Use this tag format:

```text
v<smartertube-version>+st<smarttube-base>
```

Examples:

```text
v0.4.0-beta.1+st31.93
v0.4.1-beta.2+st31.93
v0.5.0-alpha.1+st31.96
v1.0.0-rc.1+st32.10
v1.0.0+st32.10
```

Meaning:

```text
v0.4.0-beta.1  = SmarterTube product version
st31.93        = upstream SmartTube base
```

The upstream base is metadata. It is not the SmarterTube product version.

## Why This Exists

Previous release names such as:

```text
31.93-mobile-1.4
```

mixed two different concepts:

```text
31.93 = SmartTube upstream base
1.4   = SmarterTube release number
```

That made the app look more stable than it was and made release comparison ambiguous.

SmarterTube should not publish full `1.x` releases until the phone/tablet product has passed the release checklist and feature audit.

## Product Maturity

### 0.x

Use `0.x` while the app is still beta/alpha quality.

Examples:

```text
v0.4.0-beta.1+st31.93
v0.4.2-beta.3+st31.93
v0.5.0-alpha.1+st31.96
```

### 1.x

Use `1.x` only when SmarterTube is considered product-stable.

A `1.0.0` release requires:

```text
[ ] Core flows audited
[ ] Feature matrix has no unknowns for core flows
[ ] Release checklist passes
[ ] Update checking supports the final naming scheme
[ ] Known issues are documented
[ ] No major portrait/tablet layout failures are known
```

## Semantic Versioning Rules

Use the normal shape:

```text
MAJOR.MINOR.PATCH
```

### MAJOR

Increment for major product-level changes or breaking changes.

Before `1.0.0`, breaking changes may still happen in `0.x`, but they must be documented.

### MINOR

Increment for user-visible feature additions or substantial UI work.

Examples:

```text
v0.5.0-beta.1+st31.93
v0.6.0-beta.1+st32.00
```

Use a minor bump for:

- new native phone/tablet screens
- major layout rewrites
- new user-facing functionality
- large upstream base changes that affect behaviour

### PATCH

Increment for bug fixes and small improvements.

Examples:

```text
v0.4.1-beta.1+st31.93
v0.4.2-beta.1+st31.93
```

Use a patch bump for:

- broken button fixes
- small UI corrections
- crash fixes
- updater fixes
- release metadata corrections

## Prerelease Labels

Use prerelease labels to describe release channel.

```text
-alpha.N
-beta.N
-rc.N
```

Examples:

```text
v0.5.0-alpha.1+st31.96
v0.4.0-beta.1+st31.93
v1.0.0-rc.1+st32.10
```

### Alpha

Use for experimental or risky work.

```text
v0.5.0-alpha.1+st31.96
```

### Beta

Use for public testing builds.

```text
v0.4.0-beta.1+st31.93
```

### Release Candidate

Use only when aiming for stable.

```text
v1.0.0-rc.1+st32.10
```

### Stable

Stable releases have no prerelease label.

### GitHub release flag

The channel is carried by the **tag name**, not by GitHub's "pre-release" checkbox. Publish
**beta, rc and stable** releases as normal GitHub releases (not flagged pre-release) so the newest
build is shown as **Latest** and is picked up by Obtainium's default settings. Only **alpha**
builds are flagged pre-release. The in-app updater (`MobileUpdateChecker`) trusts the parsed tag
channel over GitHub's flag, so the flag never changes which update a user is offered.

(Policy since 2026-09-25: flagging every beta as pre-release had left `v0.4.2-beta.8` — under the
retired package id — as "Latest" for three months.)

```text
v1.0.0+st32.10
```

## Upstream Base Metadata

Always include the upstream SmartTube base after `+st`.

Examples:

```text
+st31.73
+st31.93
+st32.10
```

If the upstream base is a beta/head build rather than a stable SmartTube release, make that clear in release notes.

Example:

```text
v0.4.3-beta.1+st31.94-beta
```

Only use upstream beta bases for:

- alpha releases
- emergency beta hotfixes
- private/internal test builds

## Channel Visibility Rules

The updater and release notes should apply these rules.

```text
Stable channel:
  sees stable releases only

Beta channel:
  sees beta, release candidate, and stable releases

Alpha channel:
  sees alpha, beta, release candidate, and stable releases
```

Recommended order from least to most stable:

```text
alpha < beta < rc < stable
```

## Migration From Old Names

Old release tags may exist, for example:

```text
31.77-mobile-beta1
31.88-mobile-1.0
31.90-mobile-1.1
31.93-mobile-1.4
```

These should be treated as legacy SmarterTube releases.

Recommended migration release:

```text
v0.4.0-beta.1+st31.93
```

Release notes should say:

```text
Previous 31.xx-mobile-1.x releases were incorrectly marked as full releases. SmarterTube is being reclassified as beta until the phone/tablet feature matrix, orientation handling, and core workflows are verified.
```

## Version Comparison Rules

Do not compare complete tag strings directly.

Wrong:

```text
31.93-mobile-1.4 > v0.4.0-beta.1+st31.93
```

Wrong:

```text
v0.4.0-beta.1+st31.94 > v0.4.1-beta.1+st31.93
```

Correct comparison inputs:

```text
SmarterTube version: 0.4.1-beta.1
Channel: beta
Upstream base: 31.93
```

The updater should compare SmarterTube product versions first.

The upstream SmartTube base should be displayed to the user and used for compatibility/debugging, but it should not override the SmarterTube product version.

## Asset Naming

APK assets should include enough information for users and the updater.

Recommended pattern:

```text
SmarterTube-v0.4.0-beta.1-st31.93-universal.apk
SmarterTube-v0.4.0-beta.1-st31.93-arm64-v8a.apk
SmarterTube-v0.4.0-beta.1-st31.93-armeabi-v7a.apk
SmarterTube-v0.4.0-beta.1-st31.93-x86.apk
```

Avoid names that only contain the upstream SmartTube version.

## Release Metadata

Each release should include machine-readable metadata if practical.

This may be in the release body or as a JSON asset.

Example:

```json
{
  "app": "SmarterTube",
  "version": "0.4.0-beta.1",
  "upstream": "31.93",
  "channel": "beta",
  "min_android": 26
}
```

## Examples

### Beta Reset

```text
Tag:   v0.4.0-beta.1+st31.93
Title: SmarterTube v0.4.0-beta.1, based on SmartTube 31.93
```

Purpose:

```text
Reclassify SmarterTube as beta and introduce the new release/versioning process.
```

### Button Audit Fix Release

```text
Tag:   v0.4.1-beta.1+st31.93
Title: SmarterTube v0.4.1-beta.1, based on SmartTube 31.93
```

Purpose:

```text
Audit and fix visible controls in the phone/tablet UI.
```

### Upstream Stable Sync

```text
Tag:   v0.4.2-beta.1+st31.73
Title: SmarterTube v0.4.2-beta.1, based on SmartTube stable 31.73
```

Purpose:

```text
Move public beta releases back onto the upstream SmartTube stable track.
```

### 1.0 Release Candidate

```text
Tag:   v1.0.0-rc.1+st32.10
Title: SmarterTube v1.0.0-rc.1, based on SmartTube 32.10
```

Purpose:

```text
Candidate build for the first stable SmarterTube release.
```
