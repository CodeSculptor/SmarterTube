package com.liskovsoft.smartyoutubetv2.mobile.offline;

/** Pure rules for the chunked, resumable download (unit tested). */
public final class OfflineDownloadPlan {
    /**
     * YouTube's progressive URLs are fetched in ranged chunks; 1 MB chunks were verified to run at
     * full speed past the first megabyte for the muxed MP4 stream (spike, M0).
     */
    public static final int CHUNK_BYTES = 1_000_000;
    public static final int MAX_ATTEMPTS = 5;
    private static final long MIN_FREE_MARGIN = 100L * 1024 * 1024;

    private OfflineDownloadPlan() {}

    /** Inclusive byte range of the next chunk. {@code total <= 0} means unknown size. */
    public static long[] nextRange(long done, long total) {
        long end = done + CHUNK_BYTES - 1;
        if (total > 0 && end > total - 1) end = total - 1;
        return new long[]{done, end};
    }

    /** Total size from a {@code Content-Range: bytes 0-999/12345} header, or -1. */
    public static long parseContentRangeTotal(String header) {
        if (header == null) return -1;
        int slash = header.lastIndexOf('/');
        if (slash < 0) return -1;
        String tail = header.substring(slash + 1).trim();
        try {
            return Long.parseLong(tail);
        } catch (NumberFormatException e) {
            return -1; // "*"
        }
    }

    /** Remaining bytes plus 10% and a 100 MB cushion must fit. Unknown size: only the cushion. */
    public static boolean hasEnoughSpace(long total, long done, long freeBytes) {
        long remaining = total > 0 ? Math.max(0, total - done) : 0;
        return freeBytes >= (long) (remaining * 1.1) + MIN_FREE_MARGIN;
    }

    /** 403/410 from a ranged GET means the signed URL expired: re-resolve it. */
    public static boolean isExpiredUrl(int httpCode) {
        return httpCode == 403 || httpCode == 410;
    }

    public static boolean shouldGiveUp(int runAttemptCount) {
        return runAttemptCount >= MAX_ATTEMPTS;
    }

    public static int percent(long done, long total) {
        if (total <= 0) return 0;
        return (int) Math.min(100, done * 100 / total);
    }
}
