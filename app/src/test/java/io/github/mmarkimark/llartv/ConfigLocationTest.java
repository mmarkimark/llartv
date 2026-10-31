package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** The one-time move of home.conf and dock.txt from the shared external app folder to app-private storage. */
public class ConfigLocationTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** A real v1.4 file: values, comments, blank lines, an unknown key, no final newline, a non-ASCII value. */
    static final String CONF = "# LlarTV settings - see README\ncolumns=5\n\nlanguage=es  # mine\n"
            + "team_name=ATLÈTIC\nsomething_new=kept\n  # indented comment\r\nnight=22:00-07:00";
    static final String DOCK = "# my dock\ncom.google.android.youtube.tv\ncom.netflix.ninja\n";

    File priv, ext;

    @Before public void dirs() throws Exception {
        priv = tmp.newFolder("files");
        ext = tmp.newFolder("external");
    }

    static void put(File dir, String name, String text) throws Exception {
        Files.write(new File(dir, name).toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] bytes(File dir, String name) throws Exception {
        return Files.readAllBytes(new File(dir, name).toPath());
    }

    @Test public void aFreshInstallHasNothingToMoveAndUsesPrivateStorage() {
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv, ext));
        assertEquals(priv, ConfigLocation.resolve(priv, ext));
        assertArrayEquals(new String[]{ConfigLocation.MARKER}, priv.list());   // only the record of the move
        assertEquals(0, ext.list().length);
    }

    @Test public void anUpgradeMovesHomeConfByteForByte() throws Exception {
        put(ext, Config.FILE, CONF);
        byte[] before = bytes(ext, Config.FILE);
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv, ext));
        assertArrayEquals(before, bytes(priv, Config.FILE));
        assertFalse(new File(ext, Config.FILE).exists());
        assertEquals(2, priv.list().length);   // home.conf and the marker: no temporary file left behind
        assertTrue(new File(priv, ConfigLocation.MARKER).isFile());
    }

    @Test public void anUpgradeMovesDockTxtToo() throws Exception {
        put(ext, Config.FILE, CONF);
        put(ext, ConfigLocation.DOCK, DOCK);
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv, ext));
        assertEquals(DOCK, new String(bytes(priv, ConfigLocation.DOCK), StandardCharsets.UTF_8));
        assertEquals(CONF, new String(bytes(priv, Config.FILE), StandardCharsets.UTF_8));
        assertFalse(new File(ext, ConfigLocation.DOCK).exists());
        assertFalse(new File(ext, Config.FILE).exists());
    }

    @Test public void onlyADockTxtIsMovedAlone() throws Exception {
        put(ext, ConfigLocation.DOCK, DOCK);
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv, ext));
        assertEquals(DOCK, new String(bytes(priv, ConfigLocation.DOCK), StandardCharsets.UTF_8));
        assertFalse(new File(priv, Config.FILE).exists());
        assertFalse(new File(ext, ConfigLocation.DOCK).exists());
    }

    @Test public void theCachesAndPushedImagesStayInExternalStorage() throws Exception {
        put(ext, Config.FILE, CONF);
        put(ext, "wallpaper.jpg", "jpeg");
        put(ext, "weather.json", "{}");
        ConfigLocation.migrate(priv, ext);
        assertTrue(new File(ext, "wallpaper.jpg").exists());
        assertTrue(new File(ext, "weather.json").exists());
        assertFalse(new File(priv, "wallpaper.jpg").exists());
    }

    @Test public void alreadyMigratedKeepsThePrivateFileAndLeavesAReappearedExternalOneAlone() throws Exception {
        put(priv, Config.FILE, "columns=4\n");
        put(ext, Config.FILE, CONF);
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv, ext));
        assertEquals("columns=4\n", new String(bytes(priv, Config.FILE), StandardCharsets.UTF_8));
        assertEquals(CONF, new String(bytes(ext, Config.FILE), StandardCharsets.UTF_8));   // ignored, not deleted
        assertEquals(priv, ConfigLocation.resolve(priv, ext));
        assertEquals(4, Config.parse(ConfigLocation.resolve(priv, ext)).columns);
    }

    @Test public void runningAgainAfterAMoveChangesNothing() throws Exception {
        put(ext, Config.FILE, CONF);
        ConfigLocation.migrate(priv, ext);
        Config.write(priv, Config.setInText(Config.read(priv), "columns", "7"));
        byte[] after = bytes(priv, Config.FILE);
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv, ext));
        assertArrayEquals(after, bytes(priv, Config.FILE));
        assertFalse(new File(ext, Config.FILE).exists());
    }

    @Test public void configReadsThePrivateLocationAfterTheMove() throws Exception {
        put(ext, Config.FILE, CONF);
        File dir = ConfigLocation.resolve(priv, ext);
        assertEquals(priv, dir);
        Config k = Config.parse(dir);
        assertEquals(5, k.columns);
        assertEquals("es", k.language);
        assertEquals("ATLÈTIC", k.teamName);
        assertEquals(CONF, Config.read(dir));
    }

    @Test public void aResetAfterTheMoveWorksOnThePrivateFileAndKeepsCommentsAndUnknownLines() throws Exception {
        put(ext, Config.FILE, CONF);
        File dir = ConfigLocation.resolve(priv, ext);
        assertEquals(SettingsReset.Result.RESET, SettingsReset.choose(dir, SettingsReset.CONFIRM));
        String left = Config.read(priv);
        assertTrue(left.contains("# LlarTV settings - see README"));
        assertTrue(left.contains("something_new=kept"));
        assertFalse(left.contains("columns=5"));
        assertFalse(new File(ext, Config.FILE).exists());   // the reset never brings the external copy back
    }

    @Test public void anUnreadableExternalFileIsLeftUntouchedAndStillUsed() throws Exception {
        put(ext, Config.FILE, CONF);
        File f = new File(ext, Config.FILE);
        assertTrue(f.setReadable(false));
        assumeFalse("running as root: the file stays readable", f.canRead());
        try {
            assertEquals(ConfigLocation.Result.FAILED, ConfigLocation.migrate(priv, ext));
            assertEquals(ext, ConfigLocation.resolve(priv, ext));   // the settings stay where they are
            assertEquals(0, priv.list().length);                    // no partial private file
        } finally {
            assertTrue(f.setReadable(true));
        }
        assertEquals(CONF, new String(bytes(ext, Config.FILE), StandardCharsets.UTF_8));
        // the next start retries and succeeds
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv, ext));
        assertEquals(CONF, new String(bytes(priv, Config.FILE), StandardCharsets.UTF_8));
    }

    @Test public void aReadOnlyPrivateFolderLeavesBothFilesWhereTheyAre() throws Exception {
        put(ext, Config.FILE, CONF);
        put(ext, ConfigLocation.DOCK, DOCK);
        assertTrue(priv.setWritable(false));
        assumeFalse("running as root: the folder stays writable", priv.canWrite());
        try {
            assertEquals(ConfigLocation.Result.FAILED, ConfigLocation.migrate(priv, ext));
            assertEquals(ext, ConfigLocation.resolve(priv, ext));
            assertEquals(0, priv.list().length);
        } finally {
            assertTrue(priv.setWritable(true));
        }
        assertEquals(CONF, new String(bytes(ext, Config.FILE), StandardCharsets.UTF_8));
        assertEquals(DOCK, new String(bytes(ext, ConfigLocation.DOCK), StandardCharsets.UTF_8));
    }

    @Test public void aFailedHomeConfCopyRollsBackTheDockTxtCopiedInTheSameRun() throws Exception {
        put(ext, ConfigLocation.DOCK, DOCK);
        put(ext, Config.FILE, CONF);
        File f = new File(ext, Config.FILE);
        assertTrue(f.setReadable(false));
        assumeFalse("running as root: the file stays readable", f.canRead());
        try {
            assertEquals(ConfigLocation.Result.FAILED, ConfigLocation.migrate(priv, ext));
            assertEquals(0, priv.list().length);   // dock.txt was not kept apart from its home.conf
        } finally {
            assertTrue(f.setReadable(true));
        }
        assertEquals(DOCK, new String(bytes(ext, ConfigLocation.DOCK), StandardCharsets.UTF_8));
    }

    @Test public void aBlockedDestinationKeepsTheExternalFileAndLeavesNoTemporaryFile() throws Exception {
        put(ext, Config.FILE, CONF);
        File blocker = new File(priv, Config.FILE + ConfigLocation.TMP);
        assertTrue(blocker.mkdir());
        Files.write(new File(blocker, "keep").toPath(), new byte[]{1});
        assertEquals(ConfigLocation.Result.FAILED, ConfigLocation.migrate(priv, ext));
        assertFalse(new File(priv, Config.FILE).exists());
        assertTrue(new File(blocker, "keep").exists());   // a directory that is not ours is not removed
        assertEquals(CONF, new String(bytes(ext, Config.FILE), StandardCharsets.UTF_8));
    }

    @Test public void aMissingPrivateFolderIsCreated() throws Exception {
        File missing = new File(tmp.getRoot(), "not-yet");
        put(ext, Config.FILE, CONF);
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(missing, ext));
        assertEquals(CONF, new String(bytes(missing, Config.FILE), StandardCharsets.UTF_8));
    }

    @Test public void unavailableExternalStorageIsNotMistakenForAFreshInstall() throws Exception {
        assertEquals(ConfigLocation.Result.UNKNOWN, ConfigLocation.migrate(priv, null));
        assertNull(ConfigLocation.resolve(priv, null));   // nothing is written where it could hide the old file
        put(priv, Config.FILE, "columns=4\n");
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv, null));
        assertEquals(priv, ConfigLocation.resolve(priv, null));
    }

    // ---- B1: once the move has run, the external folder is never looked at again

    @Test public void aFreshInstallIgnoresAHomeConfThatAppearsLaterInTheOldFolder() throws Exception {
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv, ext));
        put(ext, Config.FILE, CONF);
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv, ext));
        assertEquals(priv, ConfigLocation.resolve(priv, ext));
        assertFalse(new File(priv, Config.FILE).exists());
        assertEquals(CONF, new String(bytes(ext, Config.FILE), StandardCharsets.UTF_8));   // left untouched
        assertEquals(6, Config.parse(ConfigLocation.resolve(priv, ext)).columns);
    }

    @Test public void aFreshInstallIgnoresADockTxtThatAppearsLaterInTheOldFolder() throws Exception {
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv, ext));
        put(ext, ConfigLocation.DOCK, DOCK);
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv, ext));
        assertFalse(new File(priv, ConfigLocation.DOCK).exists());
        assertEquals(DOCK, new String(bytes(ext, ConfigLocation.DOCK), StandardCharsets.UTF_8));
    }

    @Test public void withTheMarkerTheOldFolderIsNeverRead() throws Exception {
        put(priv, ConfigLocation.MARKER, "");
        put(ext, Config.FILE, CONF);
        File f = new File(ext, Config.FILE);
        assertTrue(f.setReadable(false));
        assumeFalse("running as root: the file stays readable", f.canRead());
        try {
            assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv, ext));   // a read would fail
            assertEquals(priv, ConfigLocation.resolve(priv, ext));
        } finally {
            assertTrue(f.setReadable(true));
        }
        assertFalse(new File(priv, Config.FILE).exists());
        assertEquals(CONF, new String(bytes(ext, Config.FILE), StandardCharsets.UTF_8));
    }

    @Test public void anUpgradeThenAReappearingOldCopyIsIgnored() throws Exception {
        put(ext, Config.FILE, CONF);
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv, ext));
        put(ext, Config.FILE, "columns=3\n");
        put(ext, ConfigLocation.DOCK, DOCK);
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv, ext));
        assertEquals(CONF, new String(bytes(priv, Config.FILE), StandardCharsets.UTF_8));
        assertFalse(new File(priv, ConfigLocation.DOCK).exists());
        assertEquals("columns=3\n", new String(bytes(ext, Config.FILE), StandardCharsets.UTF_8));
        assertEquals(DOCK, new String(bytes(ext, ConfigLocation.DOCK), StandardCharsets.UTF_8));
    }

    @Test public void anInstallOfThePreviousBuildWithAPrivateHomeConfIsTreatedAsMovedAndMarked() throws Exception {
        put(priv, Config.FILE, "columns=4\n");   // moved (or saved) by a build without the marker
        put(ext, ConfigLocation.DOCK, DOCK);
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv, ext));
        assertFalse(new File(priv, ConfigLocation.DOCK).exists());
        assertEquals(DOCK, new String(bytes(ext, ConfigLocation.DOCK), StandardCharsets.UTF_8));
        assertTrue(new File(priv, ConfigLocation.MARKER).isFile());
        assertEquals("columns=4\n", new String(bytes(priv, Config.FILE), StandardCharsets.UTF_8));
    }

    @Test public void aMoveInterruptedAfterDockTxtStillMovesHomeConf() throws Exception {
        // killed after dock.txt was moved in but before home.conf: only a private home.conf proves the move ended
        put(priv, ConfigLocation.DOCK, DOCK);
        put(ext, ConfigLocation.DOCK, DOCK);
        put(ext, Config.FILE, CONF);
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv, ext));
        assertEquals(CONF, new String(bytes(priv, Config.FILE), StandardCharsets.UTF_8));
        assertEquals(DOCK, new String(bytes(priv, ConfigLocation.DOCK), StandardCharsets.UTF_8));
        assertFalse(new File(ext, Config.FILE).exists());
        assertTrue(new File(priv, ConfigLocation.MARKER).isFile());
    }

    @Test public void aFailedMoveWritesNoMarkerSoItIsRetried() throws Exception {
        put(ext, Config.FILE, CONF);
        File blocker = new File(priv, Config.FILE + ConfigLocation.TMP);
        assertTrue(blocker.mkdir());
        Files.write(new File(blocker, "keep").toPath(), new byte[]{1});
        assertEquals(ConfigLocation.Result.FAILED, ConfigLocation.migrate(priv, ext));
        assertFalse(new File(priv, ConfigLocation.MARKER).exists());
        assertEquals(ext, ConfigLocation.resolve(priv, ext));
    }

    // ---- N2: an old folder that is not there (storage not ready) is not taken for a fresh install

    @Test public void anOldFolderThatIsNotThereIsNotAFreshInstall() throws Exception {
        File gone = new File(tmp.getRoot(), "not-mounted");
        assertEquals(ConfigLocation.Result.UNKNOWN, ConfigLocation.migrate(priv, gone));
        assertNull(ConfigLocation.resolve(priv, gone));
        assertFalse(new File(priv, ConfigLocation.MARKER).exists());
        File notADir = new File(tmp.getRoot(), "a-file");
        assertTrue(notADir.createNewFile());
        assertEquals(ConfigLocation.Result.UNKNOWN, ConfigLocation.migrate(priv, notADir));
    }

    @Test public void anUnlistableOldFolderIsNotAFreshInstall() throws Exception {
        assertTrue(ext.setReadable(false));
        assumeFalse("running as root: the folder stays readable", ext.canRead());
        try {
            assertEquals(ConfigLocation.Result.UNKNOWN, ConfigLocation.migrate(priv, ext));
            assertFalse(new File(priv, ConfigLocation.MARKER).exists());
        } finally {
            assertTrue(ext.setReadable(true));
        }
    }

    @Test public void anOldFolderThatIsNotThereIsFineOnceMovedOrMarked() throws Exception {
        File gone = new File(tmp.getRoot(), "not-mounted");
        put(priv, ConfigLocation.MARKER, "");
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv, gone));
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv, null));
        File priv2 = tmp.newFolder("files2");
        put(priv2, Config.FILE, "columns=4\n");
        assertEquals(ConfigLocation.Result.DONE, ConfigLocation.migrate(priv2, gone));
        assertEquals(priv2, ConfigLocation.resolve(priv2, gone));
    }

    // ---- N4

    @Test public void anUnreadableOldDockTxtAloneFailsAndIsKept() throws Exception {
        put(ext, ConfigLocation.DOCK, DOCK);
        File f = new File(ext, ConfigLocation.DOCK);
        assertTrue(f.setReadable(false));
        assumeFalse("running as root: the file stays readable", f.canRead());
        try {
            assertEquals(ConfigLocation.Result.FAILED, ConfigLocation.migrate(priv, ext));
            assertEquals(0, priv.list().length);   // no copy, no marker
            assertEquals(ext, ConfigLocation.resolve(priv, ext));
        } finally {
            assertTrue(f.setReadable(true));
        }
        assertEquals(DOCK, new String(bytes(ext, ConfigLocation.DOCK), StandardCharsets.UTF_8));
    }

    @Test public void aCopyThatDiffersIsRemovedAndReported() throws Exception {
        put(ext, Config.FILE, CONF);
        put(priv, Config.FILE, CONF + "x");   // what a bad copy would hold
        try {
            ConfigLocation.verify(CONF.getBytes(StandardCharsets.UTF_8), new File(ext, Config.FILE), new File(priv, Config.FILE));
            org.junit.Assert.fail("a copy that differs is not accepted");
        } catch (java.io.IOException expected) {}
        assertFalse(new File(priv, Config.FILE).exists());
        assertEquals(CONF, new String(bytes(ext, Config.FILE), StandardCharsets.UTF_8));
    }

    @Test public void aSourceChangedDuringTheCopyIsReportedToo() throws Exception {
        put(ext, Config.FILE, CONF + "y");    // the old file changed after it was read
        put(priv, Config.FILE, CONF);
        try {
            ConfigLocation.verify(CONF.getBytes(StandardCharsets.UTF_8), new File(ext, Config.FILE), new File(priv, Config.FILE));
            org.junit.Assert.fail("a source that changed is not accepted");
        } catch (java.io.IOException expected) {}
        assertFalse(new File(priv, Config.FILE).exists());
        assertTrue(new File(ext, Config.FILE).exists());
    }

    @Test public void aMissingDirectoryReadsAsNoSettingsAndCannotBeWritten() {
        assertEquals("", Config.read(null));
        assertEquals(6, Config.parse(null).columns);
        try {
            Config.write(null, "columns=5\n");
            org.junit.Assert.fail("no directory to write to");
        } catch (java.io.IOException expected) {}
    }
}
