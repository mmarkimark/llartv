package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Continuous move mode (#39): provisional steps, OK saves once, Back undoes everything. */
public class MoveModeTest {
    static List<String> seq(String... p) { return new ArrayList<>(Arrays.asList(p)); }

    // ---------------------------------------------------------------- grid: four directions

    @Test public void severalGridStepsAreCompletedWithoutReopeningTheMenu() {
        // grid of 3 columns: a b c / d e f / g
        MoveMode m = MoveMode.start(seq("a", "b", "c", "d", "e", "f", "g"), "a", false, 3);
        assertTrue(m.step(MoveMode.RIGHT));
        assertTrue(m.step(MoveMode.DOWN));
        assertTrue(m.step(MoveMode.RIGHT));
        assertEquals(seq("b", "c", "d", "e", "f", "a", "g"), m.current());
        assertEquals(5, m.index());
        assertTrue(m.changed());
    }

    @Test public void gridStepsThatWouldLeaveTheGridAreIgnored() {
        MoveMode m = MoveMode.start(seq("a", "b", "c", "d", "e"), "a", false, 3);
        assertFalse(m.step(MoveMode.LEFT));    // first place
        assertFalse(m.step(MoveMode.UP));      // first row
        assertTrue(m.step(MoveMode.DOWN));     // a -> place 3
        assertFalse(m.step(MoveMode.DOWN));    // no row below
        assertTrue(m.step(MoveMode.RIGHT));    // place 4, the last one
        assertFalse(m.step(MoveMode.RIGHT));   // last place
        assertEquals(seq("b", "c", "d", "e", "a"), m.current());
    }

    @Test public void leftAndRightCrossRowsLikeTheSingleStepMove() {
        MoveMode m = MoveMode.start(seq("a", "b", "c", "d"), "c", false, 3);
        assertTrue(m.step(MoveMode.RIGHT));    // end of row 1 -> start of row 2, as "Moure a la dreta" did
        assertEquals(seq("a", "b", "d", "c"), m.current());
    }

    // ---------------------------------------------------------------- dock: left and right only

    @Test public void theDockMovesOnlyLeftAndRight() {
        MoveMode m = MoveMode.start(seq("a", "b", "c"), "b", true, 3);
        assertFalse(m.step(MoveMode.UP));
        assertFalse(m.step(MoveMode.DOWN));
        assertTrue(m.step(MoveMode.LEFT));
        assertFalse(m.step(MoveMode.LEFT));
        assertEquals(seq("b", "a", "c"), m.current());
        assertEquals(0, m.index());
        HomeApps.Edit edit = m.dockEdit(seq("h"));
        assertTrue(edit.changed());
        assertEquals(seq("b", "a", "c"), edit.dock);
        assertEquals(seq("h"), edit.hidden);   // hidden entries are untouched
        assertFalse(edit.hiddenChanged);
    }

    // ---------------------------------------------------------------- OK / Back

    @Test public void backUndoesEveryStep() {
        MoveMode m = MoveMode.start(seq("a", "b", "c"), "a", true, 3);
        m.step(MoveMode.RIGHT);
        m.step(MoveMode.RIGHT);
        assertEquals(seq("a", "b", "c"), m.initial());
        assertEquals(0, m.initialIndex());
    }

    @Test public void stepsThatReturnToTheStartWriteNothing() {
        MoveMode m = MoveMode.start(seq("a", "b", "c"), "a", false, 3);
        m.step(MoveMode.RIGHT);
        m.step(MoveMode.LEFT);
        assertFalse(m.changed());
        assertNull(m.gridOrder(Collections.emptyList(), seq("a", "b", "c"), Collections.emptyList()));
        assertFalse(m.dockEdit(seq()).changed());
    }

    @Test public void theSavedGridOrderEqualsTheSingleStepMovesItReplaces() {
        // installed by label: d1 (dock) a b c d e; grid shown: a b c d e; hidden x keeps its place in order=
        List<String> byLabel = seq("a", "b", "c", "d", "d1", "e");
        List<String> hidden = seq("x");
        List<String> order = seq("x", "e");
        List<String> grid = GridOrder.apply(order, seq("a", "b", "c", "d", "e"));   // e a b c d
        MoveMode m = MoveMode.start(grid, "a", false, 2);
        m.step(MoveMode.DOWN);
        m.step(MoveMode.RIGHT);
        m.step(MoveMode.UP);
        // the same steps through the existing single-step rule
        List<String> o = order, g = grid;
        for (int delta : new int[]{2, 1, -2}) {
            o = GridOrder.move(o, byLabel, hidden, g, "a", delta);
            g = GridOrder.apply(o, seq("a", "b", "c", "d", "e"));
        }
        List<String> saved = m.gridOrder(order, byLabel, hidden);
        assertEquals(o, saved);
        assertEquals(m.current(), GridOrder.apply(saved, seq("a", "b", "c", "d", "e")));
        assertEquals(0, saved.indexOf("x"));   // the hidden entry keeps its place
    }

    @Test public void anAppThatIsNotShownCannotStartAMove() {
        assertNull(MoveMode.start(seq("a", "b"), "z", false, 3));
        assertNull(MoveMode.start(seq("a"), "a", false, 3));   // nothing to move around
    }

    @Test public void theSpokenPositionFollowsEveryAcceptedStep() {
        // TalkBack hears "position index()+1 of size" after each accepted step; a refused step announces nothing new
        MoveMode m = MoveMode.start(seq("a", "b", "c", "d"), "a", true, 4);
        m.step(MoveMode.RIGHT);
        m.step(MoveMode.RIGHT);
        assertEquals(3, m.index() + 1);
        assertEquals(4, m.current().size());
        assertFalse(m.step(MoveMode.UP));   // the dock has no rows: same position
        assertEquals(3, m.index() + 1);
    }

    // ---------------------------------------------------------------- what the screen shows (#57)

    @Test public void outsideMoveModeTheHeroKeepsItsUsualStrength() {
        assertEquals(1f, MoveMode.heroAlpha(false, true), 0f);     // dock focused
        assertEquals(0.4f, MoveMode.heroAlpha(false, false), 0f);  // grid or top bar focused
    }

    @Test public void inMoveModeTheHeroNeverNamesAnotherApp() {
        // the banner names the moving app; the hero (always a dock app) is hidden, grid included
        assertEquals(0f, MoveMode.heroAlpha(true, true), 0f);
        assertEquals(0f, MoveMode.heroAlpha(true, false), 0f);
    }

    @Test public void theBannerFollowsThePageAboveTheDockButStaysOnScreen() {
        assertEquals(500f, MoveMode.hintTop(500f, 0f, 24f), 0f);       // page not scrolled: above the dock band
        assertEquals(200f, MoveMode.hintTop(500f, -300f, 24f), 0f);    // grid scrolled: moves up with the page
        assertEquals(24f, MoveMode.hintTop(500f, -900f, 24f), 0f);     // hero area scrolled away: kept on screen
    }
}
