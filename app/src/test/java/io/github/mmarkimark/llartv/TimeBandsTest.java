package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TimeBandsTest {
    static int at(int h, int m) { return h * 60 + m; }
    final TimeBands std = TimeBands.parse("08:00-11:00", "23:00-08:00");

    @Test public void ownerBandsMatchTheRequestedHours() {
        assertEquals(TimeBands.Band.NIGHT, std.at(at(7, 59)));
        assertEquals(TimeBands.Band.MORNING, std.at(at(8, 0)));
        assertEquals(TimeBands.Band.MORNING, std.at(at(10, 59)));
        assertEquals(TimeBands.Band.DAY, std.at(at(11, 0)));
        assertEquals(TimeBands.Band.DAY, std.at(at(22, 59)));
        assertEquals(TimeBands.Band.NIGHT, std.at(at(23, 0)));
        assertEquals(TimeBands.Band.NIGHT, std.at(at(0, 40)));
    }

    @Test public void emptyValueDisablesThatBand() {
        TimeBands noNight = TimeBands.parse("08:00-11:00", "");
        assertEquals(TimeBands.Band.DAY, noNight.at(at(23, 30)));
        TimeBands noMorning = TimeBands.parse("", "23:00-08:00");
        assertEquals(TimeBands.Band.DAY, noMorning.at(at(9, 0)));
    }

    @Test public void badValuesFallBackToDefaults() {
        TimeBands b = TimeBands.parse("nonsense", "25:00-8");
        assertEquals(TimeBands.Band.MORNING, b.at(at(9, 0)));
        assertEquals(TimeBands.Band.NIGHT, b.at(at(23, 30)));
    }

    @Test public void rangeParsing() {
        assertEquals(480, TimeBands.range("8:00-11:00")[0]);
        assertEquals(660, TimeBands.range("08:00 - 11:00")[1]);
        assertNull(TimeBands.range("8-11"));
        assertNull(TimeBands.range(""));
    }

    @Test public void offDisablesThatBand() {
        TimeBands b = TimeBands.parse("off", "OFF");
        assertEquals(TimeBands.Band.DAY, b.at(9 * 60));
        assertEquals(TimeBands.Band.DAY, b.at(23 * 60 + 30));
    }
}
