package org.wishy.files;

import android.view.KeyEvent;
import android.view.View;

public abstract class Screen {
    MainActivity a; View view;
    abstract View build();
    void onShow() { }
    void onHide() { }
    boolean onBack() { return false; }
    boolean onKey(int code, KeyEvent e) { return false; }
    /** Full-screen viewers return true so they see every key (D-pad, media keys) before focus navigation does. */
    boolean wantsKeys() { return false; }
}
