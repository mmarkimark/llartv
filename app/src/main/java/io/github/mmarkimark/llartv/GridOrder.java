package io.github.mmarkimark.llartv;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The pure rules of the grid order (home.conf: order=), testable on the JVM. The order is a list of package
 * names with no fixed slots: the grid shows its installed apps in that order, then every other app in the label
 * order, so a newly installed app is appended without moving the others, and an uninstalled one leaves no hole.
 * The order never decides the dock; the dock is placed first, and the grid is what is left (HomeApps.compose).
 */
final class GridOrder {
    private GridOrder() {}

    /** The grid apps, given in label order, rearranged by order; an empty order keeps them as given. */
    static List<String> apply(List<String> order, List<String> grid) {
        Set<String> out = new LinkedHashSet<>();
        for (String p : order) if (grid.contains(p)) out.add(p);
        out.addAll(grid);
        return new ArrayList<>(out);
    }

    /**
     * The order to write after moving pkg delta places in the shown grid (1 / -1 for one place, the column count
     * for a row), or null when the move would leave the grid. The first move writes the whole sequence of
     * installed apps (dock ones included), so later installs append; entries of hidden apps keep their place,
     * even when not installed, while other uninstalled entries are dropped, so a reinstalled app counts as new.
     */
    static List<String> move(List<String> order, List<String> installedByLabel, Collection<String> hidden,
                             List<String> grid, String pkg, int delta) {
        int i = grid.indexOf(pkg), j = i + delta;
        if (i < 0 || delta == 0 || j < 0 || j >= grid.size()) return null;
        Set<String> seq = new LinkedHashSet<>();
        for (String p : order) if (installedByLabel.contains(p) || hidden.contains(p)) seq.add(p);
        seq.addAll(installedByLabel);
        List<String> out = new ArrayList<>(seq);
        out.remove(pkg);
        int at = out.indexOf(grid.get(j));
        out.add(delta > 0 ? at + 1 : at, pkg);
        return out;
    }
}
