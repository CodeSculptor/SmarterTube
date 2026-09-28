# Privacy Policy — SmarterTube

**Package ID:** `com.codesculptor.smartertube`  
**Last updated:** September 2026

---

## What this app is

SmarterTube is a phone and tablet client for YouTube. It is a fork of [SmartTube](https://github.com/yuliskov/SmartTube) by yuliskov; the YouTube client engine is upstream's code, merged unchanged. This policy covers the SmarterTube fork (`com.codesculptor.smartertube`) only — not the upstream SmartTube TV build.

---

## Data the developer collects

**Nothing, unless you opt in to anonymous stats.**

Releases up to v0.8.0-beta.1 don't include this feature and send nothing. Later releases ask you once, on the Home screen, whether to send anonymous stats. You can change your answer any time in **Settings → Anonymous stats**. If you say no, nothing is sent.

If you opt in, the app sends the following to the developer's own server (a Cloudflare Worker whose source is in [`stats-worker/`](stats-worker/)):

| What | When | Contents |
|---|---|---|
| Usage count | At most once a day, when you use the app | App version, Android version, and whether this is your first count of the week / month / ever |
| Crash report | Next time you open the app after a crash | App version, Android version, phone make and model, and the stack trace (code locations only; error messages are removed because they can contain video or search details) |

If you said no and the app crashes, the crash details are kept on your device only, and the next time you open the app it asks whether to send that one report. Nothing is sent unless you tap **Send**; tapping **Don't send** deletes it.

**Never sent:** your Google account, sign-in tokens, watch history, videos, channels, searches, or any identifier for you or your device. There is no install ID, so counts can't be linked to a person or to each other. Like any web request, the server's host (Cloudflare) sees your IP address, but the stats server doesn't read or store it, and request logging is turned off. The aggregate counts are published at the server's `/stats` endpoint so anyone can see exactly what's collected.

There is no other telemetry, analytics, or advertising SDK in the app.

---

## Data the app processes on your device

### Authentication
Sign-in uses Google's standard OAuth 2.0 device-code flow. The authentication token is stored in local device storage only. It is never transmitted to the developer or any third party other than Google/YouTube.

### YouTube / Google
Video content, metadata, search results, channel data, and account information are fetched directly from YouTube and Google servers to your device. This is governed by [Google's Privacy Policy](https://policies.google.com/privacy).

### Community-driven services (opt-in features from upstream)
When enabled, the following features contact third-party APIs with the ID of the video you are watching. No account tokens or personal identifiers are included in these requests.

| Feature | Service | Data sent |
|---|---|---|
| SponsorBlock | [sponsor.ajay.app](https://sponsor.ajay.app) | Video ID |
| Return YouTube Dislike | [returnyoutubedislike.com](https://returnyoutubedislike.com) | Video ID |
| DeArrow | [dearrow.ajay.app](https://dearrow.ajay.app) | Video ID |

These services are independently operated and have their own privacy policies.

### Update check (GitHub)
When the app opens (at most about twice a day), and when you tap **Check for updates** in About, it reads the public list of SmarterTube releases from GitHub's API (`api.github.com`) to see whether a newer version exists and to show the release notes. The request carries no account or personal identifiers; like any web request, GitHub sees your IP address. This is governed by [GitHub's Privacy Statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

---

## Your rights

The optional stats contain no identifier, so nothing held by the developer can be linked back to you to provide, correct, or delete. Turning stats off stops all further sending. For questions about data held by Google/YouTube, refer to your Google account settings.

---

## Contact

Open an issue at [github.com/CodeSculptor/SmarterTube](https://github.com/CodeSculptor/SmarterTube/issues).
