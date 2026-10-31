package io.github.mmarkimark.llartv;

/** One dream's audio lifecycle. All calls are serialized on Android's main thread; no Android dependency here. */
final class MusicPlayback {
    static final long ROTATE_MS = 120_000L;
    enum Focus { GAIN, TRANSIENT_LOSS, LOSS }

    interface Output {
        void prepare();
        boolean acquireFocus();
        void start();
        void pause();
        void release();
        void abandonFocus();
        default void nextTrack() { throw new IllegalStateException("playlist output unavailable"); }
    }

    private final Output output;
    private final boolean playlist;
    private boolean active = true, started, sourceResolved, ready, focused, playing, focusRequested;

    MusicPlayback(Output output) { this(output, false); }
    MusicPlayback(Output output, boolean playlist) { this.output = output; this.playlist = playlist; }

    void start() {
        if (!active || started) return;
        started = true;
        try { output.prepare(); } catch (RuntimeException e) { stop(); }
    }

    /** A worker may resolve a cached file after exit: do not even allocate a late player. */
    void sourceReady(Runnable prepare) {
        if (!active || !started || sourceResolved || ready) return;
        sourceResolved = true;
        try { prepare.run(); } catch (RuntimeException e) { stop(); }
    }

    void prepared() {
        if (!active || !started || ready) return;
        ready = true;
        try {
            // Take focus only when there is audio ready to play, not while waiting for the network.
            if (!focusRequested) {
                focusRequested = true;
                focused = output.acquireFocus();
                if (!focused) { stop(); return; }
            }
            play();
        } catch (RuntimeException e) { stop(); }
    }

    void focus(Focus change) {
        if (!active || (!ready && !focusRequested)) return;
        try {
            if (change == Focus.LOSS) {
                stop(); // permanent loss is terminal; a late GAIN must not restart this dream's player
            } else if (change == Focus.TRANSIENT_LOSS) {
                focused = false;
                if (playing) { output.pause(); playing = false; }
            } else {
                focused = true;
                play();
            }
        } catch (RuntimeException e) { stop(); }
    }

    private void play() {
        if (active && ready && focused && !playing) {
            output.start();
            playing = true;
        }
    }

    void rotate() { if (playlist && playing) next(); }

    private void next() {
        if (!active || !ready) return;
        ready = sourceResolved = playing = false;
        try { output.nextTrack(); } catch (RuntimeException e) { stop(); }
    }

    void completed() { if (playlist) next(); else stop(); }
    void failed() { stop(); }

    void stop() {
        if (!active) return;
        active = false; // invalidate queued prepared/focus/error callbacks before releasing resources
        playing = focused = false;
        try { output.release(); } catch (RuntimeException ignored) {}
        try { output.abandonFocus(); } catch (RuntimeException ignored) {}
    }
}
