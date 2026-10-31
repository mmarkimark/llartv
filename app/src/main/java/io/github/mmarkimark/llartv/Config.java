package io.github.mmarkimark.llartv;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * User settings that don't need a rebuild. Read from a plain text file on the TV:
 *   /sdcard/Android/data/io.github.mmarkimark.llartv/files/home.conf
 * Lines are key=value; '#' starts a comment. Unknown keys are ignored. Example:
 *   columns=6
 *   dock=com.google.android.youtube.tv,com.netflix.ninja,com.amazon.amazonvideo.livingroom
 *   hidden=com.example.someapp
 *   order=com.netflix.ninja,com.example.someapp   (the grid order; empty or missing sorts by name)
 *   team_id=364
 *   team_name=LIVERPOOL FC
 *   wallpaper=bing               (default; an image URL, or off for a plain background)
 *   wallpaper_minutes=30         (default; 0 always shows the newest image)
 *   location=41.4235,2.2010      (latitude,longitude for the weather)
 *   news_source=automatic       (automatic, 3cat, bbc-mundo or bbc-world: the news feed)
 *   news_rss=https://...         (a custom feed; it wins over the preset and is never overwritten by one)
 *   music_screen=off             (on: music while the screensaver is showing)
 *   music_url=https://...         (custom HTTPS audio; empty selects cached Holizna CC0 music)
 *   morning=08:00-11:00          (empty turns the morning panel off)
 *   night=23:00-08:00            (empty turns night mode off)
 *   dock_tint=on                 (dock band takes a dark colour from the wallpaper)
 *   clock=system                 (system, 24 or 12: the clock format)
 *   weather_home=morning         (never, morning or all: the weather on the home screen)
 *   weather_screen=all           (never, morning or all: the weather on the screensaver)
 *   news_home=morning            (never, morning or all: the news on the home screen)
 * Push it with adb, then: adb shell am force-stop io.github.mmarkimark.llartv && adb shell input keyevent KEYCODE_HOME
 */
final class Config {
    static final String FILE = "home.conf";
    int columns = 6;                 // grid columns = number of dock apps (4-6 look best)
    List<String> dock = new ArrayList<>();
    boolean dockSet = false;       // true once home.conf has a dock= line: the dock is the user's, however short
    Set<String> hidden = new LinkedHashSet<>();   // in the order of the file, so a rewrite keeps it
    List<String> order = new ArrayList<>();       // the grid order (GridOrder); empty: by label
    String teamId = "", teamName = "";
    String wallpaper = "bing";       // "bing" or an image URL: refreshed daily (DailyWallpaper); empty or "off" = none
    int wallpaperMinutes = 30;       // > 0: rotate through the downloaded images (Bing: last 8) every N minutes
    double lat = 41.4235, lon = 2.2010;   // weather location (La Verneda, Barcelona)
    String newsSource = NewsSource.AUTOMATIC;   // "automatic", "3cat", "bbc-mundo" or "bbc-world"
    String newsRss = "";           // a custom feed; empty when the preset decides
    boolean newsRssCustom = false; // true when home.conf sets news_rss, so the custom feed wins over the preset
    boolean musicScreen = false;    // optional music while the screensaver is showing
    boolean clockScreen = true;     // the time on the screensaver (the date and the credits always show)
    boolean motionScreen = true;    // the slow zoom and drift of the screensaver images
    String musicUrl = "";           // empty selects cached Holizna; otherwise a custom HTTPS stream
    String morning = TimeBands.DEFAULT_MORNING, night = TimeBands.DEFAULT_NIGHT;
    boolean dockTint = true;
    String clock = Clock.SYSTEM;    // "system", "24" or "12"
    int weatherHome = ContentPolicy.MORNING;   // the weather on the home screen
    int weatherScreen = ContentPolicy.ALL;      // the weather on the screensaver
    int newsHome = ContentPolicy.MORNING;      // the news on the home screen

    TimeBands bands() { return TimeBands.parse(morning, night); }

    /** The one rule for "a custom feed is in effect": home.conf set news_rss to a non-empty value. */
    boolean newsRssIsCustom() {
        return newsRssCustom && newsRss != null && !newsRss.trim().isEmpty();
    }

    static Config load(Context c) {
        Config k = new Config();
        k.teamId = c.getString(R.string.fixture_team_id).trim();
        k.teamName = c.getString(R.string.fixture_team_name).trim();
        return k.readFrom(c.getExternalFilesDir(null));
    }

    /** Settings from home.conf in dir, over the defaults. */
    static Config parse(File dir) { return new Config().readFrom(dir); }

