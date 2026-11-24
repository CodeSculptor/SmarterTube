package com.liskovsoft.smartyoutubetv2.mobile.offline;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * File-backed library: {@code <root>/<videoId>/{meta.json, video.mp4, video.mp4.part, thumb.jpg}}.
 * No database; listing is a scan of the folders. Plain java.io so it is unit tested on the JVM.
 */
public final class OfflineStore {
    static final String META = "meta.json";
    static final String VIDEO = "video.mp4";
    static final String PART = "video.mp4.part";
    static final String THUMB = "thumb.jpg";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final File mRoot;

    public OfflineStore(File root) {
        mRoot = root;
    }

    public File root() { return mRoot; }
    public File dir(String videoId) { return new File(mRoot, sanitize(videoId)); }
    public File videoFile(String videoId) { return new File(dir(videoId), VIDEO); }
    public File partFile(String videoId) { return new File(dir(videoId), PART); }
    public File thumbFile(String videoId) { return new File(dir(videoId), THUMB); }

    /** Video ids are 11 chars of [A-Za-z0-9_-]; reject anything that could escape the folder. */
    static String sanitize(String videoId) {
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{1,40}")) {
            throw new IllegalArgumentException("bad video id");
        }
        return videoId;
    }

    public SavedVideo get(String videoId) {
        File meta = new File(dir(videoId), META);
        if (!meta.exists()) return null;
        try {
            return SavedVideo.fromJson(new String(readAll(meta), UTF8));
        } catch (Exception e) {
            return null;
        }
    }

    /** Atomic: write a temp file then rename. */
    public synchronized void put(SavedVideo v) throws IOException {
        File dir = dir(v.videoId);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdirs " + dir);
        File tmp = new File(dir, META + ".tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            out.write(v.toJson().getBytes(UTF8));
        } finally {
            out.close();
        }
        File meta = new File(dir, META);
        if (!tmp.renameTo(meta)) {
            meta.delete();
            if (!tmp.renameTo(meta)) throw new IOException("rename meta");
        }
    }

    /** Newest first. */
    public List<SavedVideo> list() {
        List<SavedVideo> result = new ArrayList<>();
        File[] dirs = mRoot.listFiles();
        if (dirs != null) {
            for (File d : dirs) {
                if (!d.isDirectory()) continue;
                File meta = new File(d, META);
                if (!meta.exists()) continue;
                try {
                    result.add(SavedVideo.fromJson(new String(readAll(meta), UTF8)));
                } catch (Exception ignored) {
                    // damaged entry: skipped; "Delete all" still removes its folder
                }
            }
        }
        Collections.sort(result, new Comparator<SavedVideo>() {
            @Override
            public int compare(SavedVideo a, SavedVideo b) {
                return a.createdAt == b.createdAt ? 0 : (a.createdAt < b.createdAt ? 1 : -1);
            }
        });
        return result;
    }

    /** Bytes written so far (finished file, or the in-flight part). */
    public long bytesOnDisk(String videoId) {
        File f = videoFile(videoId);
        if (f.exists()) return f.length();
        File p = partFile(videoId);
        return p.exists() ? p.length() : 0;
    }

    public long totalUsedBytes() {
        long sum = 0;
        File[] dirs = mRoot.listFiles();
        if (dirs == null) return 0;
        for (File d : dirs) sum += size(d);
        return sum;
    }

    public void delete(String videoId) {
        deleteRecursive(dir(videoId));
    }

    public void deleteAll() {
        File[] dirs = mRoot.listFiles();
        if (dirs == null) return;
        for (File d : dirs) deleteRecursive(d);
    }

    private static long size(File f) {
        if (f.isFile()) return f.length();
        long sum = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) sum += size(k);
        return sum;
    }

    private static void deleteRecursive(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        f.delete();
    }

    private static byte[] readAll(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            in.close();
        }
    }
}
