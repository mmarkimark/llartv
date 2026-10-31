package io.github.mmarkimark.llartv;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Where LlarTV keeps its own configuration: home.conf and the optional dock.txt live in app-private storage
 * (Context.getFilesDir()). Up to v1.4 they lived in the shared external app folder (getExternalFilesDir); the
 * first time they are needed, migrate moves them here once, byte for byte. The caches (weather, news, wallpapers)
 * and the pushed wallpaper images stay in external storage; nothing but these two files is moved.
 *
 * The move runs once. Its end is recorded by a marker file (MARKER) in the private folder; from then on the
 * external folder is never read nor touched again, so a home.conf or dock.txt that appears there later (an older
 * LlarTV put back, a manual push) is ignored and left where it is. Rules, in order:
 *  - marker present: done, nothing else is looked at;
 *  - a private home.conf present (moved earlier, or saved on a fresh install): done, and the marker is written. A
 *    private dock.txt alone does not count: an interrupted move may have left it, so the missing files are moved;
 *  - external folder missing, unreadable or not reported (storage not ready): unknown, nothing is written, retry;
 *  - otherwise each of dock.txt and home.conf found there and still missing privately is copied to a temporary private file (synced), renamed
 *    into place and checked to hold exactly the external bytes; then the marker is written and only then are the
 *    external copies deleted. Nothing found: the marker alone is written (fresh install);
 *  - any failure: the private copies made in that run are removed, no marker is written, every external file
 *    stays untouched, LlarTV keeps using the external folder, and the move is tried again the next time.
 * Settings are never read from two places: the files go together, and home.conf decides which folder is used.
 */
final class ConfigLocation {
    static final String DOCK = "dock.txt";
    static final String TMP = ".migrating";
    static final String MARKER = ".config-moved";
    /** dock.txt first: if home.conf then fails, the dock.txt of this run is rolled back with it. */
    private static final List<String> FILES = Arrays.asList(DOCK, Config.FILE);

    enum Result {
        /** Nothing (more) to move: the private folder holds the configuration. */
        DONE,
        /** A copy failed: the external files are untouched and still in use; retry later. */
        FAILED,
        /** External storage is unavailable and there is no private home.conf yet: it cannot be told apart from a
         *  fresh install, so nothing is written anywhere; retry later. */
        UNKNOWN
    }

    private static volatile File resolved;   // set once the migration is done; afterwards no file access here

    private ConfigLocation() {}

    /**
     * The folder of home.conf and dock.txt for this app, migrating them first if needed. Cheap once migrated (one
     * field read). Null only while external storage is unavailable before the first move (the v1.4 behaviour:
     * reads give the defaults and writes fail).
     */
    static File dir(Context c) {
        File r = resolved;
        if (r != null) return r;
        synchronized (ConfigLocation.class) {
            if (resolved != null) return resolved;
            File priv = c.getFilesDir();
            File dir = resolve(priv, c.getExternalFilesDir(null));
            if (dir != null && dir.equals(priv)) resolved = priv;
            return dir;
        }
    }

    /** Migrates, then returns the folder whose home.conf is the configuration (see Result). */
    static File resolve(File privateDir, File externalDir) {
        Result m = migrate(privateDir, externalDir);
        if (m == Result.DONE) return privateDir;
        if (m == Result.UNKNOWN) return null;
        // FAILED: the private folder is as before this run; use it only if it already held home.conf
        return privateDir != null && new File(privateDir, Config.FILE).isFile() ? privateDir : externalDir;
    }

    /** The one-time move of home.conf and dock.txt from externalDir to privateDir (rules in the class comment). */
    static Result migrate(File privateDir, File externalDir) {
        if (privateDir == null) return Result.FAILED;
        if (new File(privateDir, MARKER).exists()) return Result.DONE;   // the external folder is not looked at
        // Only a private home.conf proves an earlier move ended (it is copied last); a private dock.txt alone may be
        // what an interrupted move left, so the files still missing are moved below.
        if (new File(privateDir, Config.FILE).exists()) { mark(privateDir); return Result.DONE; }
        // Storage not ready (null, missing or unlistable) must not be taken for a fresh install.
        if (externalDir == null || !externalDir.isDirectory() || externalDir.list() == null) return Result.UNKNOWN;
        List<String> pending = new ArrayList<>();
        for (String name : FILES)
            if (new File(externalDir, name).isFile() && !new File(privateDir, name).exists()) pending.add(name);
        if (pending.isEmpty()) { mark(privateDir); return Result.DONE; }

        List<File> copied = new ArrayList<>();
        try {
            if (!privateDir.isDirectory() && !privateDir.mkdirs()) throw new IOException("cannot create " + privateDir);
            for (String name : pending) copied.add(copy(new File(externalDir, name), new File(privateDir, name)));
        } catch (IOException | RuntimeException e) {
            for (File f : copied) f.delete();   // only files created by this run, equal to their external copy
            return Result.FAILED;
        }
        // Every private copy is in place and verified: record the move, then the external copies can go. One that
        // cannot be deleted is harmless: the private copy and the marker now win.
        mark(privateDir);
        for (String name : FILES) {   // the moved files, and an old copy an interrupted move already moved in
            File old = new File(externalDir, name), now = new File(privateDir, name);
            if (pending.contains(name) || sameBytes(old, now)) old.delete();
        }
        return Result.DONE;
    }

    private static boolean sameBytes(File a, File b) {
        try { return a.isFile() && b.isFile() && Arrays.equals(Files.readAllBytes(a.toPath()), Files.readAllBytes(b.toPath())); }
        catch (IOException | RuntimeException e) { return false; }
    }

    /** Copies src to dest through a temporary file and a rename; dest then holds exactly the bytes of src. */
    private static File copy(File src, File dest) throws IOException {
        byte[] bytes = Files.readAllBytes(src.toPath());
        File tmp = new File(dest.getPath() + TMP);
        try {
            writeSynced(tmp, bytes);
            if (dest.exists()) throw new IOException(dest + " appeared");   // never replace a private file
            Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            if (tmp.isFile()) tmp.delete();   // a temporary file of our own; a directory in the way is left alone
            throw e;
        }
        verify(bytes, src, dest);
        return dest;
    }

    /**
     * Records that the move has run (atomically, synced). A failure is not an error: a private home.conf also
     * counts as moved, and without one the check simply runs again the next time.
     */
    private static void mark(File privateDir) {
        File marker = new File(privateDir, MARKER), tmp = new File(privateDir, MARKER + TMP);
        try {
            if (!privateDir.isDirectory() && !privateDir.mkdirs()) return;
            writeSynced(tmp, "moved\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), marker.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            if (tmp.isFile()) tmp.delete();
        }
    }

    /** Writes the bytes and forces them to storage before the caller renames the file into place. */
    private static void writeSynced(File f, byte[] bytes) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(bytes);
            out.getFD().sync();
        }
    }

    /** Checks that dest holds exactly bytes and that src still does; otherwise removes dest and throws. */
    static void verify(byte[] bytes, File src, File dest) throws IOException {
        try {
            if (!Arrays.equals(bytes, Files.readAllBytes(dest.toPath()))
                    || !Arrays.equals(bytes, Files.readAllBytes(src.toPath()))) {
                throw new IOException("the copy of " + src.getName() + " differs");
            }
        } catch (IOException | RuntimeException e) {
            dest.delete();   // created just above by this run
            throw e;
        }
    }
}
