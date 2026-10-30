package org.wishy.files;

import android.view.*;
import android.widget.*;

/** Shared helpers for the small static pages. */
final class Pages {
    static final String DONATE_URL = "https://wishissue.github.io/wishy-apps/donate.html";
    static final String GITHUB = "https://github.com/wishissue";
    static final String REPO = "https://github.com/wishissue/WishiFiles";

    /** Material-style switch whose knob glides and whose track fades between the off and on colours. */
    static final class Sw extends View {
        boolean on; float pos; android.animation.ValueAnimator va;
        final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG); final android.graphics.RectF r = new android.graphics.RectF();
        Sw(boolean on) { super(App.ctx); this.on = on; pos = on ? 1f : 0f; setLayoutParams(new LinearLayout.LayoutParams(UI.dp(56), UI.dp(32))); }
        void set(boolean v) {
            on = v;
            if (va != null) va.cancel();
            va = android.animation.ValueAnimator.ofFloat(pos, v ? 1f : 0f);
            va.setDuration(180); va.setInterpolator(UI.EASE);
            va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(android.animation.ValueAnimator x) { pos = (Float) x.getAnimatedValue(); invalidate(); } });
            va.start();
        }
        @Override protected void onDraw(android.graphics.Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(mix(Theme.c(Theme.OUTV), Theme.c(Theme.P), pos));
            r.set(0, 0, w, h); c.drawRoundRect(r, h / 2f, h / 2f, p);
            float kr = h * (0.27f + 0.11f * pos), cx = h / 2f + (w - h) * pos;
            p.setColor(mix(Theme.c(Theme.SURF), Theme.c(Theme.ON_P), pos));
            c.drawCircle(cx, h / 2f, kr, p);
            if (pos > 0.45f) {
                int tick = Theme.c(Theme.P);
                int al = Math.min(255, (int) ((pos - 0.45f) / 0.55f * 255));
                Icons.draw(c, Icons.CHECK, cx - kr * 0.62f, h / 2f - kr * 0.62f, kr * 1.24f, (tick & 0xFFFFFF) | (al << 24));
            }
        }
    }

    static int mix(int a, int b, float t) {
        int r = 0;
        for (int sh = 24; sh >= 0; sh -= 8) { int x = (a >>> sh) & 255, y = (b >>> sh) & 255; r |= (x + Math.round((y - x) * t)) << sh; }
        return r;
    }

    /** A settings row with a switch persisted in SharedPreferences. */
    static View switchRow(int icon, String title, String sub, final String key, boolean def) {
        final Sw sw = new Sw(Theme.prefs().getBoolean(key, def));
        return UI.rowPlain(icon, title, sub, sw, new UI.Act() { @Override public void run() {
            boolean n = !sw.on; sw.set(n); Theme.prefs().edit().putBoolean(key, n).apply(); } });
    }

    static LinearLayout col() { LinearLayout l = UI.vbox(); l.setPadding(0, UI.dp(4), 0, UI.dp(30)); return l; }

    static void add(LinearLayout l, View v) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = UI.dp(10); l.addView(v, lp); }
}

class AboutScreen extends Screen {
    @Override View build() {
        LinearLayout c = Pages.col();
        // hero
        LinearLayout h = UI.hbox(); h.setPadding(UI.dp(22), UI.dp(22), UI.dp(22), UI.dp(22)); h.setBackground(UI.rr(Theme.c(Theme.PC), 34));
        LinearLayout t = UI.vbox();
        t.addView(UI.brand("Wishi Files", 30, Theme.c(Theme.ON_PC)));
        TextView v = UI.tv("Version 1.4.0  \u00b7  GPL-3.0", 14, false, Theme.alpha(Theme.c(Theme.ON_PC), 0.8f)); v.setPadding(0, UI.dp(4), 0, 0); t.addView(v);
        h.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        Pages.add(c, h);
        TileGrid g = new TileGrid(a); g.cols = 4; g.gap = UI.dp(10); g.rowH = UI.dp(Tiles.narrow() ? 100 : 84);
        add(g, Icons.SHIELD, "Privacy", new UI.Act() { @Override public void run() { a.push(new PrivacyScreen()); } });
        add(g, Icons.CODE, "Credits", new UI.Act() { @Override public void run() { a.push(new CreditsScreen()); } });
        add(g, Icons.HEART, "Support", new UI.Act() { @Override public void run() { a.push(new DonateScreen()); } });
        add(g, Icons.GLOBE, "Source code", new UI.Act() { @Override public void run() { a.open(Pages.REPO); } });
        c.addView(g, new LinearLayout.LayoutParams(-1, -2));
        UI.popAll(g);
        return UI.scaffold("About", null, UI.scroll(c), new UI.Act() { @Override public void run() { a.pop(); } });
    }
    private void add(TileGrid g, int icon, String title, UI.Act act) {
        Tiles.Tile t = Tiles.make(Prio.M, Tiles.NEUTRAL, title, null).icon(icon).arrow();
        t.onTap(act); g.add(t, 2, 1);
    }
}

