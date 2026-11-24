package com.liskovsoft.smartyoutubetv2.mobile.offline;

import android.content.Context;
import android.view.ContextThemeWrapper;

import androidx.appcompat.app.AlertDialog;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * The only way offline code builds a dialog. The Modern player's activity is not an AppCompat
 * activity, so an AppCompat dialog built on it directly crashes; wrapping the context in the app's
 * AppCompat theme works from any activity.
 */
public final class OfflineDialogs {
    private OfflineDialogs() {}

    public static AlertDialog.Builder builder(Context context) {
        return new AlertDialog.Builder(new ContextThemeWrapper(context, R.style.Theme_SmarterTube_Mobile));
    }
}
