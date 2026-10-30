package io.github.mmarkimark.llartv;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MusicCacheTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private static final byte[] AUDIO = new byte[]{1, 2, 3, 4, 5};

    MusicCache cache(File dir) throws Exception { return cache(dir, System::nanoTime); }

    MusicCache cache(File dir, java.util.function.LongSupplier clock) throws Exception {
        StringBuilder hash = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(AUDIO)) hash.append(String.format("%02x", b));
        return new MusicCache(dir, AUDIO.length, hash.toString(), clock);
    }

    private InputStream slow(AtomicLong clock, long secondsPerByte) {
        return new ByteArrayInputStream(AUDIO) {
            @Override public synchronized int read(byte[] b, int off, int len) {
                int n = super.read(b, off, Math.min(1, len));
                if (n > 0) clock.addAndGet(secondsPerByte * 1_000_000_000L);
                return n;
            }
        };
    }

    @Test public void firstDownloadMayTakeUpToTwoMinutesWithoutBeingTruncated() throws Exception {
        for (long secondsPerByte : new long[]{20, 24}) {
            AtomicLong clock = new AtomicLong();
            File file = cache(tmp.newFolder(), clock::get).get(() -> slow(clock, secondsPerByte));
            assertArrayEquals(AUDIO, Files.readAllBytes(file.toPath()));
            assertEquals(secondsPerByte * AUDIO.length * 1_000_000_000L, clock.get());
        }
    }

    @Test public void downloadBeyondTwoMinutesStillFailsWithoutPublishingOrRetrying() throws Exception {
        AtomicLong clock = new AtomicLong();
        AtomicInteger opens = new AtomicInteger();
        File dir = tmp.newFolder();
        try {
            cache(dir, clock::get).get(() -> { opens.incrementAndGet(); return slow(clock, 30); });
            fail("unbounded download accepted");
        } catch (IOException error) {
            assertEquals("music download deadline exceeded", MusicCache.failureReason(error));
        }
        assertEquals(1, opens.get());
        assertEquals(0, dir.list().length);
    }

    @Test public void downloadsOnceThenReusesTheVerifiedFileOffline() throws Exception {
        MusicCache cache = cache(tmp.newFolder());
        AtomicInteger opens = new AtomicInteger();
        File file = cache.get(() -> { opens.incrementAndGet(); return new ByteArrayInputStream(AUDIO); });
        assertArrayEquals(AUDIO, Files.readAllBytes(file.toPath()));
        File again = cache.get(() -> { opens.incrementAndGet(); throw new IOException("offline"); });
        assertEquals(file, again);
        assertArrayEquals(AUDIO, Files.readAllBytes(again.toPath()));
        assertEquals(1, opens.get());
    }

    @Test public void rejectsTruncatedOversizedAndChangedDownloadsWithoutPublishingThem() throws Exception {
        for (byte[] bad : new byte[][]{new byte[]{1, 2}, new byte[]{1, 2, 3, 4, 5, 6}, new byte[]{5, 4, 3, 2, 1}}) {
            File dir = tmp.newFolder();
            MusicCache cache = cache(dir);
            AtomicInteger opens = new AtomicInteger();
            try {
                cache.get(() -> { opens.incrementAndGet(); return new ByteArrayInputStream(bad); });
                fail("invalid content accepted");
            } catch (IOException expected) {}
            assertEquals(1, opens.get()); // no app-level retry
            assertEquals(0, dir.list().length); // neither a final file nor a partial download survives
            File file = cache.get(() -> new ByteArrayInputStream(AUDIO));
            assertArrayEquals(AUDIO, Files.readAllBytes(file.toPath()));
        }
    }

    @Test public void ioFailureLeavesNoPartialFileAndAllowsALaterAttempt() throws Exception {
        File dir = tmp.newFolder();
        MusicCache cache = cache(dir);
        try {
            cache.get(() -> new InputStream() {
                int reads;
                @Override public int read() throws IOException {
                    if (reads++ == 0) return 1;
                    throw new IOException("connection lost");
                }
            });
            fail("failure accepted");
        } catch (IOException expected) {}
        assertEquals(0, dir.list().length);
        assertArrayEquals(AUDIO, Files.readAllBytes(cache.get(() -> new ByteArrayInputStream(AUDIO)).toPath()));
    }

    @Test public void corruptCacheIsNotPlayedOrOverwrittenByAFailedDownload() throws Exception {
        File dir = tmp.newFolder();
        MusicCache cache = cache(dir);
        File file = cache.get(() -> new ByteArrayInputStream(AUDIO));
        byte[] corrupt = new byte[]{5, 4, 3, 2, 1};
        Files.write(file.toPath(), corrupt);
        try {
            cache.get(() -> { throw new IOException("offline"); });
            fail("corrupt file accepted");
        } catch (IOException expected) {}
        assertArrayEquals(corrupt, Files.readAllBytes(file.toPath()));
        assertEquals(1, dir.list().length);
        assertEquals(file, cache.get(() -> new ByteArrayInputStream(AUDIO)));
        assertArrayEquals(AUDIO, Files.readAllBytes(file.toPath()));
    }

    @Test public void interruptedDownloadCannotPublishACacheFile() throws Exception {
        File dir = tmp.newFolder();
        MusicCache cache = cache(dir);
        try {
            cache.get(() -> {
                Thread.currentThread().interrupt();
                return new ByteArrayInputStream(AUDIO);
            });
            fail("interrupted download accepted");
        } catch (IOException expected) {
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        assertEquals(0, dir.list().length);
        assertArrayEquals(AUDIO, Files.readAllBytes(cache.get(() -> new ByteArrayInputStream(AUDIO)).toPath()));
    }

    @Test public void diagnosticsExposeOnlyControlledReasonsOrExceptionTypes() throws Exception {
        String secret = "https://user:password@example.invalid/audio?token=private";
        assertEquals("IOException", MusicCache.failureReason(new IOException(secret)));
        assertEquals("FileNotFoundException", MusicCache.failureReason(new java.io.FileNotFoundException(secret)));
        MusicCache cache = cache(tmp.newFolder());
        try {
            cache.get(() -> new ByteArrayInputStream(new byte[]{5, 4, 3, 2, 1}));
            fail("changed bytes accepted");
        } catch (IOException error) {
            assertEquals("music checksum mismatch", MusicCache.failureReason(error));
        }
    }

    @Test public void concurrentDreamRequestsShareOneDownload() throws Exception {
        File dir = tmp.newFolder();
        MusicCache first = cache(dir), second = cache(dir);
        AtomicInteger opens = new AtomicInteger();
        MusicCache.Download download = () -> { opens.incrementAndGet(); return new ByteArrayInputStream(AUDIO); };
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<File> a = workers.submit(() -> { start.await(); return first.get(download); });
            Future<File> b = workers.submit(() -> { start.await(); return second.get(download); });
            start.countDown();
            File file = a.get(5, TimeUnit.SECONDS);
            assertEquals(file, b.get(5, TimeUnit.SECONDS));
            assertArrayEquals(AUDIO, Files.readAllBytes(file.toPath()));
            assertEquals(1, opens.get());
            assertEquals(1, dir.list().length);
        } finally { workers.shutdownNow(); }
    }
}
