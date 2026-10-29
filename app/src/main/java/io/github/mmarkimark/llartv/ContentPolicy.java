package io.github.mmarkimark.llartv;

import java.util.Locale;

/**
 * How much of the outside content a surface shows: never, only in the morning band or all day.
 * It is the policy behind the "weather_home", "weather_screen" and "news_home" settings.
 *
 * A surface that is set to "never" neither shows that content nor asks for it, so a feature that is
 * off everywhere does no work at all.
 */
final class ContentPolicy {
    private ContentPolicy() {}

    static final int NEVER = 0, MORNING = 1, ALL = 2;

    /** The stored value of a policy: "never", "morning" or "all". */
    static int parse(String v, int fallback) {
        String p = v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
        if (p.isEmpty()) return fallback;          // an empty value keeps the current one (removing the key restores the default)
        if (p.equals("never") || p.equals("off")) return NEVER;
        if (p.equals("morning")) return MORNING;
        if (p.equals("all") || p.equals("all_day")) return ALL;
        return fallback;
    }

    /** The value written back to home.conf. */
    static String value(int policy) {
        return policy == NEVER ? "never" : policy == MORNING ? "morning" : "all";
    }

    /** True when the surface shows the content right now. */
    static boolean shows(int policy, boolean inMorningBand) {
        return policy == ALL || (policy == MORNING && inMorningBand);
    }

    /** True when the surface wants the content at all, so it is worth fetching. */
    static boolean wants(int policy) {
        return policy != NEVER;
    }
}
