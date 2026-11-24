package com.liskovsoft.smartyoutubetv2.mobile.offline;

import org.json.JSONException;
import org.json.JSONObject;

/** One saved (or saving) video: the content of its {@code meta.json}. */
public final class SavedVideo {
    public static final String QUEUED = "QUEUED";
    public static final String DOWNLOADING = "DOWNLOADING";
    public static final String PAUSED = "PAUSED";
    public static final String FAILED = "FAILED";
    public static final String DONE = "DONE";

    public String videoId;
    public String title;
    public String author;
    public long durationSec;
    public String state = QUEUED;
    public String reason;
    public long totalBytes = -1;
    public int itag;
    public String qualityLabel;
    public long createdAt;
    public long positionMs;

    public boolean isDone() {
        return DONE.equals(state);
    }

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("videoId", videoId);
            o.put("title", title);
            o.put("author", author);
            o.put("durationSec", durationSec);
            o.put("state", state);
            o.put("reason", reason);
            o.put("totalBytes", totalBytes);
            o.put("itag", itag);
            o.put("qualityLabel", qualityLabel);
            o.put("createdAt", createdAt);
            o.put("positionMs", positionMs);
            return o.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public static SavedVideo fromJson(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        SavedVideo v = new SavedVideo();
        v.videoId = o.optString("videoId", null);
        v.title = o.isNull("title") ? null : o.optString("title", null);
        v.author = o.isNull("author") ? null : o.optString("author", null);
        v.durationSec = o.optLong("durationSec", 0);
        v.state = o.optString("state", QUEUED);
        v.reason = o.isNull("reason") ? null : o.optString("reason", null);
        v.totalBytes = o.optLong("totalBytes", -1);
        v.itag = o.optInt("itag", 0);
        v.qualityLabel = o.isNull("qualityLabel") ? null : o.optString("qualityLabel", null);
        v.createdAt = o.optLong("createdAt", 0);
        v.positionMs = o.optLong("positionMs", 0);
        if (v.videoId == null || v.videoId.isEmpty()) throw new JSONException("no videoId");
        return v;
    }
}
