package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.SeekBar;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * The Modern player's seek bar (#46 follow-up): a plain SeekBar that also draws the coloured marks
 * upstream feeds the Leanback bar through {@code PlayerUI.setSeekBarSegments} — SponsorBlock
 * segments (only the categories the user enabled colour markers for) and chapter starts.
 *
 * Same semantics as upstream's {@code misc.SeekBar.setSegments}: a list adds marks, null clears
 * them all (a new video).
 */
public class ModernSeekBar extends SeekBar {
    private static final float MIN_MARK_DP = 2.5f;

    private final List<Mark> mMarks = new ArrayList<>();
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mMinMarkPx;
    private final float mMarkHeightPx;

    private static final class Mark {
        final float start;
        final float end;
        final int color;

        Mark(float start, float end, int color) {
            this.start = start;
            this.end = end;
            this.color = color;
        }
    }

    public ModernSeekBar(Context context) {
        this(context, null);
    }

    public ModernSeekBar(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.seekBarStyle);
    }

    public ModernSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = context.getResources().getDisplayMetrics().density;
        mMinMarkPx = MIN_MARK_DP * density;
        mMarkHeightPx = 3 * density; // the track height (layout's min/maxHeight)
    }

    public void addSegments(List<SeekBarSegment> segments) {
        if (segments == null) {
            mMarks.clear();
        } else {
            for (SeekBarSegment segment : segments) {
                if (segment == null || segment.startProgress < 0 || segment.endProgress < 0 || segment.startProgress >= 1) {
                    continue;
                }
                mMarks.add(new Mark(segment.startProgress, Math.min(segment.endProgress, 1f), segment.color));
            }
        }
        invalidate();
    }

    public boolean hasSegments() {
        return !mMarks.isEmpty();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mMarks.isEmpty()) {
            return;
        }

        // Marks sit on the track, under the thumb (redrawn on top below).
        float left = getPaddingLeft();
        float trackWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        float top = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom() - mMarkHeightPx) / 2f;
        for (Mark mark : mMarks) {
            float startX = left + mark.start * trackWidth;
            float endX = Math.max(left + mark.end * trackWidth, startX + mMinMarkPx);
            mPaint.setColor(mark.color);
            canvas.drawRect(startX, top, endX, top + mMarkHeightPx, mPaint);
        }

        Drawable thumb = getThumb();
        if (thumb != null) {
            // Same transform AbsSeekBar.drawThumb uses.
            int saveCount = canvas.save();
            canvas.translate(getPaddingLeft() - getThumbOffset(), getPaddingTop());
            thumb.draw(canvas);
            canvas.restoreToCount(saveCount);
        }
    }
}
