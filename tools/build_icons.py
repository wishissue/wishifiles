#!/usr/bin/env python3
"""Builds assets/icons.bin from Material Symbols (Rounded, weight 400, Apache-2.0, https://fonts.google.com/icons).

    npm i @material-symbols/svg-400     (in any scratch dir)
    python3 tools/build_icons.py <path to node_modules/@material-symbols/svg-400/rounded> [out.bin]

Each icon is one filled layer on a 256 grid (Material's 960 grid is rescaled). Filled glyphs use the "-fill" file, outline glyphs the plain one.
Paths are flattened to absolute M/L/Q/C/Z and written as zig-zag varint deltas in 1/16 units, so the app needs no SVG parser at runtime.

File format:  u16 iconCount, then per icon: u8 layers, then per layer: u8 alpha, u16 opCount, opCount op bytes
(0=M 1=L 2=Q 3=C 4=Z), then varint coordinates for all ops in order.
"""
import math, re, struct, sys, os, xml.etree.ElementTree as ET

# (name used by Icons.java, material symbol, filled?)  -- ORDER MUST MATCH the id constants in Icons.java
SPEC = [
    ("FOLDER", "folder", 1), ("FILE", "draft", 1), ("IMAGE", "image", 1), ("VIDEO", "movie", 1), ("AUDIO", "music_note", 1),
    ("APK", "android", 1), ("DOC", "description", 1), ("HTML", "html", 0), ("ZIP", "folder_zip", 1), ("PDF", "picture_as_pdf", 1),
    ("USB", "usb", 0), ("TV", "tv", 1), ("SEARCH", "search", 0), ("SORT", "sort", 0), ("NEWFOLDER", "create_new_folder", 1),
    ("BACK", "arrow_back", 0), ("SWAP", "swap_horiz", 0), ("INSTALL", "download", 0), ("TUNE", "palette", 1), ("INFO", "info", 1),
    ("HEART", "favorite", 1), ("SHIELD", "verified_user", 1), ("CODE", "code", 0), ("GLOBE", "public", 0), ("CHECK", "check", 0),
    ("COPY", "content_copy", 1), ("CUT", "content_cut", 0), ("PASTE", "content_paste", 1), ("TRASH", "delete", 1), ("EDIT", "edit", 1),
    ("SHARE", "share", 1), ("WIFI", "wifi", 0), ("REFRESH", "refresh", 0), ("CLOSE", "close", 0), ("MORE", "more_vert", 0),
    ("PLAY", "play_arrow", 1), ("UP", "arrow_upward", 0), ("HOME", "home", 1), ("SELECT", "checklist", 0), ("STOP", "stop", 1),
    ("UPLOAD", "upload", 0), ("CHEVRON", "chevron_right", 0), ("PAUSE", "pause", 1), ("PREV", "skip_previous", 1), ("NEXT", "skip_next", 1),
    ("WARN", "warning", 1), ("CHECKED", "check_circle", 1), ("ERROR", "error", 1), ("EYE", "visibility", 1), ("EYE_OFF", "visibility_off", 1),
    ("STORAGE", "hard_drive", 1), ("FOLDER_OPEN", "folder_open", 1), ("CPU", "memory", 1), ("PACKAGE", "inventory_2", 1),
    ("GRID", "grid_view", 1), ("LIST", "view_list", 1), ("GEAR", "settings", 1),
    # 1.3 additions
    ("FOLDER_LINE", "folder", 0), ("DOWNLOADS", "download", 0), ("PICTURES", "photo_library", 1), ("MOVIES", "video_library", 1),
    ("MUSIC", "library_music", 1), ("DOCUMENTS", "article", 1), ("INBOX", "move_to_inbox", 1), ("QR", "qr_code_2", 0),
    ("LINK", "link", 0), ("DEVICES", "devices", 1), ("HISTORY", "history", 0), ("STAR", "star", 1), ("ADD", "add", 0),
    ("SD", "sd_card", 1), ("WIFI_OFF", "wifi_off", 0), ("POWER", "power_settings_new", 0), ("LOCK", "lock", 1), ("DARK", "dark_mode", 1),
    ("CONTRAST", "contrast", 0), ("AUDIOFILE", "audio_file", 1), ("VIDEOFILE", "video_file", 1), ("SPECIAL", "folder_special", 1),
    ("INSTALL_FILL", "download", 1), ("SYNC", "sync_alt", 0), ("UPLOAD_CLOUD", "cloud_upload", 1), ("DOWNLOAD_CLOUD", "cloud_download", 1),
    ("SELECT_ALL", "select_all", 0), ("MOVE", "drive_file_move", 1), ("APPS", "apps", 0), ("OPEN_NEW", "open_in_new", 0),
    ("DONE_ALL", "done_all", 0), ("SCHEDULE", "schedule", 0), ("BOLT", "bolt", 1), ("EXPAND", "keyboard_arrow_down", 0),
]

