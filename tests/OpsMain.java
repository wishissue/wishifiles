import org.wishy.files.FileOps; import java.io.*; import java.util.*;
public class OpsMain { static void ok(String n, boolean c) { System.out.println((c ? "PASS " : "FAIL ") + n); }
 public static void main(String[] a) throws Exception {
  File r = new File("/tmp/at/ops"); if (r.exists()) FileOps.delete(r); r.mkdirs();
  File d = new File(r, "src/sub"); d.mkdirs(); try (FileOutputStream o = new FileOutputStream(new File(d, "a.bin"))) { o.write(new byte[300000]); } new File(r, "src/b.txt").createNewFile(); new File(r, "dst").mkdirs();
  File c1 = FileOps.copy(new File(r, "src"), new File(r, "dst"), null); ok("copy tree", new File(c1, "sub/a.bin").length() == 300000 && new File(c1, "b.txt").exists());
  File c2 = FileOps.copy(new File(r, "src"), new File(r, "dst"), null); ok("unique name on collision: " + c2.getName(), c2.getName().equals("src (1)"));
  try { FileOps.copy(new File(r, "src"), new File(r, "src/sub"), null); ok("copy into itself blocked", false); } catch (IOException e) { ok("copy into itself blocked", true); }
  try { FileOps.move(new File(r, "src"), new File(r, "src/sub"), null); ok("move into itself blocked", false); } catch (IOException e) { ok("move into itself blocked", true); }
  File m = FileOps.move(c2, new File(r, "src"), null); ok("move", m.isDirectory() && !c2.exists() && new File(m, "sub/a.bin").exists());
  final int[] calls = {0}; try { FileOps.copy(new File(r, "src/sub/a.bin"), new File(r, "dst"), new FileOps.Progress() { public void bytes(long x, String s) { calls[0]++; } public boolean cancelled() { return calls[0] > 0; } }); ok("cancel", false); } catch (IOException e) { ok("cancel stops copy & cleans partial: " + e.getMessage() + " " + new File(r, "dst/a.bin").exists(), e.getMessage().equals("Cancelled") && !new File(r, "dst/a.bin").exists()); }
  FileOps.delete(c1); ok("delete tree", !c1.exists());
  ArrayList<File> res = new ArrayList<>(); FileOps.search(r, "A.BIN", false, 0, res, 100, new boolean[1]); ok("recursive case-insens search: " + res.size(), res.size() >= 2);
  File[] l = FileOps.list(new File(r, "src"), false, FileOps.S_NAME, false); ok("folders first: " + l[0].getName(), l[0].isDirectory());
  ok("types", FileOps.type(new File("x.APK")) == FileOps.T_APK && FileOps.type(new File("x.xapk")) == FileOps.T_APK && FileOps.type(new File("a.mkv")) == FileOps.T_VIDEO && FileOps.type(new File("a.html")) == FileOps.T_HTML && FileOps.type(new File("a.pdf")) == FileOps.T_PDF && FileOps.type(new File("a.7z")) == FileOps.T_ZIP);
 } }
