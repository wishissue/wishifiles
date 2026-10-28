package org.wishy.files;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives PackageInstaller session results. */
public class InstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        int st = i.getIntExtra("android.content.pm.extra.STATUS", 1);
        String msg = i.getStringExtra("android.content.pm.extra.STATUS_MESSAGE");
        String pkg = i.getStringExtra("android.content.pm.extra.PACKAGE_NAME");
        if (st == -1) { // STATUS_PENDING_USER_ACTION: Android's own confirm screen
            Intent confirm = (Intent) i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) { confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); try { c.startActivity(confirm); } catch (Exception e) { Installer.deliver(1, "Couldn't open Android's install screen: " + e.getMessage(), pkg); } }
            return;
        }
        Installer.deliver(st, msg, pkg);
    }
}
