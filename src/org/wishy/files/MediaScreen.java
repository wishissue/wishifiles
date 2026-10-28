package org.wishy.files;

import android.content.Context;
import android.graphics.*;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

/** Tiny built-in video / audio player on top of the system MediaPlayer (VideoView). Formats are whatever the device can decode. */
public class MediaScreen extends Screen {
    private final ArrayList<File> files; private int idx; private final boolean audio;
    private FrameLayout root; private VideoView vv; private View spinner, problem; private LinearLayout top, bottom;
    private TextView title, count, cur, tot; private Bar bar; private Glyph glyph, flash;
    private final Handler h = new Handler(Looper.getMainLooper());
    private long seekHold; private boolean prepared, wantPlay = true, problemShown, ended; private int resumePos, dur, shownPos;
    private final Runnable hide = new Runnable() { @Override public void run() { if (vv != null && vv.isPlaying()) chrome(false); } };
    private final Runnable tick = new Runnable() { @Override public void run() { update(); h.postDelayed(this, 300); } };

    public MediaScreen(ArrayList<File> files, int idx, boolean audio) { this.files = files; this.idx = Math.max(0, idx); this.audio = audio; }

    @Override boolean wantsKeys() { return !problemShown; }

    // ---------- little custom widgets ----------
    /** Play / pause glyph drawn with Canvas. */
    static final class Glyph extends View {
        boolean playing; int color = 0xFFFFFFFF; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final Path path = new Path();
        Glyph(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), hh = getHeight(), z = Math.min(w, hh) * 0.5f;
            Icons.draw(c, playing ? Icons.PAUSE : Icons.PLAY, (w - z) / 2f, (hh - z) / 2f, z, color);
        }
    }

    /** Progress bar you can also drag with a finger / mouse. */
    final class Bar extends View {
        float frac; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); boolean drag;
        Bar(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), cy = getHeight() / 2f, th = UI.dp(6), r = th / 2f;
            p.setColor(0x44FFFFFF); c.drawRoundRect(new RectF(0, cy - r, w, cy + r), r, r, p);
            p.setColor(Theme.c(Theme.P)); c.drawRoundRect(new RectF(0, cy - r, Math.max(th, w * frac), cy + r), r, r, p);
            p.setColor(0xFFFFFFFF); c.drawCircle(Math.max(r, Math.min(w - r, w * frac)), cy, drag ? UI.dp(10) : UI.dp(7.5f), p);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (dur <= 0) return true;
            float f = Math.max(0f, Math.min(1f, e.getX() / Math.max(1, getWidth())));
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: case MotionEvent.ACTION_MOVE: drag = true; frac = f; shownPos = (int) (f * dur); setTimes(); invalidate(); h.removeCallbacks(hide); return true;
                case MotionEvent.ACTION_UP: drag = false; seek((int) (f * dur)); chrome(true); invalidate(); return true;
                case MotionEvent.ACTION_CANCEL: drag = false; invalidate(); return true;
            }
            return true;
        }
    }

    // ---------- screen ----------
    @Override View build() {
        if (vv != null && prepared && !ended) { try { resumePos = vv.getCurrentPosition(); } catch (Throwable ignored) { } }
        root = new FrameLayout(a); root.setBackgroundColor(0xFF000000); root.setClipChildren(false); root.setKeepScreenOn(true);
        prepared = false; ended = false; problemShown = false; problem = null;

        if (audio) {
            LinearLayout art = UI.vbox(); art.setGravity(Gravity.CENTER);
            art.addView(UI.iconTile(Icons.AUDIO, 150, Theme.c(Theme.PC), Theme.c(Theme.ON_PC)));
            root.addView(art, new FrameLayout.LayoutParams(-1, -1));
        }
        vv = new VideoView(a);
        root.addView(vv, new FrameLayout.LayoutParams(audio ? 1 : -1, audio ? 1 : -1, Gravity.CENTER));
        vv.setOnPreparedListener(new MediaPlayer.OnPreparedListener() { @Override public void onPrepared(MediaPlayer mp) {
            prepared = true; dur = vv.getDuration(); spinner.setVisibility(View.GONE);
            if (resumePos > 0) { vv.seekTo(resumePos); resumePos = 0; }
            if (wantPlay) vv.start();
            glyph.playing = wantPlay; glyph.invalidate(); update(); chrome(true);
        } });
        vv.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { @Override public void onCompletion(MediaPlayer mp) {
            if (idx + 1 < files.size()) go(1);
            else { ended = true; glyph.playing = false; glyph.invalidate(); bar.frac = 1f; bar.invalidate(); chrome(true); }
        } });
        vv.setOnErrorListener(new MediaPlayer.OnErrorListener() { @Override public boolean onError(MediaPlayer mp, int what, int extra) { showProblem(); return true; } });

        spinner = UI.spinner(60);
        root.addView(spinner, new FrameLayout.LayoutParams(UI.dp(60), UI.dp(60), Gravity.CENTER));

        // big flash when play/pause toggles
        flash = new Glyph(a); flash.setBackground(UI.rr(0x99000000, 60)); flash.setAlpha(0f);
        root.addView(flash, new FrameLayout.LayoutParams(UI.dp(120), UI.dp(120), Gravity.CENTER));

        top = UI.hbox();
        top.setPadding(Theme.padX(), Theme.padY(), Theme.padX(), UI.dp(28));
        top.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xCC000000, 0x00000000}));
        View back = UI.chip(Icons.BACK, "Back", false, new UI.Act() { @Override public void run() { a.pop(); } });
        back.setFocusable(false); top.addView(back);
        LinearLayout tl = UI.vbox(); tl.setPadding(UI.dp(18), 0, UI.dp(12), 0);
        title = UI.tv("", 18, true, 0xFFFFFFFF); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        count = UI.tv("", 13, false, 0xFFC9C9CF); count.setPadding(0, UI.dp(3), 0, 0);
        tl.addView(title); tl.addView(count);
        top.addView(tl, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        bottom = UI.vbox();
        bottom.setPadding(Theme.padX(), UI.dp(40), Theme.padX(), Theme.padY());
        bottom.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xDD000000, 0x00000000}));
        LinearLayout row = UI.hbox();
        glyph = new Glyph(a); glyph.setBackground(UI.rr(0x33FFFFFF, 26));
        glyph.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { toggle(); } });
        row.addView(glyph, new LinearLayout.LayoutParams(UI.dp(52), UI.dp(52)));
        cur = UI.tv("0:00", 14, true, 0xFFFFFFFF); cur.setPadding(UI.dp(16), 0, UI.dp(12), 0);
        row.addView(cur);
        bar = new Bar(a);
        row.addView(bar, new LinearLayout.LayoutParams(0, UI.dp(36), 1f));
        tot = UI.tv("0:00", 14, true, 0xFFC9C9CF); tot.setPadding(UI.dp(12), 0, 0, 0);
        row.addView(tot);
        bottom.addView(row);
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        root.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { chrome(!chromeOn()); } });
        load();
        return root;
    }

    private void load() {
        File f = files.get(idx);
        title.setText(f.getName());
        count.setText(files.size() > 1 ? (idx + 1) + " of " + files.size() : UI.size(f.length()));
        prepared = false; ended = false; dur = 0; shownPos = 0; bar.frac = 0; bar.invalidate(); setTimes();
        spinner.setVisibility(View.VISIBLE);
        try { vv.setVideoPath(f.getPath()); } catch (Throwable t) { showProblem(); return; }
        glyph.playing = wantPlay; glyph.invalidate();
    }

    private void go(int d) {
        int n = idx + d;
        if (n < 0 || n >= files.size()) { UI.toast(n < 0 ? "This is the first one." : "This is the last one."); return; }
        idx = n; resumePos = 0; wantPlay = true; hideProblem();
        try { vv.stopPlayback(); } catch (Throwable ignored) { }
        load(); chrome(true);
    }

    private void showProblem() {
        if (problemShown) return;
        hideProblem();
        File f = files.get(idx); String e = FileOps.ext(f.getName());
        spinner.setVisibility(View.GONE);
        chrome(false); h.removeCallbacks(tick);
        problem = Open.problem(a, f, "Can't play this " + (audio ? "audio" : "video"),
                "This device can't play ." + (e.length() > 0 ? e : "this") + " files (the format or codec isn't supported, or the file is damaged).\n\nInstall a media player app that supports it, then try again.");
        root.addView(problem, new FrameLayout.LayoutParams(-1, -1));
        problemShown = true;
        try { vv.stopPlayback(); } catch (Throwable ignored) { }
        problem.post(new Runnable() { @Override public void run() { if (problem != null && problem.getTag() instanceof View) ((View) problem.getTag()).requestFocus(); } });
    }

    private void hideProblem() { if (problem != null) { root.removeView(problem); problem = null; } problemShown = false; }

    // ---------- controls ----------
    private boolean chromeOn() { return top.getVisibility() == View.VISIBLE && top.getAlpha() > 0.5f; }

    private void chrome(boolean show) {
        h.removeCallbacks(hide);
        if (show) {
            top.setVisibility(View.VISIBLE); bottom.setVisibility(View.VISIBLE);
            top.animate().alpha(1f).setDuration(180).start(); bottom.animate().alpha(1f).setDuration(180).start();
            if (vv != null && vv.isPlaying()) h.postDelayed(hide, 3500);
        } else {
            top.animate().alpha(0f).setDuration(260).withEndAction(new Runnable() { @Override public void run() { if (top.getAlpha() == 0f) { top.setVisibility(View.INVISIBLE); bottom.setVisibility(View.INVISIBLE); } } }).start();
            bottom.animate().alpha(0f).setDuration(260).start();
        }
    }

    private void toggle() {
        if (!prepared || problemShown) return;
        if (ended) { ended = false; vv.seekTo(0); vv.start(); wantPlay = true; }
        else if (vv.isPlaying()) { vv.pause(); wantPlay = false; }
        else { vv.start(); wantPlay = true; }
        glyph.playing = vv.isPlaying(); glyph.invalidate();
        flash.playing = !vv.isPlaying(); flash.invalidate();
        flash.setScaleX(0.7f); flash.setScaleY(0.7f); flash.setAlpha(1f);
        flash.animate().alpha(0f).scaleX(1.3f).scaleY(1.3f).setDuration(460).setInterpolator(UI.EASE).start();
        chrome(true);
    }

    private void seek(int ms) {
        if (!prepared) return;
        ms = Math.max(0, Math.min(dur > 0 ? dur - 300 : ms, ms));
        ended = false; vv.seekTo(ms); shownPos = ms; seekHold = System.currentTimeMillis() + 700; update();
    }

    private void skip(int dir, int repeat) {
        if (!prepared) return;
        int step = repeat < 3 ? 10000 : repeat < 12 ? 30000 : 60000;
        seek(shownPos + dir * step); chrome(true);
    }

    private void update() {
        if (vv == null || !prepared || bar.drag) return;
        int p = vv.getCurrentPosition(); dur = vv.getDuration();
        if (!ended && System.currentTimeMillis() >= seekHold) shownPos = p;
        bar.frac = dur > 0 ? Math.min(1f, shownPos / (float) dur) : 0f; bar.invalidate();
        glyph.playing = vv.isPlaying(); glyph.invalidate();
        setTimes();
    }

    private void setTimes() { cur.setText(fmt(shownPos)); tot.setText(dur > 0 ? fmt(dur) : "--:--"); }

    static String fmt(int ms) {
        int s = Math.max(0, ms / 1000), hr = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return hr > 0 ? String.format(Locale.US, "%d:%02d:%02d", hr, m, sec) : String.format(Locale.US, "%d:%02d", m, sec);
    }

    @Override boolean onKey(int code, KeyEvent e) {
        if (vv == null || problemShown) return false;
        int rep = e.getRepeatCount();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER: case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_SPACE: case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: case KeyEvent.KEYCODE_HEADSETHOOK:
                if (rep == 0) toggle(); return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY: if (!vv.isPlaying() && rep == 0) toggle(); return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE: if (vv.isPlaying() && rep == 0) toggle(); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_MEDIA_REWIND: skip(-1, rep); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: skip(1, rep); return true;
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_MENU: case KeyEvent.KEYCODE_INFO: chrome(true); return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT: case KeyEvent.KEYCODE_PAGE_DOWN: case KeyEvent.KEYCODE_CHANNEL_UP: case KeyEvent.KEYCODE_BUTTON_R1: if (rep == 0) go(1); return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: case KeyEvent.KEYCODE_PAGE_UP: case KeyEvent.KEYCODE_CHANNEL_DOWN: case KeyEvent.KEYCODE_BUTTON_L1: if (rep == 0) go(-1); return true;
        }
        return false;
    }

    @Override void onShow() {
        h.removeCallbacks(tick); h.post(tick);
        // Same surface still alive (activity was only paused): carry on. If the surface was rebuilt, onPrepared resumes instead.
        if (vv != null && prepared && !problemShown && vv.getDuration() > 0) { resumePos = 0; if (wantPlay && !ended && !vv.isPlaying()) vv.start(); }
    }

    @Override void onHide() {
        h.removeCallbacks(tick); h.removeCallbacks(hide);
        if (vv != null && prepared && !problemShown) {
            if (ended) { resumePos = 0; wantPlay = false; } else resumePos = vv.getCurrentPosition();
            try { vv.pause(); } catch (Throwable ignored) { }
        }
    }
}
