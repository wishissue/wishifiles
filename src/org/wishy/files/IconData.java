package org.wishy.files;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Reads assets/icons.bin (see tools/build_icons.py). Pure Java so it can be tested on a desktop JVM. Coordinates are on a 256 grid. */
final class IconData {
    static final int M = 0, L = 1, Q = 2, C = 3, Z = 4;

    static final class Layer { int alpha; boolean stroke; byte[] ops; float[] pts; }

    /** icons[i] = the layers of icon i, bottom layer first. Each layer is filled or stroked (Lucide icons are strokes). */
    static Layer[][] read(InputStream is) throws IOException {
        DataInputStream d = new DataInputStream(is);
        int n = d.readUnsignedShort();
        Layer[][] out = new Layer[n][];
        for (int i = 0; i < n; i++) {
            Layer[] ls = new Layer[d.readUnsignedByte()];
            for (int k = 0; k < ls.length; k++) {
                Layer l = new Layer();
                l.alpha = d.readUnsignedByte();
                l.stroke = d.readUnsignedByte() == 1;
                l.ops = new byte[d.readUnsignedShort()];
                d.readFully(l.ops);
                int np = 0;
                for (byte o : l.ops) np += o == M || o == L ? 1 : o == Q ? 2 : o == C ? 3 : 0;
                l.pts = new float[np * 2];
                int px = 0, py = 0;
                for (int j = 0; j < l.pts.length; j += 2) {
                    px += varint(d); py += varint(d);
                    l.pts[j] = px / 16f; l.pts[j + 1] = py / 16f;
                }
                ls[k] = l;
            }
            out[i] = ls;
        }
        return out;
    }

    private static int varint(DataInputStream d) throws IOException {
        int z = 0, shift = 0, b;
        do { b = d.readUnsignedByte(); z |= (b & 0x7F) << shift; shift += 7; } while ((b & 0x80) != 0);
        return (z >>> 1) ^ -(z & 1);
    }
}
