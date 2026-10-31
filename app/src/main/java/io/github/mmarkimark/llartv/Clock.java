package io.github.mmarkimark.llartv;

/**
 * The clock format preference (home.conf "clock"): follow the TV, 24-hour or 12-hour.
 *
 * "Follow system" keeps the exact behaviour of each surface, so an existing installation does not
 * change; the two explicit formats are the same on every surface and the 12-hour one shows AM/PM,
 * which the system-following clock has never shown.
 */
final class Clock {
    private Clock() {}

    static final String SYSTEM = "system", H24 = "24", H12 = "12";

    /** True when the time is shown in 24-hour notation. */
    static boolean is24(String pref, boolean systemIs24) {
        if (H24.equals(pref)) return true;
        if (H12.equals(pref)) return false;
        return systemIs24;
    }

    /** The pattern for the big clock and the screensaver clock. */
    static String pattern(String pref, boolean systemIs24) {
        if (H12.equals(pref)) return "h:mm a";
        return is24(pref, systemIs24) ? "H:mm" : "h:mm";
    }

    /** The pattern for the next-match card, which has always shown AM/PM in 12-hour mode. */
    static String matchPattern(String pref, boolean systemIs24) {
        return is24(pref, systemIs24) ? "HH:mm" : "h:mm a";
    }

    /**
     * A time in the system time zone, with the AM/PM marker of the interface language. The language never
     * decides the 12- or 24-hour format: the pattern does (see pattern and matchPattern).
     */
    static String format(String pattern, java.util.Locale language, java.util.Date d) {
        return new java.text.SimpleDateFormat(pattern, language).format(d);
    }

    /**
     * The big clock and the screensaver clock as text: the format from the preference (system, 24 or 12), the
     * AM/PM marker from the interface language, the time zone the TV has now. The screensaver renders it itself
     * because TextClock takes the marker from the process default locale, the TV's system language.
     */
    static String time(String pref, boolean systemIs24, java.util.Locale language, java.util.Date d) {
        return format(pattern(pref, systemIs24), language, d);
    }

    /** The delay until the next minute starts, so a clock refreshed then changes with the minute. */
    static long untilNextMinute(long nowMs) {
        return 60_000L - Math.floorMod(nowMs, 60_000L);
    }
}
