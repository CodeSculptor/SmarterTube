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
import com.liskovsoft.smartyoutubetv2.tv.ui.adddevice.AddDeviceActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.channel.ChannelActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.channeluploads.ChannelUploadsActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.AppDialogActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.search.tags.SearchTagsActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.signin.SignInActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.webbrowser.WebBrowserActivity;

/**
 * Application class for the phone (stmobile) flavor.
 *
 * Overrides view routing so the phone build uses its own native portrait Home
 * ({@link MobileBrowseActivity}) instead of the TV Leanback BrowseActivity. Every other
 * screen is reused from the TV code unchanged (Phases 3-5 will replace them in turn).
 */
public class MobileApplication extends MainApplication {
    @Override
    protected void setupViewManager() {
        ViewManager viewManager = ViewManager.instance(this);

        viewManager.setRoot(MobileBrowseActivity.class);
        viewManager.register(SplashView.class, SplashActivity.class); // root activity, no parent
        viewManager.register(BrowseView.class, MobileBrowseActivity.class); // phone Home
        viewManager.register(PlaybackView.class, PlaybackActivity.class, MobileBrowseActivity.class);
        viewManager.register(AppDialogView.class, AppDialogActivity.class, PlaybackActivity.class);
        viewManager.register(SearchView.class, SearchTagsActivity.class, MobileBrowseActivity.class);
        viewManager.register(SignInView.class, SignInActivity.class, MobileBrowseActivity.class);
        viewManager.register(AddDeviceView.class, AddDeviceActivity.class, MobileBrowseActivity.class);
        viewManager.register(ChannelView.class, ChannelActivity.class, MobileBrowseActivity.class);
        viewManager.register(ChannelUploadsView.class, ChannelUploadsActivity.class, MobileBrowseActivity.class);
        viewManager.register(WebBrowserView.class, WebBrowserActivity.class, MobileBrowseActivity.class);
    }
}
