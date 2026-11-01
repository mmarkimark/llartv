package io.github.mmarkimark.llartv;

/**
 * Minimal mode (home.conf "minimal", off by default): one reversible switch for a fast, quiet Home.
 *
 * While it is on, Home and the screensaver read the settings below through these getters instead of the stored
 * fields, and every one of them reads "off": no weather (Home and screensaver), no news, no quote, no wallpaper (a
 * black background, nothing downloaded, not even a pushed image), no screensaver photos (the clock and the date on
 * black), no football card or fixtures download, no screensaver music and the neutral dock colour instead of the
 * photo tint, and no focus glow behind the app name. The stored values are never rewritten, so switching it off brings back exactly what was chosen
 * before. This is the one place that decides it; callers never test Config.minimal themselves.
 */
final class MinimalMode {
    private MinimalMode() {}

    static boolean on(Config c) { return c.minimal; }

    /** The weather on the home screen. */
    static int weatherHome(Config c) { return c.minimal ? ContentPolicy.NEVER : c.weatherHome; }

    /** The weather on the screensaver. */
    static int weatherScreen(Config c) { return c.minimal ? ContentPolicy.NEVER : c.weatherScreen; }

    /** The news on the home screen. */
    static int newsHome(Config c) { return c.minimal ? ContentPolicy.NEVER : c.newsHome; }

    /** The quote of the day under the date. */
    static boolean quote(Config c) { return !c.minimal && c.quote; }

    /** The wallpaper source: empty (none, nothing downloaded, no screensaver photos) while minimal is on. */
    static String wallpaper(Config c) { return c.minimal ? "" : c.wallpaper; }

    /** Whether Home shows an image at all; false keeps it black even when a wallpaper.jpg was pushed. */
    static boolean wallpaperShown(Config c) { return !c.minimal; }

    /** The dock band takes its colour from the photo; false keeps the neutral colour. */
    static boolean dockTint(Config c) { return !c.minimal && c.dockTint; }

    /** The soft glow behind the focused app's name, coloured from its banner; off in minimal mode (owner L15). */
    static boolean focusGlow(Config c) { return !c.minimal; }

    /** The screensaver music option (Music.shouldPlay also needs a usable stream). */
    static boolean musicScreen(Config c) { return !c.minimal && c.musicScreen; }

    /** The football team: empty (no card, no fixtures download) while minimal is on. */
    static String teamId(Config c) { return c.minimal ? "" : c.teamId; }

    /**
     * Home builds its tiles, its cards and the team once, in onCreate, so a change to any of them rebuilds it: the
     * columns, the team in effect or the minimal switch itself (its views are not built while it is on).
     */
    static boolean homeNeedsRebuild(Config before, Config after) {
        return before.columns != after.columns || before.minimal != after.minimal
                || !teamId(before).equals(teamId(after));
    }
}
