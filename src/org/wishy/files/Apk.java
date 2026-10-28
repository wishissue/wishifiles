package org.wishy.files;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads an APK (or .apks/.xapk/.apkm bundle) without Android APIs: own binary-XML manifest parser + zip scan. */
public final class Apk {
    public File file;            // the (base) APK on disk
    public File source;          // what the user picked (bundle or apk)
    public long size;
    public String pkg = "", versionName = "", labelRes;
    public long versionCode;
    public int minSdk = 1, targetSdk = 0, maxSdk = 0, permissions;
    public String minSdkName;
    public String splitName;
    public boolean featureSplit, testOnly, debuggable, parseFailed, bundle, hasObb;
    public String parseError;
    public final TreeSet<String> abis = new TreeSet<String>();      // native ABIs shipped in the base
    public final List<String> bundleEntries = new ArrayList<String>(); // all .apk entries in a bundle
    public final TreeSet<String> bundleAbis = new TreeSet<String>();   // ABIs offered by abi splits

    public static boolean isBundleName(String n) {
        n = n.toLowerCase(Locale.ROOT);
        return n.endsWith(".apks") || n.endsWith(".xapk") || n.endsWith(".apkm");
    }

    public static boolean isInstallable(String n) { n = n.toLowerCase(Locale.ROOT); return n.endsWith(".apk") || isBundleName(n); }

    public static Apk inspect(File f, File cacheDir) {
        Apk a = new Apk();
        a.source = f; a.size = f.length(); a.file = f;
        try {
            if (isBundleName(f.getName())) a.openBundle(f, cacheDir);
            a.parseApk(a.file);
        } catch (Throwable t) {
            a.parseFailed = true;
            a.parseError = t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
        }
        return a;
    }

