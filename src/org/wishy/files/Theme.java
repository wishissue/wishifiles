package org.wishy.files;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import java.io.DataInputStream;
import java.io.InputStream;

/** Colour system: the 45 Wishy Browser palettes (Material color utilities output) x 4 modes. */
public final class Theme {
    public static final int P = 0, ON_P = 1, PC = 2, ON_PC = 3, S = 4, SC = 5, ON_SC = 6, T = 7, BG = 8, ON_BG = 9,
            SURF = 10, ON_SURF = 11, SV = 12, ON_SV = 13, OUT = 14, OUTV = 15, ERR = 16, CONT = 17, CONT_H = 18, CONT_L = 19;
    public static final String[] MODES = {"Dark", "Light", "Dusk", "AMOLED"};
    public static String[] ids, labels;
    public static int[] seeds;
    static int[][][] cols;
    public static int pal, mode;
    public static float density = 1f, scale = 1f;
    static int wPx = 1920, hPx = 1080;
    public static Typeface reg = Typeface.DEFAULT, semi = Typeface.DEFAULT_BOLD;
    static SharedPreferences sp;

    public static void init(Context c) {
        measure(c);
        sp = c.getSharedPreferences("wishi", Context.MODE_PRIVATE);
        try {
            reg = Typeface.createFromAsset(c.getAssets(), "p_regular.ttf");
            semi = Typeface.createFromAsset(c.getAssets(), "p_semibold.ttf");
        } catch (Throwable ignored) { }
        try {
            InputStream is = c.getAssets().open("themes.bin");
            DataInputStream d = new DataInputStream(is);
            int n = d.readUnsignedShort();
            ids = new String[n]; labels = new String[n]; seeds = new int[n]; cols = new int[n][4][20];
            for (int i = 0; i < n; i++) {
                byte[] b = new byte[d.readUnsignedByte()]; d.readFully(b); ids[i] = new String(b, "UTF-8");
                b = new byte[d.readUnsignedByte()]; d.readFully(b); labels[i] = new String(b, "UTF-8");
                seeds[i] = d.readInt();
                for (int m = 0; m < 4; m++) for (int k = 0; k < 20; k++) cols[i][m][k] = d.readInt();
            }
            is.close();
            for (int i = 0; i < n; i++) { cols[i][2] = dusk(cols[i][0]); cols[i][3] = amoled(cols[i][0]); }
        } catch (Throwable t) { throw new RuntimeException(t); }
        pal = Math.min(sp.getInt("pal", 0), ids.length - 1);
        mode = Math.min(sp.getInt("mode", 0), 3);
    }

    static int mix(int a, int b, float t) {
        int r = 0xFF000000;
        for (int sh = 16; sh >= 0; sh -= 8) { int x = (a >> sh) & 255, y = (b >> sh) & 255; r |= (x + Math.round((y - x) * t)) << sh; }
        return r;
    }

    /** The shipped data has Dusk and AMOLED identical to Dark, so build them from Dark. Dusk: a lifted, accent-tinted twilight. */
    static int[] dusk(int[] d) {
        int[] o = d.clone(); int p = d[P], w = 0xFFFFFFFF;
        int bg = mix(mix(d[BG], p, 0.22f), w, 0.03f);
        o[BG] = bg; o[SURF] = bg;
        o[CONT_L] = mix(bg, w, 0.04f); o[CONT] = mix(bg, w, 0.075f); o[CONT_H] = mix(bg, w, 0.12f); o[SV] = mix(bg, w, 0.17f);
        o[PC] = mix(d[PC], w, 0.08f); o[SC] = mix(d[SC], w, 0.06f);
        o[OUTV] = mix(d[OUTV], w, 0.12f);
        return o;
    }

