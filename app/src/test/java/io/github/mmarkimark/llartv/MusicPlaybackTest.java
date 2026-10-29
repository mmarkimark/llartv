package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class MusicPlaybackTest {
    static final class Output implements MusicPlayback.Output {
        final List<String> events = new ArrayList<>();
        boolean granted = true;
        String throwsAt = "";
        void event(String name) {
            events.add(name);
            if (name.equals(throwsAt)) throw new IllegalStateException(name);
        }
        public void prepare() { event("prepare"); }
        public boolean acquireFocus() { event("focus"); return granted; }
        public void start() { event("start"); }
        public void pause() { event("pause"); }
        public void release() { event("release"); }
        public void abandonFocus() { event("abandon"); }
        void expect(String... expected) { assertEquals(Arrays.asList(expected), events); }
    }

    @Test public void playbackAndDreamExitStillWorkAndCleanupIsIdempotent() {
        Output out = new Output();
        MusicPlayback p = new MusicPlayback(out);
        p.start();
        out.expect("prepare"); // do not take focus while the network is preparing
        p.prepared();
        out.expect("prepare", "focus", "start");
        p.stop();
        p.stop(); // onDreamingStopped and onDetachedFromWindow may both run
        out.expect("prepare", "focus", "start", "release", "abandon");
    }

    @Test public void deniedFocusNeverStartsEvenAfterLateCallbacks() {
        Output out = new Output();
        out.granted = false;
        MusicPlayback p = new MusicPlayback(out);
        p.start();
        p.prepared();
        p.focus(MusicPlayback.Focus.GAIN);
        p.prepared();
        out.expect("prepare", "focus", "release", "abandon");
    }

    @Test public void permanentLossReleasesAndCannotResume() {
        Output out = new Output();
        MusicPlayback p = new MusicPlayback(out);
        p.start();
        p.prepared();
        p.focus(MusicPlayback.Focus.LOSS);
        p.focus(MusicPlayback.Focus.GAIN);
        p.prepared();
        p.stop();
        out.expect("prepare", "focus", "start", "release", "abandon");
    }

    @Test public void transientLossPausesAndOnlyGainResumes() {
        Output out = new Output();
        MusicPlayback p = new MusicPlayback(out);
        p.start();
        p.prepared();
        p.focus(MusicPlayback.Focus.TRANSIENT_LOSS);
        p.prepared(); // a duplicate prepared callback cannot override focus loss
        p.focus(MusicPlayback.Focus.TRANSIENT_LOSS);
        out.expect("prepare", "focus", "start", "pause");
        p.focus(MusicPlayback.Focus.GAIN);
        p.focus(MusicPlayback.Focus.GAIN);
        p.stop();
        out.expect("prepare", "focus", "start", "pause", "start", "release", "abandon");
    }

    @Test public void exitingDuringPreparationIgnoresAllLateCallbacks() {
        Output out = new Output();
        MusicPlayback p = new MusicPlayback(out);
        p.start();
        p.stop();
        p.prepared();
        p.focus(MusicPlayback.Focus.GAIN);
        p.failed();
        p.completed();
        p.start(); // stopped instances are not reused for a later dream
        out.expect("prepare", "release", "abandon");
    }

    @Test public void completionAndStreamFailureReleaseWithoutRetrying() {
        for (boolean complete : new boolean[]{true, false}) {
            Output out = new Output();
            MusicPlayback p = new MusicPlayback(out);
            p.start();
            if (complete) {
                p.prepared();
                p.completed();
            } else p.failed(); // includes errors during preparation
            p.prepared();
            p.focus(MusicPlayback.Focus.GAIN);
            p.stop();
            if (complete) out.expect("prepare", "focus", "start", "release", "abandon");
            else out.expect("prepare", "release", "abandon");
        }
    }

    @Test public void platformExceptionsLeaveTheSessionSilentAndReleased() {
        for (String operation : new String[]{"prepare", "focus", "start", "pause"}) {
            Output out = new Output();
            out.throwsAt = operation;
            MusicPlayback p = new MusicPlayback(out);
            p.start();
            p.prepared();
            p.focus(MusicPlayback.Focus.TRANSIENT_LOSS);
            int afterFailure = out.events.size();
            p.prepared();
            p.focus(MusicPlayback.Focus.GAIN);
            p.stop();
            assertEquals(operation, afterFailure, out.events.size());
            assertEquals(operation, 1, java.util.Collections.frequency(out.events, "release"));
            assertEquals(operation, 1, java.util.Collections.frequency(out.events, "abandon"));
        }
    }
}
