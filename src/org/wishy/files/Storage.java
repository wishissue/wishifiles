package org.wishy.files;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Storage roots (internal, USB) and the Android storage-permission dance. */
public final class Storage {
    public static final class Root {
        public String name; public File dir; public int icon; public long total, free;
    }

    public static boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try { return (Boolean) Environment.class.getMethod("isExternalStorageManager").invoke(null); } catch (Throwable t) { return false; }
        }
        return App.ctx.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED;
    }

    /** Ask for access: runtime permission on Android 6-10, "All files access" settings page on 11+. */
    public static void request(final MainActivity a) {
        if (Build.VERSION.SDK_INT >= 30) {
            Uri u = Uri.parse("package:" + a.getPackageName());
            Intent i = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION", u);
            if (i.resolveActivity(a.getPackageManager()) == null) i = new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION");
            if (i.resolveActivity(a.getPackageManager()) != null) { a.startActivity(i); return; }
            Dlg.info("Allow file access", "This TV has no \"All files access\" screen. You can grant it from a computer once:\n\nadb shell appops set " + a.getPackageName() + " MANAGE_EXTERNAL_STORAGE allow\n\nThen reopen Wishi Files.");
        } else {
            a.requestPermissions(new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"}, 1);
        }
    }

    public static List<Root> roots() {
        ArrayList<Root> l = new ArrayList<Root>();
        File in = Environment.getExternalStorageDirectory();
        Root r = new Root(); r.name = "Internal storage"; r.dir = in; r.icon = Icons.STORAGE; fill(r); l.add(r);
        File[] vols = new File("/storage").listFiles();
        int n = 0;
        if (vols != null) for (File v : vols) {
            String nm = v.getName();
            if (nm.equals("emulated") || nm.equals("self") || !v.isDirectory() || !v.canRead()) continue;
            Root u = new Root(); u.dir = v; u.icon = Icons.USB; u.name = "USB drive" + (n++ > 0 ? " " + n : "") + "  \u00b7  " + nm; fill(u);
            if (u.total > 0) l.add(u);
        }
        return l;
    }

    private static void fill(Root r) { try { r.total = r.dir.getTotalSpace(); r.free = r.dir.getUsableSpace(); } catch (Exception ignored) { } }

    public static File[] shortcuts() {
        String[] d = {Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_PICTURES, Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_DOCUMENTS};
        ArrayList<File> l = new ArrayList<File>();
        for (String s : d) { File f = Environment.getExternalStoragePublicDirectory(s); if (f != null && f.isDirectory()) l.add(f); }
        return l.toArray(new File[0]);
    }

    /** Where phone uploads land. Falls back to app storage when shared storage isn't writable. */
    public static File recvDir() {
        File d = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "WishiFiles");
        if (hasAccess() && (d.isDirectory() || d.mkdirs()) && d.canWrite()) return d;
        File app = App.ctx.getExternalFilesDir("Received");
        if (app != null) { app.mkdirs(); return app; }
        return new File(App.ctx.getFilesDir(), "Received");
    }
}
