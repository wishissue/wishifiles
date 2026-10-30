package org.wishy.files;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.*;

/**
 * Files. Folders are a tile grid ranked by priority (the folders you use and fill most are biggest and highest, empty ones are small and last,
 * and an empty folder is drawn hollow while a full one shows papers). Files sit below as a grid of previews or a list.
 * Opening a folder swings its flap open and zooms into it; going back zooms out.
 */
public class FilesScreen extends Screen {
    public interface Picker { void pick(File f); }

    static final class Entry { File f; String title, sub; int type, icon, color; }
    static final ArrayList<File> CLIP = new ArrayList<File>(); static boolean clipCut;

    private File cwd; private final File start; private final Picker picker;
    private String pickTitle = "Choose a file to share"; private String[] accept;   // picker mode: heading, and the MIME types the caller can take
    private final ArrayList<Entry> items = new ArrayList<Entry>();          // files (or everything, in search results)
    private final ArrayList<Prio.Item> folders = new ArrayList<Prio.Item>();
    private final HashMap<File, Tiles.Tile> tiles = new HashMap<File, Tiles.Tile>();
    private final LinkedHashSet<File> sel = new LinkedHashSet<File>();
    private boolean searching, byPrio, busy;
    private int sortBy; private boolean desc, hidden; private int gen;
    private UI.Page page; private ListView list; private LinearLayout header, crumbs; private HorizontalScrollView crumbScroll; private LinearLayout fab; private BaseAdapter adapter;
    private boolean curGrid; private int nCols = 1, gridPx, listPx; private boolean animNext = true;
    private String searchNote = "";
    private volatile boolean[] stopSearch = new boolean[1];

    public FilesScreen(File start, Picker p) { this.start = start; this.picker = p; cwd = start; }

    /** Picker for another app: own heading and an optional MIME filter (null or empty = anything). */
    public FilesScreen(File start, Picker p, String title, String[] accept) { this(start, p); this.pickTitle = title; this.accept = accept; }

    /** Picker mode only: does this file match what the calling app asked for? Tells the person when it doesn't. */
    private boolean pickOk(File f) {
        if (MimeMatch.ok(accept, FileOps.mime(f))) return true;
        UI.toast("That's not the kind of file the other app asked for");
        return false;
    }

