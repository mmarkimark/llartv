package io.github.mmarkimark.llartv;

/** Where a popup menu goes next to its anchor, kept free of Android so the JVM tests can pin it. */
final class MenuPlacement {
    private MenuPlacement() {}

    /**
     * The top of a menu of the given height: below the anchor when it fits on the screen, otherwise above it (the
     * dock and the lower grid rows), and never off the screen. The margin is kept from the anchor and the edges.
     */
    static float top(float anchorTop, float anchorBottom, float menuHeight, float screenHeight, float margin) {
        float below = anchorBottom + margin;
        if (below + menuHeight <= screenHeight - margin) return below;
        return Math.max(margin, anchorTop - margin - menuHeight);
    }
}
