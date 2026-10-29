package io.github.mmarkimark.llartv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
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
 * D-pad: up/down move between the settings, left/right change the value of the focused one, OK
 * opens its list of values, Back closes that list and closes the screen.
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

    final List<Row> rows = new ArrayList<>();
    Ui.Fonts fonts;
    LinearLayout list, panel;
    TextView panelTitle;
    LinearLayout panelList;
    int focus = 0;
    int panelIdx = 0;
    Row panelRow;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        Ui.density = dm.density;
        fonts = new Ui.Fonts(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0B0D);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(500, 30, 0xFFF2F2F7);
        title.setText(Ca.SETTINGS_TITLE);
        column.addView(title, new LinearLayout.LayoutParams(-2, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(18), 0, dp(18));
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        column.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        cp.width = Math.round(dm.widthPixels * 0.52f);   // a fixed column, so the values line up on the right
        cp.leftMargin = Math.round(dm.widthPixels * 0.09f);
        cp.topMargin = Math.round(dm.heightPixels * 0.13f);
        root.addView(column, cp);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(28), dp(20), dp(28), dp(20));
        panel.setBackground(rounded(0xF0222226, dp(18)));
        panel.setVisibility(View.GONE);
        panelTitle = text(400, 19, 0xFFAEAEB2);
        panelList = new LinearLayout(this);
        panelList.setOrientation(LinearLayout.VERTICAL);
        panel.addView(panelTitle, new LinearLayout.LayoutParams(-1, -2));
        panel.addView(panelList, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        root.addView(panel, pp);

        setContentView(root);
        buildRows();
        applyFocus();
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
        rows.add(new Row(Ca.SYSTEM_SETTINGS, null, null, null, new Runnable() {
            @Override public void run() { openSystemSettings(); }
        }));
        for (Row r : rows) addRowView(r);
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

    void addRowView(Row r) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(22), 0, dp(22), 0);
        TextView label = text(400, 23, 0xFFF2F2F7);
        label.setText(r.label);
        row.addView(label, new LinearLayout.LayoutParams(0, dp(62), 1f));
        TextView value = text(500, 23, 0xFF8E8E93);
        value.setText(r.isAction() ? "" : r.options[r.selected]);
        value.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(value, new LinearLayout.LayoutParams(-2, dp(62)));
        r.view = row;
        r.value = value;
        list.addView(row, new LinearLayout.LayoutParams(-1, dp(64)));
    }

    void openSystemSettings() {
        try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        catch (Exception ex) { Toast.makeText(this, Ca.NOT_AVAILABLE, Toast.LENGTH_SHORT).show(); }
    }

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
            TextView t = text(500, 22, 0xFFF2F2F7);
            t.setText(o);
            t.setPadding(dp(12), 0, dp(12), 0);
            panelList.addView(t, new LinearLayout.LayoutParams(-1, dp(48)));
        }
        panel.setVisibility(View.VISIBLE);
        applyPanelFocus();
    }

    void closePanel() {
        panel.setVisibility(View.GONE);
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
            int next = r.selected + (k == R ? 1 : -1);
            if (next < 0) next = r.values.length - 1;
            if (next >= r.values.length) next = 0;
            persist(r, next);
        }
        else if (isOk(k)) {
            if (r.isAction()) { r.action.run(); return; }
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
            boolean on = i == focus;
            r.view.setBackground(on ? rounded(0x26FFFFFF, dp(12)) : null);
            r.value.setTextColor(on ? 0xFF8E8E93 : 0xFF636366);
        }
        Row r = rows.get(focus);
        if (r != null && r.view != null) r.view.requestFocus();
    }

    void applyPanelFocus() {
        for (int i = 0; i < panelList.getChildCount(); i++) {
            TextView t = (TextView) panelList.getChildAt(i);
            boolean on = i == panelIdx;
            t.setBackground(on ? rounded(0x33FFFFFF, dp(10)) : null);
            t.setTextColor(on ? 0xFFFFFFFF : 0xFFAEAEB2);
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

    GradientDrawable rounded(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    int dp(float v) { return Ui.dp(v); }
}
