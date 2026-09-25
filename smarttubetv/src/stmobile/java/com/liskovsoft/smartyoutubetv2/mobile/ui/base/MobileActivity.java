package com.liskovsoft.smartyoutubetv2.mobile.ui.base;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.DisplayMetrics;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.prefs.MobileThemePrefs;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Base for phone (stmobile) activities.
 *
 * Applies the AppCompat phone theme instead of MotherActivity's Leanback TV theme, and
 * restores the device's true display density (MotherActivity scales density for 10-foot
 * TV layouts, which makes everything tiny on a phone).
 *
 * Also forces the activity's {@link Configuration} uiMode bits to match the in-app
 * Theme pref. MotherActivity extends {@link androidx.fragment.app.FragmentActivity}
 * (not AppCompatActivity), so {@code AppCompatDelegate.setDefaultNightMode} does NOT
 * automatically drive uiMode/recreate here - we have to do it ourselves via
 * {@link #attachBaseContext}. SYSTEM leaves the inherited uiMode untouched (so the
 * system Settings -> Display -> Dark mode toggle still drives the app), LIGHT/DARK
 * force the corresponding bit.
 */
public abstract class MobileActivity extends MotherActivity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(applyThemeOverride(newBase));
    }

    private Context applyThemeOverride(Context base) {
        if (base == null) {
            return null;
        }
        MobileThemePrefs.Mode mode = MobileThemePrefs.getMode(base);
        if (mode == MobileThemePrefs.Mode.SYSTEM) {
            return base; // Inherit whatever uiMode the system gave us.
        }
        Configuration overrideConfig = new Configuration();
        // setTo(empty) would clobber locale/density - createConfigurationContext merges
        // an empty config with the base, so we only set the bits we want to change.
        int nightBit = (mode == MobileThemePrefs.Mode.DARK)
                ? Configuration.UI_MODE_NIGHT_YES
                : Configuration.UI_MODE_NIGHT_NO;
        // Preserve non-night uiMode bits (e.g. UI_MODE_TYPE_NORMAL) from base.
        int baseUiMode = base.getResources().getConfiguration().uiMode
                & ~Configuration.UI_MODE_NIGHT_MASK;
        overrideConfig.uiMode = baseUiMode | nightBit;
        return base.createConfigurationContext(overrideConfig);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restoreRealDensity();
        allowLandscapeOnTablets();
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // After the subclass's setContentView. A no-op unless the OS draws us edge-to-edge (#37).
        if (appliesSystemBarInsets()) {
            SystemBarInsets.apply(this);
        }
    }

    /** Whether {@link SystemBarInsets} pads this activity's content clear of the system bars. */
    protected boolean appliesSystemBarInsets() {
        return true;
    }

    /**
     * The content activities (browse/search/channel/sign-in) are locked to portrait in
     * the manifest - the right default on a phone. On a tablet (smallestWidth >= 600dp)
     * we want them to rotate so the {@code values-sw600dp-land} resources (4-column grids)
     * can apply. A runtime {@code setRequestedOrientation} overrides the manifest lock.
     *
     * Guarded on the activity actually being portrait-locked, so activities that opt into
     * {@code screenOrientation="behind"} (the settings dialog, playback) keep their own
     * orientation behaviour untouched.
     */
    private void allowLandscapeOnTablets() {
        boolean isTablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        if (isTablet && getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        restoreRealDensity();
        // Register in the ViewManager activity stack (the TV LeanbackActivity does the same).
        // Without this the stack only tracks TV/Leanback activities, so the player's
        // startParentView() can't see a phone screen as its caller and falls back to Home.
        if (registersInViewStack()) {
            getViewManager().addTop(this);
        }
    }

    /**
     * Whether this activity is a navigation destination that belongs in the ViewManager's
     * activity stack. Transient overlays (the settings dialog) return {@code false}: adding them
     * makes them a Back target, so pressing Back on the player would relaunch the just-closed
     * dialog — the player's {@code finishReally() → startParentView()} walks this very stack and
     * would pop to the dialog sitting just under it. Mirrors TV, where AppDialogActivity extends
     * MotherActivity (no addTop), not LeanbackActivity.
     */
    protected boolean registersInViewStack() {
        return true;
    }

    @Override
    protected void initTheme() {
        setTheme(R.style.Theme_SmarterTube_Mobile);
    }

    protected void restoreRealDensity() {
        DisplayMetrics real = Resources.getSystem().getDisplayMetrics();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        dm.density = real.density;
        dm.scaledDensity = real.scaledDensity;
        dm.densityDpi = real.densityDpi;
    }
}
