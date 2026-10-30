package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OkPressTest {
    static final long TIMEOUT = 400;   // ViewConfiguration.getLongPressTimeout() on the TV

    @Test public void aShortPressClicksOnKeyUp() {
        OkPress ok = new OkPress(TIMEOUT);
        ok.down(1000);
        assertEquals(OkPress.NONE, ok.repeat(1100));
        assertEquals(OkPress.CLICK, ok.up(1300));
    }

    @Test public void aLongPressFiresOnceAfterTheTimeoutAndSwallowsTheClick() {
        OkPress ok = new OkPress(TIMEOUT);
        ok.down(0);
        assertEquals(OkPress.NONE, ok.repeat(50));
        assertEquals(OkPress.NONE, ok.repeat(399));
        assertEquals(OkPress.HOLD, ok.repeat(400));
        assertEquals(OkPress.NONE, ok.repeat(450));   // it fires once, not on every repeat
        assertEquals(OkPress.NONE, ok.up(700));       // and the release is not a click
    }

    @Test public void aHoldWithoutRepeatEventsFiresOnRelease() {
        OkPress ok = new OkPress(TIMEOUT);
        ok.down(0);
        assertEquals(OkPress.HOLD, ok.up(1000));
    }

    @Test public void theStateResetsBetweenPresses() {
        OkPress ok = new OkPress(TIMEOUT);
        ok.down(0);
        assertEquals(OkPress.CLICK, ok.up(100));
        ok.down(200);
        assertEquals(OkPress.CLICK, ok.up(300));
    }

    @Test public void anotherKeyCancelsThePress() {
        OkPress ok = new OkPress(TIMEOUT);
        ok.down(0);
        ok.cancel();
        assertEquals(OkPress.NONE, ok.repeat(500));
        assertEquals(OkPress.NONE, ok.up(600));
    }

    @Test public void aReleaseWithoutAPressDoesNothing() {
        assertEquals(OkPress.NONE, new OkPress(TIMEOUT).up(100));
    }
}
