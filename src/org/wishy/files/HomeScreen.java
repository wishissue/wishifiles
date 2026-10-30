package org.wishy.files;

import android.view.*;
import android.widget.*;
import java.io.File;

/**
 * Home, laid out like a quick-settings panel: the thing you use most is the biggest and highest, then the next two, then two small ones.
 * Phone (portrait): Files full width, Transfer and Install side by side, Appearance and About below.
 * TV / landscape: Files tall on the left, Transfer and Install stacked on the right, Appearance and About along the bottom.
 */
public class HomeScreen extends Screen {
    @Override View build() {
        final boolean narrow = Tiles.narrow();
        int g = UI.grow();
        LinearLayout root = UI.vbox();
        root.setPadding(Math.max(0, Theme.padX() - g), Math.max(0, Theme.padY() - g), Math.max(0, Theme.padX() - g), Math.max(0, Theme.padY() - g));

        // header: wordmark on the left, a permission nudge on the right when access is missing
        LinearLayout head = UI.hbox();
        head.setPadding(g, g, g, UI.dp(14));
        LinearLayout nm = UI.vbox();
        nm.addView(UI.brand("Wishi Files", 26, Theme.c(Theme.P)));
        head.addView(nm, new LinearLayout.LayoutParams(0, -2, 1f));
        final boolean access = Storage.hasAccess();
        if (!access && !narrow) head.addView(UI.chip(Icons.WARN, "Allow file access", false, new UI.Act() { @Override public void run() { Storage.request(a); } }));
        root.addView(head);

        int cw = Tiles.contentW();
        final TileGrid grid = Tiles.grid(cw);
        if (!narrow) {
            int availH = Theme.hPx - 2 * Theme.padY() - UI.dp(66);
            grid.rowH = Math.max(UI.dp(64), (availH - 3 * grid.gap) / 4);
        }
        grid.setPadding(g, 0, g, 0);

        if (!access && narrow) {
            Tiles.Tile w = Tiles.make(Prio.M, Tiles.WARN, "Allow file access", "Needed to browse files").icon(Icons.WARN).arrow();
            w.onTap(new UI.Act() { @Override public void run() { Storage.request(a); } });
            grid.add(w, 4, 1);
        }

        // 1. Files: the hero. Shows free space and a folder with papers in it.
        String sub = null; float used = -1f;
        try {
            File in = android.os.Environment.getExternalStorageDirectory();
            long total = in.getTotalSpace(), free = in.getUsableSpace();
            if (total > 0) { sub = UI.size(free) + " free of " + UI.size(total); used = 1f - (float) free / total; }
        } catch (Exception ignored) { }
        Tiles.Tile files = Tiles.make(Prio.XL, Tiles.HERO, "Files", sub).folder(access ? 12 : 0).arrow();
        if (used >= 0) files.meter(used);
        files.onTap(new UI.Act() { @Override public void run() { a.push(new FilesScreen(null, null)); } }).peek();
        grid.add(files, 4, narrow ? 2 : 3);

        // 2. Transfer: lights up solid when the server is on
        boolean live = TransferScreen.SERVER != null && TransferScreen.SERVER.running;
        String ts = live ? "On  \u00b7  " + (TransferScreen.SERVER.clients.size() == 0 ? "waiting" : TransferScreen.SERVER.clients.size() + " connected") : "Off";
        Tiles.Tile tr = Tiles.make(Prio.L, live ? Tiles.ACTIVE : Tiles.SECOND, "Transfer", ts).icon(Icons.SWAP).arrow();
        tr.onTap(new UI.Act() { @Override public void run() { a.push(new TransferScreen()); } });
        grid.add(tr, narrow ? 2 : 4, 2);

        // 3. Install
        Tiles.Tile in = Tiles.make(narrow ? Prio.L : Prio.M, Tiles.NEUTRAL, "Install", "APK  \u00b7  XAPK  \u00b7  APKS").icon(Icons.INSTALL_FILL).arrow();
        in.onTap(new UI.Act() { @Override public void run() { a.push(new InstallScreen(null)); } });
        grid.add(in, narrow ? 2 : 4, narrow ? 2 : 1);

        // 4 & 5. Appearance, About
        Tiles.Tile ap = Tiles.make(Prio.M, Tiles.QUIET, "Theme", Theme.labels[Theme.pal] + "  \u00b7  " + Theme.MODES[Theme.mode]).icon(Icons.TUNE).arrow();
        ap.onTap(new UI.Act() { @Override public void run() { a.push(new ThemeScreen()); } });
        grid.add(ap, narrow ? 2 : 4, 1);
        Tiles.Tile ab = Tiles.make(Prio.M, Tiles.QUIET, "About", "Version 1.4.0").icon(Icons.INFO).arrow();
        ab.onTap(new UI.Act() { @Override public void run() { a.push(new AboutScreen()); } });
        grid.add(ab, narrow ? 2 : 4, 1);

        root.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        UI.popAll(grid);
        ScrollView sv = UI.scroll(root);
        sv.setPadding(0, 0, 0, 0);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Theme.c(Theme.BG));
        return sv;
    }
}
