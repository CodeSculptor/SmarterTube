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
}
