package com.liskovsoft.smartyoutubetv2.mobile.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.mobile.ui.prefs.MobileNotificationPrefs;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.disposables.Disposable;

/**
 * Periodic background poll that turns new subscription uploads into push notifications (Part 2).
 *
 * Reads {@code ContentService.getSubscriptionsObserve()} — the subscriptions video feed, which
 * has a built-in RSS-over-all-subscribed-channels fallback, so it survives the broken notifications
 * inbox endpoint and needs no notification-bell setup. New video ids (vs {@link MobileNotificationPrefs}'s
 * seen set) are posted via {@link UploadNotifier}. On the first-ever run the feed is seeded silently
 * so the existing backlog isn't dumped as alerts.
 *
 * stmobile-only; calls the YouTube service layer directly (smarttubetv already depends on
 * {@code :youtubeapi} / {@code :mediaserviceinterfaces}), so shared {@code common} stays untouched.
 */
public class NotificationPollWorker extends Worker {
    private static final String TAG = NotificationPollWorker.class.getSimpleName();
    private static final String WORK_NAME = "stmobile_upload_notifications";
    private static final long REPEAT_MINUTES = 15; // WorkManager periodic minimum
    private static final long FETCH_TIMEOUT_SECONDS = 60;
    /** How much of the current feed to put in the Notifications tab on the first run, so it isn't empty. */
    private static final int SEED_HISTORY_COUNT = 30;

    public NotificationPollWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Enqueue the periodic poll if the feature is enabled; cancel it otherwise. Idempotent. */
    public static void schedule(Context context) {
        WorkManager workManager = WorkManager.getInstance(context);

        if (!MobileNotificationPrefs.isEnabled(context)) {
            workManager.cancelUniqueWork(WORK_NAME);
            return;
        }

        workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // don't reset the timer on every app start
                new PeriodicWorkRequest.Builder(NotificationPollWorker.class, REPEAT_MINUTES, TimeUnit.MINUTES)
                        .addTag(WORK_NAME)
                        .build());
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        if (!MobileNotificationPrefs.isEnabled(context)) {
            return Result.success();
        }

        ServiceManager service = YouTubeServiceManager.instance();
        if (service.getSignInService() == null || !service.getSignInService().isSigned()) {
            Log.d(TAG, "Not signed in - skipping upload poll");
            return Result.success();
        }

        try {
            MediaGroup group = fetchSubscriptions(service.getContentService());
            List<MediaItem> items = group != null ? group.getMediaItems() : null;
            int count = items != null ? items.size() : 0;
            Log.d(TAG, "Subscriptions feed returned %d item(s)", count);

            if (items == null || items.isEmpty()) {
                return Result.success();
            }

            // Collect current ids (preserve feed order = newest first).
            List<String> currentIds = new ArrayList<>();
            for (MediaItem item : items) {
                if (item.getVideoId() != null) {
                    currentIds.add(item.getVideoId());
                }
            }

            LinkedHashSet<String> seen = MobileNotificationPrefs.getSeenIds(context);

            if (seen.isEmpty() || !MobileNotificationPrefs.hasHistory(context)) {
                // Fill the Notifications tab from the current feed (also covers installs that
                // seeded before the tab was backed by this history).
                MobileNotificationPrefs.addToHistory(context,
                        toVideos(items.subList(0, Math.min(items.size(), SEED_HISTORY_COUNT))));
            }

            if (seen.isEmpty()) {
                // First run: record the current state without alerting on the backlog.
                MobileNotificationPrefs.saveSeenIds(context, currentIds, null);
                Log.d(TAG, "First run - seeded %d id(s), no notifications", currentIds.size());
                return Result.success();
            }

            List<MediaItem> newItems = new ArrayList<>();
            for (MediaItem item : items) {
                if (item.getVideoId() != null && !seen.contains(item.getVideoId())) {
                    newItems.add(item);
                }
            }

            if (!newItems.isEmpty()) {
                Log.d(TAG, "Posting %d new upload notification(s)", newItems.size());
                UploadNotifier.notifyNewUploads(context, newItems);
                MobileNotificationPrefs.addToHistory(context, toVideos(newItems));
            }

            // Persist: newest current ids first, then previously-seen, capped.
            MobileNotificationPrefs.saveSeenIds(context, currentIds, seen);

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Upload poll failed: %s", e.getMessage());
            e.printStackTrace();
            return Result.success(); // periodic; just try again next cycle
        }
    }

    private static List<Video> toVideos(List<MediaItem> items) {
        List<Video> result = new ArrayList<>();
        for (MediaItem item : items) {
            if (item.getVideoId() != null) {
                result.add(Video.from(item));
            }
        }
        return result;
    }

    /**
     * Read the subscriptions feed synchronously. The RxHelper observables do network on an IO
     * scheduler and deliver on the main thread, so we block this worker thread on a latch rather
     * than {@code blockingFirst()} (which would fight the main-thread {@code observeOn}).
     */
    private MediaGroup fetchSubscriptions(ContentService contentService) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<MediaGroup> result = new AtomicReference<>();

        Disposable action = RxHelper.execute(
                contentService.getSubscriptionsObserve(),
                result::set,
                error -> latch.countDown(),
                latch::countDown);

        boolean completed = latch.await(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed && action != null && !action.isDisposed()) {
            action.dispose();
        }

        return result.get();
    }
}
