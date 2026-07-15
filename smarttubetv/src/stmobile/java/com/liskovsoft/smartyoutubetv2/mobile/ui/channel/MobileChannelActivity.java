package com.liskovsoft.smartyoutubetv2.mobile.ui.channel;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.mobile.ui.base.MobileActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Host for the native portrait Channel screen. Replaces the TV
 * {@code tv.ui.channel.ChannelActivity} for the stmobile flavor (wired in
 * {@link com.liskovsoft.smartyoutubetv2.mobile.ui.main.MobileApplication}).
 */
public class MobileChannelActivity extends MobileActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_channel_activity);

        if (getSupportFragmentManager().findFragmentById(R.id.mobile_channel_root) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.mobile_channel_root, new MobileChannelFragment())
                    .commit();
        }
    }

    // Keep ViewManager's logical stack in sync with the real back stack: when the channel is
    // genuinely left (Back), drop it so the player's startParentView() resolves to Home instead of
    // relaunching this channel (issues #33/#24) — which otherwise reappears on Back-from-player and
    // used to become the pinned window in pop-up mode. Opening a video from the channel does NOT
    // finish this activity, so it correctly stays as the player's parent in that flow. Both the
    // system-Back path (onBackPressed) and the on-screen back-button / programmatic path (finish())
    // are covered; removeTop is idempotent so calling it from both is harmless.
    @Override
    public void onBackPressed() {
        getViewManager().removeTop(this);
        super.onBackPressed();
    }

    @Override
    public void finish() {
        getViewManager().removeTop(this);
        super.finish();
    }
}