    // ------------------------------------------------------------------ build
    @Override View build() {
        sortBy = Theme.prefs().getInt("sortBy", 0); desc = Theme.prefs().getBoolean("sortDesc", false); hidden = Theme.prefs().getBoolean("hidden", false);
        byPrio = Theme.prefs().getBoolean("prio", true);
        page = new UI.Page(picker != null ? pickTitle : "Files", new UI.Act() { @Override public void run() { if (!onBack()) a.pop(); } });
        int g = UI.grow();
        LinearLayout c = UI.vbox(); c.setPadding(g, g, g, 0);

        crumbScroll = new HorizontalScrollView(a); crumbScroll.setHorizontalScrollBarEnabled(false); crumbScroll.setClipChildren(false); crumbScroll.setClipToPadding(false);
        crumbScroll.setPadding(UI.dp(2), UI.dp(4), UI.dp(2), UI.dp(4));
        crumbs = UI.hbox(); crumbScroll.addView(crumbs, new FrameLayout.LayoutParams(-2, -2));
        c.addView(crumbScroll, new LinearLayout.LayoutParams(-1, -2));

        list = new ListView(a);
        list.setDivider(null); list.setDividerHeight(0);
        list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setVerticalScrollBarEnabled(false); list.setClipToPadding(false); list.setClipChildren(false); list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setItemsCanFocus(true);
        list.setPadding(g, UI.dp(4), g, UI.dp(30));
        list.setVerticalFadingEdgeEnabled(true); list.setFadingEdgeLength(UI.dp(26));
        header = UI.vbox();
        list.addHeaderView(header, null, false);
        View foot = new View(a); foot.setLayoutParams(new AbsListView.LayoutParams(1, UI.dp(96)));
        list.addFooterView(foot, null, false);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return (items.size() + nCols - 1) / nCols; }
            @Override public Object getItem(int i) { return i; }
            @Override public long getItemId(int i) { return i; }
            @Override public int getViewTypeCount() { return 2; }
            @Override public int getItemViewType(int i) { return curGrid ? 1 : 0; }
            @Override public View getView(int i, View v, ViewGroup p) { return curGrid ? gridRow(i, v) : listRow(i, v); }
        };
        list.setAdapter(adapter);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, 0, 1f); ll.leftMargin = ll.rightMargin = -g;
        FrameLayout frame = new FrameLayout(a); frame.setClipChildren(true);   // scrolled folders/files must never paint over the breadcrumbs or app bar
        frame.addView(list, new FrameLayout.LayoutParams(-1, -1));

        fab = UI.fab(Icons.PASTE, "Paste", new UI.Act() { @Override public void run() { paste(); } });
        fab.setVisibility(View.GONE);
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END); fl.setMargins(0, 0, g + UI.dp(6), g + UI.dp(10));
        frame.addView(fab, fl);
        c.addView(frame, ll);
        listPx = UI.dp(48);
        page.content(c);
        refresh();
        return page.root;
    }

    static final class Square extends FrameLayout {
        Square(android.content.Context c) { super(c); }
        @Override protected void onMeasure(int w, int h) { int s = MeasureSpec.getSize(w); super.onMeasure(MeasureSpec.makeMeasureSpec(s, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(s, MeasureSpec.EXACTLY)); }
    }

    private boolean gridPref() { return Theme.prefs().getBoolean("grid", true); }

    private int cols() {
        float usable = Theme.wDp() - 2f * Theme.padX() / (Theme.density * Theme.scale);
        return Math.max(2, Math.min(8, (int) (usable / 158f)));
    }

    private void applyMode() {
        curGrid = gridPref() && cwd != null && !searching;
        nCols = curGrid ? cols() : 1;
        gridPx = (int) ((Theme.wPx - 2f * Theme.padX()) / nCols) - UI.dp(16);
    }

    private void changed() { applyMode(); adapter.notifyDataSetChanged(); }

    // ------------------------------------------------------------------ file rows (list and grid)
    private void preview(ImageView img, Entry e, int px) {
        if (e.f != null && e.type != FileOps.T_FOLDER && Thumbs.has(e.f)) Thumbs.load(img, e.f, px, null);
        else { img.setTag(R.id.k_thumb, null); img.setImageDrawable(null); img.setVisibility(View.GONE); }
    }

    private void wire(final View v) {
        UI.focusable(v, 1.03f);
        v.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) { Object o = x.getTag(R.id.k_file); if (o instanceof Entry) { UI.pop(x); click((Entry) o); } } });
        v.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View x) { Object o = x.getTag(R.id.k_file); if (o instanceof Entry) actions((Entry) o); return true; } });
    }

    private final class RowV extends LinearLayout {
        final FrameLayout tile; final Icons.IconView iv, tr; final ImageView img; final FolderGlyph fg; final TextView n, m;
        RowV() {
            super(a);
            setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL); setClipChildren(false);
            setPadding(UI.dp(12), UI.dp(9), UI.dp(14), UI.dp(9));
            tile = new FrameLayout(a);
            iv = new Icons.IconView(a, Icons.FILE, 0); int p = UI.dp(10); iv.setPadding(p, p, p, p);
            tile.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            fg = new FolderGlyph(a); fg.setPadding(0, 0, 0, 0); tile.addView(fg, new FrameLayout.LayoutParams(-1, -1));
            img = new ImageView(a); img.setScaleType(ImageView.ScaleType.CENTER_CROP); UI.roundClip(img, 15); tile.addView(img, new FrameLayout.LayoutParams(-1, -1));
            addView(tile, new LinearLayout.LayoutParams(UI.dp(48), UI.dp(48)));
            LinearLayout t = UI.vbox(); t.setPadding(UI.dp(14), 0, UI.dp(8), 0);
            n = UI.tv("", 16, true, Theme.c(Theme.ON_SURF)); n.setSingleLine(true); n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            m = UI.tv("", 12.5f, false, Theme.c(Theme.ON_SV)); m.setSingleLine(true); m.setPadding(0, UI.dp(3), 0, 0);
            t.addView(n); t.addView(m); addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            tr = new Icons.IconView(a, Icons.CHEVRON, 0); addView(tr, new LinearLayout.LayoutParams(UI.dp(24), UI.dp(24)));
            setBackground(UI.sel(Theme.c(Theme.CONT), 22));
            wire(this);
        }
        void bind(Entry e) {
            setTag(R.id.k_file, e);
            n.setText(e.title); m.setText(e.sub); m.setVisibility(e.sub == null || e.sub.length() == 0 ? View.GONE : View.VISIBLE);
            boolean fold = e.type == FileOps.T_FOLDER;
            tile.setBackground(UI.rr(Theme.toneBg(e.color, 0.2f), 15));
            iv.setVisibility(fold ? View.GONE : View.VISIBLE); iv.set(e.icon, e.color);
            fg.setVisibility(fold ? View.VISIBLE : View.GONE); if (fold) { fg.set(e.color, -1); fg.setOpenNow(0); }
            preview(img, e, listPx);
            boolean on = sel.contains(e.f);
            setActivated(on);
            if (on) { tr.set(Icons.CHECKED, Theme.c(Theme.P)); tr.setVisibility(View.VISIBLE); }
            else if (fold) { tr.set(Icons.CHEVRON, Theme.c(Theme.OUT)); tr.setVisibility(View.VISIBLE); }
            else tr.setVisibility(View.GONE);
        }
    }

    private final class CellV extends LinearLayout {
        final Square box; final Icons.IconView iv, badge, tick; final ImageView img; final TextView n, m; final FolderGlyph fg;
        CellV() {
            super(a);
            setOrientation(VERTICAL); setClipChildren(false);
            setPadding(UI.dp(8), UI.dp(8), UI.dp(8), UI.dp(8));
            setBackground(UI.sel(Theme.c(Theme.CONT), 24));
            box = new Square(a);
            iv = new Icons.IconView(a, Icons.FILE, 0); int p = UI.dp(28); iv.setPadding(p, p, p, p);
            box.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            fg = new FolderGlyph(a); box.addView(fg, new FrameLayout.LayoutParams(-1, -1));
            img = new ImageView(a); img.setScaleType(ImageView.ScaleType.CENTER_CROP); box.addView(img, new FrameLayout.LayoutParams(-1, -1));
            badge = new Icons.IconView(a, Icons.PLAY, 0xFFFFFFFF); badge.setBackground(UI.rr(0x99000000, 14));
            int bp = UI.dp(6); badge.setPadding(bp, bp, bp, bp);
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(UI.dp(28), UI.dp(28), Gravity.BOTTOM | Gravity.START); bl.setMargins(UI.dp(8), 0, 0, UI.dp(8)); box.addView(badge, bl);
            tick = new Icons.IconView(a, Icons.CHECKED, Theme.c(Theme.P)); tick.setBackground(UI.rr(Theme.c(Theme.ON_P), 14));
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(UI.dp(28), UI.dp(28), Gravity.TOP | Gravity.END); tl.setMargins(0, UI.dp(8), UI.dp(8), 0); box.addView(tick, tl);
            UI.roundClip(box, 17);
            addView(box, new LinearLayout.LayoutParams(-1, -2));
            n = UI.tv("", 13, true, Theme.c(Theme.ON_SURF)); n.setSingleLine(true); n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            n.setPadding(UI.dp(4), UI.dp(8), UI.dp(4), 0);
            m = UI.tv("", 11.5f, false, Theme.c(Theme.ON_SV)); m.setSingleLine(true); m.setPadding(UI.dp(4), UI.dp(2), UI.dp(4), UI.dp(2));
            addView(n); addView(m);
            wire(this);
        }
        void bind(Entry e) {
            setTag(R.id.k_file, e);
            n.setText(e.title); m.setText(e.type == FileOps.T_FOLDER ? "" : e.f.isFile() ? UI.size(e.f.length()) : "");
            box.setBackground(UI.rr(Theme.toneBg(e.color, 0.2f), 0));
            iv.set(e.icon, e.color);
            preview(img, e, gridPx);
            badge.setVisibility(e.type == FileOps.T_VIDEO ? View.VISIBLE : View.GONE);
            boolean on = sel.contains(e.f);
            setActivated(on); tick.setVisibility(on ? View.VISIBLE : View.GONE);
        }
    }

    private View listRow(int i, View v) {
        FrameLayout f; RowV r;
        if (v instanceof FrameLayout) { f = (FrameLayout) v; r = (RowV) f.getChildAt(0); }
        else {
            f = new FrameLayout(a); f.setClipChildren(false); f.setPadding(0, 0, 0, UI.dp(7));   // ListView ignores margins, so the gap is padding
            r = new RowV(); f.addView(r, new FrameLayout.LayoutParams(-1, -2));
        }
        r.bind(items.get(i));
        return f;
    }

    private final class GRow extends LinearLayout {
        final CellV[] cells;
        GRow(int n) {
            super(a);
            setOrientation(HORIZONTAL); setClipChildren(false); setClipToPadding(false);
            cells = new CellV[n];
            for (int k = 0; k < n; k++) {
                cells[k] = new CellV();
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                lp.setMargins(UI.dp(3), UI.dp(3), UI.dp(3), UI.dp(3));
                addView(cells[k], lp);
            }
        }
        void bind(int row) {
            for (int k = 0; k < cells.length; k++) {
                int ix = row * cells.length + k;
                if (ix < items.size()) { cells[k].setVisibility(View.VISIBLE); cells[k].bind(items.get(ix)); }
                else cells[k].setVisibility(View.INVISIBLE);
            }
        }
    }

    private View gridRow(int i, View v) {
        GRow r = v instanceof GRow && ((GRow) v).cells.length == nCols ? (GRow) v : new GRow(nCols);
        r.bind(i);
        return r;
    }

    // ------------------------------------------------------------------ data
    private Entry entry(File f) {
        Entry e = new Entry(); e.f = f; e.title = f.getName(); e.type = FileOps.type(f);
        e.icon = FileOps.icon(e.type); e.color = FileOps.color(e.type);
        DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM);
        e.sub = f.isDirectory() ? df.format(new Date(f.lastModified())) : UI.size(f.length()) + "  \u00b7  " + df.format(new Date(f.lastModified()));
        return e;
    }

    private static int emblem(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals("download") || n.equals("downloads")) return Icons.DOWNLOADS;
        if (n.equals("dcim") || n.equals("pictures") || n.equals("camera") || n.equals("screenshots")) return Icons.PICTURES;
        if (n.equals("movies") || n.equals("video") || n.equals("videos")) return Icons.MOVIES;
        if (n.equals("music") || n.equals("podcasts") || n.equals("audiobooks")) return Icons.MUSIC;
        if (n.equals("documents")) return Icons.DOCUMENTS;
        if (n.equals("wishifiles") || n.startsWith("received")) return Icons.INBOX;
        if (n.equals("android")) return Icons.GEAR;
        return -1;
    }

    private static String countText(int n) { return n < 0 ? "" : n == 0 ? "Empty" : n == 1 ? "1 item" : n + " items"; }

    private static int countOf(File d) { String[] c = d.list(); return c == null ? -1 : c.length; }

    private void refresh() {
        final int my = ++gen;
        items.clear(); folders.clear(); tiles.clear();
        crumbs();
        if (cwd == null && !searching) { items.clear(); changed(); buildRoot(); bar(); settle(); return; }
        if (searching) { bar(); return; }
        final File[] fs = FileOps.list(cwd, hidden, sortBy, desc);
        if (fs == null) { changed(); buildHeader(null, true); bar(); settle(); return; }
        final ArrayList<File> dirs = new ArrayList<File>();
        for (File f : fs) { if (f.isDirectory()) dirs.add(f); else items.add(entry(f)); }
        changed();
        header.removeAllViews();
        // folders appear after their contents are counted (off the UI thread, so a huge directory never stalls the screen)
        new Thread(new Runnable() { @Override public void run() {
            final ArrayList<Prio.Item> l = new ArrayList<Prio.Item>();
            for (File d : dirs) { Prio.Item it = new Prio.Item(); it.f = d; it.mod = d.lastModified(); it.items = l.size() < 300 ? countOf(d) : -1; l.add(it); }
            a.runOnUiThread(new Runnable() { @Override public void run() {
                if (my != gen) return;
                folders.addAll(l);
                Prio.rank(folders, byPrio);
                buildHeader(fs, false);
                settle();
            } });
        } }).start();
        bar();
    }

    /** Fade the content in the first time, then put focus on the first thing you can act on. */
    private void settle() {
        if (animNext) {
            animNext = false;
            list.setAlpha(0f); list.setTranslationY(UI.dp(12));
            list.animate().alpha(1f).translationY(0).setDuration(260).setInterpolator(UI.EASE).start();
        }
        list.post(new Runnable() { @Override public void run() { focusFirst(); } });
    }

    private void focusFirst() {
        if (header.getChildCount() > 0) {
            ArrayList<View> f = new ArrayList<View>();
            header.addFocusables(f, View.FOCUS_DOWN, View.FOCUSABLES_ALL);
            for (View v : f) if (v.isFocusable() && v.getVisibility() == View.VISIBLE) { v.requestFocus(); return; }
        }
        if (list.getChildCount() > 0) for (int i = 0; i < list.getChildCount(); i++) {
            ArrayList<View> f = new ArrayList<View>(); list.getChildAt(i).addFocusables(f, View.FOCUS_DOWN, View.FOCUSABLES_ALL);
            for (View v : f) if (v.isFocusable() && v.getVisibility() == View.VISIBLE) { v.requestFocus(); return; }
        }
    }

    // ------------------------------------------------------------------ header: folder tiles, labels, empty states
    private TextView label(String s) { TextView t = UI.section(s); t.setPadding(UI.dp(8), UI.dp(14), 0, UI.dp(8)); return t; }

    private void buildHeader(File[] fs, boolean denied) {
        header.removeAllViews(); tiles.clear();
        if (denied) {
            Tiles.Tile w = Tiles.make(Prio.L, Tiles.WARN, "Can't open", Storage.hasAccess() ? "Protected by Android" : "Allow file access first").icon(Icons.WARN);
            if (!Storage.hasAccess()) w.onTap(new UI.Act() { @Override public void run() { Storage.request(a); } });
            TileGrid g = Tiles.grid(Tiles.contentW()); g.add(w, Tiles.cs(Prio.L), 2); header.addView(g);
            UI.popAll(g); return;
        }
        if (folders.isEmpty() && items.isEmpty()) { header.addView(emptyState()); return; }
        if (!folders.isEmpty()) {
            if (!items.isEmpty()) header.addView(label("Folders  \u00b7  " + folders.size()));
            TileGrid g = Tiles.grid(Tiles.contentW());
            for (Prio.Item it : folders) {
                Tiles.Tile t = folderTile(it, it.f.getName(), countText(it.items), emblem(it.f.getName()));
                g.add(t, Tiles.cs(it.size), Tiles.rs(it.size));
            }
            header.addView(g, new LinearLayout.LayoutParams(-1, -2));
            UI.popAll(g);
        }
        if (!items.isEmpty()) header.addView(label(folders.isEmpty() ? "Files  \u00b7  " + items.size() : "Files  \u00b7  " + items.size()));
    }

    private Tiles.Tile folderTile(final Prio.Item it, String title, String sub, int emb) {
        int kind = it.empty() ? Tiles.QUIET : it.size == Prio.XL || it.size == Prio.L ? Tiles.HERO : it.size == Prio.M ? Tiles.SECOND : Tiles.NEUTRAL;
        final Tiles.Tile t = Tiles.make(it.size, kind, title, sub).folder(it.items);
        if (emb >= 0) t.folder.emblem(emb);
        if (it.size >= Prio.M) t.arrow();
        t.peek();
        final Entry e = entry(it.f);
        t.setTag(R.id.k_file, e);
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) {
            if (!sel.isEmpty() && !isRootTile(it)) { toggle(it.f); return; }
            if (picker != null && it.f.isFile()) return;
            if (t.folder != null) t.folder.openTo(1f);
            t.postDelayed(new Runnable() { @Override public void run() { go(it.f, t, true); } }, 190);
        } });
        if (!isRootTile(it)) t.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View x) { actions(e); return true; } });
        tiles.put(it.f, t);
        if (sel.contains(it.f)) t.selected(true);
        return t;
    }

    private boolean isRootTile(Prio.Item it) { return it.tag != null; }

    private View emptyState() {
        LinearLayout l = UI.vbox(); l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(0, UI.dp(34), 0, UI.dp(10));
        final FolderGlyph g = new FolderGlyph(a, Theme.over(Theme.c(Theme.P), Theme.c(Theme.ON_SV), 0.5f), 0);
        l.addView(g, new LinearLayout.LayoutParams(UI.dp(150), UI.dp(150)));
        TextView t = UI.tv("This folder is empty", 21, true, Theme.c(Theme.ON_SURF)); t.setGravity(Gravity.CENTER); t.setPadding(0, UI.dp(10), 0, UI.dp(4)); l.addView(t);
        TextView s = UI.tv(CLIP.isEmpty() ? "Create a folder or send files here" : "Paste what you copied, or create a folder", 14, false, Theme.c(Theme.ON_SV)); s.setGravity(Gravity.CENTER); l.addView(s);
        Flow acts = UI.flow(); acts.align = Flow.CENTER; acts.setPadding(0, UI.dp(18), 0, 0);
        acts.addView(UI.chip(Icons.NEWFOLDER, "New folder", true, new UI.Act() { @Override public void run() { newFolder(); } }));
        l.addView(acts);
        // the empty folder breathes: its flap lifts and settles, slowly
        breathe(g, true);
        return l;
    }

    private void breathe(final FolderGlyph g, final boolean up) {
        if (g == null) return;
        g.postDelayed(new Runnable() { @Override public void run() {
            if (!g.isAttachedToWindow()) return;
            g.openTo(up ? 0.22f : 0f); breathe(g, !up);
        } }, up ? 700 : 1500);
    }

    // ------------------------------------------------------------------ root: storage + shortcuts
    private void buildRoot() {
        header.removeAllViews(); tiles.clear();
        TileGrid g = Tiles.grid(Tiles.contentW());
        boolean access = Storage.hasAccess();
        if (!access) {
            Tiles.Tile w = Tiles.make(Prio.M, Tiles.WARN, "Allow file access", "Needed to browse files").icon(Icons.WARN).arrow();
            w.onTap(new UI.Act() { @Override public void run() { Storage.request(a); } });
            g.add(w, g.cols, 1);
        }
        List<Storage.Root> roots = Storage.roots();
        for (int i = 0; i < roots.size(); i++) {
            final Storage.Root r = roots.get(i);
            Prio.Item it = new Prio.Item(); it.f = r.dir; it.tag = r; it.items = 1; it.mod = System.currentTimeMillis();
            it.size = i == 0 ? Prio.XL : Prio.L;
            String sub = r.total > 0 ? UI.size(r.free) + " free of " + UI.size(r.total) : r.dir.getPath();
            Tiles.Tile t = folderTile(it, r.name, sub, r.icon == Icons.USB ? Icons.USB : Icons.STORAGE);
            if (t.folder != null && i == 0) t.folder.emblem(Icons.STORAGE);
            if (r.total > 0) t.meter(1f - (float) r.free / r.total);
            g.add(t, Tiles.cs(it.size), Tiles.rs(it.size));
        }
        // shortcuts, ranked
        ArrayList<Prio.Item> sc = new ArrayList<Prio.Item>();
        for (File s : Storage.shortcuts()) { Prio.Item it = new Prio.Item(); it.f = s; it.mod = s.lastModified(); it.items = countOf(s); sc.add(it); }
        File rv = Storage.recvDir(); if (rv.isDirectory()) { Prio.Item it = new Prio.Item(); it.f = rv; it.mod = rv.lastModified(); it.items = countOf(rv); it.tag = null; sc.add(it); }
        Prio.rank(sc, true);
        if (!sc.isEmpty()) {
            for (Prio.Item it : sc) {
                String nm = it.f.equals(rv) ? "Received" : it.f.getName();
                Tiles.Tile t = folderTile(it, nm, countText(it.items), it.f.equals(rv) ? Icons.INBOX : emblem(it.f.getName()));
                g.add(t, Tiles.cs(it.size), Tiles.rs(it.size));
            }
        }
        header.addView(g, new LinearLayout.LayoutParams(-1, -2));
        UI.popAll(g);
    }

    // ------------------------------------------------------------------ breadcrumbs
    private void crumbs() {
        crumbs.removeAllViews();
        if (cwd == null || searching) { crumbScroll.setVisibility(View.GONE); return; }
        crumbScroll.setVisibility(View.VISIBLE);
        ArrayList<File> chain = new ArrayList<File>();
        File root = null;
        for (Storage.Root r : Storage.roots()) if (cwd.getPath().startsWith(r.dir.getPath()) && (root == null || r.dir.getPath().length() > root.getPath().length())) root = r.dir;
        for (File f = cwd; f != null; f = f.getParentFile()) { chain.add(0, f); if (f.equals(root)) break; }
        crumb(null, "Files", Icons.HOME, false);
        for (int i = 0; i < chain.size(); i++) {
            File f = chain.get(i);
            Icons.IconView sep = new Icons.IconView(a, Icons.CHEVRON, Theme.c(Theme.OUT));
            crumbs.addView(sep, new LinearLayout.LayoutParams(UI.dp(18), UI.dp(18)));
            String nm = f.equals(root) ? rootName(f) : f.getName();
            crumb(f, nm, -1, i == chain.size() - 1);
        }
        crumbScroll.post(new Runnable() { @Override public void run() { crumbScroll.fullScroll(View.FOCUS_RIGHT); } });
    }

    private String rootName(File f) { for (Storage.Root r : Storage.roots()) if (r.dir.equals(f)) return r.name.split("  \u00b7  ")[0]; return f.getName(); }

    private void crumb(final File target, String label, int icon, boolean current) {
        LinearLayout c = UI.hbox();
        c.setPadding(UI.dp(icon >= 0 ? 10 : 14), UI.dp(7), UI.dp(14), UI.dp(7));
        c.setBackground(UI.sel(current ? Theme.c(Theme.SC) : 0x00000000, 20));
        if (icon >= 0) { Icons.IconView iv = new Icons.IconView(a, icon, Theme.c(Theme.ON_SV)); c.addView(iv, new LinearLayout.LayoutParams(UI.dp(20), UI.dp(20))); c.addView(UI.space(6, 1)); }
        TextView t = UI.tv(label, 13.5f, current, current ? Theme.c(Theme.ON_SC) : Theme.c(Theme.ON_SV)); t.setSingleLine(true); c.addView(t);
        UI.focusable(c, 1.05f);
        if (!current) UI.click(c, new UI.Act() { @Override public void run() {
            sel.clear();
            boolean deeper = target != null && cwd != null && !target.equals(cwd) && target.getPath().startsWith(cwd.getPath());
            go(target, null, deeper);
        } });
        crumbs.addView(c, new LinearLayout.LayoutParams(-2, UI.dp(36)));
    }

    // ------------------------------------------------------------------ navigation with the zoom transition
    private void go(final File dir, View from, final boolean forward) {
        if (busy) return;
        busy = true;
        searching = false; stopSearch[0] = true;
        if (dir != null && forward) Prio.opened(dir);
        if (from != null) {
            int[] a1 = new int[2], a2 = new int[2]; from.getLocationInWindow(a1); list.getLocationInWindow(a2);
            list.setPivotX(a1[0] - a2[0] + from.getWidth() / 2f); list.setPivotY(a1[1] - a2[1] + from.getHeight() / 2f);
        } else { list.setPivotX(list.getWidth() / 2f); list.setPivotY(list.getHeight() / 2f); }
        list.animate().scaleX(forward ? 1.07f : 0.95f).scaleY(forward ? 1.07f : 0.95f).alpha(0f).setDuration(150).setInterpolator(UI.EASE_OUT).withEndAction(new Runnable() {
            @Override public void run() {
                cwd = dir; animNext = false;
                refresh();
                list.setScaleX(forward ? 0.93f : 1.06f); list.setScaleY(forward ? 0.93f : 1.06f); list.setAlpha(0f);
                list.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(30).setDuration(320).setInterpolator(UI.EASE).withEndAction(new Runnable() { @Override public void run() { busy = false; } }).start();
                list.setSelection(0);
            }
        }).start();
        String name = dir == null ? (picker != null ? pickTitle : "Files") : dir.getName().length() == 0 ? "Files" : dir.getName();
        if (dir != null) for (Storage.Root r : Storage.roots()) if (r.dir.equals(dir)) name = r.name.split("  \u00b7  ")[0];
        page.setTitle(name);
    }

    // ------------------------------------------------------------------ app bar
    private void bar() {
        page.actions.removeAllViews();
        if (!sel.isEmpty()) {
            page.setTitle(sel.size() + " selected");
            page.actions.addView(UI.barBtn(Icons.COPY, new UI.Act() { @Override public void run() { clip(false); } }));
            page.actions.addView(UI.barBtn(Icons.CUT, new UI.Act() { @Override public void run() { clip(true); } }));
            page.actions.addView(UI.barBtn(Icons.TRASH, new UI.Act() { @Override public void run() { delete(new ArrayList<File>(sel)); } }));
            page.actions.addView(UI.barBtn(Icons.CLOSE, new UI.Act() { @Override public void run() { sel.clear(); reselect(); bar(); } }));
            showFab(false);
            return;
        }
        if (searching) page.setTitle("Search");
        else if (cwd == null) page.setTitle(picker != null ? pickTitle : "Files");
        else { String n = cwd.getName(); for (Storage.Root r : Storage.roots()) if (r.dir.equals(cwd)) n = r.name.split("  \u00b7  ")[0]; page.setTitle(n.length() == 0 ? "Files" : n); }
        page.actions.addView(UI.barBtn(Icons.SEARCH, new UI.Act() { @Override public void run() { search(); } }));
        if (cwd != null || searching) {
            final boolean narrow = Tiles.narrow();   // on phones the bar keeps three buttons; the rest live under "more" so the title never gets squeezed
            if (!narrow || searching) page.actions.addView(UI.barBtn(Icons.SORT, new UI.Act() { @Override public void run() { sortMenu(); } }));
            if (!searching) {
                page.actions.addView(UI.barBtn(gridPref() ? Icons.LIST : Icons.GRID, new UI.Act() { @Override public void run() {
                    Theme.prefs().edit().putBoolean("grid", !gridPref()).apply(); changed(); bar(); } }));
                if (!narrow) page.actions.addView(UI.barBtn(Icons.NEWFOLDER, new UI.Act() { @Override public void run() { newFolder(); } }));
                else page.actions.addView(UI.barBtn(Icons.MORE, new UI.Act() { @Override public void run() {
                    Dlg.menu("More", new String[]{"Sort and view", "New folder"}, new int[]{Icons.SORT, Icons.NEWFOLDER}, new Dlg.Pick() { @Override public void picked(int i) { if (i == 0) sortMenu(); else newFolder(); } }); } }));
            }
        }
        showFab(!CLIP.isEmpty() && cwd != null && !searching);
    }

    private void showFab(boolean show) {
        if (fab == null) return;
        if (show) {
            UI.setChipLabel(fab, "Paste (" + CLIP.size() + ")");
            if (fab.getVisibility() != View.VISIBLE) {
                fab.setVisibility(View.VISIBLE); fab.setAlpha(0f); fab.setTranslationY(UI.dp(40)); fab.setScaleX(0.8f); fab.setScaleY(0.8f);
                fab.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new android.view.animation.OvershootInterpolator(1.4f)).start();
            }
        } else if (fab.getVisibility() == View.VISIBLE) {
            fab.animate().alpha(0f).translationY(UI.dp(30)).setDuration(160).withEndAction(new Runnable() { @Override public void run() { fab.setVisibility(View.GONE); } }).start();
        }
    }

    // ------------------------------------------------------------------ actions
    private void click(Entry e) {
        if (!sel.isEmpty()) { toggle(e.f); return; }
        if (e.type == FileOps.T_FOLDER) { go(e.f, null, true); return; }
        if (picker != null) { if (pickOk(e.f)) { picker.pick(e.f); a.pop(); } return; }
        open(e.f);
    }

    private void toggle(File f) {
        if (!sel.remove(f)) sel.add(f);
        reselect(); bar();
    }

    /** Refresh selection marks without rebuilding anything. */
    private void reselect() {
        for (Map.Entry<File, Tiles.Tile> t : tiles.entrySet()) t.getValue().selected(sel.contains(t.getKey()));
        adapter.notifyDataSetChanged();
    }

    private void open(File f) {
        ArrayList<File> sib = new ArrayList<File>();
        for (Entry e : items) if (e.type != FileOps.T_FOLDER) sib.add(e.f);
        Open.file(a, f, sib);
    }

    @Override boolean onBack() {
        if (!sel.isEmpty()) { sel.clear(); reselect(); bar(); return true; }
        if (searching) { searching = false; stopSearch[0] = true; refresh(); return true; }
        if (cwd == null) return false;
        boolean isRoot = false;
        for (Storage.Root r : Storage.roots()) if (r.dir.equals(cwd)) isRoot = true;
        if (start != null && cwd.equals(start)) return false;
        File p = cwd.getParentFile();
        if (p == null || isRoot) go(null, null, false); else go(p, null, false);
        return true;
    }

    @Override boolean onKey(int code, KeyEvent e) {
        View f = a.getCurrentFocus();
        while (f != null) {
            Object o = f.getTag(R.id.k_file);
            if (o instanceof Entry) { actions((Entry) o); return true; }
            f = f.getParent() instanceof View ? (View) f.getParent() : null;
        }
        return false;
    }

    private void actions(final Entry e) {
        if (e.f == null) return;
        final File f = e.f; final boolean dir = f.isDirectory();
        final ArrayList<String> L = new ArrayList<String>(); final ArrayList<Integer> I = new ArrayList<Integer>();
        L.add("Open"); I.add(dir ? Icons.FOLDER_OPEN : Icons.PLAY);
        if (e.type == FileOps.T_APK) { L.add("Inspect and install"); I.add(Icons.INSTALL); }
        L.add(sel.contains(f) ? "Deselect" : "Select"); I.add(Icons.SELECT);
        L.add("Copy"); I.add(Icons.COPY); L.add("Cut"); I.add(Icons.CUT);
        L.add("Rename"); I.add(Icons.EDIT); L.add("Delete"); I.add(Icons.TRASH);
        if (!dir) { L.add("Share on network"); I.add(Icons.SHARE); }
        L.add("Details"); I.add(Icons.INFO);
        int[] ic = new int[I.size()]; for (int k = 0; k < ic.length; k++) ic[k] = I.get(k);
        Dlg.menu(f.getName(), L.toArray(new String[0]), ic, new Dlg.Pick() { @Override public void picked(int k) {
            String s = L.get(k);
            if (s.equals("Open")) { if (dir) go(f, tiles.get(f), true); else if (picker != null) { if (pickOk(f)) { picker.pick(f); a.pop(); } } else open(f); }
            else if (s.startsWith("Inspect")) a.push(new InstallScreen(f));
            else if (s.equals("Select") || s.equals("Deselect")) toggle(f);
            else if (s.equals("Copy")) { sel.clear(); sel.add(f); clip(false); }
            else if (s.equals("Cut")) { sel.clear(); sel.add(f); clip(true); }
            else if (s.equals("Rename")) rename(f);
            else if (s.equals("Delete")) delete(new ArrayList<File>(Arrays.asList(f)));
            else if (s.startsWith("Share")) { TransferServer.share(f); UI.toast("Shared. Open Transfer to connect a phone."); }
            else details(f);
        } });
    }

    private void clip(boolean cut) { CLIP.clear(); CLIP.addAll(sel); clipCut = cut; sel.clear(); reselect(); bar(); UI.toast((cut ? "Cut " : "Copied ") + CLIP.size() + ". Open the destination and tap Paste."); }

    private void newFolder() {
        if (cwd == null) { UI.toast("Open a storage first."); return; }
        Dlg.input("New folder", "Folder name", "", "Create", new Dlg.Text() { @Override public void got(String s) {
            if (s.length() == 0 || s.contains("/")) { UI.toast("Enter a name without slashes."); return; }
            File d = new File(cwd, s);
            if (d.exists()) { UI.toast("That name already exists."); return; }
            if (!d.mkdirs()) Dlg.info("Couldn't create the folder", Storage.hasAccess() ? "This location may be read-only." : "Allow file access first."); animNext = true; refresh(); } });
    }

    private void rename(final File f) {
        Dlg.input("Rename", "New name", f.getName(), "Rename", new Dlg.Text() { @Override public void got(String s) {
            if (s.length() == 0 || s.contains("/") || s.equals(f.getName())) return;
            File to = new File(f.getParentFile(), s);
            if (to.exists()) { UI.toast("That name already exists."); return; }
            if (!f.renameTo(to)) Dlg.info("Couldn't rename", "Android refused. The location may be read-only.");
            if (searching) { searching = false; } refresh(); } });
    }

    private void delete(final ArrayList<File> fs) {
        String what = fs.size() == 1 ? "\"" + fs.get(0).getName() + "\"" : fs.size() + " items";
        Dlg.confirm("Delete " + what + "?", "This can't be undone. Folders are deleted with everything inside.", "Delete", new UI.Act() { @Override public void run() {
            sel.clear();
            runOp("Deleting\u2026", new Op() { public void run(FileOps.Progress p) throws IOException { for (File f : fs) FileOps.delete(f); } }, 0); } });
    }

    private void paste() {
        if (cwd == null) return;
        final ArrayList<File> src = new ArrayList<File>(CLIP); final boolean cut = clipCut; final File dst = cwd;
        long total = 0; for (File f : src) total += FileOps.treeSize(f);
        runOp(cut ? "Moving\u2026" : "Copying\u2026", new Op() { public void run(FileOps.Progress p) throws IOException {
            for (File f : src) { if (cut) FileOps.move(f, dst, p); else FileOps.copy(f, dst, p); } } }, total);
        if (cut) CLIP.clear();
    }

    interface Op { void run(FileOps.Progress p) throws IOException; }

    private void runOp(String title, final Op op, final long total) {
        LinearLayout box = UI.vbox();
        final TextView t = UI.tv("Starting\u2026", 14.5f, false, Theme.c(Theme.ON_SV)); t.setSingleLine(true); t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        final TextView pc = UI.tv(total > 0 ? "0%" : "", 14.5f, true, Theme.c(Theme.ON_SURF)); pc.setPadding(0, UI.dp(10), 0, UI.dp(6));
        final UI.Meter m = new UI.Meter(a); m.set(0);
        box.addView(t); box.addView(pc); box.addView(m, new LinearLayout.LayoutParams(-1, UI.dp(8)));
        final boolean[] cancel = new boolean[1];
        final Dialog[] d = new Dialog[1];
        d[0] = Dlg.make(title).view(box).btn("Cancel", false, new UI.Act() { @Override public void run() { cancel[0] = true; } }).show();
        d[0].setCancelable(false);
        new Thread(new Runnable() { @Override public void run() {
            final long[] last = {0}; String err = null;
            try {
                op.run(new FileOps.Progress() {
                    @Override public void bytes(final long done, final String cur) {
                        long now = System.currentTimeMillis(); if (now - last[0] < 120) return; last[0] = now;
                        a.runOnUiThread(new Runnable() { @Override public void run() {
                            t.setText(cur);
                            if (total > 0) { pc.setText(UI.size(done) + " of " + UI.size(total) + "   " + (done * 100 / total) + "%"); m.set((float) done / total); }
                            else pc.setText(UI.size(done));
                        } });
                    }
                    @Override public boolean cancelled() { return cancel[0]; }
                });
            } catch (IOException e) { err = e.getMessage(); }
            final String er = err;
            a.runOnUiThread(new Runnable() { @Override public void run() {
                try { d[0].dismiss(); } catch (Exception ignored) { }
                if (er != null && !er.equals("Cancelled")) Dlg.info("That didn't finish", er);
                animNext = true; refresh();
            } });
        } }).start();
    }

    private void sortMenu() {
        String[] n = {"Name", "Size", "Date modified", "Type"};
        String[] l = new String[6]; int[] ic = new int[6];
        for (int i = 0; i < 4; i++) { l[i] = n[i] + (i == sortBy ? (desc ? "   \u2193" : "   \u2191") : ""); ic[i] = Icons.SORT; }
        l[4] = hidden ? "Hide hidden files" : "Show hidden files"; ic[4] = hidden ? Icons.EYE_OFF : Icons.EYE;
        l[5] = byPrio ? "Folders: use sort order" : "Folders: biggest first"; ic[5] = Icons.STAR;
        Dlg.menu("Sort", l, ic, new Dlg.Pick() { @Override public void picked(int i) {
            if (i == 4) { hidden = !hidden; Theme.prefs().edit().putBoolean("hidden", hidden).apply(); refresh(); return; }
            if (i == 5) { byPrio = !byPrio; Theme.prefs().edit().putBoolean("prio", byPrio).apply(); refresh(); return; }
            if (i == sortBy) desc = !desc; else { sortBy = i; desc = i == 2 || i == 1; }
            Theme.prefs().edit().putInt("sortBy", sortBy).putBoolean("sortDesc", desc).apply(); refresh(); } });
    }

    private void search() {
        final File base = cwd == null ? android.os.Environment.getExternalStorageDirectory() : cwd;
        Dlg.input("Search in " + base.getName(), "Part of a file name", "", "Search", new Dlg.Text() { @Override public void got(final String q) {
            if (q.length() == 0) return;
            searching = true; stopSearch[0] = true; stopSearch = new boolean[1]; final boolean[] stop = stopSearch;
            items.clear(); folders.clear(); tiles.clear(); changed(); crumbs();
            header.removeAllViews();
            LinearLayout w = UI.hbox(); w.setPadding(UI.dp(8), UI.dp(20), 0, UI.dp(12));
            w.addView(UI.spinner(26)); TextView st = UI.tv("Searching\u2026", 15, false, Theme.c(Theme.ON_SV)); st.setPadding(UI.dp(12), 0, 0, 0); w.addView(st);
            header.addView(w);
            bar();
            new Thread(new Runnable() { @Override public void run() {
                final ArrayList<File> res = new ArrayList<File>();
                FileOps.search(base, q, hidden, 0, res, 500, stop);
                a.runOnUiThread(new Runnable() { @Override public void run() {
                    if (stop[0]) return;
                    items.clear();
                    for (File f : res) { Entry e = entry(f); e.sub = f.getParent(); items.add(e); }
                    header.removeAllViews();
                    if (res.isEmpty()) {
                        LinearLayout l = UI.vbox(); l.setGravity(Gravity.CENTER_HORIZONTAL); l.setPadding(0, UI.dp(40), 0, 0);
                        View ic = UI.iconTile(Icons.SEARCH, 84, Theme.c(Theme.PC), Theme.c(Theme.ON_PC)); l.addView(ic); UI.popIn(ic);
                        TextView t = UI.tv("Nothing found", 21, true, Theme.c(Theme.ON_SURF)); t.setPadding(0, UI.dp(16), 0, UI.dp(4)); l.addView(t);
                        TextView s = UI.tv("No names match \"" + q + "\"", 14, false, Theme.c(Theme.ON_SV)); l.addView(s);
                        header.addView(l);
                    } else header.addView(label(res.size() + " results for \"" + q + "\""));
                    changed(); bar();
                    list.post(new Runnable() { @Override public void run() { focusFirst(); } });
                } });
            } }).start();
        } });
    }

    private void details(final File f) {
        final TextView t = UI.tv("", 15, false, Theme.c(Theme.ON_SV)); t.setLineSpacing(0, 1.2f);
        final String head = "Path: " + f.getPath() + "\nType: " + (f.isDirectory() ? "Folder" : FileOps.mime(f)) + "\nModified: " + new Date(f.lastModified()) + "\nReadable: " + f.canRead() + "   Writable: " + f.canWrite();
        t.setText(head + "\nSize: calculating\u2026");
        Dlg.make(f.getName()).view(t).btn("OK", true, null).show();
        new Thread(new Runnable() { @Override public void run() {
            final long s = FileOps.treeSize(f);
            a.runOnUiThread(new Runnable() { @Override public void run() { t.setText(head + "\nSize: " + UI.size(s) + " (" + s + " bytes)"); } });
        } }).start();
    }

    @Override void onShow() { if (list != null && picker == null) { animNext = false; refresh(); } }
}
