package com.liskovsoft.smartyoutubetv2.mobile.ui.signin;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.fragment.app.Fragment;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.mobile.ui.browse.MobileBrowseActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Single-phone sign-in screen. Implements {@link SignInView}, driven directly by
 * {@link YTSignInPresenter} (the OAuth device-code flow).
 *
 * The device-code flow is mandatory — it is the only flow that yields InnerTube-compatible
 * credentials, and Google blocks OAuth inside embedded WebViews. To make it seamless on a
 * single phone, the code is pre-filled into the URL and opened in an in-app Chrome Custom
 * Tab: the user signs in and approves without leaving the app, then the presenter's poll
 * detects completion and closes this screen automatically.
 */
public class MobileSignInFragment extends Fragment implements SignInView {
    private SignInPresenter mPresenter;
    private TextView mCodeView;
    private Button mBrowserButton;
    private Button mDoneButton;
    private String mFullSignInUrl;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Use YTSignInPresenter directly. The base SignInPresenter is only a dispatcher:
        // it drives nothing unless a sub-presenter was pre-"armed" by a start() call,
        // which never happens when the screen is opened via startView(). Going direct
        // runs the OAuth device-code flow (updateUserCode) immediately.
        mPresenter = YTSignInPresenter.instance(getContext());
        mPresenter.setView(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.mobile_signin_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mCodeView = view.findViewById(R.id.signin_code);
        mBrowserButton = view.findViewById(R.id.signin_browser_button);
        mDoneButton = view.findViewById(R.id.signin_done_button);

        // Disabled until the device code arrives (mFullSignInUrl is set in showCode()).
        mBrowserButton.setEnabled(false);
        mBrowserButton.setOnClickListener(v -> openInBrowser());
        // Manual escape hatch: if the user completed sign-in in the Custom Tab and the
        // polling-detected auto-return is slow, this brings the app's task forward.
        // Polling continues; close() still fires on success.
        mDoneButton.setOnClickListener(v -> returnToApp());

        mPresenter.onViewInitialized();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPresenter != null) {
            mPresenter.onViewDestroyed();
        }
    }

    @Override
    public void showCode(String userCode, String signInUrl) {
        showCode(userCode, signInUrl, null);
    }

    @Override
    public void showCode(String userCode, String signInUrl, String fullSignInUrl) {
        if (TextUtils.isEmpty(userCode) || !isAdded()) {
            return;
        }

        // YTSignInPresenter supplies a ready-to-open activation URL (code pre-filled) as
        // fullSignInUrl. Fall back to hand-building it from signInUrl + user_code only if
        // the presenter ever calls the 2-arg overload.
        mFullSignInUrl = fullSignInUrl != null
                ? fullSignInUrl
                : signInUrl + "?user_code=" + userCode.replace(" ", "-");
        mCodeView.setText(userCode);
        mBrowserButton.setEnabled(true);
    }

    @Override
    public void close() {
        returnToApp();
        Activity activity = getActivity();
        if (activity != null) {
            activity.finish();
        }
    }

    /**
     * Bring the app's task to the foreground. The Custom Tab opens YouTube's
     * activation page in its OWN task (Chrome's), so {@code CLEAR_TOP} alone does not
     * cover it — {@code REORDER_TO_FRONT} pulls our task on top of Chrome's. Called
     * both on polling success (via {@link #close()}) and from the manual "Done? Return
     * to app" escape hatch.
     */
    private void returnToApp() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        Intent intent = new Intent(activity, MobileBrowseActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        activity.startActivity(intent);
    }

    /**
     * Opens the activation URL in an in-app Chrome Custom Tab. Falls back to an external
     * browser if no Custom Tabs provider is available. Reveals the "Done? Return to app"
     * escape hatch once the tab is launched.
     */
    private void openInBrowser() {
        if (mFullSignInUrl == null || getContext() == null) {
            return;
        }
        try {
            new CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                    .launchUrl(getContext(), Uri.parse(mFullSignInUrl));
        } catch (Exception e) {
            Utils.openLinkExt(getContext(), mFullSignInUrl);
        }
        if (mDoneButton != null) {
            mDoneButton.setVisibility(View.VISIBLE);
        }
    }
}
