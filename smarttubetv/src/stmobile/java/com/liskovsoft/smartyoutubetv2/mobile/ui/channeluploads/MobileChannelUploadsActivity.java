package com.liskovsoft.smartyoutubetv2.mobile.ui.channeluploads;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.mobile.ui.base.MobileActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Host for the native portrait Channel-uploads screen. Replaces the TV
 * {@code tv.ui.channeluploads.ChannelUploadsActivity} for the stmobile flavor.
 */
public class MobileChannelUploadsActivity extends MobileActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_channel_uploads_activity);

        if (getSupportFragmentManager().findFragmentById(R.id.mobile_channel_uploads_root) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.mobile_channel_uploads_root, new MobileChannelUploadsFragment())
                    .commit();
        }
    }

    // Keep ViewManager's logical stack in sync with the real back stack: when this screen is
    // genuinely left (Back), drop it so the player's startParentView() resolves to Home instead of
    // relaunching it (issues #33/#24). Opening a video from here does NOT close this activity, so it
    // correctly stays as the player's parent in that flow. Covered on both the system-Back path
    // (onBackPressed) and the on-screen back-button / programmatic path (finish()).
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
