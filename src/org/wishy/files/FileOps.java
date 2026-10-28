package org.wishy.files;

import java.io.*;
import java.util.*;

/** File-system logic with no Android dependency: classification, sorting, copy/move/delete trees, search. */
public final class FileOps {
    public static final int T_FOLDER = 0, T_APK = 1, T_IMAGE = 2, T_VIDEO = 3, T_AUDIO = 4, T_DOC = 5, T_PDF = 6, T_HTML = 7, T_ZIP = 8, T_OTHER = 9;

    public interface Progress { void bytes(long done, String current); boolean cancelled(); }

    public static String ext(String n) { int i = n.lastIndexOf('.'); return i < 0 || i == n.length() - 1 ? "" : n.substring(i + 1).toLowerCase(Locale.ROOT); }

    private static boolean in(String e, String list) { return (" " + list + " ").contains(" " + e + " "); }

    public static int type(File f) {
        if (f.isDirectory()) return T_FOLDER;
        String e = ext(f.getName());
        if (in(e, "apk apks xapk apkm")) return T_APK;
        if (in(e, "jpg jpeg png gif webp bmp heic heif avif svg ico")) return T_IMAGE;
        if (in(e, "mp4 mkv avi mov webm m4v ts 3gp flv wmv mpg mpeg m2ts")) return T_VIDEO;
        if (in(e, "mp3 flac wav ogg m4a aac opus wma mid amr")) return T_AUDIO;
        if (e.equals("pdf")) return T_PDF;
        if (in(e, "html htm xhtml mhtml")) return T_HTML;
        if (in(e, "zip rar 7z tar gz tgz bz2 xz iso jar")) return T_ZIP;
        if (in(e, "txt md doc docx odt rtf xls xlsx ods csv ppt pptx odp log json xml srt ini conf yml yaml")) return T_DOC;
        return T_OTHER;
    }

    /** Pictures the built-in viewer tries to show (anything else goes to another app, or is reported as unsupported). */
    public static boolean viewableImage(File f) { return in(ext(f.getName()), "jpg jpeg png gif webp bmp heic heif avif"); }

    public static int icon(int t) {
        switch (t) {
            case T_FOLDER: return Icons.FOLDER; case T_APK: return Icons.APK; case T_IMAGE: return Icons.IMAGE; case T_VIDEO: return Icons.VIDEO;
            case T_AUDIO: return Icons.AUDIO; case T_DOC: return Icons.DOC; case T_PDF: return Icons.PDF; case T_HTML: return Icons.HTML; case T_ZIP: return Icons.ZIP;
            default: return Icons.FILE;
        }
    }

    /** Theme tone slot per type (see Theme.tone): folder, apk, image, video, audio, doc, pdf, html, zip, other. Colours come from the palette, never hard-coded. */
    public static final int[] TYPE_TONE = {0, 2, 3, 1, 5, 4, 6, 7, 8, 9};

    public static int color(int type) { return Theme.tone(TYPE_TONE[type]); }

    public static String mime(File f) {
        String e = ext(f.getName());
        if (in(e, "apk")) return "application/vnd.android.package-archive";
        if (in(e, "html htm xhtml")) return "text/html";
        String m = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(e);
        return m != null ? m : "*/*";
    }

    public static final int S_NAME = 0, S_SIZE = 1, S_DATE = 2, S_TYPE = 3;

    public static Comparator<File> comparator(final int by, final boolean desc) {
        return new Comparator<File>() {
            @Override public int compare(File a, File b) {
                boolean da = a.isDirectory(), db = b.isDirectory();
                if (da != db) return da ? -1 : 1;
                int r;
                switch (by) {
                    case S_SIZE: r = da ? a.getName().compareToIgnoreCase(b.getName()) : Long.compare(a.length(), b.length()); break;
                    case S_DATE: r = Long.compare(a.lastModified(), b.lastModified()); break;
                    case S_TYPE: r = ext(a.getName()).compareTo(ext(b.getName())); if (r == 0) r = a.getName().compareToIgnoreCase(b.getName()); break;
                    default: r = a.getName().compareToIgnoreCase(b.getName());
                }
                return desc ? -r : r;
            }
        };
    }

