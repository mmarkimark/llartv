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
 * In the night band the optional quiet visual (night_screen) shows only the clock on black and the optional night
 * silence (night_mute) holds the music back; see NightQuiet. The band is checked at start, on every per-minute clock
 * tick and with every image, so a running session follows both boundaries without any extra polling.
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
    final Runnable slideTick = this::next;   // one instance, so the quiet visual can remove the pending slide
    View credit;             // the photo title and caption
    Config conf;             // this session's settings, read in onDreamingStarted
    NightQuiet quiet = new NightQuiet(false, false);
    boolean clockScreen = true, shouldPlay;
    boolean quietShown;      // the quiet visual is showing: the clock on black, no photos, credits or weather
    Boolean lastNight;       // the band at the previous check of this session; null before the first one
    int slideGen;            // bumped by showQuiet, so an image decoded for an earlier slide loop is dropped

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
        Config conf = Config.parse(ConfigLocation.dir(this));
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
        this.credit = credit;
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
        if (tx != null && quietShown) date.setText(Ca.date(tx, new java.util.Date(now)));   // no photos to refresh it
        checkNight();   // the per-minute tick also catches the night band's start and end while no photo changes
        ui.postDelayed(clockTick, Clock.untilNextMinute(now));
    }

    /** Weather the home screen last fetched (the screensaver never goes online itself); hidden when stale or off. */
    void showWeather() {
        if (quietShown) { weather.setVisibility(View.GONE); return; }
        Config conf = Config.parse(ConfigLocation.dir(this));
        java.util.Calendar c = java.util.Calendar.getInstance();
        boolean morning = bands != null
                && bands.at(c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)) == TimeBands.Band.MORNING;
        Weather w = ContentPolicy.shows(conf.weatherScreen, morning) ? Weather.load(getExternalFilesDir(null)) : null;
        boolean fresh = w != null && System.currentTimeMillis() - w.fetchedAt < 6 * 3600_000L;
        if (fresh) weather.setText(w.line(tx));
        weather.setVisibility(fresh ? View.VISIBLE : View.GONE);
    }

    /** Same night mode as the home screen: checked at start, every minute and with every image (see checkNight). */
    void applyNight() {
        float a = inNight() ? 1f : 0f;
        if (a == night) return;
        night = a;
        android.graphics.ColorFilter f = a > 0f ? new android.graphics.ColorMatrixColorFilter(Look.nightMatrix(a)) : null;
        front.setColorFilter(f); back.setColorFilter(f);
        for (TextView t : inks) t.setTextColor(Look.ink((Integer) t.getTag(), a));
    }

    boolean inNight() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return bands != null
                && bands.at(c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)) == TimeBands.Band.NIGHT;
    }

    /**
     * Checks the night band (NightQuiet): the night tint, then on a change the quiet visual and the music. Called at
     * start, from the per-minute clock tick and with every image; does nothing before the session's settings are read.
     */
    void checkNight() {
        if (conf == null || bands == null) return;
        applyNight();
        boolean n = inNight();
        boolean q = quiet.quiet(n);
        if (q != quietShown) showQuiet(q, true);
        int change = quiet.musicChange(lastNight, n, shouldPlay);
        lastNight = n;
        if (change == NightQuiet.STOP) stopMusic();
        else if (change == NightQuiet.START) startMusic(conf);   // startMusic stops any previous player first
    }

    /**
     * Shows or leaves the quiet visual. Entering it stops the slide loop and hides the photos, their credits and the
     * weather, leaving the clock (always) and the date on black. Leaving it restores the photos, the credits, the
     * weather and clock_screen, and restarts the slide loop when there are photos (only when restart is true).
     */
    void showQuiet(boolean q, boolean restart) {
        quietShown = q;
        slideGen++;
        ui.removeCallbacks(slideTick);
        int photos = q ? View.GONE : View.VISIBLE;
        if (q) {
            front.animate().cancel(); back.animate().cancel();
            front.setImageDrawable(null); back.setImageDrawable(null);   // frees the bitmaps while they are hidden
        }
        front.setVisibility(photos); back.setVisibility(photos); credit.setVisibility(photos);
        time.setVisibility(NightQuiet.clockShown(clockScreen, q) ? View.VISIBLE : View.GONE);
        showWeather();
        if (!q && restart && slides != null && !slides.isEmpty()) next();
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
        this.conf = null;   // no night check until this session's settings are all in place
        tx = Strings.of(this, conf.language);   // one snapshot for the whole session, so it never mixes two languages
        date.setText(Ca.date(tx, new java.util.Date()));
        clockPref = conf.clock;
        tickClock();
        bands = conf.bands();
        motion = conf.motionScreen;
        clockScreen = conf.clockScreen;
        quiet = new NightQuiet(conf.nightScreen, conf.nightMute);
        shouldPlay = Music.shouldPlay(conf);
        File dir = getExternalFilesDir(null);
        slides = DailyWallpaper.slides(conf.wallpaper, dir);
        applyNight();
        boolean n = inNight();
        lastNight = n;   // the first check only records the band: the music is decided right here
        this.conf = conf;
        showQuiet(quiet.quiet(n), false);   // also shows the weather unless the quiet visual hides it
        if (quiet.musicAllowed(shouldPlay, n)) startMusic(conf);
        if (quietShown || slides.isEmpty()) return;   // quiet night, nothing downloaded or wallpaper=off: clock on black
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
        int n = index++, gen = slideGen;
        decoder.execute(() -> {
            Bitmap b = decode(s.file);
            ui.post(() -> { if (gen == slideGen) show(b, s, n); });
        });
    }

    void show(Bitmap b, DailyWallpaper.Slide s, int n) {
        checkNight();   // with every image too; entering the quiet visual stops the slide loop
        if (quietShown) return;   // an image decoded just before the night began is dropped
        if (b == null) { ui.postDelayed(slideTick, 1000); return; }
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
        showWeather();
        title.setText(s.title);
        caption.setText(s.caption);
        title.setVisibility(s.title.isEmpty() ? View.GONE : View.VISIBLE);
        caption.setVisibility(s.caption.isEmpty() ? View.GONE : View.VISIBLE);
        ui.postDelayed(slideTick, SLIDE_MS);
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
