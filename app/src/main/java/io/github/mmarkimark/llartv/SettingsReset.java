package io.github.mmarkimark.llartv;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Resets LlarTV's own settings (issue #9): every line of home.conf that sets a documented key is removed, so its
 * default comes back, the same as when the line was never there. Comments, blank lines, unknown keys and any other
 * line are kept verbatim; nothing else is touched: not dock.txt, the downloaded or pushed images, the music, the
 * other caches, the installed apps, nor any Android setting such as the launcher or the screensaver.
 *
 * The confirmation offers Cancel first, and Cancel is where the focus starts. Cancel does no file access at all;
 * Confirm writes through Config.write, so home.conf is either the old file or the new one, never half of one.
 */
final class SettingsReset {
    /** The keys Config understands and the README documents; the test keeps the three lists equal. */
    static final List<String> KEYS = Collections.unmodifiableList(Arrays.asList(
            "columns", "dock", "hidden", "order", "team_id", "team_name", "wallpaper", "wallpaper_minutes",
            "location", "location_name", "language", "news_source", "news_rss", "music_screen", "music_url", "clock_screen",
            "motion_screen", "morning", "night", "dock_tint", "clock", "weather_home", "weather_screen", "news_home",
            "night_screen", "night_mute", "quote", "minimal"));
    private static final Set<String> KNOWN = new LinkedHashSet<>(KEYS);

    /** The choices of the confirmation, in the order shown. */
    static final int CANCEL = 0, CONFIRM = 1;
    static final int DEFAULT_CHOICE = CANCEL;

    enum Result { CANCELLED, RESET, ALREADY_DEFAULT }

    private SettingsReset() {}

    /**
     * Runs the choice made in the confirmation. Cancel (or anything but Confirm) reads and writes nothing. A failed
     * read or write throws, and home.conf is then left exactly as it was.
     */
    static Result choose(File dir, int choice) throws IOException {
        if (choice != CONFIRM) return Result.CANCELLED;
        File f = new File(dir, Config.FILE);
        // Config.read turns a read error into "", which here would wipe the unknown lines; so read strictly.
        String text = f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
        String cleared = clear(text);
        if (cleared.equals(text)) return Result.ALREADY_DEFAULT;   // nothing to reset: no write at all
        Config.write(dir, cleared);
        return Result.RESET;
    }

    /** The configuration text without the lines that set a documented key; every other line is kept as it is. */
    static String clear(String text) {
        // split with -1 keeps the empty piece after a final newline, so the file keeps (or lacks) its last newline
        List<String> kept = new ArrayList<>();
        for (String line : text.split("\n", -1)) if (!setsKnownKey(line)) kept.add(line);
        return String.join("\n", kept);
    }

    /** True when the line is a key=value setting of a documented key, read the way Config reads it. */
    static boolean setsKnownKey(String line) {
        int hash = line.indexOf('#');
        String head = hash >= 0 ? line.substring(0, hash) : line;
        int eq = head.indexOf('=');
        return eq >= 0 && KNOWN.contains(head.substring(0, eq).trim().toLowerCase(Locale.ROOT));
    }
}