class CreditsScreen extends Screen {
    static final String[][] C = {
        {"Wishy Browser", "Design language, themes and pages reused from the original", "GPL-3.0", "https://github.com/wishissue/WishyBrowser", "GLOBE"},
        {"Material Color Utilities", "Generated the 45 colour themes (via Wishy Browser)", "Apache-2.0", "https://github.com/material-foundation/material-color-utilities", "TUNE"},
        {"Orion Store", "Design inspiration and theme seed colours", "GPL-3.0", "https://github.com/RookieEnough/Orion-Store", "HEART"},
        {"Plus Jakarta Sans", "Typeface by Tokotype (Latin subset)", "OFL-1.1", "https://fonts.google.com/specimen/Plus+Jakarta+Sans", "DOC"},
        {"Lucide", "All icons in the app and on the transfer page", "ISC", "https://lucide.dev", "IMAGE"},
        {"Bagel Fat One", "Brand typeface for the wordmark (Latin letters subset)", "OFL-1.1", "https://fonts.google.com/specimen/Bagel+Fat+One", "DOC"},
        {"Fossify File Manager", "Studied for file-browser behaviour. No code copied", "GPL-3.0", "https://github.com/FossifyOrg/File-Manager", "CODE"},
        {"Aurora Store", "Studied for PackageInstaller handling. No code copied", "GPL-3.0", "https://github.com/whyorean/AuroraStore", "CODE"},
        {"dropX", "Studied for the QR handshake and threat model. No code copied", "MIT", "https://github.com/flu1d3v/dropX", "CODE"},
        {"android-wifi-fileserver", "Studied for HTTP range and upload handling. No code copied", "MIT", "https://github.com/rafiibrahim8/android-wifi-fileserver", "CODE"},
        {"QR Code specification", "ISO/IEC 18004. The encoder was written from the spec", "n/a", "https://www.iso.org/standard/62021.html", "INFO"},
    };
    /** Icon for a credit row by name; anything unknown falls back to a generic one instead of failing. */
    static int icon(String n) {
        if ("GLOBE".equals(n)) return Icons.GLOBE; if ("TUNE".equals(n)) return Icons.TUNE; if ("HEART".equals(n)) return Icons.HEART;
        if ("DOC".equals(n)) return Icons.DOC; if ("IMAGE".equals(n)) return Icons.IMAGE; if ("CODE".equals(n)) return Icons.CODE;
        return Icons.INFO;
    }
    @Override View build() {
        LinearLayout c = Pages.col();
        for (final String[] x : C) {
            TextView lic = UI.tv(x[2], 12.5f, true, Theme.c(Theme.ON_SC));
            lic.setPadding(UI.dp(12), UI.dp(6), UI.dp(12), UI.dp(6)); lic.setBackground(UI.rr(Theme.c(Theme.SC), 14));
            Pages.add(c, UI.rowPlain(icon(x[4]), x[0], x[1], lic, new UI.Act() { @Override public void run() { a.open(x[3]); } }));
        }
        UI.reveal(c);
        return UI.scaffold("Credits", null, UI.scroll(c), new UI.Act() { @Override public void run() { a.pop(); } });
    }
}

