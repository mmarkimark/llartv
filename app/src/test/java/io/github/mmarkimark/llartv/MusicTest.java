package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** When the screensaver plays music (issue #17): only when it is switched on and there is a stream to play. */
public class MusicTest {
    @Test public void itIsSilentUntilItIsSwitchedOn() {
        Config k = new Config();
        assertFalse(Music.shouldPlay(k));
        k.set("music_url", "https://example.org/lofi.mp3");
        assertFalse(Music.shouldPlay(k));   // a stream without the option is still silence
        k.set("music_screen", "on");
        assertTrue(Music.shouldPlay(k));
    }

    @Test public void enablingMusicWithoutAnOverrideSelectsTheDefaultTrack() {
        Config k = new Config();
        assertFalse(Music.shouldPlay(k));
        k.set("music_screen", "on");
        assertTrue(Music.shouldPlay(k));
        assertTrue(Music.isDefault(k.musicUrl));
        k.set("music_url", "   ");
        assertTrue(Music.shouldPlay(k));
        k.set("music_screen", "off");
        assertFalse(Music.shouldPlay(k));
    }

    @Test public void switchingItOffSilencesItAgain() {
        Config k = new Config();
        k.set("music_screen", "on");
        k.set("music_url", "https://example.org/lofi.mp3");
        assertTrue(Music.shouldPlay(k));
        k.set("music_screen", "off");
        assertFalse(Music.shouldPlay(k));
    }

    @Test public void aCustomStreamStillWinsAndInvalidOverridesStaySilent() {
        Config k = new Config();
        k.set("music_screen", "on");
        k.set("music_url", "https://example.org/station.mp3");
        assertTrue(Music.shouldPlay(k));
        assertFalse(Music.isDefault(k.musicUrl));
        for (String invalid : new String[]{"http://example.org/music", "file:///sdcard/music", "not-a-url"}) {
            k.set("music_url", invalid);
            assertFalse(Music.isDefault(k.musicUrl));
            assertFalse(Music.shouldPlay(k));
        }
    }

    @Test public void onlyAnHttpsStreamCounts() {
        assertTrue(Music.isStream("https://example.org/lofi.mp3"));
        assertTrue(Music.isStream("  https://example.org/lofi.mp3  "));
        assertFalse(Music.isStream("http://example.org/lofi.mp3"));    // Android has blocked cleartext since Android 9
        assertFalse(Music.isStream("file:///sdcard/lofi.mp3"));       // a local file would need a permission
        assertFalse(Music.isStream("example.org/lofi.mp3"));
        assertFalse(Music.isStream(""));
        assertFalse(Music.isStream("   "));
        assertFalse(Music.isStream(null));
    }
}
