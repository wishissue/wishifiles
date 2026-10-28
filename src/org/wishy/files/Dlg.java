package org.wishy.files;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

/** Themed, D-pad friendly dialogs. */
public final class Dlg {
    public interface Text { void got(String s); }
    public interface Pick { void picked(int i); }

    public static final class B {
        final Dialog d; final LinearLayout box; final Flow btns;
        View firstFocus;
        B(String title) {
            d = new Dialog(App.ctx);
            d.requestWindowFeature(Window.FEATURE_NO_TITLE);
            box = UI.vbox();
            box.setPadding(UI.dp(26), UI.dp(24), UI.dp(26), UI.dp(14));
            box.setBackground(UI.rr(Theme.c(Theme.CONT_H), 30));
            TextView t = UI.tv(title, 22, true, Theme.c(Theme.ON_SURF));
            t.setPadding(0, 0, 0, UI.dp(12));
            box.addView(t);
            btns = UI.flow();
            btns.align = Flow.END;
            btns.setPadding(UI.dp(4), UI.dp(14), UI.dp(2), UI.dp(2));
        }
        public B msg(String s) { TextView t = UI.tv(s, 15, false, Theme.c(Theme.ON_SV)); t.setLineSpacing(0, 1.15f); box.addView(t); return this; }
        public B view(View v) { box.addView(v); return this; }
        public B btn(String label, boolean primary, final UI.Act a) {
            View c = UI.textBtn(label, primary, new UI.Act() { @Override public void run() { d.dismiss(); if (a != null) a.run(); } });
            btns.addView(c);
            if (primary || firstFocus == null) firstFocus = c;
            return this;
        }
        public Dialog show() {
            box.addView(btns, new LinearLayout.LayoutParams(-1, -2));
            d.setContentView(box);
            Window w = d.getWindow();
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0.6f);
            w.setLayout(Math.min(UI.dp(620), Theme.wPx - UI.dp(40)), -2);
            d.show();
            box.setScaleX(0.88f); box.setScaleY(0.88f); box.setAlpha(0f); box.setTranslationY(UI.dp(18));
            box.animate().scaleX(1).scaleY(1).alpha(1).translationY(0).setDuration(300).setInterpolator(UI.EASE).start();
            if (firstFocus != null) firstFocus.requestFocus();
            return d;
        }
    }

    public static B make(String title) { return new B(title); }

    public static void info(String title, String msg) { make(title).msg(msg).btn("OK", true, null).show(); }

    public static void confirm(String title, String msg, String yes, UI.Act onYes) {
        make(title).msg(msg).btn("Cancel", false, null).btn(yes, true, onYes).show();
    }

    public static void menu(String title, String[] labels, int[] icons, final Pick p) {
        final B b = make(title);
        LinearLayout list = UI.vbox();
        final Dialog[] dd = new Dialog[1];
        View first = null;
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            LinearLayout r = UI.rowPlain(icons[i], labels[i], null, null, new UI.Act() { @Override public void run() { dd[0].dismiss(); p.picked(idx); } });
            r.setPadding(UI.dp(12), UI.dp(9), UI.dp(12), UI.dp(9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = UI.dp(6);
            list.addView(r, lp);
            if (first == null) first = r;
        }
        ScrollView sv = UI.scroll(list);
        b.view(sv);
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.min(UI.dp(72 * labels.length + 8) + 2 * UI.grow(), (int) (Theme.hPx * 0.55f))));
        b.btn("Close", false, null);
        dd[0] = b.show();
        if (first != null) first.requestFocus();
    }

    public static void input(String title, String hint, String initial, String ok, final Text t) {
        final EditText e = new EditText(App.ctx);
        e.setText(initial == null ? "" : initial);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextColor(Theme.c(Theme.ON_SURF));
        e.setHintTextColor(Theme.c(Theme.ON_SV));
        e.setTypeface(Theme.reg);
        e.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, UI.dp(17));
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setPadding(UI.dp(18), UI.dp(12), UI.dp(18), UI.dp(12));
        e.setBackground(UI.sel(Theme.c(Theme.CONT), 20));
        if (initial != null) e.setSelection(0, Math.max(0, initial.lastIndexOf('.') > 0 ? initial.lastIndexOf('.') : initial.length()));
        final B b = make(title).view(e).btn("Cancel", false, null);
        b.btn(ok, true, new UI.Act() { @Override public void run() { t.got(e.getText().toString().trim()); } });
        final Dialog d = b.show();
        e.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int a, KeyEvent ev) { d.dismiss(); t.got(e.getText().toString().trim()); return true; }
        });
        e.requestFocus();
    }
}
