package com.liskovsoft.smartyoutubetv2.mobile.stats;

/**
 * Pure crash-report formatting. No Android dependencies — unit-tested.
 *
 * <p>Privacy rule: the report carries exception <em>class names and stack frames only</em>, never
 * {@link Throwable#getMessage()}. Exception messages routinely contain URLs, video ids, titles,
 * search text or tokens; frames are just code locations.
 */
public final class CrashReport {
    /** Frames kept per throwable in the cause chain. */
    static final int MAX_FRAMES = 40;
    /** Causes followed below the top-level throwable. */
    static final int MAX_CAUSES = 5;

    private CrashReport() {}

    /** Message-free stack trace: {@code Class\n\tat frame...\nCaused by: Class\n\tat ...}. */
    public static String trace(Throwable top) {
        StringBuilder sb = new StringBuilder();
        Throwable t = top;
        for (int depth = 0; t != null && depth <= MAX_CAUSES; depth++) {
            if (depth > 0) {
                sb.append("Caused by: ");
            }
            sb.append(t.getClass().getName()).append('\n');
            StackTraceElement[] frames = t.getStackTrace();
            int n = Math.min(frames.length, MAX_FRAMES);
            for (int i = 0; i < n; i++) {
                StackTraceElement f = frames[i];
                sb.append("\tat ").append(f.getClassName()).append('.').append(f.getMethodName())
                        .append('(').append(f.getFileName() != null ? f.getFileName() : "Unknown Source");
                if (f.getLineNumber() >= 0) {
                    sb.append(':').append(f.getLineNumber());
                }
                sb.append(")\n");
            }
            if (frames.length > n) {
                sb.append("\t... ").append(frames.length - n).append(" more\n");
            }
            Throwable cause = t.getCause();
            t = cause != t ? cause : null;
        }
        return sb.toString();
    }

    public static String toJson(Throwable top, String appVersion, int sdkInt, String device) {
        return "{\"v\":\"" + Json.escape(appVersion) + "\""
                + ",\"sdk\":" + sdkInt
                + ",\"device\":\"" + Json.escape(device) + "\""
                + ",\"trace\":\"" + Json.escape(trace(top)) + "\""
                + "}";
    }
}
