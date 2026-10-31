package io.github.mmarkimark.llartv;

/**
 * The quiet night screensaver (issue #37): two independent options, both off by default, so an installation that
 * never sets them keeps its screensaver exactly as before.
 *
 * Control model. Both options act only inside the existing night band (home.conf "night", read through
 * Config.bands() as TimeBands.Band.NIGHT); with night mode off they never act.
 *   night_screen=on  the quiet visual: the photos, their credits and the weather are hidden, there is no zoom or
 *                    motion, the background is black and the clock is always shown, even with clock_screen=off.
 *                    Outside the night band clock_screen decides again. The date stays.
 *   night_mute=on    the night silence: no screensaver music inside the night band. It does not depend on the
 *                    visual, and the visual alone does not stop the music.
 *
 * Music policy. music_screen=off (Music.shouldPlay false) never plays, at any hour. A session that starts inside
 * the night band starts silent. A running session that enters the night band stops its music (STOP); one that
 * leaves it starts a fresh music session (START, through BingDream.startMusic, which stops any previous player
 * first, so two players never overlap). The first evaluation of a session and an unchanged band change nothing.
 *
 * This only blanks the screensaver's own content; it does not turn the backlight off and does not promise to
 * prevent burn-in.
 */
final class NightQuiet {
    /** What a band evaluation asks the music to do. */
    static final int KEEP = 0, START = 1, STOP = 2;

    final boolean screen, mute;

    NightQuiet(boolean screen, boolean mute) {
        this.screen = screen;
        this.mute = mute;
    }

    /** True when the quiet visual is shown: night_screen on and inside the night band. */
    boolean quiet(boolean night) {
        return screen && night;
    }

    /** The time is shown when clock_screen is on, or always while the quiet visual is shown. */
    static boolean clockShown(boolean clockScreen, boolean quiet) {
        return clockScreen || quiet;
    }

    /** Whether music may play now: music_screen allows it and the night silence does not hold it back. */
    boolean musicAllowed(boolean shouldPlay, boolean night) {
        return shouldPlay && !(mute && night);
    }

    /**
     * The music change when the band is evaluated again. prevNight is null on the first evaluation of a session,
     * which only records the band: onDreamingStarted already started (or did not start) the music.
     */
    int musicChange(Boolean prevNight, boolean night, boolean shouldPlay) {
        if (prevNight == null || prevNight == night || !mute || !shouldPlay) return KEEP;
        return night ? STOP : START;
    }
}
