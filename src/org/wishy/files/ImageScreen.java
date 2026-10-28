package org.wishy.files;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.media.ExifInterface;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.io.File;
import java.util.ArrayList;

/** Tiny built-in picture viewer: fit-to-screen, pinch / double-tap / OK to zoom, D-pad to pan or browse. No libraries. */
public class ImageScreen extends Screen {
    private final ArrayList<File> files; private int idx;
    private FrameLayout root; private ZoomView zv; private View spinner, problem; private LinearLayout top; private TextView title, count, hint;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int gen; private boolean problemShown;
    private final Runnable hide = new Runnable() { @Override public void run() { fade(top, false); fade(hint, false); } };

    public ImageScreen(ArrayList<File> files, int idx) { this.files = files; this.idx = Math.max(0, idx); }

    @Override boolean wantsKeys() { return !problemShown; }

    // ---------- the zoomable surface ----------
    static final class ZoomView extends View {
        Bitmap bmp; float z = 1f, px, py; final Matrix m = new Matrix(); final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        final ScaleGestureDetector sg; final GestureDetector gd; Runnable onTap; Runnable onNext, onPrev; ValueAnimator anim;

        ZoomView(Context c) {
            super(c);
            sg = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector d) { zoomAt(z * d.getScaleFactor(), d.getFocusX() - getWidth() / 2f, d.getFocusY() - getHeight() / 2f); return true; }
            });
            gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { return true; }
                @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) { if (z > 1.01f && !sg.isInProgress()) { px -= dx; py -= dy; clamp(); invalidate(); } return true; }
                @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                    if (z <= 1.01f && Math.abs(vx) > 900 && Math.abs(vx) > Math.abs(vy)) { if (vx < 0) { if (onNext != null) onNext.run(); } else if (onPrev != null) onPrev.run(); return true; }
                    return false;
                }
                @Override public boolean onDoubleTap(MotionEvent e) { toggle(e.getX() - getWidth() / 2f, e.getY() - getHeight() / 2f); return true; }
                @Override public boolean onSingleTapConfirmed(MotionEvent e) { if (onTap != null) onTap.run(); return true; }
            });
        }

        void set(Bitmap b) { bmp = b; z = 1f; px = py = 0; invalidate(); }
        float fit() { return bmp == null ? 1f : Math.min(getWidth() / (float) bmp.getWidth(), getHeight() / (float) bmp.getHeight()); }
        boolean zoomed() { return z > 1.01f; }

        void clamp() {
            if (bmp == null) return;
            float w = bmp.getWidth() * fit() * z, hh = bmp.getHeight() * fit() * z;
            float mx = Math.max(0, (w - getWidth()) / 2f), my = Math.max(0, (hh - getHeight()) / 2f);
            px = Math.max(-mx, Math.min(mx, px)); py = Math.max(-my, Math.min(my, py));
        }

        /** Zoom to [nz] keeping the point (fx, fy), measured from the screen centre, where it is. */
        void zoomAt(float nz, float fx, float fy) {
            nz = Math.max(1f, Math.min(8f, nz));
            float r = nz / z; px = fx - (fx - px) * r; py = fy - (fy - py) * r; z = nz;
            if (z <= 1.001f) { z = 1f; px = py = 0; }
            clamp(); invalidate();
        }

        void animateTo(final float nz, final float fx, final float fy) {
            if (anim != null) anim.cancel();
            final float z0 = z, px0 = px, py0 = py;
            float tz = Math.max(1f, Math.min(8f, nz)), r = tz / z0;
            final float tx = tz <= 1f ? 0 : fx - (fx - px0) * r, ty = tz <= 1f ? 0 : fy - (fy - py0) * r, fz = tz;
            anim = ValueAnimator.ofFloat(0f, 1f); anim.setDuration(220); anim.setInterpolator(new DecelerateInterpolator());
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { @Override public void onAnimationUpdate(ValueAnimator a) {
                float t = (Float) a.getAnimatedValue(); z = z0 + (fz - z0) * t; px = px0 + (tx - px0) * t; py = py0 + (ty - py0) * t; clamp(); invalidate(); } });
            anim.start();
        }

        void toggle(float fx, float fy) { if (zoomed()) animateTo(1f, 0, 0); else animateTo(2.5f, fx, fy); }

        /** D-pad pan. Returns false when already against that edge. */
        boolean pan(float dx, float dy) {
            float ox = px, oy = py; px += dx; py += dy; clamp(); invalidate();
            return Math.abs(px - ox) > 0.5f || Math.abs(py - oy) > 0.5f;
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { clamp(); }

        @Override protected void onDraw(Canvas c) {
            if (bmp == null) return;
            float s = fit() * z;
            m.reset(); m.postTranslate(-bmp.getWidth() / 2f, -bmp.getHeight() / 2f); m.postScale(s, s); m.postTranslate(getWidth() / 2f + px, getHeight() / 2f + py);
            c.drawBitmap(bmp, m, paint);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            sg.onTouchEvent(e); gd.onTouchEvent(e); return true;
        }
    }

    // ---------- screen ----------
    @Override View build() {
        root = new FrameLayout(a); root.setBackgroundColor(0xFF000000); root.setClipChildren(false);
        zv = new ZoomView(a); root.addView(zv, new FrameLayout.LayoutParams(-1, -1));
        zv.onTap = new Runnable() { @Override public void run() { toggleChrome(); } };
        zv.onNext = new Runnable() { @Override public void run() { go(1); } };
        zv.onPrev = new Runnable() { @Override public void run() { go(-1); } };

        spinner = UI.spinner(56);
        root.addView(spinner, new FrameLayout.LayoutParams(UI.dp(56), UI.dp(56), Gravity.CENTER));
        spinner.setVisibility(View.GONE);

        top = UI.hbox();
        top.setPadding(Theme.padX(), Theme.padY(), Theme.padX(), UI.dp(24));
        top.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xCC000000, 0x00000000}));
        View back = UI.chip(Icons.BACK, "Back", false, new UI.Act() { @Override public void run() { a.pop(); } });
        back.setFocusable(false);
        top.addView(back);
        LinearLayout tl = UI.vbox(); tl.setPadding(UI.dp(18), 0, UI.dp(12), 0);
        title = UI.tv("", 18, true, 0xFFFFFFFF); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        count = UI.tv("", 13, false, 0xFFC9C9CF); count.setPadding(0, UI.dp(3), 0, 0);
        tl.addView(title); tl.addView(count);
        top.addView(tl, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        hint = UI.tv("", 1, false, 0);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hl.bottomMargin = Theme.padY();
        root.addView(hint, hl);

        problemShown = false;
        load(idx, 0);
        showChrome();
        return root;
    }

    private void fade(final View v, final boolean show) {
        if (v == null) return;
        if (show) { v.setVisibility(View.VISIBLE); v.animate().alpha(1f).setDuration(180).start(); }
        else v.animate().alpha(0f).setDuration(260).withEndAction(new Runnable() { @Override public void run() { if (v.getAlpha() == 0f) v.setVisibility(View.INVISIBLE); } }).start();
    }

    private boolean chromeOn() { return top.getVisibility() == View.VISIBLE && top.getAlpha() > 0.5f; }

    /** Show the title/hint bars, then fade them out again after a few seconds. */
    private void showChrome() { h.removeCallbacks(hide); fade(top, true); fade(hint, true); h.postDelayed(hide, 3500); }

    private void toggleChrome() { if (chromeOn()) { h.removeCallbacks(hide); fade(top, false); fade(hint, false); } else showChrome(); }

    private void go(int d) {
        if (files.size() < 2) { UI.toast(files.size() == 1 ? "This is the only picture here." : ""); return; }
        int n = idx + d;
        if (n < 0 || n >= files.size()) { UI.toast(n < 0 ? "This is the first picture." : "This is the last picture."); return; }
        load(n, d);
    }

    private void load(final int i, final int dir) {
        idx = i; final int g = ++gen; final File f = files.get(i);
        title.setText(f.getName());
        count.setText((i + 1) + " of " + files.size() + "   \u00b7   " + UI.size(f.length()));
        if (dir != 0) zv.animate().alpha(0f).translationX(-dir * UI.dp(40)).setDuration(110).start();
        spinner.setVisibility(View.VISIBLE);
        final int maxSide = Math.min(4096, Math.max(Theme.wPx, Theme.hPx) * 2);
        final long budget = Math.min(12000000L, Runtime.getRuntime().maxMemory() / 16);
        new Thread(new Runnable() { @Override public void run() {
            final Bitmap b = decode(f, maxSide, budget);
            a.runOnUiThread(new Runnable() { @Override public void run() {
                if (g != gen || zv == null) { return; }
                spinner.setVisibility(View.GONE);
                if (b == null) { showProblem(f); return; }
                hideProblem();
                zv.set(b);
                zv.setTranslationX(dir * UI.dp(40));
                zv.animate().alpha(1f).translationX(0).setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
            } });
        } }).start();
    }

    private void showProblem(File f) {
        hideProblem();
        zv.set(null);
        problem = Open.problem(a, f, "This picture can't be shown", "The file may be damaged, too large for this device, or in a format Android can't read here.\n\nYou can try another app, or install a gallery app that supports it.");
        root.addView(problem, new FrameLayout.LayoutParams(-1, -1));
        problemShown = true;
        h.removeCallbacks(hide);
        top.setVisibility(View.INVISIBLE); hint.setVisibility(View.INVISIBLE);
        problem.post(new Runnable() { @Override public void run() { if (problem != null && problem.getTag() instanceof View) ((View) problem.getTag()).requestFocus(); } });
    }

    private void hideProblem() { if (problem != null) { root.removeView(problem); problem = null; } problemShown = false; }

    /** Decode no larger than the screen can use (2x for zoom) and honour the camera's rotation flag. Null if it can't be read. */
    static Bitmap decode(File f, int maxSide, long budget) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int ss = 1;
            while (o.outWidth / ss > maxSide || o.outHeight / ss > maxSide || (long) (o.outWidth / ss) * (o.outHeight / ss) > budget) ss *= 2;
            o.inJustDecodeBounds = false; o.inSampleSize = ss; o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
            if (b == null) return null;
            int ori = ExifInterface.ORIENTATION_NORMAL;
            try { ori = new ExifInterface(f.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL); } catch (Throwable ignored) { }
            Matrix m = new Matrix();
            switch (ori) {
                case ExifInterface.ORIENTATION_ROTATE_90: m.postRotate(90); break;
                case ExifInterface.ORIENTATION_ROTATE_180: m.postRotate(180); break;
                case ExifInterface.ORIENTATION_ROTATE_270: m.postRotate(270); break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: m.postScale(1, -1); break;
                case ExifInterface.ORIENTATION_TRANSPOSE: m.postRotate(90); m.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_TRANSVERSE: m.postRotate(270); m.postScale(-1, 1); break;
                default: return b;
            }
            Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            if (r != b) b.recycle();
            return r;
        } catch (Throwable t) { return null; } // includes OutOfMemoryError
    }

    @Override boolean onKey(int code, KeyEvent e) {
        if (zv == null || problemShown) return false;
        float step = Math.max(zv.getWidth(), zv.getHeight()) * 0.16f;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (zv.zoomed()) { if (!zv.pan(step, 0) && e.getRepeatCount() == 0) go(-1); } else go(-1);
                chromeQuiet(); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (zv.zoomed()) { if (!zv.pan(-step, 0) && e.getRepeatCount() == 0) go(1); } else go(1);
                chromeQuiet(); return true;
            case KeyEvent.KEYCODE_DPAD_UP: if (zv.zoomed()) zv.pan(0, step); else peek(); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: if (zv.zoomed()) zv.pan(0, -step); else peek(); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER: case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_SPACE:
                if (e.getRepeatCount() == 0) zv.toggle(0, 0); return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT: case KeyEvent.KEYCODE_PAGE_DOWN: case KeyEvent.KEYCODE_CHANNEL_UP: case KeyEvent.KEYCODE_BUTTON_R1: go(1); return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: case KeyEvent.KEYCODE_PAGE_UP: case KeyEvent.KEYCODE_CHANNEL_DOWN: case KeyEvent.KEYCODE_BUTTON_L1: go(-1); return true;
            case KeyEvent.KEYCODE_ZOOM_IN: case KeyEvent.KEYCODE_PLUS: case KeyEvent.KEYCODE_EQUALS: case KeyEvent.KEYCODE_NUMPAD_ADD: zv.animateTo(zv.z * 1.6f, 0, 0); return true;
            case KeyEvent.KEYCODE_ZOOM_OUT: case KeyEvent.KEYCODE_MINUS: case KeyEvent.KEYCODE_NUMPAD_SUBTRACT: zv.animateTo(zv.z / 1.6f, 0, 0); return true;
            case KeyEvent.KEYCODE_MENU: case KeyEvent.KEYCODE_INFO: case KeyEvent.KEYCODE_BUTTON_Y: peek(); return true;
        }
        return false;
    }

    private void peek() { showChrome(); }
    private void chromeQuiet() { if (chromeOn()) { h.removeCallbacks(hide); h.postDelayed(hide, 2000); } }

    @Override void onHide() { h.removeCallbacks(hide); }
}
