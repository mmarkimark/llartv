package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The quote of the day (issue #69): one bundled TSV file per interface language, one quote per line
 * (text, author, source), the line of the day-of-year, Catalan when a language has no usable file.
 */
public class QuotesTest {
    static List<Quotes.Quote> parse(String tsv) { return Quotes.parse(new StringReader(tsv)); }

    /** n numbered sayings, "q1" to "qn". */
    static List<Quotes.Quote> numbered(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 1; i <= n; i++) b.append("q").append(i).append("\t\thttps://example.org/").append(i).append('\n');
        return parse(b.toString());
    }

    static Calendar day(int year, int month, int dayOfMonth) {
        return new GregorianCalendar(year, month, dayOfMonth, 12, 0);
    }

    // ---------------------------------------------------------------- parsing

    @Test public void eachLineIsTextAuthorAndSource() {
        List<Quotes.Quote> q = parse("Qui no s'arrisca no pisca.\t\thttps://ca.wikiquote.org/wiki/Proverbis\n"
                + "Know thyself.\tSocrates\thttps://en.wikiquote.org/wiki/Socrates\n");
        assertEquals(2, q.size());
        assertEquals("Qui no s'arrisca no pisca.", q.get(0).text);
        assertEquals("", q.get(0).author);
        assertEquals("https://ca.wikiquote.org/wiki/Proverbis", q.get(0).source);
        assertEquals("Know thyself.", q.get(1).text);
        assertEquals("Socrates", q.get(1).author);
        assertEquals("https://en.wikiquote.org/wiki/Socrates", q.get(1).source);
    }

    @Test public void blankAndMalformedLinesAreSkipped() {
        List<Quotes.Quote> q = parse("\uFEFFfirst\tA\ts1\r\n"   // a byte-order mark and Windows line ends
                + "\n"
                + "   \n"
                + "only text\n"
                + "two\tfields\n"
                + "five\tfields\tare\ttoo\tmany\n"
                + "\tno text\ts\n"
                + "  \t  \t  \n"
                + "second\t\ts2\n");
        assertEquals(2, q.size());
        assertEquals("first", q.get(0).text);
        assertEquals("A", q.get(0).author);
        assertEquals("s1", q.get(0).source);
        assertEquals("second", q.get(1).text);
    }

    @Test public void fieldsAreTrimmed() {
        Quotes.Quote q = parse("  Text with spaces.  \t  Author  \t  src  \n").get(0);
        assertEquals("Text with spaces.", q.text);
        assertEquals("Author", q.author);
        assertEquals("src", q.source);
    }

    @Test public void anEmptyStreamHasNoQuotes() {
        assertTrue(parse("").isEmpty());
        assertNull(Quotes.pick(parse(""), day(2026, Calendar.NOVEMBER, 1)));
        assertNull(Quotes.pick(null, day(2026, Calendar.NOVEMBER, 1)));
    }

    // ---------------------------------------------------------------- the quote of a day

    @Test public void consecutiveDaysShowConsecutiveLines() {
        List<Quotes.Quote> q = numbered(1000);
        int i = q.indexOf(Quotes.pick(q, day(2026, Calendar.NOVEMBER, 1)));
        assertSame(q.get((i + 1) % 1000), Quotes.pick(q, day(2026, Calendar.NOVEMBER, 2)));
        assertSame(q.get((i + 30) % 1000), Quotes.pick(q, day(2026, Calendar.DECEMBER, 1)));
    }

    @Test public void theNewYearContinuesInsteadOfStartingAgain() {
        // with 1,000 quotes a year boundary must not jump back to the first line (about three years without repeats)
        List<Quotes.Quote> q = numbered(1000);
        int dec31 = q.indexOf(Quotes.pick(q, day(2026, Calendar.DECEMBER, 31)));
        assertSame(q.get((dec31 + 1) % 1000), Quotes.pick(q, day(2027, Calendar.JANUARY, 1)));
        int feb28 = q.indexOf(Quotes.pick(q, day(2028, Calendar.FEBRUARY, 28)));
        assertSame(q.get((feb28 + 1) % 1000), Quotes.pick(q, day(2028, Calendar.FEBRUARY, 29)));
    }

    @Test public void aThousandQuotesLastAThousandDaysWithoutARepeat() {
        List<Quotes.Quote> q = numbered(1000);
        java.util.Set<Quotes.Quote> seen = new java.util.HashSet<>();
        Calendar c = day(2026, Calendar.NOVEMBER, 1);
        for (int d = 0; d < 1000; d++) { assertTrue(seen.add(Quotes.pick(q, c))); c.add(Calendar.DAY_OF_MONTH, 1); }
        assertSame(Quotes.pick(q, day(2026, Calendar.NOVEMBER, 1)), Quotes.pick(q, c));   // day 1,001 starts over
    }

    @Test public void theSameQuoteAllDayAndANewOneAfterMidnight() {
        List<Quotes.Quote> q = numbered(366);
        Calendar morning = new GregorianCalendar(2026, Calendar.NOVEMBER, 1, 0, 0, 0);
        Calendar night = new GregorianCalendar(2026, Calendar.NOVEMBER, 1, 23, 59, 59);
        Calendar next = new GregorianCalendar(2026, Calendar.NOVEMBER, 2, 0, 0, 0);
        assertSame(Quotes.pick(q, morning), Quotes.pick(q, night));
        assertFalse(Quotes.pick(q, night) == Quotes.pick(q, next));
    }

    @Test public void fewerLinesThanDaysWrapAround() {
        List<Quotes.Quote> q = numbered(3);
        Calendar c = day(2026, Calendar.JANUARY, 1);
        Quotes.Quote first = Quotes.pick(q, c);
        c.add(Calendar.DAY_OF_MONTH, 3);
        assertSame(first, Quotes.pick(q, c));
        assertEquals("q1", Quotes.pick(numbered(1), day(2026, Calendar.JULY, 15)).text);
    }

    @Test public void anOptionalFourthFieldNamesTheTranslator() {
        Quotes.Quote q = parse("Text\tSeneca\tsrc\tLlarTV\n").get(0);
        assertEquals("Seneca", q.author);
        assertEquals("LlarTV", q.translator);
        assertEquals("", parse("Text\tSeneca\tsrc\n").get(0).translator);
        assertTrue(parse("a\tb\tc\td\te\n").isEmpty());   // more than four fields is malformed
        assertEquals("Text\n\u2014\u00A0Seneca", Quotes.format(q));   // the translator is only in docs/quotes.md
    }

    // ---------------------------------------------------------------- display

    @Test public void theAuthorFollowsTheTextAfterADash() {
        Quotes.Quote q = parse("Know thyself.\tSocrates\ts\n").get(0);
        assertEquals("\u2014\u00A0Socrates", Quotes.author(q));
        assertEquals("Know thyself.\n\u2014\u00A0Socrates", Quotes.format(q));
    }

    @Test public void aSayingHasNoAuthorLine() {
        Quotes.Quote q = parse("Qui no s'arrisca no pisca.\t\ts\n").get(0);
        assertEquals("", Quotes.author(q));
        assertEquals("Qui no s'arrisca no pisca.", Quotes.format(q));
        assertEquals("", Quotes.format(null));
    }

    // ---------------------------------------------------------------- loading by language

    /** Assets in memory: path -> TSV text; a missing path throws like AssetManager.open. */
    static final class FakeAssets implements Quotes.Opener {
        final Map<String, String> files = new HashMap<>();
        final List<String> opened = new ArrayList<>();
        @Override public InputStream open(String path) throws java.io.IOException {
            opened.add(path);
            String s = files.get(path);
            if (s == null) throw new FileNotFoundException(path);
            return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test public void theFileOfTheInterfaceLanguageIsUsed() {
        FakeAssets a = new FakeAssets();
        a.files.put("quotes/ca.tsv", "Català.\t\ts\n");
        a.files.put("quotes/es.tsv", "Castellano.\t\ts\n");
        a.files.put("quotes/en.tsv", "English.\t\ts\n");
        Quotes.Library lib = new Quotes.Library(a);
        assertEquals("Català.", lib.get(Language.CA).get(0).text);
        assertEquals("Castellano.", lib.get(Language.ES).get(0).text);
        assertEquals("English.", lib.get(Language.EN).get(0).text);
        assertEquals("Català.", lib.get("xx").get(0).text);   // an unknown language is Catalan, as everywhere
    }

    @Test public void aMissingOrEmptyFileFallsBackToCatalan() {
        FakeAssets a = new FakeAssets();
        a.files.put("quotes/ca.tsv", "Català.\t\ts\n");
        a.files.put("quotes/es.tsv", "\n\nmalformed\n");
        Quotes.Library lib = new Quotes.Library(a);
        assertEquals("Català.", lib.get(Language.ES).get(0).text);
        assertEquals("Català.", lib.get(Language.EN).get(0).text);
    }

    @Test public void noFileAtAllMeansNoQuote() {
        Quotes.Library lib = new Quotes.Library(new FakeAssets());
        assertTrue(lib.get(Language.EN).isEmpty());
        assertNull(Quotes.pick(lib.get(Language.EN), day(2026, Calendar.NOVEMBER, 1)));
    }

    @Test public void eachLanguageIsReadOnce() {
        FakeAssets a = new FakeAssets();
        a.files.put("quotes/ca.tsv", "Català.\t\ts\n");
        a.files.put("quotes/en.tsv", "English.\t\ts\n");
        Quotes.Library lib = new Quotes.Library(a);
        assertNull(lib.cached(Language.EN));   // nothing is read before it is asked for
        List<Quotes.Quote> first = lib.get(Language.EN);
        assertSame(first, lib.get(Language.EN));
        assertSame(first, lib.cached(Language.EN));
        assertEquals(1, a.opened.size());
    }

    // ---------------------------------------------------------------- the bundled corpora, once they exist

    @Test public void theBundledFilesHaveAThousandQuotesWithTheirSource() throws Exception {
        File dir = assetsDir();
        for (String lang : Language.OPTIONS) {
            File f = new File(dir, "quotes/" + lang + ".tsv");
            assumeTrue("not bundled yet: " + f, f.isFile());
            List<Quotes.Quote> q;
            try (InputStream in = new FileInputStream(f)) { q = Quotes.parse(in); }
            assertEquals(lang, 1000, q.size());   // one thousand quotes per language (S2)
            int sayings = 0;
            for (Quotes.Quote x : q) {
                assertFalse(lang + ": " + x.text, x.source.isEmpty());
                assertTrue(lang + ": " + x.text, x.source.startsWith("https://"));
                assertNotNull(x.author);
                if (x.author.isEmpty()) sayings++;
                assertTrue(lang + ": a translator needs an author: " + x.text, x.translator.isEmpty() || !x.author.isEmpty());
                assertTrue(lang + ": " + x.text, x.text.length() <= 160);
            }
            assertTrue(lang + ": only " + sayings + " of " + q.size() + " sayings", sayings * 4 >= q.size() && sayings * 4 <= q.size() * 3);
            assertTrue(lang + ": more than ten lines for one author", countAuthors(q) <= 10);
        }
    }

    static int countAuthors(List<Quotes.Quote> q) {
        java.util.Map<String, Integer> n = new java.util.HashMap<>();
        for (Quotes.Quote x : q) if (!x.author.isEmpty()) n.merge(x.author, 1, Integer::sum);
        int max = 0;
        for (int c : n.values()) max = Math.max(max, c);
        return max;
    }

    static File assetsDir() {
        File d = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while (d != null && !new File(d, "app/src/main/assets").isDirectory()) d = d.getParentFile();
        return d == null ? new File("app/src/main/assets") : new File(d, "app/src/main/assets");
    }
}
