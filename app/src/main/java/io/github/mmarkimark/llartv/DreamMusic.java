package io.github.mmarkimark.llartv;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.util.Log;
import java.io.IOException;

/** Android adapter for one dream. Each callback keeps its own session, never a later dream's player. */
final class DreamMusic implements MusicPlayback.Output {
    private final MusicPlayback session = new MusicPlayback(this);
    private final AudioManager audio;
    private final AudioFocusRequest focus;
    private final AudioAttributes attributes;
    private final String url;
    private MediaPlayer player;
    private boolean requested;

    DreamMusic(Context context, Handler ui, String url) {
        this.url = url;
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(change -> {
                    switch (change) {
                        case AudioManager.AUDIOFOCUS_GAIN: session.focus(MusicPlayback.Focus.GAIN); break;
                        case AudioManager.AUDIOFOCUS_LOSS: session.focus(MusicPlayback.Focus.LOSS); break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            session.focus(MusicPlayback.Focus.TRANSIENT_LOSS); break;
                    }
                }, ui).build();
    }

    void begin() { session.start(); }
    void stop() { session.stop(); }

    @Override public void prepare() {
        player = new MediaPlayer(); // created on the main thread, so MediaPlayer callbacks use the same looper
        player.setAudioAttributes(attributes);
        player.setOnPreparedListener(mp -> session.prepared());
        player.setOnCompletionListener(mp -> session.completed());
        player.setOnErrorListener((mp, what, extra) -> {
            Log.i("LlarTV", "screensaver music: stream failed (" + what + ", " + extra + "), staying silent");
            session.failed();
            return true;
        });
        try {
            player.setDataSource(url.trim());
            player.prepareAsync();
        } catch (IOException e) {
            Log.i("LlarTV", "screensaver music: could not prepare, staying silent");
            throw new IllegalStateException(e); // never log the URL: it may contain credentials or tokens
        }
    }

    @Override public boolean acquireFocus() {
        if (audio == null) return false;
        requested = true;
        return audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    @Override public void start() {
        player.start();
        Log.i("LlarTV", "screensaver music: playing");
    }

    @Override public void pause() { player.pause(); }

    @Override public void release() {
        if (player == null) return;
        MediaPlayer old = player;
        player = null;
        old.release(); // release is valid during preparation too; stop() is not
    }

    @Override public void abandonFocus() {
        if (requested) {
            requested = false;
            audio.abandonAudioFocusRequest(focus);
        }
    }
}
