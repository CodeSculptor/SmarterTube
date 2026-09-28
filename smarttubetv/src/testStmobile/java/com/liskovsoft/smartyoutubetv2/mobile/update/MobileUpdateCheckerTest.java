package com.liskovsoft.smartyoutubetv2.mobile.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.mobile.update.MobileUpdateChecker.Asset;
import com.liskovsoft.smartyoutubetv2.mobile.update.MobileUpdateChecker.ReleaseInfo;
import com.liskovsoft.smartyoutubetv2.mobile.update.MobileUpdateChecker.Result;
import com.liskovsoft.smartyoutubetv2.mobile.update.MobileUpdateChecker.Status;
import com.liskovsoft.smartyoutubetv2.mobile.update.SmarterTubeVersion.Channel;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for the pure {@link MobileUpdateChecker#selectFrom} selection logic — release
 * discovery/ordering/channel/ABI rules from {@code docs/UPDATER_COMPATIBILITY.md}, exercised
 * without any network. Run with: gradlew :smarttubetv:testStmobileDebugUnitTest
 */
public class MobileUpdateCheckerTest {
    private static final String ABI = "arm64-v8a";

    @Test
    public void offersNewerBetaWithMatchingAbi() {
        List<ReleaseInfo> releases = Arrays.asList(
                release("v0.4.0-beta.1+st31.93", true),
                release("v0.4.1-beta.1+st31.93", true));
        Result r = MobileUpdateChecker.selectFrom(releases, "v0.4.0-beta.1+st31.93", ABI, Channel.BETA);
        assertEquals(Status.UPDATE_AVAILABLE, r.status);
        assertEquals("v0.4.1-beta.1+st31.93", r.latestTag);
        assertNotNull(r.assetUrl);
        assertTrue(r.assetUrl.contains("arm64-v8a"));
    }

    @Test
    public void reportsUpToDateWhenCurrentIsLatest() {
        List<ReleaseInfo> releases = Collections.singletonList(release("v0.4.0-beta.1+st31.93", true));
        Result r = MobileUpdateChecker.selectFrom(releases, "v0.4.0-beta.1+st31.93", ABI, Channel.BETA);
        assertEquals(Status.UP_TO_DATE, r.status);
    }

    @Test
    public void betaUserDoesNotSeeAlpha() {
        List<ReleaseInfo> releases = Arrays.asList(
                release("v0.4.0-beta.1+st31.93", true),
                release("v0.5.0-alpha.1+st31.96", true));
        Result r = MobileUpdateChecker.selectFrom(releases, "v0.4.0-beta.1+st31.93", ABI, Channel.BETA);
        // The only newer release is an alpha, hidden from beta users -> nothing to offer.
        assertEquals(Status.UP_TO_DATE, r.status);
    }

    @Test
    public void alphaUserSeesAlpha() {
        List<ReleaseInfo> releases = Arrays.asList(
                release("v0.4.0-beta.1+st31.93", true),
                release("v0.5.0-alpha.1+st31.96", true));
        Result r = MobileUpdateChecker.selectFrom(releases, "v0.4.0-beta.1+st31.93", ABI, Channel.ALPHA);
        assertEquals(Status.UPDATE_AVAILABLE, r.status);
        assertEquals("v0.5.0-alpha.1+st31.96", r.latestTag);
    }

    @Test
    public void legacyCurrentBuildIsOfferedTheBetaReset() {
        // A user still on the legacy 31.93-mobile-1.4 build should be offered the beta reset.
        List<ReleaseInfo> releases = Collections.singletonList(release("v0.4.0-beta.1+st31.93", true));
        Result r = MobileUpdateChecker.selectFrom(releases, "31.93-mobile-1.4", ABI, Channel.BETA);
        assertEquals(Status.UPDATE_AVAILABLE, r.status);
        assertEquals("v0.4.0-beta.1+st31.93", r.latestTag);
    }

    @Test
    public void ignoresUpstreamAndMalformedTags() {
        List<ReleaseInfo> releases = Arrays.asList(
                release("31.94", false),          // upstream SmartTube TV release
                release("not-a-release", false),  // malformed
                release("v0.4.1-beta.1+st31.93", true));
        Result r = MobileUpdateChecker.selectFrom(releases, "v0.4.0-beta.1+st31.93", ABI, Channel.BETA);
        assertEquals(Status.UPDATE_AVAILABLE, r.status);
        assertEquals("v0.4.1-beta.1+st31.93", r.latestTag);
    }

    @Test
    public void noUpdateWhenProductVersionOlderDespiteNewerUpstream() {
        // Latest available has a newer upstream base but an OLDER product version.
        List<ReleaseInfo> releases = Collections.singletonList(release("v0.4.0-beta.9+st31.94", true));
        Result r = MobileUpdateChecker.selectFrom(releases, "v0.4.1-beta.1+st31.93", ABI, Channel.BETA);
        assertEquals(Status.UP_TO_DATE, r.status);
    }

    @Test
    public void noCompatibleAssetWhenAbiMissing() {
        ReleaseInfo onlyX86 = new ReleaseInfo("v0.4.1-beta.1+st31.93", true,
                "https://github.com/CodeSculptor/SmarterTube/releases/tag/x",
                Collections.singletonList(new Asset("SmarterTube-v0.4.1-beta.1-st31.93-x86.apk", "http://x/x86.apk")));
        Result r = MobileUpdateChecker.selectFrom(
                Collections.singletonList(onlyX86), "v0.4.0-beta.1+st31.93", ABI, Channel.BETA);
        assertEquals(Status.NO_COMPATIBLE_ASSET, r.status);
        assertNotNull(r.releaseUrl);
    }

    @Test
    public void parsesGitHubReleasesJson() {
        String json = "[{\"tag_name\":\"v0.4.1-beta.1+st31.93\",\"prerelease\":true,"
                + "\"html_url\":\"https://github.com/CodeSculptor/SmarterTube/releases/tag/v0.4.1\","
                + "\"assets\":[{\"name\":\"SmarterTube-v0.4.1-beta.1-st31.93-arm64-v8a.apk\","
                + "\"browser_download_url\":\"http://x/arm64.apk\"}]}]";
        List<ReleaseInfo> releases = MobileUpdateChecker.parseReleases(json);
        assertEquals(1, releases.size());
        assertEquals("v0.4.1-beta.1+st31.93", releases.get(0).tag);
        assertEquals(1, releases.get(0).assets.size());
    }

    @Test
    public void parseReleasesToleratesGarbage() {
        // A GitHub error object (not an array) must yield no releases, not a crash.
        assertTrue(MobileUpdateChecker.parseReleases("{\"message\":\"Not Found\"}").isEmpty());
        assertTrue(MobileUpdateChecker.parseReleases(null).isEmpty());
        assertTrue(MobileUpdateChecker.parseReleases("garbage").isEmpty());
    }

    // ---- helpers ----

    /** A release carrying universal + arm64 + armeabi APK assets named per the doc convention. */
    @Test
    public void findsInstalledReleaseIgnoringUpstreamBase() {
        List<ReleaseInfo> releases = Arrays.asList(
                release("v0.6.1-beta.1+st32.59", false),
                release("v0.7.0-beta.1+st32.59", false),
                release("v0.8.0-beta.1+st32.60", false));
        Result r = MobileUpdateChecker.selectFrom(releases, "v0.7.0-beta.1+st32.59", ABI, Channel.BETA);
        assertEquals(Status.UPDATE_AVAILABLE, r.status);
        assertNotNull(r.installedRelease);
        assertEquals("v0.7.0-beta.1+st32.59", r.installedRelease.tag);
    }

    @Test
    public void noInstalledReleaseForUnreleasedBuild() {
        List<ReleaseInfo> releases = Collections.singletonList(release("v0.7.0-beta.1+st32.59", false));
        Result r = MobileUpdateChecker.selectFrom(releases, "v0.7.1-beta.1+st32.59", ABI, Channel.BETA);
        assertEquals(Status.UP_TO_DATE, r.status);
        assertEquals(null, r.installedRelease);
    }

    private static ReleaseInfo release(String tag, boolean prerelease) {
        String base = "SmarterTube-" + tag.replace('+', '-');
        List<Asset> assets = new ArrayList<>();
        assets.add(new Asset(base + "-universal.apk", "http://x/" + tag + "/universal.apk"));
        assets.add(new Asset(base + "-arm64-v8a.apk", "http://x/" + tag + "/arm64-v8a.apk"));
        assets.add(new Asset(base + "-armeabi-v7a.apk", "http://x/" + tag + "/armeabi-v7a.apk"));
        return new ReleaseInfo(tag, prerelease,
                "https://github.com/CodeSculptor/SmarterTube/releases/tag/" + tag, assets);
    }
}
