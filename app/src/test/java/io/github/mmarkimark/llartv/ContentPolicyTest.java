package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ContentPolicyTest {
    @Test public void neverAndOffParseToNever() {
        assertEquals(ContentPolicy.NEVER, ContentPolicy.parse("never", ContentPolicy.MORNING));
        assertEquals(ContentPolicy.NEVER, ContentPolicy.parse("off", ContentPolicy.ALL));
    }

    @Test public void morningParsesToMorning() {
        assertEquals(ContentPolicy.MORNING, ContentPolicy.parse("morning", ContentPolicy.NEVER));
    }

    @Test public void allAndAllDayParseToAll() {
        assertEquals(ContentPolicy.ALL, ContentPolicy.parse("all", ContentPolicy.NEVER));
        assertEquals(ContentPolicy.ALL, ContentPolicy.parse("all_day", ContentPolicy.MORNING));
    }

    @Test public void emptyValueRestoresTheDefault() {
        assertEquals(ContentPolicy.MORNING, ContentPolicy.parse("", ContentPolicy.MORNING));
        assertEquals(ContentPolicy.ALL, ContentPolicy.parse("", ContentPolicy.ALL));
    }

    @Test public void unknownValueKeepsTheFallback() {
        assertEquals(ContentPolicy.MORNING, ContentPolicy.parse("sometimes", ContentPolicy.MORNING));
        assertEquals(ContentPolicy.NEVER, ContentPolicy.parse("sometimes", ContentPolicy.NEVER));
    }

    @Test public void parsingIsCaseInsensitiveAndTrimmed() {
        assertEquals(ContentPolicy.NEVER, ContentPolicy.parse(" NEVER ", ContentPolicy.ALL));
        assertEquals(ContentPolicy.ALL, ContentPolicy.parse("All_Day", ContentPolicy.NEVER));
    }

    @Test public void valuesAreTheStoredStrings() {
        assertEquals("never", ContentPolicy.value(ContentPolicy.NEVER));
        assertEquals("morning", ContentPolicy.value(ContentPolicy.MORNING));
        assertEquals("all", ContentPolicy.value(ContentPolicy.ALL));
    }

    @Test public void allShowsAllDay() {
        assertTrue(ContentPolicy.shows(ContentPolicy.ALL, true));
        assertTrue(ContentPolicy.shows(ContentPolicy.ALL, false));
    }

    @Test public void morningShowsOnlyInsideTheMorningBand() {
        assertTrue(ContentPolicy.shows(ContentPolicy.MORNING, true));
        assertFalse(ContentPolicy.shows(ContentPolicy.MORNING, false));
    }

    @Test public void neverShowsNothing() {
        assertFalse(ContentPolicy.shows(ContentPolicy.NEVER, true));
        assertFalse(ContentPolicy.shows(ContentPolicy.NEVER, false));
    }

    @Test public void onlyNeverWantsNothing() {
        assertFalse(ContentPolicy.wants(ContentPolicy.NEVER));
        assertTrue(ContentPolicy.wants(ContentPolicy.MORNING));
        assertTrue(ContentPolicy.wants(ContentPolicy.ALL));
    }
}
