package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class ConfigFileTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void writtenSettingsAreReadBack() throws Exception {
        File dir = tmp.getRoot();
        Config.write(dir, "columns=5\nnight=22:00-07:00\n");
        assertEquals("columns=5\nnight=22:00-07:00\n", Config.read(dir));
        Config k = Config.parse(dir);
        assertEquals(5, k.columns);
        assertEquals(TimeBands.Band.NIGHT, k.bands().at(22 * 60 + 30));
    }

    @Test public void writingReplacesTheWholeFile() throws Exception {
        File dir = tmp.getRoot();
        Config.write(dir, "columns=5\n");
        Config.write(dir, "dock_tint=off\n");
        assertEquals("dock_tint=off\n", Config.read(dir));
        assertEquals(6, Config.parse(dir).columns);
        assertFalse(new File(dir, "home.conf.tmp").exists());
    }

    @Test public void noFileMeansDefaults() {
        File dir = tmp.getRoot();
        assertEquals("", Config.read(dir));
        assertEquals("bing", Config.parse(dir).wallpaper);
    }
}
