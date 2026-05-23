package com.liskovsoft.smartyoutubetv2.mobile.ui.main;

import com.liskovsoft.smartyoutubetv2.common.app.views.AddDeviceView;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.app.views.WebBrowserView;
import com.liskovsoft.smartyoutubetv2.mobile.ui.browse.MobileBrowseActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.channel.MobileChannelActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.channeluploads.MobileChannelUploadsActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.dialogs.MobileAppDialogActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.playback.MobilePlaybackActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.search.MobileSearchActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.signin.MobileSignInActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.adddevice.AddDeviceActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.webbrowser.WebBrowserActivity;

/**
 * Application class for the phone (stmobile) flavor.
 *
 * Overrides view routing so the phone build uses its own native portrait screens
 * ({@link MobileBrowseActivity}, {@link MobileSearchActivity}, ...) instead of the TV
 * Leanback ones. The remaining screens are reused from the TV code unchanged (later
 * phases will replace them in turn).
 */
public class MobileApplication extends MainApplication {
    @Override
    protected void setupViewManager() {
        ViewManager viewManager = ViewManager.instance(this);

        viewManager.setRoot(MobileBrowseActivity.class);
        viewManager.register(SplashView.class, SplashActivity.class); // root activity, no parent
        viewManager.register(BrowseView.class, MobileBrowseActivity.class); // phone Home
        viewManager.register(PlaybackView.class, MobilePlaybackActivity.class, MobileBrowseActivity.class);
        viewManager.register(AppDialogView.class, MobileAppDialogActivity.class, MobilePlaybackActivity.class);
        viewManager.register(SearchView.class, MobileSearchActivity.class, MobileBrowseActivity.class);
        viewManager.register(SignInView.class, MobileSignInActivity.class, MobileBrowseActivity.class);
        viewManager.register(AddDeviceView.class, AddDeviceActivity.class, MobileBrowseActivity.class);
        viewManager.register(ChannelView.class, MobileChannelActivity.class, MobileBrowseActivity.class);
        viewManager.register(ChannelUploadsView.class, MobileChannelUploadsActivity.class, MobileBrowseActivity.class);
        viewManager.register(WebBrowserView.class, WebBrowserActivity.class, MobileBrowseActivity.class);
    }
}
