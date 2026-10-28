package org.wishy.files;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Quick-settings style grid. Children ask for a column span and a row span; they are packed first-fit in order, so putting the
 * most important tile first gives it the biggest, highest spot and the rest settle in around it with no holes.
 * Never clips (focus zoom needs room). Row height is fixed by the owner so every tile of the same span is the same size.
 */
public final class TileGrid extends ViewGroup {
    public int cols = 4, gap = UI.dp(12), rowH = UI.dp(96);

    public static final class LP extends ViewGroup.LayoutParams {
        public int cs, rs; int c, r;
        public LP(int cs, int rs) { super(-1, -2); this.cs = cs; this.rs = rs; }
    }

    public TileGrid(Context c) { super(c); setClipChildren(false); setClipToPadding(false); }

    public void add(View v, int cs, int rs) { addView(v, new LP(Math.max(1, Math.min(cs, cols)), Math.max(1, rs))); }

    @Override protected boolean checkLayoutParams(ViewGroup.LayoutParams p) { return p instanceof LP; }
    @Override protected ViewGroup.LayoutParams generateDefaultLayoutParams() { return new LP(1, 1); }
    @Override protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) { return new LP(1, 1); }

    private int rowsUsed;

    private void place() {
        int n = getChildCount(), cap = 4;
        for (int i = 0; i < n; i++) cap += ((LP) getChildAt(i).getLayoutParams()).rs;
        boolean[][] used = new boolean[cap][cols];
        rowsUsed = 0;
        for (int i = 0; i < n; i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            LP lp = (LP) v.getLayoutParams();
            int cs = Math.min(lp.cs, cols), rs = lp.rs;
            boolean ok = false;
            for (int r = 0; r + rs <= cap && !ok; r++) {
                for (int c = 0; c + cs <= cols && !ok; c++) {
                    boolean free = true;
                    for (int y = r; y < r + rs && free; y++) for (int x = c; x < c + cs; x++) if (used[y][x]) { free = false; break; }
                    if (!free) continue;
                    for (int y = r; y < r + rs; y++) for (int x = c; x < c + cs; x++) used[y][x] = true;
                    lp.c = c; lp.r = r; ok = true; rowsUsed = Math.max(rowsUsed, r + rs);
                }
            }
        }
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
        place();
        float cw = (w - gap * (cols - 1)) / (float) cols;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            LP lp = (LP) v.getLayoutParams();
            int vw = Math.round(cw * lp.cs + gap * (lp.cs - 1)), vh = rowH * lp.rs + gap * (lp.rs - 1);
            v.measure(MeasureSpec.makeMeasureSpec(vw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(vh, MeasureSpec.EXACTLY));
        }
        int h = rowsUsed == 0 ? 0 : rowsUsed * rowH + (rowsUsed - 1) * gap;
        setMeasuredDimension(MeasureSpec.getSize(wSpec), h + getPaddingTop() + getPaddingBottom());
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l - getPaddingLeft() - getPaddingRight();
        float cw = (w - gap * (cols - 1)) / (float) cols;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            LP lp = (LP) v.getLayoutParams();
            int x = getPaddingLeft() + Math.round((cw + gap) * lp.c), y = getPaddingTop() + (rowH + gap) * lp.r;
            v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
        }
    }
}
