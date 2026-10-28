package org.wishy.files;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * File previews: pictures, video frames, album art, APK icons and the first page of PDFs. Decoded small (sub-sampled) off the UI thread,
 * kept in a memory-capped LRU cache, and applied only if the recycled view still wants that file. Nothing is written to disk.
 */
final class Thumbs {
    private static final Handler UI_H = new Handler(Looper.getMainLooper());
    private static final android.util.LruCache<String, Bitmap> CACHE = new android.util.LruCache<String, Bitmap>(
            (int) Math.max(4L << 20, Math.min(24L << 20, Runtime.getRuntime().maxMemory() / 10))) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, new ThreadFactory() {
        @Override public Thread newThread(Runnable r) { Thread t = new Thread(r, "thumbs"); t.setPriority(Thread.MIN_PRIORITY + 1); t.setDaemon(true); return t; }
    });

    /** Does this file type have a preview worth loading? */
    static boolean has(File f) {
        int t = FileOps.type(f);
        return t == FileOps.T_IMAGE || t == FileOps.T_VIDEO || t == FileOps.T_AUDIO || t == FileOps.T_APK || t == FileOps.T_PDF;
    }

    /** Show the preview of [f] in [v] (about [px] pixels square). Returns true if a cached one was applied at once. [fallback] runs when none exists. */
    static void load(final ImageView v, final File f, final int px, final Runnable fallback) {
        final String key = f.getPath() + "|" + f.lastModified() + "|" + px;
        v.animate().cancel();
        v.setTag(R.id.k_thumb, key);
        Bitmap hit = CACHE.get(key);
        if (hit != null) { v.setImageBitmap(hit); v.setAlpha(1f); v.setVisibility(android.view.View.VISIBLE); return; }
        v.setImageDrawable(null); v.setVisibility(android.view.View.INVISIBLE);
        POOL.execute(new Runnable() { @Override public void run() {
            if (!key.equals(v.getTag(R.id.k_thumb))) return;           // scrolled away already
            Bitmap b = null;
            try { b = make(f, px); } catch (Throwable ignored) { }
            final Bitmap res = b;
            if (res != null) CACHE.put(key, res);
            UI_H.post(new Runnable() { @Override public void run() {
                if (!key.equals(v.getTag(R.id.k_thumb))) return;
                if (res == null) { if (fallback != null) fallback.run(); return; }
                v.setImageBitmap(res); v.setVisibility(android.view.View.VISIBLE); v.setAlpha(0f);
                v.animate().alpha(1f).setDuration(180).start();
            } });
        } });
    }

    private static Bitmap make(File f, int px) throws Exception {
        switch (FileOps.type(f)) {
            case FileOps.T_IMAGE: return image(f, px);
            case FileOps.T_VIDEO: return video(f, px);
            case FileOps.T_AUDIO: return art(f, px);
            case FileOps.T_APK: return apk(f, px);
            case FileOps.T_PDF: return pdf(f, px);
        }
        return null;
    }

    private static Bitmap image(File f, int px) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0) return null;
        int ss = 1; while (o.outWidth / (ss * 2) >= px && o.outHeight / (ss * 2) >= px) ss *= 2;
        o.inJustDecodeBounds = false; o.inSampleSize = ss;
        String e = FileOps.ext(f.getName());
        o.inPreferredConfig = e.equals("jpg") || e.equals("jpeg") ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        if (b == null) return null;
        if (e.equals("jpg") || e.equals("jpeg")) {
            try {
                int ori = new ExifInterface(f.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1), deg = ori == 6 ? 90 : ori == 3 ? 180 : ori == 8 ? 270 : 0;
                if (deg != 0) { android.graphics.Matrix m = new android.graphics.Matrix(); m.postRotate(deg); b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true); }
            } catch (Throwable ignored) { }
        }
        return b;
    }

    private static Bitmap video(File f, int px) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(f.getPath());
            Bitmap b = r.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (b == null) b = r.getFrameAtTime(0);
            return small(b, px);
        } finally { try { r.release(); } catch (Throwable ignored) { } }
    }

    private static Bitmap art(File f, int px) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(f.getPath());
            byte[] d = r.getEmbeddedPicture();
            if (d == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(d, 0, d.length, o);
            int ss = 1; while (o.outWidth / (ss * 2) >= px) ss *= 2;
            o.inJustDecodeBounds = false; o.inSampleSize = ss;
            return BitmapFactory.decodeByteArray(d, 0, d.length, o);
        } finally { try { r.release(); } catch (Throwable ignored) { } }
    }

    private static Bitmap apk(File f, int px) {
        android.content.pm.PackageManager pm = App.ctx.getPackageManager();
        android.content.pm.PackageInfo pi = pm.getPackageArchiveInfo(f.getAbsolutePath(), 0);
        if (pi == null || pi.applicationInfo == null) return null;
        pi.applicationInfo.sourceDir = pi.applicationInfo.publicSourceDir = f.getAbsolutePath();
        Drawable d = pi.applicationInfo.loadIcon(pm);
        if (d == null) return null;
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b); d.setBounds(0, 0, px, px); d.draw(c);
        return b;
    }

    private static Bitmap pdf(File f, int px) throws Exception {
        android.os.ParcelFileDescriptor fd = android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
        android.graphics.pdf.PdfRenderer r = new android.graphics.pdf.PdfRenderer(fd);
        try {
            android.graphics.pdf.PdfRenderer.Page p = r.openPage(0);
            float s = (float) px / Math.min(p.getWidth(), p.getHeight());
            Bitmap b = Bitmap.createBitmap(Math.max(1, Math.round(p.getWidth() * s)), Math.max(1, Math.round(p.getHeight() * s)), Bitmap.Config.ARGB_8888);
            b.eraseColor(Color.WHITE);
            p.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            p.close();
            return b;
        } finally { r.close(); fd.close(); }
    }

    private static Bitmap small(Bitmap b, int px) {
        if (b == null) return null;
        float s = (float) px / Math.min(b.getWidth(), b.getHeight());
        if (s >= 1f) return b;
        Bitmap o = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * s)), Math.max(1, Math.round(b.getHeight() * s)), true);
        if (o != b) b.recycle();
        return o;
    }
}
