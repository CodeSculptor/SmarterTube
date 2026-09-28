package com.liskovsoft.smartyoutubetv2.mobile.update;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.DeviceHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.smartyoutubetv2.mobile.update.SmarterTubeVersion.Channel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Response;

/**
 * "Check for updates" for the phone (stmobile) build.
 *
 * <p>Queries the fork's GitHub releases ({@code CodeSculptor/SmarterTube}) — never upstream
 * SmartTube — and applies the SmarterTube versioning rules (see {@code docs/UPDATER_COMPATIBILITY.md}):
 * <ul>
 *   <li>parses each {@code tag_name} with {@link SmarterTubeVersion}, ignoring upstream/malformed
 *       tags instead of crashing;</li>
 *   <li>uses the GitHub <em>list</em> endpoint, not {@code /releases/latest}, so beta/rc
 *       prereleases are visible;</li>
 *   <li>filters by the current build's release channel (a beta build doesn't see alpha);</li>
 *   <li>orders by SmarterTube product version (legacy {@code 31.xx-mobile-*} sorts oldest);</li>
 *   <li>selects the APK asset for the device ABI via {@link ApkAssetSelector}.</li>
 * </ul>
 *
 * <p>The selection logic ({@link #selectFrom}) is pure and unit-tested; the network fetch and
 * JSON parsing are thin wrappers around it. Failures are reported, never thrown to the caller.
 * It does not install APKs: it surfaces the asset/release URL for the UI to open.
 */
public final class MobileUpdateChecker {
    private static final String TAG = MobileUpdateChecker.class.getSimpleName();

    public enum Status {
        /** Current build is the newest visible release. */
        UP_TO_DATE,
        /** A newer release exists and a compatible APK asset was found. */
        UPDATE_AVAILABLE,
        /** A newer release exists but no APK matches the device ABI. */
        NO_COMPATIBLE_ASSET,
        /** Could not check (network/parse failure). */
        ERROR
    }

    /** One release as returned by the GitHub releases API (the bits we use). */
    public static final class ReleaseInfo {
        public final String tag;        // tag_name, e.g. "v0.4.1-beta.1+st31.93"
        public final boolean prerelease; // GitHub's own prerelease flag (cross-checked, not trusted alone)
        public final String htmlUrl;    // release notes page
        public final List<Asset> assets;
        public final String body;       // release notes markdown (may be null)

        public ReleaseInfo(String tag, boolean prerelease, String htmlUrl, List<Asset> assets) {
            this(tag, prerelease, htmlUrl, assets, null);
        }

        public ReleaseInfo(String tag, boolean prerelease, String htmlUrl, List<Asset> assets, String body) {
            this.tag = tag;
            this.prerelease = prerelease;
            this.htmlUrl = htmlUrl;
            this.assets = assets != null ? assets : new ArrayList<>();
            this.body = body;
        }
    }

    public static final class Asset {
        public final String name;
        public final String url; // browser_download_url

