package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure (Android-free) searchable index of the settings screens: build entries from the option
 * categories a settings presenter produces, then filter them by a typed query.
 *
 * What becomes an entry:
 *  - the section itself (e.g. "Player");
 *  - a section's category header (e.g. "Seek interval");
 *  - a single-choice (radio) category: ONE entry, titled by the category, showing the current value;
 *    its option labels are searchable but don't clutter the results (typing "hindi" finds
 *    "Language", not 90 separate rows). This matches how the phone UI renders such a category, as
 *    one value row or slider;
 *  - every option of a checkbox / switch / button / string list, individually.
 */
final class SettingsSearchIndex {
    private static final int MAX_RESULTS = 80;

    private SettingsSearchIndex() {
    }

    static final class Entry {
        /** Position of the owning section in the Settings list; the jump re-opens that section. */
        final int sectionPos;
        final String section;
        /** Category header the setting sits under, or "" when it's a top-level switch/button. */
        final String category;
        final String title;
        /** Description or, for a single-choice category, the current value. */
        final String summary;
        /** Searchable-only text (a single-choice category's option labels). */
        final String extra;
        /** The option row to land on, or "" to land on the category (or the section when both empty). */
        final String optionTitle;

        Entry(int sectionPos, String section, String category, String title, String summary,
              String extra, String optionTitle) {
            this.sectionPos = sectionPos;
            this.section = section;
            this.category = category;
            this.title = title;
            this.summary = summary;
            this.extra = extra;
            this.optionTitle = optionTitle;
        }

        boolean isSectionOnly() {
            return category.isEmpty() && optionTitle.isEmpty() && title.equals(section);
        }

        /** "Section" or "Section > Category" under the result title. */
        String breadcrumb() {
            if (isSectionOnly()) {
                return "";
            }
            if (!category.isEmpty() && !category.equals(title)) {
                return section + " › " + category;
            }
            return section;
        }
    }

    static Entry section(int sectionPos, CharSequence title) {
        String t = str(title);
        return new Entry(sectionPos, t, "", t, "", "", "");
    }

    /** Entries for one section's categories (as captured from its presenter's show()). */
    static List<Entry> fromCategories(int sectionPos, CharSequence sectionTitle,
                                      List<OptionCategory> categories) {
        List<Entry> out = new ArrayList<>();
        if (categories == null) {
            return out;
        }
        String section = str(sectionTitle);
        for (OptionCategory category : categories) {
            if (category == null || category.options == null || category.options.isEmpty()
                    || category.type == OptionCategory.TYPE_COMMENTS
                    || category.type == OptionCategory.TYPE_CHAT
                    || category.type == OptionCategory.TYPE_LONG_TEXT) {
                continue;
            }
            String catTitle = str(category.title);
            if (category.type == OptionCategory.TYPE_RADIO_LIST && !catTitle.isEmpty()) {
                StringBuilder labels = new StringBuilder();
                String current = "";
                for (OptionItem option : category.options) {
                    labels.append(str(option.getTitle())).append(' ');
                    if (current.isEmpty() && option.isSelected()) {
                        current = str(option.getTitle());
                    }
                }
                out.add(new Entry(sectionPos, section, catTitle, catTitle, current, labels.toString(), ""));
                continue;
            }
            boolean single = category.type == OptionCategory.TYPE_SINGLE_SWITCH
                    || category.type == OptionCategory.TYPE_SINGLE_BUTTON;
            if (!single && !catTitle.isEmpty()) {
                out.add(new Entry(sectionPos, section, catTitle, catTitle, "", "", ""));
            }
            for (OptionItem option : category.options) {
                String title = str(option.getTitle());
                if (title.isEmpty()) {
                    continue;
                }
                out.add(new Entry(sectionPos, section, catTitle, title, str(option.getDescription()), "", title));
            }
        }
        return out;
    }

    /**
     * Entries matching {@code query}: every whitespace-separated word must appear (case-insensitive)
     * somewhere in the entry. Title hits rank first, then category/section, then summary, then
     * option labels; ties keep the settings' own order.
     */
    static List<Entry> filter(List<Entry> all, String query) {
        List<Entry> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty() || all == null) {
            return out;
        }
        String[] words = q.split("\\s+");
        List<int[]> scored = new ArrayList<>(); // {score, index}
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < all.size(); i++) {
            Entry e = all.get(i);
            int score = score(e, words);
            if (score < 0 || !seen.add(e.section + "\u0001" + e.category + "\u0001" + e.title)) {
                continue;
            }
            scored.add(new int[]{score, i});
        }
        scored.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        for (int[] s : scored) {
            out.add(all.get(s[1]));
            if (out.size() >= MAX_RESULTS) {
                break;
            }
        }
        return out;
    }

    /** Lower is better; -1 = no match. */
    private static int score(Entry e, String[] words) {
        String title = e.title.toLowerCase(Locale.ROOT);
        String context = (e.category + " " + e.section).toLowerCase(Locale.ROOT);
        String summary = e.summary.toLowerCase(Locale.ROOT);
        String extra = e.extra.toLowerCase(Locale.ROOT);
        int worst = 0;
        boolean allInTitle = true;
        for (String w : words) {
            int s;
            if (title.contains(w)) {
                s = 0;
            } else if (context.contains(w)) {
                s = 1;
                allInTitle = false;
            } else if (summary.contains(w)) {
                s = 2;
                allInTitle = false;
            } else if (extra.contains(w)) {
                s = 3;
                allInTitle = false;
            } else {
                return -1;
            }
            worst = Math.max(worst, s);
        }
        // Title starting with the first word beats a title merely containing it.
        return allInTitle && title.startsWith(words[0]) ? 0 : worst + (allInTitle ? 1 : 0);
    }

    private static String str(CharSequence text) {
        return text == null ? "" : text.toString().trim();
    }
}
