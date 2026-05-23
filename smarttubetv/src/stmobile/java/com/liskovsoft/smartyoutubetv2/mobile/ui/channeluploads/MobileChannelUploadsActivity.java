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
}
