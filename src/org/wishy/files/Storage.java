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

    /** What Android lets an app ask for with a normal popup: storage on 6-12, photo/video/music access on 13+. */
    static String[] runtimePerms() {
        if (Build.VERSION.SDK_INT >= 33) return new String[]{"android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO"};
        if (Build.VERSION.SDK_INT >= 30) return new String[]{"android.permission.READ_EXTERNAL_STORAGE"};
        return new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"};
    }

    static boolean hasRuntime() {
        for (String p : runtimePerms()) if (App.ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) return true;
        return false;
    }

    /** Start a settings screen without asking resolveActivity first: since Android 11 it can say "nobody handles this" for screens that do exist. */
    private static boolean launch(MainActivity a, Intent i) {
        try { a.startActivity(i); return true; } catch (Throwable t) { return false; }
    }

    /**
     * Ask for access. Android 6-10: the normal popup. Android 11+: the "All files access" settings page; if the device has none
     * (most Android TVs), fall back to the normal popup for media, and then explain the one-time adb step for everything else.
     */
    public static void request(final MainActivity a) {
        if (Build.VERSION.SDK_INT >= 30) {
            Uri u = Uri.parse("package:" + a.getPackageName());
            if (launch(a, new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION", u))) return;
            if (launch(a, new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION"))) return;
            if (!hasRuntime()) { a.requestPermissions(runtimePerms(), 1); return; }
            tvHelp(a);
        } else {
            a.requestPermissions(runtimePerms(), 1);
        }
    }

    /** After the popup closed (allowed or not): if full access still isn't there, tell the person how to get it. */
    static void afterRuntime(MainActivity a) {
        if (Build.VERSION.SDK_INT >= 30 && !hasAccess()) tvHelp(a);
    }

    static void tvHelp(final MainActivity a) {
        String ip = TransferServer.localIp();
        String cmd = "adb shell appops set " + a.getPackageName() + " MANAGE_EXTERNAL_STORAGE allow";
        String msg = "This device has no \"All files access\" screen, so Android can't show a popup for it."
                + (hasRuntime() ? " Photos, videos and music are already allowed." : "")
                + "\n\nTo open every folder, turn on Network debugging in the Developer options, then run this once from a computer on the same Wi-Fi:\n\n"
                + (ip != null ? "adb connect " + ip + ":5555\n" : "") + cmd + "\n\nThen reopen Wishi Files.";
        Dlg.make("Allow all files access").msg(msg)
                .btn("App settings", false, new UI.Act() { @Override public void run() {
                    if (!launch(a, new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:" + a.getPackageName())))) UI.toast("This device has no app settings screen");
                } })
                .btn("OK", true, null).show();
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
