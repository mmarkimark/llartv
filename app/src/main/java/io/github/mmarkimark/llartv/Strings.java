package io.github.mmarkimark.llartv;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

/**
 * The texts of one interface language: res/values (Catalan, also the fallback), values-es and values-en.
 *
 * A snapshot is immutable: it is built once for a language, from resources of its own configuration, and a
 * language change builds a new one. So a screen never mixes two languages, and a read from any thread sees one
 * whole language. The TV's own configuration, and with it the system language, is never changed.
 */
final class Strings {
    /** Where the texts come from: the app's resources on the TV, the resource files in the JVM tests. */
    interface Source {
        String text(int id);
        /** The form of a plural for a count, not yet formatted. */
        String quantity(int id, int count);
    }

    final String language;
    final Locale locale;
    private final Source source;

    Strings(String language, Source source) {
        this.language = Language.of(language);
        this.locale = Language.locale(this.language);
        this.source = source;
    }

    /** The texts of a language, from resources configured for its locale (the context's own are left alone). */
    static Strings of(Context c, String language) {
        String lang = Language.of(language);
        Configuration conf = new Configuration(c.getResources().getConfiguration());
        conf.setLocale(Language.locale(lang));
        final Resources res = c.createConfigurationContext(conf).getResources();
        return new Strings(lang, new Source() {
            @Override public String text(int id) { return res.getString(id); }
            @Override public String quantity(int id, int count) { return res.getQuantityString(id, count); }
        });
    }

    String get(int id) { return source.text(id); }

    /** A text with its placeholders filled, numbers written the way the language writes them. */
    String get(int id, Object... args) { return String.format(locale, source.text(id), args); }

    /** The plural form for a count, with the count filled in. */
    String plural(int id, int count) { return String.format(locale, source.quantity(id, count), count); }
}