TOK = re.compile(r"([MmLlHhVvCcSsQqTtAaZz])|(-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?)")


def arc_to_cubics(x1, y1, rx, ry, phi, fa, fs, x2, y2):
    if rx == 0 or ry == 0:
        return [("L", x2, y2)]
    rx, ry = abs(rx), abs(ry)
    cp, sp = math.cos(math.radians(phi)), math.sin(math.radians(phi))
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p, y1p = cp * dx + sp * dy, -sp * dx + cp * dy
    lam = x1p ** 2 / rx ** 2 + y1p ** 2 / ry ** 2
    if lam > 1:
        s = math.sqrt(lam); rx *= s; ry *= s
    num = rx ** 2 * ry ** 2 - rx ** 2 * y1p ** 2 - ry ** 2 * x1p ** 2
    den = rx ** 2 * y1p ** 2 + ry ** 2 * x1p ** 2
    co = math.sqrt(max(0, num / den)) * (-1 if fa == fs else 1)
    cxp, cyp = co * rx * y1p / ry, -co * ry * x1p / rx
    cx, cy = cp * cxp - sp * cyp + (x1 + x2) / 2, sp * cxp + cp * cyp + (y1 + y2) / 2

    def ang(ux, uy, vx, vy):
        a = math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        return a
    t1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not fs and dt > 0: dt -= 2 * math.pi
    if fs and dt < 0: dt += 2 * math.pi
    n = max(1, int(math.ceil(abs(dt) / (math.pi / 2) - 1e-9)))
    d = dt / n
    k = 4 / 3 * math.tan(d / 4)
    out = []
    for i in range(n):
        a1, a2 = t1 + i * d, t1 + (i + 1) * d
        def pt(a):
            return (cx + rx * math.cos(a) * cp - ry * math.sin(a) * sp, cy + rx * math.cos(a) * sp + ry * math.sin(a) * cp)
        def dv(a):
            return (-rx * math.sin(a) * cp - ry * math.cos(a) * sp, -rx * math.sin(a) * sp + ry * math.cos(a) * cp)
        p1, p2 = pt(a1), pt(a2); d1, d2 = dv(a1), dv(a2)
        out.append(("C", p1[0] + k * d1[0], p1[1] + k * d1[1], p2[0] - k * d2[0], p2[1] - k * d2[1], p2[0], p2[1]))
    return out


