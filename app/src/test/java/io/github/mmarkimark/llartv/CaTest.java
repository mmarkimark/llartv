package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class CaTest {
    @Test public void greetingsKeepTheExistingHourThresholds() {
        assertEquals("Bona nit", Ca.greeting(4));
        assertEquals("Bon dia", Ca.greeting(5));
        assertEquals("Bon dia", Ca.greeting(11));
        assertEquals("Bona tarda", Ca.greeting(12));
        assertEquals("Bon vespre", Ca.greeting(17));
        assertEquals("Bona nit", Ca.greeting(22));
    }

    @Test public void datesAreCatalanWhateverTheSystemLocale() {
        java.util.Locale old = java.util.Locale.getDefault();
        java.util.Locale.setDefault(new java.util.Locale("es", "ES"));
        try {
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Europe/Madrid"));
            c.set(2026, Calendar.OCTOBER, 25, 12, 0);
            String d = Ca.date(c.getTime());
            assertTrue(d, d.startsWith("diumenge, 25 d"));
            assertTrue(d, d.endsWith("octubre"));
        } finally { java.util.Locale.setDefault(old); }
    }

    @Test public void countdownTexts() {
        assertEquals("d'aquí a 5 min", Ca.countdown(5));
        assertEquals("d'aquí a 2 h 5 min", Ca.countdown(125));
        assertEquals("d'aquí a 3 dies", Ca.countdown(3 * 24 * 60 + 10));
        assertEquals("ha començat fa 12 min", Ca.kickedOff(12));
    }
}
