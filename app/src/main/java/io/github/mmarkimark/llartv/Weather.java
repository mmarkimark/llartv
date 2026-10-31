package io.github.mmarkimark.llartv;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Current weather and today's max/min from Open-Meteo (no API key), for the morning panel and the screensaver.
 * HomeActivity fetches it and saves it to files/weather.cache; BingDream only reads that cache.
 * The cache keeps the WMO code, not a text, so the condition shows in whatever the interface language is now.
 */
final class Weather {
    static final String CACHE = "weather.cache";

    final int temp, max, min, code;   // °C rounded to int; code is the WMO weather code
    final long fetchedAt;

    Weather(int temp, int max, int min, int code, long fetchedAt) {
        this.temp = temp; this.max = max; this.min = min; this.code = code; this.fetchedAt = fetchedAt;
    }

    static String url(double lat, double lon) {
        return String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min"
                + "&timezone=auto&forecast_days=1", lat, lon);
    }

    /** Parses Open-Meteo's forecast response; throws when a field is missing (JSONException is checked on Android). */
    static Weather parse(String openMeteoJson, long now) throws Exception {
        JSONObject o = new JSONObject(openMeteoJson);
        JSONObject current = o.getJSONObject("current"), daily = o.getJSONObject("daily");
        return new Weather((int) Math.round(current.getDouble("temperature_2m")),
                (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)),
                (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)),
                current.getInt("weather_code"), now);
    }

    static Weather fetch(double lat, double lon) throws Exception {
        return parse(read(DailyWallpaper.open(url(lat, lon))), System.currentTimeMillis());
    }

    /** An Open-Meteo forecast is about 1 KB; a larger body fails the fetch and the cached weather stays. */
    static final int MAX_BYTES = 256 * 1024;

    static String read(HttpURLConnection c) throws Exception { return Http.text(c, MAX_BYTES); }

    /** The text of a WMO weather code as Open-Meteo uses them, in the interface language; empty for an unknown code. */
    static String describe(Strings s, int wmoCode) {
        int id = condition(wmoCode);
        return id == 0 ? "" : s.get(id);
    }

    /** The resource for a WMO code; 0 for an unknown one. */
    static int condition(int wmoCode) {
        switch (wmoCode) {
            case 0: return R.string.wx_clear;
            case 1: return R.string.wx_mainly_clear;
            case 2: return R.string.wx_partly_cloudy;
            case 3: return R.string.wx_overcast;
            case 45: case 48: return R.string.wx_fog;
            case 51: case 53: case 55: return R.string.wx_drizzle;
            case 56: case 57: return R.string.wx_freezing_drizzle;
            case 61: case 63: return R.string.wx_rain;
            case 65: return R.string.wx_heavy_rain;
            case 66: case 67: return R.string.wx_freezing_rain;
            case 71: case 73: case 77: return R.string.wx_snow;
            case 75: return R.string.wx_heavy_snow;
            case 80: case 81: return R.string.wx_showers;
            case 82: return R.string.wx_heavy_showers;
            case 85: case 86: return R.string.wx_snow_showers;
            case 95: return R.string.wx_thunderstorm;
            case 96: case 99: return R.string.wx_thunderstorm_hail;
            default: return 0;
        }
    }

    /** "16° · Serè" (just "16°" for an unknown code). */
    String line(Strings s) {
        String d = describe(s, code);
        return d.isEmpty() ? temp + "°" : temp + "° · " + d;
    }

    /** "Màx. 22° · Mín. 12°". */
    String range(Strings s) { return s.get(R.string.weather_range, max, min); }

    /** Writes files/weather.cache as one tab-separated line, via a temp file so a reader never sees half of it. */
    void save(File dir) throws Exception {
        String line = temp + "\t" + max + "\t" + min + "\t" + code + "\t" + fetchedAt;
        File tmp = new File(dir, CACHE + ".part");
        try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(line.getBytes(StandardCharsets.UTF_8)); }
        if (!tmp.renameTo(new File(dir, CACHE))) { tmp.delete(); throw new Exception("weather cache rename failed"); }
    }

    /** The cached weather, or null when there is none or it can't be read. */
    static Weather load(File dir) {
        try {
            String[] p = new String(Files.readAllBytes(new File(dir, CACHE).toPath()), StandardCharsets.UTF_8).trim().split("\t");
            if (p.length != 5) return null;
            return new Weather(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                    Integer.parseInt(p[3]), Long.parseLong(p[4]));
        } catch (Exception e) { return null; }
    }
}
