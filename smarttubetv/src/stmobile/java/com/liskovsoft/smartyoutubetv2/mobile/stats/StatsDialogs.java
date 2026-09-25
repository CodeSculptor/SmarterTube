package com.liskovsoft.smartyoutubetv2.mobile.stats;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;

import com.liskovsoft.smartyoutubetv2.tv.R;

/** The three user-facing surfaces of the opt-in stats: first-run prompt, Settings picker, per-crash prompt. */
public final class StatsDialogs {
    private StatsDialogs() {}

    /**
     * On Home start: asks once for consent, or — if the user hasn't opted in — offers to send a
     * crash report saved from the previous run. Dormant when no stats endpoint is configured.
     * {@code done} runs once the dialog is closed, or right away when there's nothing to ask.
     */
    public static void maybeShowOnHome(Activity activity, Runnable done) {
        if (!StatsReporter.isConfigured(activity)) {
            done.run();
            return;
        }
        StatsPrefs.Consent consent = StatsPrefs.getConsent(activity);
        if (consent == StatsPrefs.Consent.UNASKED) {
            showConsentPrompt(activity, done);
        } else if (consent == StatsPrefs.Consent.OFF && StatsReporter.hasPendingCrash(activity)) {
            showCrashPrompt(activity, done);
        } else {
            done.run();
        }
    }

    private static void showConsentPrompt(Activity activity, Runnable done) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.mobile_stats_prompt_title)
                .setMessage(R.string.mobile_stats_details)
                .setCancelable(false)
                .setPositiveButton(R.string.mobile_stats_prompt_yes, (d, w) -> setConsent(activity, true))
                .setNegativeButton(R.string.mobile_stats_prompt_no, (d, w) -> setConsent(activity, false))
                .setOnDismissListener(d -> done.run())
                .show();
    }

    private static void showCrashPrompt(Activity activity, Runnable done) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.mobile_stats_crash_title)
                .setMessage(R.string.mobile_stats_crash_message)
                .setPositiveButton(R.string.mobile_stats_crash_send, (d, w) -> StatsReporter.sendPendingCrash(activity))
                .setNegativeButton(R.string.mobile_stats_crash_discard, (d, w) -> StatsReporter.discardPendingCrash(activity))
                .setOnCancelListener(d -> StatsReporter.discardPendingCrash(activity))
                .setOnDismissListener(d -> done.run())
                .show();
    }

    /** Settings row: Off / On, with a "What's sent?" explainer. */
    public static void showSettingsPicker(Context context) {
        String[] labels = {
                context.getString(R.string.mobile_notifications_option_off),
                context.getString(R.string.mobile_notifications_option_on),
        };
        int checked = StatsPrefs.isEnabled(context) ? 1 : 0;
        new AlertDialog.Builder(context)
                .setTitle(R.string.mobile_stats_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    setConsent(context, which == 1);
                })
                .setNeutralButton(R.string.mobile_stats_whats_sent, (d, w) ->
                        new AlertDialog.Builder(context)
                                .setTitle(R.string.mobile_stats_title)
                                .setMessage(R.string.mobile_stats_details)
                                .setPositiveButton(android.R.string.ok, null)
                                .show())
                .show();
    }

    private static void setConsent(Context context, boolean on) {
        StatsPrefs.setConsent(context, on ? StatsPrefs.Consent.ON : StatsPrefs.Consent.OFF);
        if (on) {
            StatsReporter.onForeground(context);
        } else {
            StatsReporter.discardPendingCrash(context);
        }
    }
}
