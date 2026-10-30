package org.wishy.files;

/** Does a file's MIME type satisfy what another app asked for? Handles the any-type wildcard, "image" style family wildcards and exact types; unknown types only match "anything". */
final class MimeMatch {
    private MimeMatch() { }

    static boolean ok(String[] accept, String mime) {
        if (accept == null || accept.length == 0) return true;
        for (String t : accept) {
            if (t == null || t.equals("*/*") || t.equals("*")) return true;
            if (mime == null) continue;
            if (t.equals(mime)) return true;
            if (t.endsWith("/*") && mime.startsWith(t.substring(0, t.length() - 1))) return true;
        }
        return false;
    }
}
