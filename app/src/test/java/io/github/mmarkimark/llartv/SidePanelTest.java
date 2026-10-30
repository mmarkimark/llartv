package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SidePanelTest {
    @Test public void takesTheWidthOfTheSystemSettingsPanel() {
        // Measured on the Xiaomi MiTV-MSSP3: the Android TV settings panel spans x = 1200..1920 of 1920.
        assertEquals(720, SidePanel.width(1920));
        assertEquals(480, SidePanel.width(1280));
        assertEquals(1440, SidePanel.width(3840));
    }

    @Test public void aVisibleRowKeepsTheScroll() {
        assertEquals(0, SidePanel.scrollFor(100, 228, 0, 800));
        assertEquals(40, SidePanel.scrollFor(100, 228, 40, 800));
    }

    @Test public void aRowBelowTheViewScrollsUntilItsBottomShows() {
        assertEquals(96, SidePanel.scrollFor(768, 896, 0, 800));
    }

    @Test public void aRowAboveTheViewScrollsUntilItsTopShows() {
        assertEquals(128, SidePanel.scrollFor(128, 256, 300, 800));
    }

    @Test public void aRowTallerThanTheViewShowsItsTop() {
        assertEquals(500, SidePanel.scrollFor(500, 1400, 0, 800));
    }
}
