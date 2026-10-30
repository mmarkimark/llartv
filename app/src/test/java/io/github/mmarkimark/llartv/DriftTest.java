package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DriftTest {
    @Test public void withoutMotionThereIsNoDriftToAnimate() {
        for (int n = 0; n < 6; n++) assertNull(Drift.of(n, 1920, 1080, false));
    }

    @Test public void withMotionTheZoomAndTheDriftAreTheUsualOnes() {
        // even images zoom in, odd ones zoom out; the drift stays inside the 6% margin of the smaller scale
        float mx = 0.06f * 1920 / 2f * 0.8f, my = 0.06f * 1080 / 2f * 0.8f;
        assertArrayEquals(new float[]{1.06f, 1.16f, mx, -my}, Drift.of(0, 1920, 1080, true), 0.001f);
        assertArrayEquals(new float[]{1.16f, 1.06f, mx, my}, Drift.of(1, 1920, 1080, true), 0.001f);
        assertArrayEquals(new float[]{1.06f, 1.16f, -mx, my}, Drift.of(2, 1920, 1080, true), 0.001f);
        assertArrayEquals(new float[]{1.16f, 1.06f, -mx, -my}, Drift.of(3, 1920, 1080, true), 0.001f);
    }
}
