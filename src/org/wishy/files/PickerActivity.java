package org.wishy.files;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import java.io.File;

/**
 * Lets other apps use Wishi Files as their "choose a file" screen (GET_CONTENT, OPEN_DOCUMENT, PICK).
 *
 * It is its own activity because MainActivity is singleTask, and Android cancels the result of any singleTask activity that was started for
 * a result. This one launches normally, inside the caller's task, shows the same Files screens in picker mode, and hands back one file
 * through ShareProvider (read-only, random id, never a path).
 */
public class PickerActivity extends MainActivity {
    @Override protected boolean ownsServer() { return false; }

    @Override protected void start(Intent in) {
        push(new FilesScreen(null, new FilesScreen.Picker() { @Override public void pick(File f) { deliver(f); } }, "Choose a file", wanted(in)));
    }

    /** MIME types the caller can accept: EXTRA_MIME_TYPES, else the intent type, else anything. */
    static String[] wanted(Intent in) {
        String[] m = in == null ? null : in.getStringArrayExtra(Intent.EXTRA_MIME_TYPES);
        if (m != null && m.length > 0) return m;
        String t = in == null ? null : in.getType();
        return t == null ? new String[]{"*/*"} : new String[]{t};
    }

    private void deliver(File f) {
        Uri u = ShareProvider.uriFor(f);
        Intent r = new Intent();
        r.setDataAndType(u, FileOps.mime(f));
        r.setClipData(ClipData.newRawUri(f.getName(), u));
        r.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        setResult(RESULT_OK, r);
        finish();
    }
}
