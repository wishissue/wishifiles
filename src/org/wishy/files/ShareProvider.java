package org.wishy.files;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only content:// access to files the user chose to open. Random ids (never paths), so apps can't walk the file system. */
public class ShareProvider extends ContentProvider {
    private static final Map<String, File> MAP = new ConcurrentHashMap<String, File>();

    public static Uri uriFor(File f) {
        String id = TransferServer.randomHex(8);
        if (MAP.size() > 200) MAP.clear();
        MAP.put(id, f);
        return Uri.parse("content://org.wishy.files.share/" + id + "/" + Uri.encode(f.getName()));
    }

    private File file(Uri u) throws FileNotFoundException {
        File f = u.getPathSegments().isEmpty() ? null : MAP.get(u.getPathSegments().get(0));
        if (f == null || !f.isFile()) throw new FileNotFoundException("gone");
        return f;
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new SecurityException("read-only");
        return ParcelFileDescriptor.open(file(u), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
        try {
            File f = file(u);
            MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch (FileNotFoundException e) { return null; }
    }

    @Override public String getType(Uri u) { try { return FileOps.mime(file(u)); } catch (FileNotFoundException e) { return null; } }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
