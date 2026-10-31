package io.github.mmarkimark.llartv;

import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MusicTrackTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    static final byte[] AUDIO = {1, 2, 3, 4, 5};
    static final String SOURCE = "https://github.com/mmarkimark/llartv/releases/download/v1.4/test.mp3";
    static final String ASSET = "https://release-assets.githubusercontent.com/example/test.mp3?signature=private";

    String hash() throws Exception {
        StringBuilder h = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(AUDIO)) h.append(String.format("%02x", b));
        return h.toString();
    }
    MusicTrack track(String file) throws Exception { return new MusicTrack("Test", file, SOURCE, AUDIO.length, hash()); }

    @Test public void catalogueHasThreeDistinctPinnedTracksAndRetainsDancingIdentity() {
        assertEquals(3, MusicTrack.COUNT);
        Set<String> files = new HashSet<>(), hashes = new HashSet<>();
        for (int i = 0; i < MusicTrack.COUNT; i++) {
            MusicTrack t = MusicTrack.at(i);
            assertTrue(files.add(t.file)); assertTrue(hashes.add(t.sha256));
            assertTrue(t.bytes > 0); assertEquals(64, t.sha256.length());
        }
        assertEquals("music-holizna-dancing-v1.mp3", MusicTrack.at(0).file);
        assertEquals(MusicCache.URL, MusicTrack.at(0).url);
        assertEquals(MusicCache.BYTES, MusicTrack.at(0).bytes);
        assertEquals(MusicCache.SHA256, MusicTrack.at(0).sha256);
        assertEquals("7f07fbf89860959ff527f7b74606146dd0b0de263739989a70ce712c7c361614", MusicTrack.at(1).sha256);
        assertEquals(7_684_325L, MusicTrack.at(1).bytes);
        assertEquals("f12cbb52bd4b92bd6cf8a343645567c119627e9cbf98108a3eb6ac2cc275ee46", MusicTrack.at(2).sha256);
        assertEquals(8_576_659L, MusicTrack.at(2).bytes);
    }

    @Test public void independentFilesAreVerifiedAndReusedOfflineWithoutOverwritingLegacy() throws Exception {
        File dir = tmp.newFolder();
        File legacy = new MusicCache(dir, AUDIO.length, hash()).get(() -> new ByteArrayInputStream(AUDIO));
        MusicCache second = new MusicCache(dir, "second.mp3", AUDIO.length, hash(), System::nanoTime);
        File b = second.get(() -> new ByteArrayInputStream(AUDIO));
        assertNotEquals(legacy, b); assertEquals(2, dir.list().length);
        assertArrayEquals(AUDIO, Files.readAllBytes(legacy.toPath()));
        assertEquals(b, second.get(() -> { throw new IOException("offline"); }));
        assertArrayEquals(AUDIO, Files.readAllBytes(b.toPath()));
        try { second.get(() -> { throw new AssertionError("verified cache must not download"); }); }
        finally { assertArrayEquals(AUDIO, Files.readAllBytes(legacy.toPath())); }
    }

    @Test public void failedNewTrackLeavesExistingTrackAndNoPartialFile() throws Exception {
        File dir = tmp.newFolder();
        File legacy = new MusicCache(dir, AUDIO.length, hash()).get(() -> new ByteArrayInputStream(AUDIO));
        try {
            new MusicCache(dir, "second.mp3", AUDIO.length, hash(), System::nanoTime)
                    .get(() -> new ByteArrayInputStream(new byte[]{5,4,3,2,1}));
            fail();
        } catch (IOException expected) { assertEquals("music checksum mismatch", MusicCache.failureReason(expected)); }
        assertEquals(1, dir.list().length); assertArrayEquals(AUDIO, Files.readAllBytes(legacy.toPath()));
    }

    static class Connection extends HttpURLConnection {
        int status = 200, disconnected;
        String type = "application/octet-stream", location;
        long size = AUDIO.length;
        Connection(String url) throws Exception { super(new URL(url)); }
        public void connect() {} public boolean usingProxy() { return false; }
        public void disconnect() { disconnected++; }
        public int getResponseCode() { return status; }
        public String getContentType() { return type; }
        public long getContentLengthLong() { return size; }
        public String getHeaderField(String name) { return "Location".equals(name) ? location : null; }
        public InputStream getInputStream() { return new ByteArrayInputStream(AUDIO); }
    }

    @Test public void releaseAssetAllowsOneBoundedHttpsRedirectAndOctetStream() throws Exception {
        Connection first = new Connection(SOURCE), last = new Connection(ASSET);
        first.status = 302; first.location = ASSET;
        List<String> opened = new ArrayList<>();
        try (InputStream in = MusicCache.open(track("test.mp3"), u -> {
            opened.add(u.toString()); return opened.size() == 1 ? first : last;
        })) { assertArrayEquals(AUDIO, read(in)); }
        assertEquals(Arrays.asList(SOURCE, ASSET), opened);
        assertEquals(1, first.disconnected); assertEquals(1, last.disconnected);
        assertFalse(first.getInstanceFollowRedirects()); assertFalse(last.getInstanceFollowRedirects());
        assertEquals(8000, last.getReadTimeout()); assertEquals(8000, last.getConnectTimeout());
    }

    @Test public void redirectCannotDowngradeEscapeHostUseCredentialsPortOrFragment() throws Exception {
        for (String bad : new String[]{"http://release-assets.githubusercontent.com/a", "https://example.invalid/a",
                "https://release-assets.githubusercontent.com.evil.invalid/a", "https://user@release-assets.githubusercontent.com/a",
                "https://release-assets.githubusercontent.com:444/a", "https://release-assets.githubusercontent.com/a#x"}) {
            Connection c = new Connection(SOURCE); c.status = 302; c.location = bad;
            List<String> opened = new ArrayList<>();
            try { MusicCache.open(track("test.mp3"), u -> { opened.add(u.toString()); return c; }); fail(bad); }
            catch (IOException expected) { assertFalse(MusicCache.failureReason(expected).contains("user@")); }
            assertEquals(Collections.singletonList(SOURCE), opened); assertEquals(1, c.disconnected);
        }
    }

    @Test public void redirectsAreCappedAndMissingLocationIsRejected() throws Exception {
        for (String location : new String[]{null, ASSET}) {
            List<Connection> opened = new ArrayList<>();
            try { MusicCache.open(track("test.mp3"), u -> {
                Connection c;
                try { c = new Connection(u.toString()); } catch (Exception e) { throw new IOException(e); }
                c.status = 302; c.location = location; opened.add(c); return c;
            }); fail(); } catch (IOException expected) {}
            assertEquals(location == null ? 1 : 3, opened.size());
            for (Connection c : opened) assertEquals(1, c.disconnected);
        }
    }

    @Test public void legacySourceStillRequiresMp3AndNeverFollowsRedirects() throws Exception {
        for (int status : new int[]{200,302}) {
            Connection c = new Connection(MusicCache.URL); c.status = status; c.location = ASSET;
            try { MusicCache.open(MusicTrack.at(0), u -> c); fail(); } catch (IOException expected) {}
            assertEquals(1, c.disconnected);
        }
        Connection c = new Connection(MusicCache.URL); c.type = "audio/mpeg"; c.size = MusicCache.BYTES;
        try (InputStream in = MusicCache.open(MusicTrack.at(0), u -> c)) { assertArrayEquals(AUDIO, read(in)); }
        assertEquals(1, c.disconnected);
    }

    @Test public void releaseAssetRejectsWrongMimeSizeOrStatusBeforeReading() throws Exception {
        for (int failure = 0; failure < 3; failure++) {
            Connection c = new Connection(SOURCE);
            if (failure == 0) c.type = "text/html";
            if (failure == 1) c.size++;
            if (failure == 2) c.status = 403;
            try { MusicCache.open(track("test.mp3"), u -> c); fail(); } catch (IOException expected) {}
            assertEquals(1, c.disconnected);
        }
    }

    byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[16]; int n;
        while ((n = in.read(b)) != -1) out.write(b,0,n);
        return out.toByteArray();
    }
}
