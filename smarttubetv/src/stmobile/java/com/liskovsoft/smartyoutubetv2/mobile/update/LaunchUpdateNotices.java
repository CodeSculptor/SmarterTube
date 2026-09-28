package com.liskovsoft.smartyoutubetv2.mobile.update;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.text.HtmlCompat;

import com.google.android.material.snackbar.Snackbar;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.tv.BuildConfig;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Update notices shown when the phone Home screen opens:
 * <ul>
 *   <li><b>Update available</b>: a Snackbar when a newer SmarterTube release exists (same
 *   scheme-aware check as About &gt; Check for updates, {@link MobileUpdateChecker}). "View" opens
 *   that release's "What's new" with a Download APK button.</li>
 *   <li><b>What's new</b>: a one-time dialog after the app is updated, showing the installed
 *   release's "What's new" section from its GitHub release notes ({@link ReleaseNotes}). Fresh
 *   installs don't get it.</li>
 * </ul>
 * Both come from one GitHub {@code /releases} request. It runs at most once per process, and only
 * when the "What's new" popup is still owed or {@link #CHECK_INTERVAL_MS} has passed since the
 * last successful check (keeps us well inside GitHub's unauthenticated rate limit). Failures are
 * silent; an owed popup is retried on the next launch.
 */
public final class LaunchUpdateNotices {
    private static final String TAG = LaunchUpdateNotices.class.getSimpleName();
    private static final String PREFS_NAME = "mobile_update_notice_prefs";
    private static final String KEY_LAST_SEEN_VERSION = "last_seen_version";
    private static final String KEY_LAST_CHECK_MS = "last_check_ms";
    private static final long CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L;
    private static final int SNACKBAR_DURATION_MS = 15_000;

    private static boolean sRanThisProcess;

    private LaunchUpdateNotices() {
    }

    public static void onHomeCreated(Activity activity) {
        if (sRanThisProcess) {
            return;
        }
        sRanThisProcess = true;

        SharedPreferences prefs = prefs(activity);
        String current = BuildConfig.VERSION_NAME;
        String lastSeen = prefs.getString(KEY_LAST_SEEN_VERSION, null);
        if (lastSeen == null && isFreshInstall(activity)) {
            // Nothing to announce to someone who just installed this version.
            prefs.edit().putString(KEY_LAST_SEEN_VERSION, current).apply();
            lastSeen = current;
        }
        final boolean whatsNewOwed = !current.equals(lastSeen);
        long sinceLastCheck = System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK_MS, 0);
        if (!whatsNewOwed && sinceLastCheck >= 0 && sinceLastCheck < CHECK_INTERVAL_MS) {
            return;
        }

        MobileUpdateChecker.check(activity.getString(R.string.mobile_about_url_releases_api), current,
                result -> {
                    if (result.status == MobileUpdateChecker.Status.ERROR) {
                        return; // offline etc. - try again next launch
                    }
                    prefs.edit().putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis()).apply();
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return; // the popup stays owed
                    }
                    boolean hasUpdate = result.status == MobileUpdateChecker.Status.UPDATE_AVAILABLE
                            || result.status == MobileUpdateChecker.Status.NO_COMPATIBLE_ASSET;
                    Runnable updateNotice = hasUpdate ? () -> showUpdateSnackbar(activity, result) : null;

                    if (whatsNewOwed) {
                        prefs.edit().putString(KEY_LAST_SEEN_VERSION, current).apply();
                        if (showWhatsNew(activity, result.installedRelease, updateNotice)) {
                            return; // the update notice follows the dialog
                        }
                    }
                    if (updateNotice != null) {
                        updateNotice.run();
                    }
                });
    }

    /** @return whether a dialog was shown ({@code then} runs when it closes). */
    private static boolean showWhatsNew(Activity activity, @Nullable MobileUpdateChecker.ReleaseInfo release,
                                        @Nullable Runnable then) {
        String html = release != null ? ReleaseNotes.whatsNewHtml(release.body) : null;
        if (html == null) {
            return false; // dev build, or notes without a "What's new" section
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.mobile_whats_new_title, ReleaseNotes.displayTag(release.tag)))
                .setMessage(HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY))
                .setPositiveButton(android.R.string.ok, null);
        if (release.htmlUrl != null) {
            builder.setNeutralButton(R.string.mobile_whats_new_full_notes, (d, w) -> openUrl(activity, release.htmlUrl));
        }
        builder.setOnDismissListener(d -> {
            if (then != null && !activity.isFinishing()) {
                then.run();
            }
        });
        builder.show();
        return true;
    }

    private static void showUpdateSnackbar(Activity activity, MobileUpdateChecker.Result result) {
        View root = activity.findViewById(android.R.id.content);
        if (root == null) {
            return;
        }
        Snackbar.make(root, activity.getString(R.string.mobile_update_notice,
                        ReleaseNotes.displayTag(result.latestTag)), SNACKBAR_DURATION_MS)
                .setAction(R.string.mobile_update_notice_view, v -> showUpdateDetails(activity, result))
                .show();
    }

    private static void showUpdateDetails(Activity activity, MobileUpdateChecker.Result result) {
        String html = ReleaseNotes.whatsNewHtml(result.latestNotes);
        String releaseUrl = result.releaseUrl != null
                ? result.releaseUrl : activity.getString(R.string.mobile_about_url_releases);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.mobile_update_details_title,
                        ReleaseNotes.displayTag(result.latestTag)))
                .setMessage(html != null
                        ? HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
                        : activity.getString(R.string.mobile_update_details_no_notes))
                .setNegativeButton(R.string.mobile_update_later, null);
        if (result.assetUrl != null) {
            // Two buttons only: a third stacks them vertically on phones. About links the releases page.
            builder.setPositiveButton(R.string.mobile_about_download, (d, w) -> openUrl(activity, result.assetUrl));
        } else {
            builder.setPositiveButton(R.string.mobile_about_open_releases, (d, w) -> openUrl(activity, releaseUrl));
        }
        builder.show();
    }

    /** First launch of a fresh install (not an update over an earlier version). */
    private static boolean isFreshInstall(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.firstInstallTime == info.lastUpdateTime;
        } catch (Exception e) {
            Log.e(TAG, "Can't read install times: %s", e.getMessage());
            return true; // don't risk a stray popup
        }
    }

    private static void openUrl(Context context, String url) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
