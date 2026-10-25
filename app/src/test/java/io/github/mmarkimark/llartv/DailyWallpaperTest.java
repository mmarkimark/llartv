package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

public class DailyWallpaperTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    File dirWith(String list, String... images) throws Exception {
        File dir = tmp.newFolder();
        for (String n : images) new File(dir, n).createNewFile();
        try (FileWriter w = new FileWriter(new File(dir, DailyWallpaper.LIST))) { w.write(list); }
        return dir;
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
}
