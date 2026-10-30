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

    /** HTTPS candidate only: this app keeps Android's default cleartext policy. The platform player still
     *  decides whether the URL's content is supported; a private server needs a certificate the TV trusts. */
    static boolean isStream(String url) {
        if (url == null) return false;
        return url.trim().startsWith("https://");
    }
}
