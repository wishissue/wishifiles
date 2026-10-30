package org.wishy.files;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.FrameLayout;
import java.io.*;
import java.util.ArrayList;

/** Single activity; screens are plain View trees pushed on a tiny back stack. */
public class MainActivity extends Activity {
    private FrameLayout root;
    private final ArrayList<Screen> stack = new ArrayList<Screen>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        App.ctx = this;
        Theme.init(this);
        Icons.load(this);
        fullScreen();
        root = new FrameLayout(this);
        root.setClipChildren(false);
        root.setBackgroundColor(Theme.c(Theme.BG));
        setContentView(root);
        start(getIntent());
    }

    /** First screen(s). The file picker (PickerActivity) overrides this to open straight into the chooser. */
    protected void start(Intent i) {
        push(new HomeScreen());
        handleIntent(i);
    }

    /** Only the main window may stop the transfer server when it closes; a short-lived picker must not. */
    protected boolean ownsServer() { return true; }

    /** Edge-to-edge, no system bars, drawn into display cutouts. */
    private void fullScreen() {
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        w.setBackgroundDrawable(new ColorDrawable(Theme.c(Theme.BG)));
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.getClass().getField("layoutInDisplayCutoutMode").setInt(lp, 1); // SHORT_EDGES
                w.setAttributes(lp);
            } catch (Throwable ignored) { }
        }
        hideBars();
    }

    @SuppressWarnings("deprecation")
    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override public void onWindowFocusChanged(boolean has) { super.onWindowFocusChanged(has); if (has) hideBars(); }

    /** Screen size or density changed (rotation, TV resolution switch): re-measure and lay everything out again. */
    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        Theme.measure(this);
        if (!stack.isEmpty()) rebuild();
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); handleIntent(i); }

    private void handleIntent(Intent i) {
        final Uri u = i == null ? null : i.getData();
        if (u == null || !Intent.ACTION_VIEW.equals(i.getAction())) return;
        if ("file".equals(u.getScheme())) { push(new InstallScreen(new File(u.getPath()))); return; }
        UI.toast("Reading the APK\u2026");
        new Thread(new Runnable() { @Override public void run() {
            try {
                final File f = new File(getCacheDir(), "incoming.apk");
                InputStream in = getContentResolver().openInputStream(u);
                OutputStream o = new FileOutputStream(f);
                byte[] buf = new byte[65536]; int r;
                while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
                o.close(); in.close();
                runOnUiThread(new Runnable() { @Override public void run() { push(new InstallScreen(f)); } });
            } catch (final Exception e) {
                runOnUiThread(new Runnable() { @Override public void run() { Dlg.info("Couldn't read that file", String.valueOf(e.getMessage())); } });
            }
        } }).start();
    }

    Screen top() { return stack.get(stack.size() - 1); }

    void push(Screen s) {
        s.a = this;
        if (!stack.isEmpty()) { Screen o = top(); o.onHide(); UI.leave(root, o.view, -UI.dp(30)); }
        stack.add(s);
        s.view = s.build();
        root.addView(s.view, new FrameLayout.LayoutParams(-1, -1));
        UI.enter(s.view, UI.dp(44));
        s.onShow();
    }

    void pop() {
        if (stack.size() <= 1) { finish(); return; }
        Screen s = stack.remove(stack.size() - 1);
        s.onHide(); UI.leave(root, s.view, UI.dp(44));
        Screen t = top();
        t.view = t.build();
        root.addView(t.view, new FrameLayout.LayoutParams(-1, -1));
        UI.enter(t.view, -UI.dp(30));
        t.onShow();
    }

    /** Re-create the visible screen (theme change, permission change). */
    void rebuild() {
        root.setBackgroundColor(Theme.c(Theme.BG));
        getWindow().setBackgroundDrawable(new ColorDrawable(Theme.c(Theme.BG)));
        Screen t = top();
        root.removeView(t.view);
        t.view = t.build();
        root.addView(t.view, new FrameLayout.LayoutParams(-1, -1));
        t.onShow();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        Screen t = top();
        if (t.wantsKeys()) { if (t.onKey(code, e)) return true; }
        else if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_BUTTON_Y || code == KeyEvent.KEYCODE_INFO) { if (t.onKey(code, e)) return true; }
        return super.onKeyDown(code, e);
    }

    @Override public void onBackPressed() { if (!top().onBack()) { if (stack.size() > 1) pop(); else finish(); } }

    @Override protected void onPause() { super.onPause(); if (!stack.isEmpty()) top().onHide(); }

    @Override protected void onResume() { super.onResume(); App.ctx = this; if (!stack.isEmpty()) top().onShow(); }

    @Override public void onRequestPermissionsResult(int c, String[] p, int[] r) { rebuild(); Storage.afterRuntime(this); }

    @Override protected void onDestroy() { super.onDestroy(); if (isFinishing() && ownsServer()) TransferScreen.stopAll(); }

    void open(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Dlg.info("No browser found", "Open this address on your phone or computer:\n\n" + url); }
    }
}
