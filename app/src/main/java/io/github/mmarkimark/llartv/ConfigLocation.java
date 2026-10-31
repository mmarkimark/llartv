package io.github.mmarkimark.llartv;

import android.content.Context;

import java.io.File;
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
 * Migration rules, per file:
 *  - private copy missing and external copy present: copy to a temporary private file, rename it into place,
 *    check it holds exactly the external bytes, and only then delete the external copy;
 *  - private copy present: already migrated, nothing happens; an external copy that reappears (an older LlarTV
 *    put back) is ignored and left where it is, never merged nor deleted;
 *  - any failure: the private copies made in that run are removed, every external file stays untouched, LlarTV
 *    keeps using the external folder, and the move is tried again the next time.
 * Settings are never read from two places: the files go together, and home.conf decides which folder is used.
 */
final class ConfigLocation {
    static final String DOCK = "dock.txt";
    static final String TMP = ".migrating";
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
        if (externalDir == null) return new File(privateDir, Config.FILE).exists() ? Result.DONE : Result.UNKNOWN;
        List<String> pending = new ArrayList<>();
        for (String name : FILES) {
            if (!new File(privateDir, name).exists() && new File(externalDir, name).isFile()) pending.add(name);
        }
        if (pending.isEmpty()) return Result.DONE;

        List<File> copied = new ArrayList<>();
        try {
            if (!privateDir.isDirectory() && !privateDir.mkdirs()) throw new IOException("cannot create " + privateDir);
            for (String name : pending) copied.add(copy(new File(externalDir, name), new File(privateDir, name)));
        } catch (IOException | RuntimeException e) {
            for (File f : copied) f.delete();   // only files created by this run, equal to their external copy
            return Result.FAILED;
        }
        // Every private copy is in place and verified: the external copies can go. One that cannot be deleted is
        // harmless, the private copy now wins.
        for (String name : pending) new File(externalDir, name).delete();
        return Result.DONE;
    }

    /** Copies src to dest through a temporary file and a rename; dest then holds exactly the bytes of src. */
    private static File copy(File src, File dest) throws IOException {
        byte[] bytes = Files.readAllBytes(src.toPath());
        File tmp = new File(dest.getPath() + TMP);
        try {
            Files.write(tmp.toPath(), bytes);
            if (dest.exists()) throw new IOException(dest + " appeared");   // never replace a private file
            Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            if (tmp.isFile()) tmp.delete();   // a temporary file of our own; a directory in the way is left alone
            throw e;
        }
        try {
            if (!Arrays.equals(bytes, Files.readAllBytes(dest.toPath()))
                    || !Arrays.equals(bytes, Files.readAllBytes(src.toPath()))) {
                throw new IOException("the copy of " + src.getName() + " differs");
            }
        } catch (IOException | RuntimeException e) {
            dest.delete();   // created just above by this run
            throw e;
        }
        return dest;
    }
}
