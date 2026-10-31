package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The settings reset (issue #9): only the documented keys of home.conf go, so their defaults come back; every
 * comment, unknown line and other file stays. Cancel and a failed write leave home.conf exactly as it was.
 */
public class SettingsResetTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** Every documented key with a value that is not its default. */
    static final String ALL_CHANGED = String.join("\n",
            "columns=5", "dock=com.netflix.ninja", "hidden=com.example.hidden", "order=com.b,com.a",
            "team_id=83", "team_name=FC BARCELONA", "wallpaper=https://example.com/me.jpg", "wallpaper_minutes=0",
            "location=41.98,2.82", "language=en", "news_source=bbc-world", "news_rss=https://example.com/rss",
            "music_screen=on", "music_url=https://example.com/radio", "clock_screen=off", "motion_screen=off",
            "morning=07:00-10:00", "night=off", "dock_tint=off", "clock=24", "weather_home=all",
            "weather_screen=never", "news_home=never") + "\n";

    // ---------------------------------------------------------------- the text rule

    @Test public void knownKeysGoAndCommentsAndUnknownLinesStayVerbatim() {
        String text = "# LlarTV settings\n"
                + "columns=5\n"
                + "future_key = kept as is  # with its comment\n"
                + "\n"
                + "# clock=12 is a comment, not a setting\n"
                + "  Clock = 24   # case and spaces do not hide a known key\n"
                + "not a setting line\n"
                + "language=es\n";
        assertEquals("# LlarTV settings\n"
                + "future_key = kept as is  # with its comment\n"
                + "\n"
                + "# clock=12 is a comment, not a setting\n"
                + "not a setting line\n", SettingsReset.clear(text));
    }

    @Test public void everyRepeatOfAKnownKeyGoes() {
        assertEquals("x=1\n", SettingsReset.clear("clock=24\nx=1\nclock=12\nCLOCK=system\n"));
    }

    @Test public void theLastLineWithoutANewlineAndCarriageReturnsAreHandled() {
        assertEquals("x=1", SettingsReset.clear("x=1\ncolumns=5"));
        assertEquals("x=1\r\n", SettingsReset.clear("columns=5\r\nx=1\r\n"));
        assertEquals("", SettingsReset.clear("columns=5\n"));
        assertEquals("", SettingsReset.clear(""));
    }

    @Test public void theDocumentedDefaultsComeBack() throws Exception {
        File dir = tmp.getRoot();
        Config.write(dir, "# mine\n" + ALL_CHANGED + "future_key=1\n");
        assertEquals(SettingsReset.Result.RESET, SettingsReset.choose(dir, SettingsReset.CONFIRM));
        assertEquals("# mine\nfuture_key=1\n", Config.read(dir));
        assertSameFields(new Config(), Config.parse(dir));   // as after a restart: read again from the file
    }

    @Test public void theNonDefaultFixtureChangesEveryField() {
        // so theDocumentedDefaultsComeBack would notice a key that the reset forgot
        Config changed = new Config();
        for (String line : ALL_CHANGED.split("\n")) {
            int eq = line.indexOf('=');
            changed.set(line.substring(0, eq), line.substring(eq + 1));
        }
        Config defaults = new Config();
        for (Field f : fields()) {
            try { assertFalse(f.getName(), String.valueOf(f.get(defaults)).equals(String.valueOf(f.get(changed)))); }
            catch (IllegalAccessException e) { throw new AssertionError(e); }
        }
    }

    @Test public void theKeysAreExactlyTheDocumentedOnes() throws Exception {
        String readme = new String(Files.readAllBytes(readme().toPath()), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("(?m)^\\| `([a-z_]+)` \\|").matcher(readme);
        TreeSet<String> documented = new TreeSet<>();
        while (m.find()) documented.add(m.group(1));
        assertEquals(documented, new TreeSet<>(SettingsReset.KEYS));
        TreeSet<String> fixture = new TreeSet<>();
        for (String line : ALL_CHANGED.split("\n")) fixture.add(line.substring(0, line.indexOf('=')));
        assertEquals(fixture, new TreeSet<>(SettingsReset.KEYS));
    }

    // ---------------------------------------------------------------- the confirmation

    @Test public void cancelIsTheFirstChoiceAndTheDefaultFocus() {
        assertEquals(0, SettingsReset.CANCEL);
        assertEquals(1, SettingsReset.CONFIRM);
        assertEquals(SettingsReset.CANCEL, SettingsReset.DEFAULT_CHOICE);
    }

    @Test public void cancelChangesNothing() throws Exception {
        File dir = tmp.getRoot();
        Config.write(dir, ALL_CHANGED);
        File conf = new File(dir, Config.FILE);
        assertTrue(conf.setLastModified(1_000_000_000L));
        byte[] before = Files.readAllBytes(conf.toPath());
        assertEquals(SettingsReset.Result.CANCELLED, SettingsReset.choose(dir, SettingsReset.CANCEL));
        assertArrayEquals(before, Files.readAllBytes(conf.toPath()));
        assertEquals(1_000_000_000L, conf.lastModified());
        assertFalse(new File(dir, Config.FILE + ".tmp").exists());
        assertEquals("en", Config.parse(dir).language);
    }

    @Test public void cancelWithoutAFileCreatesNone() throws Exception {
        File dir = tmp.getRoot();
        assertEquals(SettingsReset.Result.CANCELLED, SettingsReset.choose(dir, SettingsReset.CANCEL));
        assertEquals(0, dir.list().length);
    }

    @Test public void alreadyDefaultWritesNothing() throws Exception {
        File dir = tmp.getRoot();
        assertEquals(SettingsReset.Result.ALREADY_DEFAULT, SettingsReset.choose(dir, SettingsReset.CONFIRM));
        assertFalse(new File(dir, Config.FILE).exists());   // no file is created for nothing
        Config.write(dir, "# only a comment\nfuture_key=1\n");
        File conf = new File(dir, Config.FILE);
        assertTrue(conf.setLastModified(1_000_000_000L));
        assertEquals(SettingsReset.Result.ALREADY_DEFAULT, SettingsReset.choose(dir, SettingsReset.CONFIRM));
        assertEquals(1_000_000_000L, conf.lastModified());
    }

    // ---------------------------------------------------------------- what stays

    @Test public void otherFilesAndCachesAreLeftAlone() throws Exception {
        File dir = tmp.getRoot();
        Config.write(dir, ALL_CHANGED);
        Map<String, String> sentinels = new TreeMap<>();
        sentinels.put("dock.txt", "com.netflix.ninja\n");
        sentinels.put("wallpaper.jpg", "custom wallpaper bytes");
        sentinels.put("wallpapers/list.txt", "a.jpg\n");
        sentinels.put("wallpapers/a.jpg", "image bytes");
        sentinels.put("music/track.mp3", "music bytes");
        for (Map.Entry<String, String> e : sentinels.entrySet()) {
            File f = new File(dir, e.getKey());
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), e.getValue().getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(SettingsReset.Result.RESET, SettingsReset.choose(dir, SettingsReset.CONFIRM));
        for (Map.Entry<String, String> e : sentinels.entrySet()) {
            assertEquals(e.getKey(), e.getValue(),
                    new String(Files.readAllBytes(new File(dir, e.getKey()).toPath()), StandardCharsets.UTF_8));
        }
        TreeSet<String> names = new TreeSet<>(java.util.Arrays.asList(dir.list()));
        assertEquals(new TreeSet<>(java.util.Arrays.asList(Config.FILE, "dock.txt", "wallpaper.jpg", "wallpapers", "music")), names);
    }

    @Test public void scriptsCanConfigureAgainAfterAReset() throws Exception {
        File dir = tmp.getRoot();
        Config.write(dir, ALL_CHANGED);
        SettingsReset.choose(dir, SettingsReset.CONFIRM);
        Config.write(dir, Config.setInText(Config.read(dir), "columns", "4"));   // what the settings and config.sh do
        assertEquals("columns=4\n", Config.read(dir));
        assertEquals(4, Config.parse(dir).columns);
    }

    @Test public void homeReloadsTheAppsAfterAReset() throws Exception {
        File dir = tmp.getRoot();
        Config.write(dir, ALL_CHANGED);
        Config before = Config.parse(dir);
        SettingsReset.choose(dir, SettingsReset.CONFIRM);
        Config after = Config.parse(dir);
        assertTrue(HomeActivity.appsChanged(before, after));
        assertFalse(HomeActivity.appsChanged(after, Config.parse(dir)));   // an unchanged file reloads nothing
        assertTrue(before.columns != after.columns);                       // so Home is rebuilt for the tiles
        assertEquals(Language.CA, after.language);
    }

    // ---------------------------------------------------------------- failures

    @Test public void aFailedWriteLeavesTheFileAsItWas() throws Exception {
        File dir = tmp.getRoot();
        Config.write(dir, ALL_CHANGED);
        File conf = new File(dir, Config.FILE);
        byte[] before = Files.readAllBytes(conf.toPath());
        File blocker = new File(dir, Config.FILE + ".tmp");   // a directory where the temporary file must go
        assertTrue(blocker.mkdir());
        Files.write(new File(blocker, "keep").toPath(), new byte[]{1});
        try {
            SettingsReset.choose(dir, SettingsReset.CONFIRM);
            fail("the write cannot succeed");
        } catch (IOException expected) {}
        assertArrayEquals(before, Files.readAllBytes(conf.toPath()));
        assertEquals("en", Config.parse(dir).language);
        assertTrue(new File(blocker, "keep").exists());   // not ours to delete
    }

    @Test public void anUnreadableFileIsNotResetToEmpty() throws Exception {
        File dir = tmp.getRoot();
        File conf = new File(dir, Config.FILE);
        assertTrue(conf.mkdir());   // exists, but cannot be read as a file
        Files.write(new File(conf, "keep").toPath(), new byte[]{1});
        try {
            SettingsReset.choose(dir, SettingsReset.CONFIRM);
            fail("an unreadable home.conf must not be replaced");
        } catch (IOException expected) {}
        assertTrue(conf.isDirectory());
        assertTrue(new File(conf, "keep").exists());
        assertFalse(new File(dir, Config.FILE + ".tmp").exists());
    }

    // ---------------------------------------------------------------- helpers

    static java.util.List<Field> fields() {
        java.util.List<Field> out = new java.util.ArrayList<>();
        for (Field f : Config.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            out.add(f);
        }
        return out;
    }

    static void assertSameFields(Config expected, Config actual) throws IllegalAccessException {
        for (Field f : fields()) assertEquals(f.getName(), String.valueOf(f.get(expected)), String.valueOf(f.get(actual)));
    }

    static File readme() {
        File d = new File("").getAbsoluteFile();
        while (d != null && !new File(d, "README.md").exists()) d = d.getParentFile();
        if (d == null) throw new AssertionError("README.md not found");
        return new File(d, "README.md");
    }
}
