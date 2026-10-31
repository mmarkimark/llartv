package io.github.mmarkimark.llartv;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Continuous move mode (#39), free of Android so the JVM tests can pin it.
 *
 * The app's menu starts it on one dock or grid app. Every D-pad step only rearranges the shown row: nothing is
 * written. OK saves the final place once (the dock line, or the grid order= built from the same single-step
 * rule GridOrder.move always used); Back or MENU drops the steps and shows the initial order again. The grid
 * moves in four directions (left/right one place, crossing rows; up/down a whole row), the dock only left/right.
 * A step that would leave the row is ignored.
 */
final class MoveMode {
    static final int LEFT = 0, RIGHT = 1, UP = 2, DOWN = 3;

    final String pkg;
    final boolean dock;
    private final int cols;
    private final List<String> initial, current;
    private final List<Integer> deltas = new ArrayList<>();

    private MoveMode(List<String> shown, String pkg, boolean dock, int cols) {
        this.pkg = pkg;
        this.dock = dock;
        this.cols = cols;
        this.initial = Collections.unmodifiableList(new ArrayList<>(shown));
        this.current = new ArrayList<>(shown);
    }

    /** A move of pkg in the shown dock or grid; null when pkg is not shown there or has nowhere to go. */
    static MoveMode start(List<String> shown, String pkg, boolean dock, int cols) {
        if (!shown.contains(pkg) || shown.size() < 2) return null;
        return new MoveMode(shown, pkg, dock, cols);
    }

    /** Moves the app one step in that direction; false (and nothing changes) when the step is not allowed. */
    boolean step(int direction) {
        int i = index(), d = delta(direction), j = i + d;
        if (d == 0 || j < 0 || j >= current.size()) return false;
        current.remove(i);
        current.add(j, pkg);
        deltas.add(d);
        return true;
    }

    private int delta(int direction) {
        switch (direction) {
            case LEFT: return -1;
            case RIGHT: return 1;
            case UP: return dock ? 0 : -cols;
            case DOWN: return dock ? 0 : cols;
            default: return 0;
        }
    }

    /**
     * Opacity of the large hero label (#57): it always names a dock app, so while any app is being moved it is
     * hidden (the banner names the moving app instead); otherwise full on the dock, dimmed elsewhere, as before.
     */
    static float heroAlpha(boolean moving, boolean dockFocused) {
        if (moving) return 0f;
        return dockFocused ? 1f : 0.4f;
    }

    /**
     * Top of the move banner (#57): its place just above the dock band (anchorTop, page not scrolled) moved with
     * the page, but never above minTop, so it stays on screen when a long grid scrolls the hero area away.
     */
    static float hintTop(float anchorTop, float pageShift, float minTop) {
        return Math.max(anchorTop + pageShift, minTop);
    }

    List<String> current() { return Collections.unmodifiableList(current); }
    List<String> initial() { return initial; }
    int index() { return current.indexOf(pkg); }
    int initialIndex() { return initial.indexOf(pkg); }
    boolean changed() { return !current.equals(initial); }

    /** The dock edit OK writes: the final dock order, hidden apps untouched; unchanged when it is back in place. */
    HomeApps.Edit dockEdit(List<String> hidden) {
        return new HomeApps.Edit(new ArrayList<>(current), hidden, changed(), false);
    }

    /**
     * The grid order= OK writes, or null when the app is back in its place: the steps replayed through
     * GridOrder.move, so hidden and dock entries are kept exactly as a series of single-step moves keeps them.
     */
    List<String> gridOrder(List<String> order, List<String> installedByLabel, Collection<String> hidden) {
        if (!changed()) return null;
        List<String> o = order, g = new ArrayList<>(initial);
        for (int d : deltas) {
            List<String> next = GridOrder.move(o, installedByLabel, hidden, g, pkg, d);
            if (next == null) return null;   // cannot happen for steps step() accepted; never write a guess
            o = next;
            int i = g.indexOf(pkg);
            g.remove(i);
            g.add(i + d, pkg);
        }
        return o;
    }
}
