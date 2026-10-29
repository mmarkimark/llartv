package io.github.mmarkimark.llartv;

/**
 * The screensaver music (issue #17).
 *
 * The app ships no stream of its own: the owner points it at one from the configuration, so no reuse terms
 * are baked into the release. The music plays only while the screensaver is showing, and a stream that is
 * missing or that fails leaves the screensaver silent.
 */
final class Music {
    private Music() {}

    /** True when the screensaver has to play music: the option is on and there is a stream to play. */
    static boolean shouldPlay(Config conf) {
        return conf.musicScreen && isStream(conf.musicUrl);
    }

    /** True for a URL the player can stream: https. Android has blocked cleartext traffic since Android 9, so an
     *  http stream would fail silently; a local server needs a certificate the TV trusts. */
    static boolean isStream(String url) {
        if (url == null) return false;
        return url.trim().startsWith("https://");
    }
}
