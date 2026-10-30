package io.github.mmarkimark.llartv;

/** The geometry of the settings side panel, kept free of Android so the JVM tests can pin it. */
final class SidePanel {
    private SidePanel() {}

    /** The Android TV settings panel takes the right 3/8 of the screen (720 of 1920 px on the reference TV). */
    static int width(int screenWidth) { return Math.round(screenWidth * 3f / 8f); }

    /**
     * The scroll position that shows a row between top and bottom inside a view of the given height scrolled to
     * scrollY: unchanged when the row is already visible, otherwise the smallest move that shows it, top first.
     */
    static int scrollFor(int top, int bottom, int scrollY, int height) {
        if (top < scrollY || bottom - top > height) return top;
        if (bottom > scrollY + height) return bottom - height;
        return scrollY;
    }
}
