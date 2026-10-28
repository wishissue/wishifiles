import org.wishy.files.TransferServer; import java.io.*; import java.nio.file.*;
public class ServerMain { public static void main(String[] a) throws Exception {
  final String page = new String(Files.readAllBytes(Paths.get("/home/claude/wishi/assets/web/index.html")), "UTF-8");
  TransferServer s = new TransferServer(new TransferServer.Host() {
    public File recvDir() { return new File("/tmp/st/recv"); } public String device() { return "Test <TV>"; } public String page() { return page; }
    public String css() { return ":root{--p:#51a9e8}"; } public void onEvent(String m) { System.out.println("EVENT " + m); } public void onStopped(String w) { System.out.println("STOPPED " + w); System.exit(0); } }, "TESTTOKEN123");
  s.idleMs = Long.parseLong(a[0]); TransferServer.share(new File("/tmp/st/shared.bin"));
  System.out.println("PORT " + s.start()); Thread.sleep(600000); } }
