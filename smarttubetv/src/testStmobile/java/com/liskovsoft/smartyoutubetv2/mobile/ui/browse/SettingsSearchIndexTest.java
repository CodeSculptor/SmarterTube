package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link SettingsSearchIndex}. Run with:
 * gradlew :smarttubetv:testStmobileDebugUnitTest
 */
public class SettingsSearchIndexTest {
    private static class Item implements OptionItem {
        private final String title;
        private final String desc;
        private final boolean selected;

        Item(String title, String desc, boolean selected) {
            this.title = title;
            this.desc = desc;
            this.selected = selected;
        }

        @Override public int getId() { return 0; }
        @Override public CharSequence getTitle() { return title; }
        @Override public CharSequence getDescription() { return desc; }
        @Override public boolean isSelected() { return selected; }
        @Override public void onSelect(boolean isSelected) { }
        @Override public Object getData() { return null; }
        @Override public void setRequired(OptionItem... items) { }
        @Override public OptionItem[] getRequired() { return null; }
        @Override public void setRadio(OptionItem... items) { }
        @Override public OptionItem[] getRadio() { return null; }
        @Override public ChatReceiver getChatReceiver() { return null; }
        @Override public CommentsReceiver getCommentsReceiver() { return null; }
    }

    private static List<SettingsSearchIndex.Entry> sample() {
        List<SettingsSearchIndex.Entry> all = new ArrayList<>();
        all.add(SettingsSearchIndex.section(0, "Player"));
        all.addAll(SettingsSearchIndex.fromCategories(0, "Player", Arrays.asList(
                OptionCategory.radioList("Video speed", Arrays.<OptionItem>asList(
                        new Item("0.5x", null, false), new Item("1x", null, true), new Item("2x", null, false))),
                OptionCategory.checkedList("Player buttons", Arrays.<OptionItem>asList(
                        new Item("Subtitles", "Show the CC button", true),
                        new Item("Video stats", null, false))),
                OptionCategory.singleSwitch(new Item("Remember speed", "Keep the last speed", false)))));
        all.add(SettingsSearchIndex.section(1, "General"));
        all.addAll(SettingsSearchIndex.fromCategories(1, "General", Arrays.asList(
                OptionCategory.radioList("Language", Arrays.<OptionItem>asList(
                        new Item("English", null, true), new Item("Hindi", null, false))))));
        return all;
    }

    @Test
    public void radioCategoryIsOneEntryWithCurrentValue() {
        List<SettingsSearchIndex.Entry> hits = SettingsSearchIndex.filter(sample(), "video speed");
        assertEquals(1, hits.size());
        assertEquals("Video speed", hits.get(0).title);
        assertEquals("1x", hits.get(0).summary);
        assertEquals("", hits.get(0).optionTitle);
        assertEquals("Player", hits.get(0).section);
    }

    @Test
    public void radioOptionLabelsAreSearchableButFindTheCategory() {
        List<SettingsSearchIndex.Entry> hits = SettingsSearchIndex.filter(sample(), "hindi");
        assertEquals(1, hits.size());
        assertEquals("Language", hits.get(0).title);
    }

    @Test
    public void checkboxOptionsAreIndividualEntriesUnderTheirCategory() {
        List<SettingsSearchIndex.Entry> hits = SettingsSearchIndex.filter(sample(), "subtitles");
        assertEquals(1, hits.size());
        assertEquals("Subtitles", hits.get(0).title);
        assertEquals("Player buttons", hits.get(0).category);
        assertEquals("Subtitles", hits.get(0).optionTitle);
        assertEquals("Player › Player buttons", hits.get(0).breadcrumb());
    }

    @Test
    public void descriptionsAreSearched() {
        List<SettingsSearchIndex.Entry> hits = SettingsSearchIndex.filter(sample(), "cc button");
        assertEquals(1, hits.size());
        assertEquals("Subtitles", hits.get(0).title);
    }

    @Test
    public void allWordsMustMatchInAnyOrderAndCaseInsensitive() {
        assertEquals(1, SettingsSearchIndex.filter(sample(), "SPEED remember").size());
        assertTrue(SettingsSearchIndex.filter(sample(), "speed nonsense").isEmpty());
    }

    @Test
    public void titleMatchesRankBeforeContextMatches() {
        List<SettingsSearchIndex.Entry> hits = SettingsSearchIndex.filter(sample(), "player");
        // The "Player" section itself first, then settings that only match through their section.
        assertEquals("Player", hits.get(0).title);
        assertTrue(hits.get(0).isSectionOnly());
        assertTrue(hits.size() > 1);
    }

    @Test
    public void blankQueryReturnsNothing() {
        assertTrue(SettingsSearchIndex.filter(sample(), "   ").isEmpty());
        assertTrue(SettingsSearchIndex.filter(sample(), null).isEmpty());
    }

    @Test
    public void topLevelSwitchHasNoCategory() {
        List<SettingsSearchIndex.Entry> hits = SettingsSearchIndex.filter(sample(), "remember speed");
        assertEquals("", hits.get(0).category);
        assertEquals("Remember speed", hits.get(0).optionTitle);
    }
}
