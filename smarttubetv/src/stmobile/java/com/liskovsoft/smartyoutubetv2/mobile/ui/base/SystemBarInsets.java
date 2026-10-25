package com.liskovsoft.smartyoutubetv2.mobile.ui.base;

import android.app.Activity;
import android.os.Build;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;

/**
 * Keeps phone screens clear of system bars that are actually showing (#37).
 *
 * Upstream's MotherActivity lays every screen out behind the system bars. "Fullscreen mode" (on by
 * default) calls setDecorFitsSystemWindows(false) and hides the bars. That's fine while they stay
 * hidden, but a Pixel on Android 17 kept the status bar showing, and it drew over the top bar.
 *
 * Only visible bars pad: hidden bars report 0, so where the bars do hide (most phones) nothing
 * changes. Bars briefly swiped in over a fullscreen app overlay the content, as in any immersive
 * app. With Fullscreen mode off, the platform already fits the window and this pads the same amount
 * the platform's own fitting did.
 *
 * Cutouts (#47): content normally runs under a centred camera hole, but a cutout that covers the
 * ends of the top bar (a corner notch, or MIUI's blacked-out notch strip) pads too; see
 * {@link CutoutGuard}.
 *
 * Below Android 11 (#47, a Lenovo K6 Note on Android 7) the bars don't report 0 when hidden:
 * upstream's legacy Fullscreen flags include SYSTEM_UI_FLAG_LAYOUT_STABLE, which makes the insets
 * always describe the bars as if they were showing. Padding by them left an empty, nav-bar-sized
 * strip under every screen. There, Fullscreen mode skips the bar padding (the bars are hidden by
 * immersive sticky, and a swiped-in bar overlays the content, as above).
 *
 * Applied to {@code android.R.id.content}, so every layout below it gets the fix without changes.
 * The player is not a {@link MobileActivity} and manages its own full-screen window.
 */
public final class SystemBarInsets {
    private SystemBarInsets() {
    }

    public static void apply(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        int left = content.getPaddingLeft();
        int top = content.getPaddingTop();
        int right = content.getPaddingRight();
        int bottom = content.getPaddingBottom();
        // Stable (always "showing") bar insets below API 30; see the class comment.
        boolean stableBarInsets = Build.VERSION.SDK_INT < 30
                && GeneralData.instance(activity).isFullscreenModeEnabled();
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            // Visible bars only: a hidden bar reports 0, so the default Fullscreen mode (bars
            // hidden) keeps its full-bleed layout. No cutout inset for the same reason: with the
            // bars hidden the content deliberately runs up under the camera hole.
            Insets bars = stableBarInsets ? Insets.NONE : insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // ...except a cutout band over the ends of the bars' edges, which hides buttons (#47).
            Insets cutout = CutoutGuard.bands(activity, insets);
            Insets pad = Insets.max(bars, cutout);
            v.setPadding(left + pad.left, top + pad.top, right + pad.right, bottom + pad.bottom);
            // Fully consumed, which stops dispatch to the children, as the platform's own fitting
            // does. With Fullscreen mode off, the FitSystemWindows theme sets fitsSystemWindows on
            // every view, and any view that still received (zeroed) insets would have its XML
            // padding overwritten.
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
    }
}