class DonateScreen extends Screen {
    @Override View build() {
        LinearLayout c = Pages.col(); c.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView t = UI.tv("Support Wishi Files", 24, true, Theme.c(Theme.ON_BG)); t.setGravity(Gravity.CENTER); c.addView(t);
        TextView s = UI.tv("Scan with your phone", 16, false, Theme.c(Theme.ON_SV));
        s.setGravity(Gravity.CENTER); s.setPadding(0, UI.dp(10), 0, UI.dp(20)); c.addView(s);
        ImageView q = new ImageView(a); q.setImageResource(R.drawable.qr_donate); q.setBackground(UI.rr(0xFFFFFFFF, 34)); q.setPadding(UI.dp(14), UI.dp(14), UI.dp(14), UI.dp(14));
        int qs = Math.min(264, Math.max(160, (int) Theme.wDp() - 120)); c.addView(q, new LinearLayout.LayoutParams(UI.dp(qs), UI.dp(qs)));
        TextView u = UI.tv(Pages.DONATE_URL.substring(8), 15, true, Theme.c(Theme.P)); u.setPadding(0, UI.dp(16), 0, UI.dp(16)); c.addView(u);
        View b = UI.chip(Icons.HEART, "Open page", true, new UI.Act() { @Override public void run() { a.open(Pages.DONATE_URL); } });
        c.addView(b);
        UI.reveal(c);
        return UI.scaffold("Donate", null, UI.scroll(c), new UI.Act() { @Override public void run() { a.pop(); } });
    }
}

class PrivacyScreen extends Screen {
    @Override View build() {
        LinearLayout c = Pages.col();
        LinearLayout card = UI.hbox(); card.setPadding(UI.dp(24), UI.dp(24), UI.dp(24), UI.dp(24)); card.setBackground(UI.rr(Theme.c(Theme.PC), 34));
        card.addView(UI.iconTile(Icons.SHIELD, 64, Theme.c(Theme.P), Theme.c(Theme.ON_P)));
        LinearLayout t = UI.vbox(); t.setPadding(UI.dp(20), 0, 0, 0);
        t.addView(UI.tv("Private by design", 22, true, Theme.c(Theme.ON_PC)));
        TextView s = UI.tv("No ads, no analytics, no accounts, no cloud. Transfers stay on your home network.", 15, false, Theme.c(Theme.ON_PC)); s.setPadding(0, UI.dp(4), 0, 0); t.addView(s);
        card.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        Pages.add(c, card);
        c.addView(UI.section("Transfer"));
        Pages.add(c, Pages.switchRow(Icons.WIFI, "Stop sharing when idle", "Turns the server off after 10 minutes without activity", "autostop", true));
        Pages.add(c, Pages.switchRow(Icons.TV, "Keep screen on while sharing", "Stops the TV sleeping in the middle of a transfer", "keepon", true));
        c.addView(UI.section("What the app can do"));
        Pages.add(c, UI.row(Icons.FOLDER, "Storage access", "Only to browse, copy and save files you choose. Android asks you first.", null, null));
        Pages.add(c, UI.row(Icons.WIFI, "Network", "Used only by the transfer server, and only while it is on. A secret code in the QR protects each session.", null, null));
        Pages.add(c, UI.row(Icons.INSTALL, "Installing apps", "Android shows its own confirmation. Nothing installs silently.", null, null));
        Pages.add(c, UI.row(Icons.SHIELD, "Always on", "No Google services, no telemetry, nothing sent anywhere.", null, null));
        UI.reveal(c);
        return UI.scaffold("Privacy", null, UI.scroll(c), new UI.Act() { @Override public void run() { a.pop(); } });
    }
}

