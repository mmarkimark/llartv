package io.github.mmarkimark.llartv;

import java.util.Locale;

/**
 * Where the headlines come from, and the credit that goes with them (issue #12).
 *
 * The stored choice is home.conf "news_source": "automatic", "3cat", "bbc-mundo" or "bbc-world".
 * "automatic" follows the interface language; the language selector is issue #11 (v1.2), so until then
 * it is Catalan. A custom "news_rss" always wins over the preset and is never overwritten by one.
 *
 * Every source carries the attribution the terms of use ask for: the BBC feeds need the text "BBC News"
 * and a hyperlink to bbc.co.uk/news near the headlines, and 3Cat needs no confusion about who wrote them.
 */
final class NewsSource {
    static final String AUTOMATIC = "automatic";
    static final String THREE_CAT = "3cat";
    static final String BBC_MUNDO = "bbc-mundo";
    static final String BBC_WORLD = "bbc-world";
    static final String CUSTOM = "custom";

    static final String URL_3CAT = "https://api.3cat.cat/noticies?_format=rss&origen=frontal&frontal=n324-portada-noticia&version=2.0";
    static final String URL_BBC_MUNDO = "https://feeds.bbci.co.uk/mundo/rss.xml";
    static final String URL_BBC_WORLD = "https://feeds.bbci.co.uk/news/world/rss.xml";

    /** The options the settings menu offers, in the order it shows them. */
    static final String[] OPTIONS = {AUTOMATIC, THREE_CAT, BBC_MUNDO, BBC_WORLD};

    final String id;       // the stored id, or "custom"
    final String url;      // the feed to read
    final String label;    // the attribution next to each headline
    final String credit;   // the credit line under the headlines: the source and its site
    final String key;      // the cache key: the id, or "custom:" + the URL

    NewsSource(String id, String url, String label, String credit, String key) {
        this.id = id;
        this.url = url;
        this.label = label;
        this.credit = credit;
        this.key = key;
    }

    /** The source in effect: the custom feed when there is one, the preset otherwise. */
    static NewsSource resolve(Config conf) {
        if (conf.newsRssCustom && conf.newsRss != null && !conf.newsRss.trim().isEmpty()) return custom(conf.newsRss.trim());
        return preset(conf.newsSource);
    }

    /** The preset for a stored id. An unknown or empty id keeps the default. */
    static NewsSource preset(String id) {
        String p = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (BBC_MUNDO.equals(p)) return new NewsSource(BBC_MUNDO, URL_BBC_MUNDO, "BBC News", "BBC News Mundo · bbc.co.uk/news", BBC_MUNDO);
        if (BBC_WORLD.equals(p)) return new NewsSource(BBC_WORLD, URL_BBC_WORLD, "BBC News", "BBC News World · bbc.co.uk/news", BBC_WORLD);
        return new NewsSource(THREE_CAT, URL_3CAT, "3Cat", "3Cat · 3cat.cat", THREE_CAT);
    }

    /** A feed the user set by hand; it is credited with its own host. */
    static NewsSource custom(String url) {
        String host = host(url);
        return new NewsSource(CUSTOM, url, host, host, CUSTOM + ":" + url);
    }

    static boolean isPreset(String id) {
        for (String o : OPTIONS) if (o.equals(id)) return true;
        return false;
    }

    /** The host of a URL, without the scheme, the credentials, the port and a leading "www.". */
    static String host(String url) {
        String s = url == null ? "" : url.trim();
        int scheme = s.indexOf("://");
        if (scheme >= 0) s = s.substring(scheme + 3);
        int end = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/' || c == '?' || c == '#') { end = i; break; }
        }
        s = s.substring(0, end);
        int at = s.indexOf('@');
        if (at >= 0) s = s.substring(at + 1);
        int colon = s.indexOf(':');
        if (colon >= 0) s = s.substring(0, colon);
        if (s.startsWith("www.")) s = s.substring(4);
        return s;
    }
}
