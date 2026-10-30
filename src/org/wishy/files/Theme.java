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
    public static Typeface reg = Typeface.DEFAULT, semi = Typeface.DEFAULT_BOLD, brand = Typeface.DEFAULT_BOLD;
    static SharedPreferences sp;

    public static void init(Context c) {
        measure(c);
        sp = c.getSharedPreferences("wishi", Context.MODE_PRIVATE);
        try {
            reg = Typeface.createFromAsset(c.getAssets(), "pj_regular.ttf");
            semi = Typeface.createFromAsset(c.getAssets(), "pj_semibold.ttf");
        } catch (Throwable ignored) { }
        try { brand = Typeface.createFromAsset(c.getAssets(), "bagel.ttf"); } catch (Throwable ignored) { brand = semi; }
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
            wishIssue();
            for (int i = 0; i < n; i++) { cols[i][2] = dusk(cols[i][0]); cols[i][3] = amoled(cols[i][0]); }
        } catch (Throwable t) { throw new RuntimeException(t); }
        pal = Math.min(sp.getInt("pal", 0), ids.length - 1);
        // first run: follow the system. TVs are dark unless the system says otherwise.
        int ui = c.getResources().getConfiguration().uiMode;
        boolean tv = (ui & android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
        boolean night = (ui & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        mode = Math.min(sp.getInt("mode", tv || night ? 0 : 1), 3);
    }

    /** The default palette (index 0) follows the WishIssue design tokens: blue-led, with the light page #E6F1FF and a near-black navy dark page. */
    private static void wishIssue() {
        labels[0] = "WishIssue Blue"; seeds[0] = 0xFF1560D4;
        //                P         ON_P      PC        ON_PC     S         SC        ON_SC     T         BG        ON_BG
        //                SURF      ON_SURF   SV        ON_SV     OUT       OUTV      ERR       CONT      CONT_H    CONT_L
        cols[0][1] = new int[]{0xFF1560D4, 0xFFFFFFFF, 0xFFD6E4FF, 0xFF0A2D6B, 0xFF4A5D7C, 0xFFDCE6F7, 0xFF14233F, 0xFF0F6B7A, 0xFFE6F1FF, 0xFF101828,
                0xFFE6F1FF, 0xFF101828, 0xFFE8EEF9, 0xFF536176, 0xFF8A97AD, 0xFFD4DEED, 0xFFBA1A1A, 0xFFFFFFFF, 0xFFF3F7FF, 0xFFF8FAFF};
        cols[0][0] = new int[]{0xFFA8C8FF, 0xFF082C63, 0xFF17408A, 0xFFD6E4FF, 0xFFB9C6E0, 0xFF2A3550, 0xFFD9E3F9, 0xFF8FD0DD, 0xFF080B12, 0xFFF1F4FB,
                0xFF080B12, 0xFFF1F4FB, 0xFF182235, 0xFFB0BCD0, 0xFF6C7B95, 0xFF303B4F, 0xFFFFB4AB, 0xFF111725, 0xFF182235, 0xFF0D121D};
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
