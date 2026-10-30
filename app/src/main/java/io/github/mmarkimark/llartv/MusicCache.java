package io.github.mmarkimark.llartv;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Rebuildable, one-file CC0 music cache. Call only from a worker, never the UI thread. */
final class MusicCache {
    static final String URL = "https://opengameart.org/sites/default/files/04_holiznacc0_-_dancing.mp3";
    static final long BYTES = 5_429_965;
    static final String SHA256 = "c974c57cf242be09806002a236a4d97fad80e015c1f36535c800a782f9396228";
    private static final String FILE = "music-holizna-dancing-v1.mp3";

    interface Download { InputStream open() throws IOException; }

    private static final class Failure extends IOException {
        Failure(String reason) { super(reason); } // only controlled text/numeric metadata, never source URLs
    }

    static String failureReason(Exception error) {
        return error instanceof Failure ? error.getMessage() : error.getClass().getSimpleName();
    }

    private final File dir;
    private final long bytes;
    private final String hash;
    private final java.util.function.LongSupplier clock;

    MusicCache(File dir, long bytes, String hash) { this(dir, bytes, hash, System::nanoTime); }

    MusicCache(File dir, long bytes, String hash, java.util.function.LongSupplier clock) {
        this.dir = dir;
        this.bytes = bytes;
        this.hash = hash;
        this.clock = clock;
    }

    static File load(File dir) throws IOException {
        return new MusicCache(dir, BYTES, SHA256).get(MusicCache::open);
    }

    /** Only verified bytes become visible at the final path; concurrent dreams share one download. */
    File get(Download download) throws IOException {
        synchronized (MusicCache.class) {
            checkInterrupted();
            if (dir == null || !dir.isDirectory()) throw new Failure("music cache directory unavailable");
            File file = new File(dir, FILE);
            if (file.isFile() && file.length() == bytes) {
                try (InputStream in = new FileInputStream(file)) {
                    if (hash.equals(copyHash(in, null))) return file;
                }
            }
            File part = File.createTempFile("music-", ".part", dir);
            try {
                String received;
                try (InputStream in = download.open(); OutputStream out = new FileOutputStream(part)) {
                    received = copyHash(in, out);
                }
                if (!hash.equals(received)) throw new Failure("music checksum mismatch");
                checkInterrupted();
                if (!part.renameTo(file)) throw new Failure("music cache install failed");
                return file;
            } finally { part.delete(); }
        }
    }

    private String copyHash(InputStream in, OutputStream out) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IOException("SHA-256 unavailable", e); }
        byte[] buffer = new byte[16384];
        // A one-time 5.43 MB transfer needs more time than streaming startup; reads are still capped at 8s.
        long count = 0, deadline = clock.getAsLong() + 120_000_000_000L;
        while (true) {
            checkInterrupted();
            if (clock.getAsLong() > deadline) throw new Failure("music download deadline exceeded");
            int n = in.read(buffer);
            if (n < 0) break;
            count += n;
            if (count > bytes) throw new Failure("music download too large");
            digest.update(buffer, 0, n);
            if (out != null) out.write(buffer, 0, n);
        }
        checkInterrupted();
        if (count != bytes) throw new Failure("incomplete music download: " + count + " bytes");
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) result.append(String.format(java.util.Locale.ROOT, "%02x", b));
        return result.toString();
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("music download cancelled");
    }

    private static InputStream open() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(URL).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false); // never follow a changed source or HTTPS downgrade
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new Failure("music source HTTP " + status);
            String type = connection.getContentType();
            if (type == null || !type.toLowerCase(java.util.Locale.ROOT).startsWith("audio/mpeg"))
                throw new Failure("music source is not MP3 audio");
            long size = connection.getContentLengthLong();
            if (size >= 0 && size != BYTES) throw new Failure("music source size changed: " + size + " bytes");
            return new FilterInputStream(connection.getInputStream()) {
                @Override public void close() throws IOException {
                    try { super.close(); } finally { connection.disconnect(); }
                }
            };
        } catch (IOException | RuntimeException e) {
            connection.disconnect();
            throw e;
        }
    }
}
