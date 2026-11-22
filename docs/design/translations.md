# Phone UI translations — audit and decision

Status: **audit + string-resource move done (IMPLEMENTED-UNVERIFIED); translation workflow awaiting owner decision.**
No locale folders are shipped and no service account has been created.

## Why

Users on a phone read the phone UI (sign-in, About, Settings rows, player sheets, Shorts bar,
notifications) in their own language. Today the `stmobile` flavor has English only, so a
device set to German shows a mix: upstream screens translated, phone screens English.

## Step 1 — audit (done)

Scope: `smarttubetv/src/stmobile` (res layouts, `values/strings.xml`, Java).

| Finding | Action |
| --- | --- |
| ~125 strings already in `stmobile/res/values/strings.xml` | unchanged; URLs / app name already `translatable="false"` |
| `fragment_playback.xml` (Shorts bottom bar, "You" sheet, Save-to-playlist sheet, Like/Dislike/Back descriptions) had ~15 hardcoded English texts | moved to resources |
| Java: "Couldn't update notification preference" (x2), "Account" fallback name | moved to resources |
| `UploadNotifier`: `"%1$d new video(s)…"` | converted to a proper `<plurals>` (needed by languages with several plural forms) |
| Symbol-only labels (←, ✕, ›, ☰, emoji) | left as is: glyphs, not language. Their buttons' accessibility labels are existing strings where present |
| `lb_playback_transport_controls_row.xml` "Hello World!" | copy of an upstream sizing placeholder, never shown; lint-suppressed, not translated |
| Java string literals in logs, user-agent, file names, JSON keys | not user-visible; untouched |

**Reuse instead of new strings:** the bottom bar and sheets now point at strings that
`common/` already ships translated in ~48 locales (`header_home`, `header_shorts`,
`header_subscriptions`, `header_playlists`, `header_history`, `header_settings`, `action_like`,
`action_dislike`). Those eight labels are therefore translated on day one at zero cost, and
no `common/` file was changed. Five genuinely new strings were added to the stmobile file
(`mobile_back`, `mobile_nav_you`, `mobile_account_default`, `mobile_my_channel`,
`mobile_no_playlists`), plus `mobile_notifications_update_failed` and the uploads plural.

## Step 2 — how upstream does locales

- `common/src/main/res/values/strings.xml` (755 strings) is the single source;
  `values-xx/strings.xml` for ~48 locales sit beside it.
- `crowdin.yml` lists exactly one file: source `/common/src/main/res/values/strings.xml`,
  translation `/common/src/main/res/values-%android_code%/strings.xml`. Upstream translators
  work in Crowdin; translated files arrive in git through upstream.
- Android merges resources across modules/flavors by name and by qualifier, so
  `smarttubetv/src/stmobile/res/values-de/strings.xml` works with no code change, and a
  stmobile string never collides with an upstream name (all are prefixed `mobile_`, except
  `media_notification_*`, `notification_bell`, `channel_avatar`).

Can stmobile strings ride upstream's pipeline? **No.** Upstream's Crowdin project belongs to
upstream, so adding a file to our `crowdin.yml` has no effect there, and sending phone-only
strings upstream would be wrong (they're not used on TV) and would couple us to their merge
timing. The pipeline's *conventions* (Android XML, `%android_code%` mapping) can be copied.

## Step 3 — options for the decision

All need a second source file: `/smarttubetv/src/stmobile/res/values/strings.xml` →
`/smarttubetv/src/stmobile/res/values-%android_code%/strings.xml`.

| | A. Own Crowdin project (extend `crowdin.yml`) | B. Weblate (hosted, free for libre projects) | C. Community PRs only |
| --- | --- | --- | --- |
| Contributor effort | Web editor, no git | Web editor, no git | Edit XML + open a PR |
| Fits the repo | Same file format and mapping as upstream; `crowdin.yml` gets a 2nd entry | Native git integration, can commit to a branch; needs `weblate` component config | No tooling |
| Maintenance | Sync via Crowdin GitHub integration or CLI; account + project to run | Same, hosted weblate pushes PRs | PR review only |
| Risk | Account/project setup; free tier for open-source needs an application | Same | Few contributors will do raw XML; stale translations drift |
| Merge-conflict cost | Low: files live only in stmobile | Low | Low |

Recommendation: **A or B behind PRs from the platform** (never direct pushes to `master`);
**C as the zero-setup start** — the file layout is identical, so we can begin with C and
move to a platform later without touching code. Pick A if familiarity with upstream's tooling
matters, B if a libre-friendly host matters more. Either way the Telegram group is the
natural place to find translators.

Practical rules for any option: source file is English, plurals use `<plurals>`, anything
not for translation stays `translatable="false"`, fallback is English for missing keys
(Android does this automatically), and upstream locale folders are never edited by us.

## Decision needed from the owner

1. Which of A / B / C?
2. Approve creating the account/project (this session did not sign up anywhere).
3. Ship `values-xx` folders now? (Not shipped. The 8 reused labels are already translated via `common/`.)
