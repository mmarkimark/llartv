package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LookTest {
    static int r(int c) { return (c >> 16) & 0xFF; }
    static int g(int c) { return (c >> 8) & 0xFF; }
    static int b(int c) { return c & 0xFF; }

    @Test public void bandTintKeepsTheHueAndCapsBrightness() {
        int c = Look.bandTint(200, 100, 50);              // warm photo
        assertEquals(0xDB, c >>> 24);                      // about 86% opaque
        assertEquals(46, r(c));                            // brightest channel capped at 46
        assertEquals(23, g(c));
        assertEquals(12, b(c));
    }

    @Test public void blackPhotoGivesTheUsualBand() {
        assertEquals(Look.DEFAULT_BAND, Look.bandTint(0, 0, 0));
    }

    @Test public void inkGoesFromWhiteToCream() {
        assertEquals(0xFFFFFFFF, Look.ink(0xFFFFFFFF, 0f));
        assertEquals(Look.CREAM, Look.ink(0xFFFFFFFF, 1f));
        int half = Look.ink(0xFFFFFFFF, 0.5f);
        assertEquals((0xFF + r(Look.CREAM)) / 2, r(half), 1);
    }

    @Test public void nightFilterIsIdentityByDayAndDarkAtNight() {
        float[] day = Look.nightMatrix(0f);
        assertArrayEquals(new float[]{1,0,0,0,0, 0,1,0,0,0, 0,0,1,0,0, 0,0,0,1,0}, day, 1e-5f);
        float[] night = Look.nightMatrix(1f);
        // a white pixel ends up dim and warm: red > green > blue, all well below full brightness
        float rr = night[0] + night[1] + night[2], gg = night[5] + night[6] + night[7], bb = night[10] + night[11] + night[12];
        assertEquals(true, rr < 0.6f && rr > gg && gg > bb);
    }
}
