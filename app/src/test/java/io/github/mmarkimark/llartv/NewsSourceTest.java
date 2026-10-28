package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The news source presets (issue #12): which feed, which credit, and who wins when both are set. */
public class NewsSourceTest {
    @Test public void automaticResolvesToCatalanUntilTheLanguageSelectorLands() {
        // "automatic" follows the interface language; the language selector is issue #11 (v1.2), so it is Catalan.
        NewsSource s = NewsSource.preset(NewsSource.AUTOMATIC);
        assertEquals(NewsSource.URL_3CAT, s.url);
        assertEquals("3Cat", s.label);
        assertEquals(NewsSource.THREE_CAT, s.key);
    }

    @Test public void eachPresetHasItsOwnFeed() {
        assertEquals(NewsSource.URL_3CAT, NewsSource.preset(NewsSource.THREE_CAT).url);
        assertEquals("https://feeds.bbci.co.uk/mundo/rss.xml", NewsSource.preset(NewsSource.BBC_MUNDO).url);
        assertEquals("https://feeds.bbci.co.uk/news/world/rss.xml", NewsSource.preset(NewsSource.BBC_WORLD).url);
    }

    @Test public void theBbcFeedsCarryTheRequiredCredit() {
        // The BBC terms ask for the text "BBC News" and a hyperlink to bbc.co.uk/news near the headlines.
        NewsSource mundo = NewsSource.preset(NewsSource.BBC_MUNDO);
        assertEquals("BBC News", mundo.label);
        assertTrue(mundo.credit.contains("BBC News Mundo"));
        assertTrue(mundo.credit.contains("bbc.co.uk/news"));
        NewsSource world = NewsSource.preset(NewsSource.BBC_WORLD);
        assertEquals("BBC News", world.label);
        assertTrue(world.credit.contains("BBC News World"));
        assertTrue(world.credit.contains("bbc.co.uk/news"));
    }

    @Test public void theCatalanFeedIsCreditedTo3Cat() {
        NewsSource s = NewsSource.preset(NewsSource.THREE_CAT);
        assertEquals("3Cat", s.label);
        assertTrue(s.credit.contains("3Cat"));
        assertTrue(s.credit.contains("3cat.cat"));
    }

    @Test public void anUnknownIdKeepsTheDefault() {
        assertEquals(NewsSource.URL_3CAT, NewsSource.preset("nope").url);
        assertEquals(NewsSource.URL_3CAT, NewsSource.preset("").url);
        assertEquals(NewsSource.URL_3CAT, NewsSource.preset(null).url);
        assertFalse(NewsSource.isPreset("nope"));
        assertTrue(NewsSource.isPreset(NewsSource.BBC_WORLD));
    }

    @Test public void aCustomFeedWinsOverThePreset() {
        Config k = new Config();
        k.set("news_source", NewsSource.BBC_MUNDO);
        k.set("news_rss", "https://example.org/rss");
        NewsSource s = NewsSource.resolve(k);
        assertEquals("https://example.org/rss", s.url);
        assertEquals(NewsSource.CUSTOM, s.id);
        assertEquals("example.org", s.label);
        assertEquals("custom:https://example.org/rss", s.key);
    }

    @Test public void withoutACustomFeedThePresetDecides() {
        Config k = new Config();
        k.set("news_source", NewsSource.BBC_WORLD);
        NewsSource s = NewsSource.resolve(k);
        assertEquals(NewsSource.URL_BBC_WORLD, s.url);
        assertEquals(NewsSource.BBC_WORLD, s.key);
    }

    @Test public void anEmptyCustomFeedIsNotACustomFeed() {
        Config k = new Config();
        k.set("news_rss", "   ");
        assertEquals(NewsSource.URL_3CAT, NewsSource.resolve(k).url);
    }

    @Test public void theMenuLabelsMatchThePresetOrder() {
        assertEquals(NewsSource.OPTIONS.length, Ca.NEWS_SOURCE_LABELS.length);
        assertEquals(NewsSource.AUTOMATIC, NewsSource.OPTIONS[0]);
        assertEquals(NewsSource.THREE_CAT, NewsSource.OPTIONS[1]);
        assertEquals(NewsSource.BBC_MUNDO, NewsSource.OPTIONS[2]);
        assertEquals(NewsSource.BBC_WORLD, NewsSource.OPTIONS[3]);
        assertEquals(Ca.NEWS_SOURCE_AUTO, Ca.NEWS_SOURCE_LABELS[0]);
        assertEquals(Ca.NEWS_SOURCE_3CAT, Ca.NEWS_SOURCE_LABELS[1]);
        assertEquals(Ca.NEWS_SOURCE_MUNDO, Ca.NEWS_SOURCE_LABELS[2]);
        assertEquals(Ca.NEWS_SOURCE_WORLD, Ca.NEWS_SOURCE_LABELS[3]);
    }

    @Test public void theCustomKeyIsWrittenOnOneLine() {
        NewsSource s = NewsSource.custom("https://example.org/a\tb");
        assertFalse(s.key.contains("\t"));
        assertEquals("custom:https://example.org/a b", s.key);
    }

    @Test public void theHostIsLowerCasedForTheWwwPrefix() {
        assertEquals("example.org", NewsSource.host("https://WWW.example.org/rss"));
    }

    @Test public void theHostComesFromTheUrl() {
        assertEquals("example.org", NewsSource.host("https://example.org/rss"));
        assertEquals("example.org", NewsSource.host("https://www.example.org/rss"));
        assertEquals("feeds.bbci.co.uk", NewsSource.host("https://feeds.bbci.co.uk/mundo/rss.xml"));
        assertEquals("example.org", NewsSource.host("https://example.org"));
        assertEquals("example.org", NewsSource.host("example.org/rss"));
    }
}
