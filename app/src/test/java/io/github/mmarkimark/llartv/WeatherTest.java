package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;

public class WeatherTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    // Shape returned by Open-Meteo for the forecast URL Weather.fetch builds (trimmed units blocks kept).
    static final String JSON = "{\"latitude\":41.42,\"longitude\":2.2,\"generationtime_ms\":0.05,\"utc_offset_seconds\":7200,"
            + "\"timezone\":\"Europe/Madrid\",\"timezone_abbreviation\":\"GMT+2\",\"elevation\":12.0,"
            + "\"current_units\":{\"time\":\"iso8601\",\"interval\":\"seconds\",\"temperature_2m\":\"°C\",\"weather_code\":\"wmo code\"},"
            + "\"current\":{\"time\":\"2026-10-25T09:15\",\"interval\":900,\"temperature_2m\":16.4,\"weather_code\":0},"
            + "\"daily_units\":{\"time\":\"iso8601\",\"temperature_2m_max\":\"°C\",\"temperature_2m_min\":\"°C\"},"
            + "\"daily\":{\"time\":[\"2026-10-25\"],\"temperature_2m_max\":[21.6],\"temperature_2m_min\":[12.3]}}";

    @Test public void parsesCurrentAndDailyRoundingToInt() throws Exception {
        Weather w = Weather.parse(JSON, 1234L);
        assertEquals(16, w.temp);
        assertEquals(22, w.max);
        assertEquals(12, w.min);
        assertEquals(0, w.code);
        assertEquals(1234L, w.fetchedAt);
    }

    @Test public void lineAndRangeAreCatalan() throws Exception {
        Weather w = Weather.parse(JSON, 0);
        assertEquals("16° · Serè", w.line());
        assertEquals("Màx. 22° · Mín. 12°", w.range());
    }

    @Test public void negativeTemperaturesRoundAndShowSign() throws Exception {
        Weather w = Weather.parse(JSON.replace("16.4", "-2.6").replace("12.3", "-4.5"), 0);
        assertEquals(-3, w.temp);
        assertEquals(-4, w.min);   // Math.round(-4.5) == -4
        assertEquals("-3° · Serè", w.line());
    }

    @Test public void describesRepresentativeCodesInCatalan() {
        assertEquals("Serè", Weather.describe(0));
        assertEquals("Poc ennuvolat", Weather.describe(1));
        assertEquals("Mig ennuvolat", Weather.describe(2));
        assertEquals("Ennuvolat", Weather.describe(3));
        assertEquals("Boira", Weather.describe(45));
        assertEquals("Boira", Weather.describe(48));
        assertEquals("Plugim", Weather.describe(53));
        assertEquals("Pluja", Weather.describe(61));
        assertEquals("Pluja forta", Weather.describe(65));
        assertEquals("Neu", Weather.describe(73));
        assertEquals("Ruixats", Weather.describe(80));
        assertEquals("Ruixats de neu", Weather.describe(85));
        assertEquals("Tempesta", Weather.describe(95));
        assertEquals("Tempesta amb calamarsa", Weather.describe(99));
    }

    @Test public void everyOpenMeteoCodeHasText() {
        int[] codes = {0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99};
        for (int c : codes) if (Weather.describe(c).isEmpty()) throw new AssertionError("no text for code " + c);
    }

    @Test public void unknownCodeLeavesOnlyTheTemperature() throws Exception {
        assertEquals("", Weather.describe(42));
        Weather w = Weather.parse(JSON.replace("\"weather_code\":0", "\"weather_code\":42"), 0);
        assertEquals("16°", w.line());
    }

    @Test public void cacheRoundTrips() throws Exception {
        File dir = tmp.newFolder();
        Weather.parse(JSON, 987654321L).save(dir);
        Weather w = Weather.load(dir);
        assertEquals(16, w.temp);
        assertEquals(22, w.max);
        assertEquals(12, w.min);
        assertEquals(0, w.code);
        assertEquals(987654321L, w.fetchedAt);
        assertEquals(false, new File(dir, "weather.cache.part").exists());
    }

    @Test public void missingOrCorruptCacheIsNull() throws Exception {
        File dir = tmp.newFolder();
        assertNull(Weather.load(dir));
        try (FileWriter w = new FileWriter(new File(dir, "weather.cache"))) { w.write("garbage"); }
        assertNull(Weather.load(dir));
    }
}
