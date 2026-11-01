package io.github.mmarkimark.llartv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The quote of the day on Home (issue #69). The quotes are bundled, one file per interface language
 * (assets/quotes/ca.tsv, es.tsv, en.tsv; see docs/quotes.md), one quote per line:
 *   text<TAB>author<TAB>source       (author empty for a traditional saying; source = the page it was checked against)
 * The day's quote is line (day-of-year - 1), wrapping around when a file has fewer lines than the year has days:
 * the same quote all day, a new one at midnight. It follows the interface language; a language without a usable
 * file shows the Catalan one. No network is ever used.
 *
 * Everything but the Opener given to a Library is plain Java, so it is tested on the JVM.
 */
final class Quotes {
    private Quotes() {}

    static final class Quote {
        final String text, author, source;
        Quote(String text, String author, String source) { this.text = text; this.author = author; this.source = source; }
    }

    /** Opens a bundled file by its asset path (AssetManager::open on the TV). */
    interface Opener { InputStream open(String path) throws IOException; }

    /** The asset path of a language's quotes. */
    static String path(String lang) { return "quotes/" + Language.of(lang) + ".tsv"; }

    /** The quotes of a UTF-8 TSV stream; the stream is closed. A read error keeps the quotes read so far. */
    static List<Quote> parse(InputStream in) {
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) { return parse(r); }
        catch (IOException e) { return new ArrayList<>(); }
    }

    /** The quotes of TSV text: blank lines, lines without exactly three fields and lines without a text are skipped. */
    static List<Quote> parse(Reader r) {
        List<Quote> out = new ArrayList<>();
        BufferedReader b = r instanceof BufferedReader ? (BufferedReader) r : new BufferedReader(r);
        try {
            String line;
            boolean first = true;
            while ((line = b.readLine()) != null) {
                if (first && line.startsWith("\uFEFF")) line = line.substring(1);
                first = false;
                String[] f = line.split("\t", -1);
                if (f.length != 3) continue;
                String text = f[0].trim();
                if (text.isEmpty()) continue;
                out.add(new Quote(text, f[1].trim(), f[2].trim()));
            }
        } catch (IOException ignored) {}
        return out;
    }

    /** The quote of the day c falls on; null when there are none. */
    static Quote pick(List<Quote> quotes, Calendar c) {
        if (quotes == null || quotes.isEmpty()) return null;
        return quotes.get((c.get(Calendar.DAY_OF_YEAR) - 1) % quotes.size());
    }

    /** "— Author" (a no-break space after the dash, so it never ends a line), or empty for a saying. */
    static String author(Quote q) {
        return q == null || q.author.isEmpty() ? "" : "\u2014\u00A0" + q.author;
    }

    /** The text, then "— Author" on a line of its own when there is one; empty without a quote. */
    static String format(Quote q) {
        if (q == null) return "";
        String a = author(q);
        return a.isEmpty() ? q.text : q.text + "\n" + a;
    }

    /** The quotes of each language, each file read at most once; safe to use from any thread. */
    static final class Library {
        private final Opener opener;
        private final Map<String, List<Quote>> byLanguage = new ConcurrentHashMap<>();

        Library(Opener opener) { this.opener = opener; }

        /** The quotes of a language as read so far; null when it has not been read yet. */
        List<Quote> cached(String lang) { return byLanguage.get(Language.of(lang)); }

        /** The quotes of a language, read on first use (do it off the main thread); Catalan's when it has none. */
        List<Quote> get(String lang) {
            String l = Language.of(lang);
            List<Quote> q = byLanguage.get(l);
            if (q != null) return q;
            q = read(l);
            if (q.isEmpty() && !Language.DEFAULT.equals(l)) q = get(Language.DEFAULT);
            List<Quote> before = byLanguage.putIfAbsent(l, q);
            return before != null ? before : q;
        }

        private List<Quote> read(String lang) {
            try { return Collections.unmodifiableList(parse(opener.open(path(lang)))); }
            catch (IOException | RuntimeException e) { return Collections.emptyList(); }
        }
    }
}
