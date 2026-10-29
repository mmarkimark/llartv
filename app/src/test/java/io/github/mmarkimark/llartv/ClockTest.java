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
}
