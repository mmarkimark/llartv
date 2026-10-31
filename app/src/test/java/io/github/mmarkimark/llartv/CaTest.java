package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** The text rules of each interface language: greetings, dates, countdowns and the settings values. */
public class CaTest {
    static final Strings CA = LanguageTest.strings(Language.CA), ES = LanguageTest.strings(Language.ES),
            EN = LanguageTest.strings(Language.EN);

    @Test public void greetingsUseTheCatalanForms() {
        assertEquals("Bona nit", Ca.greeting(CA, 4));
        assertEquals("Bon dia", Ca.greeting(CA, 5));
        assertEquals("Bon dia", Ca.greeting(CA, 11));
        assertEquals("Bon dia", Ca.greeting(CA, 13));
        assertEquals("Bon vespre", Ca.greeting(CA, 14));
        assertEquals("Bon vespre", Ca.greeting(CA, 21));
        assertEquals("Bona nit", Ca.greeting(CA, 22));
    }

    @Test public void greetingsInSpanish() {
        assertEquals("Buenas noches", Ca.greeting(ES, 4));
        assertEquals("Buenos días", Ca.greeting(ES, 5));
        assertEquals("Buenos días", Ca.greeting(ES, 13));
        assertEquals("Buenas tardes", Ca.greeting(ES, 14));
        assertEquals("Buenas tardes", Ca.greeting(ES, 20));
        assertEquals("Buenas noches", Ca.greeting(ES, 21));
    }

    @Test public void greetingsInEnglish() {
        assertEquals("Good evening", Ca.greeting(EN, 4));
        assertEquals("Good morning", Ca.greeting(EN, 5));
        assertEquals("Good morning", Ca.greeting(EN, 11));
        assertEquals("Good afternoon", Ca.greeting(EN, 12));
        assertEquals("Good afternoon", Ca.greeting(EN, 17));
        assertEquals("Good evening", Ca.greeting(EN, 18));
        assertEquals("Good evening", Ca.greeting(EN, 23));
    }

    static Date sunday25October() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid"));
        c.set(2026, Calendar.OCTOBER, 25, 12, 0);
        return c.getTime();
    }

    @Test public void datesFollowTheInterfaceLanguageWhateverTheSystemLocale() {
        Locale old = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);   // the TV's own language plays no part
        try {
            Date d = sunday25October();
            String ca = Ca.date(CA, d);
            assertTrue(ca, ca.startsWith("diumenge, 25 d"));
            assertTrue(ca, ca.endsWith("octubre"));
            assertEquals("domingo, 25 de octubre", Ca.date(ES, d));
            assertEquals("Sunday 25 October", Ca.date(EN, d));
        } finally { Locale.setDefault(old); }
    }

    @Test public void matchDaysFollowTheInterfaceLanguage() {
        Date d = sunday25October();
        assertEquals("diumenge", Ca.weekday(CA, d));
        assertEquals("domingo", Ca.weekday(ES, d));
        assertEquals("Sunday", Ca.weekday(EN, d));
        assertTrue(Ca.shortDay(EN, d), Ca.shortDay(EN, d).startsWith("Sun 25 Oct"));
        assertTrue(Ca.shortDay(ES, d), Ca.shortDay(ES, d).startsWith("dom"));
    }

    @Test public void countdownTexts() {
        assertEquals("d'aquí a 5 min", Ca.countdown(CA, 5));
        assertEquals("d'aquí a 2 h 5 min", Ca.countdown(CA, 125));
        assertEquals("d'aquí a 3 dies", Ca.countdown(CA, 3 * 24 * 60 + 10));
        assertEquals("ha començat fa 12 min", Ca.kickedOff(CA, 12));
    }

    @Test public void countdownTextsInSpanishAndEnglish() {
        assertEquals("en 5 min", Ca.countdown(ES, 5));
        assertEquals("en 2 h 5 min", Ca.countdown(ES, 125));
        assertEquals("en 3 días", Ca.countdown(ES, 3 * 24 * 60 + 10));
        assertEquals("empezó hace 12 min", Ca.kickedOff(ES, 12));
        assertEquals("in 5 min", Ca.countdown(EN, 5));
        assertEquals("in 2 h 5 min", Ca.countdown(EN, 125));
        assertEquals("in 3 days", Ca.countdown(EN, 3 * 24 * 60 + 10));
        assertEquals("started 12 min ago", Ca.kickedOff(EN, 12));
    }

    @Test public void theHiddenAppsRowCountsWithPlurals() {
        assertEquals("Cap", Ca.hiddenApps(CA, 0));
        assertEquals("1 app", Ca.hiddenApps(CA, 1));
        assertEquals("2 apps", Ca.hiddenApps(CA, 2));
        assertEquals("Ninguna", Ca.hiddenApps(ES, 0));
        assertEquals("None", Ca.hiddenApps(EN, 0));
        assertEquals("5 apps", Ca.hiddenApps(EN, 5));
    }

    @Test public void theNewsSourceLabelsMatchThePresetOrder() {
        assertArrayEquals(new String[]{"Automàtic", "3Cat", "BBC News Mundo", "BBC News World"}, Ca.newsSources(CA));
        assertArrayEquals(new String[]{"Automático", "3Cat", "BBC News Mundo", "BBC News World"}, Ca.newsSources(ES));
        assertEquals(NewsSource.OPTIONS.length, Ca.newsSources(EN).length);
        assertEquals("Automatic", Ca.newsSources(EN)[0]);
    }

    @Test public void headingsAreUpperCasedInTheInterfaceLanguage() {
        Locale old = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));   // a dotless-i locale would turn "i" into "İ"
        try { assertEquals("NOTICIAS", Ca.upper(ES, "Noticias")); } finally { Locale.setDefault(old); }
    }
}
