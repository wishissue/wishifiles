#!/usr/bin/env python3
"""Assemble the final APK: patch manifest attr id (restoreAnyVersion placeholder -> requestLegacyExternalStorage,
needed because the apt android.jar is API 23), add classes.dex. resources.arsc stays STORED (required for targetSdk>=30)."""
import struct, sys, zipfile
base, dex, out = sys.argv[1:4]
OLD, NEW = 0x010102ba, 0x01010603   # restoreAnyVersion -> requestLegacyExternalStorage
zin = zipfile.ZipFile(base)
man = bytearray(zin.read("AndroidManifest.xml"))
pos, hit = 8, 0
while pos < len(man):
    t, hs, sz = struct.unpack_from("<HHI", man, pos)
    if t == 0x0180:  # RES_XML_RESOURCE_MAP_TYPE
        for o in range(pos + hs, pos + sz, 4):
            if struct.unpack_from("<I", man, o)[0] == OLD:
                struct.pack_into("<I", man, o, NEW); hit += 1
    pos += sz
assert hit == 1, "placeholder attribute not found (%d)" % hit
with zipfile.ZipFile(out, "w") as z:
    for i in zin.infolist():
        data = bytes(man) if i.filename == "AndroidManifest.xml" else zin.read(i.filename)
        z.writestr(i.filename, data, zipfile.ZIP_STORED if i.filename == "resources.arsc" else zipfile.ZIP_DEFLATED)
    z.write(dex, "classes.dex", zipfile.ZIP_DEFLATED)
print("manifest patched")
