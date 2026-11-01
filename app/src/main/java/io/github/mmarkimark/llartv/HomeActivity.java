package io.github.mmarkimark.llartv;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import static io.github.mmarkimark.llartv.Ui.dp;
import static io.github.mmarkimark.llartv.Ui.withAlpha;

/**
 * Apple-TV-style home screen. Focus is handled entirely here (no Android focus search),
 * so D-pad movement is deterministic: TOP bar, DOCK row, GRID rows, and the Inputs MENU.
 */
public class HomeActivity extends Activity {
    static final int TOP = 0, DOCK = 1, GRID = 2, MENU = 3;
    int COLS = 6;                       // grid columns = dock size (home.conf: columns=)
    Config conf;
    /** Live TV apps by maker, tried in order; otherwise whatever app handles "view TV channels". */
    static final String[] LIVE_TV_APPS = {
            "com.mediatek.wwtv.tvcenter",   // MediaTek-based sets (Xiaomi, many TCL/Philips/Sharp)
            "com.sony.dtv.tvx",             // Sony Bravia
            "com.google.android.tv",        // Google "Live Channels"
            "com.android.tv"};              // AOSP Live TV
    /** System helpers that expose a launcher icon but aren't apps you'd open. */
    static final java.util.Set<String> HIDDEN = new java.util.HashSet<>(Arrays.asList(
            "com.mstar.netflixobserver"));

    int W, H, pad, gap, tileW, tileH, bandH, heroH;
    FrameLayout root;
    ImageView wall;
    long wallStamp = -1;
    PageLayout page;
    TextView greeting, clock, date, hero;
    // the quote of the day under the date (#69): the text (at most two lines), then "— Author" on a line of its own
    TextView quoteText, quoteAuthor;
    static Quotes.Library quoteLibrary;   // the bundled quotes, each language read once per process (off the main thread)
    String quoteKey;                      // what the quote shows: language and day, or "off"; null before the first
    boolean quoteLoading;
    GradientDrawable glowShape;
    View glow;
    int glowColor = 0xFF8E8E93;
    ValueAnimator glowAnim, hlAnim;
    final View[] pills = new View[3];
    TextView wifiText;
    // night mode and the photo-tinted dock band
    TimeBands bands;
    GradientDrawable bandBg;
    final List<TextView> inks = new ArrayList<>();   // text views that turn cream at night (day colour in the tag)
    float nightNow, nightTarget = -1;
    ValueAnimator nightAnim;
    // morning panel (weather, news), top right under the pills
    LinearLayout panel, wxCard, newsCard, newsList;
    TextView wxTemp, wxLine, wxRange, wxCredit;
    TextView newsCredit;
    final List<TextView> cardHeads = new ArrayList<>();   // the card headings, renamed on a language change
    final List<Integer> cardHeadIds = new ArrayList<>();  // their texts (the view tags hold the night-mode colours)
    long lastWx;
    final NewsSchedule newsSchedule = new NewsSchedule();   // when the headlines have to be fetched again
    boolean wxBusy, newsBusy;
    boolean wxFailed, wallFailed, fixFailed;   // the last attempt failed: retried when the network comes back (#38)
    final Reconnect reconnect = new Reconnect();
    ConnectivityManager.NetworkCallback netCallback;
    // football fixtures card
    LinearLayout matchCard;
    TextView mLabel, mTitle, mComp, mWhen, mCountdown;
    List<Fixtures.Match> matches = new ArrayList<>();
    String teamId;
    long lastFetch;
    boolean fetching;
    long lastWallFetch;
    boolean wallFetching;
    LinearLayout dockRow;
    RecyclerView grid;
    GridLayoutManager glm;
    final AppAdapter adapter = new AppAdapter();
    HighlightView hl;
    LinearLayout menu;
    final List<View> menuRows = new ArrayList<>();
    final List<Runnable> menuActions = new ArrayList<>();
    int menuBack = TOP;                 // the region Back and a menu action return to

    final List<Ui.AppEntry> dockApps = new ArrayList<>();
    final List<Ui.AppEntry> gridApps = new ArrayList<>();
    List<String> installedByLabel = new ArrayList<>();   // every installed app by label, hidden ones included
    final List<TileView> dockTiles = new ArrayList<>();
    TileView focusedTile;
    final OkPress ok = new OkPress(ViewConfiguration.getLongPressTimeout());   // short OK launches, a hold opens the app menu
    String pendingPkg;                  // the app to focus again after a dock, hidden or order edit
    MoveMode moving;                    // the app being moved (#39), null outside move mode
    TextView moveHint;                  // says how move mode works while it is on
    Strings tx;                         // the texts of the interface language (home.conf "language")
    final TextView[] pillText = new TextView[3];   // the labels of the inputs, Wi-Fi and settings pills

    int region = DOCK, topIdx = 0, dockIdx = 0, gridIdx = 0, menuIdx = 0, minTop = 0;
    String restoreGridPkg;
    boolean loaded, restored;
    SharedPreferences prefs;
    Ui.Fonts fonts;
    final Handler ui = new Handler(Looper.getMainLooper());

    // ---------------------------------------------------------------- lifecycle

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        Ui.density = dm.density;
        W = dm.widthPixels; H = dm.heightPixels;
        conf = Config.load(this);
        tx = Strings.of(this, conf.language);
        bands = conf.bands();
        COLS = conf.columns;
        pad = dp(64); gap = dp(COLS <= 4 ? 28 : 22);
        tileW = (W - 2 * pad - (COLS - 1) * gap) / COLS;
        tileH = Math.round(tileW * 9f / 16f);
        int bandPad = dp(20), bandBottom = dp(36);
        bandH = tileH + 2 * bandPad;
        heroH = H - bandH - bandBottom;

        prefs = getSharedPreferences("home", MODE_PRIVATE);
        fonts = new Ui.Fonts(this);
        // minimal mode (MinimalMode): no quote, no team, no wallpaper, no cards; none of them is built or read
        if (!MinimalMode.on(conf) && quoteLibrary == null) quoteLibrary = new Quotes.Library(getApplicationContext().getAssets()::open);
        region = prefs.getInt("region", DOCK);
        if (region == MENU) region = TOP;
        topIdx = prefs.getInt("top", 0);
        dockIdx = prefs.getInt("dock", 0);
        restoreGridPkg = prefs.getString("gridPkg", null);
        teamId = MinimalMode.teamId(conf);   // empty in minimal mode: no cached fixtures parsed, no card, no fetch
        lastFetch = prefs.getLong("fixFetched", 0);
        lastWallFetch = prefs.getLong("wallFetched", 0);
        String cached = prefs.getString("fixJson", null);
        if (cached != null && !teamId.isEmpty()) matches = Fixtures.parse(cached, teamId);

