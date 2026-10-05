package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager;
import com.liskovsoft.smartyoutubetv2.mobile.ui.base.MobileActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * The phone Settings screen. Opens on top of whatever the user was doing (Home, or a playing
 * video) so Back returns there untouched: Home doesn't reload, and the video is still there.
 *
 * The window is translucent (the layout paints its own opaque background) so the screen
 * underneath is only paused, never stopped: a stopped player goes through the background-playback
 * / engine-release logic, which is exactly what must not happen here.
 */
public class MobileSettingsActivity extends MobileActivity {
    /**
     * @param fromPlayer launched over the player: don't let it read this as the user leaving the
     *                   app (onUserLeaveHint drives PIP / background audio).
     */
    public static void start(Context context, boolean fromPlayer) {
        Intent intent = new Intent(context, MobileSettingsActivity.class);
        if (fromPlayer) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        }
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    @Override
    protected void initTheme() {
        // Last setTheme of the super.onCreate chain, so translucency survives (see MobileAppDialogActivity).
        setTheme(R.style.Theme_SmarterTube_Mobile_TranslucentPanel);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_settings_activity);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        RecyclerView list = findViewById(R.id.settings_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new SettingsItemAdapter(
                MobileSettingsRows.build(this, AppDataSourceManager.instance().getSettingItems(this))));
    }

    @Override
    protected boolean registersInViewStack() {
        // A transient overlay like the settings dialog: as a ViewManager stack entry it would
        // become a Back target for the player's startParentView().
        return false;
    }
}
