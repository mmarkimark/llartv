package io.github.mmarkimark.llartv;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Headlines from an RSS 2.0 feed (home.conf "news_source" or a custom "news_rss") for the morning panel.
 * Parsed with plain string matching rather than an XML parser so the same code runs on the TV and in JVM tests;
 * the bytes are decoded with the charset the XML prolog declares (3Cat's feed is ISO-8859-1).
 *
 * The cache keeps the article link of every headline, and the source it belongs to: the headlines of one
 * source are never shown under another one, and a cache from an older version is not reused at all.
 */
final class News {
    private News() {}

    static final String CACHE = "news.cache";
    static final String CACHE_HEADER = "#llartv-news-v2";   // the format version, then the source key

    static final class Item {
        final String title;
        final long published;   // epoch ms; 0 when the feed gave no readable pubDate
        final String link;      // the article URL; empty when the feed gave none
        Item(String title, long published) { this(title, published, ""); }
        Item(String title, long published, String link) {
            this.title = title;
            this.published = published;
            this.link = link == null ? "" : link;
        }
    }

    private static final Pattern ENCODING = Pattern.compile("^\\s*<\\?xml[^>]*encoding\\s*=\\s*[\"']([A-Za-z0-9._:-]+)[\"']");
    private static final Pattern ITEM = Pattern.compile("<item\\b[^>]*>(.*?)</item\\s*>", Pattern.DOTALL);
    private static final Pattern CDATA = Pattern.compile("<!\\[CDATA\\[(.*?)]]>", Pattern.DOTALL);
    private static final Pattern ENTITY = Pattern.compile("&(#[0-9]+|#[xX][0-9a-fA-F]+|amp|lt|gt|quot|apos);");

    static List<Item> parse(byte[] rss) {
        List<Item> items = new ArrayList<>();
        String xml = decode(rss);
        Matcher m = ITEM.matcher(xml);
        while (m.find()) {
            String title = text(m.group(1), "title");
            if (title == null || title.isEmpty()) continue;
            String date = text(m.group(1), "pubDate");
            items.add(new Item(title, date == null ? 0 : date(date), link(m.group(1))));
        }
        return items;
    }

    /** The article URL of an item: its <link>, or its <guid> when that one is a URL. */
    private static String link(String item) {
        String link = text(item, "link");
        if (link != null && !link.isEmpty()) return link;
        String guid = text(item, "guid");
        return guid != null && guid.startsWith("http") ? guid : "";
    }

    /** Decodes with the prolog's encoding (read as ISO-8859-1, which any ASCII-compatible prolog survives), else UTF-8. */
    static String decode(byte[] bytes) {
        int skip = bytes.length >= 3 && (bytes[0] & 0xff) == 0xEF && (bytes[1] & 0xff) == 0xBB && (bytes[2] & 0xff) == 0xBF ? 3 : 0;
        Charset cs = StandardCharsets.UTF_8;
        String head = new String(bytes, skip, Math.min(bytes.length - skip, 200), StandardCharsets.ISO_8859_1);
        Matcher m = ENCODING.matcher(head);
        if (skip == 0 && m.find()) {
            try { cs = Charset.forName(m.group(1)); } catch (Exception ignored) {}
        }
        return new String(bytes, skip, bytes.length - skip, cs);
    }

    /** Text of the first <tag> in an item, CDATA unwrapped, entities resolved and whitespace collapsed. */
    private static String text(String item, String tag) {
        Matcher m = Pattern.compile("<" + tag + "\\b[^>]*>(.*?)</" + tag + "\\s*>", Pattern.DOTALL).matcher(item);
        if (!m.find()) return null;
        String raw = m.group(1);
        StringBuilder out = new StringBuilder();
        Matcher c = CDATA.matcher(raw);
        int last = 0;
        while (c.find()) {
            out.append(unescape(raw.substring(last, c.start()))).append(c.group(1));   // CDATA is literal
            last = c.end();
        }
        out.append(unescape(raw.substring(last)));
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    static String unescape(String s) {
        Matcher m = ENTITY.matcher(s);
        StringBuffer sb = new StringBuffer();   // appendReplacement(StringBuilder) needs API 34
        while (m.find()) {
            String e = m.group(1), r;
            switch (e) {
                case "amp": r = "&"; break;
                case "lt": r = "<"; break;
                case "gt": r = ">"; break;
                case "quot": r = "\""; break;
                case "apos": r = "'"; break;
                default:
                    try {
                        int cp = e.charAt(1) == 'x' || e.charAt(1) == 'X'
                                ? Integer.parseInt(e.substring(2), 16) : Integer.parseInt(e.substring(1));
                        r = new String(Character.toChars(cp));
                    } catch (Exception bad) { r = m.group(); }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(r));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** RFC-822 pubDate ("Sun, 25 Oct 2026 07:30:00 GMT" or "+0200") to epoch ms; 0 when unreadable. */
    static long date(String s) {
        try { return ZonedDateTime.parse(s.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli(); }
        catch (Exception e) { return 0; }
    }

    static List<Item> fetch(String url, int max) throws Exception {
        List<Item> items = parse(read(url));
        return items.size() > max ? new ArrayList<>(items.subList(0, max)) : items;
    }

    /** The raw bytes behind a URL (DailyWallpaper.get decodes as UTF-8 and drops line breaks, which feeds can't afford). */
    static byte[] read(String url) throws Exception {
        HttpURLConnection c = DailyWallpaper.open(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally { c.disconnect(); }
    }

    /** Relative age in the interface language: "ara mateix", "fa 5 min", "fa 3 h", "ahir", "fa 2 dies"; empty when unknown. */
    static String age(Strings s, long published, long now) {
        if (published <= 0) return "";
        long min = (now - published) / 60_000L;
        if (min < 1) return s.get(R.string.age_now);
        if (min < 60) return s.get(R.string.age_minutes, min);
        long h = min / 60;
        if (h < 24) return s.get(R.string.age_hours, h);
        long days = h / 24;
        return days == 1 ? s.get(R.string.age_yesterday) : s.plural(R.plurals.age_days, (int) days);
    }

    /** Writes files/news.cache: a header with the format version and the source, then one item per line. */
    static void save(File dir, NewsSource source, List<Item> items) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(CACHE_HEADER).append('\t').append(source.key).append('\n');
        for (Item i : items) {
            sb.append(i.published).append('\t').append(DailyWallpaper.oneLine(i.title)).append('\t')
                    .append(DailyWallpaper.oneLine(i.link)).append('\n');
        }
        File tmp = new File(dir, CACHE + ".part");
        try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(sb.toString().getBytes(StandardCharsets.UTF_8)); }
        if (!tmp.renameTo(new File(dir, CACHE))) { tmp.delete(); throw new Exception("news cache rename failed"); }
    }

    /** The cached items of that source, or null when there are none, they are from another source, or the cache is older. */
    static List<Item> load(File dir, NewsSource source) {
        try {
            String[] lines = new String(Files.readAllBytes(new File(dir, CACHE).toPath()), StandardCharsets.UTF_8).split("\n");
            String head = lines[0];
            if (!head.startsWith(CACHE_HEADER)) return null;   // a cache from an older version is not reused
            int tab = head.indexOf('\t');
            if (tab < 0 || !head.substring(tab + 1).trim().equals(source.key)) return null;
            List<Item> items = new ArrayList<>();
            for (int i = 1; i < lines.length; i++) {
                String l = lines[i];
                if (l.isEmpty()) continue;
                int t1 = l.indexOf('\t');
                if (t1 < 0) return null;
                int t2 = l.indexOf('\t', t1 + 1);
                String title = t2 < 0 ? l.substring(t1 + 1) : l.substring(t1 + 1, t2);
                String link = t2 < 0 ? "" : l.substring(t2 + 1);
                items.add(new Item(title, Long.parseLong(l.substring(0, t1)), link));
            }
            return items;
        } catch (Exception e) { return null; }
    }
}
