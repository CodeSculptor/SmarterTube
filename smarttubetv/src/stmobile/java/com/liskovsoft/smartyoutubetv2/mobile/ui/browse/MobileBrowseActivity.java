package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.mobile.ui.base.MobileActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Host for the native phone Home screen. Replaces the TV BrowseActivity for the
 * stmobile flavor (wired in {@link com.liskovsoft.smartyoutubetv2.mobile.ui.main.MobileApplication}).
 */
public class MobileBrowseActivity extends MobileActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_browse_activity);

        if (getSupportFragmentManager().findFragmentById(R.id.mobile_browse_root) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.mobile_browse_root, new MobileBrowseFragment())
                    .commit();
        }
    }
}
