package org.wishy.files;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * A folder drawn with Canvas that tells you what is inside at a glance and opens when you open it.
 *  - empty folder: hollow, softly dimmed, no papers
 *  - folder with items: papers peek out of the top (1 to 3 depending on how full it is)
 *  - open fraction 0..1: the front flap swings down (perspective trapezoid) and the papers rise
 * Only transforms/paths are redrawn, and only while it animates.
 */
public final class FolderGlyph extends View {
    private int items = -1;          // -1 unknown, 0 empty, n items
    private int color = 0xFF888888;
    private int emblem = -1;         // optional category icon printed on the flap (Downloads, Pictures, ...)
    private float open;              // 0 closed .. 1 open
    private ValueAnimator va;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();

    public FolderGlyph(Context c) { super(c); }

    public FolderGlyph(Context c, int color, int items) { super(c); this.color = color; this.items = items; }

    public void set(int color, int items) { this.color = color; this.items = items; invalidate(); }

    public void emblem(int icon) { emblem = icon; invalidate(); }

    public boolean isEmpty() { return items == 0; }

    public void setOpenNow(float v) { cancel(); open = v; invalidate(); }

    /** Animate the flap to [to] (0..1). Emphasized easing; opening is a touch slower than closing so it feels weighty. */
    public void openTo(float to) {
        cancel();
        if (Math.abs(to - open) < 0.01f) return;
        va = ValueAnimator.ofFloat(open, to);
        va.setDuration(to > open ? 300 : 220);
        va.setInterpolator(UI.EASE);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { open = (Float) a.getAnimatedValue(); invalidate(); }
        });
        va.start();
    }

    private void cancel() { if (va != null) { va.cancel(); va = null; } }

    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); cancel(); }

    private static int mix(int a, int b, float t) { return Theme.over(b, a, t); }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h) / 100f;
        if (s <= 0) return;
        c.save();
        c.translate((w - 100 * s) / 2f, (h - 100 * s) / 2f);
        c.scale(s, s);
        boolean empty = items == 0;
        float o = open;
        int main = color, black = 0xFF000000, white = 0xFFFFFFFF;
        int back = mix(main, black, empty ? 0.10f : 0.24f);
        int cavity = mix(main, black, 0.42f);
        int alphaMul = empty ? 0x8C : 0xFF;
        p.setStyle(Paint.Style.FILL);
        p.setPathEffect(null);

        // back panel with its tab
        p.setColor(Theme.alpha(back, alphaMul / 255f));
        r.set(6, 24, 94, 84); c.drawRoundRect(r, 9, 9, p);
        r.set(6, 13, 44, 36); c.drawRoundRect(r, 8, 8, p);

        // the dark inside, revealed more as it opens
        p.setColor(Theme.alpha(cavity, (empty ? 0.55f : 0.8f) * Math.min(1f, 0.35f + o)));
        r.set(11, 29, 89, 58); c.drawRoundRect(r, 5, 5, p);

        // papers
        int n = items <= 0 ? 0 : items < 3 ? 1 : items < 10 ? 2 : 3;
        for (int i = n - 1; i >= 0; i--) {
            float lift = 14f * o * (0.55f + 0.45f * i);
            float top = 33f - 3.5f * i - lift;
            float rot = (i == 0 ? -4f : i == 1 ? 3.5f : -1.5f) * o;
            c.save();
            c.rotate(rot, 50, 70);
            p.setColor(mix(main, white, 0.90f - 0.11f * i));
            r.set(15 + 2.5f * i, top, 85 - 2.5f * i, top + 44);
            c.drawRoundRect(r, 4.5f, 4.5f, p);
            // two text lines on the paper once there is room to see them
            if (o > 0.15f) {
                p.setColor(Theme.alpha(mix(main, black, 0.35f), 0.35f * Math.min(1f, o * 1.6f)));
                r.set(22 + 2.5f * i, top + 8, 62 - 2.5f * i, top + 11.5f); c.drawRoundRect(r, 1.7f, 1.7f, p);
                r.set(22 + 2.5f * i, top + 15, 74 - 2.5f * i, top + 18.5f); c.drawRoundRect(r, 1.7f, 1.7f, p);
            }
            c.restore();
        }

        // front flap: a trapezoid whose top edge drops and widens as it opens (a flap swinging toward you)
        int front = mix(main, black, 0.10f * o);
        p.setColor(Theme.alpha(front, alphaMul / 255f));
        p.setPathEffect(new CornerPathEffect(9));
        float topY = 42f + 24f * o, grow = 4.5f * o;
        path.reset();
        path.moveTo(6 - grow, topY); path.lineTo(94 + grow, topY); path.lineTo(94, 84); path.lineTo(6, 84); path.close();
        c.drawPath(path, p);
        p.setPathEffect(null);

        // a soft highlight along the flap's top edge keeps it from looking flat
        p.setColor(Theme.alpha(white, empty ? 0.10f : 0.18f));
        r.set(10 - grow, topY + 1.5f, 90 + grow, topY + 4f); c.drawRoundRect(r, 1.6f, 1.6f, p);

        // emblem: printed on the flap, fades away as the flap drops
        if (emblem >= 0 && o < 0.8f) {
            float ea = (1f - o / 0.8f) * (empty ? 0.55f : 0.85f);
            Icons.draw(c, emblem, 35, 53f + 12f * o, 30, Theme.alpha(mix(main, black, 0.58f), ea));
        }
        c.restore();
    }
}
