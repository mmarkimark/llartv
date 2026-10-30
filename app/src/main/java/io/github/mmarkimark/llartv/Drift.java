package io.github.mmarkimark.llartv;

/** The screensaver's slow zoom and drift for each image, kept free of Android so the JVM tests can pin it. */
final class Drift {
    private Drift() {}

    /**
     * {start scale, end scale, end x, end y} for image n on a w x h screen (the image starts at -x, -y), or null
     * when motion is off: the image then stays still and no animation runs at all, only the cross-fade between
     * images. Even images zoom in and odd ones zoom out, and the drift direction alternates, so consecutive images
     * differ; the drift stays inside the margin the smaller scale leaves, so no black edge ever shows.
     */
    static float[] of(int n, int w, int h, boolean motion) {
        if (!motion) return null;
        boolean zoomIn = n % 2 == 0;
        float s0 = zoomIn ? 1.06f : 1.16f, s1 = zoomIn ? 1.16f : 1.06f;
        float mx = (1.06f - 1f) * w / 2f * 0.8f, my = (1.06f - 1f) * h / 2f * 0.8f;
        float dx = ((n / 2) % 2 == 0 ? 1 : -1) * mx, dy = (n % 3 == 0 ? -1 : 1) * my;
        return new float[]{s0, s1, dx, dy};
    }
}
