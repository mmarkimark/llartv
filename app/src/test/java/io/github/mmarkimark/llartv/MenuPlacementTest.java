package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MenuPlacementTest {
    @Test public void aMenuThatFitsGoesBelowItsAnchor() {
        // the Inputs pill near the top of a 1080 px screen
        assertEquals(214f, MenuPlacement.top(104, 190, 300, 1080, 24), 0.01f);
    }

    @Test public void aMenuThatDoesNotFitBelowGoesAboveItsAnchor() {
        // a dock tile near the bottom: the menu ends just above the tile
        assertEquals(410f, MenuPlacement.top(834, 970, 400, 1080, 24), 0.01f);
    }

    @Test public void aMenuThatFitsNeitherWayStaysOnScreen() {
        assertEquals(24f, MenuPlacement.top(300, 900, 700, 1080, 24), 0.01f);
    }
}