class ThemeScreen extends Screen {
    static float lum(int c) { return (0.299f * ((c >> 16) & 255) + 0.587f * ((c >> 8) & 255) + 0.114f * (c & 255)) / 255f; }
    @Override View build() {
        LinearLayout c = Pages.col();
        Flow modes = UI.flow();
        for (int i = 0; i < 4; i++) {
            final int m = i;
            View tile = new View(a) {
                final android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG); final android.graphics.RectF r = new android.graphics.RectF();
                @Override protected void onDraw(android.graphics.Canvas cv) {
                    float w = getWidth(), h = getHeight(), rad = UI.dp(18);
                    pt.setStyle(android.graphics.Paint.Style.FILL);
                    pt.setColor(Theme.col(Theme.pal, m, Theme.BG)); r.set(0, 0, w, h); cv.drawRoundRect(r, rad, rad, pt);
                    pt.setColor(Theme.col(Theme.pal, m, Theme.CONT_H)); r.set(w * 0.09f, h * 0.13f, w * 0.91f, h * 0.5f); cv.drawRoundRect(r, rad * 0.55f, rad * 0.55f, pt);
                    pt.setColor(Theme.col(Theme.pal, m, Theme.P)); cv.drawCircle(w * 0.21f, h * 0.315f, h * 0.085f, pt);
                    pt.setColor(Theme.col(Theme.pal, m, Theme.ON_SURF)); pt.setTypeface(Theme.semi); pt.setTextSize(UI.dp(13)); pt.setTextAlign(android.graphics.Paint.Align.CENTER);
                    cv.drawText(Theme.MODES[m], w / 2f, h * 0.84f, pt);
                    pt.setStyle(android.graphics.Paint.Style.STROKE);
                    boolean on = Theme.mode == m;
                    pt.setStrokeWidth(UI.dp(on ? 3.5f : 1.2f)); pt.setColor(on ? Theme.col(Theme.pal, m, Theme.P) : (Theme.col(Theme.pal, m, Theme.OUTV) & 0xFFFFFF) | 0x99000000);
                    float in = pt.getStrokeWidth() / 2f; r.set(in, in, w - in, h - in); cv.drawRoundRect(r, rad, rad, pt);
                }
            };
            FrameLayout wrap = new FrameLayout(a); wrap.setPadding(UI.dp(6), UI.dp(6), UI.dp(6), UI.dp(6));
            wrap.setBackground(UI.sel(0x00000000, 26));
            wrap.addView(tile, new FrameLayout.LayoutParams(UI.dp(128), UI.dp(84)));
            UI.focusable(wrap, 1.05f);
            UI.click(wrap, new UI.Act() { @Override public void run() { Theme.set(Theme.pal, m); a.rebuild(); } });
            modes.addView(wrap);
        }
        c.addView(modes);
        c.addView(UI.section(Theme.labels[Theme.pal]));
        Flow row = UI.flow();
        c.addView(row, new LinearLayout.LayoutParams(-1, -2));
        for (int i = 0; i < Theme.ids.length; i++) {
            final int p = i;
            final int seed = Theme.seeds[i] | 0xFF000000;
            final boolean light = lum(seed) > 0.6f;
            View sw = new View(a) {
                final android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(android.graphics.Canvas cv) {
                    float w = getWidth(), cx = w / 2f, cy = getHeight() / 2f;
                    pt.setStyle(android.graphics.Paint.Style.FILL); pt.setColor(seed);
                    cv.drawCircle(cx, cy, w * 0.33f, pt);
                    if (p == Theme.pal) {
                        pt.setStyle(android.graphics.Paint.Style.STROKE); pt.setStrokeWidth(UI.dp(2.5f)); pt.setColor(Theme.c(Theme.ON_SURF));
                        cv.drawCircle(cx, cy, w * 0.41f, pt);
                        Icons.draw(cv, Icons.CHECK, w * 0.3f, w * 0.3f, w * 0.4f, light ? 0xFF1B1B1F : 0xFFFFFFFF);
                    }
                }
            };
            sw.setBackground(UI.sel(Theme.c(Theme.CONT), 40));
            UI.focusable(sw, 1.12f);
            UI.click(sw, new UI.Act() { @Override public void run() { Theme.set(p, Theme.mode); a.rebuild(); } });
            row.addView(sw, new ViewGroup.LayoutParams(UI.dp(64), UI.dp(64)));
        }
        return UI.scaffold("Themes", null, UI.scroll(c), new UI.Act() { @Override public void run() { a.pop(); } });
    }
}
