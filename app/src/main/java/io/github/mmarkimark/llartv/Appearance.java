package io.github.mmarkimark.llartv;

import java.util.Arrays;
import java.util.Locale;

/**
 * The choices the settings menu offers for the wallpaper and night mode, over the existing home.conf keys
 * ("wallpaper", "wallpaper_minutes" and "night"). A value set by the scripts that is not a preset is kept as one
 * more choice, so opening the menu never rewrites it. Free of Android, so the JVM tests can pin it.
 */
final class Appearance {
    private Appearance() {}

    enum Wallpaper { BING, PLAIN, CUSTOM }

    /** Every n minutes; 0 is "Fixa": the newest photo, without rotating (it still changes when Bing publishes). */
    static final int[] MINUTES = {15, 30, 60, 180, 0};
    /** Off, then ranges that all cross midnight; 23:00-08:00 is TimeBands' default. */
    static final String[] NIGHTS = {"off", "21:00-07:00", "22:00-07:00", "22:00-08:00", "23:00-07:00",
            "23:00-08:00", "00:00-08:00"};

    /** The wallpaper setting as Config reads it: empty is off (a plain background), "bing", or an image URL. */
    static Wallpaper wallpaper(String value) {
        if (value.isEmpty()) return Wallpaper.PLAIN;
        return value.equalsIgnoreCase("bing") ? Wallpaper.BING : Wallpaper.CUSTOM;
    }

    /** The photo-change choices, with the current value appended when it is not a preset. */
    static int[] minutes(int current) {
        for (int m : MINUTES) if (m == current) return MINUTES.clone();
        int[] all = Arrays.copyOf(MINUTES, MINUTES.length + 1);
        all[MINUTES.length] = current;
        return all;
    }

    static String minutesLabel(int m) {
        if (m == 0) return Ca.PHOTO_FIXED;
        if (m == 60) return Ca.PHOTO_HOURLY;
        if (m % 60 == 0) return "Cada " + m / 60 + " hores";
        return "Cada " + m + " min";
    }

    /** The night setting the way TimeBands reads it: "off", a zero-padded range, or the default when unreadable. */
    static String night(String value) {
        int[] r = TimeBands.band(value, TimeBands.DEFAULT_NIGHT);
        if (r == null) return "off";
        return String.format(Locale.ROOT, "%02d:%02d-%02d:%02d", r[0] / 60, r[0] % 60, r[1] / 60, r[1] % 60);
    }

    /** The night-mode choices, with the current range appended when it is not a preset. */
    static String[] nights(String current) {
        String n = night(current);
        for (String p : NIGHTS) if (p.equals(n)) return NIGHTS.clone();
        String[] all = Arrays.copyOf(NIGHTS, NIGHTS.length + 1);
        all[NIGHTS.length] = n;
        return all;
    }

    static String nightLabel(String value) {
        return value.equals("off") ? Ca.NIGHT_OFF : value.replace("-", " – ");
    }

    static int indexOf(int[] values, int v) {
        for (int i = 0; i < values.length; i++) if (values[i] == v) return i;
        return -1;
    }

    static int indexOf(String[] values, String v) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(v)) return i;
        return -1;
    }
}
