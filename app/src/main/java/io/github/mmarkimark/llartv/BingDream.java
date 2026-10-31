package io.github.mmarkimark.llartv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Screensaver: the downloaded wallpapers (wallpaper=bing, see DailyWallpaper) shown full screen with a slow
 * zoom-and-drift ("Ken Burns") and a cross-fade, plus the clock, the date and each image's title and caption.
 * Images and weather use the local cache; optional music streams from the configured HTTPS URL.
 * Any remote key closes it.
 * Enable with: adb shell settings put secure screensaver_components io.github.mmarkimark.llartv/.BingDream
 */
public class BingDream extends DreamService {
    static final long SLIDE_MS = 25_000;   // how long each image stays, including its fade-in
    static final long FADE_MS = 1_800;

    final Handler ui = new Handler(Looper.getMainLooper());
    final ExecutorService decoder = Executors.newSingleThreadExecutor();
    ImageView front, back;
    TextView title, caption, date, weather;
    TextView[] inks;
    TimeBands bands;
    float night = -1;
    List<DailyWallpaper.Slide> slides;
    int index, W, H;
    boolean motion = true;   // home.conf motion_screen: off keeps the images still, without zoom or drift
    Strings tx;              // the interface language, read again every time the screensaver starts
    TextView time;           // the clock, rendered by tickClock (TextClock would take AM/PM from the system locale)
    String clockPref = Clock.SYSTEM;
    final Runnable clockTick = this::tickClock;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        Ui.density = dm.density;
        W = dm.widthPixels; H = dm.heightPixels;
        Ui.Fonts fonts = new Ui.Fonts(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        back = image(); front = image();
        root.addView(back, new FrameLayout.LayoutParams(-1, -1));
        root.addView(front, new FrameLayout.LayoutParams(-1, -1));

        // darker top and bottom so the clock and the caption stay readable on bright photos
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x66000000, 0x00000000, 0x00000000, 0x80000000}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        int pad = Math.round(W * 0.05f);
        LinearLayout clockCol = new LinearLayout(this);
        clockCol.setOrientation(LinearLayout.VERTICAL);
        // The clock format follows LlarTV's own preference (home.conf "clock"), rendered by tickClock.
        Config conf = Config.parse(getExternalFilesDir(null));
        clockPref = conf.clock;
        time = text(fonts, 150, 86, 0xFFFFFFFF);
        time.setIncludeFontPadding(false);
        // clock_screen=off hides the time only: the date, the weather line and the photo credits stay
        time.setVisibility(conf.clockScreen ? View.VISIBLE : View.GONE);
        date = text(fonts, 300, 21, 0xFFD9D6CF);
        clockCol.addView(time, new LinearLayout.LayoutParams(-2, -2));
        clockCol.addView(date, new LinearLayout.LayoutParams(-2, -2));
        weather = text(fonts, 300, 18, 0xFFD9D6CF);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-2, -2);
        wp.topMargin = Math.round(4 * Ui.density);
        clockCol.addView(weather, wp);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        cp.leftMargin = pad; cp.topMargin = Math.round(H * 0.07f);
        root.addView(clockCol, cp);

        LinearLayout credit = new LinearLayout(this);
        credit.setOrientation(LinearLayout.VERTICAL);
        credit.setGravity(Gravity.END);
        title = text(fonts, 500, 20, 0xFFFFFFFF);
        caption = text(fonts, 300, 13, 0xFFD0CDC6);
        caption.setSingleLine(false);
        caption.setMaxLines(2);
        caption.setGravity(Gravity.END);
        credit.addView(title, new LinearLayout.LayoutParams(-2, -2));
        credit.addView(caption, new LinearLayout.LayoutParams(-2, -2));
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(Math.round(W * 0.45f), -2, Gravity.BOTTOM | Gravity.END);
        rp.rightMargin = pad; rp.bottomMargin = Math.round(H * 0.06f);
        root.addView(credit, rp);

        inks = new TextView[]{time, date, weather, title, caption};
        for (TextView t : inks) t.setTag(t.getCurrentTextColor());
        setContentView(root);
    }

    /**
     * Shows the time in the interface language and schedules itself for the next minute on the ui handler, which
     * onDreamingStopped and onDetachedFromWindow already clear. "system" reads the TV's 12/24-hour setting on
     * every tick, as TextClock did, and the time zone is the TV's current one.
     */
    void tickClock() {
        ui.removeCallbacks(clockTick);
        long now = System.currentTimeMillis();
        if (tx != null) time.setText(Clock.time(clockPref, android.text.format.DateFormat.is24HourFormat(this),
                tx.locale, new java.util.Date(now)));
        ui.postDelayed(clockTick, Clock.untilNextMinute(now));
    }

    /** Weather the home screen last fetched (the screensaver never goes online itself); hidden when stale or off. */
    void showWeather() {
        Config conf = Config.parse(getExternalFilesDir(null));
        java.util.Calendar c = java.util.Calendar.getInstance();
        boolean morning = bands != null
                && bands.at(c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)) == TimeBands.Band.MORNING;
        Weather w = ContentPolicy.shows(conf.weatherScreen, morning) ? Weather.load(getExternalFilesDir(null)) : null;
        boolean fresh = w != null && System.currentTimeMillis() - w.fetchedAt < 6 * 3600_000L;
        if (fresh) weather.setText(w.line(tx));
        weather.setVisibility(fresh ? View.VISIBLE : View.GONE);
    }

    /** Same night mode as the home screen: checked at start and with every image, so it switches at 23:00 too. */
    void applyNight() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        float a = bands.at(c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)) == TimeBands.Band.NIGHT ? 1f : 0f;
        if (a == night) return;
        night = a;
        android.graphics.ColorFilter f = a > 0f ? new android.graphics.ColorMatrixColorFilter(Look.nightMatrix(a)) : null;
        front.setColorFilter(f); back.setColorFilter(f);
        for (TextView t : inks) t.setTextColor(Look.ink((Integer) t.getTag(), a));
    }

    DreamMusic music;

    void startMusic(Config conf) {
        stopMusic();
        if (!Music.shouldPlay(conf)) return;
        try {
            music = new DreamMusic(this, ui, conf.musicUrl);
            music.begin();
        } catch (RuntimeException e) {
            android.util.Log.i("LlarTV", "screensaver music: unavailable, staying silent");
            stopMusic();
        }
    }

    void stopMusic() {
        if (music != null) { music.stop(); music = null; }
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        Config conf = Config.load(this);
        tx = Strings.of(this, conf.language);   // one snapshot for the whole session, so it never mixes two languages
        date.setText(Ca.date(tx, new java.util.Date()));
        clockPref = conf.clock;
        tickClock();
        bands = conf.bands();
        motion = conf.motionScreen;
        applyNight();
        showWeather();
        startMusic(conf);
        File dir = getExternalFilesDir(null);
        slides = DailyWallpaper.slides(conf.wallpaper, dir);
        if (slides.isEmpty()) return;   // nothing downloaded yet, or wallpaper=off: just the clock on black
        // start on the image the home screen is showing, so the screensaver picks up where home left off
        File now = DailyWallpaper.current(dir, conf.wallpaperMinutes, System.currentTimeMillis());
        for (int i = 0; i < slides.size(); i++) if (slides.get(i).file.equals(now)) index = i;
        next();
    }

    @Override public void onDreamingStopped() {
        ui.removeCallbacksAndMessages(null);
        front.animate().cancel(); back.animate().cancel();
        stopMusic();
        super.onDreamingStopped();
    }

    @Override public void onDetachedFromWindow() {
        ui.removeCallbacksAndMessages(null);
        decoder.shutdownNow();
        stopMusic();
        super.onDetachedFromWindow();
    }

    /** Decodes the next image off the main thread, then fades it in over the current one and starts its drift. */
    void next() {
        DailyWallpaper.Slide s = slides.get(index % slides.size());
        int n = index++;
        decoder.execute(() -> {
            Bitmap b = decode(s.file);
            ui.post(() -> show(b, s, n));
        });
    }

    void show(Bitmap b, DailyWallpaper.Slide s, int n) {
        if (b == null) { ui.postDelayed(this::next, 1000); return; }
        // the two image views keep their z-order (back below front); the incoming image goes into whichever one is
        // hidden, and the cross-fade either fades the upper view in or fades it out to reveal the lower one
        ImageView in = front.getAlpha() > 0.5f ? back : front, out = in == front ? back : front;
        in.setImageBitmap(b);
        startDrift(in, n);
        if (in == front) {
            front.setAlpha(0f);
            front.animate().alpha(1f).setDuration(FADE_MS).withEndAction(() -> out.setImageDrawable(null)).start();
        } else {
            back.setAlpha(1f);
            front.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> out.setImageDrawable(null)).start();
        }
        date.setText(Ca.date(tx, new java.util.Date()));   // refreshed with every image, so it rolls over at midnight
        applyNight();
        showWeather();
        title.setText(s.title);
        caption.setText(s.caption);
        title.setVisibility(s.title.isEmpty() ? View.GONE : View.VISIBLE);
        caption.setVisibility(s.caption.isEmpty() ? View.GONE : View.VISIBLE);
        ui.postDelayed(this::next, SLIDE_MS);
    }

    /** Slow zoom and drift (see Drift); with motion off the image stays still and no animation is started. */
    void startDrift(ImageView v, int n) {
        v.animate().cancel();
        float[] d = Drift.of(n, W, H, motion);
        if (d == null) {
            v.setScaleX(1f); v.setScaleY(1f); v.setTranslationX(0f); v.setTranslationY(0f);
            return;
        }
        v.setScaleX(d[0]); v.setScaleY(d[0]); v.setTranslationX(-d[2]); v.setTranslationY(-d[3]);
        v.animate().scaleX(d[1]).scaleY(d[1]).translationX(d[2]).translationY(d[3])
                .setDuration(SLIDE_MS + FADE_MS).setInterpolator(new LinearInterpolator()).start();
    }

    Bitmap decode(File f) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= W) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (Throwable t) { return null; }
    }

    ImageView image() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return v;
    }

    TextView text(Ui.Fonts fonts, int weight, float sp, int color) {
        TextView t = new TextView(this);
        t.setTypeface(fonts.get(weight));
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setShadowLayer(18, 0, 2, 0x73000000);
        return t;
    }
}
