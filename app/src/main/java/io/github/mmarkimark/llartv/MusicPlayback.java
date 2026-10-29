package io.github.mmarkimark.llartv;

/** One dream's audio lifecycle. All calls are serialized on Android's main thread; no Android dependency here. */
final class MusicPlayback {
    enum Focus { GAIN, TRANSIENT_LOSS, LOSS }

    interface Output {
        void prepare();
        boolean acquireFocus();
        void start();
        void pause();
        void release();
        void abandonFocus();
    }

    private final Output output;
    private boolean active = true, started, ready, focused, playing;

    MusicPlayback(Output output) { this.output = output; }

    void start() {
        if (!active || started) return;
        started = true;
        try { output.prepare(); } catch (RuntimeException e) { stop(); }
    }

    void prepared() {
        if (!active || !started || ready) return;
        ready = true;
        try {
            // Take focus only when there is audio ready to play, not while waiting for the network.
            focused = output.acquireFocus();
            if (focused) play(); else stop();
        } catch (RuntimeException e) { stop(); }
    }

    void focus(Focus change) {
        if (!active || !ready) return;
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

    void completed() { stop(); }
    void failed() { stop(); }

    void stop() {
        if (!active) return;
        active = false; // invalidate queued prepared/focus/error callbacks before releasing resources
        playing = focused = false;
        try { output.release(); } catch (RuntimeException ignored) {}
        try { output.abandonFocus(); } catch (RuntimeException ignored) {}
    }
}
