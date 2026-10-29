package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConfigTest {
    @Test public void defaultsForTheNewKeys() {
        Config k = new Config();
        assertEquals(41.4235, k.lat, 1e-6);
        assertEquals(2.2010, k.lon, 1e-6);
        assertTrue(k.newsRss.startsWith("https://api.3cat.cat/"));
        assertTrue(k.dockTint);
        assertEquals(TimeBands.Band.MORNING, k.bands().at(9 * 60));
        assertEquals(TimeBands.Band.NIGHT, k.bands().at(23 * 60 + 30));
    }

    @Test public void newKeysAreRead() {
        Config k = new Config();
        k.set("location", "41.39, 2.17");
        k.set("news_rss", "https://example.org/rss");
        k.set("morning", "07:30-10:00");
        k.set("night", "");
        k.set("dock_tint", "off");
        assertEquals(41.39, k.lat, 1e-6);
        assertEquals(2.17, k.lon, 1e-6);
        assertEquals("https://example.org/rss", k.newsRss);
        assertEquals(TimeBands.Band.MORNING, k.bands().at(7 * 60 + 45));
        assertEquals(TimeBands.Band.DAY, k.bands().at(23 * 60 + 30));
        assertFalse(k.dockTint);
    }

    @Test public void badLocationKeepsTheDefault() {
        Config k = new Config();
        k.set("location", "somewhere");
        assertEquals(41.4235, k.lat, 1e-6);
    }

    @Test public void existingKeysBehaveAsBefore() {
        Config k = new Config();
        k.set("columns", "12");
        k.set("dock", "a.b, c.d");
        k.set("wallpaper_minutes", "-5");
        assertEquals(7, k.columns);
        assertEquals(2, k.dock.size());
        assertEquals(0, k.wallpaperMinutes);
    }

    @Test public void bingWallpaperRotatesByDefault() {
        Config k = new Config();
        assertEquals("bing", k.wallpaper);
        assertEquals(30, k.wallpaperMinutes);
    }

    @Test public void wallpaperCanBeTurnedOff() {
        Config empty = new Config(), off = new Config();
        empty.set("wallpaper", "");
        off.set("wallpaper", "off");
        assertEquals("", empty.wallpaper);
        assertEquals("", off.wallpaper);
    }

    @Test public void clockDefaultsToSystem() {
        assertEquals(Clock.SYSTEM, new Config().clock);
    }

    @Test public void explicitClockFormatsAreRead() {
        Config k24 = new Config(), k12 = new Config();
        k24.set("clock", "24");
        k12.set("clock", "12");
        assertEquals("24", k24.clock);
        assertEquals("12", k12.clock);
    }

    @Test public void unknownClockKeepsSystemAndEmptyMeansSystem() {
        Config k = new Config();
        k.set("clock", "25");
        assertEquals(Clock.SYSTEM, k.clock);
        k.set("clock", "abc");
        assertEquals(Clock.SYSTEM, k.clock);
        k.set("clock", "");
        assertEquals(Clock.SYSTEM, k.clock);
    }
}
