package com.liskovsoft.smartyoutubetv2.mobile.ui.about;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.liskovsoft.smartyoutubetv2.tv.BuildConfig;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * About screen — the fork-attribution surface. Shows app name, current version (from
 * the stmobile flavor block in build.gradle, NOT upstream defaultConfig), and explicit
 * credit + links to upstream SmartTube and this fork. This is the in-app "this is a
 * fork, not original code" disclosure.
 */
public class MobileAboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_about_activity);

        ((TextView) findViewById(R.id.about_version))
                .setText(getString(R.string.mobile_about_version, BuildConfig.VERSION_NAME));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        findViewById(R.id.link_upstream).setOnClickListener(v ->
                openUrl(getString(R.string.mobile_about_url_upstream)));
        findViewById(R.id.link_fork).setOnClickListener(v ->
                openUrl(getString(R.string.mobile_about_url_fork)));
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }
}