        public Asset(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    public static final class Result {
        public final Status status;
        public final String latestTag;    // e.g. "v0.4.1-beta.1+st31.93"
        public final String upstreamBase; // e.g. "31.93" (display/diagnostics)
        public final String assetUrl;     // direct APK url when UPDATE_AVAILABLE
        public final String releaseUrl;   // release notes page
        public final String latestNotes;  // latest release's notes markdown (may be null)
        /** The release matching the installed build, or null if not found (e.g. a dev build). */
        public final ReleaseInfo installedRelease;

        public Result(Status status, String latestTag, String upstreamBase, String assetUrl, String releaseUrl) {
            this(status, latestTag, upstreamBase, assetUrl, releaseUrl, null, null);
        }

        public Result(Status status, String latestTag, String upstreamBase, String assetUrl, String releaseUrl,
                      String latestNotes, ReleaseInfo installedRelease) {
            this.status = status;
            this.latestTag = latestTag;
            this.upstreamBase = upstreamBase;
            this.assetUrl = assetUrl;
            this.releaseUrl = releaseUrl;
            this.latestNotes = latestNotes;
            this.installedRelease = installedRelease;
        }
    }

    public interface Listener {
        void onResult(Result result);
    }

    private MobileUpdateChecker() {
    }

    /**
     * Fetches {@code releasesApiUrl} (the GitHub {@code /releases} list endpoint), selects the best
     * update for this build, and reports back on the main thread. Network + parsing run on a worker
     * thread; the listener is always invoked on the main thread.
     */
    public static void check(String releasesApiUrl, String currentVersionName, Listener listener) {
        final Handler main = new Handler(Looper.getMainLooper());
        final String deviceAbi = DeviceHelpers.getPrimaryAbi();
        final Channel channel = channelOf(currentVersionName);
        new Thread(() -> {
            Result result;
            try {
                String json = fetch(releasesApiUrl);
                List<ReleaseInfo> releases = parseReleases(json);
                result = selectFrom(releases, currentVersionName, deviceAbi, channel);
            } catch (Exception e) {
                Log.e(TAG, "Update check failed: %s", e.getMessage());
                result = new Result(Status.ERROR, null, null, null, null);
            }
            final Result delivered = result;
            main.post(() -> listener.onResult(delivered));
        }, "MobileUpdateChecker").start();
    }

    /**
     * Pure selection logic over already-parsed releases. No Android/network dependencies — unit-tested.
     *
     * @param releases           releases from the fork's GitHub repo (newest-first not required)
     * @param currentVersionName the installed build's versionName (new or legacy scheme)
     * @param deviceAbi          device primary ABI, e.g. {@code arm64-v8a}
     * @param userChannel        the channel the current build is on (controls visibility)
     */
    public static Result selectFrom(List<ReleaseInfo> releases, String currentVersionName,
                                    String deviceAbi, Channel userChannel) {
        SmarterTubeVersion current = SmarterTubeVersion.parse(currentVersionName);
        if (current == null) {
            // Unknown/legacy-unparseable current build: treat as the oldest possible so any
            // real release is offered rather than silently swallowed.
            current = SmarterTubeVersion.parse("31.0-mobile-0");
        }

        ReleaseInfo bestRelease = null;
        SmarterTubeVersion bestVersion = null;
        ReleaseInfo installed = null;
        for (ReleaseInfo r : releases) {
            if (r == null) {
                continue;
            }
            SmarterTubeVersion v = SmarterTubeVersion.parse(r.tag);
            if (v == null) {
                Log.d(TAG, "Ignoring unrecognised release tag: %s", r.tag);
                continue; // upstream-only or malformed -> ignore
            }
            if (installed == null && !v.isLegacy() && v.compareTo(current) == 0) {
                installed = r; // the installed build's own release (for "What's new")
            }
            // Trust the parsed SmarterTube channel over GitHub's prerelease flag; log a mismatch.
            boolean parsedPrerelease = v.getChannel() != Channel.STABLE;
            if (parsedPrerelease != r.prerelease) {
                Log.d(TAG, "Prerelease flag mismatch for %s: parsed=%s, github=%s",
                        r.tag, parsedPrerelease, r.prerelease);
            }
            if (!v.isVisibleTo(userChannel)) {
                continue; // channel filtering
            }
            if (bestVersion == null || v.compareTo(bestVersion) > 0) {
                bestVersion = v;
                bestRelease = r;
            }
        }

        if (bestVersion == null) {
            return new Result(Status.UP_TO_DATE, null, null, null, null, null, installed);
        }
        if (bestVersion.compareTo(current) <= 0) {
            return new Result(Status.UP_TO_DATE, bestVersion.getRaw(), bestVersion.getUpstreamBase(),
                    null, bestRelease.htmlUrl, bestRelease.body, installed);
        }

        List<String> names = new ArrayList<>();
        for (Asset a : bestRelease.assets) {
            if (a != null && a.name != null) {
                names.add(a.name);
            }
        }
        String chosenName = ApkAssetSelector.select(names, deviceAbi);
        if (chosenName == null) {
            return new Result(Status.NO_COMPATIBLE_ASSET, bestVersion.getRaw(),
                    bestVersion.getUpstreamBase(), null, bestRelease.htmlUrl, bestRelease.body, installed);
        }
        String assetUrl = urlForName(bestRelease.assets, chosenName);
        return new Result(Status.UPDATE_AVAILABLE, bestVersion.getRaw(),
                bestVersion.getUpstreamBase(), assetUrl, bestRelease.htmlUrl, bestRelease.body, installed);
    }

    /** Parses the GitHub {@code /releases} JSON array. Tolerates malformed entries (skips them). */
    public static List<ReleaseInfo> parseReleases(String json) {
        List<ReleaseInfo> out = new ArrayList<>();
        if (json == null) {
            return out;
        }
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                String tag = o.optString("tag_name", null);
                if (tag == null || tag.isEmpty()) {
                    continue;
                }
                boolean prerelease = o.optBoolean("prerelease", false);
                String htmlUrl = o.optString("html_url", null);
                String body = o.isNull("body") ? null : o.optString("body", null);
                List<Asset> assets = new ArrayList<>();
                JSONArray assetArr = o.optJSONArray("assets");
                if (assetArr != null) {
                    for (int j = 0; j < assetArr.length(); j++) {
                        JSONObject a = assetArr.optJSONObject(j);
                        if (a != null) {
                            String name = a.optString("name", null);
                            String url = a.optString("browser_download_url", null);
                            if (name != null && url != null) {
                                assets.add(new Asset(name, url));
                            }
                        }
                    }
                }
                out.add(new ReleaseInfo(tag, prerelease, htmlUrl, assets, body));
            }
        } catch (Exception e) {
            // Not a JSON array (e.g. a GitHub error object) -> no releases.
            Log.e(TAG, "Could not parse releases JSON: %s", e.getMessage());
        }
        return out;
    }

    /** Current build's release channel, defaulting to BETA while the app is pre-1.0. */
    public static Channel channelOf(String versionName) {
        SmarterTubeVersion v = SmarterTubeVersion.parse(versionName);
        return v != null ? v.getChannel() : Channel.BETA;
    }

    @Nullable
    private static String urlForName(List<Asset> assets, String name) {
        for (Asset a : assets) {
            if (a != null && name.equals(a.name)) {
                return a.url;
            }
        }
        return null;
    }

    private static String fetch(String url) throws Exception {
        // Reuse the app's shared OkHttp client (tuned DNS/TLS/timeouts) rather than a raw
        // HttpURLConnection, which is a weaker path on some networks.
        try (Response response = OkHttpManager.instance(false).doGetRequest(url)) {
            if (response == null || !response.isSuccessful() || response.body() == null) {
                int code = response != null ? response.code() : -1;
                throw new IllegalStateException("HTTP " + code + " for " + url);
            }
            return response.body().string();
        }
    }
}
