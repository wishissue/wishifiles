package org.wishy.files;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** One place that decides how a file opens: installer, built-in image viewer, built-in player, another app, or "not supported". */
final class Open {
    /** [siblings] are the files shown next to it (for next/previous); may be null. */
    static void file(MainActivity a, File f, List<File> siblings) {
        int t = FileOps.type(f);
        if (t == FileOps.T_APK) { a.push(new InstallScreen(f)); return; }
        if (t == FileOps.T_IMAGE && FileOps.viewableImage(f)) {
            ArrayList<File> l = playlist(f, siblings, FileOps.T_IMAGE);
            a.push(new ImageScreen(l, l.indexOf(f))); return;
        }
        if (t == FileOps.T_VIDEO || t == FileOps.T_AUDIO) {
            ArrayList<File> l = playlist(f, siblings, t);
            a.push(new MediaScreen(l, l.indexOf(f), t == FileOps.T_AUDIO)); return;
        }
        external(a, f);
    }

    private static ArrayList<File> playlist(File f, List<File> sib, int type) {
        ArrayList<File> l = new ArrayList<File>();
        if (sib != null) for (File x : sib) if (x.isFile() && FileOps.type(x) == type && (type != FileOps.T_IMAGE || FileOps.viewableImage(x))) l.add(x);
        if (!l.contains(f)) { l.clear(); l.add(f); }
        return l;
    }

    /** Hand the file to whatever app on the device can open it; if there is none, say so plainly. */
    static void external(MainActivity a, File f) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(ShareProvider.uriFor(f), FileOps.mime(f));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { a.startActivity(i); } catch (ActivityNotFoundException x) { unsupported(f); }
    }

    static void unsupported(File f) {
        String e = FileOps.ext(f.getName());
        Dlg.info("Not supported", "Wishi Files can't open " + (e.length() > 0 ? "." + e + " files" : "this file")
                + ", and no other app on this device can either.\n\nInstall an app that supports this type of file, then try again.");
    }

    /** Full-screen "can't show this" panel used by the viewers. Calls [close] for Back. */
    static View problem(final MainActivity a, final File f, String title, String msg) {
        LinearLayout l = UI.vbox();
        l.setGravity(Gravity.CENTER);
        l.setPadding(Theme.padX(), Theme.padY(), Theme.padX(), Theme.padY());
        l.addView(UI.iconTile(Icons.INFO, 84, Theme.c(Theme.ERR), Theme.on(Theme.c(Theme.ERR))));
        TextView t = UI.tv(title, 26, true, 0xFFFFFFFF); t.setGravity(Gravity.CENTER); t.setPadding(0, UI.dp(22), 0, UI.dp(8)); l.addView(t);
        TextView m = UI.tv(msg, 16, false, 0xFFC9C9CF); m.setGravity(Gravity.CENTER); m.setLineSpacing(0, 1.2f);
        m.setMaxWidth(Math.round(Theme.wPx * 0.7f)); l.addView(m);
        Flow acts = UI.flow(); acts.align = Flow.CENTER; acts.setPadding(0, UI.dp(24), 0, 0);
        View first = UI.chip(Icons.PLAY, "Open with another app", true, new UI.Act() { @Override public void run() { external(a, f); } });
        acts.addView(first); l.setTag(first);
        acts.addView(UI.chip(Icons.BACK, "Back", false, new UI.Act() { @Override public void run() { a.pop(); } }));
        l.addView(acts);
        return l;
    }
}
