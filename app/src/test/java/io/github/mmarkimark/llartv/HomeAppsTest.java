package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomeAppsTest {
    static List<String> dock(String... pkgs) { return Arrays.asList(pkgs); }
    static List<String> hidden(String... pkgs) { return Arrays.asList(pkgs); }
    static Set<String> hiddenSet(String... pkgs) { return new HashSet<>(Arrays.asList(pkgs)); }

    // ---------------------------------------------------------------- dock and hidden edits

    @Test public void addingToTheDockAppendsIt() {
        HomeApps.Edit e = HomeApps.addToDock(dock("a", "b"), hidden(), "c", 4);
        assertTrue(e.changed());
        assertTrue(e.dockChanged);
        assertFalse(e.hiddenChanged);
        assertEquals(dock("a", "b", "c"), e.dock);
        assertEquals(hidden(), e.hidden);
    }

    @Test public void addingToAFullDockWritesNothing() {
        HomeApps.Edit e = HomeApps.addToDock(dock("a", "b", "c"), hidden(), "d", 3);
        assertFalse(e.changed());
        assertEquals(dock("a", "b", "c"), e.dock);
        assertEquals(hidden(), e.hidden);
    }

    @Test public void addingAnAppThatIsAlreadyInTheDockWritesNothing() {
        HomeApps.Edit e = HomeApps.addToDock(dock("a", "b"), hidden(), "a", 4);
        assertFalse(e.changed());
    }

    @Test public void aHiddenAppIsNeverAddedToTheDock() {
        HomeApps.Edit e = HomeApps.addToDock(dock("a"), hidden("x"), "x", 4);
        assertFalse(e.changed());
    }

    @Test public void removingFromTheDockTakesItOut() {
        HomeApps.Edit e = HomeApps.removeFromDock(dock("a", "b", "c"), hidden("x"), "b");
        assertTrue(e.dockChanged);
        assertFalse(e.hiddenChanged);
        assertEquals(dock("a", "c"), e.dock);
        assertEquals(hidden("x"), e.hidden);
    }

    @Test public void removingAnAppThatIsNotInTheDockWritesNothing() {
        HomeApps.Edit e = HomeApps.removeFromDock(dock("a"), hidden(), "z");
        assertFalse(e.changed());
    }

    @Test public void movingLeftSwapsWithTheNeighbour() {
        HomeApps.Edit e = HomeApps.moveInDock(dock("a", "b", "c"), hidden(), "b", -1);
        assertTrue(e.dockChanged);
        assertEquals(dock("b", "a", "c"), e.dock);
    }

    @Test public void movingRightSwapsWithTheNeighbour() {
        HomeApps.Edit e = HomeApps.moveInDock(dock("a", "b", "c"), hidden(), "a", 1);
        assertEquals(dock("b", "a", "c"), e.dock);
    }

    @Test public void movingAtEitherEndOfTheDockIsANoOp() {
        assertFalse(HomeApps.moveInDock(dock("a", "b"), hidden(), "a", -1).changed());
        assertFalse(HomeApps.moveInDock(dock("a", "b"), hidden(), "b", 1).changed());
        assertFalse(HomeApps.moveInDock(dock(), hidden(), "a", 1).changed());
        assertEquals(dock("a", "b"), HomeApps.moveInDock(dock("a", "b"), hidden(), "a", -1).dock);
    }

    @Test public void hidingAnAppHidesItAndTakesItOutOfTheDock() {
        HomeApps.Edit e = HomeApps.hideApp(dock("a", "b"), hidden("x"), "b");
        assertTrue(e.hiddenChanged);
        assertFalse(e.dockChanged);   // the existing rule filters hidden apps when the dock is built
        assertEquals(dock("a", "b"), e.dock);
        assertEquals(hidden("x", "b"), e.hidden);
    }

    @Test public void hidingAGridAppLeavesTheDockAlone() {
        HomeApps.Edit e = HomeApps.hideApp(dock("a", "b"), hidden(), "z");
        assertTrue(e.hiddenChanged);
        assertEquals(dock("a", "b"), e.dock);
        assertEquals(hidden("z"), e.hidden);
    }

    @Test public void hidingAnAppThatIsAlreadyHiddenWritesNothing() {
        HomeApps.Edit e = HomeApps.hideApp(dock("a"), hidden("x"), "x");
        assertFalse(e.changed());
    }

    @Test public void unhidingRemovesItFromHidden() {
        HomeApps.Edit e = HomeApps.unhide(dock("a"), hidden("x", "b"), "b");
        assertTrue(e.hiddenChanged);
        assertFalse(e.dockChanged);
        assertEquals(dock("a"), e.dock);
        assertEquals(hidden("x"), e.hidden);
    }

    @Test public void unhidingAnAppThatIsNotHiddenWritesNothing() {
        HomeApps.Edit e = HomeApps.unhide(dock("a"), hidden(), "z");
        assertFalse(e.changed());
    }

    @Test public void editsNeverChangeTheListsTheyWereGiven() {
        List<String> d = new java.util.ArrayList<>(Arrays.asList("a", "b"));
        HomeApps.addToDock(d, hidden(), "c", 4);
        assertEquals(Arrays.asList("a", "b"), d);
    }

    @Test public void anEditKeepsTheConfiguredDockAppsThatAreNotShown() {
        // "gone" is not installed and "h" is hidden: neither is shown, and an edit of the shown dock keeps both
        List<String> shown = dock("a", "b"), configured = dock("a", "gone", "b", "h");
        assertEquals(dock("b", "a", "gone", "h"),
                HomeApps.withUnshown(HomeApps.moveInDock(shown, hidden("h"), "b", -1).dock, shown, configured));
    }

    @Test public void anAppRemovedOnPurposeIsNotKeptBack() {
        List<String> shown = dock("a", "b"), configured = dock("a", "gone", "b");
        assertEquals(dock("a", "gone"),
                HomeApps.withUnshown(HomeApps.removeFromDock(shown, hidden(), "b").dock, shown, configured));
    }

    // ---------------------------------------------------------------- dock composition (S4)

    /** The installed apps, already in the grid's order (alphabetically by label). */
    static List<String> installed(String... pkgs) { return Arrays.asList(pkgs); }

    @Test public void anExplicitDockShowsOnlyItsInstalledAppsInOrderWithNoFill() {
        HomeApps.Composed c = HomeApps.compose(dock("y", "z"), installed("a", "b", "y", "z"), hiddenSet(), false, 6);
        assertEquals(dock("y", "z"), c.dock);
        assertEquals(installed("a", "b"), c.grid);
    }

    @Test public void anAutoDockKeepsFillingFromTheAlphabeticallySortedGrid() {
        HomeApps.Composed c = HomeApps.compose(dock("y"), installed("a", "b", "c", "y"), hiddenSet(), true, 3);
        assertEquals(dock("y", "a", "b"), c.dock);
        assertEquals(installed("c"), c.grid);
    }

    @Test public void anExplicitEmptyDockStaysEmpty() {
        HomeApps.Composed c = HomeApps.compose(dock(), installed("a", "b"), hiddenSet(), false, 4);
        assertEquals(dock(), c.dock);
        assertEquals(installed("a", "b"), c.grid);
    }

    @Test public void anEmptyAutoDockComesEntirelyFromTheGrid() {
        HomeApps.Composed c = HomeApps.compose(dock(), installed("a", "b", "c"), hiddenSet(), true, 4);
        assertEquals(dock("a", "b", "c"), c.dock);
        assertEquals(installed(), c.grid);
    }

    @Test public void hiddenAppsAreNeverInTheDockOrTheGrid() {
        HomeApps.Composed c = HomeApps.compose(dock("y", "h"), installed("h", "y", "z"), hiddenSet("h"), true, 4);
        assertEquals(dock("y", "z"), c.dock);
        assertEquals(installed(), c.grid);
    }

    @Test public void uninstalledPackagesAreSkipped() {
        HomeApps.Composed c = HomeApps.compose(dock("y", "gone"), installed("a", "y"), hiddenSet(), false, 4);
        assertEquals(dock("y"), c.dock);
        assertEquals(installed("a"), c.grid);
    }

    @Test public void theDockIsCappedAtTheColumns() {
        HomeApps.Composed c = HomeApps.compose(dock("1", "2", "3", "4", "5", "6", "7", "8"),
                installed("1", "2", "3", "4", "5", "6", "7", "8"), hiddenSet(), false, 6);
        assertEquals(dock("1", "2", "3", "4", "5", "6"), c.dock);
        assertEquals(installed("7", "8"), c.grid);
    }

    @Test public void aDuplicateInTheDockOrderIsPlacedOnce() {
        HomeApps.Composed c = HomeApps.compose(dock("y", "y", "a"), installed("a", "y"), hiddenSet(), false, 4);
        assertEquals(dock("y", "a"), c.dock);
        assertEquals(installed(), c.grid);
    }
}
