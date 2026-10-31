package io.github.mmarkimark.llartv;

/** Original audio bytes and immutable integrity pins. Audio lives outside the APK. */
final class MusicTrack {
    static final int COUNT = 3;
    private static final String RELEASE = "https://github.com/mmarkimark/llartv/releases/download/v1.4/";
    final String title, file, url, sha256;
    final long bytes;

    MusicTrack(String title, String file, String url, long bytes, String sha256) {
        this.title = title; this.file = file; this.url = url; this.bytes = bytes; this.sha256 = sha256;
    }

    private static final MusicTrack[] TRACKS = {
        new MusicTrack("Dancing", "music-holizna-dancing-v1.mp3", MusicCache.URL,
                MusicCache.BYTES, MusicCache.SHA256),
        new MusicTrack("Chill Wave", "music-macleod-chill-wave-v1.mp3",
                RELEASE + "music-macleod-chill-wave-v1.mp3", 7_684_325L,
                "7f07fbf89860959ff527f7b74606146dd0b0de263739989a70ce712c7c361614"),
        new MusicTrack("Dream Culture", "music-macleod-dream-culture-v1.mp3",
                RELEASE + "music-macleod-dream-culture-v1.mp3", 8_576_659L,
                "f12cbb52bd4b92bd6cf8a343645567c119627e9cbf98108a3eb6ac2cc275ee46")
    };

    static MusicTrack at(int index) { return TRACKS[index]; }
    boolean releaseAsset() { return url.startsWith(RELEASE); }
}
