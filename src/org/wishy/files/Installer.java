package org.wishy.files;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.Environment;
import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Android glue for installing: device facts, PackageInstaller sessions, result delivery. */
public final class Installer {
    public interface Listener { void onResult(int status, String msg, String pkg); void onStage(String s); }

    static volatile Listener listener;
    static volatile int lastStatus = Integer.MIN_VALUE; static volatile String lastMsg, lastPkg;
    private static final Handler H = new Handler(Looper.getMainLooper());

    public static void deliver(final int st, final String msg, final String pkg) {
        lastStatus = st; lastMsg = msg; lastPkg = pkg;
        H.post(new Runnable() { @Override public void run() { Listener l = listener; if (l != null) l.onResult(st, msg, pkg); } });
    }

    public static boolean canInstall(Context c) {
        if (Build.VERSION.SDK_INT < 26) return true;
        try { return (Boolean) PackageManager.class.getMethod("canRequestPackageInstalls").invoke(c.getPackageManager()); } catch (Throwable t) { return true; }
    }

    public static void unknownSourcesSettings(Context c) {
        Intent i = new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + c.getPackageName()));
        if (i.resolveActivity(c.getPackageManager()) == null) i = new Intent("android.settings.SECURITY_SETTINGS");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); } catch (Exception e) { UI.toast("Open Settings > Security > Unknown sources"); }
    }

    private static boolean sameSigs(Signature[] a, Signature[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) return false;
        HashSet<String> x = new HashSet<String>(), y = new HashSet<String>();
        for (Signature s : a) x.add(s.toCharsString());
        for (Signature s : b) y.add(s.toCharsString());
        return x.equals(y);
    }

    /** Collect everything Apk.check() needs to know about this device. */
    public static Apk.Dev device(Context c, Apk a) {
        Apk.Dev d = new Apk.Dev();
        d.sdk = Build.VERSION.SDK_INT; d.release = Build.VERSION.RELEASE;
        d.abis = Build.VERSION.SDK_INT >= 21 ? Build.SUPPORTED_ABIS : new String[]{Build.CPU_ABI, Build.CPU_ABI2};
        try { StatFs fs = new StatFs(Environment.getDataDirectory().getPath()); d.freeBytes = fs.getAvailableBytes(); } catch (Throwable t) { d.freeBytes = Long.MAX_VALUE; }
        if (a != null && !a.parseFailed) {
            try {
                PackageManager pm = c.getPackageManager();
                PackageInfo cur = pm.getPackageInfo(a.pkg, PackageManager.GET_SIGNATURES);
                d.installedCode = (long) cur.versionCode;
                d.installedName = cur.versionName == null ? String.valueOf(d.installedCode) : cur.versionName;
                PackageInfo arc = pm.getPackageArchiveInfo(a.file.getAbsolutePath(), PackageManager.GET_SIGNATURES);
                d.sig = arc == null || arc.signatures == null ? 0 : (sameSigs(arc.signatures, cur.signatures) ? 1 : 2);
            } catch (PackageManager.NameNotFoundException ignored) { } catch (Throwable ignored) { }
        }
        return d;
    }

    public static void install(final Context c, final Apk a, final Apk.Dev d) {
        lastStatus = Integer.MIN_VALUE;
        new Thread(new Runnable() { @Override public void run() { doInstall(c, a, d); } }, "wishi-install").start();
    }

    private static void stage(final String s) { H.post(new Runnable() { @Override public void run() { Listener l = listener; if (l != null) l.onStage(s); } }); }

    private static void doInstall(Context c, Apk a, Apk.Dev d) {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.Session s = null; int id = -1;
        try {
            PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            p.setAppPackageName(a.pkg);
            p.setSize(a.size);
            id = pi.createSession(p);
            s = pi.openSession(id);
            stage("Copying to Android\u2019s installer\u2026");
            if (a.bundle) {
                ZipFile z = new ZipFile(a.source);
                try {
                    int k = 0;
                    for (String name : a.selectEntries(d.abis)) {
                        ZipEntry e = z.getEntry(name);
                        write(s, "split" + (k++) + ".apk", z.getInputStream(e), e.getSize());
                    }
                } finally { z.close(); }
            } else write(s, "base.apk", new FileInputStream(a.file), a.file.length());
            Intent i = new Intent(c, InstallReceiver.class).setAction("org.wishy.files.INSTALL_RESULT");
            PendingIntent pend = PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? (1 << 25) : 0));
            stage("Waiting for Android\u2019s confirmation\u2026");
            s.commit(pend.getIntentSender());
            s = null;
        } catch (Throwable t) {
            if (s != null) { try { s.abandon(); } catch (Throwable ignored) { } }
            String m = String.valueOf(t.getMessage());
            deliver(m.contains("ENOSPC") ? 6 : 1, m, a.pkg);
        }
    }

    private static void write(PackageInstaller.Session s, String name, InputStream in, long len) throws IOException {
        OutputStream os = s.openWrite(name, 0, len);
        try {
            byte[] b = new byte[131072]; int r;
            while ((r = in.read(b)) > 0) os.write(b, 0, r);
            s.fsync(os);
        } finally { in.close(); os.close(); }
    }

    public static void uninstall(Context c, String pkg) {
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); } catch (Exception e) { UI.toast("Can't open the uninstall screen on this device."); }
    }

    public static boolean launch(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
        return true;
    }
}
