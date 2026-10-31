package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public class ClockTest {
    @Test public void twentyFourIsAlways24() {
        assertTrue(Clock.is24(Clock.H24, true));
        assertTrue(Clock.is24(Clock.H24, false));
    }

    @Test public void twelveIsNever24() {
        assertFalse(Clock.is24(Clock.H12, true));
        assertFalse(Clock.is24(Clock.H12, false));
    }

    @Test public void systemFollowsTheSystemFlag() {
        assertTrue(Clock.is24(Clock.SYSTEM, true));
        assertFalse(Clock.is24(Clock.SYSTEM, false));
    }

    @Test public void unknownValuesBehaveLikeSystem() {
        assertTrue(Clock.is24("25", true));
        assertFalse(Clock.is24("25", false));
        assertTrue(Clock.is24("abc", true));
        assertFalse(Clock.is24("abc", false));
        assertTrue(Clock.is24("", true));
        assertFalse(Clock.is24("", false));
    }

    @Test public void bigClockFollowsTheSystemWithoutAmPm() {
        assertEquals("H:mm", Clock.pattern(Clock.SYSTEM, true));
        assertEquals("h:mm", Clock.pattern(Clock.SYSTEM, false));   // as before the change: no AM/PM
    }

    @Test public void bigClock24IsAlwaysH() {
        assertEquals("H:mm", Clock.pattern(Clock.H24, true));
        assertEquals("H:mm", Clock.pattern(Clock.H24, false));
    }

    @Test public void bigClock12AlwaysShowsAmPm() {
        assertEquals("h:mm a", Clock.pattern(Clock.H12, true));
        assertEquals("h:mm a", Clock.pattern(Clock.H12, false));
    }

    @Test public void matchCardFollowsTheSystem() {
        assertEquals("HH:mm", Clock.matchPattern(Clock.SYSTEM, true));
        assertEquals("h:mm a", Clock.matchPattern(Clock.SYSTEM, false));
    }

    @Test public void matchCardExplicitFormats() {
        assertEquals("HH:mm", Clock.matchPattern(Clock.H24, true));
        assertEquals("HH:mm", Clock.matchPattern(Clock.H24, false));
        assertEquals("h:mm a", Clock.matchPattern(Clock.H12, true));
        assertEquals("h:mm a", Clock.matchPattern(Clock.H12, false));
    }

    @Test public void twelveHourRenderingsAreUnambiguous() {
        assertEquals("12:00 AM", render("h:mm a", 0, 0));   // midnight
        assertEquals("0:00", render("H:mm", 0, 0));         // "H" is one digit at midnight...
        assertEquals("00:00", render("HH:mm", 0, 0));       // ...while the match card's "HH:mm" is padded
        assertEquals("12:00 PM", render("h:mm a", 12, 0));  // noon
        assertEquals("12:00", render("H:mm", 12, 0));
        assertEquals("3:45 PM", render("h:mm a", 15, 45));  // quarter to four in the afternoon
        assertEquals("15:45", render("H:mm", 15, 45));
    }

    /** Renders h:m of 2024-01-01; the fixed zone keeps the renderings machine-independent. */
    private static String render(String pattern, int hour, int minute) {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        Calendar c = new GregorianCalendar(utc, Locale.US);
        c.clear();
        c.set(2024, Calendar.JANUARY, 1, hour, minute);
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(utc);
        return f.format(c.getTime());
    }

    // ---------------------------------------------------------------- the interface language (issue #11)

    @Test public void theLanguageNeverForcesAClockFormat() {
        for (String lang : Language.OPTIONS) {
            Config k = new Config();
            k.set("language", lang);
            assertEquals(Clock.SYSTEM, k.clock);
            assertEquals("H:mm", Clock.pattern(k.clock, true));
            assertEquals("h:mm", Clock.pattern(k.clock, false));
        }
    }

    @Test public void theTimeUsesTheSystemTimeZoneAndTheInterfaceLanguage() {
        TimeZone old = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"));
        try {
            Calendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
            c.clear();
            c.set(2026, Calendar.JULY, 1, 13, 5);   // 15:05 in Barcelona
            assertEquals("15:05", Clock.format("H:mm", Language.locale(Language.EN), c.getTime()));
            String en = Clock.format("h:mm a", Language.locale(Language.EN), c.getTime());
            assertTrue(en, en.startsWith("3:05") && en.toUpperCase(Locale.ROOT).contains("PM"));
            String es = Clock.format("h:mm a", Language.locale(Language.ES), c.getTime());
            assertTrue(es, es.startsWith("3:05") && !es.contains("PM"));
        } finally { TimeZone.setDefault(old); }
    }

    // ---------------------------------------------------------------- the screensaver clock (verifier blocker)

    static java.util.Date at(int hour, int minute) {
        Calendar c = new GregorianCalendar(TimeZone.getTimeZone("Europe/Madrid"));
        c.clear();
        c.set(2026, Calendar.JULY, 1, hour, minute);
        return c.getTime();
    }

    @Test public void twelveHourMarkerFollowsTheInterfaceLanguageNotTheSystemLocale() {
        Locale oldL = Locale.getDefault();
        TimeZone oldZ = TimeZone.getDefault();
        Locale.setDefault(new Locale("es", "ES"));   // the TV in Spanish, whose marker is "p. m."
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"));
        try {
            String en = Clock.time(Clock.H12, true, Language.locale(Language.EN), at(15, 5));
            assertTrue(en, en.startsWith("3:05 ") && en.toUpperCase(Locale.ROOT).endsWith("PM"));
            assertFalse(en, en.contains("p. m.") || en.contains("p.\u00A0m."));
        } finally { Locale.setDefault(oldL); TimeZone.setDefault(oldZ); }
    }

    @Test public void theScreensaverClockKeepsSystemAnd24Behaviour() {
        TimeZone oldZ = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"));
        try {
            for (String lang : Language.OPTIONS) {
                Locale l = Language.locale(lang);
                assertEquals("15:05", Clock.time(Clock.SYSTEM, true, l, at(15, 5)));
                assertEquals("3:05", Clock.time(Clock.SYSTEM, false, l, at(15, 5)));   // no AM/PM, as TextClock showed
                assertEquals("9:05", Clock.time(Clock.H24, false, l, at(9, 5)));
                assertEquals("15:05", Clock.time(Clock.H24, false, l, at(15, 5)));
            }
        } finally { TimeZone.setDefault(oldZ); }
    }

    @Test public void theScreensaverClockUsesTheCurrentSystemTimeZone() {
        TimeZone oldZ = TimeZone.getDefault();
        try {
            java.util.Date d = at(15, 5);   // 13:05 UTC
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertEquals("13:05", Clock.time(Clock.H24, true, Language.locale(Language.CA), d));
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));   // a time-zone change shows on the next tick
            assertEquals("22:05", Clock.time(Clock.H24, true, Language.locale(Language.CA), d));
        } finally { TimeZone.setDefault(oldZ); }
    }

    @Test public void theNextTickLandsOnTheNextMinute() {
        assertEquals(60_000L, Clock.untilNextMinute(120_000L));
        assertEquals(1L, Clock.untilNextMinute(119_999L));
        assertEquals(30_000L, Clock.untilNextMinute(90_000L));
    }
}