    private void openBundle(File f, File cache) throws IOException {
        bundle = true;
        ZipFile z = new ZipFile(f);
        try {
            ZipEntry base = null;
            for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                ZipEntry en = e.nextElement();
                String n = en.getName();
                if (n.startsWith("Android/obb/") || n.endsWith(".obb")) hasObb = true;
                if (!n.toLowerCase(Locale.ROOT).endsWith(".apk") || en.isDirectory()) continue;
                bundleEntries.add(n);
                String low = n.toLowerCase(Locale.ROOT);
                String abi = abiOfSplit(low);
                if (abi != null) bundleAbis.add(abi);
                boolean isSplit = low.contains("config.") || low.contains("split_") || low.contains("split-");
                if (base == null || (!isSplit && (low.endsWith("base.apk") || isSplitName(base.getName()))) ) base = en;
            }
            if (base == null) throw new IOException("no .apk inside this bundle");
            cache.mkdirs();
            File out = new File(cache, "base-" + Integer.toHexString(f.getAbsolutePath().hashCode()) + ".apk");
            InputStream in = z.getInputStream(base);
            OutputStream os = new FileOutputStream(out);
            try { byte[] b = new byte[65536]; int r; while ((r = in.read(b)) > 0) os.write(b, 0, r); } finally { os.close(); in.close(); }
            file = out;
        } finally { z.close(); }
    }

    private static boolean isSplitName(String n) { n = n.toLowerCase(Locale.ROOT); return n.contains("config.") || n.contains("split_") || n.contains("split-"); }

    /** "config.arm64_v8a.apk" -> "arm64-v8a" (null if not an ABI split). */
    public static String abiOfSplit(String low) {
        String[][] m = {{"arm64_v8a", "arm64-v8a"}, {"arm64-v8a", "arm64-v8a"}, {"armeabi_v7a", "armeabi-v7a"}, {"armeabi-v7a", "armeabi-v7a"},
                {"x86_64", "x86_64"}, {"x86.apk", "x86"}, {"config.x86", "x86"}, {"armeabi.apk", "armeabi"}};
        for (String[] p : m) if (low.contains(p[0])) return p[1];
        return null;
    }

    private void parseApk(File f) throws IOException {
        ZipFile z = new ZipFile(f);
        try {
            ZipEntry me = z.getEntry("AndroidManifest.xml");
            if (me == null) throw new IOException("no AndroidManifest.xml (not an APK)");
            InputStream in = z.getInputStream(me);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[16384]; int r;
            while ((r = in.read(b)) > 0) bo.write(b, 0, r);
            in.close();
            manifest(bo.toByteArray());
            for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                String n = e.nextElement().getName();
                if (n.startsWith("lib/") && n.endsWith(".so")) {
                    int s = n.indexOf('/', 4);
                    if (s > 0) abis.add(n.substring(4, s));
                }
            }
        } finally { z.close(); }
    }

    // ---- binary XML (AXML) ----
    private static String[] pool(ByteBuffer bb, int pos) {
        int count = bb.getInt(pos + 8), flags = bb.getInt(pos + 16), strStart = bb.getInt(pos + 20);
        boolean utf8 = (flags & 0x100) != 0;
        String[] out = new String[count];
        for (int i = 0; i < count; i++) {
            int p = pos + strStart + bb.getInt(pos + 28 + 4 * i);
            try {
                if (utf8) {
                    int l = bb.get(p) & 0xFF; p++; if ((l & 0x80) != 0) { p++; }
                    int bl = bb.get(p) & 0xFF; p++; if ((bl & 0x80) != 0) { bl = ((bl & 0x7F) << 8) | (bb.get(p) & 0xFF); p++; }
                    byte[] sb = new byte[bl]; for (int k = 0; k < bl; k++) sb[k] = bb.get(p + k);
                    out[i] = new String(sb, "UTF-8");
                } else {
                    int l = bb.getShort(p) & 0xFFFF; p += 2; if ((l & 0x8000) != 0) { l = ((l & 0x7FFF) << 16) | (bb.getShort(p) & 0xFFFF); p += 2; }
                    char[] cs = new char[l]; for (int k = 0; k < l; k++) cs[k] = (char) (bb.getShort(p + 2 * k) & 0xFFFF);
                    out[i] = new String(cs);
                }
            } catch (Exception ex) { out[i] = ""; }
        }
        return out;
    }

    private static String attrName(String[] s, int[] ids, int nameIdx) {
        int id = nameIdx < ids.length ? ids[nameIdx] : 0;
        switch (id) {
            case 0x01010001: return "label"; case 0x0101000f: return "debuggable"; case 0x0101021b: return "versionCode";
            case 0x0101021c: return "versionName"; case 0x0101020c: return "minSdkVersion"; case 0x01010270: return "targetSdkVersion";
            case 0x01010271: return "maxSdkVersion"; case 0x01010272: return "testOnly"; case 0x01010576: return "versionCodeMajor";
            case 0x0101054f: return "isFeatureSplit";
            default: return nameIdx >= 0 && nameIdx < s.length ? s[nameIdx] : "";
        }
    }

    private void manifest(byte[] data) throws IOException {
        if (data.length < 16) throw new IOException("manifest too short");
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if ((bb.getShort(0) & 0xFFFF) != 0x0003) throw new IOException("manifest is not binary XML");
        String[] strs = new String[0]; int[] ids = new int[0];
        long vcMajor = 0;
        int pos = 8;
        while (pos + 8 <= data.length) {
            int type = bb.getShort(pos) & 0xFFFF, hdr = bb.getShort(pos + 2) & 0xFFFF, sz = bb.getInt(pos + 4);
            if (sz <= 0 || pos + sz > data.length) break;
            if (type == 0x0001) strs = pool(bb, pos);
            else if (type == 0x0180) { ids = new int[(sz - hdr) / 4]; for (int i = 0; i < ids.length; i++) ids[i] = bb.getInt(pos + hdr + 4 * i); }
            else if (type == 0x0102) {
                String el = strs[bb.getInt(pos + 20)];
                int attrStart = bb.getShort(pos + 24) & 0xFFFF, attrSize = bb.getShort(pos + 26) & 0xFFFF, cnt = bb.getShort(pos + 28) & 0xFFFF;
                if (el.startsWith("uses-permission")) permissions++;
                for (int i = 0; i < cnt; i++) {
                    int ap = pos + 16 + attrStart + i * attrSize;
                    String nm = attrName(strs, ids, bb.getInt(ap + 4));
                    int raw = bb.getInt(ap + 8), dt = bb.get(ap + 15) & 0xFF, dv = bb.getInt(ap + 16);
                    String sv = dt == 0x03 && dv >= 0 && dv < strs.length ? strs[dv] : (raw >= 0 && raw < strs.length ? strs[raw] : null);
                    if (el.equals("manifest")) {
                        if (nm.equals("package")) pkg = sv == null ? "" : sv;
                        else if (nm.equals("versionCode")) versionCode = dv & 0xFFFFFFFFL;
                        else if (nm.equals("versionCodeMajor")) vcMajor = dv & 0xFFFFFFFFL;
                        else if (nm.equals("versionName")) versionName = sv == null ? "" : sv;
                        else if (nm.equals("split")) splitName = sv;
                        else if (nm.equals("isFeatureSplit")) featureSplit = dv != 0;
                    } else if (el.equals("uses-sdk")) {
                        if (nm.equals("minSdkVersion")) { if (dt == 0x03) minSdkName = sv; else minSdk = dv; }
                        else if (nm.equals("targetSdkVersion")) { if (dt == 0x03) minSdkName = minSdkName == null ? sv : minSdkName; else targetSdk = dv; }
                        else if (nm.equals("maxSdkVersion")) maxSdk = dv;
                    } else if (el.equals("application")) {
                        if (nm.equals("label")) { if (dt == 0x03) labelRes = sv; else if (dt == 0x01) labelRes = null; }
                        else if (nm.equals("debuggable")) debuggable = dv != 0;
                        else if (nm.equals("testOnly")) testOnly = dv != 0;
                    }
                    if (el.equals("application") && nm.equals("testOnly")) testOnly = dv != 0;
                    if (el.equals("manifest") && nm.equals("testOnly")) testOnly = dv != 0;
                }
            }
            pos += sz;
        }
        if (vcMajor != 0) versionCode |= vcMajor << 32;
        if (pkg.length() == 0) throw new IOException("manifest has no package name");
        if (targetSdk == 0) targetSdk = minSdk;
    }

    // ---- compatibility analysis ----
    public static final class Dev {
        public int sdk; public String release = "?"; public String[] abis = new String[0]; public long freeBytes = Long.MAX_VALUE;
        public long installedCode = -1; public String installedName; public int sig; // 0 unknown, 1 same, 2 different
    }

    public static final class Msg {
        public final int level; public final String title, body; // 0 info, 1 warning, 2 blocker
        Msg(int l, String t, String b) { level = l; title = t; body = b; }
    }

    public static String androidName(int sdk) {
        int[] k = {21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36};
        String[] v = {"5.0", "5.1", "6", "7", "7.1", "8", "8.1", "9", "10", "11", "12", "12L", "13", "14", "15", "16"};
        for (int i = 0; i < k.length; i++) if (k[i] == sdk) return v[i];
        return "API " + sdk;
    }

    public List<Msg> check(Dev d) {
        List<Msg> m = new ArrayList<Msg>();
        if (parseFailed) {
            m.add(new Msg(2, "This file can't be read as an APK", "It may be damaged, an incomplete download, or not an APK at all (" + parseError + "). Download it again, or check that it really is an .apk, .apks, .xapk or .apkm file."));
            return m;
        }
        if (splitName != null && !bundle)
            m.add(new Msg(2, "This is a split APK, not a whole app", "\"" + splitName + "\" is only one piece of an app bundle. Android can't install it alone. Get the full bundle (.apks, .xapk or .apkm), or the app's universal APK, and open that instead."));
        if (minSdk > d.sdk || minSdkName != null && minSdk <= 1 && false)
            m.add(new Msg(2, "Needs a newer Android", "This app needs Android " + androidName(minSdk) + " (API " + minSdk + ") or newer. This device runs Android " + d.release + " (API " + d.sdk + "). Look for an older version of the app that supports your system."));
        if (maxSdk > 0 && maxSdk < d.sdk)
            m.add(new Msg(2, "Too old for this Android", "This app only supports up to Android " + androidName(maxSdk) + ", and this device runs Android " + d.release + ". Look for a newer version."));
        TreeSet<String> all = new TreeSet<String>(abis);
        if (bundle) all.addAll(bundleAbis);
        if (!all.isEmpty()) {
            boolean ok = false;
            for (String x : d.abis) if (all.contains(x)) ok = true;
            if (!ok) m.add(new Msg(2, "Wrong CPU type", "This app only ships native code for " + join(all) + ", but this device runs " + join(Arrays.asList(d.abis)) + ". Look for the build made for your CPU (often called \"" + (d.abis.length > 0 ? d.abis[0] : "universal") + "\" or \"universal\")."));
            else if (bundle && bundleAbis.size() > 0) m.add(new Msg(0, "CPU split selected", "Will install the " + pickAbi(d.abis, bundleAbis) + " split."));
        }
        if (targetSdk > 0 && ((d.sdk >= 35 && targetSdk < 24) || (d.sdk == 34 && targetSdk < 23)))
            m.add(new Msg(2, "Built for a very old Android", "This app targets Android " + androidName(targetSdk) + " (API " + targetSdk + "). Android " + d.release + " refuses to install apps this old. Look for a newer version."));
        if (testOnly) m.add(new Msg(1, "Test-only build", "The developer marked this as a test build. Android only installs these with adb (-t), so the install will likely be refused."));
        if (debuggable) m.add(new Msg(0, "Debug build", "This is a debuggable build, which is fine for testing but slower than a release build."));
        long need = size * 2 + (16L << 20);
        if (d.freeBytes < need) m.add(new Msg(d.freeBytes < size ? 2 : 1, "Low storage", "Installing needs about " + TransferServer.fmtSize(need) + " of free space, and only " + TransferServer.fmtSize(d.freeBytes) + " is free. Uninstall apps you don't use, or clear space, then try again."));
        if (d.installedCode >= 0) {
            if (versionCode < d.installedCode) m.add(new Msg(2, "Older than the installed version", "Version " + versionName + " (" + versionCode + ") is older than " + d.installedName + " (" + d.installedCode + ") already on this device. Android doesn't allow downgrades unless you uninstall the app first (its data will be lost)."));
            else m.add(new Msg(0, versionCode == d.installedCode ? "Already installed" : "Update available", "Installed: " + d.installedName + " (" + d.installedCode + "). This file: " + versionName + " (" + versionCode + ")."));
            if (d.sig == 2) m.add(new Msg(2, "Signature doesn't match", "The installed copy was signed with a different key (for example, a Play Store build versus a build from another source). Android refuses to update it. Uninstall the installed app first; its data will be lost."));
        }
        if (hasObb) m.add(new Msg(1, "Contains OBB game data", "This bundle includes expansion (OBB) files. Wishi Files installs the app but doesn't copy the OBB data, so the game may need to download it."));
        if (bundle && m.isEmpty()) m.add(new Msg(0, "App bundle", bundleEntries.size() + " APK files will be installed together."));
        return m;
    }

    public static boolean hasBlocker(List<Msg> l) { for (Msg x : l) if (x.level >= 2) return true; return false; }

    static String join(Collection<String> c) { StringBuilder sb = new StringBuilder(); for (String s : c) { if (sb.length() > 0) sb.append(", "); sb.append(s); } return sb.toString(); }

    public static String pickAbi(String[] dev, Collection<String> avail) { for (String s : dev) if (avail.contains(s)) return s; return null; }

    /** Which bundle entries to install: base + non-ABI splits + the single best ABI split. */
    public List<String> selectEntries(String[] devAbis) {
        List<String> out = new ArrayList<String>();
        String best = pickAbi(devAbis, bundleAbis);
        for (String n : bundleEntries) {
            String ab = abiOfSplit(n.toLowerCase(Locale.ROOT));
            if (ab != null && !ab.equals(best)) continue;
            out.add(n);
        }
        return out;
    }

    // ---- install failure explanations ----
    public static final class Why { public String title, body; public boolean offerUninstall, offerSettings; }

    /** Turn PackageInstaller's status + message into plain language. */
    public static Why explain(int status, String msg, Apk a, Dev d) {
        String m = msg == null ? "" : msg;
        String u = m.toUpperCase(Locale.ROOT);
        Why w = new Why();
        if (status == 3) { w.title = "Install cancelled"; w.body = "You cancelled the install, so nothing changed."; return w; }
        if (u.contains("OLDER_SDK") || u.contains("REQUIRES NEWER SDK") || u.contains("NEWER SDK")) {
            w.title = "Needs a newer Android";
            w.body = a != null && !a.parseFailed ? "This app needs Android " + androidName(a.minSdk) + " (API " + a.minSdk + ") or newer, and this device runs Android " + d.release + " (API " + d.sdk + "). Look for an older version of the app." : "This app needs a newer Android than this device runs. Look for an older version.";
        } else if (u.contains("NO_MATCHING_ABIS") || u.contains("CPU_ABI") || u.contains("NATIVE_LIBRARIES")) {
            w.title = "Wrong CPU type";
            w.body = "The app's native code doesn't match this device (" + join(Arrays.asList(d.abis)) + ")" + (a != null && !a.abis.isEmpty() ? ", and the file only has " + join(a.abis) : "") + ". Download the build made for your CPU, or a universal build.";
        } else if (u.contains("UPDATE_INCOMPATIBLE") || u.contains("INCONSISTENT_CERTIFICATES") || u.contains("SIGNATURES DO NOT MATCH") || u.contains("SIGNATURE") || status == 5 && u.contains("INCOMPATIBLE")) {
            w.title = "Signature conflict"; w.offerUninstall = true;
            w.body = "The installed copy of this app was signed with a different key than this file, so Android won't update it. Uninstall the installed app first (its data will be lost), then install again.";
        } else if (u.contains("VERSION_DOWNGRADE") || u.contains("DOWNGRADE")) {
            w.title = "Older than the installed version"; w.offerUninstall = true;
            w.body = "A newer version is already installed and Android doesn't allow going backwards. Uninstall it first (its data will be lost) or install a newer file.";
        } else if (u.contains("INSUFFICIENT_STORAGE") || status == 6) {
            w.title = "Not enough storage";
            w.body = "There isn't enough free space" + (d.freeBytes != Long.MAX_VALUE ? " (" + TransferServer.fmtSize(d.freeBytes) + " free)" : "") + ". The install needs roughly twice the file size. Uninstall apps you don't use or clear space, then try again.";
        } else if (u.contains("MISSING_SPLIT") || u.contains("MISSING SPLIT") || u.contains("SPLIT")) {
            w.title = "Missing app pieces (split APKs)";
            w.body = "This app is delivered as several APK files and some are missing. Use the complete bundle (.apks, .xapk or .apkm), or the app's universal APK.";
        } else if (u.contains("TEST_ONLY")) {
            w.title = "Test-only build"; w.body = "This build is marked test-only, which Android only installs through adb. Get a release build instead.";
        } else if (u.contains("DEPRECATED_SDK") || u.contains("TARGET SDK")) {
            w.title = "Built for a very old Android"; w.body = "Android " + d.release + " blocks apps built for old Android versions. Look for a newer release of the app.";
        } else if (u.contains("CONFLICTING_PROVIDER") || u.contains("DUPLICATE_PERMISSION") || u.contains("DUPLICATE_PACKAGE")) {
            w.title = "Conflicts with another app"; w.offerUninstall = false;
            w.body = "Another installed app (maybe a different version or copy of this one) uses the same name for a provider or permission. Uninstall the other copy, then try again. Android's message: " + m;
        } else if (u.contains("NO_CERTIFICATES") || u.contains("NO CERTIFICATES") || u.contains("UNSIGNED")) {
            w.title = "The APK isn't signed"; w.body = "Android only installs signed apps, and this file has no valid signature. Download it again from its source.";
        } else if (u.contains("PARSE_FAILED") || u.contains("MANIFEST") || u.contains("INVALID_APK") || status == 4) {
            w.title = "The APK is damaged or invalid"; w.body = "Android couldn't read the file. It may be an incomplete download or a corrupted transfer. Fetch it again and retry. Android's message: " + m;
        } else if (u.contains("USER_RESTRICTED") || status == 2) {
            w.title = "Install blocked"; w.offerSettings = true;
            w.body = "Something on this device blocked the install: a parental-control or admin restriction, Play Protect, or the \"install unknown apps\" setting for Wishi Files. " + (m.length() > 0 ? "Android says: " + m : "");
        } else if (u.contains("MISSING_SHARED_LIBRARY") || u.contains("USES-LIBRARY") || u.contains("USES_LIBRARY")) {
            w.title = "Needs a system library this TV doesn't have"; w.body = "The app depends on a system component (for example Google Play Services) that isn't on this device. " + m;
        } else if (u.contains("VERIFICATION")) {
            w.title = "Verification failed"; w.body = "The package verifier rejected or couldn't check this app. Try again, or turn off Play Protect scanning for a moment. " + m;
        } else if (u.contains("ALREADY_EXISTS")) {
            w.title = "Already installed"; w.body = "This exact app is already installed.";
        } else if (status == 8) {
            w.title = "Install timed out"; w.body = "Android didn't finish in time. Try once more.";
        } else {
            w.title = "Android couldn't install it";
            w.body = (m.length() > 0 ? m : "No reason was given (status " + status + ").") + "\n\nIf this keeps happening, check Android version, CPU type and free space above.";
        }
        return w;
    }
}
