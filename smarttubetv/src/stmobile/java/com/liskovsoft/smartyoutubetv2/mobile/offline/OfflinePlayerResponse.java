package com.liskovsoft.smartyoutubetv2.mobile.offline;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Pure parser for the innertube /player JSON used to decide whether a video can be saved as a plain
 * file, and which stream to save. Only a muxed progressive MP4 (video + audio, direct URL) is
 * accepted; SABR-only, live, unplayable and cipher-protected results are reported as not saveable.
 */
public final class OfflinePlayerResponse {
    public enum Saveability {
        SAVEABLE, LIVE, UNPLAYABLE, NO_DIRECT_STREAM
    }

    public String status;
    public String reason;
    public String videoId;
    public String title;
    public String author;
    public long durationSec;
    public boolean live;
    public String thumbnailUrl;

    // Chosen progressive stream (null when none)
    public String url;
    public int itag;
    public String mimeType;
    public String qualityLabel;
    public int height;
    /** Content length in bytes, or -1 when the response did not include it. */
    public long contentLength = -1;

    public static OfflinePlayerResponse parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        OfflinePlayerResponse r = new OfflinePlayerResponse();

        JSONObject ps = root.optJSONObject("playabilityStatus");
        r.status = ps != null ? ps.optString("status", "") : "";
        r.reason = ps != null ? ps.optString("reason", "") : "";

        JSONObject vd = root.optJSONObject("videoDetails");
        if (vd != null) {
            r.videoId = vd.optString("videoId", null);
            r.title = vd.optString("title", null);
            r.author = vd.optString("author", null);
            r.durationSec = parseLong(vd.optString("lengthSeconds", "0"), 0);
            r.live = vd.optBoolean("isLive", false) || vd.optBoolean("isUpcoming", false)
                    || (vd.optBoolean("isLiveContent", false) && r.durationSec == 0);
            JSONObject thumb = vd.optJSONObject("thumbnail");
            JSONArray thumbs = thumb != null ? thumb.optJSONArray("thumbnails") : null;
            if (thumbs != null && thumbs.length() > 0) {
                r.thumbnailUrl = thumbs.optJSONObject(thumbs.length() - 1).optString("url", null);
            }
        }

        JSONObject sd = root.optJSONObject("streamingData");
        JSONArray formats = sd != null ? sd.optJSONArray("formats") : null;
        if (formats != null) {
            for (int i = 0; i < formats.length(); i++) {
                JSONObject f = formats.optJSONObject(i);
                if (f == null) continue;
                String url = f.optString("url", "");
                String mime = f.optString("mimeType", "");
                // Direct URL only (no signatureCipher), video container with an audio codec.
                if (url.isEmpty() || !mime.startsWith("video/mp4") || !mime.contains("mp4a")) continue;
                int height = f.optInt("height", 0);
                if (r.url == null || height > r.height) {
                    r.url = url;
                    r.itag = f.optInt("itag", 0);
                    r.mimeType = mime;
                    r.height = height;
                    r.qualityLabel = f.optString("qualityLabel", height > 0 ? height + "p" : "");
                    r.contentLength = parseLong(f.optString("contentLength", ""), -1);
                }
            }
        }
        return r;
    }

    public Saveability saveability() {
        if (live) return Saveability.LIVE;
        if (!"OK".equals(status)) return Saveability.UNPLAYABLE;
        if (url == null) return Saveability.NO_DIRECT_STREAM;
        return Saveability.SAVEABLE;
    }

    private static long parseLong(String s, long fallback) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
