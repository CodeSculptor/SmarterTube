package com.liskovsoft.smartyoutubetv2.mobile.ui.signin;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.mobile.ui.base.MobileActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Host for the native portrait sign-in screen. Replaces the TV Leanback SignInActivity
 * for the stmobile flavor (wired in MobileApplication).
 */
public class MobileSignInActivity extends MobileActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_signin_activity);

        if (getSupportFragmentManager().findFragmentById(R.id.mobile_signin_root) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.mobile_signin_root, new MobileSignInFragment())
                    .commit();
        }
    }
}
