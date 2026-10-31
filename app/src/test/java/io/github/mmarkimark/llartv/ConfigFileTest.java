package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

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

    @Test public void aFailedMoveKeepsTheOldFileAndLeavesNoTemporaryFile() throws Exception {
        File dir = tmp.getRoot();
        File conf = new File(dir, Config.FILE);
        assertTrue(conf.mkdir());   // a non-empty directory cannot be replaced by the move
        Files.write(new File(conf, "keep").toPath(), new byte[]{1});
        try {
            Config.write(dir, "columns=5\n");
            fail("the move cannot succeed");
        } catch (IOException expected) {}
        assertTrue(new File(conf, "keep").exists());
        assertFalse(new File(dir, Config.FILE + ".tmp").exists());   // no half-finished file is left behind
    }

    @Test public void aFailedTemporaryWriteKeepsTheOldFile() throws Exception {
        File dir = tmp.getRoot();
        Config.write(dir, "columns=5\n");
        File blocker = new File(dir, Config.FILE + ".tmp");
        assertTrue(blocker.mkdir());
        Files.write(new File(blocker, "keep").toPath(), new byte[]{1});
        try {
            Config.write(dir, "columns=4\n");
            fail("the temporary file cannot be written");
        } catch (IOException expected) {}
        assertEquals("columns=5\n", Config.read(dir));
        assertTrue(new File(blocker, "keep").exists());   // only a temporary file of its own is ever removed
    }

    @Test public void noFileMeansDefaults() {
        File dir = tmp.getRoot();
        assertEquals("", Config.read(dir));
        assertEquals("bing", Config.parse(dir).wallpaper);
    }

    @Test public void setInTextReplacesTheValueAndKeepsEveryOtherLine() {
        String text = "# LlarTV settings\ncolumns=5\nclock=system\nteam_name=LIVERPOOL FC\nfuture_key=1\n";
        assertEquals("# LlarTV settings\ncolumns=5\nclock=24\nteam_name=LIVERPOOL FC\nfuture_key=1\n",
                Config.setInText(text, "clock", "24"));
    }

    @Test public void setInTextAppendsAMissingKeyWithATrailingNewline() {
        assertEquals("columns=5\nclock=24\n", Config.setInText("columns=5\n", "clock", "24"));
        assertEquals("columns=5\nclock=24\n", Config.setInText("columns=5", "clock", "24"));   // no trailing newline: one is added
    }

    @Test public void setInTextOnAnEmptyFileStartsTheFile() {
        assertEquals("clock=24\n", Config.setInText("", "clock", "24"));   // no leading blank line
    }

    @Test public void setInTextMatchesTheKeyCaseInsensitivelyAndOnlyTheFirstLine() {
        String text = "# clock=12 commented out\nClock=system\nclock=12\n";
        assertEquals("# clock=12 commented out\nCLOCK=24\nclock=12\n", Config.setInText(text, "CLOCK", "24"));
    }

    @Test public void setInTextRoundTripsThroughTheFile() throws Exception {
        File dir = tmp.getRoot();
        Config.write(dir, "columns=5\nclock=system\n");
        Config.write(dir, Config.setInText(Config.read(dir), "clock", "24"));
        assertEquals("columns=5\nclock=24\n", Config.read(dir));
        Config k = Config.parse(dir);
        assertEquals("24", k.clock);
        assertEquals(5, k.columns);
    }
}
