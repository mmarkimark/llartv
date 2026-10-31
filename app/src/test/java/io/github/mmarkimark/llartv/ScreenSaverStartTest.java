package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ScreenSaverStartTest {
    static final String PKG = "io.github.mmarkimark.llartv";

    @Test public void ourDreamInEitherFlattenedFormIsReady() {
        assertEquals(ScreenSaverStart.Check.READY, ScreenSaverStart.check(PKG + "/.BingDream", "1"));
        assertEquals(ScreenSaverStart.Check.READY, ScreenSaverStart.check(PKG + "/" + PKG + ".BingDream", "1"));
        assertEquals(ScreenSaverStart.Check.READY, ScreenSaverStart.check(" " + PKG + "/.BingDream ", "1"));
    }

    @Test public void anUnsetEnabledFlagFollowsTheSystemDefault() {
        assertEquals(ScreenSaverStart.Check.READY, ScreenSaverStart.check(PKG + "/.BingDream", null));
    }

    @Test public void anotherOrMissingDreamIsNotSelected() {
        assertEquals(ScreenSaverStart.Check.NOT_SELECTED, ScreenSaverStart.check(null, "1"));
        assertEquals(ScreenSaverStart.Check.NOT_SELECTED, ScreenSaverStart.check("", "1"));
        assertEquals(ScreenSaverStart.Check.NOT_SELECTED, ScreenSaverStart.check("null", "1"));
        assertEquals(ScreenSaverStart.Check.NOT_SELECTED,
                ScreenSaverStart.check("com.google.android.backdrop/.Backdrop", "1"));
        assertEquals(ScreenSaverStart.Check.NOT_SELECTED,
                ScreenSaverStart.check(PKG + "/.HomeActivity", "1"));
        assertEquals(ScreenSaverStart.Check.NOT_SELECTED,
                ScreenSaverStart.check(PKG + ".evil/.BingDream", "1"));
    }

    @Test public void onlyTheFirstDreamCountsBecauseThatIsTheOneAndroidStarts() {
        assertEquals(ScreenSaverStart.Check.READY,
                ScreenSaverStart.check(PKG + "/.BingDream,com.google.android.backdrop/.Backdrop", "1"));
        assertEquals(ScreenSaverStart.Check.NOT_SELECTED,
                ScreenSaverStart.check("com.google.android.backdrop/.Backdrop," + PKG + "/.BingDream", "1"));
    }

    @Test public void aDisabledScreensaverIsReportedEvenWhenOursIsSelected() {
        assertEquals(ScreenSaverStart.Check.DISABLED, ScreenSaverStart.check(PKG + "/.BingDream", "0"));
        assertEquals(ScreenSaverStart.Check.NOT_SELECTED, ScreenSaverStart.check("com.other/.Dream", "0"));
    }

    @Test public void isOwnDreamMatchesOnlyBingDream() {
        assertTrue(ScreenSaverStart.isOwnDream(PKG + "/.BingDream"));
        assertFalse(ScreenSaverStart.isOwnDream(PKG + "/.BingDreamX"));
        assertFalse(ScreenSaverStart.isOwnDream(PKG + ".BingDream"));
        assertFalse(ScreenSaverStart.isOwnDream(null));
    }

    @Test public void theLauncherIsTheExportedSystemUiActivity() {
        assertEquals("com.android.systemui", ScreenSaverStart.LAUNCHER_PACKAGE);
        assertEquals("com.android.systemui.Somnambulator", ScreenSaverStart.LAUNCHER_CLASS);
    }
}
