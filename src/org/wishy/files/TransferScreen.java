package org.wishy.files;

import android.graphics.*;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class TransferScreen extends Screen {
    static TransferServer SERVER; static String PAGE; static final ArrayList<String> EVENTS = new ArrayList<String>(); static String stopReason;
    private boolean userStopped;
    private View dot; private boolean dotOn; private LinearLayout upBox, shBox; private TextView status, urlTv; private QrView qr; private String sig = "";
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() { @Override public void run() { update(); h.postDelayed(this, 600); } };

    static final class QrView extends View {
        Qr q; int placeholder = Icons.POWER;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        QrView(android.content.Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), rad = UI.dp(24);
            if (q == null) {
                // nothing to scan: a calm placeholder instead of a blank white square
                p.setColor(Theme.alpha(Theme.c(Theme.ON_PC), 0.08f));
                c.drawRoundRect(new RectF(0, 0, w, getHeight()), rad, rad, p);
                float s = w * 0.34f;
                Icons.draw(c, placeholder, (w - s) / 2f, (getHeight() - s) / 2f, s, Theme.alpha(Theme.c(Theme.ON_PC), 0.55f));
                return;
            }
            p.setColor(Color.WHITE);
            c.drawRoundRect(new RectF(0, 0, w, getHeight()), rad, rad, p);
            p.setColor(0xFF111111);
            float quiet = w * 0.07f, m = (w - 2 * quiet) / q.n;
            for (int y = 0; y < q.n; y++) for (int x = 0; x < q.n; x++) if (q.get(x, y)) c.drawRect(quiet + x * m, quiet + y * m, quiet + (x + 1) * m + 0.6f, quiet + (y + 1) * m + 0.6f, p);
        }
    }

    private Tiles.Tile power, newCode;

    @Override View build() {
        final boolean narrow = Tiles.narrow();
        int g = UI.grow();
        int qs = narrow ? Math.max(180, Math.min(280, (int) Theme.wDp() - 150)) : (int) Math.min(290, Math.max(210, Theme.hDp() - 230));

        // hero: the QR code, what state we are in, and the address
        LinearLayout hero = UI.vbox(); hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(UI.dp(18), UI.dp(18), UI.dp(18), UI.dp(18));
        hero.setBackground(UI.rr(Theme.c(Theme.PC), 34));
        qr = new QrView(a);
        hero.addView(qr, new LinearLayout.LayoutParams(UI.dp(qs), UI.dp(qs)));
        LinearLayout sr = UI.hbox(); sr.setPadding(0, UI.dp(16), 0, 0);
        dot = new View(a); dot.setBackground(UI.rr(Theme.c(Theme.ERR), 8));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(UI.dp(11), UI.dp(11)); dl.rightMargin = UI.dp(10);
        sr.addView(dot, dl);
        status = UI.tv("", 18, true, Theme.c(Theme.ON_PC)); status.setSingleLine(true);
        sr.addView(status);
        hero.addView(sr);
        urlTv = UI.tv("", 13.5f, false, Theme.alpha(Theme.c(Theme.ON_PC), 0.8f)); urlTv.setGravity(Gravity.CENTER); urlTv.setPadding(0, UI.dp(4), 0, 0); urlTv.setMaxWidth(UI.dp(qs));
        hero.addView(urlTv);

        // actions, laid out like quick settings: the main switch is the big one
        int gw = narrow ? Tiles.contentW() : Theme.wPx - 2 * Theme.padX() - UI.dp(qs + 36) - UI.dp(28);
        TileGrid grid = new TileGrid(a); grid.cols = 4; grid.gap = UI.dp(10); grid.rowH = UI.dp(narrow ? 84 : 78);
        power = Tiles.make(Prio.L, Tiles.SECOND, "Sharing", "Off").icon(Icons.POWER);
        power.onTap(new UI.Act() { @Override public void run() {
            if (SERVER != null && SERVER.running) { userStopped = true; stop("Sharing stopped."); } else { userStopped = false; start(); } } });
        grid.add(power, 2, 2);
        newCode = Tiles.make(Prio.M, Tiles.NEUTRAL, "New code", null).icon(Icons.REFRESH);
        newCode.onTap(new UI.Act() { @Override public void run() { if (SERVER != null && SERVER.running) { SERVER.rotateToken(); sig = ""; update(); UI.toast("New code. Old links and connected devices are locked out."); } } });
        grid.add(newCode, 2, 1);
        Tiles.Tile sh = Tiles.make(Prio.M, Tiles.NEUTRAL, "Send a file", null).icon(Icons.SHARE);
        sh.onTap(new UI.Act() { @Override public void run() {
            a.push(new FilesScreen(null, new FilesScreen.Picker() { public void pick(File f) { TransferServer.share(f); UI.toast("Shared: " + f.getName()); } })); } });
        grid.add(sh, 2, 1);
        Tiles.Tile rc = Tiles.make(Prio.M, Tiles.QUIET, "Received files", "From your other devices").icon(Icons.INBOX).arrow();
        rc.onTap(new UI.Act() { @Override public void run() { a.push(new FilesScreen(Storage.recvDir(), null)); } });
        grid.add(rc, 4, 1);

        upBox = UI.vbox(); upBox.setPadding(0, UI.dp(14), 0, 0);
        shBox = UI.vbox();
        LinearLayout r = UI.vbox(); r.setPadding(0, 0, 0, UI.dp(30));
        r.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        r.addView(upBox); r.addView(shBox);

        View content;
        if (narrow) {
            LinearLayout col = UI.vbox();
            col.addView(hero, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2); rl.topMargin = UI.dp(14);
            col.addView(r, rl);
            content = UI.scroll(col);
            UI.popAll(grid);
        } else {
            LinearLayout root = UI.hbox(); root.setGravity(Gravity.TOP); root.setPadding(g, g, 0, g);
            root.addView(hero, new LinearLayout.LayoutParams(UI.dp(qs + 36), -2));
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, -1, 1f); sl.leftMargin = UI.dp(28) - g;
            root.addView(UI.scroll(r), sl);
            content = root;
            UI.popAll(grid);
        }
        sig = "";
        return UI.scaffold("Transfer", null, content, new UI.Act() { @Override public void run() { a.pop(); } });
    }

    @Override void onShow() {
        sig = "";
        if ((SERVER == null || !SERVER.running) && !userStopped) start();
        h.removeCallbacks(tick); h.post(tick);
    }
    @Override void onHide() { h.removeCallbacks(tick); dotOn = false; if (dot != null) { dot.animate().cancel(); dot.setAlpha(1f); } }

    private void start() {
        if (SERVER != null && SERVER.running) return;
        if (TransferServer.localIp() == null) { update(); return; }
        try {
            if (PAGE == null) {
                InputStream in = a.getAssets().open("web/index.html"); ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) > 0) o.write(b, 0, n); in.close(); PAGE = o.toString("UTF-8");
            }
            stopReason = null;
            SERVER = new TransferServer(new TransferServer.Host() {
                public File recvDir() { return Storage.recvDir(); }
                public String device() { return Build.MODEL == null ? "Android TV" : Build.MODEL; }
                public String page() { return PAGE; }
                public String css() { return Theme.css(); }
                public void onEvent(final String m) { synchronized (EVENTS) { EVENTS.add(0, m); while (EVENTS.size() > 8) EVENTS.remove(EVENTS.size() - 1); } }
                public void onStopped(final String why) { stopReason = why; a.runOnUiThread(new Runnable() { public void run() { a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); } }); }
            });
            SERVER.idleMs = Theme.prefs().getBoolean("autostop", true) ? 10 * 60 * 1000L : Long.MAX_VALUE / 4;
            SERVER.start();
            if (Theme.prefs().getBoolean("keepon", true)) a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            synchronized (EVENTS) { EVENTS.clear(); }
        } catch (Exception e) { stopReason = "Couldn't start the server: " + e.getMessage(); }
        sig = ""; update();
    }

    private void stop(String why) { if (SERVER != null) SERVER.stop(why); sig = ""; update(); }

    static void stopAll() { if (SERVER != null) SERVER.stop("App closed."); }

    private String url() { return "http://" + TransferServer.localIp() + ":" + SERVER.port + "/?t=" + SERVER.token; }

    private void update() {
        boolean on = SERVER != null && SERVER.running;
        String ip = TransferServer.localIp();
        String s = on ? "on:" + SERVER.token : "off";
        if (!s.equals(sig)) {
            sig = s;
            if (on && ip != null) { String u = url(); qr.q = Qr.encode(u); urlTv.setText("http://" + ip + ":" + SERVER.port); status.setText("Scan to connect"); }
            else { qr.q = null; qr.placeholder = ip == null ? Icons.WIFI_OFF : Icons.POWER; urlTv.setText(""); status.setText(ip == null ? "No network" : stopReason != null ? stopReason : "Off"); }
            qr.invalidate();
            qr.setAlpha(0f); qr.setScaleX(0.9f); qr.setScaleY(0.9f);
            qr.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(UI.EASE).start();
            dot.setBackground(UI.rr(on && ip != null ? Theme.c(Theme.P) : Theme.c(Theme.ERR), 8));
            pulse(on && ip != null);
        }
        boolean live = on && ip != null;
        int nc = on ? SERVER.clients.size() : 0;
        String ps = live ? "On  \u00b7  " + (nc == 0 ? "waiting" : nc + " connected") : ip == null ? "No network" : "Off";
        if (!ps.contentEquals(power.subTv.getText())) power.text("Sharing", ps);
        int want = live ? Tiles.ACTIVE : Tiles.SECOND;
        if (power.kind != want) power.kind(want);
        upBox.removeAllViews();
        if (!on) { shBox.removeAllViews(); sigSh = ""; return; }
        for (TransferServer.Upload u : SERVER.uploads()) {
            if (u.done && System.currentTimeMillis() - u.touched > 20000) continue;
            LinearLayout b = UI.vbox(); b.setPadding(UI.dp(18), UI.dp(14), UI.dp(18), UI.dp(16)); b.setBackground(UI.rr(Theme.c(Theme.CONT), 24));
            int pct = u.size == 0 ? 100 : (int) (u.received * 100 / u.size);
            LinearLayout top = UI.hbox();
            top.addView(UI.iconTile(u.done ? Icons.CHECKED : Icons.UPLOAD, 38, u.done ? Theme.c(Theme.P) : Theme.c(Theme.PC), u.done ? Theme.c(Theme.ON_P) : Theme.c(Theme.ON_PC)));
            LinearLayout nm = UI.vbox(); nm.setPadding(UI.dp(12), 0, 0, 0);
            TextView n = UI.tv((u.finalName != null ? u.finalName : u.name), 15, true, Theme.c(Theme.ON_SURF)); n.setSingleLine(true); n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE); nm.addView(n);
            TextView m = UI.tv((u.done ? "Received  \u00b7  " : "Receiving  \u00b7  ") + UI.size(u.received) + " of " + UI.size(u.size) + "  (" + pct + "%)", 12.5f, false, Theme.c(Theme.ON_SV)); m.setPadding(0, UI.dp(2), 0, 0); nm.addView(m);
            top.addView(nm, new LinearLayout.LayoutParams(0, -2, 1f)); b.addView(top);
            UI.Meter bar = new UI.Meter(a); bar.set(pct / 100f);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, UI.dp(6)); bl.topMargin = UI.dp(12); b.addView(bar, bl);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = UI.dp(8); upBox.addView(b, lp);
        }
        StringBuilder g = new StringBuilder(); for (TransferServer.Shared x : TransferServer.SHARED) g.append(x.id);
        if (!g.toString().equals(sigSh)) {
            sigSh = g.toString(); shBox.removeAllViews();
            if (!TransferServer.SHARED.isEmpty()) {
                shBox.addView(UI.section("Shared"));
                for (final TransferServer.Shared x : TransferServer.SHARED) {
                    LinearLayout row = UI.rowPlain(Icons.SHARE, x.file.getName(), UI.size(x.file.length()), UI.trail(Icons.CLOSE, Theme.c(Theme.ON_SV)), new UI.Act() { @Override public void run() { TransferServer.SHARED.remove(x); sigSh = ""; update(); } });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = UI.dp(8); shBox.addView(row, lp);
                }
            }
        }
    }
    private String sigSh = "";

    /** A calm breathing dot while the server is live (alpha only, so it is free). */
    private void pulse(boolean live) {
        dot.animate().cancel();
        dot.setAlpha(1f); dotOn = live;
        if (live) loop();
    }
    private void loop() {
        if (!dotOn) return;
        dot.animate().alpha(0.3f).setDuration(900).setInterpolator(UI.EASE).withEndAction(new Runnable() { @Override public void run() {
            if (!dotOn) return;
            dot.animate().alpha(1f).setDuration(900).setInterpolator(UI.EASE).withEndAction(new Runnable() { @Override public void run() { loop(); } }).start(); } }).start();
    }
}
