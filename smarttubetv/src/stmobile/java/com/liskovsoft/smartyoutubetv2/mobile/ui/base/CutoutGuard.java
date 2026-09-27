package com.liskovsoft.smartyoutubetv2.mobile.ui.base;

import android.app.Activity;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;

import androidx.core.graphics.Insets;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Which edges of a display cutout the phone UI has to keep clear of (#47).
 *
 * With the system bars hidden (upstream's Fullscreen mode) screens are laid out into the cutout
 * area. On a centred camera hole that's what we want (the Samsung look, see {@link SystemBarInsets}):
 * the hole sits between the top bar's buttons. But a corner notch, or MIUI's "hide notch" setting,
 * which blacks out the whole strip beside the notch, covers the buttons at the ends of the bar (the
 * hamburger / back button in the reporter's screenshots).
 *
 * So a cutout edge counts only when one of its bounding rects reaches into the outer quarters of
 * that edge, where the corner buttons live. A centred hole or notch returns 0 for that edge.
 */
public final class CutoutGuard {
    private static final float OUTER_FRACTION = 0.25f;

    private CutoutGuard() {
    }

    /** Per-edge bands for the activity's current window (see {@link #bands(WindowInsetsCompat, int, int)}). */
    public static Insets bands(Activity activity, WindowInsetsCompat insets) {
        Point size = windowSize(activity);
        return bands(insets, size.x, size.y);
    }

    /**
     * The window's size from the WindowManager: insets can arrive before the first layout (views
     * still 0 x 0), and upstream overwrites the Resources display metrics with a stale copy.
     */
    @SuppressWarnings("deprecation")
    private static Point windowSize(Activity activity) {
        if (Build.VERSION.SDK_INT >= 30) {
            Rect bounds = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
            return new Point(bounds.width(), bounds.height());
        }
        Point size = new Point();
        activity.getWindowManager().getDefaultDisplay().getRealSize(size);
        return size;
    }

    /**
     * Per-edge distance to keep clear of, in window pixels, for a window of the given size. Zero on
     * every edge without a cutout, or whose cutout stays in the middle of the edge.
     */
    public static Insets bands(WindowInsetsCompat insets, int windowWidth, int windowHeight) {
        DisplayCutoutCompat cutout = insets != null ? insets.getDisplayCutout() : null;
        if (cutout == null || windowWidth <= 0 || windowHeight <= 0) {
            return Insets.NONE;
        }
        int left = 0, top = 0, right = 0, bottom = 0;
        for (Rect r : cutout.getBoundingRects()) {
            if (r.isEmpty()) {
                continue;
            }
            boolean reachesHorizontalEnds = r.left < windowWidth * OUTER_FRACTION
                    || r.right > windowWidth * (1 - OUTER_FRACTION);
            boolean reachesVerticalEnds = r.top < windowHeight * OUTER_FRACTION
                    || r.bottom > windowHeight * (1 - OUTER_FRACTION);
            if (r.top <= 0 && reachesHorizontalEnds) {
                top = Math.max(top, Math.max(cutout.getSafeInsetTop(), r.bottom));
            } else if (r.bottom >= windowHeight && reachesHorizontalEnds) {
                bottom = Math.max(bottom, Math.max(cutout.getSafeInsetBottom(), windowHeight - r.top));
            } else if (r.left <= 0 && reachesVerticalEnds) {
                left = Math.max(left, Math.max(cutout.getSafeInsetLeft(), r.right));
            } else if (r.right >= windowWidth && reachesVerticalEnds) {
                right = Math.max(right, Math.max(cutout.getSafeInsetRight(), windowWidth - r.left));
            }
        }
        return Insets.of(left, top, right, bottom);
    }
}
