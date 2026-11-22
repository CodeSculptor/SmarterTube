package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager;
import com.liskovsoft.smartyoutubetv2.mobile.ui.base.MobileActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.dialogs.SettingsSearchTarget;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The phone Settings screen. Opens on top of whatever the user was doing (Home, or a playing
 * video) so Back returns there untouched: Home doesn't reload, and the video is still there.
 *
 * The window is translucent (the layout paints its own opaque background) so the screen
 * underneath is only paused, never stopped: a stopped player goes through the background-playback
 * / engine-release logic, which is exactly what must not happen here.
 *
 * A search field above the list finds individual settings across all sections (see
 * {@link MobileSettingsSearch}); a result opens its section and lands on the setting.
 */
public class MobileSettingsActivity extends MobileActivity {
    private List<SettingsItem> mItems;
    private final Set<SettingsItem> mScannable = new HashSet<>();
    private List<SettingsSearchIndex.Entry> mIndex;
    private SettingsSearchAdapter mSearchAdapter;
    private RecyclerView mResults;
    private View mEmpty;
    private EditText mSearchBox;

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

        List<SettingsItem> upstream = AppDataSourceManager.instance().getSettingItems(this);
        mItems = MobileSettingsRows.build(this, upstream);

        RecyclerView list = findViewById(R.id.settings_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new SettingsItemAdapter(mItems));

        // Sections whose presenter can be read without side effects (see MobileSettingsSearch).
        // Accounts loads over the network and About is dropped from this screen: left out.
        String accounts = getString(R.string.settings_accounts);
        String about = getString(R.string.settings_about);
        for (SettingsItem item : upstream) {
            if (!accounts.equals(item.title) && !about.equals(item.title)) {
                mScannable.add(item);
            }
        }
        setupSearch();
    }

    private void setupSearch() {
        mSearchBox = findViewById(R.id.settings_search);
        mResults = findViewById(R.id.settings_search_results);
        mEmpty = findViewById(R.id.settings_search_empty);
        View clear = findViewById(R.id.settings_search_clear);

        mSearchAdapter = new SettingsSearchAdapter(this::openResult);
        mResults.setLayoutManager(new LinearLayoutManager(this));
        mResults.setAdapter(mSearchAdapter);

        clear.setOnClickListener(v -> mSearchBox.setText(""));
        mSearchBox.setOnEditorActionListener((v, actionId, event) -> {
            hideKeyboard();
            return true;
        });
        mSearchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                updateResults(s.toString());
            }
        });
    }

    private void updateResults(String query) {
        boolean searching = !query.trim().isEmpty();
        mResults.setVisibility(searching ? View.VISIBLE : View.GONE);
        if (!searching) {
            mEmpty.setVisibility(View.GONE);
            return;
        }
        if (mIndex == null) {
            mIndex = MobileSettingsSearch.build(mItems, mScannable);
        }
        List<SettingsSearchIndex.Entry> results = SettingsSearchIndex.filter(mIndex, query);
        mSearchAdapter.setResults(results);
        mEmpty.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openResult(SettingsSearchIndex.Entry entry) {
        hideKeyboard();
        SettingsItem section = mItems.get(entry.sectionPos);
        // Land on the exact row once the section's dialog is up (consumed by MobileAppDialogAdapter).
        SettingsSearchTarget.set(entry.category, entry.optionTitle);
        if (section.onClick != null) {
            section.onClick.run();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mSearchBox.getWindowToken(), 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from a section dialog: values may have changed, so re-read them on the next search.
        mIndex = null;
        if (mSearchBox != null && mSearchBox.length() > 0) {
            updateResults(mSearchBox.getText().toString());
        }
    }

    @Override
    protected boolean registersInViewStack() {
        // A transient overlay like the settings dialog: as a ViewManager stack entry it would
        // become a Back target for the player's startParentView().
        return false;
    }
}
