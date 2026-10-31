package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * The interface language (issue #11): the stored choice, its fallback, and the translations themselves.
 *
 * The JVM has no Android resources, so strings(lang) resolves the real res/values*.xml files the way Android
 * does for LlarTV's three languages: the language's own file, else the default (Catalan) one; "one" for 1 and
 * "other" for anything else, the CLDR rule of Catalan, Spanish and English for whole numbers.
 */
public class LanguageTest {
    // ---------------------------------------------------------------- the stored choice

    @Test public void theThreeLanguagesAreReadInAnyCase() {
        assertEquals(Language.CA, Language.parse("ca"));
        assertEquals(Language.ES, Language.parse(" ES "));
        assertEquals(Language.EN, Language.parse("En"));
        assertEquals("", Language.parse("fr"));
        assertEquals("", Language.parse(""));
        assertEquals("", Language.parse(null));
    }

    @Test public void catalanIsTheDefaultAndTheFallback() {
        assertEquals(Language.CA, Language.DEFAULT);
        assertEquals(Language.CA, Language.of("fr"));
        assertEquals(Language.CA, Language.of(null));
        assertEquals(Language.ES, Language.of("es"));
    }

    @Test public void eachLanguageHasItsLocale() {
        assertEquals("ca", Language.locale(Language.CA).getLanguage());
        assertEquals("es", Language.locale(Language.ES).getLanguage());
        assertEquals("en", Language.locale(Language.EN).getLanguage());
        assertEquals("ca", Language.locale("fr").getLanguage());
    }

    @Test public void theMenuShowsEachLanguageInItsOwnName() {
        assertEquals(3, Language.OPTIONS.length);
        assertEquals(Language.OPTIONS.length, Language.NAMES.length);
        assertEquals("Català", Language.NAMES[0]);
        assertEquals("Español", Language.NAMES[1]);
        assertEquals("English", Language.NAMES[2]);
    }

    @Test public void eachLanguageHasItsAutomaticNewsPreset() {
        assertEquals(NewsSource.THREE_CAT, Language.newsPreset(Language.CA));
        assertEquals(NewsSource.BBC_MUNDO, Language.newsPreset(Language.ES));
        assertEquals(NewsSource.BBC_WORLD, Language.newsPreset(Language.EN));
        assertEquals(NewsSource.THREE_CAT, Language.newsPreset("fr"));
    }

    @Test public void aSnapshotKeepsItsLanguageAndLocale() {
        Strings es = strings(Language.ES);
        assertEquals(Language.ES, es.language);
        assertEquals("es", es.locale.getLanguage());
        assertEquals("Ajustes de LlarTV", es.get(R.string.settings_title));
        assertEquals("Configuració de LlarTV", strings(Language.CA).get(R.string.settings_title));
        assertEquals("LlarTV settings", strings(Language.EN).get(R.string.settings_title));
    }

    // ---------------------------------------------------------------- the translations

    static final String[] FILES = {"values", "values-es", "values-en"};

    @Test public void everyLanguageHasTheSameStringsAndPlurals() throws Exception {
        Res ca = Res.load("values");
        for (String f : FILES) {
            Res r = Res.load(f);
            assertEquals(f, ca.strings.keySet(), r.strings.keySet());
            assertEquals(f, ca.plurals.keySet(), r.plurals.keySet());
            for (Map.Entry<String, Map<String, String>> p : r.plurals.entrySet()) {
                assertTrue(f + " " + p.getKey(), p.getValue().containsKey("one"));
                assertTrue(f + " " + p.getKey(), p.getValue().containsKey("other"));
            }
        }
        assertTrue("too few strings: " + ca.strings.size(), ca.strings.size() > 80);
    }

    @Test public void everyResourceTheCodeNamesResolvesInEveryLanguage() throws Exception {
        for (String lang : Language.OPTIONS) {
            Res own = Res.load(dirFor(lang));
            for (Field f : R.string.class.getFields()) {
                if (NOT_TRANSLATED.contains(f.getName())) continue;
                assertTrue(lang + " lacks " + f.getName(), own.strings.containsKey(f.getName()));
            }
            for (Field f : R.plurals.class.getFields()) {
                assertTrue(lang + " lacks plural " + f.getName(), own.plurals.containsKey(f.getName()));
            }
        }
    }

    static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?[sd]");

    @Test public void placeholdersMatchAcrossLanguages() throws Exception {
        Res ca = Res.load("values");
        for (String f : new String[]{"values-es", "values-en"}) {
            Res r = Res.load(f);
            for (String k : ca.strings.keySet()) {
                assertEquals(f + " " + k, placeholders(ca.strings.get(k)), placeholders(r.strings.get(k)));
            }
            for (String k : ca.plurals.keySet()) {
                // "other" carries the count; "one" may spell it out ("Cada hora") but adds no other placeholder
                Set<String> other = placeholders(ca.plurals.get(k).get("other"));
                assertEquals(f + " " + k, other, placeholders(r.plurals.get(k).get("other")));
                for (String q : r.plurals.get(k).keySet()) {
                    assertTrue(f + " " + k + " " + q, other.containsAll(placeholders(r.plurals.get(k).get(q))));
                }
            }
        }
    }

    @Test public void formattedStringsUsePositionalArguments() throws Exception {
        // aapt2 rejects several non-positional arguments; one rule for all keeps the three files alike
        for (String f : FILES) {
            Res r = Res.load(f);
            for (Map.Entry<String, String> e : r.strings.entrySet()) {
                Matcher m = PLACEHOLDER.matcher(e.getValue());
                while (m.find()) assertTrue(f + " " + e.getKey(), m.group(1) != null);
            }
        }
    }

    /** The texts a language rightly shares with Catalan: names, units and words the two languages spell alike. */
    static final Map<String, Set<String>> SAME_AS_CATALAN = new HashMap<>();
    static {
        SAME_AS_CATALAN.put("values-es", new TreeSet<>(java.util.Arrays.asList("wifi", "ethernet", "clock_24", "clock_12",
                "language", "on", "off", "not_available", "versus", "photo_every_minutes", "wallpaper_bing",
                "hidden_apps", "weekday_pattern", "short_day_pattern")));
        SAME_AS_CATALAN.put("values-en", new TreeSet<>(java.util.Arrays.asList("wifi", "ethernet", "clock_24", "clock_12",
                "hidden_apps", "weekday_pattern", "short_day_pattern")));
    }

    @Test public void translationsAreNotLeftInCatalan() throws Exception {
        Res ca = Res.load("values");
        for (String f : new String[]{"values-es", "values-en"}) {
            Res r = Res.load(f);
            Set<String> same = new TreeSet<>();
            for (String k : ca.strings.keySet()) if (ca.strings.get(k).equals(r.strings.get(k))) same.add(k);
            for (String k : ca.plurals.keySet()) if (ca.plurals.get(k).equals(r.plurals.get(k))) same.add(k);
            assertEquals(f, SAME_AS_CATALAN.get(f), same);
        }
    }

    @Test public void accentsSurviveTheResourceFiles() {
        assertEquals("Serè", strings(Language.CA).get(R.string.wx_clear));
        assertEquals("Despejado", strings(Language.ES).get(R.string.wx_clear));
        assertEquals("Màx. 22° · Mín. 12°", strings(Language.CA).get(R.string.weather_range, 22, 12));
        assertEquals("Máx. 22° · Mín. 12°", strings(Language.ES).get(R.string.weather_range, 22, 12));
        assertEquals("No es pot canviar d'entrada", strings(Language.CA).get(R.string.cant_switch_input));
    }

    @Test public void pluralsPickOneAndOther() {
        assertEquals("1 app", strings(Language.EN).plural(R.plurals.hidden_apps, 1));
        assertEquals("3 apps", strings(Language.EN).plural(R.plurals.hidden_apps, 3));
        assertEquals("d'aquí a 1 dia", strings(Language.CA).plural(R.plurals.countdown_days, 1));
        assertEquals("d'aquí a 4 dies", strings(Language.CA).plural(R.plurals.countdown_days, 4));
        assertEquals("en 4 días", strings(Language.ES).plural(R.plurals.countdown_days, 4));
    }

    @Test public void theResetScopeNamesWhatStaysInEveryLanguage() {
        for (String lang : Language.OPTIONS) {
            Strings s = strings(lang);
            String scope = s.get(R.string.reset_scope);
            assertTrue(lang, scope.contains("dock.txt"));
            assertTrue(lang, scope.contains("home.conf"));
            assertNotEquals(lang, s.get(R.string.reset_cancel), s.get(R.string.reset_confirm));
        }
        assertEquals("Cancel·lar", strings(Language.CA).get(R.string.reset_cancel));
        assertEquals("Cancelar", strings(Language.ES).get(R.string.reset_cancel));
        assertEquals("Cancel", strings(Language.EN).get(R.string.reset_cancel));
    }

    // ---------------------------------------------------------------- the literal audit

    /**
     * Every text a person reads comes from the resources. A literal in the app's sources that looks like words
     * (a space or an accented letter) must be one of these, each for its reason. Exception messages and log lines
     * are for adb, not for the TV, so they are skipped (a log line still may not carry an accent).
     */
    static final Set<String> ALLOWED_LITERALS = new TreeSet<>(java.util.Arrays.asList(
            "Appearance.java: – ",   // the dash of a night range, the same in every language
            // the providers' names and credits are never translated
            "Ca.java:BBC News Mundo", "Ca.java:BBC News World", "NewsSource.java:BBC News",
            "NewsSource.java:BBC News Mundo · bbc.co.uk/news", "NewsSource.java:BBC News World · bbc.co.uk/news",
            "NewsSource.java:3Cat · 3cat.cat",
            "Language.java:Català", "Language.java:Español",   // each language in its own name
            // formats, separators and the shortening of ESPN's competition names (external data)
            "Clock.java:h:mm a", "Ui.java:'wght' ", "DailyWallpaper.java: ", "News.java: ",
            "HomeActivity.java:  ·  ", "HomeActivity.java: · ", "HomeActivity.java: (AV)", "HomeActivity.java:unknown ssid",
            "Weather.java:° · ", "Fixtures.java:UEFA ", "Fixtures.java:English ", "Fixtures.java:FA Community Shield",
            "Fixtures.java:Community Shield", "Fixtures.java:^\\\\d{4}-\\\\d{2} ",
            "DreamMusic.java:), staying silent"));   // the second line of a log call

    static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    @Test public void noAppTextIsWrittenInTheCode() throws Exception {
        File dir = file("src/main/java/io/github/mmarkimark/llartv");
        Set<String> found = new TreeSet<>();
        for (File f : dir.listFiles()) {
            boolean inComment = false;
            for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
                String code = line.trim();
                if (inComment) { if (code.contains("*/")) inComment = false; continue; }
                if (code.startsWith("/*")) { inComment = !code.contains("*/"); continue; }
                if (code.startsWith("*") || code.startsWith("//")) continue;
                int comment = code.indexOf(" // ");
                if (comment >= 0) code = code.substring(0, comment);
                boolean forAdb = code.contains("Log.") || code.contains("throw new");
                Matcher m = LITERAL.matcher(code);
                while (m.find()) {
                    String s = m.group(1).replace("\\u00B7", "·");
                    boolean accented = s.matches(".*[À-ÿ].*");
                    if (accented || (!forAdb && s.contains(" "))) found.add(f.getName() + ":" + s);
                }
            }
        }
        Set<String> unexpected = new TreeSet<>(found), stale = new TreeSet<>(ALLOWED_LITERALS);
        unexpected.removeAll(ALLOWED_LITERALS);
        stale.removeAll(found);
        if (!unexpected.isEmpty()) fail("app texts outside the resources: " + unexpected);
        if (!stale.isEmpty()) fail("allowed literals no longer in the code: " + stale);
    }

    // ---------------------------------------------------------------- the resolver

    /** Resources the code reads that are not texts: the football team, set per build in dock.xml. */
    static final Set<String> NOT_TRANSLATED = new TreeSet<>(java.util.Arrays.asList(
            "app_name", "fixture_team_id", "fixture_team_name"));

    static String dirFor(String lang) { return Language.CA.equals(Language.of(lang)) ? "values" : "values-" + Language.of(lang); }

    private static final Map<String, Strings> CACHE = new HashMap<>();

    /** The snapshot Strings.of builds on the TV, from the same resource files. */
    static synchronized Strings strings(String lang) {
        Strings cached = CACHE.get(lang);
        if (cached != null) return cached;
        try {
            Res own = Res.load(dirFor(lang)), base = Res.load("values");
            Map<Integer, String> names = names(R.string.class), pluralNames = names(R.plurals.class);
            Strings s = new Strings(Language.of(lang), new Strings.Source() {
                @Override public String text(int id) {
                    String name = names.get(id);
                    String v = own.strings.containsKey(name) ? own.strings.get(name) : base.strings.get(name);
                    if (v == null) throw new AssertionError("no string " + name + " in " + lang);
                    return v;
                }
                @Override public String quantity(int id, int n) {
                    String name = pluralNames.get(id);
                    Map<String, String> p = own.plurals.containsKey(name) ? own.plurals.get(name) : base.plurals.get(name);
                    if (p == null) throw new AssertionError("no plural " + name + " in " + lang);
                    String v = p.get(n == 1 ? "one" : "other");
                    return v != null ? v : p.get("other");
                }
            });
            CACHE.put(lang, s);
            return s;
        } catch (Exception e) { throw new AssertionError(e); }
    }

    private static Map<Integer, String> names(Class<?> c) throws IllegalAccessException {
        Map<Integer, String> out = new HashMap<>();
        for (Field f : c.getFields()) out.put(f.getInt(null), f.getName());
        return out;
    }

    static Set<String> placeholders(String s) {
        Set<String> out = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(s);
        while (m.find()) out.add(m.group());
        return out;
    }

    static File file(String path) {
        File f = new File(path);
        return f.exists() ? f : new File("app", path);   // Gradle runs the tests in app/; an IDE may use the root
    }

    /** One strings.xml: its texts and plurals, unescaped the way aapt2 does for the forms LlarTV uses. */
    static final class Res {
        final Map<String, String> strings = new TreeMap<>();
        final Map<String, Map<String, String>> plurals = new TreeMap<>();

        static Res load(String dir) throws Exception {
            Res r = new Res();
            File f = file("src/main/res/" + dir + "/strings.xml");
            if (!f.exists()) return r;
            Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f).getDocumentElement();
            NodeList ss = root.getElementsByTagName("string");
            for (int i = 0; i < ss.getLength(); i++) {
                Element e = (Element) ss.item(i);
                if ("false".equals(e.getAttribute("translatable"))) continue;
                r.strings.put(e.getAttribute("name"), unescape(e.getTextContent()));
            }
            NodeList ps = root.getElementsByTagName("plurals");
            for (int i = 0; i < ps.getLength(); i++) {
                Element e = (Element) ps.item(i);
                Map<String, String> q = new TreeMap<>();
                NodeList items = e.getElementsByTagName("item");
                for (int j = 0; j < items.getLength(); j++) {
                    Element it = (Element) items.item(j);
                    q.put(it.getAttribute("quantity"), unescape(it.getTextContent()));
                }
                r.plurals.put(e.getAttribute("name"), q);
            }
            return r;
        }

        static String unescape(String raw) {
            String s = raw.trim().replaceAll("\\s+", " ");
            if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\\' && i + 1 < s.length()) {
                    char n = s.charAt(++i);
                    b.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
                } else if (c == '\'') {
                    throw new AssertionError("unescaped apostrophe in: " + raw);   // aapt2 would drop it
                } else b.append(c);
            }
            return b.toString();
        }
    }

    @Test public void theResolverUnescapesLikeAapt() {
        assertEquals("d'aquí", Res.unescape("d\\'aquí"));
        assertEquals("a b", Res.unescape("  a \n  b "));
        boolean rejected = false;
        try { Res.unescape("d'aquí"); } catch (AssertionError expected) { rejected = true; }
        assertTrue("an unescaped apostrophe must fail", rejected);
        assertEquals(new ArrayList<>(java.util.Arrays.asList("%1$d", "%2$s")), new ArrayList<>(placeholders("%1$d de %2$s")));
        assertFalse(placeholders("100%").contains("%"));
    }
}
