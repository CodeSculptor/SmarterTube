package com.liskovsoft.smartyoutubetv2.mobile.ui.base;

import android.app.Activity;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            // Visible bars only: a hidden bar reports 0, so the default Fullscreen mode (bars
            // hidden) keeps its full-bleed layout. No cutout inset for the same reason: with the
            // bars hidden the content deliberately runs up under the camera hole.
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            // Fully consumed, which stops dispatch to the children, as the platform's own fitting
            // does. With Fullscreen mode off, the FitSystemWindows theme sets fitsSystemWindows on
            // every view, and any view that still received (zeroed) insets would have its XML
            // padding overwritten.
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
    }
}
