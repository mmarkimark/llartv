package io.github.mmarkimark.llartv;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Android adapter for one dream. Each callback keeps its own session, never a later dream's player. */
final class DreamMusic implements MusicPlayback.Output {
    private final MusicPlayback session = new MusicPlayback(this);
    private final AudioManager audio;
    private final AudioFocusRequest focus;
    private final AudioAttributes attributes;
    private final String url;
    private final boolean local;
    private final File cacheDir;
    private final Handler ui;
    private ExecutorService loader;
    private Future<?> loading;
    private MediaPlayer player;
    private boolean requested;

    DreamMusic(Context context, Handler ui, String url) {
        this.url = url;
        this.ui = ui;
        local = Music.isDefault(url);
        cacheDir = context.getFilesDir();
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
        if (!local) { preparePlayer(url.trim(), false); return; }
        loader = Executors.newSingleThreadExecutor();
        loading = loader.submit(() -> {
            try {
                File file = MusicCache.load(cacheDir);
                ui.post(() -> session.sourceReady(() -> preparePlayer(file.getAbsolutePath(), true)));
            } catch (IOException | RuntimeException e) {
                Log.i("LlarTV", "screensaver music: cache unavailable (" + MusicCache.failureReason(e)
                        + "), staying silent");
                ui.post(session::failed);
            } finally { loader.shutdown(); }
        });
    }

    private void preparePlayer(String source, boolean loop) {
        player = new MediaPlayer(); // created on the main thread, so MediaPlayer callbacks use the same looper
        player.setAudioAttributes(attributes);
        player.setLooping(loop);
        player.setOnPreparedListener(mp -> session.prepared());
        player.setOnCompletionListener(mp -> session.completed());
        player.setOnErrorListener((mp, what, extra) -> {
            Log.i("LlarTV", "screensaver music: stream failed (" + what + ", " + extra + "), staying silent");
            session.failed();
            return true;
        });
        try {
            player.setDataSource(source);
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
        Log.i("LlarTV", "screensaver music: playing" + (local ? " (cached loop)" : ""));
    }

    @Override public void pause() { player.pause(); }

    @Override public void release() {
        if (loading != null) loading.cancel(true);
        if (loader != null) loader.shutdownNow();
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
