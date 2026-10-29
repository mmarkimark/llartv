package io.github.mmarkimark.llartv;

/**
 * When the headlines have to be fetched again.
 *
 * The rule has three parts, and they used to live inline in HomeActivity, where a change of source
 * could be blocked by the attempt of the previous one:
 *   - a source that was never tried is fetched straight away;
 *   - a source whose last attempt failed is tried again after the retry window;
 *   - a source that was fetched successfully is not fetched again until the news interval is over.
 *
 * It is kept here, free of Android, so the JVM tests can pin it.
 */
final class NewsSchedule {
    private String fetchedSource = "";        // the source of the last successful fetch
    private String attemptedSource = "";      // the source of the last attempt, successful or not
    private long fetchedAt;
    private long attemptedAt;

    /** True when the headlines of that source have to be fetched now. */
    boolean due(String key, long now, long every, long retry) {
        if (!key.equals(fetchedSource)) {
            // another source has its own headlines: fetch it now, unless it just failed
            return !(key.equals(attemptedSource) && now - attemptedAt < retry);
        }
        return now - fetchedAt >= every;
    }

    /** The fetch of that source is starting. */
    void attempt(String key, long now) {
        attemptedSource = key;
        attemptedAt = now;
    }

    /** The fetch of that source worked; it now counts as fetched. */
    void success(String key, long now) {
        fetchedSource = key;
        fetchedAt = now;
        attempt(key, now);
    }
}
