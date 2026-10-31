package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Quiet night screensaver (#37): two independent options, both off by default, active only in the night band. */
public class NightQuietTest {
    static final NightQuiet OFF = new NightQuiet(false, false), VISUAL = new NightQuiet(true, false),
            MUTE = new NightQuiet(false, true), BOTH = new NightQuiet(true, true);

    @Test public void bothOptionsAreOffByDefaultAndCanBeTurnedOn() {
        Config k = new Config();
        assertFalse(k.nightScreen);
        assertFalse(k.nightMute);
        k.set("night_screen", "on");
        k.set("night_mute", "on");
        assertTrue(k.nightScreen);
        assertTrue(k.nightMute);
        k.set("night_screen", "");
        k.set("night_mute", "off");
        assertFalse(k.nightScreen);   // an empty value restores the default
        assertFalse(k.nightMute);
    }

    @Test public void withBothOptionsOffNothingChangesAtNight() {
        assertFalse(OFF.quiet(true));
        assertTrue(OFF.musicAllowed(true, true));
        for (Boolean prev : new Boolean[]{null, false, true})
            for (boolean now : new boolean[]{false, true})
                assertEquals(NightQuiet.KEEP, OFF.musicChange(prev, now, true));
        assertFalse(NightQuiet.clockShown(false, OFF.quiet(true)));   // clock_screen=off still hides the time
    }

    @Test public void theQuietVisualOnlyActsInTheNightBandAndAlwaysShowsTheClock() {
        assertTrue(VISUAL.quiet(true));
        assertFalse(VISUAL.quiet(false));
        assertTrue(NightQuiet.clockShown(false, VISUAL.quiet(true)));    // even with clock_screen=off
        assertFalse(NightQuiet.clockShown(false, VISUAL.quiet(false)));  // the day keeps clock_screen
        assertTrue(VISUAL.musicAllowed(true, true));                      // the visual alone keeps the music
    }

    @Test public void theNightSilenceIsIndependentOfTheVisual() {
        assertFalse(MUTE.quiet(true));
        assertFalse(MUTE.musicAllowed(true, true));
        assertTrue(MUTE.musicAllowed(true, false));
        assertFalse(MUTE.musicAllowed(false, false));   // music_screen=off stays silent all day
    }

    @Test public void aSessionStartingInsideTheNightStartsSilentThenPlaysWhenTheNightEnds() {
        assertFalse(BOTH.musicAllowed(true, true));                         // what onDreamingStarted asks
        assertEquals(NightQuiet.KEEP, BOTH.musicChange(null, true, true));  // first evaluation: nothing to change
        assertEquals(NightQuiet.KEEP, BOTH.musicChange(true, true, true));
        assertEquals(NightQuiet.START, BOTH.musicChange(true, false, true));
    }

    @Test public void aSessionStartingOutsideTheNightStopsWhenItBeginsAndResumesAfter() {
        assertTrue(MUTE.musicAllowed(true, false));
        assertEquals(NightQuiet.STOP, MUTE.musicChange(false, true, true));
        assertEquals(NightQuiet.KEEP, MUTE.musicChange(true, true, true));   // no repeated stop
        assertEquals(NightQuiet.START, MUTE.musicChange(true, false, true));
        assertEquals(NightQuiet.KEEP, MUTE.musicChange(false, false, true)); // no second player
    }

    @Test public void musicThatIsOffNeverStartsAtABoundary() {
        assertEquals(NightQuiet.KEEP, MUTE.musicChange(true, false, false));
        assertEquals(NightQuiet.KEEP, MUTE.musicChange(false, true, false));
    }
}
