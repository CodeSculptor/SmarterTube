package com.liskovsoft.smartyoutubetv2.mobile.ui.dialogs;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.mobile.ui.base.MobileActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Phone-native host for the settings dialog. Replaces the TV
 * {@code tv.ui.dialogs.AppDialogActivity} (Leanback infinite-scroll preferences) for the
 * stmobile flavor — wired in
 * {@link com.liskovsoft.smartyoutubetv2.mobile.ui.main.MobileApplication}.
 */
public class MobileAppDialogActivity extends MobileActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_app_dialog_activity);

        if (getSupportFragmentManager().findFragmentById(R.id.mobile_app_dialog_root) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.mobile_app_dialog_root, new MobileAppDialogFragment())
                    .commit();
        }
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
    }
}
