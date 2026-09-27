package com.liskovsoft.smartyoutubetv2.mobile.ui.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Flavor-local persistence for the phone "Upload notifications" feature (Part 2 — push).
 *
 * Holds:
 *  - the master on/off toggle (default OFF — opt-in), plus a one-time "have we asked yet" flag,
 *  - the set of already-seen upload video ids, used by {@code NotificationPollWorker} to
 *    avoid alerting twice for the same video, and
 *  - the upload history (videos the poll has picked up), which backs the phone Notifications
 *    tab now that YouTube's notifications inbox endpoint is dead.
 *
 * Seen ids are stored most-recent-first and capped at {@link #MAX_SEEN} so the prefs entry
 * can't grow without bound. Mirrors the structure of {@link MobileThemePrefs}; stmobile-only,
 * so shared {@code common} code stays untouched and the fork stays upstream-mergeable.
 */
public final class MobileNotificationPrefs {
    private static final String PREFS_NAME = "mobile_notification_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_PROMPTED = "prompted";
    private static final String KEY_SEEN = "seen_ids";
    private static final String KEY_HISTORY = "history";
    private static final String HISTORY_TIME = "t";
    private static final String HISTORY_VIDEO = "v";
    private static final String DELIM = "\n";
    /** Plenty to cover a poll's worth of new uploads while bounding the stored string. */
    private static final int MAX_SEEN = 300;
    /** Upload history kept for the Notifications tab. */
    private static final int MAX_HISTORY = 100;

    private MobileNotificationPrefs() {}

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** Whether the one-time "Get notified about new uploads?" prompt has already been shown. */
    public static boolean wasPrompted(Context context) {
        return prefs(context).getBoolean(KEY_PROMPTED, false);
    }

    public static void setPrompted(Context context) {
        prefs(context).edit().putBoolean(KEY_PROMPTED, true).apply();
    }

    /** Seen upload ids, most-recent-first. Never null. */
    public static LinkedHashSet<String> getSeenIds(Context context) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String raw = prefs(context).getString(KEY_SEEN, null);
        if (!TextUtils.isEmpty(raw)) {
            for (String id : raw.split(DELIM)) {
                if (!TextUtils.isEmpty(id)) {
                    result.add(id);
                }
            }
        }
        return result;
    }

    /**
     * Persist the seen set. {@code freshest} ids are written first (kept on overflow); any
     * leftover {@code older} ids follow. The combined list is de-duplicated and capped at
     * {@link #MAX_SEEN}.
     */
    public static void saveSeenIds(Context context, Collection<String> freshest, Collection<String> older) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (freshest != null) {
            merged.addAll(freshest);
        }
        if (older != null) {
            merged.addAll(older);
        }

        List<String> capped = new ArrayList<>(merged);
        if (capped.size() > MAX_SEEN) {
            capped = capped.subList(0, MAX_SEEN);
        }

        prefs(context).edit().putString(KEY_SEEN, TextUtils.join(DELIM, capped)).apply();
    }

    /** Forget all seen ids and history — used on account switch so a new account seeds fresh (no cross-account alerts). */
    public static void clearSeenIds(Context context) {
        prefs(context).edit().remove(KEY_SEEN).remove(KEY_HISTORY).apply();
    }

    /**
     * Prepend newly picked-up uploads (newest first) to the history, stamped with the time they
     * were picked up. Duplicates are dropped and the history is capped at {@link #MAX_HISTORY}.
     */
    public static void addToHistory(Context context, List<Video> videos) {
        if (videos == null || videos.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        JSONArray result = new JSONArray();
        LinkedHashSet<String> ids = new LinkedHashSet<>();

        try {
            for (Video video : videos) {
                if (video.videoId != null && ids.add(video.videoId)) {
                    result.put(new JSONObject().put(HISTORY_TIME, now).put(HISTORY_VIDEO, video.toString()));
                }
            }

            JSONArray old = readHistory(context);
            for (int i = 0; i < old.length() && result.length() < MAX_HISTORY; i++) {
                JSONObject entry = old.optJSONObject(i);
                Video video = entry != null ? Video.fromString(entry.optString(HISTORY_VIDEO, null)) : null;
                if (video != null && video.videoId != null && ids.add(video.videoId)) {
                    result.put(entry);
                }
            }
        } catch (JSONException e) {
            return;
        }

        prefs(context).edit().putString(KEY_HISTORY, result.toString()).apply();
    }

    public static boolean hasHistory(Context context) {
        return readHistory(context).length() > 0;
    }

    /**
     * Upload history, newest first. Each video's subtitle is rewritten to "channel • picked-up time"
     * so it doesn't show the feed's frozen "N hours ago" snapshot. Never null.
     */
    public static List<Video> getHistory(Context context) {
        List<Video> result = new ArrayList<>();
        JSONArray history = readHistory(context);
        long now = System.currentTimeMillis();

        for (int i = 0; i < history.length(); i++) {
            JSONObject entry = history.optJSONObject(i);
            Video video = entry != null ? Video.fromString(entry.optString(HISTORY_VIDEO, null)) : null;
            if (video == null || video.videoId == null) {
                continue;
            }
            CharSequence time = DateUtils.getRelativeTimeSpanString(
                    entry.optLong(HISTORY_TIME, now), now, DateUtils.MINUTE_IN_MILLIS);
            String author = video.getAuthor();
            video.secondTitle = TextUtils.isEmpty(author) ? time : author + " • " + time;
            result.add(video);
        }

        return result;
    }

    private static JSONArray readHistory(Context context) {
        String raw = prefs(context).getString(KEY_HISTORY, null);
        if (!TextUtils.isEmpty(raw)) {
            try {
                return new JSONArray(raw);
            } catch (JSONException e) {
                // Corrupt entry - start over
            }
        }
        return new JSONArray();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
