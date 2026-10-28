import org.wishy.files.Apk; import java.io.File; import java.util.*;
public class ApkMain { public static void main(String[] a) { Apk.Dev d = new Apk.Dev(); d.sdk = Integer.parseInt(a[1]); d.release = Apk.androidName(d.sdk); d.abis = a[2].split(","); d.freeBytes = Long.parseLong(a[3]);
  if (a.length > 4) { d.installedCode = Long.parseLong(a[4]); d.installedName = "9.9"; d.sig = Integer.parseInt(a[5]); }
  Apk k = Apk.inspect(new File(a[0]), new File("/tmp/at/cache"));
  System.out.println("pkg=" + k.pkg + " vc=" + k.versionCode + " vn=" + k.versionName + " min=" + k.minSdk + " tgt=" + k.targetSdk + " max=" + k.maxSdk + " perms=" + k.permissions + " split=" + k.splitName + " abis=" + k.abis + " bundleAbis=" + k.bundleAbis + " failed=" + k.parseFailed + " " + k.parseError + " testOnly=" + k.testOnly);
  for (Apk.Msg m : k.check(d)) System.out.println("  [" + m.level + "] " + m.title); if (k.bundle) System.out.println("  select=" + k.selectEntries(d.abis)); } }
