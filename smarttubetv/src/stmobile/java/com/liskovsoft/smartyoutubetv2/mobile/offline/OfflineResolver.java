package com.liskovsoft.smartyoutubetv2.mobile.offline;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Looks up a video's direct progressive stream for saving. Deliberately independent of the
 * playback chain in MediaServiceCore (that chain is tuned for streaming and often returns SABR-only
 * results): one unauthenticated innertube /player call with the Android client, whose muxed MP4
 * (itag 18, 360p) is served as plain ranged HTTP (M0 spike, see docs/design/offline-saving.md).
 * Nothing here defeats signing, DRM or token checks: if the response has no direct URL the video is
 * simply not saveable.
 */
public final class OfflineResolver {
    // Public client constants; keep in step with MediaServiceCore's ANDROID client (CLIENTS.ANDROID).
    private static final String API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";
    private static final String CLIENT_VERSION = "21.03.36";
    private static final String USER_AGENT =
            "com.google.android.youtube/21.03.36(Linux; U; Android 16; en_US; SM-S908E Build/TP1A.220624.014) gzip";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient mClient;

    public OfflineResolver(OkHttpClient client) {
        mClient = client;
    }

    public static OkHttpClient newClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    static String requestBody(String videoId) {
        // videoId is validated by OfflineStore.sanitize before it gets here.
        return "{\"context\":{\"client\":{\"clientName\":\"ANDROID\",\"clientVersion\":\"" + CLIENT_VERSION
                + "\",\"androidSdkVersion\":36,\"osName\":\"Android\",\"osVersion\":\"16\","
                + "\"hl\":\"en\",\"gl\":\"US\",\"userAgent\":\"" + USER_AGENT + "\"}},"
                + "\"videoId\":\"" + videoId + "\",\"racyCheckOk\":true,\"contentCheckOk\":true}";
    }

    public OfflinePlayerResponse resolve(String videoId) throws IOException {
        OfflineStore.sanitize(videoId);
        Request request = new Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?key=" + API_KEY + "&prettyPrint=false")
                .header("User-Agent", USER_AGENT)
                .header("X-YouTube-Client-Name", "3")
                .header("X-YouTube-Client-Version", CLIENT_VERSION)
                .post(RequestBody.create(JSON, requestBody(videoId)))
                .build();
        Response response = mClient.newCall(request).execute();
        try {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("player HTTP " + response.code());
            }
            return OfflinePlayerResponse.parse(response.body().string());
        } catch (org.json.JSONException e) {
            throw new IOException("bad player response", e);
        } finally {
            response.close();
        }
    }

    public static String userAgent() {
        return USER_AGENT;
    }
}
