import java.io.*;
import java.nio.file.*;

/** Decodes assets/icons.bin with the app's own IconData reader and writes one SVG per icon layer set (for pixel comparison with the source SVGs). */
public class IconsMain {
    public static void main(String[] a) throws Exception {
        org.wishy.files.IconDataTest.run(a[0], a[1]);
    }
}
