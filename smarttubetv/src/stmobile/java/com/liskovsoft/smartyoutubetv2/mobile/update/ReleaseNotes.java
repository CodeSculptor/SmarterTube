package com.liskovsoft.smartyoutubetv2.mobile.update;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns a GitHub release body (markdown) into the short "What's new" text shown in the app.
 *
 * <p>Release notes (written via {@code release.ps1 -NotesFile}) carry a {@code ## What's new}
 * section of bullets; that section is all the in-app popups show. Only the markdown the notes
 * actually use is handled: bullets (one nesting level), {@code **bold**}, {@code `code`} and
 * {@code [text](url)} links. Issue refs like {@code (#46)} are dropped — they mean nothing in the app.
 *
 * <p>Pure string logic, no Android dependencies — unit-tested.
 */
public final class ReleaseNotes {
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+.*");
    private static final Pattern WHATS_NEW = Pattern.compile("^#{1,6}\\s+what['’]?s\\s+new\\b.*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BULLET = Pattern.compile("^(\\s*)[-*+]\\s+(.*)$");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern CODE = Pattern.compile("`([^`]*)`");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern ISSUE_PAREN = Pattern.compile("\\s*\\(#\\d+\\)");
    private static final Pattern ISSUE_IN_PAREN = Pattern.compile(",\\s*#\\d+\\)");

    private ReleaseNotes() {
    }

    /**
     * @return the lines of the {@code What's new} section (heading excluded, blank edges trimmed),
     *         or {@code null} if the body has no such section or it is empty.
     */
    @Nullable
    public static List<String> extractWhatsNew(@Nullable String body) {
        if (body == null) {
            return null;
        }
        List<String> out = new ArrayList<>();
        boolean inSection = false;
        for (String raw : body.split("\\r?\\n")) {
            String line = raw.replaceAll("\\s+$", "");
            if (!inSection) {
                inSection = WHATS_NEW.matcher(line.trim()).matches();
                continue;
            }
            if (HEADING.matcher(line.trim()).matches()) {
                break; // next section
            }
            out.add(line);
        }
        while (!out.isEmpty() && out.get(0).trim().isEmpty()) {
            out.remove(0);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).trim().isEmpty()) {
            out.remove(out.size() - 1);
        }
        return out.isEmpty() ? null : out;
    }

    /**
     * Renders {@link #extractWhatsNew} output as the small HTML subset {@code HtmlCompat.fromHtml}
     * understands ({@code <b>}, {@code <br>}, entities). Returns {@code null} when there is nothing
     * to show.
     */
    @Nullable
    public static String whatsNewHtml(@Nullable String body) {
        List<String> lines = extractWhatsNew(body);
        if (lines == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean lastBlank = false;
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                if (!lastBlank && sb.length() > 0) {
                    sb.append("<br>");
                }
                lastBlank = true;
                continue;
            }
            lastBlank = false;
            if (sb.length() > 0) {
                sb.append("<br>");
            }
            java.util.regex.Matcher m = BULLET.matcher(line);
            if (m.matches()) {
                boolean nested = m.group(1).replace("\t", "  ").length() >= 2;
                sb.append(nested ? "&nbsp;&nbsp;&nbsp;&nbsp;◦&nbsp;" : "•&nbsp;");
                sb.append(inline(m.group(2)));
            } else if (Character.isWhitespace(line.charAt(0))) {
                sb.append("&nbsp;&nbsp;&nbsp;").append(inline(line.trim())); // bullet continuation
            } else {
                sb.append(inline(line.trim()));
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /** Release tag for display: drops the {@code +st<base>} metadata, e.g. {@code v0.7.0-beta.1}. */
    public static String displayTag(String tag) {
        if (tag == null) {
            return "";
        }
        int plus = tag.indexOf('+');
        return plus > 0 ? tag.substring(0, plus) : tag;
    }

    private static String inline(String text) {
        String t = ISSUE_PAREN.matcher(text).replaceAll("");
        t = ISSUE_IN_PAREN.matcher(t).replaceAll(")");
        t = LINK.matcher(t).replaceAll("$1");
        t = CODE.matcher(t).replaceAll("$1");
        t = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return BOLD.matcher(t).replaceAll("<b>$1</b>");
    }
}
