package io.github.mmarkimark.llartv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * LlarTV's own settings, navigable with the TV remote.
 *
 * It writes to the same home.conf as scripts/config.sh, through the same atomic write, so both
 * ways keep working side by side and the files, the unknown keys and the untouched values are
 * preserved. The screen is only built when it is opened; Home never constructs it.
 *
 * It opens as a side panel on the right, like the Android TV settings: the panel slides in over the
 * dimmed home screen, which stays visible behind it.
 *
 * D-pad: up/down move between the settings, left/right change the value of the focused one, OK
 * opens its list of values inside the panel, Back closes that list first and then the panel.
 */
public class SettingsActivity extends Activity {
    /** One line of the menu: either a value to pick or an action to run. */
    static final class Row {
        final String label;
        final String key;            // home.conf key, null for an action
        final String[] options;      // the values shown
        final String[] values;       // what is written to home.conf, null for an action
        final Runnable action;       // for an action row
        int selected;
        boolean custom;              // a custom feed is in effect, so the presets do not apply
        LinearLayout view;
        TextView value;

        Row(String label, String key, String[] options, String[] values, Runnable action) {
            this.label = label;
            this.key = key;
            this.options = options;
            this.values = values;
            this.action = action;
        }

        boolean isAction() { return values == null; }
    }

    // The colours of the Android TV settings panel, sampled on the reference TV.
    static final int PANEL_BG = 0xFF202124, FOCUS_BG = 0xFF35363A, SCRIM = 0x99000000;
    static final int INK = 0xFFE8EAED, INK_DIM = 0xFF9AA0A6, ACCENT = 0xFF8AB4F8;
    static final long SLIDE_MS = 220;

    final List<Row> rows = new ArrayList<>();
    Ui.Fonts fonts;
    View scrim;
    LinearLayout side, list, panel;
    ScrollView scroll;
    TextView panelTitle;
    LinearLayout panelList;
    int focus = 0;
    int panelIdx = 0;
    Row panelRow;
    boolean closing;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        overridePendingTransition(0, 0);   // the panel slides in by itself
        DisplayMetrics dm = getResources().getDisplayMetrics();
        Ui.density = dm.density;
        fonts = new Ui.Fonts(this);

        FrameLayout root = new FrameLayout(this);   // transparent: the home screen shows through, dimmed
        scrim = new View(this);
        scrim.setBackgroundColor(SCRIM);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setBackgroundColor(PANEL_BG);
        side.setPadding(0, dp(48), 0, dp(24));
        TextView title = text(500, 20, INK);
        title.setText(Ca.SETTINGS_TITLE);
        title.setPadding(dp(24), 0, dp(24), dp(20));
        side.addView(title, new LinearLayout.LayoutParams(-1, -2));

        scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        side.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // The values of one setting replace the list inside the panel, like a page of the system settings.
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setVisibility(View.GONE);
        panelTitle = text(500, 15, ACCENT);
        panelTitle.setPadding(dp(24), 0, dp(24), dp(8));
        panelList = new LinearLayout(this);
        panelList.setOrientation(LinearLayout.VERTICAL);
        panel.addView(panelTitle, new LinearLayout.LayoutParams(-1, -2));
        panel.addView(panelList, new LinearLayout.LayoutParams(-1, -2));
        side.addView(panel, new LinearLayout.LayoutParams(-1, 0, 1f));

        int width = SidePanel.width(dm.widthPixels);
        root.addView(side, new FrameLayout.LayoutParams(width, -1, Gravity.END));
        setContentView(root);
        buildRows();
        applyFocus();

