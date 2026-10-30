package org.wishy.files;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Outline;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;

/**
 * The tile: one tonal rounded surface with a glyph, a title and an optional subtitle / gauge. Every screen is built from these,
 * so Home, Files, Transfer, Install and About share the same shapes, colours and motion.
 * Hero is the primary container, Active is solid primary (something switched on), Warn is an error tint, and everything else
 * (Second, Neutral, Quiet) is a calm surface with a hairline outline.
 */
final class Tiles {
    static final int HERO = 0, SECOND = 1, NEUTRAL = 2, QUIET = 3, ACTIVE = 4, WARN = 5;
    static final float[] TITLE = {13f, 16.5f, 21f, 27f}, SUB = {11.5f, 12.5f, 13.5f, 15f}, RAD = {16f, 20f, 26f, 30f};
    static final int[] PAD = {10, 14, 18, 22};

    static boolean narrow() { return Theme.wDp() < 720; }

    static int bg(int k) {
        switch (k) {
            case HERO: return Theme.c(Theme.PC);
            case ACTIVE: return Theme.c(Theme.P);
            case WARN: return Theme.over(Theme.c(Theme.ERR), Theme.c(Theme.CONT), 0.22f);
            default: return Theme.c(Theme.CONT);
        }
    }
    /** Calm surfaces (everything except Hero, Active and Warn) get a hairline outline instead of extra tone. */
    static int border(int k) { return k == HERO || k == ACTIVE || k == WARN ? 0 : Theme.c(Theme.OUTV); }
    static int fg(int k) {
        switch (k) {
            case HERO: return Theme.c(Theme.ON_PC);
            case ACTIVE: return Theme.c(Theme.ON_P);
            default: return Theme.c(Theme.ON_SURF);
        }
    }
    static int sub(int k) { return k == ACTIVE ? Theme.alpha(Theme.c(Theme.ON_P), 0.82f) : k == HERO ? Theme.alpha(fg(k), 0.78f) : Theme.c(Theme.ON_SV); }
    static int glyphBg(int k) { return k == ACTIVE ? Theme.c(Theme.ON_P) : k == QUIET ? Theme.c(Theme.PC) : k == WARN ? Theme.c(Theme.ERR) : Theme.c(Theme.P); }
    static int glyphFg(int k) { return k == ACTIVE ? Theme.c(Theme.P) : k == QUIET ? Theme.c(Theme.ON_PC) : k == WARN ? Theme.c(Theme.BG) : Theme.c(Theme.ON_P); }
    static int folderCol(int k) { return k == ACTIVE ? Theme.c(Theme.ON_P) : k == QUIET ? Theme.over(Theme.c(Theme.P), Theme.c(Theme.ON_SV), 0.55f) : Theme.c(Theme.P); }

    /** Columns of the grid and the span of a size class. */
    static int cols() { return narrow() ? 4 : 8; }
    static int cs(int size) { int q = cols() / 4; return size == Prio.XL ? cols() : size == Prio.L ? 2 * q : size == Prio.M ? 2 * q : q; }
    static int rs(int size) { return size == Prio.XL || size == Prio.L ? 2 : 1; }

    /** Row height that makes a Small tile roughly square on a phone and a wide pill on a TV. */
    static int rowH(int contentW, int gap) {
        float q = (contentW - 3f * gap) / 4f;
        return Math.round(narrow() ? q * 1.1f : Math.min(q * 0.5f, UI.dp(120)));
    }

    static TileGrid grid(int contentW) {
        TileGrid g = new TileGrid(App.ctx);
        g.cols = cols(); g.gap = UI.dp(narrow() ? 10 : 12); g.rowH = rowH(contentW, g.gap);
        return g;
    }

    static int contentW() { return Theme.wPx - 2 * Theme.padX(); }

    /** Whether a tile of this size lays its glyph beside its text (true) or above it (false). */
    static boolean horiz(int size) { return size == Prio.M || (size == Prio.S && !narrow()) || (size == Prio.XL && narrow()); }

    static float glyphDp(int size, boolean horiz) {
        switch (size) { case Prio.S: return horiz ? 42 : 34; case Prio.M: return narrow() ? 40 : 48; case Prio.L: return 84; default: return horiz ? 112 : 128; }
    }

    static final class Tile extends FrameLayout {
        int kind, size; boolean horiz; boolean folderGlyph;
        FolderGlyph folder; FrameLayout iconBox; Icons.IconView iconV; int iconId = -1;
        TextView title, subTv; UI.Meter meter; View check; Icons.IconView arrow; View arrowBox; LinearLayout box, text;