        buildUi(bandBottom);
        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(pkgReceiver, pf);
        loadApps();
    }

    @Override protected void onResume() {
        super.onResume();
        Config before = conf;
        conf = Config.load(this);          // LlarTV's own settings may have changed while we were away
        // The tile sizes, the team and the minimal-mode views are set up once in onCreate: a change to them (in the
        // settings, a reset or scripts/config.sh) rebuilds Home.
        if (MinimalMode.homeNeedsRebuild(before, conf)) { recreate(); return; }
        bands = conf.bands();
        applyLanguage();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(sysReceiver, f);
        watchNetwork();
        tick();
        updateWifi();
        loadWallpaper();
        maybeFetchWallpaper();
        refreshMatchCard();
        maybeFetchFixtures();
        updateInputsPill();
        if (appsChanged(before, conf)) loadApps();   // dock, hidden apps or grid order changed elsewhere
        else if (loaded) afterLayout(() -> applyFocus(false));
    }

    @Override protected void onPause() {
        super.onPause();
        cancelMove();   // leaving Home in move mode saves nothing (#39)
        try { unregisterReceiver(sysReceiver); } catch (Exception ignored) {}
        unwatchNetwork();
        saveFocus();
        nightTarget = -1;   // on return, show the right mode at once instead of fading
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) {}
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: back to the dock, dropping an unsaved move (#39).
        cancelMove();
        if (menu.getVisibility() == View.VISIBLE) { menu.setVisibility(View.GONE); region = menuBack; }
        if (loaded && !dockApps.isEmpty() && region != MENU) { region = DOCK; applyFocus(true); }
    }

    @Override public void onBackPressed() { /* launcher: never finish */ }

    final BroadcastReceiver sysReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (a == null) return;
            if (a.startsWith("android.intent.action.TIME")) tick(); else updateWifi();
        }
    };

    // ---------------------------------------------------------------- reconnect (#38)

    /** While Home is visible, follows the default network's validated Internet capability (no polling). */
    void watchNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null || netCallback != null) return;
        netCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities caps) {
                boolean usable = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                ui.post(() -> onNetwork(usable));
            }
            @Override public void onLost(Network n) { ui.post(() -> onNetwork(false)); }
        };
        try { cm.registerDefaultNetworkCallback(netCallback); } catch (RuntimeException e) { netCallback = null; }
    }

    void unwatchNetwork() {
        reconnect.reset();
        if (netCallback == null) return;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        try { if (cm != null) cm.unregisterNetworkCallback(netCallback); } catch (RuntimeException ignored) {}
        netCallback = null;
    }

    /** Usable Internet came back: only content whose last attempt failed is made due; the usual rules fetch it. */
    void onNetwork(boolean usable) {
        if (netCallback == null || !reconnect.regained(usable, System.currentTimeMillis())) return;
        if (wxFailed) lastWx = 0;
        if (wallFailed) lastWallFetch = 0;
        if (fixFailed) lastFetch = 0;
        newsSchedule.retryFailed();
        if (matchCard != null) maybeFetchFixtures();
        maybeFetchWallpaper();
        refreshMorning();   // weather and news, each only when a surface still wants it
    }

    final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (moving != null) endMove();   // the rows change under the move: drop its unsaved steps (#39)
            loadApps();
        }
    };

    // ---------------------------------------------------------------- layout

    void buildUi(int bandBottom) {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false);

        boolean minimal = MinimalMode.on(conf);   // minimal mode: no wallpaper view, no quote, no cards (black Home)
        if (!minimal) {
            wall = new ImageView(this);
            wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
            wall.setAlpha(0.55f);
            root.addView(wall, new FrameLayout.LayoutParams(-1, -1));
        }

        page = new PageLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        root.addView(page, new FrameLayout.LayoutParams(-1, -2));

        // --- hero section: clock + pills + focused dock app name with ambient glow
        FrameLayout heroSec = new FrameLayout(this);
        heroSec.setClipChildren(false);
        page.addView(heroSec, new LinearLayout.LayoutParams(-1, heroH));

        glowShape = new GradientDrawable();
        glowShape.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        // radius < half the view's height so the gradient fades out fully inside its bounds (no hard edge)
        glowShape.setGradientRadius(dp(340));
        setGlow(glowColor);
        glow = new View(this);
        glow.setBackground(glowShape);
        if (!MinimalMode.focusGlow(conf)) glow.setVisibility(View.GONE);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(dp(1100), dp(720), Gravity.TOP | Gravity.START);
        glp.leftMargin = pad + dp(170) - dp(550);
        glp.topMargin = heroH - dp(60) - dp(360);
        heroSec.addView(glow, glp);

        hero = text(200, 58, 0xF2FFFFFF);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        hp.leftMargin = pad;
        hp.bottomMargin = dp(16);
        heroSec.addView(hero, hp);

        LinearLayout tl = new LinearLayout(this);
        tl.setOrientation(LinearLayout.VERTICAL);
        greeting = text(300, 22, 0xFF8E8E93);
        clock = text(150, 86, 0xFFFFFFFF);
        clock.setIncludeFontPadding(false);
        clock.setLetterSpacing(-0.01f);
        date = text(300, 21, 0xFFAEAEB2);
        // WRAP_CONTENT (not the vertical default MATCH_PARENT) so a wider time ("9:59" -> "10:00") re-measures the column
        tl.addView(greeting, new LinearLayout.LayoutParams(-2, -2));
        tl.addView(clock, new LinearLayout.LayoutParams(-2, -2));
        tl.addView(date, new LinearLayout.LayoutParams(-2, -2));
        if (!minimal) {
            // the quote: smaller and dimmer than the date, kept clear of the cards on the right (380 dp wide)
            int quoteW = W - 2 * pad - dp(380) - dp(48);
            quoteText = text(300, 16, 0xFF8E8E93);
            quoteText.setSingleLine(false);
            quoteText.setMaxLines(2);
            quoteText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            quoteText.setMaxWidth(quoteW);
            quoteText.setLineSpacing(dp(2), 1f);
            quoteText.setVisibility(View.GONE);
            quoteAuthor = text(300, 13, 0xFF8E8E93);
            quoteAuthor.setLetterSpacing(0.02f);
            quoteAuthor.setEllipsize(android.text.TextUtils.TruncateAt.END);
            quoteAuthor.setMaxWidth(quoteW);
            quoteAuthor.setVisibility(View.GONE);
            LinearLayout.LayoutParams qtp = new LinearLayout.LayoutParams(-2, -2);
            qtp.topMargin = dp(14);
            tl.addView(quoteText, qtp);
            LinearLayout.LayoutParams qap = new LinearLayout.LayoutParams(-2, -2);
            qap.topMargin = dp(4);
            tl.addView(quoteAuthor, qap);
        }
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tlp.leftMargin = pad;
        tlp.topMargin = dp(40);
        heroSec.addView(tl, tlp);

        LinearLayout pillRow = new LinearLayout(this);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        pills[0] = pill(R.drawable.ic_input, tx.get(R.string.inputs), pillText, 0);
        pills[1] = pill(R.drawable.ic_wifi, tx.get(R.string.wifi), pillText, 1);
        wifiText = pillText[1];
        pills[2] = pill(R.drawable.ic_settings, tx.get(R.string.settings), pillText, 2);
        for (int i = 0; i < 3; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(42));
            if (i > 0) lp.leftMargin = dp(12);
            pillRow.addView(pills[i], lp);
        }
        FrameLayout.LayoutParams prp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        prp.rightMargin = pad;
        prp.topMargin = dp(52);
        heroSec.addView(pillRow, prp);

        if (!minimal) {   // minimal mode builds neither the football card nor the morning panel
            // --- football card (top right, under the pills): next match, date/time, countdown
            matchCard = new LinearLayout(this);
            matchCard.setOrientation(LinearLayout.VERTICAL);
            matchCard.setPadding(dp(22), dp(16), dp(22), dp(18));
            GradientDrawable mcBg = new GradientDrawable();
            mcBg.setColor(0x14FFFFFF);
            mcBg.setCornerRadius(dp(22));
            mcBg.setStroke(dp(1), 0x26FFFFFF);
            matchCard.setBackground(mcBg);
            LinearLayout labelRow = new LinearLayout(this);
            labelRow.setOrientation(LinearLayout.HORIZONTAL);
            labelRow.setGravity(Gravity.CENTER_VERTICAL);
            View dot = new View(this);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(getColor(R.color.fixture_accent));
            dot.setBackground(dotBg);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(9), dp(9));
            dlp.rightMargin = dp(9);
            labelRow.addView(dot, dlp);
            mLabel = text(500, 13, 0xFFAEAEB2);
            mLabel.setLetterSpacing(0.08f);
            mLabel.setText(matchLabel());
            labelRow.addView(mLabel);
            matchCard.addView(labelRow);
            mTitle = text(300, 30, 0xFFFFFFFF);
            LinearLayout.LayoutParams tlp2 = new LinearLayout.LayoutParams(-2, -2);
            tlp2.topMargin = dp(8);
            matchCard.addView(mTitle, tlp2);
            mComp = text(300, 16, 0xFFAEAEB2);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.topMargin = dp(4);
            matchCard.addView(mComp, clp);
            LinearLayout whenRow = new LinearLayout(this);
            whenRow.setOrientation(LinearLayout.HORIZONTAL);
            whenRow.setGravity(Gravity.BOTTOM);
            mWhen = text(400, 21, 0xFFFFFFFF);
            mCountdown = text(300, 16, 0xFF8E8E93);
            whenRow.addView(mWhen);
            LinearLayout.LayoutParams cdp = new LinearLayout.LayoutParams(-2, -2);
            cdp.leftMargin = dp(12);
            whenRow.addView(mCountdown, cdp);
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-2, -2);
            wlp.topMargin = dp(12);
            matchCard.addView(whenRow, wlp);
            matchCard.setVisibility(View.GONE);
            FrameLayout.LayoutParams mcp = new FrameLayout.LayoutParams(dp(380), -2, Gravity.TOP | Gravity.END);
            mcp.rightMargin = pad;
            mcp.topMargin = dp(52) + dp(42) + dp(26);
            heroSec.addView(matchCard, mcp);

            // --- morning panel (same place as the football card, which it replaces from 08:00 to 11:00)
            panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setVisibility(View.GONE);
            wxCard = card(R.string.weather);
            LinearLayout wxRow = new LinearLayout(this);
            wxRow.setOrientation(LinearLayout.HORIZONTAL);
            wxRow.setGravity(Gravity.CENTER_VERTICAL);
            wxTemp = text(200, 32, 0xFFFFFFFF);
            wxTemp.setIncludeFontPadding(false);
            LinearLayout wxText = new LinearLayout(this);
            wxText.setOrientation(LinearLayout.VERTICAL);
            wxLine = text(400, 15, 0xFFDCD9D2);
            wxRange = text(300, 12, 0xFFA9A6A0);
            wxText.addView(wxLine);
            wxText.addView(wxRange);
            wxRow.addView(wxTemp);
            LinearLayout.LayoutParams wtp = new LinearLayout.LayoutParams(-2, -2);
            wtp.leftMargin = dp(14);
            wxRow.addView(wxText, wtp);
            wxCard.addView(wxRow);
            wxCredit = text(300, 10, 0xFFA9A6A0);   // the place and the source, like the headlines' credit (#70)
            wxCredit.setLetterSpacing(0.02f);
            LinearLayout.LayoutParams wcp = new LinearLayout.LayoutParams(-2, -2);
            wcp.topMargin = dp(6);
            wxCard.addView(wxCredit, wcp);
            newsCard = card(R.string.news);
            newsList = new LinearLayout(this);
            newsList.setOrientation(LinearLayout.VERTICAL);
            newsCard.addView(newsList);
            newsCredit = text(300, 10, 0xFFA9A6A0);
            newsCredit.setLetterSpacing(0.02f);
            LinearLayout.LayoutParams ncp = new LinearLayout.LayoutParams(-2, -2);
            ncp.topMargin = dp(6);
            newsCard.addView(newsCredit, ncp);
            for (View c : new View[]{wxCard, newsCard}) {
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
                cp.bottomMargin = dp(8);
                panel.addView(c, cp);
            }
            heroSec.addView(panel, new FrameLayout.LayoutParams(mcp));
        }

        // --- dock band
        FrameLayout band = new FrameLayout(this);
        bandBg = new GradientDrawable();
        bandBg.setColor(Look.DEFAULT_BAND);
        bandBg.setCornerRadius(dp(30));
        bandBg.setStroke(dp(1), 0x0FFFFFFF);
        band.setBackground(bandBg);
        band.setClipChildren(false);
        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setGravity(Gravity.CENTER_VERTICAL);
        dockRow.setPadding(pad - dp(40), 0, 0, 0);
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);   // the first tile scales into the row's left padding
        band.addView(dockRow, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, bandH);
        blp.leftMargin = blp.rightMargin = dp(40);
        blp.bottomMargin = bandBottom;
        page.addView(band, blp);

        // --- app grid (all rows laid out; the page itself translates up/down)
        grid = new RecyclerView(this);
        glm = new GridLayoutManager(this, COLS) {
            @Override public boolean canScrollVertically() { return false; }
        };
        grid.setLayoutManager(glm);
        grid.setItemAnimator(null);
        grid.setNestedScrollingEnabled(false);
        grid.setFocusable(false);
        grid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(pad - gap / 2, 0, pad - gap / 2, dp(90));
        grid.setAdapter(adapter);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2);
        gp.topMargin = dp(14);
        page.addView(grid, gp);

        // --- overlays: inputs menu, then the glass highlight on top of everything
        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable mb = new GradientDrawable();
        mb.setColor(0xF01C1C1F);
        mb.setCornerRadius(dp(20));
        mb.setStroke(dp(1), 0x33FFFFFF);
        menu.setBackground(mb);
        menu.setPadding(dp(8), dp(8), dp(8), dp(8));
        menu.setVisibility(View.GONE);
        root.addView(menu, new FrameLayout.LayoutParams(dp(260), -2));

        hl = new HighlightView(this);
        root.addView(hl, new FrameLayout.LayoutParams(-1, -1));

        // --- move mode hint (#39): only while an app is being moved, above the highlight. It sits centred just
        // above the dock band, where the (then hidden) hero label is, so it never covers the top-bar pills (#57).
        moveHint = text(500, 16, 0xFFFFFFFF);
        GradientDrawable hb = new GradientDrawable();
        hb.setColor(0xF01C1C1F);
        hb.setCornerRadius(dp(18));
        hb.setStroke(dp(2), HighlightView.MOVING_STROKE);   // same warm accent as the moving tile's outline
        moveHint.setBackground(hb);
        moveHint.setPadding(dp(18), dp(8), dp(18), dp(8));
        moveHint.setVisibility(View.GONE);
        root.addView(moveHint, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        final View heroSecView = heroSec;
        root.getViewTreeObserver().addOnPreDrawListener(() -> {   // follows the page while it scrolls
            if (moving != null) {
                float anchor = page.getTop() + heroSecView.getBottom() - dp(20) - moveHint.getHeight();
                moveHint.setTranslationY(MoveMode.hintTop(anchor, page.getTranslationY(), dp(24)));
            }
            return true;
        });

        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        setContentView(root);
        root.requestFocus();
    }

    TextView text(int weight, float sp, int color) {
        TextView t = new TextView(this);
        t.setTypeface(fonts.get(weight));
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setTag(color);
        inks.add(t);
        return t;
    }

    View pill(int icon, String label, TextView[] out, int slot) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(dp(16), 0, dp(18), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1AFFFFFF);
        bg.setCornerRadius(dp(21));
        bg.setStroke(dp(1), 0x26FFFFFF);
        p.setBackground(bg);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(0xFFE5E5EA);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(19), dp(19));
        ip.rightMargin = dp(8);
        p.addView(iv, ip);
        TextView t = text(400, 16, 0xFFE5E5EA);
        t.setText(label);
        t.setMaxWidth(dp(220));
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        p.addView(t);
        out[slot] = t;
        return p;
    }

    /** Vertical container measured with unlimited height so the grid lays out every row. */
    static final class PageLayout extends LinearLayout {
        PageLayout(Context c) { super(c); }
        @Override protected void onMeasure(int w, int h) {
            super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        }
    }

    // ---------------------------------------------------------------- apps

    void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            LinkedHashMap<String, Ui.AppEntry> map = new LinkedHashMap<>();
            for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
                for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                    ActivityInfo ai = ri.activityInfo;
                    String p = ai.packageName;
                    if (p.equals(getPackageName()) || map.containsKey(p) || HIDDEN.contains(p)) continue;
                    // Built-in apps that only have a phone-style entry (no TV entry) are system helpers
                    // (button handlers, test tools...), not apps you'd open from the sofa. User-installed ones stay.
                    if (Intent.CATEGORY_LAUNCHER.equals(cat) && (ai.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                    Ui.AppEntry e = new Ui.AppEntry();
                    e.pkg = p;
                    e.label = String.valueOf(ri.loadLabel(pm));
                    e.launch = new Intent(Intent.ACTION_MAIN).addCategory(cat)
                            .setComponent(new ComponentName(p, ai.name))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    Drawable bn = ai.loadBanner(pm);
                    if (bn == null) bn = ai.applicationInfo.loadBanner(pm);
                    e.banner = bn;
                    if (bn == null) e.icon = ri.loadIcon(pm);
                    map.put(p, e);
                }
            }
            List<Ui.AppEntry> all = new ArrayList<>(map.values());
            Collections.sort(all, (a, b) -> a.label.compareToIgnoreCase(b.label));
            List<String> byLabel = new ArrayList<>();
            for (Ui.AppEntry e : all) byLabel.add(e.pkg);
            // A dock= list of one's own shows exactly its apps, however short; the built-in list (and dock.txt)
            // is still filled from the alphabetically sorted grid, as it always has.
            // The grid order (order=) only rearranges what is left for the grid; it never changes the dock.
            HomeApps.Composed c = HomeApps.compose(readDockConfig(), byLabel, conf.hidden, !conf.dockSet, COLS, conf.order);
            List<Ui.AppEntry> d = new ArrayList<>(), g = new ArrayList<>();
            for (String p : c.dock) d.add(map.get(p));
            for (String p : c.grid) g.add(map.get(p));
            if (MinimalMode.focusGlow(conf))   // the glow colour is sampled only when the glow is shown
                for (Ui.AppEntry e : d) e.color = Ui.sampleColor(e.banner != null ? e.banner : e.icon);
            ui.post(() -> { installedByLabel = byLabel; bindApps(d, g); });
        }).start();
    }

    /** True when the dock, the hidden apps or the grid order differ, so the apps must be placed again. */
    static boolean appsChanged(Config a, Config b) {
        return a.dockSet != b.dockSet || !a.dock.equals(b.dock) || !new ArrayList<>(a.hidden).equals(new ArrayList<>(b.hidden))
                || !a.order.equals(b.order);
    }

    /** Dock order: an explicit dock= list in home.conf (kept even when empty), else dock.txt in the app's files
     *  dir if present (no rebuild needed), else res/values/dock.xml. */
    List<String> readDockConfig() {
        if (conf.dockSet) return conf.dock;
        File dir = ConfigLocation.dir(this);
        File f = dir == null ? null : new File(dir, ConfigLocation.DOCK);
        if (f != null && f.exists()) {
            List<String> out = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String l;
                while ((l = r.readLine()) != null) { l = l.trim(); if (!l.isEmpty() && !l.startsWith("#")) out.add(l); }
            } catch (Exception ignored) {}
            if (!out.isEmpty()) return out;
        }
        return Arrays.asList(getResources().getStringArray(R.array.dock_packages));
    }

    void bindApps(List<Ui.AppEntry> d, List<Ui.AppEntry> g) {
        String keepGrid = loaded && region == GRID && gridIdx < gridApps.size() ? gridApps.get(gridIdx).pkg : null;
        dockApps.clear(); dockApps.addAll(d);
        gridApps.clear(); gridApps.addAll(g);
        if (dockTiles.size() == dockApps.size()) {
            // Same number of tiles (every move-mode step, most reloads): rebind them instead of building new views.
            // The margins depend only on the count; applyFocus sets the focused look again after focusedTile resets.
            for (int i = 0; i < dockTiles.size(); i++) {
                dockTiles.get(i).setFocusedLook(false, false);
                dockTiles.get(i).bind(dockApps.get(i));
            }
        } else {
            dockRow.removeAllViews();
            dockTiles.clear();
            for (int i = 0; i < dockApps.size(); i++) {
                TileView t = new TileView(this, fonts, tileW, tileH, false);
                t.bind(dockApps.get(i));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, tileH);
                if (i < dockApps.size() - 1) lp.rightMargin = gap;
                dockRow.addView(t, lp);
                dockTiles.add(t);
            }
        }
        focusedTile = null;
        adapter.notifyDataSetChanged();

        if (!restored) { keepGrid = restoreGridPkg; restored = true; }
        if (keepGrid != null) for (int i = 0; i < gridApps.size(); i++) if (gridApps.get(i).pkg.equals(keepGrid)) gridIdx = i;
        dockIdx = clamp(dockIdx, dockApps.size());
        gridIdx = clamp(gridIdx, gridApps.size());
        if (region == DOCK && dockApps.isEmpty()) region = gridApps.isEmpty() ? TOP : GRID;
        if (region == GRID && gridApps.isEmpty()) region = DOCK;
        if (pendingPkg != null) {   // after an edit, the focus follows the app: a neighbour when it left
            String p = pendingPkg;
            pendingPkg = null;
            for (int i = 0; i < dockApps.size(); i++) if (dockApps.get(i).pkg.equals(p)) { region = DOCK; dockIdx = i; break; }
            if (region != DOCK) for (int i = 0; i < gridApps.size(); i++) if (gridApps.get(i).pkg.equals(p)) { region = GRID; gridIdx = i; break; }
        }
        loaded = true;
        if (!dockApps.isEmpty()) { Ui.AppEntry e = dockApps.get(dockIdx); hero.setText(e.label); setGlow(e.color); glowColor = e.color; }
        afterLayout(() -> { applyFocus(false); ui.postDelayed(() -> applyFocus(false), 400); });
    }

    static int clamp(int i, int n) { return n == 0 ? 0 : Math.max(0, Math.min(i, n - 1)); }

    final class AppAdapter extends RecyclerView.Adapter<AppAdapter.VH> {
        final class VH extends RecyclerView.ViewHolder {
            final TileView tile;
            VH(TileView t) { super(t); tile = t; }
        }
        @Override public VH onCreateViewHolder(ViewGroup parent, int type) {
            TileView t = new TileView(HomeActivity.this, fonts, tileW, tileH, true);
            t.setPadding(gap / 2, 0, gap / 2, dp(6));
            t.setLayoutParams(new RecyclerView.LayoutParams(tileW + gap, -2));
            return new VH(t);
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            h.tile.bind(gridApps.get(pos));
            boolean f = region == GRID && pos == gridIdx;
            h.tile.setFocusedLook(f, false);
            if (f) focusedTile = h.tile;
        }
        @Override public int getItemCount() { return gridApps.size(); }
    }

    // ---------------------------------------------------------------- focus & keys

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean ours = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
                || isOk(k) || k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_MENU;
        if (!ours) return super.dispatchKeyEvent(e);
        int act = e.getAction();
        if (moving != null) {   // move mode: every remote key belongs to the move until OK or Back ends it
            if (act == KeyEvent.ACTION_DOWN && (e.getRepeatCount() == 0 || !isOk(k))) onMoveKey(k);
            return true;
        }
        if (isOk(k)) {
            // In the dock and the grid, OK acts on the release: a short press launches, a hold opens the
            // app's menu. In the top bar and a menu it still acts on the press, as it always has.
            if (loaded && (region == DOCK || region == GRID)) {
                if (act == KeyEvent.ACTION_DOWN) {
                    if (e.getRepeatCount() == 0) ok.down(e.getEventTime());
                    else if (ok.repeat(e.getEventTime()) == OkPress.HOLD) openAppMenu();
                } else if (act == KeyEvent.ACTION_UP) {
                    int r = ok.up(e.getEventTime());
                    Ui.AppEntry cur = currentApp();
                    if (r == OkPress.CLICK && cur != null) launch(cur);
                    else if (r == OkPress.HOLD) openAppMenu();   // no repeats arrived in time: fire on release
                }
            } else if (act == KeyEvent.ACTION_DOWN && loaded && e.getRepeatCount() == 0) onNav(k);
            return true;
        }
        if (act == KeyEvent.ACTION_DOWN && loaded) {
            ok.cancel();   // another key took over: a pending OK must not click when it goes up
            onNav(k);
        }
        return true;
    }

    static boolean isOk(int k) {
        return k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    void onNav(int k) {
        final int L = KeyEvent.KEYCODE_DPAD_LEFT, R = KeyEvent.KEYCODE_DPAD_RIGHT,
                U = KeyEvent.KEYCODE_DPAD_UP, D = KeyEvent.KEYCODE_DPAD_DOWN, B = KeyEvent.KEYCODE_BACK;
        int n = gridApps.size();
        switch (region) {
            case TOP:
                if (k == L) { if (topIdx <= minTop) return; topIdx--; }
                else if (k == R) { if (topIdx == 2) return; topIdx++; }
                else if (k == D || k == B) { if (!dockApps.isEmpty()) region = DOCK; else if (n > 0) region = GRID; else return; }
                else if (isOk(k)) { pillAction(topIdx); return; }
                else return;
                break;
            case DOCK:
                if (k == L) { if (dockIdx == 0) return; dockIdx--; }
                else if (k == R) { if (dockIdx >= dockApps.size() - 1) return; dockIdx++; }
                else if (k == U) region = TOP;
                else if (k == D) { if (n == 0) return; region = GRID; gridIdx = Math.min(dockIdx, n - 1); }
                else if (k == KeyEvent.KEYCODE_MENU) { appInfo(dockApps.get(dockIdx)); return; }
                else return;
                break;
            case GRID: {
                int row = gridIdx / COLS, col = gridIdx % COLS, rows = (n + COLS - 1) / COLS;
                if (k == L) { if (col == 0) return; gridIdx--; }
                else if (k == R) { if (col == COLS - 1 || gridIdx + 1 >= n) return; gridIdx++; }
                else if (k == U) {
                    if (row > 0) gridIdx -= COLS;
                    else if (!dockApps.isEmpty()) { region = DOCK; dockIdx = Math.min(col, dockApps.size() - 1); }
                    else region = TOP;
                }
                else if (k == D) { if (row >= rows - 1) return; gridIdx = Math.min(gridIdx + COLS, n - 1); }
                else if (k == B) { if (dockApps.isEmpty()) return; region = DOCK; }
                else if (k == KeyEvent.KEYCODE_MENU) { appInfo(gridApps.get(gridIdx)); return; }
                else return;
                break;
            }
            case MENU:
                if (k == U) { if (menuIdx == 0) { closeMenu(); return; } menuIdx--; }
                else if (k == D) { if (menuIdx >= menuRows.size() - 1) return; menuIdx++; }
                else if (isOk(k)) { Runnable r = menuActions.get(menuIdx); closeMenu(); r.run(); return; }
                else if (k == B || k == L || k == R) { closeMenu(); return; }
                else return;
                break;
        }
        applyFocus(true);
    }

    /** Apply the current focus state: tile scale, hero text/glow, page scroll, highlight. */
    void applyFocus(boolean animate) {
        TileView t = null;
        if (region == DOCK && dockIdx < dockTiles.size()) t = dockTiles.get(dockIdx);
        else if (region == GRID) t = gridTile(gridIdx);
        if (t != focusedTile && focusedTile != null) focusedTile.setFocusedLook(false, animate);
        if (t != null) t.setFocusedLook(true, animate);
        focusedTile = t;
        updateHero(animate);
        scrollPage(animate);
        moveHighlight(animate);
        // The focus is remembered in onPause, not here: this runs on every D-pad step (#56).
    }

    TileView gridTile(int pos) {
        View v = glm.findViewByPosition(pos);
        return v instanceof TileView ? (TileView) v : null;
    }

    View targetView() {
        switch (region) {
            case TOP: return pills[topIdx];
            case DOCK: return dockIdx < dockTiles.size() ? dockTiles.get(dockIdx).card : null;
            case GRID: { TileView t = gridTile(gridIdx); return t == null ? null : t.card; }
            case MENU: return menuIdx < menuRows.size() ? menuRows.get(menuIdx) : null;
        }
        return null;
    }

    /** Bounds of v in root coordinates, at the given scale around its centre (follows page translation). */
    RectF rectOf(View v, float scale) {
        int[] rl = new int[2], pl = new int[2];
        root.getLocationInWindow(rl);
        ((View) v.getParent()).getLocationInWindow(pl);
        float l = pl[0] + v.getLeft() - rl[0], t = pl[1] + v.getTop() - rl[1];
        float w = v.getWidth() * scale, h = v.getHeight() * scale;
        float cx = l + v.getWidth() / 2f, cy = t + v.getHeight() / 2f;
        return new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
    }

    void moveHighlight(boolean animate) {
        final View tv = targetView();
        if (tv == null) { hl.hide(); return; }
        final float sc = (region == DOCK || region == GRID) ? TileView.FOCUS_SCALE : 1f;
        final float rad = region == TOP ? tv.getHeight() / 2f : region == MENU ? dp(12) : dp(14) * sc;
        if (hlAnim != null) hlAnim.cancel();
        if (!animate || !hl.shown || hl.rect.isEmpty()) { hl.set(rectOf(tv, sc), rad); return; }
        final RectF from = new RectF(hl.rect);
        final float fromR = hl.radius;
        hlAnim = ValueAnimator.ofFloat(0f, 1f);
        hlAnim.setDuration(360);
        hlAnim.setInterpolator(new OvershootInterpolator(1.25f));   // the "spring"
        hlAnim.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            RectF to = rectOf(tv, sc);
            hl.set(new RectF(lerp(from.left, to.left, f), lerp(from.top, to.top, f),
                    lerp(from.right, to.right, f), lerp(from.bottom, to.bottom, f)), lerp(fromR, rad, Math.min(1f, f)));
        });
        hlAnim.start();
    }

    static float lerp(float a, float b, float f) { return a + (b - a) * f; }

    void scrollPage(boolean animate) {
        float target = 0;
        if (region == GRID) {
            View rowView = glm.findViewByPosition((gridIdx / COLS) * COLS);
            if (rowView != null) {
                float rowTop = grid.getTop() + rowView.getTop();
                target = -(rowTop - dp(110));
                float min = -(page.getHeight() - H);
                if (target < min) target = min;
                if (target > 0) target = 0;
            }
        }
        page.animate().cancel();
        if (animate) page.animate().translationY(target).setDuration(320).setInterpolator(new DecelerateInterpolator(2f)).start();
        else page.setTranslationY(target);
    }

    void updateHero(boolean animate) {
        float g = region == DOCK ? 1f : 0.4f, a = MoveMode.heroAlpha(moving != null, region == DOCK);
        if (dockApps.isEmpty()) { hero.setText(""); return; }
        final Ui.AppEntry e = dockApps.get(dockIdx);
        if (!e.label.contentEquals(hero.getText())) {
            hero.animate().cancel();
            if (animate) {
                hero.animate().alpha(0f).setDuration(90).withEndAction(() -> {
                    hero.setText(e.label);
                    hero.animate().alpha(a).setDuration(180).start();
                }).start();
            } else { hero.setText(e.label); hero.setAlpha(a); }
            animateGlow(e.color, animate);
        } else {
            if (animate) hero.animate().alpha(a).setDuration(180).start(); else hero.setAlpha(a);
        }
        glow.animate().alpha(g).setDuration(animate ? 220 : 0).start();
    }

    void animateGlow(int to, boolean animate) {
        if (glowAnim != null) glowAnim.cancel();
        if (!animate) { glowColor = to; setGlow(to); return; }
        glowAnim = ValueAnimator.ofObject(new ArgbEvaluator(), glowColor, to);
        glowAnim.setDuration(380);
        glowAnim.addUpdateListener(an -> { glowColor = (int) an.getAnimatedValue(); setGlow(glowColor); });
        glowAnim.start();
    }

    void setGlow(int c) {
        if (!MinimalMode.focusGlow(conf)) {   // minimal mode: no glow and no coloured shadow on the app name
            if (glow != null) glow.setVisibility(View.GONE);   // buildUi calls this before the glow view exists
            if (hero != null) hero.setShadowLayer(0, 0, 0, 0);
            return;
        }
        if (glow != null) glow.setVisibility(View.VISIBLE);
        glowShape.setColors(new int[]{withAlpha(c, 0x52), withAlpha(c, 0x1A), 0x00000000});
        if (hero != null) hero.setShadowLayer(dp(26), 0, 0, withAlpha(c, 0xB0));
    }

    void saveFocus() {
        if (!loaded) return;
        prefs.edit().putInt("region", region == MENU ? menuBack : region).putInt("top", topIdx).putInt("dock", dockIdx)
                .putString("gridPkg", gridIdx < gridApps.size() ? gridApps.get(gridIdx).pkg : null).apply();
    }

    void afterLayout(Runnable r) {
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                r.run();
            }
        });
        root.requestLayout();
    }

    // ---------------------------------------------------------------- actions

    void launch(Ui.AppEntry e) {
        try { startActivity(e.launch); }
        catch (Exception ex) { Toast.makeText(this, tx.get(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show(); }
    }

    void appInfo(Ui.AppEntry e) {
        safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg)));
    }

    void safeStart(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ex) { Toast.makeText(this, tx.get(R.string.not_available), Toast.LENGTH_SHORT).show(); }
    }

    void pillAction(int i) {
        if (i == 0) openMenu();
        else if (i == 1) safeStart(new Intent(Settings.ACTION_WIFI_SETTINGS));
        else {
            try { startActivity(new Intent(this, SettingsActivity.class)); }
            catch (Exception ex) { Toast.makeText(this, tx.get(R.string.not_available), Toast.LENGTH_SHORT).show(); }
        }
    }

    /** Physical inputs (HDMI, AV, ...) on this device. Empty on boxes and sticks, which are themselves an HDMI source. */
    List<TvInputInfo> hardwareInputs() {
        List<TvInputInfo> inputs = new ArrayList<>();
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) for (TvInputInfo in : tim.getTvInputList()) {
                if (in.getParentId() != null) continue;               // skip CEC child devices
                int t = in.getType();
                if (t == TvInputInfo.TYPE_HDMI || t == TvInputInfo.TYPE_COMPOSITE || t == TvInputInfo.TYPE_COMPONENT
                        || t == TvInputInfo.TYPE_SVIDEO || t == TvInputInfo.TYPE_SCART || t == TvInputInfo.TYPE_VGA
                        || t == TvInputInfo.TYPE_DVI) inputs.add(in);
            }
        } catch (Exception ignored) {}
        return inputs;
    }

    /** Show the Inputs pill only when there is something to switch to (hides it on Mi Box, TV Stick, Shield...). */
    void updateInputsPill() {
        boolean has = !hardwareInputs().isEmpty() || liveTvIntent() != null;
        pills[0].setVisibility(has ? View.VISIBLE : View.GONE);
        minTop = has ? 0 : 1;
        if (topIdx < minTop) topIdx = minTop;
    }

    void openMenu() {
        menu.removeAllViews();
        menuRows.clear();
        menuActions.clear();
        List<TvInputInfo> inputs = hardwareInputs();
        Collections.sort(inputs, (a, b) -> {
            int ta = a.getType() == TvInputInfo.TYPE_HDMI ? 0 : 1, tb = b.getType() == TvInputInfo.TYPE_HDMI ? 0 : 1;
            if (ta != tb) return ta - tb;
            return inputLabel(a).compareToIgnoreCase(inputLabel(b));
        });
        for (TvInputInfo in : inputs) {
            String label = inputLabel(in);
            if (in.getType() == TvInputInfo.TYPE_COMPOSITE && !label.toUpperCase(Locale.ROOT).contains("AV")) label = label + " (AV)";
            final String id = in.getId();
            addMenuRow(label, () -> switchInput(id));
        }
        if (liveTvIntent() != null) addMenuRow(tx.get(R.string.live_tv), this::openLiveTv);

        showMenu(rectOf(pills[0], 1f), TOP);
    }

    String inputLabel(TvInputInfo in) {
        CharSequence c = in.loadCustomLabel(this);
        if (c == null || c.length() == 0) c = in.loadLabel(this);
        return c == null ? in.getId() : c.toString();
    }

    void addMenuRow(String label, Runnable action) {
        TextView t = text(400, 17, 0xFFF2F2F7);
        t.setText(label);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(16), 0, dp(16), 0);
        menu.addView(t, new LinearLayout.LayoutParams(-1, dp(46)));
        menuRows.add(t);
        menuActions.add(action);
    }

    void closeMenu() {
        menu.animate().cancel();
        menu.setVisibility(View.GONE);
        region = menuBack;
        applyFocus(true);
    }

    /** Shows the popup under the anchor's top-left, kept on screen, and remembers where Back returns to. */
    void showMenu(RectF at, int backTo) {
        menu.measure(View.MeasureSpec.makeMeasureSpec(dp(260), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.AT_MOST));
        float y = MenuPlacement.top(at.top, at.bottom, menu.getMeasuredHeight(), H, dp(12));
        menu.setX(Math.max(dp(12), Math.min(at.left, W - dp(260) - dp(12))));
        // setY works through the translation, so the slide-in animates y itself (animating translationY to 0
        // would throw the menu back to the top of the screen)
        menu.setY(y - dp(8));
        menu.setAlpha(0f);
        menu.setVisibility(View.VISIBLE);
        menu.animate().alpha(1f).y(y).setDuration(170).start();
        menuBack = backTo;
        region = MENU;
        menuIdx = 0;
        afterLayout(() -> applyFocus(true));
    }

    /** The app OK would act on now: the focused dock or grid tile; null when its row is empty. */
    Ui.AppEntry currentApp() {
        if (region == DOCK && dockIdx < dockApps.size()) return dockApps.get(dockIdx);
        if (region == GRID && gridIdx < gridApps.size()) return gridApps.get(gridIdx);
        return null;
    }

    /** The long-press menu of the focused dock or grid app: the dock, its order, hiding and the app info. */
    void openAppMenu() {
        final Ui.AppEntry e = currentApp();
        if (e == null) return;
        boolean inDock = region == DOCK;
        menu.removeAllViews();
        menuRows.clear();
        menuActions.clear();
        if (inDock) addMenuRow(tx.get(R.string.remove_from_dock), () -> editApps(HomeApps.removeFromDock(shownDock(), hiddenList(), e.pkg), e.pkg));
        else addMenuRow(tx.get(R.string.add_to_dock), () -> addToDock(e));
        // one Move action for the dock order or the grid order; shown only when there is somewhere to go
        if ((inDock ? dockApps.size() : gridApps.size()) > 1) addMenuRow(tx.get(R.string.move), () -> startMove(e.pkg, inDock));
        addMenuRow(tx.get(R.string.hide), () -> hideApp(e));
        addMenuRow(tx.get(R.string.app_info), () -> appInfo(e));
        showMenu(rectOf(targetView(), TileView.FOCUS_SCALE), region);
    }

    /** The dock as it is shown: what an edit writes, so the first edit fixes the current order in home.conf. */
    List<String> shownDock() {
        List<String> out = new ArrayList<>();
        for (Ui.AppEntry e : dockApps) out.add(e.pkg);
        return out;
    }

    List<String> hiddenList() { return new ArrayList<>(conf.hidden); }

    /** Adding to the dock: a full dock writes nothing and says so instead (S5). */
    void addToDock(Ui.AppEntry e) {
        HomeApps.Edit edit = HomeApps.addToDock(shownDock(), hiddenList(), e.pkg, COLS);
        if (!edit.changed()) { Toast.makeText(this, tx.get(R.string.dock_full), Toast.LENGTH_SHORT).show(); return; }
        editApps(edit, e.pkg);
    }

    /** Writes a dock/hidden edit to home.conf the way the settings panel writes its values, then reloads
     *  the apps with the focus following focusPkg (S6, S7). Returns whether home.conf was written. */
    boolean editApps(HomeApps.Edit edit, String focusPkg) {
        if (!edit.changed()) return false;
        // an explicit dock keeps its entries that are not shown (not installed or hidden) when it is rewritten
        List<String> dock = conf.dockSet ? HomeApps.withUnshown(edit.dock, shownDock(), conf.dock) : edit.dock;
        return writeApps(edit.dockChanged ? String.join(",", dock) : null,
                edit.hiddenChanged ? String.join(",", edit.hidden) : null, null, focusPkg);
    }

    /** Hides the app and, only when home.conf was really written, says where to recover it (#36). */
    void hideApp(Ui.AppEntry e) {
        List<String> before = hiddenList();
        HomeApps.Edit edit = HomeApps.hideApp(shownDock(), before, e.pkg);
        if (HomeApps.hiddenGuidance(editApps(edit, e.pkg), before, edit.hidden))
            Toast.makeText(this, tx.get(R.string.hidden_guide), Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- move mode (#39)

    /** Starts moving pkg in the dock or the grid: steps are only shown until OK saves or Back undoes them. */
    void startMove(String pkg, boolean inDock) {
        List<String> shown = new ArrayList<>();
        for (Ui.AppEntry a : inDock ? dockApps : gridApps) shown.add(a.pkg);
        moving = MoveMode.start(shown, pkg, inDock, COLS);
        if (moving == null) return;
        ok.cancel();
        region = inDock ? DOCK : GRID;
        String hint = tx.get(inDock ? R.string.move_hint_dock : R.string.move_hint_grid, currentApp().label);
        moveHint.setText(hint);
        moveHint.setContentDescription(hint);
        moveHint.setVisibility(View.VISIBLE);
        hl.setMoving(true);
        root.announceForAccessibility(hint);
        applyFocus(true);
    }

    void onMoveKey(int k) {
        int dir = k == KeyEvent.KEYCODE_DPAD_LEFT ? MoveMode.LEFT : k == KeyEvent.KEYCODE_DPAD_RIGHT ? MoveMode.RIGHT
                : k == KeyEvent.KEYCODE_DPAD_UP ? MoveMode.UP : k == KeyEvent.KEYCODE_DPAD_DOWN ? MoveMode.DOWN : -1;
        // Every outcome is also spoken (TalkBack), since the keys never move the accessibility focus here.
        if (dir >= 0) {
            if (moving.step(dir)) {
                showMoveOrder(moving.current());
                root.announceForAccessibility(tx.get(R.string.move_position, moving.index() + 1, moving.current().size()));
            }
        } else if (isOk(k)) {
            boolean changed = moving.changed();
            if (finishMove() && changed) root.announceForAccessibility(tx.get(R.string.move_saved));
        } else if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_MENU) {
            cancelMove();   // also called when Home is left, where nothing is announced
            root.announceForAccessibility(tx.get(R.string.move_undone));
        }
    }

    /** Shows the dock or the grid in the given order, with the focus on the app being moved; nothing is written. */
    void showMoveOrder(List<String> order) {
        List<Ui.AppEntry> row = moving.dock ? dockApps : gridApps, out = new ArrayList<>();
        for (String p : order) for (Ui.AppEntry a : row) if (a.pkg.equals(p)) { out.add(a); break; }
        pendingPkg = moving.pkg;
        if (moving.dock) bindApps(out, new ArrayList<>(gridApps)); else bindApps(new ArrayList<>(dockApps), out);
    }

    /** OK: saves the final place once; true when saved. A failed write says so (writeApps) and shows the initial order again. */
    boolean finishMove() {
        MoveMode m = moving;
        endMove();
        boolean saved;
        if (m.dock) {
            HomeApps.Edit edit = m.dockEdit(hiddenList());
            saved = !edit.changed() || editApps(edit, m.pkg);
        } else {
            List<String> order = m.gridOrder(conf.order, installedByLabel, conf.hidden);
            saved = order == null || writeApps(null, null, String.join(",", order), m.pkg);
        }
        if (!saved) { moving = m; showMoveOrder(m.initial()); endMove(); }
        return saved;
    }

    /** Back or MENU: undoes every step. */
    void cancelMove() {
        if (moving == null) return;
        showMoveOrder(moving.initial());
        endMove();
    }

    void endMove() {
        moving = null;
        moveHint.setVisibility(View.GONE);
        hl.setMoving(false);
        updateHero(true);   // the hero label comes back (#57)
    }

    /** Writes the given dock, hidden and order values (null: unchanged) to home.conf, keeping every other line,
     *  then reloads the apps with the focus following focusPkg. */
    boolean writeApps(String dock, String hidden, String order, String focusPkg) {
        File dir = ConfigLocation.dir(this);
        if (dir == null) { Toast.makeText(this, tx.get(R.string.cant_save), Toast.LENGTH_SHORT).show(); return false; }
        try {
            String text = Config.read(dir);
            if (dock != null) text = Config.setInText(text, "dock", dock);
            if (hidden != null) text = Config.setInText(text, "hidden", hidden);
            if (order != null) text = Config.setInText(text, "order", order);
            Config.write(dir, text);
        } catch (IOException ex) {
            Toast.makeText(this, tx.get(R.string.cant_save), Toast.LENGTH_SHORT).show();
            return false;
        }
        conf = Config.load(this);
        pendingPkg = focusPkg;
        loadApps();
        return true;
    }

    void switchInput(String inputId) {
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        Intent i = new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); return; } catch (ActivityNotFoundException ignored) {}
        Intent li = liveTvIntent();
        if (li != null) { li.setData(uri); safeStart(li); }
        else Toast.makeText(this, tx.get(R.string.cant_switch_input), Toast.LENGTH_SHORT).show();
    }

    /** This TV's Live TV app: a known maker app, else whatever handles "view TV channels"; null if none. */
    Intent liveTvIntent() {
        PackageManager pm = getPackageManager();
        for (String p : LIVE_TV_APPS) {
            Intent li = pm.getLeanbackLaunchIntentForPackage(p);
            if (li == null) li = pm.getLaunchIntentForPackage(p);
            if (li != null) return li;
        }
        Intent v = new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI);
        ResolveInfo ri = pm.resolveActivity(v, 0);
        if (ri != null && ri.activityInfo != null) {
            String p = ri.activityInfo.packageName;
            if (!"android".equals(p) && !getPackageName().equals(p)) return v.setPackage(p);
        }
        return null;
    }

    void openLiveTv() {
        Intent li = liveTvIntent();
        if (li != null) safeStart(li);
        else Toast.makeText(this, tx.get(R.string.no_live_tv), Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- the interface language

    /**
     * After a language change in the settings (or with scripts/config.sh) the fixed labels are renamed here; the
     * clock, the date, the cards and the menus are rendered from tx anyway on their next refresh (onResume runs
     * them all). The whole screen switches at once, from one immutable snapshot.
     */
    void applyLanguage() {
        if (tx.language.equals(conf.language)) return;
        tx = Strings.of(this, conf.language);
        pillText[0].setText(tx.get(R.string.inputs));
        pillText[2].setText(tx.get(R.string.settings));
        for (int i = 0; i < cardHeads.size(); i++) cardHeads.get(i).setText(Ca.upper(tx, tx.get(cardHeadIds.get(i))));
        if (mLabel != null) mLabel.setText(matchLabel());   // no football card in minimal mode
        wifiText.setText("");   // updateWifi writes it again in the new language
        refreshQuote();         // the quote follows the interface language
    }

    /** "PROPER PARTIT", or the team's name in front of it. */
    String matchLabel() {
        String next = tx.get(R.string.next_match);
        return conf.teamName.isEmpty() ? next : conf.teamName.toUpperCase(tx.locale) + "  \u00B7  " + next;
    }

    // ---------------------------------------------------------------- clock, wifi, wallpaper (event-driven, no polling)

    void tick() {
        Date now = new Date();
        clock.setText(Clock.format(Clock.pattern(conf.clock, DateFormat.is24HourFormat(this)), tx.locale, now));
        date.setText(Ca.date(tx, now));
        refreshQuote();
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(Ca.greeting(tx, h));
        if (matchCard != null) { refreshMatchCard(); maybeFetchFixtures(); }
        loadWallpaper();
        maybeFetchWallpaper();
        updateNight();
        refreshMorning();
    }

    /**
     * Shows the quote of the day (#69) for the interface language, or hides it when quote=off or there is none.
     * Only does something when the day, the language or the setting changed. The first use of a language reads its
     * file on a background thread and comes back here; afterwards it is cached for the life of the process.
     */
    void refreshQuote() {
        if (quoteText == null) return;
        String lang = tx.language;
        Calendar c = Calendar.getInstance();
        boolean on = MinimalMode.quote(conf);
        String key = on ? lang + "/" + c.get(Calendar.YEAR) + "/" + c.get(Calendar.DAY_OF_YEAR) : "off";
        if (key.equals(quoteKey)) return;
        if (!on) { quoteKey = key; showQuote(null); return; }
        List<Quotes.Quote> list = quoteLibrary.cached(lang);
        if (list == null) {
            if (quoteLoading) return;
            quoteLoading = true;
            final Quotes.Library lib = quoteLibrary;
            new Thread(() -> {
                lib.get(lang);
                ui.post(() -> { quoteLoading = false; refreshQuote(); });
            }, "quotes").start();
            return;
        }
        quoteKey = key;
        showQuote(Quotes.pick(list, c));
    }

    void showQuote(Quotes.Quote q) {
        quoteText.setText(q == null ? "" : q.text);
        quoteText.setVisibility(q == null ? View.GONE : View.VISIBLE);
        String author = Quotes.author(q);
        quoteAuthor.setText(author);
        quoteAuthor.setVisibility(author.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Fades night mode in or out over TimeBands.TRANSITION_S when the band changes; jumps straight there on resume. */
    void updateNight() {
        Calendar c = Calendar.getInstance();
        float target = bands.at(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)) == TimeBands.Band.NIGHT ? 1f : 0f;
        if (target == nightTarget) return;
        boolean first = nightTarget < 0;
        nightTarget = target;
        if (nightAnim != null) nightAnim.cancel();
        if (first) { applyNight(target); return; }
        nightAnim = ValueAnimator.ofFloat(nightNow, target);
        nightAnim.setDuration(TimeBands.TRANSITION_S * 1000L);
        nightAnim.addUpdateListener(a -> applyNight((float) a.getAnimatedValue()));
        nightAnim.start();
    }

    void applyNight(float amount) {
        nightNow = amount;
        if (wall != null) wall.setColorFilter(amount <= 0f ? null : new ColorMatrixColorFilter(Look.nightMatrix(amount)));
        for (TextView t : inks) t.setTextColor(Look.ink((Integer) t.getTag(), amount));
        dockRow.setAlpha(1f - 0.2f * amount);
    }

    @SuppressWarnings("deprecation")
    void updateWifi() {
        String s = tx.get(R.string.offline);
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) {
                if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) s = tx.get(R.string.ethernet);
                else if (ni.getType() == ConnectivityManager.TYPE_WIFI) {
                    WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    String ssid = wm.getConnectionInfo().getSSID();
                    if (ssid == null || ssid.contains("unknown ssid")) ssid = ni.getExtraInfo();
                    if (ssid != null) ssid = ssid.replace("\"", "").trim();
                    s = (ssid == null || ssid.isEmpty() || ssid.contains("unknown")) ? tx.get(R.string.wifi) : ssid;
                } else s = tx.get(R.string.connected);
            }
        } catch (Exception ignored) {}
        if (!s.contentEquals(wifiText.getText())) {
            wifiText.setText(s);
            if (loaded && region == TOP) afterLayout(() -> moveHighlight(false));
        }
    }

    /**
     * Optional wallpaper: a pushed JPEG/PNG wins, else the downloaded one (wallpaper=bing), rotating every
     * wallpaper_minutes. Black when absent or when wallpaper=off. Cheap to call every minute: it only decodes when
     * the file changes.
     */
    void loadWallpaper() {
        // minimal mode: no image view at all, nothing listed or decoded, and the band keeps its neutral colour
        if (wall == null || !MinimalMode.wallpaperShown(conf)) return;
        File f = DailyWallpaper.shown(MinimalMode.wallpaper(conf), getExternalFilesDir(null), conf.wallpaperMinutes,
                System.currentTimeMillis());
        long stamp = f == null ? 0 : f.getName().hashCode() * 31L + (f.lastModified() ^ f.length());
        if (stamp == wallStamp) return;
        wallStamp = stamp;
        if (f == null) { wall.setImageDrawable(null); bandBg.setColor(Look.DEFAULT_BAND); return; }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= W) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        wall.setImageBitmap(b);
        int avg = MinimalMode.dockTint(conf) && b != null ? Ui.averageRgb(b) : 0;
        bandBg.setColor(Look.bandTint((avg >> 16) & 0xFF, (avg >> 8) & 0xFF, avg & 0xFF));
    }

    static final long WALL_REFETCH_EVERY = 6 * 3600_000L;   // Bing changes once a day; 6 h picks it up the same day

    /** Downloads the wallpaper images in the background, only while home is showing. Failures retry after 15 min. */
    void maybeFetchWallpaper() {
        if (MinimalMode.wallpaper(conf).isEmpty() || wallFetching) return;   // off or minimal mode: nothing downloaded
        File dir = getExternalFilesDir(null);
        long now = System.currentTimeMillis();
        boolean have = new File(dir, DailyWallpaper.LIST).exists();   // no list yet (first run or upgrade): fetch now
        if (have && now - lastWallFetch < WALL_REFETCH_EVERY) return;
        if (!have && now - lastWallFetch < 15 * 60_000L) return;
        wallFetching = true;
        String source = MinimalMode.wallpaper(conf);
        new Thread(() -> {
            boolean ok;
            try { DailyWallpaper.download(source, dir); ok = true; } catch (Exception e) { ok = false; }
            boolean done = ok;
            ui.post(() -> {
                lastWallFetch = System.currentTimeMillis();
                wallFailed = !done;
                if (done) prefs.edit().putLong("wallFetched", lastWallFetch).apply();
                wallFetching = false;
                loadWallpaper();
            });
        }).start();
    }

    // ---------------------------------------------------------------- morning panel: weather and news

    static final long WX_EVERY = 30 * 60_000L, NEWS_EVERY = 30 * 60_000L, RETRY = 10 * 60_000L;

    /** A card of the morning panel, its heading kept in cardHeads so a language change can rename it. */
    LinearLayout card(int heading) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(9), dp(16), dp(11));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x9E0C0C0F);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), 0x1FFFFFFF);
        c.setBackground(bg);
        TextView h = text(500, 10, 0xFFBDBAB3);
        h.setLetterSpacing(0.08f);
        h.setText(Ca.upper(tx, tx.get(heading)));
        cardHeads.add(h);
        cardHeadIds.add(heading);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-2, -2);
        hp.bottomMargin = dp(3);
        c.addView(h, hp);
        return c;
    }

    /** A text view rebuilt every refresh: kept out of the night-mode list so that list doesn't grow. */
    TextView rowText(int weight, float sp, int color) {
        TextView t = text(weight, sp, color);
        inks.remove(t);
        t.setTextColor(Look.ink(color, nightNow));
        return t;
    }

    /** Shows the panel from the caches, as the content settings ask, and starts any fetch that is due (only while home shows). */
    void refreshMorning() {
        if (panel == null) return;
        File dir = getExternalFilesDir(null);
        long now = System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        boolean morning = bands.at(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)) == TimeBands.Band.MORNING;
        // The weather is only fetched when a surface still wants it; the screensaver only reads the cache.
        // In minimal mode the panel is never built (panel is null above), and these all read never anyway.
        if (ContentPolicy.wants(MinimalMode.weatherHome(conf)) || ContentPolicy.wants(MinimalMode.weatherScreen(conf))) maybeFetchWeather(dir, now);
        boolean showWx = ContentPolicy.shows(MinimalMode.weatherHome(conf), morning);
        boolean showNews = ContentPolicy.shows(MinimalMode.newsHome(conf), morning);
        if (showNews) maybeFetchNews(dir, now);
        if (!showWx && !showNews) { panel.setVisibility(View.GONE); return; }

        Weather w = showWx ? Weather.load(dir) : null;
        boolean hasWx = w != null && now - w.fetchedAt < 6 * 3600_000L;
        if (hasWx) {
            wxTemp.setText(w.temp + "°"); wxLine.setText(Weather.describe(tx, w.code)); wxRange.setText(w.range(tx));
            String place = conf.locationLabel();
            wxCredit.setText(place.isEmpty() ? tx.get(R.string.weather_source) : tx.get(R.string.weather_credit, place));
        }
        wxCard.setVisibility(hasWx ? View.VISIBLE : View.GONE);

        NewsSource source = NewsSource.resolve(conf);
        List<News.Item> items = showNews ? News.load(dir, source) : null;
        newsList.removeAllViews();
        int shown = 0;
        if (items != null) for (News.Item it : items) {
            if (shown == 2) break;
            TextView t = rowText(400, 14, 0xFFFFFFFF);
            t.setSingleLine(false);
            t.setMaxLines(2);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setText(it.title);
            String age = News.age(tx, it.published, now);
            TextView src = rowText(300, 11, 0xFFA9A6A0);
            src.setText(age.isEmpty() ? source.label : source.label + " · " + age);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
            if (shown > 0) ip.topMargin = dp(5);
            newsList.addView(t, ip);
            newsList.addView(src);
            shown++;
        }
        // The credit the terms of the feed ask for: the source and its site, next to the headlines.
        newsCredit.setText(shown > 0 ? source.credit : "");
        newsCard.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);

        boolean any = hasWx || shown > 0;
        panel.setVisibility(any ? View.VISIBLE : View.GONE);
        if (any && matchCard != null) matchCard.setVisibility(View.GONE);
    }

    void maybeFetchWeather(File dir, long now) {
        if (wxBusy || now - lastWx < (Weather.load(dir) == null ? RETRY : WX_EVERY)) return;
        wxBusy = true;
        double lat = conf.lat, lon = conf.lon;
        new Thread(() -> {
            boolean ok;
            try { Weather.fetch(lat, lon).save(dir); ok = true; } catch (Exception ignored) { ok = false; }
            final boolean fetched = ok;
            ui.post(() -> { lastWx = System.currentTimeMillis(); wxFailed = !fetched; wxBusy = false; refreshMorning(); });
        }).start();
    }

    void maybeFetchNews(File dir, long now) {
        NewsSource source = NewsSource.resolve(conf);
        if (newsBusy || source.url.isEmpty()) return;
        if (!newsSchedule.due(source.key, now, NEWS_EVERY, RETRY)) return;
        newsBusy = true;
        newsSchedule.attempt(source.key, now);
        String url = source.url;
        new Thread(() -> {
            boolean ok;
            try { News.save(dir, source, News.fetch(url, 5)); ok = true; } catch (Exception ignored) { ok = false; }
            final boolean fetched = ok;
            ui.post(() -> {
                if (fetched) newsSchedule.success(source.key, System.currentTimeMillis());
                newsBusy = false;
                refreshMorning();
            });
        }).start();
    }

    // ---------------------------------------------------------------- football fixtures card

    static final long MATCH_LENGTH = 2 * 3600_000L;     // treat a match as "on" for 2 h after kick-off
    static final long REFETCH_EVERY = 3 * 3600_000L;   // at most one fetch every 3 h, and only while home is shown

    Fixtures.Match nextMatch(long now) {
        for (Fixtures.Match m : matches) if (m.kickoff + MATCH_LENGTH > now) return m;
        return null;
    }

    void maybeFetchFixtures() {
        if (teamId == null || teamId.isEmpty() || fetching) return;
        long now = System.currentTimeMillis();
        Fixtures.Match n = nextMatch(now);
        boolean stale = now - lastFetch > REFETCH_EVERY;
        boolean retryFailed = now - lastFetch > 15 * 60_000L && n == null;   // nothing cached yet: retry after 15 min
        if (!stale && !retryFailed) return;
        fetching = true;
        new Thread(() -> {
            try {
                String json = Fixtures.fetch(teamId);
                List<Fixtures.Match> parsed = Fixtures.parse(json, teamId);
                ui.post(() -> {
                    if (!parsed.isEmpty()) {
                        matches = parsed;
                        prefs.edit().putString("fixJson", json).apply();
                    }
                    lastFetch = System.currentTimeMillis();
                    prefs.edit().putLong("fixFetched", lastFetch).apply();
                    fixFailed = false;
                    fetching = false;
                    refreshMatchCard();
                });
            } catch (Exception e) {
                ui.post(() -> { lastFetch = System.currentTimeMillis(); fixFailed = true; fetching = false; });
            }
        }).start();
    }

    void refreshMatchCard() {
        if (matchCard == null) return;
        long now = System.currentTimeMillis();
        Fixtures.Match m = nextMatch(now);
        if (m == null) { matchCard.setVisibility(View.GONE); return; }
        if (panel != null && panel.getVisibility() == View.VISIBLE) { matchCard.setVisibility(View.GONE); return; }   // morning panel has the spot
        matchCard.setVisibility(View.VISIBLE);
        mTitle.setText(tx.get(R.string.versus, m.opponent));
        String comp = m.competition;
        String where = tx.get(m.home ? R.string.home_game : R.string.away_game);
        mComp.setText(comp.isEmpty() ? where : comp + "  \u00B7  " + where);
        if (now >= m.kickoff) {
            mWhen.setText(tx.get(R.string.live_now));
            mWhen.setTextColor(getColor(R.color.fixture_accent));
            long mins = (now - m.kickoff) / 60_000L;
            mCountdown.setText(Ca.kickedOff(tx, mins));
            return;
        }
        mWhen.setTextColor(0xFFFFFFFF);
        Calendar k = Calendar.getInstance(); k.setTimeInMillis(m.kickoff);
        Calendar today = Calendar.getInstance();
        Calendar tomorrow = Calendar.getInstance(); tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        String day;
        if (sameDay(k, today)) day = tx.get(R.string.today);
        else if (sameDay(k, tomorrow)) day = tx.get(R.string.tomorrow);
        else if (m.kickoff - now < 6L * 24 * 3600_000L) day = Ca.weekday(tx, new Date(m.kickoff));
        else day = Ca.shortDay(tx, new Date(m.kickoff));
        String time = Clock.format(Clock.matchPattern(conf.clock, DateFormat.is24HourFormat(this)), tx.locale, new Date(m.kickoff));
        mWhen.setText(day + "  \u00B7  " + time);
        long mins = (m.kickoff - now) / 60_000L;
        mCountdown.setText(Ca.countdown(tx, mins));
    }

    static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
