package io.github.mmarkimark.llartv;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/**
 * Size-bounded reads of HTTP bodies. Every download has a limit well above what the service really sends, so a
 * huge or endless response fails like any network error instead of filling the launcher's memory or the disk:
 * news feed 2 MiB (News), football schedule 1 MiB (Fixtures), weather 256 KiB (Weather), Bing image list 1 MiB
 * and each wallpaper image 20 MiB (DailyWallpaper).
 */
final class Http {
    private Http() {}

    /** Throws unless the response is HTTP 200; an error page or a redirect is not the body asked for. */
    static void requireOk(HttpURLConnection c) throws IOException {
        int code = c.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code);
    }

    /** Reads the whole body, or throws as soon as it would pass max bytes. */
    static byte[] readCapped(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copyCapped(in, out, max);
        return out.toByteArray();
    }

    /** Copies in to out and returns the byte count, or throws as soon as it would pass max bytes. */
    static long copyCapped(InputStream in, OutputStream out, long max) throws IOException {
        byte[] buf = new byte[16384];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            if (total + n > max) throw new IOException("body larger than " + max + " bytes");
            out.write(buf, 0, n);
            total += n;
        }
        return total;
    }

    /** A 200 response's body as UTF-8 text without its line breaks (JSON), at most max bytes; always disconnects. */
    static String text(HttpURLConnection c, int max) throws IOException {
        try {
            requireOk(c);
            try (InputStream in = c.getInputStream()) {
                return new String(readCapped(in, max), StandardCharsets.UTF_8).replaceAll("\r\n|\r|\n", "");
            }
        } finally { c.disconnect(); }
    }

    /**
     * Streams a 200 response's body to f via f.part, at most max bytes; always disconnects. On any failure
     * (status, size, network, empty body) the .part file is deleted and f is left as it was.
     */
    static void save(HttpURLConnection c, File f, long max) throws IOException {
        File tmp = new File(f.getPath() + ".part");
        boolean done = false;
        try {
            requireOk(c);
            long n;
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                n = copyCapped(in, out, max);
            }
            if (n == 0) throw new IOException("empty body");
            if (!tmp.renameTo(f)) throw new IOException("rename failed");
            done = true;
        } finally {
            c.disconnect();
            if (!done) tmp.delete();
        }
    }
}
