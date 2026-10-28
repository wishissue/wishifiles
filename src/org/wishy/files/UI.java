package org.wishy.files;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.*;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.*;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.*;

/** Small view toolkit: rounded surfaces, focus-animated rows/chips, page scaffold. All native Views, remote friendly. */
public final class UI {
    public interface Act { void run(); }

    /** Material "emphasized decelerate": quick start, long soft landing. Used for everything that arrives. */
    public static final Interpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    /** Material "emphasized accelerate": things leaving the screen speed away. */
    public static final Interpolator EASE_OUT = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);

    public static int dp(float v) { return Theme.dp(v); }

    public static GradientDrawable rr(int color, float rdp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(rdp));
        return g;
    }

    /** Touch ripple clipped to the same rounded shape. */
    public static Drawable ripple(Drawable content, float rdp) {
        return new RippleDrawable(ColorStateList.valueOf(Theme.alpha(Theme.c(Theme.P), 0.22f)), content, rr(0xFFFFFFFF, rdp));
    }

    /** Selector: normal fill, focused = container + ring, activated = secondary container + ring. A ripple answers touch. */
    public static Drawable sel(int normal, float rdp) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable f = rr(Theme.c(Theme.PC), rdp);
        f.setStroke(dp(3), Theme.c(Theme.P));
        GradientDrawable a = rr(Theme.c(Theme.SC), rdp);
        a.setStroke(dp(2), Theme.c(Theme.P));
        s.addState(new int[]{android.R.attr.state_focused}, f);
        s.addState(new int[]{android.R.attr.state_selected}, f);
        s.addState(new int[]{android.R.attr.state_activated}, a);
        s.addState(new int[]{}, rr(normal, rdp));
        s.setEnterFadeDuration(110);
        s.setExitFadeDuration(150);
        return ripple(s, rdp);
    }

    /** Surface for tiles: keeps its own colour when focused and adds a clear ring instead (tiles come in many tones). */
    public static Drawable tileSel(int bg, float rdp) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable f = rr(bg, rdp);
        f.setStroke(dp(3.5f), Theme.c(Theme.ON_BG));
        GradientDrawable a = rr(Theme.over(Theme.c(Theme.P), bg, 0.30f), rdp);
        a.setStroke(dp(3), Theme.c(Theme.P));
        s.addState(new int[]{android.R.attr.state_focused}, f);
        s.addState(new int[]{android.R.attr.state_activated}, a);
        s.addState(new int[]{}, rr(bg, rdp));
        s.setEnterFadeDuration(110);
        s.setExitFadeDuration(160);
        return ripple(s, rdp);
    }

    public static TextView tv(String s, float sp, boolean bold, int color) {
        TextView t = new TextView(App.ctx);
        t.setText(s);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Theme.dp(sp));
        t.setTextColor(color);
        t.setTypeface(bold ? Theme.semi : Theme.reg);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView body(String s) { return tv(s, 15, false, Theme.c(Theme.ON_SV)); }

    /** Room (in dp) around scrolling content so a focus-zoomed item is never cut off by its scroller. */
    public static int grow() { return dp(12); }

    /** Zoom actually used: the requested scale, but never more than ~9dp of growth per side, so wide rows can't hit the screen edge. */
    static float eff(View x) {
        Object o = x.getTag(R.id.k_scale);
        float base = o instanceof Float ? (Float) o : 1f;
        int w = x.getWidth();
        if (w <= 0) return base;
        return Math.max(1f, Math.min(base, 1f + 2f * dp(9) / w));
    }

    /** Make a view D-pad focusable with a gentle zoom when focused. Zoomed items also rise above their neighbours.
     *  Only transforms are animated (no layout, no colour), so it stays smooth on weak TV GPUs. */
    public static <T extends View> T focusable(T v, final float scale) {
        v.setFocusable(true);
        v.setClickable(true);
        v.setTag(R.id.k_scale, Float.valueOf(scale));
        v.setOutlineProvider(null);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View x, boolean has) { focusAnim(x, has); }
        });
        return v;
    }

    static void focusAnim(View x, boolean has) {
        float s = has ? eff(x) : 1f;
        x.animate().setStartDelay(0).scaleX(s).scaleY(s).translationZ(has ? dp(6) : 0).setDuration(has ? 200 : 150).setInterpolator(EASE).start();
    }

    /** Quick squash-and-bounce when something is pressed. */
    static void pop(final View x) {
        final float back = x.isFocused() ? eff(x) : 1f;
        x.animate().setStartDelay(0).scaleX(back * 0.955f).scaleY(back * 0.955f).setDuration(70).setInterpolator(EASE).withEndAction(new Runnable() {
            @Override public void run() {
                x.animate().setStartDelay(0).scaleX(back).scaleY(back).setDuration(220).setInterpolator(new OvershootInterpolator(2.4f)).start();
            }
        }).start();
    }

    /** A new screen eases in (slides [fromX] px while fading); a short delay lets the outgoing screen start fading first. */
    static void enter(View v, float fromX) {
        v.setAlpha(0f); v.setTranslationX(fromX); v.setScaleX(0.97f); v.setScaleY(0.97f);
        v.animate().alpha(1f).translationX(0).scaleX(1f).scaleY(1f).setStartDelay(50).setDuration(300).setInterpolator(EASE).start();
    }

    /** The outgoing screen slides [toX] px and fades, then is removed. It can no longer take focus or clicks. */
    static void leave(final ViewGroup parent, final View v, float toX) {
        if (v instanceof ViewGroup) ((ViewGroup) v).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        v.setEnabled(false); v.setClickable(false);
        v.clearFocus();
        v.animate().alpha(0f).translationX(toX).scaleX(0.97f).scaleY(0.97f).setDuration(140).setInterpolator(EASE_OUT).withEndAction(new Runnable() {
            @Override public void run() { parent.removeView(v); }
        }).start();
    }

    /** Scale-and-fade a view in with a small overshoot (success ticks, empty states). */
    static void popIn(View v) {
        v.setScaleX(0.55f); v.setScaleY(0.55f); v.setAlpha(0f);
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(120).setDuration(380).setInterpolator(new OvershootInterpolator(1.8f)).start();
    }

    public static View click(View v, final Act a) {
        v.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) { pop(x); a.run(); } });
        return v;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    public static LinearLayout.LayoutParams lpw(int wDp, int hDp, float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(wDp < 0 ? wDp : dp(wDp), hDp < 0 ? hDp : dp(hDp), weight);
        return p;
    }

    public static LinearLayout vbox() { LinearLayout l = new LinearLayout(App.ctx); l.setOrientation(LinearLayout.VERTICAL); l.setClipChildren(false); l.setClipToPadding(false); return l; }
    public static LinearLayout hbox() { LinearLayout l = new LinearLayout(App.ctx); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); l.setClipChildren(false); l.setClipToPadding(false); return l; }

    public static Flow flow() { return new Flow(App.ctx); }

    public static View space(int wDp, int hDp) { View v = new View(App.ctx); v.setLayoutParams(lp(dp(wDp), dp(hDp))); return v; }

    /** Rounded tile holding an icon. */
    public static View iconTile(int icon, int sizeDp, int bg, int fg) { return iconTile(icon, sizeDp, bg, fg, 0, 0); }

    /** Tile that follows its parent's focus: on focus it flips to [focusBg] / [focusFg] (pass 0 to keep it fixed). */
    public static View iconTile(int icon, int sizeDp, int bg, int fg, int focusBg, int focusFg) {
        FrameLayout f = new FrameLayout(App.ctx);
        float rad = sizeDp * 0.32f;
        Icons.IconView iv;
        if (focusBg != 0) {
            StateListDrawable s = new StateListDrawable();
            GradientDrawable fb = rr(focusBg, rad);
            s.addState(new int[]{android.R.attr.state_focused}, fb);
            s.addState(new int[]{android.R.attr.state_selected}, fb);
            s.addState(new int[]{android.R.attr.state_pressed}, fb);
            s.addState(new int[]{}, rr(bg, rad));
            s.setEnterFadeDuration(110); s.setExitFadeDuration(150);
            f.setBackground(s);
            f.setDuplicateParentStateEnabled(true);
            iv = new Icons.IconView(App.ctx, icon, fg, focusFg);
            iv.setDuplicateParentStateEnabled(true);
        } else {
            f.setBackground(rr(bg, rad));
            iv = new Icons.IconView(App.ctx, icon, fg);
        }
        int pad = dp(sizeDp * 0.17f);
        iv.setPadding(pad, pad, pad, pad);
        f.addView(iv, new FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        f.setLayoutParams(lp(dp(sizeDp), dp(sizeDp)));
        return f;
    }

    /** Clip a view's drawing to a rounded rectangle (thumbnails). */
    static void roundClip(View v, final float rdp) {
        v.setOutlineProvider(new ViewOutlineProvider() { @Override public void getOutline(View x, Outline o) { o.setRoundRect(0, 0, x.getWidth(), x.getHeight(), dp(rdp)); } });
        v.setClipToOutline(true);
    }

    /** Round icon-only button. [filled] = primary, otherwise a tonal (secondary container) circle. */
    public static View iconBtn(int icon, boolean filled, final Act a) {
        FrameLayout f = new FrameLayout(App.ctx);
        f.setBackground(sel(filled ? Theme.c(Theme.P) : Theme.c(Theme.SC), 28));
        Icons.IconView iv = new Icons.IconView(App.ctx, icon, filled ? Theme.c(Theme.ON_P) : Theme.c(Theme.ON_SC));
        f.addView(iv, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        f.setLayoutParams(lp(dp(50), dp(50)));
        focusable(f, 1.08f);
        if (a != null) click(f, a);
        return f;
    }

    /** App-bar action: no resting container, just the glyph; the focus ring and ripple appear on demand. */
    public static View barBtn(int icon, final Act a) {
        FrameLayout f = new FrameLayout(App.ctx);
        f.setBackground(sel(0x00000000, 26));
        Icons.IconView iv = new Icons.IconView(App.ctx, icon, Theme.c(Theme.ON_SV));
        f.addView(iv, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        f.setLayoutParams(lp(dp(48), dp(48)));
        focusable(f, 1.1f);
        if (a != null) click(f, a);
        return f;
    }

    /** Small trailing icon for rows (chevron, tick, close). */
    public static View trail(int icon, int color) {
        Icons.IconView iv = new Icons.IconView(App.ctx, icon, color);
        iv.setLayoutParams(lp(dp(24), dp(24)));
        return iv;
    }

    /** Wishy-style ActionRow: icon tile, title, subtitle, optional trailing view. Selectable rows get a chevron unless they bring their own trailing view. */
    public static LinearLayout row(int icon, String title, String sub, View trailing, final Act a) {
        if (trailing == null && a != null) trailing = trail(Icons.CHEVRON, Theme.c(Theme.OUT));
        return rowImpl(iconTile(icon, 46, Theme.c(Theme.PC), Theme.c(Theme.ON_PC), Theme.c(Theme.P), Theme.c(Theme.ON_P)), title, sub, trailing, a);
    }

    /** Row without the automatic chevron (menus, toggles). */
    public static LinearLayout rowPlain(int icon, String title, String sub, View trailing, final Act a) {
        return rowImpl(iconTile(icon, 46, Theme.c(Theme.PC), Theme.c(Theme.ON_PC), Theme.c(Theme.P), Theme.c(Theme.ON_P)), title, sub, trailing, a);
    }

    /** Row with a fixed tile colour (status rows: success, warning, error). */
    public static LinearLayout rowTone(int icon, String title, String sub, int bg, int fg) {
        return rowImpl(iconTile(icon, 46, bg, fg), title, sub, null, null);
    }

    private static LinearLayout rowImpl(View tile, String title, String sub, View trailing, final Act a) {
        LinearLayout r = hbox();
        r.setPadding(dp(14), dp(12), dp(16), dp(12));
        r.setBackground(sel(Theme.c(Theme.CONT), 22));
        r.addView(tile);
        LinearLayout t = vbox();
        t.setPadding(dp(16), 0, dp(8), 0);
        TextView a1 = tv(title, 16.5f, true, Theme.c(Theme.ON_SURF));
        a1.setSingleLine(true); a1.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(a1);
        if (sub != null && sub.length() > 0) {
            TextView b = tv(sub, 13.5f, false, Theme.c(Theme.ON_SV));
            b.setLineSpacing(0, 1.1f);
            b.setPadding(0, dp(3), 0, 0);
            t.addView(b);
        }
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        if (trailing != null) r.addView(trailing);
        focusable(r, 1.02f);
        if (a != null) click(r, a);
        return r;
    }

    /** Section label: small, primary-coloured, with a little air above. */
    public static TextView section(String s) {
        TextView t = tv(s, 13, true, Theme.c(Theme.P));
        t.setLetterSpacing(0.04f);
        t.setPadding(dp(6), dp(22), 0, dp(8));
        return t;
    }

    /** Pill button. Filled = primary, otherwise tonal (secondary container). Optional leading icon. */
    public static LinearLayout chip(int icon, String label, boolean filled, final Act a) {
        return button(icon, label, filled ? 0 : 1, a);
    }

    /** Dialog-style text button: no container, primary-coloured label. */
    public static LinearLayout textBtn(String label, boolean strong, final Act a) {
        LinearLayout c = button(-1, label, 2, a);
        ((TextView) c.getTag()).setTypeface(strong ? Theme.semi : Theme.reg);
        return c;
    }

    /** kind 0 filled, 1 tonal, 2 text. */
    private static LinearLayout button(int icon, String label, int kind, final Act a) {
        LinearLayout c = hbox();
        c.setPadding(dp(icon >= 0 ? 16 : 22), dp(11), dp(22), dp(11));
        int bg = kind == 0 ? Theme.c(Theme.P) : kind == 1 ? Theme.c(Theme.SC) : 0x00000000;
        int fg = kind == 0 ? Theme.c(Theme.ON_P) : kind == 1 ? Theme.c(Theme.ON_SC) : Theme.c(Theme.P);
        c.setBackground(sel(bg, 26));
        if (icon >= 0) {
            Icons.IconView iv = new Icons.IconView(App.ctx, icon, fg);
            c.addView(iv, new LinearLayout.LayoutParams(dp(22), dp(22)));
            c.addView(space(8, 1));
        }
        TextView t = tv(label, 14.5f, true, fg);
        t.setSingleLine(true);
        c.addView(t);
        focusable(c, 1.06f);
        if (a != null) click(c, a);
        c.setTag(t);
        return c;
    }

    /** Extended floating action button: raised primary-container pill that slides up into place. */
    public static LinearLayout fab(int icon, String label, final Act a) {
        LinearLayout c = hbox();
        c.setPadding(dp(18), dp(15), dp(24), dp(15));
        c.setBackground(sel(Theme.c(Theme.PC), 28));
        Icons.IconView iv = new Icons.IconView(App.ctx, icon, Theme.c(Theme.ON_PC));
        c.addView(iv, new LinearLayout.LayoutParams(dp(24), dp(24)));
        c.addView(space(10, 1));
        TextView t = tv(label, 15.5f, true, Theme.c(Theme.ON_PC));
        t.setSingleLine(true);
        c.addView(t);
        focusable(c, 1.06f);
        final float rad = dp(28);
        c.setOutlineProvider(new ViewOutlineProvider() { @Override public void getOutline(View x, Outline o) { o.setRoundRect(0, 0, x.getWidth(), x.getHeight(), rad); } });
        c.setElevation(dp(6));
        if (a != null) click(c, a);
        c.setTag(t);
        return c;
    }

    public static void setChipLabel(View chip, String s) { ((TextView) chip.getTag()).setText(s); }

    /** Fade+rise every child of a group in sequence (cozy entrance). Capped so long lists never feel slow. */
    public static void reveal(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            final View v = g.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(dp(20));
            v.animate().alpha(1f).translationY(0).setStartDelay(80 + Math.min(i, 9) * 40L).setDuration(300).setInterpolator(EASE).withEndAction(new Runnable() { @Override public void run() { v.animate().setStartDelay(0); } }).start();
        }
    }

    /** Tiles arrive together: each scales up from 88% with a soft overshoot while it fades in, staggered row by row. */
    public static void popAll(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            final View v = g.getChildAt(i);
            v.setAlpha(0f); v.setScaleX(0.88f); v.setScaleY(0.88f); v.setTranslationY(dp(16));
            v.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0).setStartDelay(60 + Math.min(i, 12) * 45L).setDuration(420)
                    .setInterpolator(new OvershootInterpolator(0.9f)).withEndAction(new Runnable() { @Override public void run() { v.animate().setStartDelay(0); } }).start();
        }
    }

    /** A page: tonal back button, title, optional actions, then the content below. Title swaps with a short slide. */
    public static final class Page {
        public final LinearLayout root; public final LinearLayout actions; public final TextView title; private final FrameLayout holder;
        public Page(String t, final Act back) {
            root = vbox();
            root.setBackgroundColor(Theme.c(Theme.BG));
            root.setPadding(Theme.padX(), Theme.padY(), Theme.padX(), Theme.padY());
            LinearLayout bar = hbox();
            bar.setPadding(0, 0, 0, dp(12));
            bar.addView(iconBtn(Icons.BACK, false, back));
            title = tv(t, 24, true, Theme.c(Theme.ON_BG));
            title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            title.setPadding(dp(16), 0, dp(8), 0);
            bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
            actions = hbox();
            bar.addView(actions);
            root.addView(bar);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, 0, 1f);
            int g = grow(); cl.setMargins(-g, -g, -g, -g);
            holder = new FrameLayout(App.ctx);
            holder.setClipChildren(true);   // scrolled content never paints over the bar
            root.addView(holder, cl);
        }
        public Page content(View v) { holder.removeAllViews(); holder.addView(v, new FrameLayout.LayoutParams(-1, -1)); return this; }
        public FrameLayout host() { return holder; }
        public void setTitle(final String s) {
            if (s.contentEquals(title.getText())) return;
            title.animate().alpha(0f).translationY(-dp(6)).setDuration(90).withEndAction(new Runnable() { @Override public void run() {
                title.setText(s); title.setTranslationY(dp(8));
                title.animate().alpha(1f).translationY(0).setDuration(200).setInterpolator(EASE).start(); } }).start();
        }
    }

    /** Page frame: back button, title, content. Content must pad itself by grow() (scroll() and the Files list do). */
    public static View scaffold(String title, View actions, View content, final Act back) {
        Page p = new Page(title, back);
        if (actions != null) p.actions.addView(actions);
        p.content(content);
        return p.root;
    }

    public static ScrollView scroll(View inner) {
        ScrollView s = new ScrollView(App.ctx);
        s.setVerticalScrollBarEnabled(false);
        s.setFillViewport(false);
        s.setClipChildren(false);
        s.setClipToPadding(false);
        s.setOverScrollMode(View.OVER_SCROLL_NEVER);
        s.setVerticalFadingEdgeEnabled(true); s.setFadingEdgeLength(dp(26));
        int g = grow();
        s.setPadding(g, g, g, g);
        s.addView(inner, new FrameLayout.LayoutParams(-1, -2));
        return s;
    }

    /** Themed indeterminate spinner: a soft track with an arc that turns and breathes. Draws only while visible, so it costs nothing when hidden. */
    public static final class Spin extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final RectF r = new RectF(); final int color;
        Spin(Context c, int color) { super(c); this.color = color; p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); }
        @Override protected void onDraw(Canvas c) {
            float w = Math.min(getWidth(), getHeight());
            if (w <= 0) return;
            float sw = w * 0.1f, rad = (w - sw) / 2f, cx = getWidth() / 2f, cy = getHeight() / 2f;
            p.setStrokeWidth(sw);
            p.setColor((color & 0xFFFFFF) | 0x2E000000);
            c.drawCircle(cx, cy, rad, p);
            long t = SystemClock.uptimeMillis();
            float breathe = 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * (t % 1600) / 1600f);
            float sweep = 28f + 212f * breathe, start = -90f + (t % 1100) / 1100f * 360f;
            r.set(cx - rad, cy - rad, cx + rad, cy + rad);
            p.setColor(color);
            c.drawArc(r, start, sweep, false, p);
            postInvalidateOnAnimation();
        }
    }

    public static View spinner(int sizeDp) {
        Spin s = new Spin(App.ctx, Theme.c(Theme.P));
        s.setLayoutParams(lp(dp(sizeDp), dp(sizeDp)));
        return s;
    }

    /** Slim rounded gauge, 0..1 (storage used). Fills in with an animation the first time it is set. */
    public static final class Meter extends View {
        float frac, shown; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final RectF r = new RectF(); int fill = 0, track = 0; ValueAnimator va;
        Meter(Context c) { super(c); }
        public void set(float f) { frac = Math.max(0f, Math.min(1f, f)); shown = frac; invalidate(); }
        public void colors(int fill, int track) { this.fill = fill; this.track = track; invalidate(); }
        public void animateTo(float f) {
            frac = Math.max(0f, Math.min(1f, f));
            if (va != null) va.cancel();
            va = ValueAnimator.ofFloat(shown, frac); va.setStartDelay(220); va.setDuration(800); va.setInterpolator(EASE);
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { @Override public void onAnimationUpdate(ValueAnimator a) { shown = (Float) a.getAnimatedValue(); invalidate(); } });
            va.start();
        }
        @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); if (va != null) va.cancel(); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = h / 2f;
            p.setColor(track != 0 ? track : (Theme.c(Theme.OUTV) & 0xFFFFFF | 0x66000000)); r.set(0, 0, w, h); c.drawRoundRect(r, rad, rad, p);
            if (shown > 0) { p.setColor(fill != 0 ? fill : frac > 0.92f ? Theme.c(Theme.ERR) : Theme.c(Theme.P)); r.set(0, 0, Math.max(h, w * shown), h); c.drawRoundRect(r, rad, rad, p); }
        }
    }

    public static void toast(String s) { Toast.makeText(App.ctx, s, Toast.LENGTH_SHORT).show(); }

    public static String size(long b) {
        if (b < 1024) return b + " B";
        double v = b / 1024.0; String[] u = {"KB", "MB", "GB", "TB"}; int i = 0;
        while (v >= 1024 && i < 3) { v /= 1024; i++; }
        return String.format(v >= 100 ? "%.0f %s" : "%.1f %s", v, u[i]);
    }
}
