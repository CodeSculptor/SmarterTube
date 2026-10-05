# SmarterTube self-hosted F-Droid repository

This directory is the source for SmarterTube's own F-Droid repository. It serves the
**exact APKs published on GitHub Releases**, signed with the SmarterTube release key,
so an F-Droid install upgrades in place over a GitHub/Obtainium install (same cert).

The repo is published to GitHub Pages at
`https://codesculptor.github.io/SmarterTube/fdroid/repo` by
[`.github/workflows/fdroid-publish.yml`](../.github/workflows/fdroid-publish.yml),
which runs automatically when a GitHub Release is published (and on manual dispatch).

## Two keys — do not confuse them

| Key | File | Used for | Where it lives |
|---|---|---|---|
| **APK signing key** | `smartertube-release.jks` (cert `50fdb412…`) | signs the APKs | local + off-repo backup only — **never** in CI |
| **Repo-index key** | `fdroid/keystore.p12` | signs only the repo *index* | GitHub Actions secret + off-repo backup |

F-Droid does **not** re-sign the APKs — it serves ours as-is. The repo-index key only
signs the index; its SHA-256 fingerprint is part of the URL users add. If it is lost,
the fingerprint changes and every user must re-add the repo.

## One-time bootstrap (run once, locally)

1. Install the tools: `pip install fdroidserver` (or `apt-get install fdroidserver`).
2. From the repo root: `cd fdroid && fdroid init`. This generates `keystore.p12` and a
   fresh `config.yml` containing random `keystorepass` / `keypass`, the `repo_keyalias`
   (default `repokey`), and `keydname`. **Note these values** and the printed
   **fingerprint**.
3. **Restore the committed `config.yml`** (this file's sibling) over the one `fdroid init`
   wrote — keep our public settings; make sure `repo_keyalias` matches what init created
   (default `repokey`). Do **not** add the password lines to the committed file.
4. Provide the repo icon at `fdroid/icon.png` (a copy of the launcher icon is fine). The
   workflow copies it to `repo/icons/` and `archive/icons/`, where fdroidserver expects
   it. Do **not** put it (or any other non-APK file) directly in `fdroid/repo/` —
   `fdroid update` indexes every loose file there as a package of its own.
5. **Back up** `fdroid/keystore.p12` + the passwords + fingerprint to
   `C:\Users\steph\Backups\SmarterTube-release-key\` (alongside the APK key). See
   `memory/release_signing.md`.
6. Set the GitHub Actions secrets (values from step 2):

   ```
   gh secret set FDROID_KEYSTORE_P12_BASE64 --repo CodeSculptor/SmarterTube < <(base64 -w0 fdroid/keystore.p12)
   gh secret set FDROID_KEYSTORE_PASS       --repo CodeSculptor/SmarterTube   # the keystorepass
   gh secret set FDROID_KEY_PASS            --repo CodeSculptor/SmarterTube   # the keypass
   ```

7. In the repo's GitHub settings, set **Pages → Build and deployment → Source = GitHub
   Actions**.
8. Trigger the workflow once (`workflow_dispatch`) and confirm
   `https://codesculptor.github.io/SmarterTube/fdroid/repo/index-v2.json` loads.
9. Put the URL + fingerprint in the project `README.md` "Install via F-Droid" section.

## What is committed vs generated

- **Committed:** `config.yml` (no passwords), `metadata/com.codesculptor.smartertube.yml`,
  `icon.png`, this README.
- **Gitignored / generated / secret:** `keystore.p12`, the APKs, and all generated index
  files (`index-*`, `entry-*`, `icons*/`, `archive/`). See the root `.gitignore`.

## What gets published

The workflow has two jobs: `build` (download APKs, sign the index, upload the Pages
artifact) and `deploy` (publish it). The repo serves the APKs of the **two most recent
releases** (`--limit 2` in the workflow, `archive_older: 8` in `config.yml` — four
per-ABI APKs per release); older versions stay available on GitHub Releases. Every run
re-uploads all served APKs as one Pages artifact (~125 MB per release), so raise those
numbers with care. `build` fails if the index lists anything other than
`com.codesculptor.smartertube`.

## If a publish run fails

**Re-dispatch, do not re-run:**

```bash
gh workflow run fdroid-publish.yml --repo CodeSculptor/SmarterTube --ref master
```

A re-run (`gh run rerun`, or "Re-run jobs" in the UI) keeps the previous attempt's
Pages artifact attached to the run; on 2026-10-05 that made the re-run fail for good
with "Multiple artifacts named github-pages". The artifact name now carries the run
attempt to avoid this, but a fresh dispatch also picks up the current `master` and
release list, so it is always the right recovery.
