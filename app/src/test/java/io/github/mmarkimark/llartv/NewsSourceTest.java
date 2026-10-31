package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The news source presets (issue #12): which feed, which credit, and who wins when both are set. */
public class NewsSourceTest {
    @Test public void automaticAloneIsTheCatalanPreset() {
        NewsSource s = NewsSource.preset(NewsSource.AUTOMATIC);
        assertEquals(NewsSource.URL_3CAT, s.url);
        assertEquals("3Cat", s.label);
        assertEquals(NewsSource.THREE_CAT, s.key);
    }

    @Test public void automaticFollowsTheInterfaceLanguage() {
        assertEquals(NewsSource.THREE_CAT, withLanguage(Language.CA).key);
        assertEquals(NewsSource.BBC_MUNDO, withLanguage(Language.ES).key);
        assertEquals(NewsSource.URL_BBC_MUNDO, withLanguage(Language.ES).url);
        assertEquals(NewsSource.BBC_WORLD, withLanguage(Language.EN).key);
        assertEquals(NewsSource.URL_BBC_WORLD, withLanguage(Language.EN).url);
        assertEquals(NewsSource.THREE_CAT, withLanguage("fr").key);   // an unknown language is Catalan
    }

    static NewsSource withLanguage(String lang) {
        Config k = new Config();
        k.set("language", lang);
        return NewsSource.resolve(k);
    }

    @Test public void anExplicitPresetIgnoresTheLanguage() {
        Config k = new Config();
        k.set("news_source", NewsSource.THREE_CAT);
        k.set("language", Language.EN);
        assertEquals(NewsSource.THREE_CAT, NewsSource.resolve(k).key);
        k.set("news_source", NewsSource.BBC_MUNDO);
        assertEquals(NewsSource.BBC_MUNDO, NewsSource.resolve(k).key);
    }

    @Test public void aCustomFeedWinsInEveryLanguage() {
        for (String lang : Language.OPTIONS) {
            Config k = new Config();
            k.set("news_rss", "https://example.org/rss");
            k.set("language", lang);
            NewsSource s = NewsSource.resolve(k);
            assertEquals(lang, "https://example.org/rss", s.url);
            assertEquals(lang, "custom:https://example.org/rss", s.key);
            assertEquals(lang, "example.org", s.credit);
        }
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

    @Test public void theMenuOptionsKeepTheirOrder() {
        // Ca.newsSources gives the labels in this order (CaTest pins them)
        assertEquals(NewsSource.AUTOMATIC, NewsSource.OPTIONS[0]);
        assertEquals(NewsSource.THREE_CAT, NewsSource.OPTIONS[1]);
        assertEquals(NewsSource.BBC_MUNDO, NewsSource.OPTIONS[2]);
        assertEquals(NewsSource.BBC_WORLD, NewsSource.OPTIONS[3]);
    }

    @Test public void theCreditsAreTheSameInEveryLanguage() {
        // the attribution is the provider's, so it is never translated
        for (String lang : Language.OPTIONS) {
            Config k = new Config();
            k.set("language", lang);
            k.set("news_source", NewsSource.BBC_WORLD);
            assertEquals("BBC News World · bbc.co.uk/news", NewsSource.resolve(k).credit);
            assertEquals("BBC News", NewsSource.resolve(k).label);
        }
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
