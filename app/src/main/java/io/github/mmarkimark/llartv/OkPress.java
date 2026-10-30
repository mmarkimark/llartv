package io.github.mmarkimark.llartv;

/**
 * One OK key held down, as a small state machine driven by the key events: a long press fires once, after the
 * platform long-press timeout, and then swallows the click; a short press reports the click when the key goes
 * up. The timeout is passed in (ViewConfiguration.getLongPressTimeout()), so this stays testable on the JVM.
 */
final class OkPress {
    static final int NONE = 0, CLICK = 1, HOLD = 2;

    private final long timeout;
    private long downAt = Long.MIN_VALUE;
    private boolean fired;

    OkPress(long timeoutMs) { timeout = timeoutMs; }

    /** The initial press of the key. */
    void down(long eventTime) {
        downAt = eventTime;
        fired = false;
    }

    /** A repeat of the held key: the hold fires once, when the timeout has passed. */
    int repeat(long eventTime) {
        if (downAt == Long.MIN_VALUE || fired || eventTime - downAt < timeout) return NONE;
        fired = true;
        return HOLD;
    }

    /** The key goes up: a click, the hold when it fired too late for its repeats, or nothing. */
    int up(long eventTime) {
        int r = downAt == Long.MIN_VALUE || fired ? NONE : eventTime - downAt >= timeout ? HOLD : CLICK;
        downAt = Long.MIN_VALUE;
        fired = false;
        return r;
    }

    /** Another key took over while OK was held: forget the press, so its release never clicks. */
    void cancel() {
        downAt = Long.MIN_VALUE;
        fired = false;
    }
}
