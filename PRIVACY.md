# Privacy Policy — SmarterTube

**Package ID:** `com.codesculptor.smartertube`  
**Last updated:** September 2026

---

## What this app is

SmarterTube is a phone and tablet client for YouTube. It is a fork of [SmartTube](https://github.com/yuliskov/SmartTube) by yuliskov; the YouTube client engine is upstream's code, merged unchanged. This policy covers the SmarterTube fork (`com.codesculptor.smartertube`) only — not the upstream SmartTube TV build.

---

## Data the developer collects

**None.** SmarterTube has no developer-controlled backend, no telemetry, no analytics, and no crash reporting. The developer receives no data about you or your usage.

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

Because the developer collects no data, there is nothing to provide, correct, or delete. For questions about data held by Google/YouTube, refer to your Google account settings.

---

## Contact

Open an issue at [github.com/CodeSculptor/SmarterTube](https://github.com/CodeSculptor/SmarterTube/issues).
