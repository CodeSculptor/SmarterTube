package com.liskovsoft.smartyoutubetv2.mobile.ui.dialogs;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.mobile.ui.base.MobileActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Phone-native host for the settings dialog. Replaces the TV
 * {@code tv.ui.dialogs.AppDialogActivity} (Leanback infinite-scroll preferences) for the
 * stmobile flavor — wired in
 * {@link com.liskovsoft.smartyoutubetv2.mobile.ui.main.MobileApplication}.
 */
public class MobileAppDialogActivity extends MobileActivity {
    private boolean mPanelMode;
    private boolean mSheetMode;

    // One-shot suppression deadline (epoch millis). When set and still fresh, the NEXT
    // dialog launch finishes itself immediately instead of showing. Used by the sign-in
    // flow to swallow the redundant single-account picker that YTSignInPresenter force-
    // shows after a fresh sign-in (see MobileSignInFragment.close()). A timestamp deadline
    // (not a bare boolean) self-expires, so a stale flag can never eat a later legitimate
    // dialog if the expected picker never launches.
    private static volatile long sSuppressDialogUntil;
    private static final long SUPPRESS_WINDOW_MS = 4000;

    /** Arm a one-shot suppression of the next dialog launched within the next few seconds. */
    public static void suppressNextDialog() {
        sSuppressDialogUntil = System.currentTimeMillis() + SUPPRESS_WINDOW_MS;
    }

    /**
     * In comments panel mode (or bottom-sheet sub-dialog mode), apply the translucent panel theme
     * as the LAST setTheme of the super.onCreate chain. MotherActivity.onCreate calls initTheme(),
     * and MobileActivity overrides it to set the opaque brand theme — if we only setTheme() before
     * super.onCreate that opaque theme wins and kills windowIsTranslucent (black fill behind the
     * card / above the comments). Setting it here ensures translucency survives. (FitSystemWindows,
     * applied after this by MotherActivity, only touches the status/nav bars, not
     * windowIsTranslucent/background.)
     */
    @Override
    protected void initTheme() {
        if (mPanelMode || mSheetMode) {
            setTheme(R.style.Theme_SmarterTube_Mobile_TranslucentPanel);
        } else {
            super.initTheme();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Comments over the portrait player: translucent window inset below the 16:9 video
        // strip, so the video stays visible (and keeps playing — the player activity only
        // pauses under a translucent window instead of stopping). Must be decided before
        // super.onCreate: windowIsTranslucent can't change after the window exists, and the
        // actual setTheme happens in our initTheme() override (called from super.onCreate),
        // which runs AFTER MobileActivity.initTheme() would otherwise re-apply the opaque
        // brand theme and clobber translucency.
        mPanelMode = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT
                && AppDialogPresenter.instance(this).isComments();

        // A focused sub-dialog (e.g. the Video speed slider from the player) shows as a bottom-sheet
        // card; make the window translucent so the still-playing video (or the screen behind) stays
        // visible behind the dim scrim instead of a black blackout. Orientation-agnostic — unlike the
        // portrait-only comments panel, the sheet is most useful over the landscape player. Decided
        // here for the same reason as mPanelMode: windowIsTranslucent is fixed before super.onCreate.
        mSheetMode = !mPanelMode && AppDialogPresenter.instance(this).isSheetDialog();

        super.onCreate(savedInstanceState);

        if (mPanelMode || mSheetMode) {
            // Kill the default slide-in/out; the panel/sheet should appear in place over the player.
            overridePendingTransition(0, 0);
        }

        if (mSheetMode) {
            // The player runs fullscreen with a translucent nav bar (App.Theme.Leanback.Player:
            // windowFullscreen + windowTranslucentNavigation). Match those flags here so dropping a
            // translucent dialog over it does not make the system bars reappear and resize the
            // player window — that resize leaves the video SurfaceView rendering scaled-down in the
            // top-left corner with black around it.
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        }

        // Consume a pending one-shot suppression: if armed and still fresh, this dialog is
        // the redundant post-sign-in account picker — finish before showing anything.
        long deadline = sSuppressDialogUntil;
        sSuppressDialogUntil = 0;
        if (deadline != 0 && System.currentTimeMillis() < deadline) {
            finish();
            return;
        }

        setContentView(R.layout.mobile_app_dialog_activity);

        if (mPanelMode) {
            applyPanelInsets();
        } else if (mSheetMode) {
            // Drop the activity root's opaque fill so the translucent window shows the content
            // behind it; the fragment paints its own dim scrim + bottom card over the top. No strip
            // inset here — the sheet is anchored to the bottom, not below a 16:9 player strip.
            View root = findViewById(R.id.mobile_app_dialog_root);
            if (root != null) {
                root.setBackground(null);
            }
        }

        if (getSupportFragmentManager().findFragmentById(R.id.mobile_app_dialog_root) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.mobile_app_dialog_root, new MobileAppDialogFragment())
                    .commit();
        }
    }

    @Override
    protected boolean appliesSystemBarInsets() {
        // The sheet copies the player's full-screen + translucent-nav flags and draws its own
        // bottom card over the player; padding it would lift the card off the bottom edge.
        return !mSheetMode;
    }

    /**
     * Panel mode: the dialog content starts below the player's 16:9 strip; the transparent
     * area above it shows the still-playing video, and tapping it closes the comments.
     */
    private void applyPanelInsets() {
        View container = findViewById(R.id.mobile_app_dialog_root);
        if (container == null) {
            return;
        }

        // The container is already inset below the status bar (fitsSystemWindows pushes the
        // activity content down by the status-bar height), so its top already sits at the bar's
        // bottom. The portrait player draws the video as a 16:9 strip starting just below the
        // status bar, so the panel only needs to drop by the strip's HEIGHT — adding the status
        // bar again would double-count it and leave the player header peeking through the gap.
        // width * 9 / 16 = strip height.
        int stripHeight = getResources().getDisplayMetrics().widthPixels * 9 / 16;

        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) container.getLayoutParams();
        lp.topMargin = stripHeight;
        container.setLayoutParams(lp);

        // Tap on the visible video above the panel = close the comments.
        View content = findViewById(android.R.id.content);
        if (content != null) {
            content.setOnClickListener(v -> finish());
        }
    }

    @Override
    protected boolean registersInViewStack() {
        // The settings dialog (and comments panel) is a transient overlay, not a navigation node.
        // Keeping it out of the ViewManager stack stops Back on the player from re-opening a
        // just-closed dialog (issue: bring up Video speed → Back on the video → panel returns).
        return false;
    }

    @Override
    public void onBackPressed() {
        // A nested comments replies thread is one back-level inside the fragment: restore the
        // top-level comments instead of closing the dialog.
        MobileAppDialogFragment fragment = (MobileAppDialogFragment)
                getSupportFragmentManager().findFragmentById(R.id.mobile_app_dialog_root);
        if (fragment != null && fragment.canGoBack()) {
            fragment.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void finish() {
        // Mirror TV AppDialogActivity: fire the presenter's onFinish callbacks before the
        // real finish, regardless of whether finish() came from back or from a programmatic
        // closeDialog(). onViewDestroyed() will run afterwards via the fragment's onDestroy.
        MobileAppDialogFragment fragment = (MobileAppDialogFragment)
                getSupportFragmentManager().findFragmentById(R.id.mobile_app_dialog_root);
        if (fragment != null) {
            fragment.onFinishCallback();
        }
        super.finish();
        if (mPanelMode || mSheetMode) {
            // Match the no-animation entrance: panel/sheet closes in place, no slide-out.
            overridePendingTransition(0, 0);
        }
    }
}
