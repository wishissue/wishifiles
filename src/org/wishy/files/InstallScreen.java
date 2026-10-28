package org.wishy.files;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;

public class InstallScreen extends Screen {
    private File file; private Apk apk; private Apk.Dev dev; private List<Apk.Msg> msgs; private CharSequence label; private Drawable icon;
    private FrameLayout body; private Runnable render; private boolean installing; private TextView stage;

    public InstallScreen(File f) { file = f; }

    @Override View build() {
        body = new FrameLayout(a);
        UI.Act back = new UI.Act() { @Override public void run() { a.pop(); } };
        View v = UI.scaffold("Install", null, body, back);
        if (render != null) render.run(); else if (file == null) listMode(); else inspect(file);
        return v;
    }

    private void setBody(View v) { body.removeAllViews(); body.addView(v, new FrameLayout.LayoutParams(-1, -1)); }

    private void busy(String s) {
        LinearLayout l = UI.vbox(); l.setGravity(Gravity.CENTER);
        l.addView(UI.spinner(52));
        TextView t = UI.tv(s, 17, false, Theme.c(Theme.ON_SV)); t.setPadding(0, UI.dp(16), 0, 0); l.addView(t); setBody(l);
    }

    // ---- pick an APK from the usual places ----
    private void listMode() {
        render = new Runnable() { public void run() { listMode(); } };
        busy("Searching\u2026");
        new Thread(new Runnable() { @Override public void run() {
            final ArrayList<File> found = new ArrayList<File>();
            ArrayList<File> roots = new ArrayList<File>();
            roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)); roots.add(Storage.recvDir());
            for (Storage.Root r : Storage.roots()) roots.add(r.dir);
            for (File r : roots) scan(r, 0, found);
            a.runOnUiThread(new Runnable() { @Override public void run() { showList(found); } });
        } }).start();
    }

    private void scan(File d, int depth, ArrayList<File> out) {
        File[] c = d.listFiles();
        if (c == null || depth > 2 || out.size() > 150) return;
        for (File f : c) {
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory()) { if (!f.getName().equals("Android")) scan(f, depth + 1, out); }
            else if (Apk.isInstallable(f.getName()) && !out.contains(f)) out.add(f);
        }
    }

    private void showList(ArrayList<File> found) {
        LinearLayout c = Pages.col();
        Collections.sort(found, new Comparator<File>() { public int compare(File x, File y) { return Long.compare(y.lastModified(), x.lastModified()); } });
        TileGrid g = Tiles.grid(Tiles.contentW()); g.rowH = UI.dp(Tiles.narrow() ? 84 : 78); g.cols = 4;
        Tiles.Tile br = Tiles.make(Prio.M, Tiles.SECOND, "Browse files", "Pick an APK yourself").icon(Icons.FOLDER).arrow();
        br.onTap(new UI.Act() { @Override public void run() { a.push(new FilesScreen(null, null)); } });
        g.add(br, 4, 1);
        c.addView(g, new LinearLayout.LayoutParams(-1, -2));
        if (found.isEmpty()) {
            LinearLayout e = UI.vbox(); e.setGravity(Gravity.CENTER_HORIZONTAL); e.setPadding(0, UI.dp(36), 0, 0);
            View ic = UI.iconTile(Icons.APK, 88, Theme.c(Theme.PC), Theme.c(Theme.ON_PC)); e.addView(ic); UI.popIn(ic);
            TextView t = UI.tv("No APK files found", 21, true, Theme.c(Theme.ON_SURF)); t.setPadding(0, UI.dp(16), 0, UI.dp(4)); e.addView(t);
            e.addView(UI.tv("Looked in Downloads and your storage", 14, false, Theme.c(Theme.ON_SV)));
            c.addView(e);
        } else {
            c.addView(UI.section("Found  \u00b7  " + found.size()));
            for (final File f : found) {
                LinearLayout r = UI.row(Icons.APK, f.getName(), UI.size(f.length()) + "  \u00b7  " + f.getParent(), null, new UI.Act() { @Override public void run() { file = f; inspect(f); } });
                ImageView im = new ImageView(a); im.setScaleType(ImageView.ScaleType.CENTER_CROP); UI.roundClip(im, 15);
                ((FrameLayout) r.getChildAt(0)).addView(im, new FrameLayout.LayoutParams(-1, -1));
                Thumbs.load(im, f, UI.dp(46), null);
                Pages.add(c, r);
            }
        }
        setBody(UI.scroll(c)); UI.reveal(c);
        UI.popAll(g);
    }

    // ---- inspect ----
    private void inspect(final File f) {
        render = new Runnable() { public void run() { inspect(f); } };
        busy("Inspecting\u2026");
        new Thread(new Runnable() { @Override public void run() {
            final Apk k = Apk.inspect(f, a.getCacheDir());
            final Apk.Dev d = Installer.device(a, k);
            CharSequence lb = null; Drawable ic = null;
            try {
                PackageManager pm = a.getPackageManager();
                PackageInfo pi = pm.getPackageArchiveInfo(k.file.getAbsolutePath(), 0);
                if (pi != null) { ApplicationInfo ai = pi.applicationInfo; ai.sourceDir = ai.publicSourceDir = k.file.getAbsolutePath(); lb = ai.loadLabel(pm); ic = ai.loadIcon(pm); }
            } catch (Throwable ignored) { }
            final CharSequence flb = lb; final Drawable fic = ic;
            a.runOnUiThread(new Runnable() { @Override public void run() { apk = k; dev = d; msgs = k.check(d); label = flb; icon = fic; showApk(); } });
        } }).start();
    }

    private TileGrid facts;
    private void fact(LinearLayout c, int ic, String t, String s) {
        if (facts == null || facts.getParent() != c) { facts = new TileGrid(a); facts.cols = 2; facts.gap = UI.dp(10); facts.rowH = UI.dp(Tiles.narrow() ? 78 : 70); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = UI.dp(10); c.addView(facts, lp); }
        facts.add(Tiles.info(ic, t, s), Tiles.narrow() ? 2 : 1, 1);
    }

    private int tone(int level) { return level == 2 ? Theme.c(Theme.ERR) : level == 1 ? Theme.tone(6) : Theme.c(Theme.PC); }
    private View status(int icon, String title, String body, int level) {
        int bg = tone(level);
        LinearLayout r = UI.rowTone(icon, title, body, bg, level == 0 ? Theme.c(Theme.ON_PC) : Theme.on(bg));
        r.setBackground(UI.rr(level == 0 ? Theme.c(Theme.CONT) : Theme.toneBg(bg, 0.16f), 22));
        return r;
    }

    private void showApk() {
        render = new Runnable() { public void run() { showApk(); } };
        LinearLayout c = Pages.col();
        facts = null;
        LinearLayout h = UI.hbox(); h.setPadding(UI.dp(22), UI.dp(20), UI.dp(22), UI.dp(20)); h.setBackground(UI.rr(Theme.c(Theme.PC), 34));
        if (icon != null) { ImageView iv = new ImageView(a); iv.setImageDrawable(icon); h.addView(iv, new LinearLayout.LayoutParams(UI.dp(76), UI.dp(76))); }
        else h.addView(UI.iconTile(Icons.APK, 72, Theme.c(Theme.P), Theme.c(Theme.ON_P)));
        LinearLayout t = UI.vbox(); t.setPadding(UI.dp(20), 0, 0, 0);
        String title = apk.parseFailed ? file.getName() : (label != null ? label.toString() : apk.pkg);
        TextView n = UI.tv(title, 24, true, Theme.c(Theme.ON_PC)); n.setSingleLine(true); t.addView(n);
        TextView p = UI.tv(apk.parseFailed ? "Unreadable file" : apk.pkg + "  \u00b7  v" + apk.versionName + " (" + apk.versionCode + ")", 14, false, Theme.c(Theme.ON_PC)); p.setPadding(0, UI.dp(4), 0, 0); t.addView(p);
        h.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        Pages.add(c, h);

        if (!apk.parseFailed) {
            c.addView(UI.section("Checks"));
            for (Apk.Msg m : msgs) {
                Pages.add(c, status(m.level == 2 ? Icons.ERROR : m.level == 1 ? Icons.WARN : Icons.CHECKED, m.title, m.body, m.level));
            }
            if (msgs.isEmpty() || !Apk.hasBlocker(msgs)) Pages.add(c, status(Icons.CHECKED, "Looks compatible", "Android " + dev.release + "  \u00b7  " + Apk.join(Arrays.asList(dev.abis)) + "  \u00b7  " + UI.size(dev.freeBytes == Long.MAX_VALUE ? 0 : dev.freeBytes) + " free", 0));
            c.addView(UI.section("Details"));
            Pages.add(c, UI.row(Icons.APK, "File", file.getName() + "  \u00b7  " + UI.size(apk.size), null, null));
            fact(c, Icons.INFO, "Android versions", "Needs " + Apk.androidName(apk.minSdk) + " or newer  \u00b7  built for " + Apk.androidName(apk.targetSdk));
            fact(c, Icons.CPU, "CPU", apk.abis.isEmpty() && apk.bundleAbis.isEmpty() ? "No native code, runs on any CPU" : "Native code for " + Apk.join(apk.bundle ? apk.bundleAbis : apk.abis));
            fact(c, Icons.PACKAGE, "Package type", apk.bundle ? "Bundle of " + apk.bundleEntries.size() + " APKs (split install)" : apk.splitName != null ? "Split APK: " + apk.splitName : "Single APK");
            fact(c, Icons.SHIELD, "Permissions requested", String.valueOf(apk.permissions));
        } else {
            c.addView(UI.section("Checks"));
            for (Apk.Msg m : msgs) Pages.add(c, status(Icons.ERROR, m.title, m.body, 2));
        }
        c.addView(UI.space(1, 8));
        Flow acts = UI.flow();
        if (!apk.parseFailed) {
            boolean block = Apk.hasBlocker(msgs);
            acts.addView(UI.chip(Icons.INSTALL, block ? "Try anyway" : "Install", !block, new UI.Act() { @Override public void run() { startInstall(); } }));
            if (dev.installedCode >= 0) {
                acts.addView(sp()); acts.addView(UI.chip(Icons.PLAY, "Open app", false, new UI.Act() { @Override public void run() { if (!Installer.launch(a, apk.pkg)) UI.toast("This app has no launcher screen."); } }));
                acts.addView(sp()); acts.addView(UI.chip(Icons.TRASH, "Uninstall", false, new UI.Act() { @Override public void run() { Installer.uninstall(a, apk.pkg); } }));
            }
        }
        acts.addView(sp()); acts.addView(UI.chip(Icons.FOLDER, "Pick another", false, new UI.Act() { @Override public void run() { file = null; apk = null; listMode(); } }));
        c.addView(acts, new LinearLayout.LayoutParams(-1, -2));
        setBody(UI.scroll(c)); UI.reveal(c);
    }

    private View sp() { View v = new View(a); v.setVisibility(View.GONE); return v; }

    // ---- install ----
    private void startInstall() {
        if (!Installer.canInstall(a)) {
            Dlg.make("Allow installs from Wishi Files").msg("Android needs your permission once. Turn on \"Allow from this source\" for Wishi Files, then come back and press Install again.")
                .btn("Cancel", false, null).btn("Open settings", true, new UI.Act() { @Override public void run() { Installer.unknownSourcesSettings(a); } }).show();
            return;
        }
        installing = true;
        render = new Runnable() { public void run() { progress("Preparing\u2026"); } };
        progress("Preparing\u2026");
        Installer.listener = new Installer.Listener() {
            public void onStage(String s) { if (stage != null) stage.setText(s); }
            public void onResult(int st, String msg, String pkg) { installing = false; result(st, msg, pkg); }
        };
        Installer.install(a, apk, dev);
    }

    private void progress(String s) {
        LinearLayout l = UI.vbox(); l.setGravity(Gravity.CENTER);
        l.addView(UI.spinner(60));
        stage = UI.tv(s, 18, true, Theme.c(Theme.ON_SURF)); stage.setPadding(0, UI.dp(18), 0, UI.dp(6)); l.addView(stage);
        TextView n = UI.tv("Confirm when Android asks", 14, false, Theme.c(Theme.ON_SV)); l.addView(n);
        setBody(l);
    }

    private void result(int st, String msg, final String pkg) {
        LinearLayout c = Pages.col(); c.setGravity(Gravity.CENTER_HORIZONTAL);
        if (st == 0) {
            render = new Runnable() { public void run() { result(0, null, pkg); } };
            View ok = UI.iconTile(Icons.CHECKED, 104, Theme.c(Theme.P), Theme.c(Theme.ON_P)); c.addView(ok); UI.popIn(ok);
            TextView t = UI.tv("Installed", 28, true, Theme.c(Theme.ON_BG)); t.setPadding(0, UI.dp(20), 0, UI.dp(6)); c.addView(t);
            c.addView(UI.tv(label != null ? label.toString() : apk.pkg, 16, false, Theme.c(Theme.ON_SV)));
            Flow acts = UI.flow(); acts.align = Flow.CENTER; acts.setPadding(0, UI.dp(22), 0, 0);
            acts.addView(UI.chip(Icons.PLAY, "Open", true, new UI.Act() { @Override public void run() { if (!Installer.launch(a, apk.pkg)) UI.toast("This app has no launcher screen."); } }));
            acts.addView(sp()); acts.addView(UI.chip(Icons.INSTALL, "Install another", false, new UI.Act() { @Override public void run() { file = null; apk = null; render = null; listMode(); } }));
            c.addView(acts);
        } else {
            final Apk.Why w = Apk.explain(st, msg, apk, dev);
            render = new Runnable() { public void run() { result(-2, null, pkg); } };
            c.setGravity(Gravity.TOP);
            LinearLayout card = UI.hbox(); card.setPadding(UI.dp(22), UI.dp(20), UI.dp(22), UI.dp(20)); card.setBackground(UI.rr(Theme.c(Theme.CONT), 34));
            int tb = st == 3 ? Theme.c(Theme.P) : Theme.c(Theme.ERR);
            card.addView(UI.iconTile(st == 3 ? Icons.INFO : Icons.ERROR, 64, tb, Theme.on(tb)));
            LinearLayout t = UI.vbox(); t.setPadding(UI.dp(20), 0, 0, 0);
            t.addView(UI.tv(w.title, 23, true, Theme.c(Theme.ON_SURF)));
            TextView b = UI.tv(w.body, 15.5f, false, Theme.c(Theme.ON_SV)); b.setLineSpacing(0, 1.2f); b.setPadding(0, UI.dp(8), 0, 0); t.addView(b);
            card.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            Pages.add(c, card);
            if (msg != null && msg.length() > 0 && st != 3) { TextView raw = UI.tv("Android's message: " + msg + " (status " + st + ")", 12.5f, false, Theme.c(Theme.ON_SV)); raw.setPadding(UI.dp(6), 0, 0, UI.dp(10)); c.addView(raw); }
            Flow acts = UI.flow();
            acts.addView(UI.chip(Icons.REFRESH, "Try again", true, new UI.Act() { @Override public void run() { render = null; showApk(); } }));
            if (w.offerUninstall && apk != null) { acts.addView(sp()); acts.addView(UI.chip(Icons.TRASH, "Uninstall existing", false, new UI.Act() { @Override public void run() { Installer.uninstall(a, apk.pkg); } })); }
            if (w.offerSettings) { acts.addView(sp()); acts.addView(UI.chip(Icons.TUNE, "Install settings", false, new UI.Act() { @Override public void run() { Installer.unknownSourcesSettings(a); } })); }
            c.addView(acts, new LinearLayout.LayoutParams(-1, -2));
        }
        setBody(UI.scroll(c)); UI.reveal(c);
    }

    @Override void onShow() {
        Installer.listener = new Installer.Listener() {
            public void onStage(String s) { if (stage != null) stage.setText(s); }
            public void onResult(int st, String msg, String pkg) { installing = false; result(st, msg, pkg); }
        };
        if (installing && Installer.lastStatus != Integer.MIN_VALUE) { installing = false; result(Installer.lastStatus, Installer.lastMsg, Installer.lastPkg); }
        else if (apk != null && !installing && render != null && Installer.canInstall(a)) { /* keep current view */ }
    }
    @Override void onHide() { if (!installing) Installer.listener = null; }
}