        Tile(int size, boolean horiz, int kind) {
            super(App.ctx);
            this.size = size; this.horiz = horiz; this.kind = kind;
            setClipChildren(false);
            int p = UI.dp(PAD[size]);
            box = new LinearLayout(App.ctx);
            box.setOrientation(horiz ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
            box.setGravity(horiz ? Gravity.CENTER_VERTICAL : Gravity.TOP);
            box.setClipChildren(false);
            box.setPadding(p, p, p, p);
            addView(box, new FrameLayout.LayoutParams(-1, -1));
            text = new LinearLayout(App.ctx); text.setOrientation(LinearLayout.VERTICAL);
            title = UI.tv("", TITLE[size], true, fg(kind));
            title.setEllipsize(TextUtils.TruncateAt.END);
            if (size == Prio.S && !horiz) title.setMaxLines(2); else title.setSingleLine(true);
            text.addView(title);
            subTv = UI.tv("", SUB[size], false, sub(kind)); subTv.setSingleLine(true); subTv.setEllipsize(TextUtils.TruncateAt.END);
            subTv.setPadding(0, UI.dp(size == Prio.S ? 2 : 4), 0, 0); subTv.setVisibility(GONE);
            text.addView(subTv);
            if (horiz) {
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
                tl.leftMargin = UI.dp(size == Prio.S ? 10 : size == Prio.M ? 14 : 20);
                if (size == Prio.M && !narrow()) tl.rightMargin = UI.dp(34);
                box.addView(text, tl);
            } else {
                View fill = new View(App.ctx); box.addView(fill, new LinearLayout.LayoutParams(1, 0, 1f));
                box.addView(text, new LinearLayout.LayoutParams(-1, -2));
            }
            setBackground(UI.tileSel(bg(kind), RAD[size], border(kind)));
            UI.focusable(this, size == Prio.XL ? 1.025f : size == Prio.L ? 1.04f : 1.06f);
        }

        /** If the tile is too short (or narrow) for its nominal glyph, the glyph shrinks to fit instead of running over the text. */
        @Override protected void onMeasure(int ws, int hs) {
            super.onMeasure(ws, hs);
            View gl = folder != null ? folder : iconBox;
            if (gl == null || !(gl.getLayoutParams() instanceof LinearLayout.LayoutParams)) return;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) gl.getLayoutParams();
            int w = getMeasuredWidth(), h = getMeasuredHeight(), p = UI.dp(PAD[size]);
            int max = horiz ? Math.min(h - 2 * p, Math.round(w * 0.38f)) : h - 2 * p - text.getMeasuredHeight() - UI.dp(6);
            max = Math.max(UI.dp(26), max);
            if (lp.width > max) { lp.width = lp.height = max; super.onMeasure(ws, hs); }
        }