    public static File[] list(File dir, boolean hidden, int by, boolean desc) {
        File[] fs = dir.listFiles();
        if (fs == null) return null;
        if (!hidden) {
            ArrayList<File> keep = new ArrayList<File>();
            for (File f : fs) if (!f.getName().startsWith(".")) keep.add(f);
            fs = keep.toArray(new File[0]);
        }
        Arrays.sort(fs, comparator(by, desc));
        return fs;
    }

    public static long treeSize(File f) {
        if (f.isFile()) return f.length();
        long s = 0; File[] c = f.listFiles();
        if (c != null) for (File x : c) s += treeSize(x);
        return s;
    }

    public static File unique(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 100000; i++) { f = new File(dir, base + " (" + i + ")" + ext); if (!f.exists()) return f; }
        return new File(dir, base + "-" + System.nanoTime() + ext);
    }

    /** Is [child] equal to or inside [parent]? (guards copying a folder into itself) */
    public static boolean inside(File parent, File child) {
        try {
            String p = parent.getCanonicalPath() + File.separator, c = child.getCanonicalPath() + File.separator;
            return c.startsWith(p);
        } catch (IOException e) { return false; }
    }

    public static long done;

    /** Copy file or folder into [dstDir]; returns the new file. Throws IOException with a readable message. */
    public static File copy(File src, File dstDir, Progress p) throws IOException {
        if (src.isDirectory() && inside(src, dstDir)) throw new IOException("Can't copy a folder into itself.");
        File dst = unique(dstDir, src.getName());
        done = 0;
        copyInto(src, dst, p, 0);
        return dst;
    }

    private static void copyInto(File src, File dst, Progress p, int depth) throws IOException {
        if (p != null && p.cancelled()) throw new IOException("Cancelled");
        if (depth > 64) throw new IOException("Folder is nested too deeply.");
        if (src.isDirectory()) {
            if (!dst.mkdirs() && !dst.isDirectory()) throw new IOException("Can't create " + dst.getName());
            File[] c = src.listFiles();
            if (c != null) for (File x : c) copyInto(x, new File(dst, x.getName()), p, depth + 1);
            return;
        }
        InputStream in = new FileInputStream(src);
        OutputStream out = null;
        boolean ok = false;
        try {
            out = new FileOutputStream(dst);
            byte[] b = new byte[131072]; int r;
            while ((r = in.read(b)) > 0) {
                out.write(b, 0, r); done += r;
                if (p != null) { p.bytes(done, src.getName()); if (p.cancelled()) throw new IOException("Cancelled"); }
            }
            ok = true;
        } catch (IOException e) {
            String m = e.getMessage() == null ? "" : e.getMessage();
            if (m.contains("ENOSPC")) throw new IOException("Not enough free space on the destination.");
            throw e;
        } finally {
            try { in.close(); } catch (IOException ignored) { }
            try { if (out != null) out.close(); } catch (IOException ignored) { }
            if (!ok) dst.delete();
        }
        dst.setLastModified(src.lastModified());
    }

    public static File move(File src, File dstDir, Progress p) throws IOException {
        if (src.isDirectory() && inside(src, dstDir)) throw new IOException("Can't move a folder into itself.");
        File dst = unique(dstDir, src.getName());
        if (src.renameTo(dst)) return dst;
        copyInto(src, dst, p, 0);
        delete(src);
        return dst;
    }

    public static void delete(File f) throws IOException {
        if (f.isDirectory() && !isSymlink(f)) { File[] c = f.listFiles(); if (c != null) for (File x : c) delete(x); }
        if (!f.delete() && f.exists()) throw new IOException("Couldn't delete " + f.getName());
    }

    private static boolean isSymlink(File f) {
        File p = f.getParentFile();
        if (p == null) return false;
        try { return !f.getCanonicalFile().equals(new File(p.getCanonicalFile(), f.getName())); } catch (IOException e) { return true; }
    }

    public static void search(File dir, String q, boolean hidden, int depth, ArrayList<File> out, int max, boolean[] stop) {
        File[] c = dir.listFiles();
        if (c == null || depth > 8) return;
        String lq = q.toLowerCase(Locale.ROOT);
        for (File f : c) {
            if (stop[0] || out.size() >= max) return;
            if (!hidden && f.getName().startsWith(".")) continue;
            if (f.getName().toLowerCase(Locale.ROOT).contains(lq)) out.add(f);
            if (f.isDirectory()) search(f, q, hidden, depth + 1, out, max, stop);
        }
    }
}
