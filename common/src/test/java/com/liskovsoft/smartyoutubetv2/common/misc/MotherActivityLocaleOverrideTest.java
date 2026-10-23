package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.res.Configuration;
import android.util.DisplayMetrics;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

/**
 * The saved-language override handed to createConfigurationContext must only carry the fields it
 * means to change. Any other field set in it (orientation, screen size, window bounds) is treated
 * as an explicit override by the platform and stops following the window: after a rotation the
 * activity's resources still reported portrait, and the phone player kept its portrait layout
 * (tab bar included) in landscape.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MotherActivityLocaleOverrideTest {
    private static Configuration portraitBase() {
        Configuration base = new Configuration();
        base.orientation = Configuration.ORIENTATION_PORTRAIT;
        base.screenWidthDp = 411;
        base.screenHeightDp = 891;
        base.smallestScreenWidthDp = 411;
        base.fontScale = 1.3f;
        base.uiMode = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_YES;
        base.screenLayout = Configuration.SCREENLAYOUT_SIZE_NORMAL | Configuration.SCREENLAYOUT_LONG_YES
                | Configuration.SCREENLAYOUT_LAYOUTDIR_LTR;
        base.densityDpi = 420;
        base.setLocale(Locale.US);
        return base;
    }

    @Test
    public void overrideLeavesOrientationAndScreenSizeToTheWindow() {
        Configuration override = MotherActivity.createLocaleOverride(portraitBase(), new Locale("da", "DK"), null);

        assertEquals(Configuration.ORIENTATION_UNDEFINED, override.orientation);
        assertEquals(Configuration.SCREEN_WIDTH_DP_UNDEFINED, override.screenWidthDp);
        assertEquals(Configuration.SCREEN_HEIGHT_DP_UNDEFINED, override.screenHeightDp);
        assertEquals(Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED, override.smallestScreenWidthDp);
    }

    @Test
    public void overrideCarriesTheSavedLocale() {
        Configuration override = MotherActivity.createLocaleOverride(portraitBase(), new Locale("da", "DK"), null);

        assertEquals(new Locale("da", "DK"), override.locale);
        // Layout direction follows the new locale; the size/long bits stay as they were.
        assertEquals(Configuration.SCREENLAYOUT_SIZE_NORMAL, override.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK);
        assertEquals(Configuration.SCREENLAYOUT_LONG_YES, override.screenLayout & Configuration.SCREENLAYOUT_LONG_MASK);
    }

    @Test
    public void overrideKeepsFontScaleAndUiModeOfTheBase() {
        Configuration override = MotherActivity.createLocaleOverride(portraitBase(), Locale.GERMANY, null);

        assertEquals(1.3f, override.fontScale, 0f);
        assertEquals(Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_YES, override.uiMode);
    }

    @Test
    public void overrideAppliesTheCustomDensityOnlyWhenGiven() {
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.density = 2.5f;

        assertEquals(400, MotherActivity.createLocaleOverride(portraitBase(), Locale.GERMANY, metrics).densityDpi);
        assertEquals(Configuration.DENSITY_DPI_UNDEFINED,
                MotherActivity.createLocaleOverride(portraitBase(), Locale.GERMANY, null).densityDpi);
    }

    @Test
    public void rotatedWindowConfigurationWinsOverTheOverride() {
        // Mirrors ResourcesManager after a rotation: the activity's new configuration is taken
        // first and the stored override is applied on top of it with updateFrom().
        Configuration override = MotherActivity.createLocaleOverride(portraitBase(), new Locale("da", "DK"), null);

        Configuration rotated = portraitBase();
        rotated.orientation = Configuration.ORIENTATION_LANDSCAPE;
        rotated.screenWidthDp = 891;
        rotated.screenHeightDp = 411;
        rotated.updateFrom(override);

        assertEquals(Configuration.ORIENTATION_LANDSCAPE, rotated.orientation);
        assertEquals(891, rotated.screenWidthDp);
        assertEquals(411, rotated.screenHeightDp);
        assertEquals(new Locale("da", "DK"), rotated.locale);
    }
}
