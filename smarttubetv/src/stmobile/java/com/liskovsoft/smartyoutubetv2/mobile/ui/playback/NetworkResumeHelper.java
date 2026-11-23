package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.mobile.ui.playback.NetworkResumePolicy.Action;

/**
 * Resumes playback from the same position once the network returns after a drop (wifi lost,
 * tunnel, airplane toggle). Owned by the playback fragment: started in onStart, stopped in
 * onDestroy (NOT onStop, so background audio and the mini-player keep recovering while the
 * activity is stopped; every decision re-checks that the engine is alive and wants to play).
 * Decisions live in {@link NetworkResumePolicy}.
 *
 * Does not touch upstream's error handling: it observes connectivity only, so non-network errors
 * can never start a retry loop. Everything runs on the main thread.
 */
final class NetworkResumeHelper {
    private static final String TAG = NetworkResumeHelper.class.getSimpleName();
    /** Ignore sub-second drops, e.g. a wifi to cellular handover. */
    private static final long LOSS_DEBOUNCE_MS = 1_500;

    interface Host {
        Context getContext();
        boolean isEngineActive();
        boolean getPlayWhenReady();
        boolean isPlaying();
        /** Restart the engine; position is preserved by the engine restart. */
        void restartEngine();
        void showHint(int stringResId);
    }

    private final Host mHost;
    private final NetworkResumePolicy mPolicy = new NetworkResumePolicy();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private ConnectivityManager mManager;
    private ConnectivityManager.NetworkCallback mCallback;
    private BroadcastReceiver mReceiver; // API < 21
    private boolean mNetworkUp = true;
    private boolean mStarted;

    private final Runnable mLossRunnable = this::handleLoss;
    private final Runnable mCheckRunnable = this::handleCheck;

    NetworkResumeHelper(Host host) {
        mHost = host;
    }

    void start() {
        if (mStarted) {
            return;
        }
        Context context = mHost.getContext();
        if (context == null) {
            return;
        }
        try {
            mManager = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (mManager == null) {
                return;
            }
            mNetworkUp = isOnline();
            if (VERSION.SDK_INT >= 24) {
                mCallback = new Callback();
                mManager.registerDefaultNetworkCallback(mCallback);
            } else if (VERSION.SDK_INT >= 21) {
                mCallback = new Callback();
                mManager.registerNetworkCallback(new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), mCallback);
            } else {
                mReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context c, Intent intent) {
                        onConnectivityChanged(isOnline());
                    }
                };
                context.getApplicationContext().registerReceiver(mReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
            }
            mStarted = true;
        } catch (RuntimeException e) { // missing permission / OEM quirks: feature simply stays off
            Log.e(TAG, "Network resume disabled: %s", e.getMessage());
            mCallback = null;
            mReceiver = null;
        }
    }

    void stop() {
        mHandler.removeCallbacks(mLossRunnable);
        mHandler.removeCallbacks(mCheckRunnable);
        mPolicy.reset();
        if (!mStarted) {
            return;
        }
        mStarted = false;
        try {
            if (mCallback != null && mManager != null) {
                mManager.unregisterNetworkCallback(mCallback);
            }
            Context context = mHost.getContext();
            if (mReceiver != null && context != null) {
                context.getApplicationContext().unregisterReceiver(mReceiver);
            }
        } catch (RuntimeException e) {
            // already unregistered
        }
        mCallback = null;
        mReceiver = null;
    }

    private boolean isOnline() {
        NetworkInfo info = mManager != null ? mManager.getActiveNetworkInfo() : null;
        return info != null && info.isConnected();
    }

    /** Any thread. */
    private void onConnectivityChanged(boolean up) {
        mHandler.post(() -> {
            if (up == mNetworkUp) {
                return;
            }
            mNetworkUp = up;
            if (up) {
                mHandler.removeCallbacks(mLossRunnable); // blip: never became an outage
                if (mPolicy.onNetworkAvailable() == Action.SCHEDULE_CHECK) {
                    schedule(NetworkResumePolicy.GRACE_MS);
                }
            } else {
                mHandler.removeCallbacks(mCheckRunnable);
                mHandler.postDelayed(mLossRunnable, LOSS_DEBOUNCE_MS);
            }
        });
    }

    private void handleLoss() {
        if (mNetworkUp) {
            return;
        }
        if (mPolicy.onNetworkLost(mHost.isEngineActive(), engineWantsPlay()) == Action.SHOW_WAITING) {
            mHost.showHint(R.string.mobile_net_waiting);
        }
    }

    private void handleCheck() {
        boolean active = mHost.isEngineActive();
        Action action = mPolicy.onCheck(active, active && mHost.getPlayWhenReady(), active && mHost.isPlaying(), mNetworkUp);
        switch (action) {
            case SHOW_RECONNECTED:
                mHost.showHint(R.string.mobile_net_reconnected);
                break;
            case RESTART:
                Log.d(TAG, "Network is back, restarting the engine (attempt %s)", mPolicy.getAttempts());
                mHost.restartEngine();
                schedule(NetworkResumePolicy.backoffMs(mPolicy.getAttempts() - 1));
                break;
            case GIVE_UP:
                mHost.showHint(R.string.mobile_net_gave_up);
                break;
            default:
                break;
        }
    }

    private boolean engineWantsPlay() {
        return mHost.isEngineActive() && mHost.getPlayWhenReady();
    }

    private void schedule(long delayMs) {
        mHandler.removeCallbacks(mCheckRunnable);
        mHandler.postDelayed(mCheckRunnable, delayMs);
    }

    private final class Callback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(Network network) {
            onConnectivityChanged(true);
        }

        @Override
        public void onLost(Network network) {
            // API 24+: default-network callback, so a loss means no connectivity (a handover is
            // followed by onAvailable and absorbed by the debounce). API 21-23: any network
            // may drop while another stays up, so ask the manager.
            onConnectivityChanged(VERSION.SDK_INT >= 24 ? false : isOnline());
        }
    }
}
