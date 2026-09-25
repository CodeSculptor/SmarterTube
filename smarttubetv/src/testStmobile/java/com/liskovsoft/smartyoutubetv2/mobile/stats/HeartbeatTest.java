package com.liskovsoft.smartyoutubetv2.mobile.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Unit tests for {@link Heartbeat} — once-a-day ping logic and UTC day/week/month math.
 *
 * Run with: gradlew :smarttubetv:testStmobileDebugUnitTest
 */
public class HeartbeatTest {
    private static final Heartbeat.State NEVER = new Heartbeat.State(-1, -1, -1);

    private static long utc(int year, int month, int day, int hour) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(year, month - 1, day, hour, 0, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void firstEverPingFlagsEverything() {
        Heartbeat.Ping p = Heartbeat.due(NEVER, utc(2026, 9, 25, 12));
        assertTrue(p.first);
        assertTrue(p.newWeek);
        assertTrue(p.newMonth);
    }

    @Test
    public void secondPingSameUtcDayIsSuppressed() {
        Heartbeat.Ping p = Heartbeat.due(NEVER, utc(2026, 9, 25, 0));
        assertNull(Heartbeat.due(p.nextState, utc(2026, 9, 25, 23)));
    }

    @Test
    public void nextDaySameWeekAndMonth() {
        // Thu 24 Sep -> Fri 25 Sep 2026
        Heartbeat.Ping p1 = Heartbeat.due(NEVER, utc(2026, 9, 24, 10));
        Heartbeat.Ping p2 = Heartbeat.due(p1.nextState, utc(2026, 9, 25, 10));
        assertFalse(p2.first);
        assertFalse(p2.newWeek);
        assertFalse(p2.newMonth);
    }

    @Test
    public void mondayStartsNewWeek() {
        // Sun 27 Sep -> Mon 28 Sep 2026
        Heartbeat.Ping p1 = Heartbeat.due(NEVER, utc(2026, 9, 27, 10));
        Heartbeat.Ping p2 = Heartbeat.due(p1.nextState, utc(2026, 9, 28, 10));
        assertTrue(p2.newWeek);
        assertFalse(p2.newMonth);
    }

    @Test
    public void firstOfMonthStartsNewMonthButNotNecessarilyWeek() {
        // Wed 30 Sep -> Thu 1 Oct 2026 (same Monday-based week)
        Heartbeat.Ping p1 = Heartbeat.due(NEVER, utc(2026, 9, 30, 10));
        Heartbeat.Ping p2 = Heartbeat.due(p1.nextState, utc(2026, 10, 1, 10));
        assertTrue(p2.newMonth);
        assertFalse(p2.newWeek);
    }

    @Test
    public void monthIndexMatchesCalendarAcrossYearsAndLeapDays() {
        for (int y = 1999; y <= 2031; y++) {
            for (int m = 1; m <= 12; m++) {
                for (int d : new int[]{1, 15, 28}) {
                    long day = Heartbeat.epochDay(utc(y, m, d, 12));
                    assertEquals(y * 12L + (m - 1), Heartbeat.monthIndex(day));
                }
            }
        }
        assertEquals(2028 * 12L + 1, Heartbeat.monthIndex(Heartbeat.epochDay(utc(2028, 2, 29, 12))));
        assertEquals(2028 * 12L + 2, Heartbeat.monthIndex(Heartbeat.epochDay(utc(2028, 3, 1, 0))));
    }

    @Test
    public void weekIndexChangesOnlyOnMondays() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        long start = Heartbeat.epochDay(utc(2025, 1, 1, 12));
        for (long day = start; day < start + 800; day++) {
            c.setTimeInMillis(day * 86_400_000L);
            boolean monday = c.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY;
            assertEquals(monday, Heartbeat.weekIndex(day) != Heartbeat.weekIndex(day - 1));
        }
    }

    @Test
    public void jsonPayloadHasNoIdentifiers() {
        String json = Heartbeat.due(NEVER, utc(2026, 9, 25, 12)).toJson("v0.5.0-beta.7+st32.56", 34);
        assertEquals("{\"v\":\"v0.5.0-beta.7+st32.56\",\"sdk\":34,\"new_week\":true,\"new_month\":true,\"first\":true}", json);
    }
}
