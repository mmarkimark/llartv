package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** When the headlines have to be fetched again (the rule that a source switch is never blocked). */
public class NewsScheduleTest {
    static final long MIN = 60_000L, EVERY = 30 * MIN, RETRY = 10 * MIN;

    @Test public void aSourceNeverTriedIsFetchedAtOnce() {
        NewsSchedule s = new NewsSchedule();
        assertTrue(s.due("3cat", 1000, EVERY, RETRY));
    }

    @Test public void aFetchedSourceWaitsForTheNewsInterval() {
        NewsSchedule s = new NewsSchedule();
        s.attempt("3cat", 0);
        s.success("3cat", 0);
        assertFalse(s.due("3cat", EVERY - 1, EVERY, RETRY));
        assertTrue(s.due("3cat", EVERY, EVERY, RETRY));
    }

    @Test public void anotherSourceIsNotBlockedByThePreviousOne() {
        NewsSchedule s = new NewsSchedule();
        s.attempt("3cat", 1000);
        s.success("3cat", 1000);
        assertTrue(s.due("bbc-mundo", 1001, EVERY, RETRY));
    }

    @Test public void aFailedSourceWaitsForTheRetryWindow() {
        NewsSchedule s = new NewsSchedule();
        s.attempt("bbc-mundo", 1000);   // the attempt failed: no success() call
        assertFalse(s.due("bbc-mundo", 1000 + RETRY - 1, EVERY, RETRY));
        assertTrue(s.due("bbc-mundo", 1000 + RETRY, EVERY, RETRY));
    }

    @Test public void aFailedSourceDoesNotBlockTheNextOne() {
        NewsSchedule s = new NewsSchedule();
        s.attempt("bbc-mundo", 1000);
        assertTrue(s.due("3cat", 1001, EVERY, RETRY));
    }

    @Test public void aFailedSourceIsNotCountedAsFetched() {
        NewsSchedule s = new NewsSchedule();
        s.attempt("bbc-mundo", 0);
        assertTrue(s.due("bbc-mundo", EVERY, EVERY, RETRY));   // the retry window is over
    }
}
