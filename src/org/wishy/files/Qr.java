package org.wishy.files;

import java.io.UnsupportedEncodingException;

/** Minimal QR Code encoder: byte mode, error correction M, versions 1-10 (up to 213 bytes). Written from ISO/IEC 18004. */
public final class Qr {
    private static final int[] TOTAL = {26, 44, 70, 100, 134, 172, 196, 242, 292, 346};
    private static final int[] DATA = {16, 28, 44, 64, 86, 108, 124, 154, 182, 216};
    private static final int[] BLOCKS = {1, 1, 1, 2, 2, 4, 4, 4, 5, 5};
    private static final int[] ECB = {10, 16, 26, 18, 24, 16, 18, 22, 22, 26};
    private static final int[][] ALIGN = {{}, {6, 18}, {6, 22}, {6, 26}, {6, 30}, {6, 34}, {6, 22, 38}, {6, 24, 42}, {6, 26, 46}, {6, 28, 50}};
    private static final int[] EXP = new int[512], LOG = new int[256];

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) { EXP[i] = x; LOG[x] = i; x <<= 1; if (x >= 256) x ^= 0x11D; }
        for (int i = 255; i < 512; i++) EXP[i] = EXP[i - 255];
    }

    public final int n;
    private final boolean[][] m;
    private final boolean[][] fn;

    public boolean get(int x, int y) { return x >= 0 && y >= 0 && x < n && y < n && m[y][x]; }

    private Qr(int v) { n = 17 + 4 * v; m = new boolean[n][n]; fn = new boolean[n][n]; }

    public static Qr encode(String text) {
        byte[] b;
        try { b = text.getBytes("UTF-8"); } catch (UnsupportedEncodingException e) { throw new RuntimeException(e); }
        int v = 0;
        for (int i = 0; i < 10; i++) {
            int cc = i < 9 ? 8 : 16;
            if (DATA[i] * 8 >= 4 + cc + 8 * b.length) { v = i + 1; break; }
        }
        if (v == 0) throw new IllegalArgumentException("text too long for QR v10");
        int cap = DATA[v - 1];
        int[] bits = new int[cap * 8 + 64]; int nb = 0;
        nb = put(bits, nb, 4, 4);
        nb = put(bits, nb, b.length, v < 10 ? 8 : 16);
        for (byte x : b) nb = put(bits, nb, x & 0xFF, 8);
        nb = put(bits, nb, 0, Math.min(4, cap * 8 - nb));
        while (nb % 8 != 0) bits[nb++] = 0;
        int[] cw = new int[cap];
        for (int i = 0; i < nb / 8; i++) for (int k = 0; k < 8; k++) cw[i] = (cw[i] << 1) | bits[i * 8 + k];
        for (int i = nb / 8, pad = 0; i < cap; i++, pad ^= 1) cw[i] = pad == 0 ? 0xEC : 0x11;
        // blocks + Reed-Solomon
        int nbk = BLOCKS[v - 1], ecl = ECB[v - 1], shortLen = cap / nbk, nLong = cap % nbk;
        int[][] dat = new int[nbk][], ec = new int[nbk][];
        int[] gen = generator(ecl);
        for (int i = 0, p = 0; i < nbk; i++) {
            int len = shortLen + (i >= nbk - nLong ? 1 : 0);
            dat[i] = new int[len];
            System.arraycopy(cw, p, dat[i], 0, len); p += len;
            ec[i] = rs(dat[i], gen, ecl);
        }
        int[] all = new int[TOTAL[v - 1]]; int w = 0;
        for (int i = 0; i <= shortLen; i++) for (int j = 0; j < nbk; j++) if (i < dat[j].length) all[w++] = dat[j][i];
        for (int i = 0; i < ecl; i++) for (int j = 0; j < nbk; j++) all[w++] = ec[j][i];

        Qr q = new Qr(v);
        q.drawFunction(v);
        q.place(all);
        int best = -1, bestPen = Integer.MAX_VALUE;
        for (int mask = 0; mask < 8; mask++) {
            q.applyMask(mask); q.drawFormat(mask);
            int pen = q.penalty();
            if (pen < bestPen) { bestPen = pen; best = mask; }
            q.applyMask(mask); // undo (xor)
        }
        q.applyMask(best); q.drawFormat(best);
        return q;
    }

    private static int put(int[] bits, int pos, int val, int len) {
        for (int i = len - 1; i >= 0; i--) bits[pos++] = (val >> i) & 1;
        return pos;
    }

    private static int mul(int a, int b) { return a == 0 || b == 0 ? 0 : EXP[LOG[a] + LOG[b]]; }

    private static int[] generator(int deg) {
        int[] g = new int[deg + 1]; g[0] = 1;
        for (int i = 0; i < deg; i++) {
            for (int j = deg; j >= 0; j--) g[j] = mul(g[j], EXP[i]) ^ (j > 0 ? g[j - 1] : 0);
        }
        return g; // g[0] is the constant term
    }

    private static int[] rs(int[] data, int[] gen, int deg) {
        int[] r = new int[deg];
        for (int d : data) {
            int f = d ^ r[0];
            System.arraycopy(r, 1, r, 0, deg - 1); r[deg - 1] = 0;
            for (int j = 0; j < deg; j++) r[j] ^= mul(gen[deg - 1 - j], f);
        }
        return r;
    }

    private void set(int x, int y, boolean dark) { m[y][x] = dark; fn[y][x] = true; }

    private void drawFunction(int v) {
        for (int i = 0; i < n; i++) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0); }
        finder(3, 3); finder(n - 4, 3); finder(3, n - 4);
        int[] a = ALIGN[v - 1];
        for (int i = 0; i < a.length; i++) for (int j = 0; j < a.length; j++) {
            if ((i == 0 && j == 0) || (i == 0 && j == a.length - 1) || (i == a.length - 1 && j == 0)) continue;
            for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++)
                set(a[i] + dx, a[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
        }
        drawFormat(0);
        if (v >= 7) {
            int rem = v;
            for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >> 11) * 0x1F25);
            int bits = v << 12 | rem;
            for (int i = 0; i < 18; i++) {
                boolean bit = ((bits >> i) & 1) != 0;
                int p = n - 11 + i % 3, q = i / 3;
                set(p, q, bit); set(q, p, bit);
            }
        }
    }

    private void finder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) for (int dx = -4; dx <= 4; dx++) {
            int x = cx + dx, y = cy + dy;
            if (x < 0 || y < 0 || x >= n || y >= n) continue;
            int d = Math.max(Math.abs(dx), Math.abs(dy));
            set(x, y, d != 2 && d != 4);
        }
    }

    private void drawFormat(int mask) {
        int data = mask; // EC level M = 00
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >> 9) * 0x537);
        int bits = ((data << 10) | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) set(8, i, bit(bits, i));
        set(8, 7, bit(bits, 6)); set(8, 8, bit(bits, 7)); set(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(n - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(8, n - 15 + i, bit(bits, i));
        set(8, n - 8, true);
    }

    private static boolean bit(int v, int i) { return ((v >> i) & 1) != 0; }

    private void place(int[] cw) {
        int i = 0, total = cw.length * 8;
        for (int right = n - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < n; vert++) for (int j = 0; j < 2; j++) {
                int x = right - j;
                boolean up = ((right + 1) & 2) == 0;
                int y = up ? n - 1 - vert : vert;
                if (!fn[y][x] && i < total) { m[y][x] = ((cw[i >> 3] >> (7 - (i & 7))) & 1) != 0; i++; }
            }
        }
    }

    private void applyMask(int mk) {
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            if (fn[y][x]) continue;
            boolean f;
            switch (mk) {
                case 0: f = (x + y) % 2 == 0; break;
                case 1: f = y % 2 == 0; break;
                case 2: f = x % 3 == 0; break;
                case 3: f = (x + y) % 3 == 0; break;
                case 4: f = (x / 3 + y / 2) % 2 == 0; break;
                case 5: f = x * y % 2 + x * y % 3 == 0; break;
                case 6: f = (x * y % 2 + x * y % 3) % 2 == 0; break;
                default: f = ((x + y) % 2 + x * y % 3) % 2 == 0;
            }
            if (f) m[y][x] = !m[y][x];
        }
    }

    private int penalty() {
        int p = 0, dark = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int a = 0; a < n; a++) {
                int run = 1; int hist = 0; // hist: last 11 modules bits
                for (int b = 0; b < n; b++) {
                    boolean c = pass == 0 ? m[a][b] : m[b][a];
                    if (pass == 0 && c) dark++;
                    if (b > 0) {
                        boolean pc = pass == 0 ? m[a][b - 1] : m[b - 1][a];
                        if (c == pc) { run++; if (run == 5) p += 3; else if (run > 5) p++; } else run = 1;
                    }
                    hist = ((hist << 1) | (c ? 1 : 0)) & 0x7FF;
                                        if (b >= 10 && (hist == 0b00001011101 || hist == 0b10111010000)) p += 40;
                }
            }
        }
        for (int y = 0; y < n - 1; y++) for (int x = 0; x < n - 1; x++) {
            boolean c = m[y][x];
            if (c == m[y][x + 1] && c == m[y + 1][x] && c == m[y + 1][x + 1]) p += 3;
        }
        int tot = n * n;
        int k = (Math.abs(dark * 20 - tot * 10) + tot - 1) / tot - 1;
        return p + Math.max(k, 0) * 10;
    }
}
