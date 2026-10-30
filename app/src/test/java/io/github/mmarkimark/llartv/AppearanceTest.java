package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppearanceTest {
    static final String[] PRESET_NIGHTS = {"off", "21:00-07:00", "22:00-07:00", "22:00-08:00", "23:00-07:00",
            "23:00-08:00", "00:00-08:00"};

    @Test public void wallpaperKinds() {
        assertEquals(Appearance.Wallpaper.PLAIN, Appearance.wallpaper(""));
        assertEquals(Appearance.Wallpaper.BING, Appearance.wallpaper("bing"));
        assertEquals(Appearance.Wallpaper.BING, Appearance.wallpaper("Bing"));
        assertEquals(Appearance.Wallpaper.CUSTOM, Appearance.wallpaper("https://example.org/sky.jpg"));
    }

    @Test public void photoChangeOffersThePresetsWithTheNewestAsFixed() {
        assertArrayEquals(new int[]{15, 30, 60, 180, 0}, Appearance.minutes(30));
        assertEquals(1, Appearance.indexOf(Appearance.minutes(30), 30));
        assertEquals(4, Appearance.indexOf(Appearance.minutes(0), 0));
        assertEquals("Fixa (la més nova)", Appearance.minutesLabel(0));
        assertEquals("Cada 15 min", Appearance.minutesLabel(15));
        assertEquals("Cada hora", Appearance.minutesLabel(60));
        assertEquals("Cada 3 hores", Appearance.minutesLabel(180));
    }

    @Test public void aCustomPhotoChangeIsKeptAndSelected() {
        int[] m = Appearance.minutes(45);
        assertArrayEquals(new int[]{15, 30, 60, 180, 0, 45}, m);
        assertEquals(5, Appearance.indexOf(m, 45));
        assertEquals("Cada 45 min", Appearance.minutesLabel(45));
        assertEquals("Cada 2 hores", Appearance.minutesLabel(120));
    }

    @Test public void nightModeOffersOffAndRangesAcrossMidnight() {
        assertArrayEquals(PRESET_NIGHTS, Appearance.nights("23:00-08:00"));
        assertEquals(5, Appearance.indexOf(Appearance.nights("23:00-08:00"), "23:00-08:00"));
        assertEquals("Desactivat", Appearance.nightLabel("off"));
        assertEquals("23:00 – 08:00", Appearance.nightLabel("23:00-08:00"));
    }

    @Test public void nightModeReadsLikeTimeBands() {
        assertEquals("off", Appearance.night(""));
        assertEquals("off", Appearance.night("OFF"));
        assertEquals("23:00-08:00", Appearance.night("nonsense"));   // unreadable keeps the default
        assertEquals("07:00-09:00", Appearance.night(" 7:00 - 9:00 "));
    }

    @Test public void aCustomNightRangeIsKeptAndSelected() {
        String[] n = Appearance.nights("22:30-07:30");
        assertEquals(PRESET_NIGHTS.length + 1, n.length);
        assertEquals("22:30-07:30", n[n.length - 1]);
        assertEquals(n.length - 1, Appearance.indexOf(n, Appearance.night("22:30-07:30")));
        assertEquals("22:30 – 07:30", Appearance.nightLabel("22:30-07:30"));
    }

    @Test public void everyNightPresetIsReadByTimeBandsAsWritten() {
        for (String v : PRESET_NIGHTS) assertEquals(v, Appearance.night(v));
        TimeBands b = TimeBands.parse(TimeBands.DEFAULT_MORNING, "22:00-07:00");
        assertEquals(TimeBands.Band.NIGHT, b.at(23 * 60));
        assertEquals(TimeBands.Band.NIGHT, b.at(6 * 60 + 59));
        assertEquals(TimeBands.Band.DAY, b.at(19 * 60));
        assertEquals(null, TimeBands.parse(TimeBands.DEFAULT_MORNING, "off").night);
    }
}
