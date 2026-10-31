package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GridOrderTest {
    static List<String> list(String... pkgs) { return Arrays.asList(pkgs); }
    static Set<String> set(String... pkgs) { return new HashSet<>(Arrays.asList(pkgs)); }

    // ---------------------------------------------------------------- applying an order

    @Test public void withoutAnOrderTheGridKeepsTheLabelOrder() {
        assertEquals(list("a", "b", "c"), GridOrder.apply(list(), list("a", "b", "c")));
    }

    @Test public void orderedAppsComeFirstAndNewAppsAppendWithoutMovingThem() {
        assertEquals(list("c", "a", "b", "d"), GridOrder.apply(list("c", "a"), list("a", "b", "c", "d")));
    }

    @Test public void anUninstalledAppLeavesNoHole() {
        assertEquals(list("c", "a"), GridOrder.apply(list("gone", "c", "a"), list("a", "c")));
    }

    @Test public void aDuplicateInTheOrderIsPlacedOnce() {
        assertEquals(list("b", "a"), GridOrder.apply(list("b", "a", "b"), list("a", "b")));
    }

    // ---------------------------------------------------------------- moving in the grid

    @Test public void theFirstMoveWritesTheWholeSequenceSoNewAppsAppendLater() {
        // installed by label a b c d; dock d; grid a b c. Moving b right writes every installed app.
        List<String> o = GridOrder.move(list(), list("a", "b", "c", "d"), set(), list("a", "b", "c"), "b", 1);
        assertEquals(list("a", "c", "b", "d"), o);
        assertEquals(list("a", "c", "b", "e"), GridOrder.apply(o, list("a", "b", "c", "e")));
    }

    @Test public void movingLeftSwapsWithTheGridNeighbour() {
        assertEquals(list("b", "a", "c"), GridOrder.move(list(), list("a", "b", "c"), set(), list("a", "b", "c"), "b", -1));
    }

    @Test public void aMoveSkipsDockAndHiddenEntriesAndLeavesThemInPlace() {
        // sequence a D b H c: the dock app D and the hidden app H are not in the grid
        List<String> o = GridOrder.move(list("a", "D", "b", "H", "c"), list("a", "b", "c", "D", "H"), set("H"),
                list("a", "b", "c"), "a", 1);
        assertEquals(list("D", "b", "a", "H", "c"), o);
    }

    @Test public void aMoveByARowJumpsThatManyGridApps() {
        assertEquals(list("b", "c", "a", "d"), GridOrder.move(list(), list("a", "b", "c", "d"), set(), list("a", "b", "c", "d"), "a", 2));
        assertEquals(list("c", "a", "b", "d"), GridOrder.move(list(), list("a", "b", "c", "d"), set(), list("a", "b", "c", "d"), "c", -2));
    }

    @Test public void aMovePastEitherEndWritesNothing() {
        assertNull(GridOrder.move(list(), list("a", "b"), set(), list("a", "b"), "a", -1));
        assertNull(GridOrder.move(list(), list("a", "b"), set(), list("a", "b"), "b", 1));
        assertNull(GridOrder.move(list(), list("a", "b"), set(), list("a", "b"), "b", 3));
        assertNull(GridOrder.move(list(), list("a", "b"), set(), list("a", "b"), "z", 1));
    }

    @Test public void aMoveKeepsHiddenEntriesButDropsUninstalledOnes() {
        // "h" is hidden and not installed: kept in place; "gone" is neither: dropped, so a reinstall is new
        List<String> o = GridOrder.move(list("h", "gone", "b", "a"), list("a", "b"), set("h"), list("b", "a"), "a", -1);
        assertEquals(list("h", "a", "b"), o);
    }

    @Test public void aMoveNeverChangesTheListsItWasGiven() {
        List<String> order = list("a", "b", "c");
        GridOrder.move(order, list("a", "b", "c"), set(), list("a", "b", "c"), "a", 1);
        assertEquals(list("a", "b", "c"), order);
    }
}
