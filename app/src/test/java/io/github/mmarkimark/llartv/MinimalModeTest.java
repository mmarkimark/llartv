package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;

/** Minimal mode (S5): one switch that overrides the content settings without rewriting them. */
public class MinimalModeTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** Every overridden setting at a value that shows something, so the override is visible. */
    static Config everythingOn() {
        Config k = new Config();
        for (String line : new String[]{"weather_home=all", "weather_screen=all", "news_home=all", "quote=on",
                "wallpaper=bing", "dock_tint=on", "music_screen=on", "team_id=83", "team_name=FC BARCELONA"}) {
            int eq = line.indexOf('=');
            k.set(line.substring(0, eq), line.substring(eq + 1));
        }
        return k;
    }

    @Test public void offLeavesEverySettingAsTheUserChoseIt() {
        Config k = everythingOn();
        assertFalse(MinimalMode.on(k));
        assertEquals(ContentPolicy.ALL, MinimalMode.weatherHome(k));
        assertEquals(ContentPolicy.ALL, MinimalMode.weatherScreen(k));
        assertEquals(ContentPolicy.ALL, MinimalMode.newsHome(k));
        assertTrue(MinimalMode.quote(k));
        assertEquals("bing", MinimalMode.wallpaper(k));
        assertTrue(MinimalMode.wallpaperShown(k));
        assertTrue(MinimalMode.dockTint(k));
        assertTrue(MinimalMode.musicScreen(k));
        assertEquals("83", MinimalMode.teamId(k));
    }

    @Test public void onOverridesEveryContentSurface() {
        Config k = everythingOn();
        k.set("minimal", "on");
        assertTrue(MinimalMode.on(k));
        assertEquals(ContentPolicy.NEVER, MinimalMode.weatherHome(k));
        assertEquals(ContentPolicy.NEVER, MinimalMode.weatherScreen(k));
        assertEquals(ContentPolicy.NEVER, MinimalMode.newsHome(k));
        assertFalse(MinimalMode.quote(k));
        assertEquals("", MinimalMode.wallpaper(k));       // nothing downloaded, no screensaver photos
        assertFalse(MinimalMode.wallpaperShown(k));       // black, even with a pushed wallpaper.jpg
        assertFalse(MinimalMode.dockTint(k));             // the neutral dock colour
        assertFalse(MinimalMode.musicScreen(k));
        assertEquals("", MinimalMode.teamId(k));          // no football card, no fixtures download
    }

    @Test public void onDoesNotRewriteTheStoredChoices() {
        Config k = everythingOn();
        k.set("minimal", "on");
        assertEquals(ContentPolicy.ALL, k.weatherHome);
        assertEquals(ContentPolicy.ALL, k.weatherScreen);
        assertEquals(ContentPolicy.ALL, k.newsHome);
        assertTrue(k.quote);
        assertEquals("bing", k.wallpaper);
        assertTrue(k.dockTint);
        assertTrue(k.musicScreen);
        assertEquals("83", k.teamId);
        assertEquals("FC BARCELONA", k.teamName);
    }

    @Test public void switchingItOffRestoresThePreviousChoicesExactly() throws Exception {
        File dir = tmp.newFolder();
        String mine = "weather_home=never\nweather_screen=all\nnews_home=morning\nquote=off\n"
                + "wallpaper=https://example.com/me.jpg\ndock_tint=off\nmusic_screen=on\nteam_id=364\n";
        Config.write(dir, Config.setInText(mine, "minimal", "on"));
        Config on = Config.parse(dir);
        assertTrue(MinimalMode.on(on));
        assertEquals(ContentPolicy.NEVER, MinimalMode.weatherScreen(on));
        assertFalse(MinimalMode.musicScreen(on));

        Config.write(dir, Config.setInText(Config.read(dir), "minimal", "off"));
        Config off = Config.parse(dir);
        assertFalse(MinimalMode.on(off));
        assertEquals(ContentPolicy.NEVER, MinimalMode.weatherHome(off));
        assertEquals(ContentPolicy.ALL, MinimalMode.weatherScreen(off));
        assertEquals(ContentPolicy.MORNING, MinimalMode.newsHome(off));
        assertFalse(MinimalMode.quote(off));
        assertEquals("https://example.com/me.jpg", MinimalMode.wallpaper(off));
        assertFalse(MinimalMode.dockTint(off));
        assertTrue(MinimalMode.musicScreen(off));
        assertEquals("364", MinimalMode.teamId(off));
        // the file still holds every other line as written
        assertEquals(mine + "minimal=off\n", Config.read(dir));
    }

    @Test public void theScreensaverHasNoPhotosAndNoMusicWhileOn() throws Exception {
        File dir = tmp.newFolder();
        for (String n : new String[]{"wallpaper-a.jpg", "wallpaper-b.jpg", "wallpaper.jpg"}) new File(dir, n).createNewFile();
        try (FileWriter w = new FileWriter(new File(dir, DailyWallpaper.LIST))) { w.write("wallpaper-a.jpg\nwallpaper-b.jpg\n"); }
        Config k = everythingOn();
        assertEquals(2, DailyWallpaper.slides(MinimalMode.wallpaper(k), dir).size());
        assertTrue(Music.shouldPlay(k));
        k.set("minimal", "on");
        assertEquals(0, DailyWallpaper.slides(MinimalMode.wallpaper(k), dir).size());   // clock and date on black
        assertFalse(Music.shouldPlay(k));
        k.set("minimal", "off");
        assertTrue(Music.shouldPlay(k));
    }

    @Test public void homeIsRebuiltWhenTheSwitchChanges() {
        Config before = everythingOn(), after = everythingOn();
        assertFalse(MinimalMode.homeNeedsRebuild(before, after));
        after.set("minimal", "on");
        assertTrue(MinimalMode.homeNeedsRebuild(before, after));
        assertTrue(MinimalMode.homeNeedsRebuild(after, before));
        // the earlier rebuild reasons still count: the tile sizes and the team
        Config cols = everythingOn(); cols.set("columns", "4");
        assertTrue(MinimalMode.homeNeedsRebuild(before, cols));
        Config team = everythingOn(); team.set("team_id", "364");
        assertTrue(MinimalMode.homeNeedsRebuild(before, team));
        // an overridden setting changed while minimal is on: Home does not rebuild for it
        Config on2 = everythingOn(); on2.set("minimal", "on"); on2.set("quote", "off");
        assertFalse(MinimalMode.homeNeedsRebuild(after, on2));
    }
}
