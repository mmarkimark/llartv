package io.github.mmarkimark.llartv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
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
import android.widget.TextClock;
import android.widget.TextView;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Screensaver: the downloaded wallpapers (wallpaper=bing, see DailyWallpaper) shown full screen with a slow
 * zoom-and-drift ("Ken Burns") and a cross-fade, plus the clock, the date and each image's title and caption.
 * Everything runs on the TV: no network, no extra downloads. Any remote key closes it.
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
        // The clock format follows LlarTV's own preference (home.conf "clock"); "system" keeps the TextClock default.
        Config conf = Config.parse(getExternalFilesDir(null));
        TextClock time = clock(fonts, 150, 86, 0xFFFFFFFF, Clock.pattern(conf.clock, false), Clock.pattern(conf.clock, true));
        time.setIncludeFontPadding(false);
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

    /** Weather the home screen last fetched (the screensaver never goes online itself); hidden when stale or off. */
    void showWeather() {
        Config conf = Config.parse(getExternalFilesDir(null));
        java.util.Calendar c = java.util.Calendar.getInstance();
        boolean morning = bands != null
                && bands.at(c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)) == TimeBands.Band.MORNING;
        Weather w = ContentPolicy.shows(conf.weatherScreen, morning) ? Weather.load(getExternalFilesDir(null)) : null;
        boolean fresh = w != null && System.currentTimeMillis() - w.fetchedAt < 6 * 3600_000L;
        if (fresh) weather.setText(w.line());
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

    /** The screensaver music (issue #17): the platform player and the audio focus, nothing else. */
    MediaPlayer music;
    AudioManager audio;
    AudioFocusRequest musicFocus;
    boolean musicPaused;

    /** Plays the stream the owner configured. A stream that fails leaves the screensaver silent and quiet about it. */
    void startMusic(Config conf) {
        if (!Music.shouldPlay(conf)) return;
        try {
            music = new MediaPlayer();
            music.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            music.setOnPreparedListener(mp -> { android.util.Log.i("LlarTV", "screensaver music: playing " + conf.musicUrl); mp.start(); });
            music.setOnErrorListener((mp, what, extra) -> {
                android.util.Log.i("LlarTV", "screensaver music: the stream failed (" + what + "), staying silent");
                ui.post(this::stopMusic);
                return true;
            });
            music.setDataSource(conf.musicUrl.trim());
            music.prepareAsync();
            audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            musicFocus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(f -> onMusicFocus(f))
                    .build();
            audio.requestAudioFocus(musicFocus);
        } catch (Exception e) {
            android.util.Log.i("LlarTV", "screensaver music: could not start (" + e + "), staying silent");
            stopMusic();   // no stream, no sound and no error: the screensaver is still the screensaver
        }
    }

    /** Another app took the sound: wait for it, and pick the music up again when it is gone. */
    void onMusicFocus(int change) {
        if (music == null) return;
        try {
            if (change == AudioManager.AUDIOFOCUS_GAIN) {
                if (musicPaused) { musicPaused = false; music.start(); }
            } else if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                music.pause();
                musicPaused = true;
            }
        } catch (Exception ignored) {}
    }

    /** Stops the music and gives the sound back, so nothing is left playing in the background. */
    void stopMusic() {
        if (music != null) {
            try { music.stop(); } catch (Exception ignored) {}
            music.release();
            music = null;
        }
        if (audio != null && musicFocus != null) {
            audio.abandonAudioFocusRequest(musicFocus);
            musicFocus = null;
        }
        musicPaused = false;
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        date.setText(Ca.date(new java.util.Date()));
        Config conf = Config.load(this);
        bands = conf.bands();
        applyNight();
        showWeather();
        startMusic(conf);
        File dir = getExternalFilesDir(null);
        slides = DailyWallpaper.slides(dir);
        if (slides.isEmpty()) return;   // nothing downloaded yet: just the clock on black
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
        date.setText(Ca.date(new java.util.Date()));   // refreshed with every image, so it rolls over at midnight
        applyNight();
        showWeather();
        title.setText(s.title);
        caption.setText(s.caption);
        title.setVisibility(s.title.isEmpty() ? View.GONE : View.VISIBLE);
        caption.setVisibility(s.caption.isEmpty() ? View.GONE : View.VISIBLE);
        ui.postDelayed(this::next, SLIDE_MS);
    }

    /** Slow zoom and drift; alternates zooming in and out and the drift direction, so consecutive images differ. */
    void startDrift(ImageView v, int n) {
        boolean zoomIn = n % 2 == 0;
        float s0 = zoomIn ? 1.06f : 1.16f, s1 = zoomIn ? 1.16f : 1.06f;
        // stay inside the margin the smaller scale leaves, so no black edge ever shows
        float mx = (1.06f - 1f) * W / 2f * 0.8f, my = (1.06f - 1f) * H / 2f * 0.8f;
        float dx = ((n / 2) % 2 == 0 ? 1 : -1) * mx, dy = (n % 3 == 0 ? -1 : 1) * my;
        v.animate().cancel();
        v.setScaleX(s0); v.setScaleY(s0); v.setTranslationX(-dx); v.setTranslationY(-dy);
        v.animate().scaleX(s1).scaleY(s1).translationX(dx).translationY(dy)
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

    TextClock clock(Ui.Fonts fonts, int weight, float sp, int color, String f12, String f24) {
        TextClock c = new TextClock(this);
        c.setFormat12Hour(f12);
        c.setFormat24Hour(f24);
        c.setTypeface(fonts.get(weight));
        c.setTextSize(sp);
        c.setTextColor(color);
        c.setShadowLayer(18, 0, 2, 0x73000000);
        return c;
    }
}
