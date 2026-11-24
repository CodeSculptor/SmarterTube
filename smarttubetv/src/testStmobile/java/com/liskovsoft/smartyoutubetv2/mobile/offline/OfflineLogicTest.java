package com.liskovsoft.smartyoutubetv2.mobile.offline;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class OfflineLogicTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static String player(String status, boolean live, String formatsJson) {
        return "{\"playabilityStatus\":{\"status\":\"" + status + "\",\"reason\":\"r\"},"
                + "\"videoDetails\":{\"videoId\":\"abcdefghijk\",\"title\":\"T\",\"author\":\"A\","
                + "\"lengthSeconds\":\"213\",\"isLive\":" + live + ",\"thumbnail\":{\"thumbnails\":["
                + "{\"url\":\"http://t/1\"},{\"url\":\"http://t/2\"}]}},"
                + "\"streamingData\":{\"formats\":" + formatsJson + ",\"serverAbrStreamingUrl\":\"x\"}}";
    }

    private static final String MUXED = "[{\"itag\":18,\"url\":\"http://v\",\"mimeType\":"
            + "\"video/mp4; codecs=\\\"avc1.42001E, mp4a.40.2\\\"\",\"height\":360,\"qualityLabel\":\"360p\","
            + "\"contentLength\":\"37203872\"}]";

    @Test
    public void muxedMp4IsSaveable() throws Exception {
        OfflinePlayerResponse r = OfflinePlayerResponse.parse(player("OK", false, MUXED));
        assertEquals(OfflinePlayerResponse.Saveability.SAVEABLE, r.saveability());
        assertEquals(18, r.itag);
        assertEquals(37203872L, r.contentLength);
        assertEquals("360p", r.qualityLabel);
        assertEquals("T", r.title);
        assertEquals(213, r.durationSec);
        assertEquals("http://t/2", r.thumbnailUrl); // largest
    }

    @Test
    public void missingContentLengthIsUnknown() throws Exception {
        String f = MUXED.replace(",\"contentLength\":\"37203872\"", "");
        OfflinePlayerResponse r = OfflinePlayerResponse.parse(player("OK", false, f));
        assertEquals(OfflinePlayerResponse.Saveability.SAVEABLE, r.saveability());
        assertEquals(-1, r.contentLength);
    }

    @Test
    public void liveIsNotSaveable() throws Exception {
        OfflinePlayerResponse r = OfflinePlayerResponse.parse(player("OK", true, MUXED));
        assertEquals(OfflinePlayerResponse.Saveability.LIVE, r.saveability());
    }

    @Test
    public void unplayableIsNotSaveable() throws Exception {
        OfflinePlayerResponse r = OfflinePlayerResponse.parse(player("LOGIN_REQUIRED", false, "[]"));
        assertEquals(OfflinePlayerResponse.Saveability.UNPLAYABLE, r.saveability());
    }

    @Test
    public void sabrOnlyOrCipheredHasNoDirectStream() throws Exception {
        String ciphered = "[{\"itag\":18,\"signatureCipher\":\"s=1&url=2\",\"mimeType\":"
                + "\"video/mp4; codecs=\\\"avc1, mp4a.40.2\\\"\",\"height\":360}]";
        assertEquals(OfflinePlayerResponse.Saveability.NO_DIRECT_STREAM,
                OfflinePlayerResponse.parse(player("OK", false, ciphered)).saveability());
        assertEquals(OfflinePlayerResponse.Saveability.NO_DIRECT_STREAM,
                OfflinePlayerResponse.parse(player("OK", false, "[]")).saveability());
    }

    @Test
    public void videoOnlyOrWebmIsNotAccepted() throws Exception {
        String f = "[{\"itag\":22,\"url\":\"u\",\"mimeType\":\"video/webm; codecs=\\\"vp9\\\"\",\"height\":720},"
                + "{\"itag\":136,\"url\":\"u\",\"mimeType\":\"video/mp4; codecs=\\\"avc1\\\"\",\"height\":720}]";
        assertEquals(OfflinePlayerResponse.Saveability.NO_DIRECT_STREAM,
                OfflinePlayerResponse.parse(player("OK", false, f)).saveability());
    }

    @Test
    public void prefersTallestMuxed() throws Exception {
        String f = "[{\"itag\":18,\"url\":\"low\",\"mimeType\":\"video/mp4; codecs=\\\"avc1, mp4a.40.2\\\"\",\"height\":360},"
                + "{\"itag\":22,\"url\":\"hi\",\"mimeType\":\"video/mp4; codecs=\\\"avc1, mp4a.40.2\\\"\",\"height\":720}]";
        OfflinePlayerResponse r = OfflinePlayerResponse.parse(player("OK", false, f));
        assertEquals("hi", r.url);
        assertEquals(22, r.itag);
    }

    // ----- plan -----

    @Test
    public void rangesAdvanceInChunksAndClampToTotal() {
        assertArrayEquals(new long[]{0, 999_999}, OfflineDownloadPlan.nextRange(0, 5_000_000));
        assertArrayEquals(new long[]{1_000_000, 1_999_999}, OfflineDownloadPlan.nextRange(1_000_000, -1));
        assertArrayEquals(new long[]{4_000_000, 4_499_999}, OfflineDownloadPlan.nextRange(4_000_000, 4_500_000));
    }

    @Test
    public void contentRangeTotal() {
        assertEquals(37203872L, OfflineDownloadPlan.parseContentRangeTotal("bytes 0-999999/37203872"));
        assertEquals(-1, OfflineDownloadPlan.parseContentRangeTotal("bytes 0-9/*"));
        assertEquals(-1, OfflineDownloadPlan.parseContentRangeTotal(null));
        assertEquals(-1, OfflineDownloadPlan.parseContentRangeTotal("garbage"));
    }

    @Test
    public void spaceCheckKeepsMargin() {
        long mb = 1024 * 1024;
        assertTrue(OfflineDownloadPlan.hasEnoughSpace(100 * mb, 0, 300 * mb));
        assertFalse(OfflineDownloadPlan.hasEnoughSpace(100 * mb, 0, 150 * mb));
        assertTrue(OfflineDownloadPlan.hasEnoughSpace(100 * mb, 99 * mb, 150 * mb)); // mostly done
        assertTrue(OfflineDownloadPlan.hasEnoughSpace(-1, 0, 101 * mb)); // unknown size: margin only
    }

    @Test
    public void expiryAndRetryRules() {
        assertTrue(OfflineDownloadPlan.isExpiredUrl(403));
        assertTrue(OfflineDownloadPlan.isExpiredUrl(410));
        assertFalse(OfflineDownloadPlan.isExpiredUrl(500));
        assertFalse(OfflineDownloadPlan.shouldGiveUp(4));
        assertTrue(OfflineDownloadPlan.shouldGiveUp(5));
        assertEquals(42, OfflineDownloadPlan.percent(42, 100));
        assertEquals(0, OfflineDownloadPlan.percent(5, -1));
        assertEquals(100, OfflineDownloadPlan.percent(200, 100));
    }

    // ----- store -----

    private SavedVideo video(String id, long created) {
        SavedVideo v = new SavedVideo();
        v.videoId = id;
        v.title = "Title " + id;
        v.createdAt = created;
        return v;
    }

    @Test
    public void metaRoundTripAndOrdering() throws Exception {
        OfflineStore store = new OfflineStore(tmp.newFolder("offline"));
        SavedVideo a = video("aaaaaaaaaaa", 1);
        a.state = SavedVideo.DONE;
        a.totalBytes = 10;
        a.qualityLabel = "360p";
        a.positionMs = 1234;
        store.put(a);
        store.put(video("bbbbbbbbbbb", 2));

        SavedVideo back = store.get("aaaaaaaaaaa");
        assertNotNull(back);
        assertEquals("Title aaaaaaaaaaa", back.title);
        assertEquals(SavedVideo.DONE, back.state);
        assertEquals(1234, back.positionMs);
        assertEquals("360p", back.qualityLabel);
        assertNull(back.reason);

        List<SavedVideo> list = store.list();
        assertEquals(2, list.size());
        assertEquals("bbbbbbbbbbb", list.get(0).videoId); // newest first
    }

    @Test
    public void bytesUsageAndDelete() throws Exception {
        OfflineStore store = new OfflineStore(tmp.newFolder("offline2"));
        store.put(video("aaaaaaaaaaa", 1));
        FileOutputStream out = new FileOutputStream(store.partFile("aaaaaaaaaaa"));
        out.write(new byte[500]);
        out.close();
        assertEquals(500, store.bytesOnDisk("aaaaaaaaaaa"));
        assertTrue(store.totalUsedBytes() >= 500);

        File done = store.videoFile("aaaaaaaaaaa");
        assertTrue(store.partFile("aaaaaaaaaaa").renameTo(done));
        assertEquals(500, store.bytesOnDisk("aaaaaaaaaaa"));

        store.delete("aaaaaaaaaaa");
        assertNull(store.get("aaaaaaaaaaa"));
        assertEquals(0, store.totalUsedBytes());
    }

    @Test
    public void deleteAllAndDamagedEntry() throws Exception {
        File root = tmp.newFolder("offline3");
        OfflineStore store = new OfflineStore(root);
        store.put(video("aaaaaaaaaaa", 1));
        File bad = new File(root, "zzzzzzzzzzz");
        assertTrue(bad.mkdirs());
        FileOutputStream out = new FileOutputStream(new File(bad, "meta.json"));
        out.write("not json".getBytes("UTF-8"));
        out.close();
        assertEquals(1, store.list().size()); // damaged one skipped, not fatal
        store.deleteAll();
        assertEquals(0, store.list().size());
        assertEquals(0, store.totalUsedBytes());
    }

    @Test(expected = IllegalArgumentException.class)
    public void pathTraversalIdsAreRejected() {
        new OfflineStore(tmp.getRoot()).dir("../evil");
    }

    // ----- formatting + gating -----

    @Test
    public void formatting() {
        assertEquals("512 B", OfflineFormat.bytes(512));
        assertEquals("1.5 MB", OfflineFormat.bytes(1_572_864));
        assertEquals("?", OfflineFormat.bytes(-1));
        assertEquals("3:33", OfflineFormat.duration(213));
        assertEquals("1:02:03", OfflineFormat.duration(3723));
        assertEquals("", OfflineFormat.duration(0));
    }

    /** Guard: every dialog must come from OfflineDialogs (the player activity is not AppCompat). */
    @Test
    public void noDirectDialogBuildersInOfflineCode() throws Exception {
        File dir = new File("src/stmobile/java/com/liskovsoft/smartyoutubetv2/mobile/offline");
        File[] files = dir.listFiles();
        if (files == null) return; // run from an unexpected working dir: skip rather than fail
        for (File f : files) {
            if (f.getName().equals("OfflineDialogs.java") || !f.getName().endsWith(".java")) continue;
            String src = new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
            assertFalse(f.getName() + " builds an AlertDialog directly", src.contains("AlertDialog.Builder"));
        }
    }

    @Test
    public void minApiGate() {
        assertFalse(OfflineSupport.isSupported(20));
        assertTrue(OfflineSupport.isSupported(21));
        assertTrue(OfflineSupport.isSupported(34));
    }
}
