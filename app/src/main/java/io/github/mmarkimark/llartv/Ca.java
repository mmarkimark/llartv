package io.github.mmarkimark.llartv;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * The text rules of the interface language: greetings, dates, countdowns and the values the settings show.
 *
 * The texts themselves live in res/values*.xml and come through a Strings snapshot (issue #11); this class
 * decides which one applies and fills it in. The name is from the days when every text was Catalan.
 */
final class Ca {
    private Ca() {}

    /** The hour each greeting starts, per language: night, then the slots of the day, then night again. */
    static String greeting(Strings s, int hour) {
        switch (s.language) {
            case Language.ES:   // "buenos días" until lunch, "buenas tardes" until 21:00
                return s.get(hour < 5 ? R.string.greeting_night : hour < 14 ? R.string.greeting_morning
                        : hour < 21 ? R.string.greeting_afternoon : R.string.greeting_night);
            case Language.EN:   // "good evening" carries on through the small hours
                return s.get(hour < 5 ? R.string.greeting_night : hour < 12 ? R.string.greeting_morning
                        : hour < 18 ? R.string.greeting_afternoon : R.string.greeting_evening);
            default:            // "bon dia" until 14:00, then "bon vespre"; "bona nit" in the small hours
                return s.get(hour < 5 ? R.string.greeting_night : hour < 14 ? R.string.greeting_morning
                        : hour < 22 ? R.string.greeting_evening : R.string.greeting_night);
        }
    }

    /** A date in the interface language, in the system time zone. */
    static String format(Strings s, String pattern, Date d) { return new SimpleDateFormat(pattern, s.locale).format(d); }

    /** "diumenge, 25 d’octubre", "domingo, 25 de octubre", "Sunday 25 October". */
    static String date(Strings s, Date d) { return format(s, s.get(R.string.date_pattern), d); }

    /** The day of a match this week: "diumenge". */
    static String weekday(Strings s, Date d) { return format(s, s.get(R.string.weekday_pattern), d); }

    /** The day of a later match: "dg. 25 d’oct.". */
    static String shortDay(Strings s, Date d) { return format(s, s.get(R.string.short_day_pattern), d); }

    static String countdown(Strings s, long mins) {
        if (mins < 60) return s.get(R.string.countdown_minutes, mins);
        if (mins < 48 * 60) return s.get(R.string.countdown_hours, mins / 60, mins % 60);
        return s.plural(R.plurals.countdown_days, (int) (mins / (24 * 60)));
    }

    static String kickedOff(Strings s, long mins) { return s.get(R.string.kicked_off, mins); }

    /** A card heading in capitals, by the rules of the interface language rather than the TV's. */
    static String upper(Strings s, String text) { return text.toUpperCase(s.locale); }

    /** The value of the hidden-apps row: none, or how many. */
    static String hiddenApps(Strings s, int count) {
        return count == 0 ? s.get(R.string.none) : s.plural(R.plurals.hidden_apps, count);
    }

    /** The news-source labels, in the same order as NewsSource.OPTIONS; the providers' names are not translated. */
    static String[] newsSources(Strings s) {
        return new String[]{s.get(R.string.news_source_auto), "3Cat", "BBC News Mundo", "BBC News World"};
    }
}
