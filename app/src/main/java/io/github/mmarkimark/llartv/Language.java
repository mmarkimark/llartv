package io.github.mmarkimark.llartv;

import java.util.Locale;

/**
 * The interface language (home.conf "language", issue #11): Catalan, Spanish or English.
 *
 * Catalan is the default and the fallback: a new installation, an existing one upgraded without a language line,
 * an empty value and an unreadable one all show Catalan. The choice is LlarTV's alone: it never changes the TV's
 * system language, time zone or clock format, and it never touches another app.
 */
final class Language {
    private Language() {}

    static final String CA = "ca", ES = "es", EN = "en";
    static final String DEFAULT = CA;

    /** The options the settings menu offers, in the order it shows them. */
    static final String[] OPTIONS = {CA, ES, EN};
    /** Each language in its own name, so it can be found whatever the current one is (never translated). */
    static final String[] NAMES = {"Català", "Español", "English"};

    /** The canonical code of a stored value: trimmed and lowercased; empty when it is not one of the three. */
    static String parse(String v) {
        String p = v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
        for (String o : OPTIONS) if (o.equals(p)) return p;
        return "";
    }

    /** The language for a stored value, Catalan when it is not one of the three. */
    static String of(String v) {
        String p = parse(v);
        return p.isEmpty() ? DEFAULT : p;
    }

    /** The locale for the texts, dates and plurals of a language. */
    static Locale locale(String lang) {
        switch (of(lang)) {
            case ES: return new Locale("es", "ES");
            case EN: return new Locale("en", "GB");
            default: return new Locale("ca", "ES");
        }
    }

    /** The news preset "automatic" stands for in a language: 3Cat, BBC News Mundo or BBC News World. */
    static String newsPreset(String lang) {
        switch (of(lang)) {
            case ES: return NewsSource.BBC_MUNDO;
            case EN: return NewsSource.BBC_WORLD;
            default: return NewsSource.THREE_CAT;
        }
    }
}