        scrim.setAlpha(0f);
        side.setTranslationX(width);
        scrim.animate().alpha(1f).setDuration(SLIDE_MS).start();
        side.animate().translationX(0f).setDuration(SLIDE_MS).setInterpolator(new DecelerateInterpolator()).start();
    }

    /** Slides the panel out before the activity goes, so Back leaves the way the panel came in. */
    @Override public void finish() {
        if (closing) return;
        closing = true;
        scrim.animate().alpha(0f).setDuration(SLIDE_MS).start();
        side.animate().translationX(side.getWidth()).setDuration(SLIDE_MS)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    SettingsActivity.super.finish();
                    overridePendingTransition(0, 0);
                }).start();
    }

    /** The settings the menu shows. Each one writes to home.conf and nothing else. */
    void buildRows() {
        Config conf = Config.load(this);
        rows.add(new Row(Ca.CLOCK, "clock",
                new String[]{Ca.CLOCK_SYSTEM, Ca.CLOCK_24, Ca.CLOCK_12},
                new String[]{Clock.SYSTEM, Clock.H24, Clock.H12}, null));
        rows.get(0).selected = Math.max(0, indexOf(rows.get(0).values, conf.clock));
        addPolicyRow(Ca.WEATHER_HOME, "weather_home", conf.weatherHome);
        addPolicyRow(Ca.WEATHER_SCREEN, "weather_screen", conf.weatherScreen);
        addPolicyRow(Ca.NEWS_HOME, "news_home", conf.newsHome);
        addSourceRow(conf);
        addMusicRow(conf);
        rows.add(new Row(Ca.SYSTEM_SETTINGS, null, null, null, new Runnable() {
            @Override public void run() { openSystemSettings(); }
        }));
        for (Row r : rows) addRowView(r);
    }

    /** The news source. A custom "news_rss" wins over the presets, so the row shows that instead. */
    void addSourceRow(Config conf) {
        Row r = new Row(Ca.NEWS_SOURCE, "news_source", Ca.NEWS_SOURCE_LABELS, NewsSource.OPTIONS, null);
        r.selected = Math.max(0, indexOf(r.values, conf.newsSource));
        r.custom = conf.newsRssIsCustom();
        rows.add(r);
    }

    /** A row with the three content states: never, only in the morning band, all day. */
    void addPolicyRow(String label, String key, int policy) {
        Row r = new Row(label, key,
                new String[]{Ca.NEVER, Ca.MORNING_ONLY, Ca.ALL_DAY},
                new String[]{ContentPolicy.value(ContentPolicy.NEVER), ContentPolicy.value(ContentPolicy.MORNING),
                        ContentPolicy.value(ContentPolicy.ALL)}, null);
        r.selected = policy;   // NEVER, MORNING and ALL are 0, 1 and 2, the same order as the options
        rows.add(r);
    }

    /** The screensaver music: on or off. The stream itself comes from the configuration, not from the remote. */
    void addMusicRow(Config conf) {
        Row r = new Row(Ca.MUSIC_SCREEN, "music_screen", new String[]{Ca.ON, Ca.OFF}, new String[]{"on", "off"}, null);
        r.selected = conf.musicScreen ? 0 : 1;
        rows.add(r);
    }

    /** A row of the panel: the setting on the first line and its value under it, like the system settings. */
    void addRowView(Row r) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(24), 0, dp(24), 0);
        TextView label = text(400, 16, INK);
        label.setText(r.label);
        row.addView(label, new LinearLayout.LayoutParams(-1, -2));
        TextView value = text(400, 14, INK_DIM);
        value.setText(r.isAction() ? "" : r.custom ? Ca.NEWS_SOURCE_CUSTOM : r.options[Math.min(r.selected, r.options.length - 1)]);
        value.setVisibility(r.isAction() ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, -2);
        vp.topMargin = dp(2);
        row.addView(value, vp);
        r.view = row;
        r.value = value;
        list.addView(row, new LinearLayout.LayoutParams(-1, dp(64)));
    }

    void openSystemSettings() {
        try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        catch (Exception ex) { Toast.makeText(this, Ca.NOT_AVAILABLE, Toast.LENGTH_SHORT).show(); }
    }

    void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); }

    static int indexOf(String[] values, String v) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(v)) return i;
        return -1;
    }

    // ---------------------------------------------------------------- values

    /** Writes one key to home.conf, keeping every other line, and shows the new value. */
    void persist(Row r, int index) {
        if (r.isAction()) return;
        File dir = getExternalFilesDir(null);
        if (dir == null) {
            Toast.makeText(this, Ca.CANT_SAVE, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String text = Config.read(dir);
            Config.write(dir, Config.setInText(text, r.key, r.values[index]));
        } catch (IOException e) {
            Toast.makeText(this, Ca.CANT_SAVE, Toast.LENGTH_SHORT).show();
            return;
        }
        r.selected = index;
        r.value.setText(r.options[index]);
    }

    void openPanel(Row r) {
        panelRow = r;
        panelIdx = r.selected;
        panelTitle.setText(r.label);
        panelList.removeAllViews();
        for (String o : r.options) {
            TextView t = text(400, 16, INK);
            t.setText(o);
            t.setPadding(dp(24), 0, dp(24), 0);
            panelList.addView(t, new LinearLayout.LayoutParams(-1, dp(56)));
        }
        scroll.setVisibility(View.GONE);
        panel.setVisibility(View.VISIBLE);
        applyPanelFocus();
    }

    void closePanel() {
        panel.setVisibility(View.GONE);
        scroll.setVisibility(View.VISIBLE);
        panelRow = null;
        applyFocus();
    }

    // ---------------------------------------------------------------- navigation

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean ours = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
                || isOk(k) || k == KeyEvent.KEYCODE_BACK;
        if (!ours) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) onNav(k);
        return true;
    }

    static boolean isOk(int k) {
        return k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    void onNav(int k) {
        final int U = KeyEvent.KEYCODE_DPAD_UP, D = KeyEvent.KEYCODE_DPAD_DOWN,
                L = KeyEvent.KEYCODE_DPAD_LEFT, R = KeyEvent.KEYCODE_DPAD_RIGHT, B = KeyEvent.KEYCODE_BACK;
        if (panelRow != null) {
            if (k == U) { if (panelIdx == 0) return; panelIdx--; }
            else if (k == D) { if (panelIdx >= panelRow.options.length - 1) return; panelIdx++; }
            else if (isOk(k)) { Row r = panelRow; int i = panelIdx; closePanel(); persist(r, i); return; }
            else if (k == B) { closePanel(); return; }
            else return;
            applyPanelFocus();
            return;
        }
        Row r = rows.get(focus);
        if (k == U) { if (focus == 0) return; focus--; }
        else if (k == D) { if (focus >= rows.size() - 1) return; focus++; }
        else if (k == L || k == R) {
            if (r.isAction()) return;
            if (r.custom) { toast(Ca.NEWS_CUSTOM_TIP); return; }
            int next = r.selected + (k == R ? 1 : -1);
            if (next < 0) next = r.values.length - 1;
            if (next >= r.values.length) next = 0;
            persist(r, next);
        }
        else if (isOk(k)) {
            if (r.isAction()) { r.action.run(); return; }
            if (r.custom) { toast(Ca.NEWS_CUSTOM_TIP); return; }
            openPanel(r);
            return;
        }
        else if (k == B) { finish(); return; }
        else return;
        applyFocus();
    }

    void applyFocus() {
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            r.view.setBackgroundColor(i == focus ? FOCUS_BG : Color.TRANSPARENT);
        }
        // The panel is shorter than the screen, so the list scrolls to keep the focused row in view.
        final View v = rows.get(focus).view;
        scroll.post(() -> {
            int y = SidePanel.scrollFor(v.getTop(), v.getBottom(), scroll.getScrollY(), scroll.getHeight());
            if (y != scroll.getScrollY()) scroll.smoothScrollTo(0, y);
        });
    }

    void applyPanelFocus() {
        for (int i = 0; i < panelList.getChildCount(); i++) {
            TextView t = (TextView) panelList.getChildAt(i);
            boolean on = i == panelIdx;
            t.setBackgroundColor(on ? FOCUS_BG : Color.TRANSPARENT);
            t.setTextColor(on ? Color.WHITE : INK);
        }
    }

    // ---------------------------------------------------------------- look

    TextView text(int weight, float sp, int color) {
        TextView t = new TextView(this);
        t.setTypeface(fonts.get(weight));
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return t;
    }

    int dp(float v) { return Ui.dp(v); }
}
