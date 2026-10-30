package org.wishy.files;

import java.io.*;

public class IconDataTest {
    public static void run(String bin, String outDir) throws Exception {
        IconData.Layer[][] icons = IconData.read(new FileInputStream(bin));
        new File(outDir).mkdirs();
        for (int i = 0; i < icons.length; i++) {
            StringBuilder sb = new StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 256 256' fill='#000'>");
            for (IconData.Layer l : icons[i]) {
                sb.append("<path opacity='").append(l.alpha / 255f).append(l.stroke ? "' fill='none' stroke='#000' stroke-width='21.333' stroke-linecap='round' stroke-linejoin='round" : "").append("' d='");
                int j = 0;
                for (byte op : l.ops) {
                    switch (op) {
                        case IconData.M: sb.append("M").append(l.pts[j]).append(' ').append(l.pts[j + 1]); j += 2; break;
                        case IconData.L: sb.append("L").append(l.pts[j]).append(' ').append(l.pts[j + 1]); j += 2; break;
                        case IconData.Q: sb.append("Q"); for (int k = 0; k < 4; k++) sb.append(l.pts[j + k]).append(' '); j += 4; break;
                        case IconData.C: sb.append("C"); for (int k = 0; k < 6; k++) sb.append(l.pts[j + k]).append(' '); j += 6; break;
                        default: sb.append("Z");
                    }
                }
                sb.append("'/>");
            }
            sb.append("</svg>");
            try (FileWriter w = new FileWriter(outDir + "/" + i + ".svg")) { w.write(sb.toString()); }
        }
        System.out.println("decoded " + icons.length + " icons");
    }
}
