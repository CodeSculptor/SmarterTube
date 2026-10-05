package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.mobile.notifications.NotificationPollWorker;
import com.liskovsoft.smartyoutubetv2.mobile.stats.StatsDialogs;
import com.liskovsoft.smartyoutubetv2.mobile.stats.StatsReporter;
import com.liskovsoft.smartyoutubetv2.mobile.ui.prefs.MobileNotificationPrefs;
import com.liskovsoft.smartyoutubetv2.mobile.ui.prefs.MobilePlayerStylePrefs;
import com.liskovsoft.smartyoutubetv2.mobile.ui.prefs.MobileThemePrefs;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The phone Settings list: the phone-only rows plus upstream's settings entries. Shared by the
 * Settings screen ({@link MobileSettingsActivity}) and the Settings section fallback in
 * {@link MobileBrowseFragment}.
 */
final class MobileSettingsRows {
    private static final int REQ_POST_NOTIFICATIONS = 4712;
    private static volatile boolean sThemeChanged;

    private MobileSettingsRows() {
    }

    /** True once after the Theme row changed the theme: the caller should recreate() to pick it up. */
    static boolean consumeThemeChanged() {
        boolean changed = sThemeChanged;
        sThemeChanged = false;
        return changed;
    }

    /**
     * Prepend the phone-only rows (Theme, Player style, ...) to the upstream Settings list. Upstream's
     * ColorScheme picker is TV-only (all 8 schemes are dark variants) and isn't
     * surfaced in the stmobile UI, so this is a dedicated Day-Night toggle that
     * drives {@link MobileThemePrefs}.
     */
    static List<SettingsItem> build(Activity context, List<SettingsItem> upstreamItems) {
        List<SettingsItem> items = new ArrayList<>();
        if (context != null) {
            items.add(new SettingsItem(
                    context.getString(R.string.mobile_theme_title),
                    () -> showThemePicker(context),
                    R.drawable.settings_theme));
            items.add(new SettingsItem(
                    context.getString(R.string.mobile_player_style_title),
                    () -> showPlayerStylePicker(context),
                    R.drawable.settings_player_style));
            items.add(new SettingsItem(
                    context.getString(R.string.mobile_swipe_gestures_title),
                    () -> showSwipeGesturesToggle(context),
                    R.drawable.settings_swipe_gestures));
            items.add(new SettingsItem(
                    context.getString(R.string.mobile_notifications_title),
                    () -> showNotificationsToggle(context),
                    R.drawable.settings_notifications));
            if (StatsReporter.isConfigured(context)) {
                items.add(new SettingsItem(
                        context.getString(R.string.mobile_stats_title),
                        () -> StatsDialogs.showSettingsPicker(context),
                        R.drawable.settings_stats));
            }
        }
        if (upstreamItems != null) {
            // Drop upstream's "About" row: it's the TV-oriented About panel (dead
            // "Check for updates", the meaningless ATV/Amazon "global search" bridge). The
            // phone build's own About screen in the drawer footer (MobileAboutActivity) is
            // the single About surface.
            String aboutTitle = context != null ? context.getString(R.string.settings_about) : null;
            for (SettingsItem item : upstreamItems) {
                if (aboutTitle != null && aboutTitle.equals(item.title)) {
                    continue;
                }
                items.add(item);
            }
        }
        return items;
    }

    private static void showThemePicker(Activity context) {
        MobileThemePrefs.Mode[] modes = MobileThemePrefs.Mode.values();
        String[] labels = {
                context.getString(R.string.mobile_theme_option_system),
                context.getString(R.string.mobile_theme_option_light),
                context.getString(R.string.mobile_theme_option_dark),
        };
        MobileThemePrefs.Mode current = MobileThemePrefs.getMode(context);
        int checked = current.ordinal();
        new AlertDialog.Builder(context)
                .setTitle(R.string.mobile_theme_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    if (modes[which] == current) {
                        dialog.dismiss();
                        return;
                    }
                    MobileThemePrefs.setMode(context, modes[which]);
                    dialog.dismiss();
                    // MotherActivity is a FragmentActivity (not AppCompatActivity), so
                    // setDefaultNightMode does not auto-recreate. MobileActivity reads
                    // the pref in attachBaseContext, so a manual recreate() pulls in the
                    // new uiMode override.
                    // Screens underneath (Home under the Settings screen) re-check this when they resume.
                    sThemeChanged = true;
                    context.recreate();
                })
                .show();
    }

    /**
     * Phone-only "Player style" picker (#46): Classic / Modern / Tap to pause. Drives
     * {@link MobilePlayerStylePrefs}; the player reads it each time it resumes.
     */
    private static void showPlayerStylePicker(Activity context) {
        MobilePlayerStylePrefs.Style[] styles = MobilePlayerStylePrefs.Style.values();
        String[] labels = {
                context.getString(R.string.mobile_player_style_classic),
                context.getString(R.string.mobile_player_style_modern),
                context.getString(R.string.mobile_player_style_tap_to_pause),
        };
        int checked = MobilePlayerStylePrefs.getStyle(context).ordinal();
        new AlertDialog.Builder(context)
                .setTitle(R.string.mobile_player_style_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    MobilePlayerStylePrefs.setStyle(context, styles[which]);
                    dialog.dismiss();
                })
                .show();
    }

    /** Phone-only "Swipe gestures" on / volume only / off (#48, #47); the player reads it when it resumes. */
    private static void showSwipeGesturesToggle(Activity context) {
        String[] labels = {
                context.getString(R.string.mobile_swipe_gestures_on),
                context.getString(R.string.mobile_swipe_gestures_volume_only),
                context.getString(R.string.mobile_swipe_gestures_off),
        };
        int checked = !MobilePlayerStylePrefs.isSwipeGesturesEnabled(context) ? 2
                : MobilePlayerStylePrefs.isBrightnessSwipeEnabled(context) ? 0 : 1;
        new AlertDialog.Builder(context)
                .setTitle(R.string.mobile_swipe_gestures_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    MobilePlayerStylePrefs.setSwipeGesturesEnabled(context, which != 2);
                    if (which != 2) {
                        MobilePlayerStylePrefs.setBrightnessSwipeEnabled(context, which == 0);
                    }
                    dialog.dismiss();
                })
                .show();
    }

    /**
     * Phone-only "Upload notifications" on/off toggle (Part 2 — push). Drives
     * {@link MobileNotificationPrefs} and (re)schedules {@link NotificationPollWorker}. On enable,
     * asks for the Android 13+ POST_NOTIFICATIONS permission via the host activity.
     */
    private static void showNotificationsToggle(Activity context) {
        String[] labels = {
                context.getString(R.string.mobile_notifications_option_off),
                context.getString(R.string.mobile_notifications_option_on),
        };
        boolean enabled = MobileNotificationPrefs.isEnabled(context);
        int checked = enabled ? 1 : 0;
        new AlertDialog.Builder(context)
                .setTitle(R.string.mobile_notifications_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    boolean turnOn = which == 1;
                    dialog.dismiss();
                    if (turnOn == enabled) {
                        return;
                    }
                    MobileNotificationPrefs.setEnabled(context, turnOn);
                    MobileNotificationPrefs.setPrompted(context); // chose here; never prompt on launch
                    NotificationPollWorker.schedule(context);
                    if (turnOn) {
                        requestPostNotificationsPermission(context);
                    }
                })
                .show();
    }

    /** Best-effort Android 13+ POST_NOTIFICATIONS request; posting silently no-ops without it. */
    private static void requestPostNotificationsPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
        }
    }
}
