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
        public void nextTrack() { event("next"); }
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

    @Test public void resolvedLocalSourceIsPreparedOnceWithinItsSession() {
        Output out = new Output();
        MusicPlayback p = new MusicPlayback(out);
        p.start();
        p.sourceReady(() -> out.event("local"));
        p.sourceReady(() -> out.event("duplicate"));
        p.prepared();
        p.stop();
        p.sourceReady(() -> out.event("late"));
        out.expect("prepare", "local", "focus", "start", "release", "abandon");
    }

    @Test public void downloadFinishingAfterExitCannotCreateAPlayer() {
        Output out = new Output();
        MusicPlayback p = new MusicPlayback(out);
        p.start();
        p.stop();
        p.sourceReady(() -> out.event("late player"));
        p.prepared();
        out.expect("prepare", "release", "abandon");
    }

    @Test public void localPreparationFailureIsSilentAndTerminal() {
        Output out = new Output();
        MusicPlayback p = new MusicPlayback(out);
        p.start();
        p.sourceReady(() -> { throw new IllegalStateException("file unavailable"); });
        p.sourceReady(() -> out.event("retry"));
        p.prepared();
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

    @Test public void playlistRotatesAtTwoMinutesAndPreparesDistinctSourcesWithoutRetakingFocus() {
        assertEquals(120_000L, MusicPlayback.ROTATE_MS);
        Output out = new Output();
        MusicPlayback p = new MusicPlayback(out, true);
        p.start(); p.sourceReady(() -> out.event("first")); p.prepared();
        p.rotate(); p.rotate(); // no second advance while the next source is loading
        p.sourceReady(() -> out.event("second")); p.prepared();
        p.stop();
        out.expect("prepare", "first", "focus", "start", "next", "second", "start", "release", "abandon");
    }

    @Test public void shortLocalTrackAdvancesAtCompletionButCustomStreamStillEnds() {
        Output out = new Output();
        MusicPlayback p = new MusicPlayback(out, true);
        p.start(); p.prepared(); p.completed();
        p.sourceReady(() -> out.event("second")); p.prepared(); p.stop();
        out.expect("prepare", "focus", "start", "next", "second", "start", "release", "abandon");
        Output custom = new Output(); MusicPlayback stream = new MusicPlayback(custom);
        stream.start(); stream.prepared(); stream.rotate(); stream.completed();
        custom.expect("prepare", "focus", "start", "release", "abandon");
    }

    @Test public void playlistDoesNotRotateWhilePausedOrRestartDuringFocusLossBetweenTracks() {
        Output out = new Output(); MusicPlayback p = new MusicPlayback(out, true);
        p.start(); p.prepared(); p.focus(MusicPlayback.Focus.TRANSIENT_LOSS); p.rotate();
        out.expect("prepare", "focus", "start", "pause");
        p.focus(MusicPlayback.Focus.GAIN); p.rotate();
        p.focus(MusicPlayback.Focus.TRANSIENT_LOSS); // next track is still downloading
        p.sourceReady(() -> out.event("second")); p.prepared();
        out.expect("prepare", "focus", "start", "pause", "start", "next", "second");
        p.focus(MusicPlayback.Focus.GAIN); p.stop();
        out.expect("prepare", "focus", "start", "pause", "start", "next", "second", "start", "release", "abandon");
    }

    @Test public void permanentLossOrExitDuringRotationInvalidatesLateSourceTimerAndPlayerCallbacks() {
        for (boolean loss : new boolean[]{true, false}) {
            Output out = new Output(); MusicPlayback p = new MusicPlayback(out, true);
            p.start(); p.prepared(); p.rotate();
            if (loss) p.focus(MusicPlayback.Focus.LOSS); else p.stop();
            p.sourceReady(() -> out.event("late")); p.prepared(); p.rotate(); p.completed();
            p.focus(MusicPlayback.Focus.GAIN); p.stop();
            out.expect("prepare", "focus", "start", "next", "release", "abandon");
        }
    }

    @Test public void failedNextTrackIsTerminalWithoutAnotherDownloadOrFocusRequest() {
        Output out = new Output(); MusicPlayback p = new MusicPlayback(out, true);
        p.start(); p.prepared(); p.rotate(); p.failed(); p.prepared(); p.rotate();
        p.sourceReady(() -> out.event("retry")); p.focus(MusicPlayback.Focus.GAIN);
        out.expect("prepare", "focus", "start", "next", "release", "abandon");
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
