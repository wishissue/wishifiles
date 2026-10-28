package org.wishy.files;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tiny HTTP/1.1 server hosted on the TV. Pure java.net (no Android classes) so it is unit-testable on a desktop JVM.
 * Security: random per-session token (shown only in the TV's QR code), constant-time compare, per-IP lockout,
 * idle auto-stop, strict CSP with nonce, sanitised file names, uploads confined to one folder, downloads limited to
 * files the user explicitly shared (random ids, never paths).
 */
public final class TransferServer {
    public interface Host {
        File recvDir();
        String device();
        String page();
        String css();
        void onEvent(String msg);
        void onStopped(String why);
    }

    public static final class Shared {
        public final String id; public final File file;
        Shared(String id, File f) { this.id = id; this.file = f; }
    }

    public static final class Upload {
        public final String id, name; public final long size; final File part;
        public volatile long received; public volatile boolean done; public volatile String finalName; public volatile long touched;
        Upload(String id, String name, long size, File part) { this.id = id; this.name = name; this.size = size; this.part = part; touched = System.currentTimeMillis(); }
    }

    public static final CopyOnWriteArrayList<Shared> SHARED = new CopyOnWriteArrayList<Shared>();
    private static final SecureRandom RNG = new SecureRandom();
    private static final long MAX_CHUNK = 32L << 20;

    public static Shared share(File f) {
        for (Shared s : SHARED) if (s.file.equals(f)) return s;
        Shared s = new Shared(randomHex(8), f);
        SHARED.add(s);
        return s;
    }

    public static String randomHex(int bytes) {
        byte[] b = new byte[bytes]; RNG.nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x & 0xFF));
        return sb.toString();
    }

    private static String newToken() {
        byte[] b = new byte[16]; RNG.nextBytes(b);
        return android.util.Base64.encodeToString(b, android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
    }

    private final Host host;
    private ServerSocket ss;
    private ThreadPoolExecutor pool;
    private ScheduledExecutorService watchdog;
    public volatile String token;
    public volatile int port;
    public volatile boolean running;
    public volatile long idleMs = 10 * 60 * 1000L;
    public volatile long bytesIn, bytesOut;
    public final Set<String> clients = Collections.synchronizedSet(new LinkedHashSet<String>());
    private final Map<String, Upload> uploads = new ConcurrentHashMap<String, Upload>();
    private final Map<String, long[]> fails = new ConcurrentHashMap<String, long[]>();
    private final AtomicInteger active = new AtomicInteger();
    private volatile long lastActivity = System.currentTimeMillis();
    private final String tokenOverride;

    public TransferServer(Host h) { this(h, null); }
    /** tokenOverride is for unit tests on a desktop JVM, where android.util.Base64 is unavailable. */
    public TransferServer(Host h, String tokenOverride) { host = h; this.tokenOverride = tokenOverride; }

    public List<Upload> uploads() { return new ArrayList<Upload>(uploads.values()); }

    public int start() throws IOException {
        token = tokenOverride != null ? tokenOverride : newToken();
        ServerSocket s = null;
        for (int p = 8080; p < 8100 && s == null; p++) {
            try { s = new ServerSocket(); s.setReuseAddress(true); s.bind(new InetSocketAddress(p), 16); } catch (IOException e) { try { s.close(); } catch (Exception ignored) { } s = null; }
        }
        if (s == null) { s = new ServerSocket(0, 16); }
        ss = s; port = s.getLocalPort(); running = true; lastActivity = System.currentTimeMillis();
        pool = new ThreadPoolExecutor(8, 8, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(32));
        pool.allowCoreThreadTimeOut(true);
        cleanOldParts();
        Thread t = new Thread(new Runnable() { @Override public void run() { acceptLoop(); } }, "wishi-accept");
        t.setDaemon(true); t.start();
        watchdog = Executors.newSingleThreadScheduledExecutor();
        watchdog.scheduleWithFixedDelay(new Runnable() { @Override public void run() {
            if (running && active.get() == 0 && System.currentTimeMillis() - lastActivity > idleMs) stop("Sharing stopped after " + Math.max(1, idleMs / 60000) + " minutes of inactivity.");
        } }, 5, 5, TimeUnit.SECONDS);
        return port;
    }

    public void rotateToken() { token = tokenOverride != null ? tokenOverride + "2" : newToken(); clients.clear(); }

    public void stop(String why) {
        if (!running) return;
        running = false;
        try { ss.close(); } catch (Exception ignored) { }
        if (pool != null) pool.shutdownNow();
        if (watchdog != null) watchdog.shutdownNow();
        host.onStopped(why);
    }

    private void cleanOldParts() {
        try {
            File[] fs = host.recvDir().listFiles();
            if (fs == null) return;
            for (File f : fs) if (f.getName().endsWith(".wishipart") && System.currentTimeMillis() - f.lastModified() > 3L * 86400000L) f.delete();
        } catch (Exception ignored) { }
    }

    private void acceptLoop() {
        while (running) {
            try {
                final Socket c = ss.accept();
                try {
                    pool.execute(new Runnable() { @Override public void run() { handle(c); } });
                } catch (RejectedExecutionException e) { try { c.close(); } catch (Exception ignored) { } }
            } catch (IOException e) { if (running) { try { Thread.sleep(50); } catch (InterruptedException ignored) { } } }
        }
    }

    // ---------- HTTP plumbing ----------
    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') { int l = sb.length(); if (l > 0 && sb.charAt(l - 1) == '\r') sb.setLength(l - 1); return sb.toString(); }
            sb.append((char) c);
            if (sb.length() > 8192) throw new IOException("line too long");
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static Map<String, String> parseQuery(String q) {
        Map<String, String> m = new HashMap<String, String>();
        if (q == null || q.length() == 0) return m;
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            try {
                String k = URLDecoder.decode(i < 0 ? kv : kv.substring(0, i), "UTF-8");
                String v = i < 0 ? "" : URLDecoder.decode(kv.substring(i + 1), "UTF-8");
                m.put(k, v);
            } catch (Exception ignored) { }
        }
        return m;
    }

    private void send(OutputStream out, int code, String type, byte[] body, String extra) throws IOException {
        String msg = code == 200 ? "OK" : code == 206 ? "Partial Content" : code == 400 ? "Bad Request" : code == 401 ? "Unauthorized" : code == 404 ? "Not Found"
                : code == 409 ? "Conflict" : code == 416 ? "Range Not Satisfiable" : code == 429 ? "Too Many Requests" : code == 507 ? "Insufficient Storage" : "Error";
        StringBuilder h = new StringBuilder("HTTP/1.1 " + code + " " + msg + "\r\n");
        h.append("Content-Type: ").append(type).append("\r\nContent-Length: ").append(body.length).append("\r\n");
        h.append("Connection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nX-Frame-Options: DENY\r\n");
        if (extra != null) h.append(extra);
        h.append("\r\n");
        out.write(h.toString().getBytes("ISO-8859-1"));
        out.write(body);
        out.flush();
    }

    private void json(OutputStream out, int code, String body) throws IOException { send(out, code, "application/json; charset=utf-8", body.getBytes("UTF-8"), null); }

    private void err(OutputStream out, int code, String message) throws IOException { json(out, code, "{\"error\":" + q(message) + "}"); }

    static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.append('"').toString();
    }

    private boolean tokenOk(String t) {
        String cur = token;
        if (t == null || cur == null) return false;
        try { return MessageDigest.isEqual(t.getBytes("UTF-8"), cur.getBytes("UTF-8")); } catch (Exception e) { return false; }
    }

    private boolean banned(String ip) {
        long[] f = fails.get(ip);
        return f != null && f[2] > System.currentTimeMillis();
    }

    private void fail(String ip) {
        long now = System.currentTimeMillis();
        long[] f = fails.get(ip);
        if (f == null || now - f[1] > 60000) { f = new long[]{0, now, 0}; fails.put(ip, f); }
        if (++f[0] >= 10) { f[2] = now + 5 * 60000; host.onEvent("Blocked " + ip + " for 5 minutes after repeated wrong codes"); }
    }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(30000); s.setTcpNoDelay(true);
            InputStream in = new BufferedInputStream(s.getInputStream(), 32768);
            OutputStream out = new BufferedOutputStream(s.getOutputStream(), 32768);
            String line = readLine(in);
            if (line == null) return;
            String[] rl = line.split(" ");
            if (rl.length < 3) { err(out, 400, "Bad request"); return; }
            String method = rl[0], target = rl[1];
            Map<String, String> h = new HashMap<String, String>();
            String hl; int n = 0;
            while ((hl = readLine(in)) != null && hl.length() > 0) {
                if (++n > 64) { err(out, 400, "Too many headers"); return; }
                int i = hl.indexOf(':');
                if (i > 0) h.put(hl.substring(0, i).trim().toLowerCase(Locale.ROOT), hl.substring(i + 1).trim());
            }
            String path = target, query = "";
            int qi = target.indexOf('?');
            if (qi >= 0) { path = target.substring(0, qi); query = target.substring(qi + 1); }
            Map<String, String> qp = parseQuery(query);
            String ip = s.getInetAddress().getHostAddress();
            if (banned(ip)) { err(out, 429, "Too many wrong attempts. Try again in a few minutes."); return; }
            String t = h.get("x-wishi-token");
            if (t == null) t = qp.get("t");
            if (!tokenOk(t)) {
                fail(ip);
                send(out, 401, "text/html; charset=utf-8", ("<!doctype html><meta name=viewport content='width=device-width'><body style='font:16px system-ui;padding:2em;background:#111;color:#eee'><h2>Wishi Files</h2><p>Scan the QR code shown on your TV to connect.</p>").getBytes("UTF-8"), "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'\r\n");
                return;
            }
            lastActivity = System.currentTimeMillis();
            if (clients.add(ip)) host.onEvent("Connected: " + ip);
            if (method.equals("GET") && path.equals("/")) {
                String nonce = randomHex(12);
                String page = host.page().replace("{{NONCE}}", nonce).replace("{{CSS}}", host.css()).replace("{{DEVICE}}", htmlEsc(host.device()));
                send(out, 200, "text/html; charset=utf-8", page.getBytes("UTF-8"),
                        "Content-Security-Policy: default-src 'none'; script-src 'nonce-" + nonce + "'; style-src 'nonce-" + nonce + "'; connect-src 'self'; img-src data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'\r\n");
            } else if (method.equals("GET") && path.equals("/api/list")) {
                list(out);
            } else if (method.equals("POST") && path.equals("/api/upload")) {
                uploadInit(out, qp);
            } else if (method.equals("PUT") && path.startsWith("/api/upload/")) {
                uploadChunk(in, out, path.substring(12), qp, h);
            } else if (method.equals("GET") && path.startsWith("/api/dl/")) {
                download(out, path.substring(8), h);
            } else err(out, 404, "Not found");
        } catch (Exception e) {
            // client dropped or timed out: partial uploads are kept so the page can resume
        } finally {
            try { s.close(); } catch (Exception ignored) { }
        }
    }

    static String fmtSize(long b) { if (b < 1024) return b + " B"; double v = b / 1024.0; String[] u = {"KB", "MB", "GB", "TB"}; int i = 0; while (v >= 1024 && i < 3) { v /= 1024; i++; } return String.format(Locale.US, v >= 100 ? "%.0f %s" : "%.1f %s", v, u[i]); }

    static String htmlEsc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

    private void list(OutputStream out) throws IOException {
        StringBuilder sb = new StringBuilder("{\"device\":" + q(host.device()) + ",\"free\":" + host.recvDir().getUsableSpace() + ",\"shared\":[");
        boolean first = true;
        for (Shared s : SHARED) {
            if (!s.file.isFile()) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"id\":\"").append(s.id).append("\",\"name\":").append(q(s.file.getName())).append(",\"size\":").append(s.file.length()).append('}');
        }
        json(out, 200, sb.append("]}").toString());
    }

    static String sanitize(String name) {
        if (name == null) return "file";
        name = name.replaceAll("[\\\\/:*?\"<>|\\u0000-\\u001f]", "_").trim();
        while (name.startsWith(".")) name = "_" + name.substring(1);
        if (name.length() > 150) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 12 ? name.substring(dot) : "";
            name = name.substring(0, 150 - ext.length()) + ext;
        }
        return name.length() == 0 ? "file" : name;
    }

    static File unique(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 10000; i++) { f = new File(dir, base + " (" + i + ")" + ext); if (!f.exists()) return f; }
        return new File(dir, base + "-" + randomHex(3) + ext);
    }

    private void uploadInit(OutputStream out, Map<String, String> qp) throws IOException {
        String id = qp.get("id"), name = sanitize(qp.get("name"));
        long size;
        try { size = Long.parseLong(qp.get("size")); } catch (Exception e) { size = -1; }
        if (id == null || !id.matches("[a-f0-9]{8,32}") || size < 0) { err(out, 400, "Bad upload request"); return; }
        File dir = host.recvDir();
        if (!dir.isDirectory()) dir.mkdirs();
        if (!dir.isDirectory() || !dir.canWrite()) { err(out, 500, "The TV can't write to its receive folder. Open Wishi Files on the TV and allow storage access."); return; }
        Upload u = uploads.get(id);
        if (u == null || u.size != size || u.done) { u = new Upload(id, name, size, new File(dir, "." + id + ".wishipart")); uploads.put(id, u); }
        synchronized (u) {
            long have = u.part.exists() ? u.part.length() : 0;
            if (have > size) { u.part.delete(); have = 0; }
            if (size - have > dir.getUsableSpace() - (8L << 20)) {
                err(out, 507, "Not enough free space on the TV: needs " + fmtSize(size - have) + ", has " + fmtSize(dir.getUsableSpace()) + "."); return;
            }
            u.received = have; u.touched = System.currentTimeMillis();
            if (size == 0 || have == size) { if (!u.part.exists()) u.part.createNewFile(); finish(u); }
            json(out, 200, "{\"id\":\"" + id + "\",\"offset\":" + have + ",\"done\":" + u.done + ",\"name\":" + q(u.finalName == null ? u.name : u.finalName) + "}");
        }
    }

    private void finish(Upload u) throws IOException {
        File dest = unique(u.part.getParentFile(), u.name);
        if (!u.part.renameTo(dest)) throw new IOException("rename failed");
        u.finalName = dest.getName(); u.done = true;
        host.onEvent("Received " + dest.getName());
    }

    private void uploadChunk(InputStream in, OutputStream out, String id, Map<String, String> qp, Map<String, String> h) throws IOException {
        Upload u = uploads.get(id);
        long len;
        try { len = Long.parseLong(h.get("content-length")); } catch (Exception e) { err(out, 400, "Content-Length required"); return; }
        if (u == null) { drain(in, len); err(out, 404, "Unknown upload. Please retry the file."); return; }
        long off;
        try { off = Long.parseLong(qp.get("offset")); } catch (Exception e) { drain(in, len); err(out, 400, "offset required"); return; }
        if (len < 0 || len > MAX_CHUNK || off < 0 || off + len > u.size) { drain(in, Math.min(len, MAX_CHUNK)); err(out, 400, "Bad chunk range"); return; }
        active.incrementAndGet();
        try {
            synchronized (u) {
                long cur = u.part.exists() ? u.part.length() : 0;
                if (u.done || off != cur) { drain(in, len); json(out, 409, "{\"offset\":" + cur + ",\"done\":" + u.done + "}"); return; }
                RandomAccessFile raf = new RandomAccessFile(u.part, "rw");
                try {
                    raf.seek(off);
                    byte[] buf = new byte[65536];
                    long left = len;
                    while (left > 0) {
                        int r = in.read(buf, 0, (int) Math.min(buf.length, left));
                        if (r < 0) throw new EOFException();
                        raf.write(buf, 0, r);
                        left -= r; u.received = off + (len - left); bytesIn += r; u.touched = lastActivity = System.currentTimeMillis();
                    }
                } finally { raf.close(); }
                if (u.part.length() == u.size) finish(u);
                json(out, 200, "{\"offset\":" + u.part.length() + ",\"done\":" + u.done + ",\"name\":" + q(u.finalName == null ? u.name : u.finalName) + "}");
            }
        } finally { active.decrementAndGet(); }
    }

    private static void drain(InputStream in, long n) throws IOException {
        byte[] b = new byte[16384];
        while (n > 0) { int r = in.read(b, 0, (int) Math.min(b.length, n)); if (r < 0) return; n -= r; }
    }

    private void download(OutputStream out, String id, Map<String, String> h) throws IOException {
        Shared sh = null;
        for (Shared s : SHARED) if (s.id.equals(id)) sh = s;
        if (sh == null || !sh.file.isFile() || !sh.file.canRead()) { err(out, 404, "That file is no longer shared."); return; }
        long size = sh.file.length(), a = 0, b = size - 1; boolean partial = false;
        String rg = h.get("range");
        if (rg != null && rg.startsWith("bytes=") && rg.indexOf(',') < 0) {
            try {
                String[] p = rg.substring(6).split("-", -1);
                if (p[0].length() == 0) { long nlast = Long.parseLong(p[1]); a = Math.max(0, size - nlast); }
                else { a = Long.parseLong(p[0]); if (p[1].length() > 0) b = Math.min(b, Long.parseLong(p[1])); }
                partial = true;
            } catch (Exception e) { a = 0; b = size - 1; partial = false; }
            if (partial && (a > b || a >= size)) { send(out, 416, "text/plain", new byte[0], "Content-Range: bytes */" + size + "\r\n"); return; }
        }
        String fn = URLEncoder.encode(sh.file.getName(), "UTF-8").replace("+", "%20");
        long len = b - a + 1;
        String head = "HTTP/1.1 " + (partial ? "206 Partial Content" : "200 OK") + "\r\nContent-Type: application/octet-stream\r\nContent-Length: " + len
                + "\r\nAccept-Ranges: bytes\r\n" + (partial ? "Content-Range: bytes " + a + "-" + b + "/" + size + "\r\n" : "")
                + "Content-Disposition: attachment; filename*=UTF-8''" + fn + "\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n\r\n";
        out.write(head.getBytes("ISO-8859-1"));
        active.incrementAndGet();
        RandomAccessFile raf = new RandomAccessFile(sh.file, "r");
        try {
            raf.seek(a);
            byte[] buf = new byte[65536]; long left = len;
            while (left > 0) {
                int r = raf.read(buf, 0, (int) Math.min(buf.length, left));
                if (r < 0) break;
                out.write(buf, 0, r); left -= r; bytesOut += r; lastActivity = System.currentTimeMillis();
            }
            out.flush();
            if (a == 0 && left == 0) host.onEvent("Sent " + sh.file.getName());
        } finally { raf.close(); active.decrementAndGet(); }
    }

    // ---------- network helpers ----------
    /** Best local IPv4 address (Ethernet/Wi-Fi first), or null when offline. */
    public static String localIp() {
        String best = null; int bestScore = -1;
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                String nm = ni.getName();
                if (nm.startsWith("rmnet") || nm.startsWith("tun") || nm.startsWith("dummy") || nm.startsWith("p2p")) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || !a.isSiteLocalAddress()) continue;
                    int sc = nm.startsWith("eth") ? 3 : nm.startsWith("wlan") ? 2 : 1;
                    if (sc > bestScore) { bestScore = sc; best = a.getHostAddress(); }
                }
            }
        } catch (Exception ignored) { }
        return best;
    }
}
