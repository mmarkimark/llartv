package io.github.mmarkimark.llartv;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The pure rules of the dock and the hidden apps, so the remote menu and the settings panel share them with
 * loadApps and they can be tested on the JVM. Everything here works on package names, in the order the dock
 * shows them; hidden is kept as an ordered list so the value written to home.conf is stable.
 */
final class HomeApps {
    private HomeApps() {}

    /** One edit: the new package lists to write, and which lines of home.conf change. */
    static final class Edit {
        final List<String> dock, hidden;
        final boolean dockChanged, hiddenChanged;

        Edit(List<String> dock, List<String> hidden, boolean dockChanged, boolean hiddenChanged) {
            this.dock = dock;
            this.hidden = hidden;
            this.dockChanged = dockChanged;
            this.hiddenChanged = hiddenChanged;
        }

        boolean changed() { return dockChanged || hiddenChanged; }
    }

    static Edit addToDock(List<String> dock, List<String> hidden, String pkg, int columns) {
        // Capacity is the number of columns; a hidden app is never in the dock.
        if (dock.size() >= columns || dock.contains(pkg) || hidden.contains(pkg))
            return new Edit(dock, hidden, false, false);
        List<String> d = new ArrayList<>(dock);
        d.add(pkg);
        return new Edit(d, hidden, true, false);
    }

    static Edit removeFromDock(List<String> dock, List<String> hidden, String pkg) {
        if (!dock.contains(pkg)) return new Edit(dock, hidden, false, false);
        List<String> d = new ArrayList<>(dock);
        d.remove(pkg);
        return new Edit(d, hidden, true, false);
    }

    /** Moves pkg one place towards the start (-1) or the end (1); at either end it writes nothing. */
    static Edit moveInDock(List<String> dock, List<String> hidden, String pkg, int delta) {
        int i = dock.indexOf(pkg), j = i + delta;
        if (i < 0 || j < 0 || j >= dock.size()) return new Edit(dock, hidden, false, false);
        List<String> d = new ArrayList<>(dock);
        d.remove(i);
        d.add(j, pkg);
        return new Edit(d, hidden, true, false);
    }

    /**
     * Hides pkg: out of the grid and, by the existing rule (hidden apps are filtered before the dock is
     * built), out of the dock too — the dock line itself is not rewritten.
     */
    static Edit hideApp(List<String> dock, List<String> hidden, String pkg) {
        boolean changed = !hidden.contains(pkg);
        List<String> h = new ArrayList<>(hidden);
        if (changed) h.add(pkg);
        return new Edit(dock, h, false, changed);
    }

    static Edit unhide(List<String> dock, List<String> hidden, String pkg) {
        if (!hidden.contains(pkg)) return new Edit(dock, hidden, false, false);
        List<String> h = new ArrayList<>(hidden);
        h.remove(pkg);
        return new Edit(dock, h, false, true);
    }

    /** The dock and the grid packages after composition. */
    static final class Composed {
        final List<String> dock, grid;

        Composed(List<String> dock, List<String> grid) {
            this.dock = dock;
            this.grid = grid;
        }
    }

    /**
     * Places the apps: the dock order's installed, non-hidden packages, in order, capped at columns; the rest
     * keeps the order given (the grid is alphabetically sorted by label). With fill, an incomplete dock takes
     * the first apps of the grid, like the built-in default list always has; an explicit dock= list shows
     * exactly its apps, however short (S4).
     */
    static Composed compose(List<String> dockOrder, List<String> installedByLabel, Set<String> hidden, boolean fill, int columns) {
        List<String> dock = new ArrayList<>(), grid = new ArrayList<>();
        for (String p : dockOrder) {
            if (dock.size() >= columns) break;
            if (hidden.contains(p) || !installedByLabel.contains(p) || dock.contains(p)) continue;
            dock.add(p);
        }
        for (String p : installedByLabel) if (!hidden.contains(p) && !dock.contains(p)) grid.add(p);
        if (fill) while (dock.size() < columns && !grid.isEmpty()) dock.add(grid.remove(0));
        return new Composed(dock, grid);
    }
}
