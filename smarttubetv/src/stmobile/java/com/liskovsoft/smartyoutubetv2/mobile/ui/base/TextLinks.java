package com.liskovsoft.smartyoutubetv2.mobile.ui.base;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes URLs, #hashtags and m:ss timestamps in a description / comment tappable, without
 * LinkMovementMethod (which would swallow every tap on the text view). Callers resolve the tapped
 * span themselves via {@link #spanAt}, so a tap elsewhere in the same view keeps its old behaviour
 * (collapse the description, open the reply thread).
 */
public final class TextLinks {
    public interface TimestampHandler {
        void onTimestamp(long positionMs);
    }

    private static final int LINK_COLOR = 0xFF3EA6FF;
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"]+");
    private static final Pattern HASHTAG = Pattern.compile("(?<![\\p{L}\\p{N}_&/#])#[\\p{L}\\p{N}_]+");
    private static final Pattern TIMESTAMP = Pattern.compile("(?<![\\d:])(?:\\d{1,2}:)?[0-5]?\\d:[0-5]\\d(?![\\d:])");

    private TextLinks() {
    }

    private static final Pattern YT_VIDEO = Pattern.compile(
            "https?://(?:www\\.|m\\.)?(?:youtube\\.com/watch\\?(?:[^\\s<>\"]*?&)?v=|youtu\\.be/)([A-Za-z0-9_-]{11})[^\\s<>\"]*");

    /** Distinct YouTube video links in the text as {url, videoId}, at most {@code max}. */
    public static java.util.List<String[]> youtubeVideoLinks(CharSequence text, int max) {
        java.util.List<String[]> result = new java.util.ArrayList<>();
        if (text == null) {
            return result;
        }
        Matcher m = YT_VIDEO.matcher(text);
        while (result.size() < max && m.find()) {
            String id = m.group(1);
            boolean dup = false;
            for (String[] r : result) {
                if (r[1].equals(id)) dup = true;
            }
            if (!dup) {
                String url = m.group();
                while (url.length() > 1 && ".,;:!?)]}'".indexOf(url.charAt(url.length() - 1)) >= 0) {
                    url = url.substring(0, url.length() - 1);
                }
                result.add(new String[]{url, id});
            }
        }
        return result;
    }

    /** Up to {@code max} distinct hashtags from the text, space separated; null if none. */
    public static String hashtags(CharSequence text, int max) {
        if (text == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        Matcher m = HASHTAG.matcher(text);
        while (count < max && m.find()) {
            String tag = m.group();
            if (sb.indexOf(tag) >= 0) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(tag);
            count++;
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * @param timestamps null to leave timestamps as plain text (comments on a non-playing video)
     */
    public static SpannableString linkify(Context context, CharSequence text, TimestampHandler timestamps) {
        SpannableString result = new SpannableString(text != null ? text : "");
        if (result.length() == 0) {
            return result;
        }
        final Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        String s = result.toString();
        boolean[] taken = new boolean[s.length()];

        Matcher m = URL.matcher(s);
        while (m.find()) {
            int end = m.end();
            // Trailing sentence punctuation isn't part of the link.
            while (end > m.start() + 1 && ".,;:!?)]}'".indexOf(s.charAt(end - 1)) >= 0) {
                end--;
            }
            final String url = s.substring(m.start(), end);
            mark(result, taken, m.start(), end, new LinkSpan(() -> openUrl(appContext, url)));
        }

        m = HASHTAG.matcher(s);
        while (m.find()) {
            if (taken[m.start()]) continue;
            final String tag = m.group();
            mark(result, taken, m.start(), m.end(), new LinkSpan(() ->
                    SearchPresenter.instance(appContext).startSearch(tag)));
        }

        if (timestamps != null) {
            m = TIMESTAMP.matcher(s);
            while (m.find()) {
                if (taken[m.start()]) continue;
                final long ms = parseTimestampMs(m.group());
                mark(result, taken, m.start(), m.end(), new LinkSpan(() -> timestamps.onTimestamp(ms)));
            }
        }

        return result;
    }

    /** The link under the touch point, or null. */
    public static ClickableSpan spanAt(TextView view, MotionEvent event) {
        CharSequence text = view.getText();
        Layout layout = view.getLayout();
        if (!(text instanceof Spanned) || layout == null) {
            return null;
        }
        float x = event.getX() - view.getTotalPaddingLeft() + view.getScrollX();
        float y = event.getY() - view.getTotalPaddingTop() + view.getScrollY();
        if (y < 0 || y > layout.getHeight()) {
            return null;
        }
        int line = layout.getLineForVertical((int) y);
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) {
            return null;
        }
        int offset = layout.getOffsetForHorizontal(line, x);
        ClickableSpan[] spans = ((Spanned) text).getSpans(offset, offset, ClickableSpan.class);
        return spans.length > 0 ? spans[0] : null;
    }

    private static void mark(SpannableString s, boolean[] taken, int start, int end, Object span) {
        s.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        for (int i = start; i < end; i++) {
            taken[i] = true;
        }
    }

    private static long parseTimestampMs(String ts) {
        long total = 0;
        for (String part : ts.split(":")) {
            total = total * 60 + Long.parseLong(part);
        }
        return total * 1000;
    }

    public static void openUrl(Context context, String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            // YouTube links open in this app (it has the intent filter), everything else externally.
            if (host.equals("youtu.be") || host.equals("youtube.com") || host.endsWith(".youtube.com")) {
                intent.setPackage(context.getPackageName());
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            // No handler for the link — nothing sensible to do.
        }
    }

    private static final class LinkSpan extends ClickableSpan {
        private final Runnable mAction;

        LinkSpan(Runnable action) {
            mAction = action;
        }

        @Override
        public void onClick(View widget) {
            mAction.run();
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            ds.setColor(LINK_COLOR);
            ds.setUnderlineText(false);
        }
    }
}
