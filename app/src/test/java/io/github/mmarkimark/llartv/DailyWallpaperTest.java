package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class DailyWallpaperTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    File dirWith(String list, String... images) throws Exception {
        File dir = tmp.newFolder();
        for (String n : images) new File(dir, n).createNewFile();
        try (FileWriter w = new FileWriter(new File(dir, DailyWallpaper.LIST))) { w.write(list); }
        return dir;
    }

    @Test public void offShowsAPlainBackgroundEvenWithCachedPhotos() throws Exception {
        File dir = dirWith("wallpaper-a.jpg\nwallpaper-b.jpg\n", "wallpaper-a.jpg", "wallpaper-b.jpg");
        assertNull(DailyWallpaper.shown("", dir, 30, 0));
        assertNull(DailyWallpaper.shown("", dir, 0, 0));
    }

    @Test public void aPushedImageStillWinsWhenTheDownloadsAreOff() throws Exception {
        File dir = dirWith("wallpaper-a.jpg\n", "wallpaper-a.jpg", "wallpaper.jpg");
        assertEquals("wallpaper.jpg", DailyWallpaper.shown("", dir, 30, 0).getName());
        assertEquals("wallpaper.jpg", DailyWallpaper.shown("bing", dir, 30, 0).getName());
    }

    @Test public void bingKeepsShowingTheNewestOrTheRotatedPhoto() throws Exception {
        File dir = dirWith("wallpaper-a.jpg\nwallpaper-b.jpg\n", "wallpaper-a.jpg", "wallpaper-b.jpg");
        assertEquals("wallpaper-a.jpg", DailyWallpaper.shown("bing", dir, 0, 0).getName());
        assertEquals("wallpaper-b.jpg", DailyWallpaper.shown("bing", dir, 1, 60_000).getName());
    }

    @Test public void theScreensaverHasNoPhotosWhenTheWallpaperIsOff() throws Exception {
        File dir = dirWith("wallpaper-a.jpg\nwallpaper-b.jpg\n", "wallpaper-a.jpg", "wallpaper-b.jpg");
        assertEquals(0, DailyWallpaper.slides("", dir).size());
        assertEquals(2, DailyWallpaper.slides("bing", dir).size());
    }

    @Test public void slidesCarryTitleAndCaption() throws Exception {
        File dir = dirWith("wallpaper-a.jpg\tLa vida en manada\tDos lobos grises, Yellowstone\nwallpaper-b.jpg\tGirona en flor\tPati florit",
                "wallpaper-a.jpg", "wallpaper-b.jpg");
        List<DailyWallpaper.Slide> s = DailyWallpaper.slides(dir);
        assertEquals(2, s.size());
        assertEquals("wallpaper-a.jpg", s.get(0).file.getName());
        assertEquals("La vida en manada", s.get(0).title);
        assertEquals("Dos lobos grises, Yellowstone", s.get(0).caption);
        assertEquals("Girona en flor", s.get(1).title);
    }

    @Test public void listsFromEarlierVersionsStillWork() throws Exception {
        File dir = dirWith("wallpaper-a.jpg\nwallpaper-b.jpg\n", "wallpaper-a.jpg", "wallpaper-b.jpg");
        List<DailyWallpaper.Slide> s = DailyWallpaper.slides(dir);
        assertEquals(2, s.size());
        assertEquals("", s.get(0).title);
        assertEquals("", s.get(0).caption);
        assertEquals(2, DailyWallpaper.images(dir).size());
    }

    @Test public void missingImagesAreSkipped() throws Exception {
        File dir = dirWith("wallpaper-a.jpg\tA\ta\nwallpaper-gone.jpg\tB\tb", "wallpaper-a.jpg");
        assertEquals(1, DailyWallpaper.slides(dir).size());
    }

    @Test public void rotationStillPicksByClock() throws Exception {
        File dir = dirWith("wallpaper-a.jpg\tA\ta\nwallpaper-b.jpg\tB\tb", "wallpaper-a.jpg", "wallpaper-b.jpg");
        assertEquals("wallpaper-a.jpg", DailyWallpaper.current(dir, 0, 45 * 60_000L).getName());
        assertEquals("wallpaper-a.jpg", DailyWallpaper.current(dir, 30, 10 * 60_000L).getName());
        assertEquals("wallpaper-b.jpg", DailyWallpaper.current(dir, 30, 40 * 60_000L).getName());
        assertNull(DailyWallpaper.current(tmp.newFolder(), 30, 0));
    }

    @Test public void listLineKeepsFieldsOnOneLine() {
        assertEquals("wallpaper-a.jpg\tTwo words\tLine one line two",
                DailyWallpaper.listLine("wallpaper-a.jpg", "Two\twords", "Line one\nline two"));
    }

    static final int MIB = 1024 * 1024;

    File previousImage() throws Exception {
        File f = new File(tmp.newFolder(), "wallpaper-a.jpg");
        Files.write(f.toPath(), "previous".getBytes(StandardCharsets.UTF_8));
        return f;
    }

    void assertPreviousKeptAndNoPart(File f) throws Exception {
        assertArrayEquals("previous".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(f.toPath()));
        assertFalse("no partial download is left behind", new File(f.getPath() + ".part").exists());
    }

    @Test public void anImageIsSavedWhole() throws Exception {
        File f = previousImage();
        DailyWallpaper.save(new FakeConnection(200, "new image"), f);
        assertArrayEquals("new image".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(f.toPath()));
        assertFalse(new File(f.getPath() + ".part").exists());
    }

    @Test public void anImageOver20MibFailsAndKeepsThePreviousOne() throws Exception {
        File f = previousImage();
        FakeConnection c = FakeConnection.ofSize(20L * MIB + 1);
        try {
            DailyWallpaper.save(c, f);
            fail("an oversized image must fail");
        } catch (Exception expected) { /* download() then keeps the image already on disk */ }
        assertPreviousKeptAndNoPart(f);
        assertTrue(c.disconnected);
    }

    @Test public void aDownloadCutHalfwayLeavesNoPartialFile() throws Exception {
        File f = previousImage();
        InputStream broken = new SequenceInputStream(FakeConnection.sized(MIB), new InputStream() {
            @Override public int read() throws IOException { throw new IOException("connection reset"); }
        });
        try {
            DailyWallpaper.save(new FakeConnection(200, broken), f);
            fail("a cut download must fail");
        } catch (Exception expected) { /* network error */ }
        assertPreviousKeptAndNoPart(f);
    }

    @Test public void aBingListOverOneMebibyteFails() throws Exception {
        try {
            DailyWallpaper.read(FakeConnection.ofSize(MIB + 1));
            fail("an oversized image list must fail");
        } catch (Exception expected) { /* no new list; the saved photos stay */ }
    }
}
