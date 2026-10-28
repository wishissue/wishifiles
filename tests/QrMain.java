import org.wishy.files.Qr;
public class QrMain { public static void main(String[] a) { for (String s : a) { Qr q = Qr.encode(s); StringBuilder sb = new StringBuilder(); for (int y=0;y<q.n;y++){ for(int x=0;x<q.n;x++) sb.append(q.get(x,y)?'1':'0'); sb.append('\n'); } System.out.println("#"+s); System.out.print(sb); } } }
