package org.wishy.files;

public class MimeMatchTest {
    static int fail = 0;
    static void t(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fail++; }

    public static void run() {
        String[] any = {"*/*"}, img = {"image/*"}, multi = {"image/png", "application/pdf"}, mix = {"image/*", "video/*"};
        t("anything accepts everything", MimeMatch.ok(any, "video/mp4") && MimeMatch.ok(any, "application/octet-stream"));
        t("anything accepts unknown type", MimeMatch.ok(any, null));
        t("null / empty accept list = anything", MimeMatch.ok(null, "a/b") && MimeMatch.ok(new String[0], "a/b"));
        t("image/* accepts jpeg and png", MimeMatch.ok(img, "image/jpeg") && MimeMatch.ok(img, "image/png"));
        t("image/* rejects video and pdf", !MimeMatch.ok(img, "video/mp4") && !MimeMatch.ok(img, "application/pdf"));
        t("image/* rejects unknown type", !MimeMatch.ok(img, null));
        t("image/* does not match 'imagery/x'", !MimeMatch.ok(img, "imagery/x"));
        t("exact list: png and pdf in, jpeg out", MimeMatch.ok(multi, "image/png") && MimeMatch.ok(multi, "application/pdf") && !MimeMatch.ok(multi, "image/jpeg"));
        t("image/* or video/*", MimeMatch.ok(mix, "video/webm") && MimeMatch.ok(mix, "image/gif") && !MimeMatch.ok(mix, "audio/mpeg"));
        t("null entry in list is tolerated", MimeMatch.ok(new String[]{null}, "x/y"));
        if (fail > 0) { System.out.println(fail + " FAILED"); System.exit(1); }
        System.out.println("all passed");
    }
}
