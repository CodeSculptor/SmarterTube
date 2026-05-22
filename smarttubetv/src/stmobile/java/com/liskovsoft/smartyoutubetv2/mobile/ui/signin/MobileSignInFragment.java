package com.liskovsoft.smartyoutubetv2.mobile.ui.signin;

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

        // Drop the content below the status bar (MotherActivity runs TV-style fullscreen).
        View root = view.findViewById(R.id.signin_root);
        root.setPadding(root.getPaddingLeft(), root.getPaddingTop() + getStatusBarHeight(),
                root.getPaddingRight(), root.getPaddingBottom());

        // Disabled until the device code arrives (mFullSignInUrl is set in showCode()).
        mBrowserButton.setEnabled(false);
        mBrowserButton.setOnClickListener(v -> openInBrowser());

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
        if (TextUtils.isEmpty(userCode) || !isAdded()) {
            return;
        }

        // user_code in the query string pre-fills the activation page so the user does
        // not have to type it (they still confirm it once, for anti-phishing).
        mFullSignInUrl = signInUrl + "?user_code=" + userCode.replace(" ", "-");
        mCodeView.setText(userCode);
        mBrowserButton.setEnabled(true);
    }

    @Override
    public void close() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    /**
     * Opens the activation URL in an in-app Chrome Custom Tab. Falls back to an external
     * browser if no Custom Tabs provider is available.
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
    }

    private int getStatusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }
}