        private void addGlyph(View v, int w) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, w);
            box.addView(v, 0, lp);
        }

        Tile icon(int id) {
            iconId = id;
            int g = UI.dp(glyphDp(size, horiz));
            iconBox = new FrameLayout(App.ctx);
            iconV = new Icons.IconView(App.ctx, id, glyphFg(kind));
            int pad = Math.round(g * 0.2f);
            iconV.setPadding(pad, pad, pad, pad);
            iconBox.addView(iconV, new FrameLayout.LayoutParams(-1, -1));
            iconBox.setBackground(UI.rr(glyphBg(kind), Math.max(11, glyphDp(size, horiz) * 0.28f)));
            addGlyph(iconBox, g);
            return this;
        }

        /** Folder illustration: [items] -1 unknown, 0 empty, n filled. */
        Tile folder(int items) {
            folderGlyph = true;
            int g = Math.round(UI.dp(glyphDp(size, horiz)) * 1.18f);
            folder = new FolderGlyph(App.ctx, folderCol(kind), items);
            addGlyph(folder, g);
            return this;
        }

        Tile text(String t, String s) {
            title.setText(t);
            if (s != null && s.length() > 0 && !(size == Prio.S && !horiz)) { subTv.setText(s); subTv.setVisibility(VISIBLE); } else subTv.setVisibility(GONE);
            return this;
        }

        Tile meter(float frac) {
            meter = new UI.Meter(App.ctx);
            meter.colors(kind == ACTIVE ? Theme.c(Theme.ON_P) : Theme.c(Theme.P), Theme.alpha(fg(kind), 0.16f));
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, UI.dp(size == Prio.XL ? 8 : 6)); ml.topMargin = UI.dp(size == Prio.XL ? 12 : 8);
            text.addView(meter, ml);
            meter.animateTo(frac);
            return this;
        }

        /** Small circular chevron in the corner (large tiles) or at the end (medium tiles). */
        Tile arrow() {
            if (size == Prio.S || (size == Prio.M && narrow())) return this;   // no room beside the text on a phone
            int d = UI.dp(size == Prio.M ? 30 : 36);
            FrameLayout a = new FrameLayout(App.ctx);
            a.setBackground(UI.rr(Theme.alpha(fg(kind), 0.10f), 11));
            arrow = new Icons.IconView(App.ctx, Icons.CHEVRON, fg(kind));
            int pd = UI.dp(6);
            arrow.setPadding(pd, pd, pd, pd);
            a.addView(arrow, new FrameLayout.LayoutParams(-1, -1));
            int m = UI.dp(PAD[size] - 2 < 8 ? 8 : PAD[size] - 2);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(d, d, size == Prio.M ? (Gravity.END | Gravity.CENTER_VERTICAL) : (Gravity.END | Gravity.TOP));
            lp.setMargins(0, m, m, 0);
            arrowBox = a;
            addView(a, lp);
            return this;
        }

        /** Selected tiles show a check; it pops in with a small overshoot. */
        void selected(boolean on) {
            setActivated(on);
            if (check == null && on) {
                int d = UI.dp(size == Prio.S ? 24 : 30);
                Icons.IconView c = new Icons.IconView(App.ctx, Icons.CHECKED, Theme.c(Theme.P));
                c.setBackground(UI.rr(Theme.c(Theme.ON_P), 40));
                c.setPadding(0, 0, 0, 0);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(d, d, Gravity.END | Gravity.TOP);
                int m = UI.dp(size == Prio.S ? 5 : 10);
                lp.setMargins(0, m, m, 0);
                check = c; addView(c, lp);
            }
            if (check != null) {
                if (on) { check.setVisibility(VISIBLE); UI.popIn(check); if (arrowBox != null) arrowBox.setVisibility(INVISIBLE); }
                else { check.setVisibility(GONE); if (arrowBox != null) arrowBox.setVisibility(VISIBLE); }
            }
        }

        /** Re-tone the tile in place (e.g. Transfer switching on). */
        void kind(int k) {
            kind = k;
            setBackground(UI.tileSel(bg(k), RAD[size], border(k)));
            title.setTextColor(fg(k)); subTv.setTextColor(sub(k));
            if (iconBox != null) { iconBox.setBackground(UI.rr(glyphBg(k), Math.max(11, glyphDp(size, horiz) * 0.28f))); iconV.set(iconId, glyphFg(k)); }
            if (folder != null) folder.set(folderCol(k), folder.isEmpty() ? 0 : 1);
            if (arrow != null) { arrow.set(Icons.CHEVRON, fg(k)); arrowBox.setBackground(UI.rr(Theme.alpha(fg(k), 0.10f), 11)); }
            if (meter != null) meter.colors(k == ACTIVE ? Theme.c(Theme.ON_P) : Theme.c(Theme.P), Theme.alpha(fg(k), 0.16f));
        }

        /** Tap: if there is a folder glyph its flap swings open first, then [a] runs (the screen change follows the motion). */
        Tile onTap(final UI.Act a) {
            setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) {
                if (folder != null) { folder.openTo(1f); postDelayed(new Runnable() { @Override public void run() { a.run(); } }, 190); }
                else { UI.pop(x); a.run(); }
            } });
            return this;
        }

        /** While focused, a folder tile's flap lifts a little (a peek inside). */
        Tile peek() {
            setOnFocusChangeListener(new View.OnFocusChangeListener() { @Override public void onFocusChange(View x, boolean has) {
                UI.focusAnim(x, has);
                if (folder != null) folder.openTo(has ? 0.38f : 0f);
            } });
            return this;
        }
    }

    /** A tile ready to use; [horiz] defaults to the standard layout for the size. */
    static Tile make(int size, int kind, String title, String sub) { return make(size, horiz(size), kind, title, sub); }
    static Tile make(int size, boolean horiz, int kind, String title, String sub) { return new Tile(size, horiz, kind).text(title, sub); }

    /** A tile that only informs (no focus, no click): label above value. Used for facts. */
    static View info(int icon, String label, String value) {
        LinearLayout t = new LinearLayout(App.ctx);
        t.setOrientation(LinearLayout.HORIZONTAL); t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(UI.dp(14), UI.dp(10), UI.dp(14), UI.dp(10));
        t.setBackground(UI.tileSel(Theme.c(Theme.CONT), 22, Theme.c(Theme.OUTV)));
        View ic = UI.iconTile(icon, 40, Theme.c(Theme.PC), Theme.c(Theme.ON_PC));
        t.addView(ic);
        LinearLayout c = UI.vbox(); c.setPadding(UI.dp(12), 0, 0, 0);
        TextView l = UI.tv(label, 12, false, Theme.c(Theme.ON_SV)); l.setSingleLine(true);
        TextView v = UI.tv(value, 14, true, Theme.c(Theme.ON_SURF)); v.setMaxLines(2); v.setEllipsize(TextUtils.TruncateAt.END); v.setPadding(0, UI.dp(2), 0, 0);
        c.addView(l); c.addView(v);
        t.addView(c, new LinearLayout.LayoutParams(0, -2, 1f));
        return t;
    }
}
