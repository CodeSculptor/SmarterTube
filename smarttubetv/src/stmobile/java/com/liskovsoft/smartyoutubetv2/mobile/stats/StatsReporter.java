package com.liskovsoft.smartyoutubetv2.mobile.stats;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.tv.BuildConfig;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Opt-in anonymous stats for the phone build: a once-a-day usage heartbeat ({@link Heartbeat}) and
 * message-free crash reports ({@link CrashReport}), both POSTed to the fork's own stats Worker
 * ({@code stats-worker/} in the repo).
 *
 * <ul>
 *   <li>Nothing is sent unless the user opted in ({@link StatsPrefs.Consent#ON}), except a crash
 *       report the user explicitly agrees to send from the per-crash prompt.</li>
 *   <li>No install id, account, video, channel or search data is ever sent.</li>
 *   <li>Uses a bare OkHttp client — not the app's shared YouTube client — so no cookies, auth
 *       headers or interceptors can leak into these requests.</li>
 *   <li>If {@code R.string.mobile_stats_endpoint} is empty the whole feature is dormant: no prompt,
 *       no setting effect, no network.</li>
 * </ul>
 */
public final class StatsReporter {
    private static final String TAG = StatsReporter.class.getSimpleName();
    private static final String PENDING_CRASH_FILE = "pending_crash.json";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();
    private static OkHttpClient sClient;
    private static boolean sHeartbeatInFlight;

    private StatsReporter() {}

    public static boolean isConfigured(Context context) {
        return !TextUtils.isEmpty(endpoint(context));
    }

    /**
     * Wraps the current default uncaught-exception handler so a crash is saved locally (never sent
     * from the crashing process). Call BEFORE upstream installs its own handler, so upstream's
     * "ignored" exceptions — which it swallows without crashing — never reach this one.
     * Not installed at all while the feature is dormant (no endpoint), so nothing is even saved.
     */
    public static void installCrashHandler(Context context) {
        if (!isConfigured(context)) {
            return;
        }
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            try {
                writePending(app, CrashReport.toJson(e, BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT, device()));
            } catch (Throwable ignored) {
                // Never let reporting interfere with the crash itself.
            }
            if (previous != null) {
                previous.uncaughtException(thread, e);
            }
        });
    }

    /** Called whenever an activity starts. Cheap no-op unless opted in and something is due. */
    public static void onForeground(Context context) {
        if (!isConfigured(context) || !StatsPrefs.isEnabled(context)) {
            return;
        }
        sendHeartbeatIfDue(context);
        if (hasPendingCrash(context)) {
            sendPendingCrash(context);
        }
    }

    public static boolean hasPendingCrash(Context context) {
        return pendingFile(context).exists();
    }

    /** Sends the saved crash report (if any) and deletes it on success. */
    public static void sendPendingCrash(Context context) {
        final Context app = context.getApplicationContext();
        final String url = endpoint(app);
        if (TextUtils.isEmpty(url)) {
            return;
        }
        sExecutor.execute(() -> {
            File file = pendingFile(app);
            String body = readFile(file);
            if (body == null) {
                return;
            }
            if (post(url + "/crash", body)) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        });
    }

    public static void discardPendingCrash(Context context) {
        //noinspection ResultOfMethodCallIgnored
        pendingFile(context).delete();
    }

    private static synchronized void sendHeartbeatIfDue(Context context) {
        if (sHeartbeatInFlight) {
            return;
        }
        final Context app = context.getApplicationContext();
        final Heartbeat.Ping ping = Heartbeat.due(StatsPrefs.getHeartbeatState(app), System.currentTimeMillis());
        if (ping == null) {
            return;
        }
        sHeartbeatInFlight = true;
        final String url = endpoint(app);
        sExecutor.execute(() -> {
            try {
                if (post(url + "/ping", ping.toJson(BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT))) {
                    // Only mark the day as sent once the server has it, so a failed send retries later.
                    StatsPrefs.saveHeartbeatState(app, ping.nextState);
                }
            } finally {
                synchronized (StatsReporter.class) {
                    sHeartbeatInFlight = false;
                }
            }
        });
    }

    private static boolean post(String url, String json) {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "SmarterTube")
                .post(RequestBody.create(JSON, json))
                .build();
        try (Response response = client().newCall(request).execute()) {
            return response.isSuccessful();
        } catch (Exception e) {
            Log.d(TAG, "Stats send failed: %s", e.getClass().getSimpleName());
            return false;
        }
    }

    private static synchronized OkHttpClient client() {
        if (sClient == null) {
            sClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();
        }
        return sClient;
    }

    private static String endpoint(Context context) {
        String url = context.getString(R.string.mobile_stats_endpoint).trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String device() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    private static File pendingFile(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), PENDING_CRASH_FILE);
    }

    private static void writePending(Context context, String json) throws IOException {
        try (OutputStream out = new FileOutputStream(pendingFile(context))) {
            out.write(json.getBytes(UTF8));
        }
    }

    private static String readFile(File file) {
        if (!file.exists()) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) Math.min(file.length(), 64 * 1024)];
            int read = 0;
            while (read < buf.length) {
                int n = in.read(buf, read, buf.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return new String(buf, 0, read, UTF8);
        } catch (IOException e) {
            return null;
        }
    }
}
