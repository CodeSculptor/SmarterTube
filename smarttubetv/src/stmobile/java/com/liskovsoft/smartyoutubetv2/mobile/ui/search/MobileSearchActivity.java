package com.liskovsoft.smartyoutubetv2.mobile.ui.search;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.mobile.ui.base.MobileActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Host for the native portrait Search screen. Replaces the TV SearchTagsActivity for the
 * stmobile flavor (wired in {@link com.liskovsoft.smartyoutubetv2.mobile.ui.main.MobileApplication}).
 */
public class MobileSearchActivity extends MobileActivity {
    @Override
    protected boolean hostsMiniPlayer() {
        return true;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_search_activity);

        if (getSupportFragmentManager().findFragmentById(R.id.mobile_search_root) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.mobile_search_root, new MobileSearchFragment())
                    .commit();
        }
    }
}