def parse_path(d):
    toks = [(m.group(1), m.group(2)) for m in TOK.finditer(d)]
    i = 0; cmd = None; x = y = sx = sy = 0.0; lastc = None; out = []; prev = None

    def num():
        nonlocal i
        v = float(toks[i][1]); i += 1; return v

    def flag():  # arc flags may be glued: "a8,8,0,0,1-8" or "0 01"
        nonlocal i
        t = toks[i][1]
        if len(t) > 1 and t[0] in "01" and not t.startswith(("0.", "1.")):
            toks[i] = (None, t[1:]); return float(t[0])
        i += 1; return float(t)

    while i < len(toks):
        if toks[i][0]:
            cmd = toks[i][0]; i += 1
            if cmd in "Zz":
                out.append(("Z",)); x, y = sx, sy; prev = "Z"; continue
        rel = cmd.islower(); c = cmd.upper()
        ox, oy = (x, y) if rel else (0, 0)
        if c == "M":
            x, y = num() + ox, num() + oy; sx, sy = x, y; out.append(("M", x, y)); cmd = "l" if rel else "L"; prev = "M"
        elif c == "L":
            x, y = num() + ox, num() + oy; out.append(("L", x, y)); prev = "L"
        elif c == "H":
            x = num() + (x if rel else 0); out.append(("L", x, y)); prev = "L"
        elif c == "V":
            y = num() + (y if rel else 0); out.append(("L", x, y)); prev = "L"
        elif c == "C":
            a = [num() for _ in range(6)]
            p = (a[0] + ox, a[1] + oy, a[2] + ox, a[3] + oy, a[4] + ox, a[5] + oy)
            out.append(("C",) + p); lastc = (p[2], p[3]); x, y = p[4], p[5]; prev = "C"
        elif c == "S":
            a = [num() for _ in range(4)]
            c1 = (2 * x - lastc[0], 2 * y - lastc[1]) if prev == "C" else (x, y)
            p = c1 + (a[0] + ox, a[1] + oy, a[2] + ox, a[3] + oy)
            out.append(("C",) + p); lastc = (p[2], p[3]); x, y = p[4], p[5]; prev = "C"
        elif c == "Q":
            a = [num() for _ in range(4)]
            p = (a[0] + ox, a[1] + oy, a[2] + ox, a[3] + oy)
            out.append(("Q",) + p); lastc = (p[0], p[1]); x, y = p[2], p[3]; prev = "Q"
        elif c == "T":
            a = [num() for _ in range(2)]
            c1 = (2 * x - lastc[0], 2 * y - lastc[1]) if prev == "Q" else (x, y)
            p = c1 + (a[0] + ox, a[1] + oy)
            out.append(("Q",) + p); lastc = c1; x, y = p[2], p[3]; prev = "Q"
        elif c == "A":
            rx, ry, phi = num(), num(), num(); fa = flag(); fs = flag(); ex, ey = num() + ox, num() + oy
            out += arc_to_cubics(x, y, rx, ry, phi, int(fa), int(fs), ex, ey); x, y = ex, ey; prev = "A"
        else:
            raise ValueError(cmd)
    return out


def varint(n):
    z = (n << 1) ^ (n >> 63); b = bytearray()
    while True:
        c = z & 0x7F; z >>= 7
        if z: b.append(c | 0x80)
        else: b.append(c); return bytes(b)


OPN = {"M": 0, "L": 1, "Q": 2, "C": 3, "Z": 4}
Q = 16  # fixed point


def encode(ls):
    out = bytearray([len(ls)])
    for alpha, ops in ls:
        out += bytes([alpha]) + struct.pack(">H", len(ops)) + bytes(OPN[o[0]] for o in ops)
        px = py = 0
        for o in ops:
            v = o[1:]
            for j in range(0, len(v), 2):
                nx, ny = round(v[j] * Q), round(v[j + 1] * Q)
                out += varint(nx - px) + varint(ny - py); px, py = nx, ny
    return bytes(out)



def layers(svgfile):
    root = ET.parse(svgfile).getroot()
    out = []
    for el in root:
        if el.tag.split("}")[1] != "path": raise ValueError("unsupported element in " + svgfile)
        for o in parse_path(el.get("d")):
            if o[0] == "Z": out.append(o); continue
            v = o[1:]; pts = []
            for j in range(0, len(v), 2): pts += [v[j] * 256 / 960, (v[j + 1] + 960) * 256 / 960]
            out.append((o[0],) + tuple(pts))
    return [(255, out)]


def main():
    src = sys.argv[1]; dst = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), "..", "assets", "icons.bin")
    blob = struct.pack(">H", len(SPEC))
    for name, sym, fill in SPEC:
        f = os.path.join(src, sym + ("-fill" if fill else "") + ".svg")
        blob += encode(layers(f))
    open(dst, "wb").write(blob)
    print("icons:", len(SPEC), "bytes:", len(blob), "->", dst)
    for i, (name, sym, fill) in enumerate(SPEC): print("%s=%d" % (name, i), end=", ")
    print()


if __name__ == "__main__":
    main()
