package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.util.Log;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Builds the {@link SettingsSearchIndex} for the phone Settings screen.
 *
 * Settings content isn't stored anywhere: each section's presenter assembles its option categories
 * inside show() and hands them to AppDialogPresenter. To read them without opening anything, each
 * scannable section's action is run once while AppDialogPresenter's capture hook is set (it hands
 * over the categories and starts no view). The index is built lazily on first search and again
 * after returning to the screen, so the shown current values stay fresh.
 */
final class MobileSettingsSearch {
    private static final String TAG = MobileSettingsSearch.class.getSimpleName();

    private MobileSettingsSearch() {
    }

    /**
     * @param items     the Settings list, in display order (an entry's sectionPos indexes it)
     * @param scannable sections whose categories may be captured; others (phone-only rows that open
     *                  an AlertDialog, the async Accounts screen) are indexed by title only
     */
    static List<SettingsSearchIndex.Entry> build(List<SettingsItem> items, Set<SettingsItem> scannable) {
        List<SettingsSearchIndex.Entry> index = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            SettingsItem item = items.get(i);
            index.add(SettingsSearchIndex.section(i, item.title));
            if (item.onClick == null || !scannable.contains(item)) {
                continue;
            }
            final int pos = i;
            AppDialogPresenter.setCaptureListener((title, categories) ->
                    index.addAll(SettingsSearchIndex.fromCategories(pos, item.title, categories)));
            try {
                item.onClick.run();
            } catch (RuntimeException e) {
                // One section failing to describe itself must not break search for the rest.
                Log.e(TAG, "Can't index settings section " + item.title, e);
            } finally {
                AppDialogPresenter.setCaptureListener(null);
            }
        }
        return index;
    }
}
