package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Content recovery when usable connectivity returns while Home is visible (#38). */
public class ReconnectTest {
    static final long GAP = Reconnect.MIN_GAP;

    @Test public void theFirstObservationOnlyRecordsTheState() {
        assertFalse(new Reconnect().regained(true, 0));    // Home just resumed: its own fetches already run
        assertFalse(new Reconnect().regained(false, 0));
    }

    @Test public void aNetworkThatBecomesUsableRecoversOnce() {
        Reconnect r = new Reconnect();
        r.regained(false, 0);
        assertTrue(r.regained(true, 1000));
        assertFalse(r.regained(true, 2000));               // repeated notifications: no new recovery
        assertFalse(r.regained(true, 2000 + GAP));
    }

    @Test public void aNetworkWithoutInternetNeverRecovers() {
        Reconnect r = new Reconnect();
        r.regained(false, 0);
        for (int i = 1; i < 50; i++) assertFalse(r.regained(false, i * 1000L));   // captive or unvalidated
    }

    @Test public void aFlappingNetworkRecoversAtMostOncePerGap() {
        Reconnect r = new Reconnect();
        r.regained(false, 0);
        assertTrue(r.regained(true, 1000));
        assertFalse(r.regained(false, 2000));
        assertFalse(r.regained(true, 3000));               // within the gap: no retry storm
        assertFalse(r.regained(false, 4000));
        assertTrue(r.regained(true, 1000 + GAP));           // a later return counts again
    }

    @Test public void leavingHomeForgetsTheState() {
        Reconnect r = new Reconnect();
        r.regained(false, 0);
        r.reset();                                          // onPause: the callback is unregistered
        assertFalse(r.regained(true, GAP * 3));             // onResume fetches by itself
    }

    @Test public void aFailedNewsSourceIsDueAgainAfterARecovery() {
        NewsSchedule s = new NewsSchedule();
        s.attempt("bbc-world", 1000);                       // failed offline
        assertFalse(s.due("bbc-world", 2000, 30 * 60_000L, 10 * 60_000L));
        s.retryFailed();
        assertTrue(s.due("bbc-world", 2000, 30 * 60_000L, 10 * 60_000L));
    }

    @Test public void aRecoveryDoesNotRefetchFreshNews() {
        NewsSchedule s = new NewsSchedule();
        s.success("3cat", 1000);
        s.retryFailed();
        assertFalse(s.due("3cat", 2000, 30 * 60_000L, 10 * 60_000L));
    }
}
