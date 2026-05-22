package com.liskovsoft.smartyoutubetv2.mobile.ui.signin;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Native portrait sign-in screen. Implements {@link SignInView} and is driven by the
 * existing {@link SignInPresenter} unchanged.
 *
 * The mechanism is the OAuth device-code flow (a code + a URL), exactly as the TV screen:
 * Google blocks OAuth inside embedded WebViews for non-Google apps, so an inline Google
 * login is not possible — only the presentation is made phone-friendly here.
 */
public class MobileSignInFragment extends Fragment implements SignInView {
    private SignInPresenter mPresenter;
    private TextView mCodeView;
    private TextView mDescriptionView;
    private ImageView mQrView;
    private String mFullSignInUrl;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPresenter = SignInPresenter.instance(getContext());
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
        mDescriptionView = view.findViewById(R.id.signin_description);
        mQrView = view.findViewById(R.id.signin_qr);

        ((TextView) view.findViewById(R.id.signin_title)).setText(R.string.signin_view_title);

        // Drop the content below the status bar (MotherActivity runs TV-style fullscreen).
        View root = view.findViewById(R.id.signin_root);
        root.setPadding(root.getPaddingLeft(), root.getPaddingTop() + getStatusBarHeight(),
                root.getPaddingRight(), root.getPaddingBottom());

        Button browserButton = view.findViewById(R.id.signin_browser_button);
        browserButton.setText(R.string.login_from_browser);
        browserButton.setOnClickListener(v -> {
            if (mFullSignInUrl != null) {
                Utils.openLinkExt(getContext(), mFullSignInUrl);
            }
        });

        Button continueButton = view.findViewById(R.id.signin_continue_button);
        continueButton.setText(R.string.signin_view_action_text);
        continueButton.setOnClickListener(v -> {
            if (mPresenter != null) {
                mPresenter.onActionClicked();
            }
        });

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

        mFullSignInUrl = signInUrl + "?user_code=" + userCode.replace(" ", "-");
        mCodeView.setText(userCode);
        mDescriptionView.setText(getString(R.string.signin_view_description, signInUrl));

        Glide.with(mQrView)
                .load(Utils.toQrCodeLink(mFullSignInUrl))
                .error(R.drawable.activate_account_qrcode)
                .into(mQrView);
    }

    @Override
    public void close() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private int getStatusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }
}
