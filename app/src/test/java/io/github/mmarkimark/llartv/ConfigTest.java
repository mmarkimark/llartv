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
        assertEquals(NewsSource.AUTOMATIC, k.newsSource);
        assertFalse(k.newsRssCustom);
        // The default feed is the one the "automatic" preset resolves to, not a stored custom URL.
        assertEquals(NewsSource.URL_3CAT, NewsSource.resolve(k).url);
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

    @Test public void theNewsSourceIsRead() {
        Config k = new Config();
        k.set("news_source", NewsSource.BBC_MUNDO);
        assertEquals(NewsSource.BBC_MUNDO, k.newsSource);
        k.set("news_source", "nope");
        assertEquals(NewsSource.BBC_MUNDO, k.newsSource);   // an unknown value keeps the current one
        k.set("news_source", "");
        assertEquals(NewsSource.AUTOMATIC, k.newsSource);    // an empty value restores the default
    }

    @Test public void aCustomFeedIsMarkedAsCustom() {
        Config k = new Config();
        k.set("news_rss", "https://example.org/rss");
        assertTrue(k.newsRssCustom);
        assertEquals("https://example.org/rss", k.newsRss);
        assertTrue(k.newsRssIsCustom());
        k.set("news_rss", "   ");
        assertFalse(k.newsRssIsCustom());   // a blank feed is not a custom feed
    }

    @Test public void theScreensaverClockAndMotionAreOnByDefaultAndCanBeTurnedOff() {
        Config k = new Config();
        assertTrue(k.clockScreen);
        assertTrue(k.motionScreen);
        k.set("clock_screen", "off");
        k.set("motion_screen", "no");
        assertFalse(k.clockScreen);
        assertFalse(k.motionScreen);
        k.set("clock_screen", "on");
        k.set("motion_screen", "0");
        assertTrue(k.clockScreen);
        assertFalse(k.motionScreen);
        k.set("clock_screen", "");
        k.set("motion_screen", "");
        assertTrue(k.clockScreen);    // an empty value restores the default
        assertTrue(k.motionScreen);
    }

    @Test public void hiddenAppsKeepTheOrderOfTheFile() {
        Config k = new Config();
        k.set("hidden", "z.app,a.app,m.app");
        assertEquals(java.util.Arrays.asList("z.app", "a.app", "m.app"), new java.util.ArrayList<>(k.hidden));
    }

    @Test public void theMusicSettingsAreRead() {
        Config k = new Config();
        assertFalse(k.musicScreen);
        assertEquals("", k.musicUrl);
        k.set("music_screen", "on");
        k.set("music_url", "https://example.org/lofi.mp3");
        assertTrue(k.musicScreen);
        assertEquals("https://example.org/lofi.mp3", k.musicUrl);
        k.set("music_screen", "off");
        assertFalse(k.musicScreen);
        k.set("music_screen", "0");
        assertFalse(k.musicScreen);
        k.set("music_screen", "no");
        assertFalse(k.musicScreen);
        k.set("music_screen", "");
        assertFalse(k.musicScreen);   // an empty value restores the default
    }

    @Test public void badLocationKeepsTheDefault() {
        Config k = new Config();
        k.set("location", "somewhere");
        assertEquals(41.4235, k.lat, 1e-6);
    }

    @Test public void anExplicitDockLineIsRememberedEvenWhenEmpty() {
        Config k = new Config();
        assertFalse(k.dockSet);          // no dock= line: the built-in list (or dock.txt) is used and filled
        k.set("dock", "a.b, c.d");
        assertTrue(k.dockSet);
        assertEquals(2, k.dock.size());
        k.set("dock", "");
        assertTrue(k.dockSet);           // an empty chosen dock is still chosen: it shows no apps, no fill
        assertTrue(k.dock.isEmpty());
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

    @Test public void contentPolicyDefaultsAreUnchanged() {
        Config k = new Config();
        assertEquals(ContentPolicy.MORNING, k.weatherHome);
        assertEquals(ContentPolicy.ALL, k.weatherScreen);
        assertEquals(ContentPolicy.MORNING, k.newsHome);
    }

    @Test public void contentPolicyKeysAreRead() {
        Config k = new Config();
        k.set("weather_home", "never");
        k.set("weather_screen", "never");
        k.set("weather_screen", "all");   // after moving away from the default, so "all" is really read back
        k.set("news_home", "never");
        assertEquals(ContentPolicy.NEVER, k.weatherHome);
        assertEquals(ContentPolicy.ALL, k.weatherScreen);
        assertEquals(ContentPolicy.NEVER, k.newsHome);
    }

    @Test public void unknownPolicyValueKeepsTheCurrentValue() {
        Config k = new Config();
        k.set("weather_home", "all");
        k.set("weather_screen", "never");
        k.set("news_home", "all");
        k.set("weather_home", "sometimes");
        k.set("weather_screen", "sometimes");
        k.set("news_home", "sometimes");
        assertEquals(ContentPolicy.ALL, k.weatherHome);
        assertEquals(ContentPolicy.NEVER, k.weatherScreen);
        assertEquals(ContentPolicy.ALL, k.newsHome);
    }

    @Test public void emptyPolicyValueKeepsTheDefault() {
        Config k = new Config();
        k.set("weather_home", "");
        k.set("weather_screen", "");
        k.set("news_home", "");
        assertEquals(ContentPolicy.MORNING, k.weatherHome);
        assertEquals(ContentPolicy.ALL, k.weatherScreen);
        assertEquals(ContentPolicy.MORNING, k.newsHome);
    }

    @Test public void theGridOrderIsOptionalAndKeepsTheFileOrder() {
        Config k = new Config();
        assertTrue(k.order.isEmpty());
        k.set("order", "com.b, com.a,com.c");
        assertEquals(java.util.Arrays.asList("com.b", "com.a", "com.c"), k.order);
        k.set("order", "");
        assertTrue(k.order.isEmpty());
    }

    @Test public void writingTheOrderKeepsCommentsAndUnknownLines() {
        String text = "# mine\ncolumns=5\nfoo=bar # keep\n";
        String out = Config.setInText(text, "order", "b,a");
        assertEquals("# mine\ncolumns=5\nfoo=bar # keep\norder=b,a\n", out);
        assertEquals("# mine\ncolumns=5\nfoo=bar # keep\norder=a,b\n", Config.setInText(out, "order", "a,b"));
    }

    // ---------------------------------------------------------------- the interface language (issue #11)

    @Test public void aNewInstallationIsInCatalan() {
        assertEquals(Language.CA, new Config().language);
    }

    @Test public void anUpgradedInstallationWithoutALanguageLineStaysInCatalan() throws Exception {
        java.io.File dir = java.nio.file.Files.createTempDirectory("conf").toFile();
        Config.write(dir, "# LlarTV settings\ncolumns=5\nnews_source=automatic\n");
        Config k = Config.parse(dir);
        assertEquals(Language.CA, k.language);
        assertEquals(NewsSource.THREE_CAT, NewsSource.resolve(k).key);
    }

    @Test public void theLanguageIsReadInAnyCase() {
        Config k = new Config();
        k.set("language", " ES ");
        assertEquals(Language.ES, k.language);
        k.set("language", "en");
        assertEquals(Language.EN, k.language);
    }

    @Test public void anUnknownLanguageKeepsTheCurrentOneAndEmptyRestoresCatalan() {
        Config k = new Config();
        k.set("language", "fr");
        assertEquals(Language.CA, k.language);
        k.set("language", "es");
        k.set("language", "klingon");
        assertEquals(Language.ES, k.language);
        k.set("language", "");
        assertEquals(Language.CA, k.language);
    }

    @Test public void choosingALanguageSurvivesARestartAndKeepsEveryOtherLine() throws Exception {
        java.io.File dir = java.nio.file.Files.createTempDirectory("conf").toFile();
        String before = "# mine\nnews_rss=https://example.org/rss # my feed\nclock=12\nfoo=bar\n";
        Config.write(dir, before);
        Config.write(dir, Config.setInText(Config.read(dir), "language", Language.EN));
        assertEquals(before + "language=en\n", Config.read(dir));
        Config k = Config.parse(dir);   // a restart reads the file again
        assertEquals(Language.EN, k.language);
        // the language changes neither the custom feed nor the clock format
        assertTrue(k.newsRssIsCustom());
        assertEquals("https://example.org/rss", NewsSource.resolve(k).url);
        assertEquals(Clock.H12, k.clock);
        Config.write(dir, Config.setInText(Config.read(dir), "language", Language.ES));
        assertEquals(before + "language=es\n", Config.read(dir));
    }
}
