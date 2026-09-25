package com.liskovsoft.smartyoutubetv2.mobile.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link CrashReport} — above all, that exception messages (which can hold URLs,
 * video ids, search text) never make it into a report.
 *
 * Run with: gradlew :smarttubetv:testStmobileDebugUnitTest
 */
public class CrashReportTest {

    @Test
    public void traceOmitsMessagesIncludingCauses() {
        Exception cause = new IllegalArgumentException("https://youtube.com/watch?v=SECRET_ID");
        Exception top = new RuntimeException("search: my private query", cause);

        String trace = CrashReport.trace(top);

        assertFalse(trace.contains("SECRET_ID"));
        assertFalse(trace.contains("private query"));
        assertTrue(trace.startsWith("java.lang.RuntimeException\n"));
        assertTrue(trace.contains("Caused by: java.lang.IllegalArgumentException\n"));
        assertTrue(trace.contains("\tat com.liskovsoft.smartyoutubetv2.mobile.stats.CrashReportTest."));
    }

    @Test
    public void framesAreCapped() {
        Exception e = new Exception("x");
        StackTraceElement[] frames = new StackTraceElement[CrashReport.MAX_FRAMES + 10];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new StackTraceElement("a.B", "m" + i, "B.java", i);
        }
        e.setStackTrace(frames);

        String trace = CrashReport.trace(e);

        assertTrue(trace.contains("\tat a.B.m" + (CrashReport.MAX_FRAMES - 1) + "(B.java:"));
        assertFalse(trace.contains("\tat a.B.m" + CrashReport.MAX_FRAMES + "("));
        assertTrue(trace.contains("\t... 10 more\n"));
    }

    @Test
    public void causeChainIsBounded() {
        Throwable t = new Error("leaf");
        for (int i = 0; i < 20; i++) {
            t = new Exception("level", t);
        }
        String trace = CrashReport.trace(t);
        int causedBy = trace.split("Caused by: ", -1).length - 1;
        assertEquals(CrashReport.MAX_CAUSES, causedBy);
    }

    @Test
    public void jsonEscapesTabsAndNewlines() {
        Exception e = new Exception("m");
        e.setStackTrace(new StackTraceElement[]{new StackTraceElement("a.B", "m", "B.java", 1)});
        String json = CrashReport.toJson(e, "v1", 30, "Acme \"Phone\"");
        assertEquals("{\"v\":\"v1\",\"sdk\":30,\"device\":\"Acme \\\"Phone\\\"\","
                + "\"trace\":\"java.lang.Exception\\n\\tat a.B.m(B.java:1)\\n\"}", json);
    }
}
