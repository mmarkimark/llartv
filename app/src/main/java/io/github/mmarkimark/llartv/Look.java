package io.github.mmarkimark.llartv;

/** Colour maths for night mode and the photo-tinted dock band. Pure functions, so they can be unit-tested. */
final class Look {
    private Look() {}

    static final int DEFAULT_BAND = 0xFF141416;   // the dock band colour without a tint
    static final int CREAM = 0xFFECD3AD;          // text colour at night
    static final int BAND_MAX = 46;               // brightest channel of a tinted band, so banners stay readable
    static final int BAND_ALPHA = 0xDB;           // about 86% opaque

    /** Dark, translucent band colour with the hue of a photo's average colour. */
    static int bandTint(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b));
        if (max < 1) return DEFAULT_BAND;
        float k = BAND_MAX / (float) max;
        return BAND_ALPHA << 24 | Math.round(r * k) << 16 | Math.round(g * k) << 8 | Math.round(b * k);
    }

    /** Text colour between its day colour and cream; amount 0 = day, 1 = night. */
    static int ink(int day, float amount) {
        int a = day >>> 24;
        return a << 24 | mix((day >> 16) & 0xFF, (CREAM >> 16) & 0xFF, amount) << 16
                | mix((day >> 8) & 0xFF, (CREAM >> 8) & 0xFF, amount) << 8 | mix(day & 0xFF, CREAM & 0xFF, amount);
    }

    static int mix(int from, int to, float t) { return Math.round(from + (to - from) * t); }

    /**
     * 4x5 colour matrix (android.graphics.ColorMatrix layout) that darkens, desaturates and warms the wallpaper
     * as night mode fades in: identity at 0; at 1 about 40% brightness, 55% saturation and a warm cast.
     */
    static float[] nightMatrix(float amount) {
        float bright = 1f - 0.60f * amount, sat = 1f - 0.45f * amount;
        float[] warm = {1f, 1f - 0.12f * amount, 1f - 0.30f * amount};
        float[] lum = {0.2126f, 0.7152f, 0.0722f};
        float[] m = new float[20];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                float s = (1f - sat) * lum[col] + (row == col ? sat : 0f);
                m[row * 5 + col] = s * bright * warm[row];
            }
        }
        m[18] = 1f;
        return m;
    }
}