    /** AMOLED: true black background with barely-there containers, so pixels stay off. */
    static int[] amoled(int[] d) {
        int[] o = d.clone(); int k = 0xFF000000;
        o[BG] = k; o[SURF] = k; o[CONT_L] = k;
        o[CONT] = mix(d[CONT], k, 0.5f); o[CONT_H] = mix(d[CONT_H], k, 0.35f); o[SV] = mix(d[SV], k, 0.45f);
        o[PC] = mix(d[PC], k, 0.3f); o[SC] = mix(d[SC], k, 0.4f);
        return o;
    }

    /** Dark or light ink, whichever reads better on [bg]. */
    public static int on(int bg) {
        float l = (0.299f * ((bg >> 16) & 255) + 0.587f * ((bg >> 8) & 255) + 0.114f * (bg & 255)) / 255f;
        return l > 0.56f ? 0xFF1B1B1F : 0xFFFFFFFF;
    }

    /** Same colour with a new alpha (0..1). */
    public static int alpha(int c, float a) { return (c & 0xFFFFFF) | (Math.round(255 * a) << 24); }

    /** Colour [a] blended over [b] by [t] (0 = b, 1 = a), opaque result. */
    public static int over(int a, int b, float t) { return mix(b, a, t); }

    /** Muted, theme-derived hues for file types: every colour is blended from the palette's own primary, secondary and tertiary, so nothing clashes with the theme. */
    public static int tone(int slot) {
        int p = c(P), s = c(S), t = c(T), n = c(ON_SV);
        switch (slot) {
            case 0: return p;
            case 1: return s;
            case 2: return t;
            case 3: return mix(p, t, 0.5f);
            case 4: return mix(p, s, 0.5f);
            case 5: return mix(s, t, 0.5f);
            case 6: return mix(c(ERR), p, 0.25f);
            case 7: return mix(t, n, 0.45f);
            case 8: return mix(s, n, 0.45f);
            default: return n;
        }
    }

    /** A soft tinted surface for a tone: the tone at low strength over the page colour. */
    public static int toneBg(int fg, float strength) { return mix(fg, c(CONT), strength); }

    public static int col(int p, int m, int k) { return cols[p][m][k]; }

    /** Measure the screen and pick a UI scale so the layout always fills it, from a 1080p TV down to a phone. */
    public static void measure(Context c) {
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        density = dm.density; wPx = dm.widthPixels; hPx = dm.heightPixels;
        float minDp = Math.min(wPx, hPx) / density;
        scale = Math.max(0.9f, Math.min(2.2f, minDp / 540f));
    }

    /** Screen size in the app's own (scaled) dp units. */
    public static float wDp() { return wPx / (density * scale); }
    public static float hDp() { return hPx / (density * scale); }
    /** Page margins: a share of the screen so TVs with overscan never lose content. */
    public static int padX() { return Math.max(dp(20), Math.round(wPx * 0.04f)); }
    public static int padY() { return Math.max(dp(14), Math.round(hPx * 0.05f)); }

    public static int c(int i) { return cols[pal][mode][i]; }
    public static int dp(float v) { return Math.round(v * density * scale); }
    public static boolean dark() { return mode != 1; }

    public static void set(int p, int m) {
        pal = p; mode = m;
        sp.edit().putInt("pal", p).putInt("mode", m).apply();
    }

    public static SharedPreferences prefs() { return sp; }

    static String hex(int c) { return String.format("#%06x", c & 0xFFFFFF); }

    /** CSS custom properties so the TV-hosted web page matches the app theme. */
    public static String css() {
        return ":root{--p:" + hex(c(P)) + ";--onp:" + hex(c(ON_P)) + ";--pc:" + hex(c(PC)) + ";--onpc:" + hex(c(ON_PC))
                + ";--bg:" + hex(c(BG)) + ";--on:" + hex(c(ON_SURF)) + ";--sv:" + hex(c(SV)) + ";--onsv:" + hex(c(ON_SV))
                + ";--ct:" + hex(c(CONT)) + ";--cth:" + hex(c(CONT_H)) + ";--out:" + hex(c(OUTV)) + ";--err:" + hex(c(ERR)) + "}";
    }
}
