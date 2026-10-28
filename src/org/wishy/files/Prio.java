package org.wishy.files;

import android.content.SharedPreferences;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Decides which folders deserve the big tiles. Priority = how much is inside + how often and how recently you open it
 * + a small head start for well-known folders. Empty folders always sink to the bottom as the smallest tiles.
 * Opening a folder is remembered on the device (just a counter per path, no history of files).
 */
final class Prio {
    static final int S = 0, M = 1, L = 2, XL = 3;   // tile sizes, see Tiles

    static final class Item {
        File f; int items = -1; long mod; float score; int size = S; Object tag;
        boolean empty() { return items == 0; }
    }

    private static SharedPreferences sp() { return Theme.prefs(); }

    static void opened(File f) {
        String k = f.getPath();
        sp().edit().putInt("po:" + k, sp().getInt("po:" + k, 0) + 1).putLong("pt:" + k, System.currentTimeMillis()).apply();
    }

    private static float known(String name) {
        String n = name.toLowerCase();
        if (n.equals("download") || n.equals("downloads")) return 40;
        if (n.equals("dcim") || n.equals("pictures") || n.equals("camera")) return 34;
        if (n.equals("wishifiles") || n.startsWith("received")) return 30;
        if (n.equals("movies") || n.equals("video") || n.equals("videos")) return 26;
        if (n.equals("music") || n.equals("audiobooks")) return 20;
        if (n.equals("documents")) return 16;
        if (n.equals("android")) return -30;     // app data, rarely what you want
        return 0;
    }

    static float score(Item it) {
        if (it.items == 0) return -1000f + known(it.f.getName());
        float s = 100f + known(it.f.getName());
        if (it.items > 0) s += (float) Math.sqrt(Math.min(it.items, 400)) * 4f;
        long now = System.currentTimeMillis();
        int opens = sp().getInt("po:" + it.f.getPath(), 0);
        long last = sp().getLong("pt:" + it.f.getPath(), 0);
        s += Math.min(opens, 20) * 9f;
        if (last > 0) { float d = (now - last) / 86400000f; if (d < 14) s += 30f * (1f - d / 14f); }
        float md = (now - it.mod) / 86400000f;
        if (md < 14) s += 14f * (1f - md / 14f);
        return s;
    }

    /** Rank (best first) and give each a tile size: two Large, then Medium, then Small. Empty folders are always Small and last. */
    static void rank(List<Item> l, boolean byPriority) {
        for (Item it : l) it.score = score(it);
        if (byPriority) Collections.sort(l, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) { return Float.compare(b.score, a.score); }
        });
        else {
            // keep the caller's order, but still push empty folders down
            ArrayList<Item> full = new ArrayList<Item>(), empty = new ArrayList<Item>();
            for (Item it : l) (it.empty() ? empty : full).add(it);
            l.clear(); l.addAll(full); l.addAll(empty);
        }
        int filled = 0;
        for (Item it : l) if (!it.empty()) filled++;
        int k = 0;
        for (Item it : l) {
            if (it.empty()) { it.size = S; continue; }
            if (!byPriority) { it.size = M; k++; continue; }
            // very few folders: don't blow a lone folder up to a giant slab
            if (filled == 1) it.size = L;
            else if (k < 2) it.size = L;
            else if (k < 5) it.size = M;
            else it.size = S;
            k++;
        }
    }
}
