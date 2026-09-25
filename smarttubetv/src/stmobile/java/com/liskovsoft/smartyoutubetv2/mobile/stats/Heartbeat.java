package com.liskovsoft.smartyoutubetv2.mobile.stats;

/**
 * Pure logic for the anonymous daily usage heartbeat. No Android dependencies — unit-tested.
 *
 * <p>How counts work without any install id: each install sends <em>at most one</em> ping per UTC
 * day, and flags whether it is the first ping of the week / month / ever. The server only
 * increments counters, so
 * <ul>
 *   <li>pings on day D = daily active installs,</li>
 *   <li>pings with {@code new_week} in week W = weekly active installs,</li>
 *   <li>pings with {@code new_month} in month M = monthly active installs,</li>
 *   <li>pings with {@code first} = new (opted-in) installs.</li>
 * </ul>
 * The "already sent" markers live only on the device ({@link StatsPrefs}).
 *
 * <p>Days are UTC epoch days; weeks are Monday-based; months are {@code year * 12 + (month - 1)}.
 * Deliberately avoids {@code java.time} (minSdk 21, no desugaring).
 */
public final class Heartbeat {
    private static final long MILLIS_PER_DAY = 24L * 60 * 60 * 1000;

    /** Device-local "last sent" markers; -1 means never. */
    public static final class State {
        public final long lastDay;
        public final long lastWeek;
        public final long lastMonth;

        public State(long lastDay, long lastWeek, long lastMonth) {
            this.lastDay = lastDay;
            this.lastWeek = lastWeek;
            this.lastMonth = lastMonth;
        }
    }

    /** What to send today, plus the state to persist once the send succeeds. */
    public static final class Ping {
        public final boolean newWeek;
        public final boolean newMonth;
        public final boolean first;
        public final State nextState;

        Ping(boolean newWeek, boolean newMonth, boolean first, State nextState) {
            this.newWeek = newWeek;
            this.newMonth = newMonth;
            this.first = first;
            this.nextState = nextState;
        }

        public String toJson(String appVersion, int sdkInt) {
            return "{\"v\":\"" + Json.escape(appVersion) + "\""
                    + ",\"sdk\":" + sdkInt
                    + ",\"new_week\":" + newWeek
                    + ",\"new_month\":" + newMonth
                    + ",\"first\":" + first
                    + "}";
        }
    }

    private Heartbeat() {}

    /** Returns the ping due now, or {@code null} if one was already sent today. */
    public static Ping due(State state, long nowMillis) {
        long day = epochDay(nowMillis);
        if (state.lastDay == day) {
            return null;
        }
        long week = weekIndex(day);
        long month = monthIndex(day);
        return new Ping(
                state.lastWeek != week,
                state.lastMonth != month,
                state.lastDay == -1,
                new State(day, week, month));
    }

    static long epochDay(long millis) {
        return floorDiv(millis, MILLIS_PER_DAY);
    }

    /** Monday-based week number. Epoch day 0 (1970-01-01) was a Thursday, hence the +3. */
    static long weekIndex(long epochDay) {
        return floorDiv(epochDay + 3, 7);
    }

    /** {@code year * 12 + (month - 1)} for a UTC epoch day (Howard Hinnant's civil_from_days). */
    static long monthIndex(long epochDay) {
        long z = epochDay + 719468;
        long era = floorDiv(z, 146097);
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long month = mp < 10 ? mp + 3 : mp - 9; // 1..12
        long year = yoe + era * 400 + (month <= 2 ? 1 : 0);
        return year * 12 + (month - 1);
    }

    /** Local floorDiv: {@code Math.floorDiv} is API 24+ and minSdk is 21. */
    private static long floorDiv(long x, long y) {
        long q = x / y;
        if ((x % y != 0) && ((x < 0) != (y < 0))) {
            q--;
        }
        return q;
    }
}
