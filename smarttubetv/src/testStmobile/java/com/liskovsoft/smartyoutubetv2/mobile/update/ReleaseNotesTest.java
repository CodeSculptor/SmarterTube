package com.liskovsoft.smartyoutubetv2.mobile.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** Unit tests for {@link ReleaseNotes}. Run with: gradlew :smarttubetv:testStmobileDebugUnitTest */
public class ReleaseNotesTest {
    private static final String BODY = "Phone/tablet companion build of SmartTube, based on upstream SmartTube 32.59.\r\n"
            + "\r\n"
            + "## What's new\r\n"
            + "\r\n"
            + "- **Player style setting** (Settings > Player style, #46). Pick one:\r\n"
            + "  - **Modern**: phone-style controls.\r\n"
            + "\r\n"
            + "  New installs start on Modern.\r\n"
            + "- **Sliders are back** (#44). See [the docs](https://example.com) & `arm64-v8a`.\r\n"
            + "\r\n"
            + "## Install\r\n"
            + "\r\n"
            + "Download the APK.\r\n";

    @Test
    public void extractsOnlyTheWhatsNewSection() {
        List<String> lines = ReleaseNotes.extractWhatsNew(BODY);
        assertEquals(5, lines.size());
        assertTrue(lines.get(0).startsWith("- **Player style"));
        assertTrue(lines.get(4).startsWith("- **Sliders"));
    }

    @Test
    public void rendersBulletsBoldAndStripsIssueRefs() {
        String html = ReleaseNotes.whatsNewHtml(BODY);
        assertEquals("\u2022&nbsp;<b>Player style setting</b> (Settings &gt; Player style). Pick one:"
                + "<br>&nbsp;&nbsp;&nbsp;&nbsp;\u25E6&nbsp;<b>Modern</b>: phone-style controls."
                + "<br><br>&nbsp;&nbsp;&nbsp;New installs start on Modern."
                + "<br>\u2022&nbsp;<b>Sliders are back</b>. See the docs &amp; arm64-v8a.", html);
    }

    @Test
    public void acceptsCurlyApostropheAndOtherLevels() {
        assertEquals(1, ReleaseNotes.extractWhatsNew("### What\u2019s New\n- one\n").size());
    }

    @Test
    public void nullWhenNoSectionOrEmpty() {
        assertNull(ReleaseNotes.whatsNewHtml(null));
        assertNull(ReleaseNotes.whatsNewHtml("## Install\n- x\n"));
        assertNull(ReleaseNotes.whatsNewHtml("## What's new\n\n## Install\n- x\n"));
    }

    @Test
    public void displayTagDropsUpstreamBase() {
        assertEquals("v0.7.0-beta.1", ReleaseNotes.displayTag("v0.7.0-beta.1+st32.59"));
        assertEquals("v1.0.0", ReleaseNotes.displayTag("v1.0.0"));
    }
}
