package com.liskovsoft.smartyoutubetv2.mobile.update;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Downloads the update APK with the system {@link DownloadManager} instead of handing the URL to
 * a browser. Browser Custom Tabs (e.g. Brave) can leave the file as a hidden ".pending" download
 * stuck at 100% so the APK never appears. The system downloader always finalises the file and
 * shows a notification that opens the installer when tapped. Needs no extra permission.
 *
 * <p>If the download can't be enqueued, falls back to the old behaviour (open the URL).
 */
public final class ApkDownloader {
    private static final String TAG = ApkDownloader.class.getSimpleName();

    private ApkDownloader() {
    }

    public static void download(Context context, String url) {
        if (url == null) {
            return;
        }
        try {
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                throw new IllegalStateException("No DownloadManager");
            }
            String name = Uri.parse(url).getLastPathSegment();
            if (name == null || !name.toLowerCase().endsWith(".apk")) {
                name = "SmarterTube-update.apk";
            }
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                    .setTitle(name)
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            dm.enqueue(request);
            Toast.makeText(context, R.string.mobile_update_downloading, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "DownloadManager failed, opening URL instead: %s", e.getMessage());
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }
    }
}
