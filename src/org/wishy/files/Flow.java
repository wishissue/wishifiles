package org.wishy.files;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Wrapping row: children flow onto new lines instead of running off the screen. Never clips (focus zoom needs room). */
public final class Flow extends ViewGroup {
    public static final int START = 0, CENTER = 1, END = 2;
    public int hGap = UI.dp(10), vGap = UI.dp(10), align = START;

    public Flow(Context c) { super(c); setClipChildren(false); setClipToPadding(false); }

    private void measureKid(View k, int avail) {
        LayoutParams lp = k.getLayoutParams();
        int w = getChildMeasureSpec(MeasureSpec.makeMeasureSpec(Math.max(0, avail), MeasureSpec.AT_MOST), 0, lp.width);
        int h = getChildMeasureSpec(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0, lp.height);
        k.measure(w, h);
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int mode = MeasureSpec.getMode(wSpec), size = MeasureSpec.getSize(wSpec);
        int avail = (mode == MeasureSpec.UNSPECIFIED ? 100000 : size) - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, lineH = 0, maxX = 0; boolean any = false;
        for (int i = 0; i < getChildCount(); i++) {
            View k = getChildAt(i); if (k.getVisibility() == GONE) continue;
            measureKid(k, avail);
            int cw = k.getMeasuredWidth(), ch = k.getMeasuredHeight();
            if (x > 0 && x + cw > avail) { y += lineH + vGap; x = 0; lineH = 0; }
            x += cw + hGap; lineH = Math.max(lineH, ch); maxX = Math.max(maxX, x - hGap); any = true;
        }
        int h = (any ? y + lineH : 0) + getPaddingTop() + getPaddingBottom();
        int w = mode == MeasureSpec.EXACTLY ? size : Math.min(mode == MeasureSpec.AT_MOST ? size : 100000, maxX + getPaddingLeft() + getPaddingRight());
        setMeasuredDimension(w, resolveSize(h, hSpec));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int avail = r - l - getPaddingLeft() - getPaddingRight();
        int n = getChildCount(), i = 0, y = getPaddingTop();
        while (i < n) {
            int j = i, x = 0, lineH = 0;
            // find the end of this line
            while (j < n) {
                View k = getChildAt(j);
                if (k.getVisibility() == GONE) { j++; continue; }
                int cw = k.getMeasuredWidth();
                if (x > 0 && x + cw > avail) break;
                x += cw + hGap; lineH = Math.max(lineH, k.getMeasuredHeight()); j++;
            }
            int lineW = Math.max(0, x - hGap);
            int cx = getPaddingLeft() + (align == CENTER ? (avail - lineW) / 2 : align == END ? avail - lineW : 0);
            for (int q = i; q < j; q++) {
                View k = getChildAt(q); if (k.getVisibility() == GONE) continue;
                int cw = k.getMeasuredWidth(), ch = k.getMeasuredHeight();
                k.layout(cx, y + (lineH - ch) / 2, cx + cw, y + (lineH - ch) / 2 + ch);
                cx += cw + hGap;
            }
            y += lineH + vGap;
            if (j == i) break;
            i = j;
        }
    }
}
