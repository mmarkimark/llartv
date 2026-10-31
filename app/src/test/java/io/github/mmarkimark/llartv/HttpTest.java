package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class HttpTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void copyCappedCountsTheBytesUpToTheLimit() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(40_000, Http.copyCapped(FakeConnection.sized(40_000), out, 40_000));
        assertEquals(40_000, out.size());
    }

    @Test public void copyCappedStopsSoonAfterTheLimit() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Http.copyCapped(FakeConnection.sized(10_000_000), out, 40_000);
            fail("over the limit must throw");
        } catch (IOException expected) { /* like a network error */ }
        assertTrue("nothing past the limit is kept", out.size() <= 40_000);
    }

    @Test public void textJoinsLinesAsTheOldReaderDid() throws Exception {
        assertEquals("{\"a\":1,\"b\":2}", Http.text(new FakeConnection(200, "{\"a\":1,\r\n\"b\":2}\n"), 100));
    }

    @Test public void aNonOkImageLeavesTheFileAndNoPart() throws Exception {
        File f = tmp.newFile("img.jpg");
        Files.write(f.toPath(), new byte[]{7});
        FakeConnection c = new FakeConnection(404, "not found page");
        try {
            Http.save(c, f, 1000);
            fail("HTTP 404 must fail");
        } catch (IOException expected) { /* failed download */ }
        assertEquals(1, f.length());
        assertFalse(new File(f.getPath() + ".part").exists());
        assertTrue(c.disconnected);
    }

    @Test public void anEmptyImageFailsAndLeavesNoPart() throws Exception {
        File f = new File(tmp.getRoot(), "img.jpg");
        try {
            Http.save(new FakeConnection(200, ""), f, 1000);
            fail("an empty body must fail");
        } catch (IOException expected) { /* as before */ }
        assertFalse(f.exists());
        assertFalse(new File(f.getPath() + ".part").exists());
    }

    @Test public void theLimitsLeaveRoomForRealResponses() {
        assertEquals(1024 * 1024, Fixtures.MAX_BYTES);              // ESPN schedule: tens of KB
        assertEquals(256 * 1024, Weather.MAX_BYTES);                // Open-Meteo forecast: about 1 KB
        assertEquals(1024 * 1024, DailyWallpaper.MAX_LIST_BYTES);   // Bing list of 8: a few KB
        assertEquals(20L * 1024 * 1024, DailyWallpaper.MAX_IMAGE_BYTES);   // 1920x1080 JPEG: hundreds of KB
        assertEquals(2 * 1024 * 1024, News.MAX_FEED_BYTES);
    }

    @Test public void theFakeResponseServesItsStatusAndSizedBody() throws Exception {
        FakeConnection c = FakeConnection.ofSize(70_000);
        assertEquals(200, c.getResponseCode());
        long n = 0;
        byte[] buf = new byte[8192];
        for (int k; (k = c.getInputStream().read(buf)) > 0; ) n += k;
        assertEquals(70_000, n);
    }
}

/** An HTTP response without a network: a status code and a body, recording whether it was closed. */
final class FakeConnection extends HttpURLConnection {
    final int code;
    final InputStream body;
    boolean disconnected;

    FakeConnection(int code, InputStream body) throws IOException {
        super(new URL("https://example.invalid/"));
        this.code = code;
        this.body = body;
    }

    FakeConnection(int code, String body) throws IOException {
        this(code, new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    /** A 200 response whose body is n bytes of 'x', produced on the fly (no n-byte array in the test). */
    static FakeConnection ofSize(long n) throws IOException { return new FakeConnection(200, sized(n)); }

    static InputStream sized(long n) {
        return new InputStream() {
            long left = n;
            @Override public int read() { if (left <= 0) return -1; left--; return 'x'; }
            @Override public int read(byte[] b, int off, int len) {
                if (left <= 0) return -1;
                int k = (int) Math.min(len, left);
                java.util.Arrays.fill(b, off, off + k, (byte) 'x');
                left -= k;
                return k;
            }
        };
    }

    @Override public int getResponseCode() { return code; }
    @Override public InputStream getInputStream() { return body; }
    @Override public void connect() {}
    @Override public void disconnect() { disconnected = true; }
    @Override public boolean usingProxy() { return false; }
}
