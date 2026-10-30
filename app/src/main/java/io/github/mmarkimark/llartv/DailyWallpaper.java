package io.github.mmarkimark.llartv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Optional wallpaper that changes on its own: home.conf "wallpaper=bing" (the last 8 Bing images of the day)
 * or "wallpaper=https://..." (any image URL, re-downloaded daily). Each image is saved as files/wallpaper-<hash>.jpg
 * and files/wallpapers.txt lists them newest first, one per line as name<TAB>title<TAB>caption (lists from earlier
 * versions hold just the name). With "wallpaper_minutes=N" the home screen rotates through them; the screensaver
 * (BingDream) shows them all with their title and caption.
 */
final class DailyWallpaper {
    private DailyWallpaper() {}

    static final String LIST = "wallpapers.txt";
    static final String PREFIX = "wallpaper-";
    static final String LEGACY = "wallpaper-daily.jpg";   // single-image file from earlier versions
    static final int BING_COUNT = 8;                      // Bing's archive serves at most the last 8 days

    /** One downloaded image with the text shown under it in the screensaver (empty when unknown). */
    static final class Slide {
        final File file;
        final String title, caption;
        Slide(File file, String title, String caption) { this.file = file; this.title = title; this.caption = caption; }
    }

    /** Source image: its URL plus Bing's title and copyright line (empty for a plain URL source). */
    static final class Remote {
        final String url, title, caption;
        Remote(String url, String title, String caption) { this.url = url; this.title = title; this.caption = caption; }
    }

    static List<Remote> imageUrls(String source) throws Exception {
        List<Remote> urls = new ArrayList<>();
        if (!source.equalsIgnoreCase("bing")) { urls.add(new Remote(source, "", "")); return urls; }
        String mkt = Locale.getDefault().toLanguageTag();
        JSONArray images = new JSONObject(get("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=" + BING_COUNT + "&mkt=" + mkt))
                .getJSONArray("images");
        for (int i = 0; i < images.length(); i++) {
            JSONObject o = images.getJSONObject(i);
            urls.add(new Remote("https://www.bing.com" + o.getString("urlbase") + "_1920x1080.jpg",
                    o.optString("title"), o.optString("copyright")));
        }
        return urls;
    }

    /** A line of the list file; tabs and line breaks in the texts become spaces so each entry stays on one line. */
    static String listLine(String name, String title, String caption) {
        return name + "\t" + oneLine(title) + "\t" + oneLine(caption);
    }

    static String oneLine(String s) { return s == null ? "" : s.replaceAll("[\\t\\r\\n]+", " ").trim(); }

    static String fileName(String url) { return PREFIX + Integer.toHexString(url.hashCode()) + ".jpg"; }

    /**
     * Downloads the images that aren't on disk yet, rewrites the list, and deletes images that left it.
     * A URL source (not Bing) is always re-downloaded, since the image behind it may change.
     * Images that fail to download are left out; throws only when none is available.
     */
    static void download(String source, File dir) throws Exception {
        boolean bing = source.equalsIgnoreCase("bing");
        List<String> names = new ArrayList<>(), lines = new ArrayList<>();
        for (Remote r : imageUrls(source)) {
            String name = fileName(r.url);
            File f = new File(dir, name);
            if (!bing || !f.exists()) {
                try { save(r.url, f); } catch (Exception e) { if (!f.exists()) continue; }
            }
            names.add(name);
            lines.add(listLine(name, r.title, r.caption));
        }
        if (names.isEmpty()) throw new Exception("no image downloaded");
        File tmp = new File(dir, LIST + ".part");
        try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(String.join("\n", lines).getBytes("UTF-8")); }
        if (!tmp.renameTo(new File(dir, LIST))) { tmp.delete(); throw new Exception("list rename failed"); }
        File[] all = dir.listFiles();
        if (all != null) for (File f : all) {
            String n = f.getName();
            if ((n.startsWith(PREFIX) && n.endsWith(".jpg") && !names.contains(n)) || n.equals(LEGACY)) f.delete();
        }
    }

    /** Saved images with their texts, newest first (empty when none). */
    static List<Slide> slides(File dir) {
        List<Slide> slides = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(new File(dir, LIST)), "UTF-8"))) {
            String l;
            while ((l = r.readLine()) != null) {
                String[] p = l.split("\t", -1);
                String name = p[0].trim();
                File f = new File(dir, name);
                if (!name.isEmpty() && f.exists())
                    slides.add(new Slide(f, p.length > 1 ? p[1].trim() : "", p.length > 2 ? p[2].trim() : ""));
            }
        } catch (Exception ignored) {}
        if (slides.isEmpty() && new File(dir, LEGACY).exists()) slides.add(new Slide(new File(dir, LEGACY), "", ""));
        return slides;
    }

    /** Saved images, newest first (empty when none). */
    static List<File> images(File dir) {
        List<File> files = new ArrayList<>();
        for (Slide s : slides(dir)) files.add(s.file);
        return files;
    }

    /**
     * The background Home shows for the wallpaper setting: a pushed wallpaper.jpg/png always wins; otherwise
     * nothing when the downloads are off (source empty: a plain background, even with photos still cached),
     * else the downloaded photo of the moment (see current).
     */
    static File shown(String source, File dir, int minutes, long now) {
        for (String n : new String[]{"wallpaper.jpg", "wallpaper.png"}) {
            File pushed = new File(dir, n);
            if (pushed.exists()) return pushed;
        }
        return source.isEmpty() ? null : current(dir, minutes, now);
    }

    /** The screensaver's photos for the wallpaper setting: none when the downloads are off. */
    static List<Slide> slides(String source, File dir) {
        return source.isEmpty() ? new ArrayList<>() : slides(dir);
    }

    /** The image to show now: the newest, or with minutes > 0 one that advances every that many minutes. */
    static File current(File dir, int minutes, long now) {
        List<File> files = images(dir);
        if (files.isEmpty()) return null;
        if (minutes <= 0) return files.get(0);
        return files.get((int) ((now / (minutes * 60_000L)) % files.size()));
    }

    /** Downloads one image to f (via a temp file, so a failed download never leaves half an image). */
    static void save(String url, File f) throws Exception {
        HttpURLConnection c = open(url);
        File tmp = new File(f.getPath() + ".part");
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally { c.disconnect(); }
        if (tmp.length() == 0 || !tmp.renameTo(f)) { tmp.delete(); throw new Exception("empty or rename failed"); }
    }

    static String get(String url) throws Exception {
        HttpURLConnection c = open(url);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l);
            return sb.toString();
        } finally { c.disconnect(); }
    }

    static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "LlarTV/2.0");
        if (c.getResponseCode() != 200) { c.disconnect(); throw new Exception("HTTP " + c.getResponseCode()); }
        return c;
    }
}
