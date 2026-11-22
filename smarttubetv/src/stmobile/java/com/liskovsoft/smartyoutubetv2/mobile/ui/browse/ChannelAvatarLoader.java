package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.mobile.ui.prefs.MobileCardAvatarPrefs;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.reactivex.disposables.Disposable;

/**
 * Resolves channel avatar URLs for feed cards. The feed payload has none, so each channel costs one
 * metadata call (using one of its videos). Calls use an independent {@code getMetadataObserve}
 * subscription, NOT the shared MediaServiceManager singleton (its single disposable gets cancelled
 * by the player's own loads).
 *
 * Cache: memory LRU + persisted channel-id to url map (the image bytes themselves are cached by
 * Glide), so a warm start makes no network calls. In-flight requests are de-duplicated per channel,
 * at most {@link #MAX_CONCURRENT} run at once, requests start after a short delay and are dropped if
 * every card waiting on them was recycled (fast scrolling), and failures are remembered for the
 * session so a failing channel is not retried on every bind.
 * Main-thread only.
 */
final class ChannelAvatarLoader {
    interface Callback {
        void onAvatar(String key, String url);
    }

    /**
     * Cache/dedupe key. Standard feed items often carry no channel id (only the author name), so
     * fall back to the author; the metadata call then supplies the real channel id for taps.
     */
    static String keyOf(Video video) {
        if (video.channelId != null && !video.channelId.isEmpty()) {
            return video.channelId;
        }
        String author = video.getAuthor();
        return author != null && !author.isEmpty() ? "a:" + author : null;
    }

    /** Real channel id resolved for a key, or null. */
    String channelIdFor(Context context, String key) {
        if (!key.startsWith("a:")) {
            return key;
        }
        String id = mChannelIds.get(key);
        return id != null ? id : MobileCardAvatarPrefs.getUrl(context, "id_" + key);
    }

    private static final int MAX_CONCURRENT = 2;
    private static final long START_DELAY_MS = 300;
    private static final ChannelAvatarLoader INSTANCE = new ChannelAvatarLoader();

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, String> mUrls = new LruCache<>(500);
    private final Map<String, String> mChannelIds = new HashMap<>();
    private final Set<String> mFailed = new HashSet<>();
    private final Map<String, List<Callback>> mWaiting = new HashMap<>();
    private final ArrayDeque<Video> mQueue = new ArrayDeque<>();
    private final Set<String> mRunning = new HashSet<>();
    private final List<Disposable> mActions = new ArrayList<>();

    static ChannelAvatarLoader instance() {
        return INSTANCE;
    }

    private ChannelAvatarLoader() {}

    /** Cached url, or null (also checks the persisted map). */
    String peek(Context context, String channelId) {
        String url = mUrls.get(channelId);
        if (url == null) {
            url = MobileCardAvatarPrefs.getUrl(context, channelId);
            if (url != null) {
                mUrls.put(channelId, url);
            }
        }
        return url;
    }

    /** Ask for a channel's avatar; {@code cb} is called on the main thread if/when it resolves. */
    void request(Context context, Video video, Callback cb) {
        String channelId = keyOf(video);
        if (channelId == null || video.videoId == null || mFailed.contains(channelId)) {
            return;
        }
        String cached = peek(context, channelId);
        if (cached != null) {
            cb.onAvatar(channelId, cached);
            return;
        }
        List<Callback> list = mWaiting.get(channelId);
        if (list != null) {
            list.add(cb); // already queued or in flight
            return;
        }
        list = new ArrayList<>();
        list.add(cb);
        mWaiting.put(channelId, list);
        mQueue.add(video);
        mHandler.postDelayed(() -> pump(context.getApplicationContext()), START_DELAY_MS);
    }

    /** Card recycled: stop waiting. Queued channels nobody waits on any more are skipped. */
    void cancel(Callback cb) {
        for (List<Callback> list : mWaiting.values()) {
            list.remove(cb);
        }
    }

    private void pump(Context context) {
        while (mRunning.size() < MAX_CONCURRENT && !mQueue.isEmpty()) {
            Video video = mQueue.pollLast(); // newest first: what is on screen now
            String channelId = keyOf(video);
            List<Callback> list = mWaiting.get(channelId);
            if (list == null || list.isEmpty()) {
                mWaiting.remove(channelId); // scrolled past; may be re-requested later
                continue;
            }
            MediaItemService service = YouTubeServiceManager.instance().getMediaItemService();
            if (service == null) {
                finish(context, channelId, null);
                continue;
            }
            mRunning.add(channelId);
            Disposable[] holder = new Disposable[1];
            holder[0] = RxHelper.execute(
                    video.mediaItem != null
                            ? service.getMetadataObserve(video.mediaItem)
                            : service.getMetadataObserve(video.videoId, video.getPlaylistId(),
                                    video.playlistIndex, video.playlistParams),
                    (MediaItemMetadata metadata) -> {
                        mActions.remove(holder[0]);
                        if (metadata != null && metadata.getChannelId() != null) {
                            mChannelIds.put(channelId, metadata.getChannelId());
                            MobileCardAvatarPrefs.putUrl(context, "id_" + channelId, metadata.getChannelId());
                        }
                        finish(context, channelId, metadata != null ? metadata.getAuthorImageUrl() : null);
                    },
                    error -> {
                        mActions.remove(holder[0]);
                        finish(context, channelId, null);
                    });
            mActions.add(holder[0]);
        }
    }

    private void finish(Context context, String channelId, String url) {
        mRunning.remove(channelId);
        List<Callback> list = mWaiting.remove(channelId);
        if (url == null || url.isEmpty()) {
            mFailed.add(channelId);
        } else {
            mUrls.put(channelId, url);
            MobileCardAvatarPrefs.putUrl(context, channelId, url);
            if (list != null) {
                for (Callback cb : new ArrayList<>(list)) {
                    cb.onAvatar(channelId, url);
                }
            }
        }
        pump(context);
    }
}