    Config readFrom(File dir) {
        for (String line : read(dir).split("\n")) {
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash);
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            set(line.substring(0, eq).trim().toLowerCase(), line.substring(eq + 1).trim());
        }
        return this;
    }

    /** The raw contents of home.conf in dir; empty when there is none. */
    static String read(File dir) {
        File f = new File(dir, FILE);
        if (!f.exists()) return "";
        try { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }
        catch (IOException e) { return ""; }
    }

    /** Replaces home.conf in dir; written to a temporary file first, so a reader never sees half a file. */
    static void write(File dir, String text) throws IOException {
        File tmp = new File(dir, FILE + ".tmp");
        Files.write(tmp.toPath(), text.getBytes(StandardCharsets.UTF_8));
        Files.move(tmp.toPath(), new File(dir, FILE).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Applies one key=value line; unknown keys and unreadable values are ignored. */
    void set(String key, String v) {
        switch (key) {
            case "columns":
                try { columns = Math.max(3, Math.min(7, Integer.parseInt(v))); } catch (NumberFormatException ignored) {}
                break;
            case "dock": dock = split(v); dockSet = true; break;
            case "hidden": hidden = new LinkedHashSet<>(split(v)); break;
            case "order": order = split(v); break;
            case "team_id": teamId = v; break;
            case "team_name": teamName = v; break;
            case "wallpaper": wallpaper = v.equalsIgnoreCase("off") ? "" : v; break;
            case "wallpaper_minutes": try { wallpaperMinutes = Math.max(0, Integer.parseInt(v)); } catch (NumberFormatException ignored) {} break;
            case "location": {
                String[] p = v.split(",");
                try { if (p.length == 2) { double a = Double.parseDouble(p[0].trim()), b = Double.parseDouble(p[1].trim()); lat = a; lon = b; } }
                catch (NumberFormatException ignored) {}
                break;
            }
            case "news_rss": newsRss = v; newsRssCustom = true; break;
            case "news_source": {
                String p = NewsSource.canonicalId(v);
                newsSource = p.isEmpty() ? NewsSource.AUTOMATIC : NewsSource.isPreset(p) ? p : newsSource;
                break;
            }
            case "music_screen": {
                String p = v.trim().toLowerCase(java.util.Locale.ROOT);
                if (p.isEmpty()) musicScreen = false;                       // an empty value restores the default
                else musicScreen = !(p.equals("off") || p.equals("false") || p.equals("0") || p.equals("no"));
                break;
            }
            case "music_url": musicUrl = v; break;
            case "clock_screen": clockScreen = onOff(v, true); break;
            case "motion_screen": motionScreen = onOff(v, true); break;
            case "morning": morning = v; break;
            case "night": night = v; break;
            case "dock_tint": dockTint = !v.equalsIgnoreCase("off") && !v.equalsIgnoreCase("false") && !v.equals("0"); break;
            case "clock": {
                String p = v.trim().toLowerCase(java.util.Locale.ROOT);
                if (p.isEmpty()) p = Clock.SYSTEM;
                if (Clock.SYSTEM.equals(p) || Clock.H24.equals(p) || Clock.H12.equals(p)) clock = p;
                break;
            }
            case "weather_home": weatherHome = ContentPolicy.parse(v, weatherHome); break;
            case "weather_screen": weatherScreen = ContentPolicy.parse(v, weatherScreen); break;
            case "news_home": newsHome = ContentPolicy.parse(v, newsHome); break;
        }
    }

    /** An on/off value: off, false, 0 or no turn it off, anything else on, and an empty value keeps the default. */
    static boolean onOff(String v, boolean byDefault) {
        String p = v.trim().toLowerCase(java.util.Locale.ROOT);
        if (p.isEmpty()) return byDefault;
        return !(p.equals("off") || p.equals("false") || p.equals("0") || p.equals("no"));
    }

    /** Replaces one key=value line in the raw configuration text, keeping every other line; appends it when missing. */
    static String setInText(String text, String key, String value) {
        String[] lines = text.split("\n", -1);
        String line = key + "=" + value;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            int hash = l.indexOf('#');
            String head = (hash >= 0 ? l.substring(0, hash) : l).trim();
            int eq = head.indexOf('=');
            if (eq < 0) continue;
            if (!head.substring(0, eq).trim().equalsIgnoreCase(key)) continue;
            lines[i] = line;
            return String.join("\n", lines);
        }
        String prefix = text.isEmpty() || text.endsWith("\n") ? "" : "\n";
        return text + prefix + line + "\n";
    }

    static List<String> split(String v) {
        List<String> out = new ArrayList<>();
        for (String s : Arrays.asList(v.split("[,\\s]+"))) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }
}
