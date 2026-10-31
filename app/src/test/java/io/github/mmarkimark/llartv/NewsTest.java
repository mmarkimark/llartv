package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NewsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    static final long MIN = 60_000L, HOUR = 60 * MIN, DAY = 24 * HOUR;

    // 3Cat-shaped feed; encoded below as ISO-8859-1, as the real feed is.
    static final String FEED = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
            + "<rss version=\"2.0\"><channel><title>3Cat Notícies</title><link>https://www.3cat.cat</link>\n"
            + "<item>\n  <title><![CDATA[Notícies: el Govern aprova <b>els pressupostos</b>]]></title>\n"
            + "  <link>https://www.3cat.cat/a</link>\n  <pubDate>Sun, 25 Oct 2026 07:30:00 GMT</pubDate>\n</item>\n"
            + "<item><title>Barça &amp; Espanyol: &quot;derbi&quot; a l&#39;estadi &#x2014; crònica</title>"
            + "<pubDate>Sat, 24 Oct 2026 21:05:00 +0200</pubDate></item>\n"
            + "<item><title>  Tercera notícia  </title><pubDate>not a date</pubDate></item>\n"
            + "</channel></rss>";

    @Test public void parsesIso88591FeedWithCdataAndEntities() {
        List<News.Item> items = News.parse(FEED.getBytes(StandardCharsets.ISO_8859_1));
        assertEquals(3, items.size());
        assertEquals("Notícies: el Govern aprova <b>els pressupostos</b>", items.get(0).title);
        assertEquals(java.time.Instant.parse("2026-10-25T07:30:00Z").toEpochMilli(), items.get(0).published);
        assertEquals("Barça & Espanyol: \"derbi\" a l'estadi — crònica", items.get(1).title);
        assertEquals(java.time.Instant.parse("2026-10-24T19:05:00Z").toEpochMilli(), items.get(1).published);
        assertEquals("Tercera notícia", items.get(2).title);
        assertEquals(0L, items.get(2).published);
    }

    @Test public void parsesTheArticleLink() {
        List<News.Item> items = News.parse(FEED.getBytes(StandardCharsets.ISO_8859_1));
        assertEquals("https://www.3cat.cat/a", items.get(0).link);
        assertEquals("", items.get(1).link);
        assertEquals("", items.get(2).link);
    }

    @Test public void anItemWithOnlyAGuidKeepsTheLink() {
        String feed = "<rss><channel><item><title>Notícia</title>"
                + "<guid isPermaLink=\"true\">https://example.org/a</guid></item></channel></rss>";
        List<News.Item> items = News.parse(feed.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, items.size());
        assertEquals("https://example.org/a", items.get(0).link);
    }

    @Test public void aGuidThatIsNotAUrlIsNotALink() {
        String feed = "<rss><channel><item><title>Notícia</title><guid>item-123</guid></item></channel></rss>";
        List<News.Item> items = News.parse(feed.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, items.size());
        assertEquals("", items.get(0).link);
    }

    @Test public void utf8FeedWithoutPrologDecodesAsUtf8() {
        String feed = "<rss><channel><item><title>Notícies d'avui</title></item></channel></rss>";
        List<News.Item> items = News.parse(feed.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, items.size());
        assertEquals("Notícies d'avui", items.get(0).title);
    }

    @Test public void garbageGivesEmptyList() {
        assertEquals(0, News.parse("not xml at all".getBytes(StandardCharsets.UTF_8)).size());
        assertEquals(0, News.parse(new byte[0]).size());
    }

    static final Strings CA = LanguageTest.strings(Language.CA);

    @Test public void ageBoundariesInSpanishAndEnglish() {
        Strings es = LanguageTest.strings(Language.ES), en = LanguageTest.strings(Language.EN);
        long now = 1_000L * DAY;
        assertEquals("ahora mismo", News.age(es, now - 59_000, now));
        assertEquals("hace 5 min", News.age(es, now - 5 * MIN, now));
        assertEquals("hace 3 h", News.age(es, now - 3 * HOUR, now));
        assertEquals("ayer", News.age(es, now - DAY, now));
        assertEquals("hace 2 días", News.age(es, now - 2 * DAY, now));
        assertEquals("just now", News.age(en, now - 59_000, now));
        assertEquals("5 min ago", News.age(en, now - 5 * MIN, now));
        assertEquals("3 h ago", News.age(en, now - 3 * HOUR, now));
        assertEquals("yesterday", News.age(en, now - DAY, now));
        assertEquals("2 days ago", News.age(en, now - 2 * DAY, now));
        assertEquals("", News.age(en, 0, now));
    }

    @Test public void ageBoundariesInCatalan() {
        long now = 1_000L * DAY;
        assertEquals("ara mateix", News.age(CA, now - 59_000, now));
        assertEquals("ara mateix", News.age(CA, now + 5 * MIN, now));   // clock skew: never "fa -5 min"
        assertEquals("fa 1 min", News.age(CA, now - MIN, now));
        assertEquals("fa 5 min", News.age(CA, now - 5 * MIN, now));
        assertEquals("fa 59 min", News.age(CA, now - HOUR + 1, now));
        assertEquals("fa 1 h", News.age(CA, now - HOUR, now));
        assertEquals("fa 3 h", News.age(CA, now - 3 * HOUR - 20 * MIN, now));
        assertEquals("fa 23 h", News.age(CA, now - DAY + 1, now));
        assertEquals("ahir", News.age(CA, now - DAY, now));
        assertEquals("ahir", News.age(CA, now - 2 * DAY + 1, now));
        assertEquals("fa 2 dies", News.age(CA, now - 2 * DAY, now));
        assertEquals("", News.age(CA, 0, now));
    }

    @Test public void cacheRoundTrips() throws Exception {
        File dir = tmp.newFolder();
        NewsSource source = NewsSource.preset(NewsSource.THREE_CAT);
        List<News.Item> items = new ArrayList<>(News.parse(FEED.getBytes(StandardCharsets.ISO_8859_1)));
        News.save(dir, source, items);
        List<News.Item> back = News.load(dir, source);
        assertEquals(3, back.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(items.get(i).title, back.get(i).title);
            assertEquals(items.get(i).published, back.get(i).published);
            assertEquals(items.get(i).link, back.get(i).link);
        }
    }

    @Test public void theCacheOfAnotherSourceIsNotReused() throws Exception {
        File dir = tmp.newFolder();
        NewsSource cat = NewsSource.preset(NewsSource.THREE_CAT);
        NewsSource mundo = NewsSource.preset(NewsSource.BBC_MUNDO);
        News.save(dir, cat, News.parse(FEED.getBytes(StandardCharsets.ISO_8859_1)));
        assertNull(News.load(dir, mundo));   // the headlines of another source are not shown under this one
        assertEquals(3, News.load(dir, cat).size());
    }

    @Test public void anOldCacheWithoutTheHeaderIsNotReused() throws Exception {
        File dir = tmp.newFolder();
        try (FileWriter w = new FileWriter(new File(dir, "news.cache"))) {
            w.write("1761364200000\tNotícia antiga\n");
        }
        assertNull(News.load(dir, NewsSource.preset(NewsSource.THREE_CAT)));
    }

    @Test public void missingOrCorruptCacheIsNull() throws Exception {
        File dir = tmp.newFolder();
        NewsSource source = NewsSource.preset(NewsSource.THREE_CAT);
        assertNull(News.load(dir, source));
        try (FileWriter w = new FileWriter(new File(dir, "news.cache"))) { w.write("no tab here"); }
        assertNull(News.load(dir, source));
    }

    // ---------------------------------------------------------------- bounded feed size (Sentinel pass)

    @Test public void aFeedUpToTheLimitIsReadWhole() throws Exception {
        byte[] body = new byte[40_000];
        body[39_999] = 7;
        assertArrayEquals(body, News.readCapped(new ByteArrayInputStream(body), 40_000));
    }

    @Test public void aFeedOverTheLimitIsRefusedInsteadOfFillingTheMemory() {
        try {
            News.readCapped(new ByteArrayInputStream(new byte[40_001]), 40_000);
            fail("an oversized feed must be refused");
        } catch (IOException expected) { /* the fetch fails like any other network error */ }
    }

    @Test public void theFeedLimitLeavesRoomForRealFeeds() {
        assertEquals(2 * 1024 * 1024, News.MAX_FEED_BYTES);   // real feeds are tens of KB
    }
}
