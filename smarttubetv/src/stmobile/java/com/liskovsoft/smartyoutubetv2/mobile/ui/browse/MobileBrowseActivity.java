package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.content.res.Resources;
import android.os.Bundle;
import android.util.DisplayMetrics;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Host for the native phone Home screen. Replaces the TV BrowseActivity for the
 * stmobile flavor (wired in {@link com.liskovsoft.smartyoutubetv2.mobile.ui.main.MobileApplication}).
 */
public class MobileBrowseActivity extends MotherActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        restoreRealDensity();
        setContentView(R.layout.mobile_browse_activity);

        if (getSupportFragmentManager().findFragmentById(R.id.mobile_browse_root) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.mobile_browse_root, new MobileBrowseFragment())
                    .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        restoreRealDensity();
    }

    @Override
    protected void initTheme() {
        // MotherActivity.initTheme() applies a Leanback TV theme; the phone build needs an
        // AppCompat theme so DrawerLayout and other touch widgets render correctly.
        setTheme(R.style.Theme_SmarterTube_Mobile);
    }

    /**
     * MotherActivity scales the display density for 10-foot TV layouts. Undo that here so
     * dp/sp values on the phone Home screen render at the device's true density.
     */
    private void restoreRealDensity() {
        DisplayMetrics real = Resources.getSystem().getDisplayMetrics();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        dm.density = real.density;
        dm.scaledDensity = real.scaledDensity;
        dm.densityDpi = real.densityDpi;
    }
}
