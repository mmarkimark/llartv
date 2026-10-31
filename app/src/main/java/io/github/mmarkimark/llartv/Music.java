package io.github.mmarkimark.llartv;

/**
 * The screensaver music (issue #17).
 *
 * Enabled music rotates verified cached tracks unless the owner supplies a custom HTTPS stream.
 * No audio is bundled in the APK. Playback belongs to the dream and failures leave it silent.
 */
final class Music {
    private Music() {}

    /** No network or player is used while the option is off. An invalid custom URL never falls back silently. */
    static boolean shouldPlay(Config conf) {
        return conf.musicScreen && (isDefault(conf.musicUrl) || isStream(conf.musicUrl));
    }

    static boolean isDefault(String url) { return url == null || url.trim().isEmpty(); }

    /** HTTPS candidate only: this app keeps Android's default cleartext policy. The platform player still
     *  decides whether the URL's content is supported; a private server needs a certificate the TV trusts. */
    static boolean isStream(String url) {
        if (url == null) return false;
        return url.trim().startsWith("https://");
    }
}
