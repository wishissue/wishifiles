package org.wishy.files;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Icons are Material Symbols Rounded (Apache-2.0, fonts.google.com/icons): filled glyphs for objects, outline glyphs for actions. They are stored as flattened
 * vector paths in assets/icons.bin (built by tools/build_icons.py) and drawn with Canvas, so there are no bitmaps to scale or bundle.
 * The order of the ids below must match SPEC in that script.
 */
public final class Icons {
    public static final int FOLDER = 0, FILE = 1, IMAGE = 2, VIDEO = 3, AUDIO = 4, APK = 5, DOC = 6, HTML = 7, ZIP = 8, PDF = 9,
            USB = 10, TV = 11, SEARCH = 12, SORT = 13, NEWFOLDER = 14, BACK = 15, SWAP = 16, INSTALL = 17, TUNE = 18, INFO = 19,
            HEART = 20, SHIELD = 21, CODE = 22, GLOBE = 23, CHECK = 24, COPY = 25, CUT = 26, PASTE = 27, TRASH = 28, EDIT = 29,
            SHARE = 30, WIFI = 31, REFRESH = 32, CLOSE = 33, MORE = 34, PLAY = 35, UP = 36, HOME = 37, SELECT = 38, STOP = 39, UPLOAD = 40,
            CHEVRON = 41, PAUSE = 42, PREV = 43, NEXT = 44, WARN = 45, CHECKED = 46, ERROR = 47, EYE = 48, EYE_OFF = 49, STORAGE = 50,
            FOLDER_OPEN = 51, CPU = 52, PACKAGE = 53, GRID = 54, LIST = 55, GEAR = 56,
            FOLDER_LINE = 57, DOWNLOADS = 58, PICTURES = 59, MOVIES = 60, MUSIC = 61, DOCUMENTS = 62, INBOX = 63, QR = 64,
            LINK = 65, DEVICES = 66, HISTORY = 67, STAR = 68, ADD = 69, SD = 70, WIFI_OFF = 71, POWER = 72, LOCK = 73, DARK = 74,
            CONTRAST = 75, AUDIOFILE = 76, VIDEOFILE = 77, SPECIAL = 78, INSTALL_FILL = 79, SYNC = 80, UPLOAD_CLOUD = 81,
            DOWNLOAD_CLOUD = 82, SELECT_ALL = 83, MOVE = 84, APPS = 85, OPEN_NEW = 86, DONE_ALL = 87, SCHEDULE = 88, BOLT = 89, EXPAND = 90;

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static IconData.Layer[][] data;
    private static Path[][] paths;

    /** Load the icon set once (cheap: about 15 KB). */
    public static synchronized void load(Context c) {
        if (data != null) return;
        try {
            java.io.InputStream is = c.getAssets().open("icons.bin");
            data = IconData.read(is);
            is.close();
            paths = new Path[data.length][];
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static Path[] pathsFor(int id) {
        Path[] p = paths[id];
        if (p != null) return p;
        IconData.Layer[] ls = data[id];
        p = new Path[ls.length];
        for (int k = 0; k < ls.length; k++) {
            IconData.Layer l = ls[k];
            Path q = new Path();
            q.setFillType(Path.FillType.WINDING);
            int j = 0; float[] f = l.pts;
            for (byte op : l.ops) {
                switch (op) {
                    case IconData.M: q.moveTo(f[j], f[j + 1]); j += 2; break;
                    case IconData.L: q.lineTo(f[j], f[j + 1]); j += 2; break;
                    case IconData.Q: q.quadTo(f[j], f[j + 1], f[j + 2], f[j + 3]); j += 4; break;
                    case IconData.C: q.cubicTo(f[j], f[j + 1], f[j + 2], f[j + 3], f[j + 4], f[j + 5]); j += 6; break;
                    default: q.close();
                }
            }
            p[k] = q;
        }
        paths[id] = p;
        return p;
    }

    /** Draw icon [id] into a square at (x,y) of [size] px using [color]. Duotone icons draw their soft tint layer at 20% of [color]. */
    public static void draw(Canvas c, int id, float x, float y, float size, int color) {
        if (data == null) load(App.ctx);
        if (id < 0 || id >= data.length) id = FILE;
        Path[] ps = pathsFor(id); IconData.Layer[] ls = data[id];
        c.save();
        c.translate(x, y);
        float s = size / 256f;
        c.scale(s, s);
        P.setStyle(Paint.Style.FILL);
        int base = color >>> 24;
        for (int k = 0; k < ps.length; k++) {
            P.setColor((color & 0xFFFFFF) | ((base * ls[k].alpha / 255) << 24));
            c.drawPath(ps[k], P);
        }
        c.restore();
    }

    /**
     * A view that draws one icon, centred inside its padding. If [focusColor] is set the icon switches to it while the view's
     * (possibly duplicated parent) state is focused, selected or pressed, so an icon can invert together with its row.
     */
    public static class IconView extends View {
        int id, color, focusColor;
        public IconView(Context ctx, int id, int color) { super(ctx); this.id = id; this.color = color; this.focusColor = 0; }
        public IconView(Context ctx, int id, int color, int focusColor) { super(ctx); this.id = id; this.color = color; this.focusColor = focusColor; }
        public void set(int id, int color) { this.id = id; this.color = color; invalidate(); }
        @Override protected void drawableStateChanged() { super.drawableStateChanged(); if (focusColor != 0) invalidate(); }
        @Override protected void onDraw(Canvas c) {
            int w = getWidth() - getPaddingLeft() - getPaddingRight(), h = getHeight() - getPaddingTop() - getPaddingBottom();
            float s = Math.min(w, h);
            if (s <= 0) return;
            int col = color;
            if (focusColor != 0) for (int st : getDrawableState())
                if (st == android.R.attr.state_focused || st == android.R.attr.state_selected || st == android.R.attr.state_pressed) { col = focusColor; break; }
            Icons.draw(c, id, getPaddingLeft() + (w - s) / 2f, getPaddingTop() + (h - s) / 2f, s, col);
        }
    }
}
