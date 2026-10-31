package io.github.mmarkimark.llartv;

/**
 * When usable connectivity comes back while Home is visible (#38), free of Android so the JVM tests can pin it.
 *
 * "Usable" is Android's validated Internet capability, so joining a Wi-Fi network (or a captive portal) alone
 * never counts. Only a change from not usable to usable recovers, at most once per MIN_GAP, so repeated or
 * flapping notifications cannot start a retry storm. The first observation after Home resumes only records the
 * state: onResume already runs every due fetch. The recovery itself only makes the content whose last attempt
 * failed due again; the existing fetch rules (enabled surfaces, busy flags, intervals) still decide.
 */
final class Reconnect {
    static final long MIN_GAP = 60_000L;

    private Boolean usable;                 // null: nothing observed since Home resumed
    private long lastRecovery = Long.MIN_VALUE / 2;

    /** Records the network state; true when the content that failed should be retried now. */
    boolean regained(boolean nowUsable, long now) {
        boolean was = usable != null && !usable;
        usable = nowUsable;
        if (!was || !nowUsable || now - lastRecovery < MIN_GAP) return false;
        lastRecovery = now;
        return true;
    }

    /** Home left: forget the state (the network callback is unregistered until the next resume). */
    void reset() { usable = null; }
}
