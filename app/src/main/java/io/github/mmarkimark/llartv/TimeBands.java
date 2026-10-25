package io.github.mmarkimark.llartv;

/**
 * Time-of-day bands from home.conf: "morning=08:00-11:00" (weather and news panel) and
 * "night=23:00-08:00" (dark, warm screen); the rest of the day is plain DAY. A band may wrap past midnight.
 * An empty value or "off" turns that band off; an unreadable one keeps the default.
 */
final class TimeBands {
    enum Band { MORNING, DAY, NIGHT }

    static final String DEFAULT_MORNING = "08:00-11:00", DEFAULT_NIGHT = "23:00-08:00";
    static final int TRANSITION_S = 90;   // night mode fades in and out over this many seconds when the band changes

    final int[] morning, night;   // {start, end} in minutes of the day, or null when the band is off

    TimeBands(int[] morning, int[] night) { this.morning = morning; this.night = night; }

    static TimeBands parse(String morning, String night) {
        return new TimeBands(band(morning, DEFAULT_MORNING), band(night, DEFAULT_NIGHT));
    }

    static int[] band(String v, String fallback) {
        if (v == null || v.trim().isEmpty() || v.trim().equalsIgnoreCase("off")) return null;
        int[] r = range(v);
        return r != null ? r : range(fallback);
    }

    /** "HH:MM-HH:MM" to {start, end} minutes of the day; null when it doesn't parse. */
    static int[] range(String v) {
        String[] p = v.split("-");
        if (p.length != 2) return null;
        int a = minutes(p[0].trim()), b = minutes(p[1].trim());
        return a < 0 || b < 0 || a == b ? null : new int[]{a, b};
    }

    static int minutes(String hhmm) {
        String[] p = hhmm.split(":");
        if (p.length != 2) return -1;
        try {
            int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
            return h >= 0 && h < 24 && m >= 0 && m < 60 ? h * 60 + m : -1;
        } catch (NumberFormatException e) { return -1; }
    }

    static boolean inside(int[] r, int minute) {
        if (r == null) return false;
        return r[0] < r[1] ? minute >= r[0] && minute < r[1] : minute >= r[0] || minute < r[1];
    }

    /** The band at a minute of the day (0-1439). Night wins over morning if they overlap. */
    Band at(int minuteOfDay) {
        if (inside(night, minuteOfDay)) return Band.NIGHT;
        if (inside(morning, minuteOfDay)) return Band.MORNING;
        return Band.DAY;
    }
}
