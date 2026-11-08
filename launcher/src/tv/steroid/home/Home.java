package tv.steroid.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Horizon home: a big clock in a live sky, your apps standing on the horizon,
 * and a quiet strip of inputs, settings and extras underneath.
 */
public class Home extends Activity {
    /** {package, label, accent} for the app row, from config/apps.json. */
    static String[][] APPS = new String[0][];

    static void loadApps() {
        org.json.JSONArray a = Config.apps.optJSONArray("apps");
        int n = a == null ? 0 : a.length();
        String[][] out = new String[n][];
        for (int i = 0; i < n; i++) {
            org.json.JSONObject o = a.optJSONObject(i);
            out[i] = new String[]{o.optString("package"), o.optString("label"), o.optString("accent", "#F2E6C8")};
        }
        APPS = out;
    }

    static final int INK = 0xFFFFFFFF, SOFT = 0xD9FFFFFF, QUIET = 0x99FFFFFF;

    final Handler h = new Handler();
    Sky sky;
    FrameLayout root;
    LinearLayout ground;
    HorizontalScrollView rowScroll;
    View firstApp;
    TextView clock, meridiem, date, playing, focusName, temp, sky1, sky2;
    LinearLayout weather, events;
    int weatherTest = -1; // set by the adb test hook; -1 = real weather
    TextView event1, event2;
    Weather.Icon wicon;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        loadApps();
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        root = new FrameLayout(this);
        root.setClipChildren(false);
        sky = new Sky(this);
        root.addView(sky, new FrameLayout.LayoutParams(-1, -1));
        root.addView(sky(), skyParams());

        ground = new LinearLayout(this);
        ground.setOrientation(LinearLayout.VERTICAL);
        ground.setClipChildren(false);
        ground.setClipToPadding(false);
        ground.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f)); // space under the hills…
        firstApp = row();
        focusName = Ui.text(this, Ui.face(this, 600, 88), 22, INK);
        focusName.setPadding(Ui.px(14), Ui.px(2), 0, 0);
        focusName.setVisibility(View.GONE); // the banners speak for themselves
        ground.addView(focusName);
        // room between the apps and the bottom bar; the bar keeps to the bottom edge
        ground.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        ground.addView(strip(), new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(-1, -1, Gravity.TOP);
        glp.bottomMargin = Ui.px(40);
        glp.leftMargin = Ui.px(64) - Ui.px(14);
        glp.rightMargin = Ui.px(64) - Ui.px(12);
        root.addView(ground, glp);

        // The app row stands just below the near hills' edge, wherever that lands on this screen.
        root.addOnLayoutChangeListener((v, l, t, r, btm, ol, ot, or, ob) -> {
            int want = (int) ((btm - t) * Sky.NEAR) + Ui.px(8); // ground starts at the near hills' edge
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) ground.getLayoutParams();
            if (p.topMargin != want) { p.topMargin = want; ground.setLayoutParams(p); }
        });

        setContentView(root);
        if (firstApp != null) firstApp.requestFocus();
    }

    // ---------- The sky: clock and date ----------

    FrameLayout.LayoutParams skyParams() {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        p.leftMargin = Ui.px(64) - Ui.px(4); // clock glyphs carry ~4dp side bearing
        p.topMargin = Ui.px(26);
        return p;
    }

    View sky() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        LinearLayout time = new LinearLayout(this);
        time.setGravity(Gravity.BOTTOM);
        clock = Ui.text(this, Ui.face(this, 240, 75), 118, INK);
        clock.setLetterSpacing(-0.015f);
        time.addView(clock);
        meridiem = Ui.text(this, Ui.face(this, 400, 75), 30, SOFT);
        meridiem.setPadding(Ui.px(10), 0, 0, Ui.px(20));
        time.addView(meridiem);
        v.addView(time);
        date = Ui.text(this, Ui.face(this, 500, 100), 21, SOFT);
        date.setPadding(Ui.px(4), Ui.px(2), 0, 0);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = -Ui.px(22); // the display face's tall line box leaves a gap under the numerals
        v.addView(date, dlp);

        // weather: icon, temperature, then the condition over today's range
        weather = new LinearLayout(this);
        weather.setGravity(Gravity.CENTER_VERTICAL);
        weather.setPadding(Ui.px(14), Ui.px(7), Ui.px(18), Ui.px(7));
        weather.setBackground(glass());
        weather.setVisibility(View.GONE);
        wicon = new Weather.Icon(this);
        weather.addView(wicon, new LinearLayout.LayoutParams(Ui.px(22), Ui.px(22)));
        temp = Ui.text(this, Ui.face(this, 350, 75), 21, INK);
        temp.setPadding(Ui.px(8), 0, Ui.px(10), 0);
        weather.addView(temp);
        LinearLayout lines = new LinearLayout(this);
        lines.setOrientation(LinearLayout.HORIZONTAL);
        lines.setGravity(Gravity.CENTER_VERTICAL);
        sky1 = Ui.text(this, Ui.face(this, 550, 95), 14, INK);
        sky2 = Ui.text(this, Ui.face(this, 450, 95), 13, SOFT);
        sky2.setPadding(Ui.px(10), 0, 0, 0);
        lines.addView(sky1);
        lines.addView(sky2);
        weather.addView(lines);
        // what's special today, beside the weather: a festival and/or something in the sky
        events = new LinearLayout(this);
        events.setOrientation(LinearLayout.VERTICAL);
        events.setVisibility(View.GONE);
        event1 = Ui.text(this, Ui.face(this, 550, 95), 17, INK);
        event2 = Ui.text(this, Ui.face(this, 450, 95), 13, SOFT);
        event2.setPadding(0, Ui.px(3), 0, 0);
        events.addView(event1);
        events.addView(event2);

        // festival / sky note sits under the date
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-2, -2);
        elp.topMargin = Ui.px(12);
        elp.leftMargin = Ui.px(4);
        v.addView(events, elp);

        playing = button("", () -> startActivity(new Intent(this, Radio.class).putExtra("nowPlaying", true)));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.topMargin = Ui.px(16);
        plp.leftMargin = Ui.px(-8);
        v.addView(playing, plp);
        return v;
    }

    /**
     * Frosted glass for panels over the sky: a faint white tint that fades across the panel,
     * a soft sheen along the top and a thin light edge. (Android 11 can't blur what's behind.)
     */
    static android.graphics.drawable.Drawable glass() {
        GradientDrawable tint = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x3DFFFFFF, 0x1CFFFFFF});
        tint.setCornerRadius(Ui.px(20));
        tint.setStroke(Ui.px(1), 0x3DFFFFFF);
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x1AFFFFFF, 0x00FFFFFF}); // brighter at the top edge, gone by the bottom
        sheen.setCornerRadius(Ui.px(20));
        android.graphics.drawable.LayerDrawable d = new android.graphics.drawable.LayerDrawable(
            new android.graphics.drawable.Drawable[]{tint, sheen});
        return d;
    }

    // ---------- Apps on the horizon ----------

    View row() {
        rowScroll = new HorizontalScrollView(this);
        rowScroll.setHorizontalScrollBarEnabled(false);
        rowScroll.setClipChildren(false);
        rowScroll.setClipToPadding(false);
        rowScroll.setSmoothScrollingEnabled(true);
        rowScroll.setOnScrollChangeListener((v, x, y, ox, oy) -> placeFocus());
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(Ui.px(14), Ui.px(14), Ui.px(64), Ui.px(8));
        rowScroll.addView(row);
        ground.addView(rowScroll, new LinearLayout.LayoutParams(-1, -2));
        appRow = row;
        return fillRow();
    }

    LinearLayout appRow;
    String shownOrder = "";

    /** (Re)builds the app cards in usage order; returns the first card. */
    View fillRow() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        for (String[] a : APPS) if (launchIntent(pm, a[0]) != null) apps.add(a);
        if (apps.isEmpty()) // nothing configured yet: show what's installed so the TV is still usable
            for (Entry e : AllApps.list(this)) { if (apps.size() >= 6) break; apps.add(new String[]{e.pkg, e.label, "#F2E6C8"}); }
        apps = Usage.sort(this, apps);
        StringBuilder order = new StringBuilder();
        for (String[] a : apps) order.append(a[0]).append(',');
        shownOrder = order.toString();

        appRow.removeAllViews();
        View first = null;
        for (String[] a : apps) {
            View card = appCard(pm, a, launchIntent(pm, a[0]));
            appRow.addView(card, cardParams(first == null));
            if (first == null) first = card;
        }
        View all = allAppsCard();
        appRow.addView(all, cardParams(first == null));
        return first == null ? all : first;
    }

    /** On returning home, re-sort if usage changed the order; focus goes to the (new) first app. */
    void resortIfNeeded() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        for (String[] a : APPS) if (launchIntent(pm, a[0]) != null) apps.add(a);
        StringBuilder order = new StringBuilder();
        for (String[] a : Usage.sort(this, apps)) order.append(a[0]).append(',');
        if (apps.isEmpty() || order.toString().equals(shownOrder)) return;
        firstApp = fillRow();
        rowScroll.scrollTo(0, 0);
        firstApp.requestFocus();
    }

    static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    LinearLayout.LayoutParams cardParams(boolean first) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(144), Ui.px(81));
        lp.leftMargin = first ? 0 : Ui.px(18);
        return lp;
    }

    View appCard(PackageManager pm, String[] a, Intent launch) {
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClipToOutline(true);
        card.setBackground(Ui.round(0xFF1A1C24, 12));
        Drawable art = null;
        try { art = pm.getApplicationBanner(a[0]); } catch (Exception ignored) {}
        if (art != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(art);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            LinearLayout fb = new LinearLayout(this);
            fb.setOrientation(LinearLayout.VERTICAL);
            fb.setGravity(Gravity.CENTER);
            try {
                ImageView ic = new ImageView(this);
                ic.setImageDrawable(pm.getApplicationIcon(a[0]));
                fb.addView(ic, new LinearLayout.LayoutParams(Ui.px(40), Ui.px(40)));
            } catch (Exception ignored) {}
            TextView t = Ui.text(this, Ui.face(this, 600, 90), 13, INK);
            t.setText(a[1]);
            t.setPadding(0, Ui.px(6), 0, 0);
            fb.addView(t);
            card.addView(fb, new FrameLayout.LayoutParams(-1, -1));
        }
        int accent;
        try { accent = Color.parseColor(a[2]); } catch (Exception e) { accent = 0xFFF2E6C8; }
        focusable(card, a[1], accent, 12);
        card.setOnClickListener(v -> { Usage.launched(this, a[0]); start(launch); });
        return card;
    }

    View allAppsCard() {
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setBackground(Ui.round(0xFF2A2F3C, 12));
        View dots = new View(this) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                p.setColor(0xE6FFFFFF);
                float s = Ui.px(11), cx = getWidth() / 2f, cy = getHeight() / 2f;
                for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) c.drawCircle(cx + i * s, cy + j * s, Ui.px(2.6f), p);
            }
        };
        card.addView(dots, new FrameLayout.LayoutParams(-1, -1));
        focusable(card, "All apps", 0xFFF2E6C8, 12);
        card.setOnClickListener(v -> startActivity(new Intent(this, AllApps.class)));
        return card;
    }

    /** Focus look for horizon cards: lift, a white ring, the name below, and the hills lit in its colour. */
    void focusable(FrameLayout card, String name, int accent, float radius) {
        View ring = new View(this);
        GradientDrawable rd = new GradientDrawable();
        rd.setCornerRadius(Ui.px(radius));
        rd.setStroke(Ui.px(1), 0x24FFFFFF);
        ring.setBackground(rd);
        card.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        card.setOnFocusChangeListener((v, f) -> {
            Ui.lift(v, f, 1.09f);
            rd.setStroke(Ui.px(f ? 3 : 1), f ? 0xFFFFFFFF : 0x24FFFFFF);
            if (!f) return;
            focusName.setText(name);
            focused = v;
            focusedAccent = accent;
            v.post(this::placeFocus);
            h.postDelayed(this::placeFocus, 300);
        });
    }

    View focused;
    int focusedAccent;

    /** Puts the name under the focused card and the rim light above it (re-run as the row scrolls). */
    void placeFocus() {
        View v = focused;
        if (v == null || !v.hasFocus() && !v.isFocused()) return;
        int[] gr = new int[2];
        ground.getLocationOnScreen(gr);
        // layout position (ignores the focus lift's scale), minus the row's scroll
        float left = v.getLeft() - rowScroll.getScrollX() + rowScroll.getLeft() + ((View) v.getParent()).getLeft();
        sky.glow(focusedAccent, gr[0] + left + v.getWidth() / 2f);
        focusName.setTranslationX(Math.max(0, left - Ui.px(14)));
    }

    // ---------- Underneath: inputs, settings, extras ----------

    View strip() {
        LinearLayout s = new LinearLayout(this);
        s.setGravity(Gravity.CENTER_VERTICAL);
        s.setPadding(Ui.px(14) - Ui.px(12), 0, 0, 0); // button text lines up with the cards' left edge
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) {
            List<TvInputInfo> ins = new ArrayList<>();
            for (TvInputInfo in : tim.getTvInputList())
                if (in.getType() == TvInputInfo.TYPE_HDMI && !in.isHidden(this)) ins.add(in);
            java.util.Collections.sort(ins, (x, y) -> String.valueOf(x.loadLabel(this)).compareTo(String.valueOf(y.loadLabel(this))));
            for (TvInputInfo in : ins) {
                CharSequence l = in.loadLabel(this);
                Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(in.getId()));
                s.addView(button(l == null ? "HDMI" : l.toString(), () -> start(i)));
            }
        }
        s.addView(button("Wi-Fi", () -> start(new Intent(Settings.ACTION_WIFI_SETTINGS))));
        s.addView(button("Bluetooth", () -> start(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))));
        s.addView(button("Settings", () -> start(new Intent(Settings.ACTION_SETTINGS))));
        s.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        s.addView(weather); // a glass status pill on the dark ground, never in front of the sun
        s.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        if (Config.feature("radio") && Stations.count() > 0)
            s.addView(button("Radio", () -> startActivity(new Intent(this, Radio.class).putExtra("nowPlaying", RadioService.station >= 0))));
        if (Config.feature("retroTv")) s.addView(button("Retro TV", () -> startActivity(new Intent(this, Retro.class))));
        if (Config.feature("worldsWindows") && Places.count() > 0)
            s.addView(button("Windows", () -> startActivity(new Intent(this, Windows.class))));
        return s;
    }

    /** A plain text button that becomes a solid pill when focused. */
    TextView button(String label, Runnable action) {
        TextView t = Ui.text(this, Ui.face(this, 500, 95), 15, SOFT);
        t.setText(label);
        t.setFocusable(true);
        t.setPadding(Ui.px(12), Ui.px(7), Ui.px(12), Ui.px(8));
        GradientDrawable bg = Ui.round(0x00FFFFFF, 18);
        t.setBackground(bg);
        t.setOnFocusChangeListener((v, f) -> {
            bg.setColor(f ? 0xFFFFFFFF : 0x00FFFFFF);
            t.setTextColor(f ? 0xFF0C1020 : SOFT);
        });
        t.setOnClickListener(v -> action.run());
        return t;
    }

    // ---------- Plumbing ----------

    void start(Intent i) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { Toast.makeText(this, "This app can't be opened right now", Toast.LENGTH_SHORT).show(); }
    }

    void showWeather() {
        Weather.Now n = Weather.cached(this);
        if (n == null) return;
        weather.setVisibility(View.VISIBLE);
        wicon.set(n.code, n.day);
        temp.setText(n.temp + "°");
        sky1.setText(n.phrase());
        sky2.setText(n.low + "–" + n.high + "°");
        sky.weather(weatherTest >= 0 ? weatherTest : n.code);
        showEvents();
    }

    void showEvents() {
        List<String> e = Events.today(this, sky.lat, sky.lng);
        events.setVisibility(e.isEmpty() ? View.GONE : View.VISIBLE);
        if (e.isEmpty()) return;
        event1.setText(e.get(0));
        event2.setText(e.size() > 1 ? e.get(1) : "");
        event2.setVisibility(e.size() > 1 ? View.VISIBLE : View.GONE);
    }

    void refreshPlaying() {
        int s = RadioService.station;
        playing.setVisibility(s >= 0 ? View.VISIBLE : View.GONE);
        if (s >= 0) playing.setText("Playing " + Stations.name(s).split(" — ")[0]);
    }

    final Runnable tick = new Runnable() { public void run() {
        Calendar c = Calendar.getInstance();
        boolean h24 = DateFormat.is24HourFormat(Home.this);
        clock.setText(DateFormat.format(h24 ? "HH:mm" : "h:mm", c));
        meridiem.setText(h24 ? "" : (c.get(Calendar.AM_PM) == Calendar.AM ? "am" : "pm"));
        date.setText(DateFormat.format("EEEE, d MMMM", c));
        Weather.refresh(Home.this, () -> h.post(Home.this::showWeather)); // no-op unless 30 min old
        if (c.get(Calendar.MINUTE) == 0) showEvents();
        h.postDelayed(this, 60000 - c.get(Calendar.SECOND) * 1000);
    }};

    @Override protected void onResume() {
        super.onResume();
        resortIfNeeded();
        tick.run();
        showWeather();
        showEvents();
        Weather.refresh(this, () -> h.post(this::showWeather));
        Watchdog.ensure(this); // heal the Home button if TCL's memory guard killed it
        RadioService.listener = this::refreshPlaying;
        refreshPlaying();
    }

    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); sky.run(f); }

    @Override protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
        sky.run(false);
    }

    @Override public void onBackPressed() { /* home stays home */ }

    /** Pressing Home on the remote always comes back to the first app. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // test hook: adb shell am start -n tv.steroid.home/.Home --ei weatherTest 63  (until the next real update)
        if (intent.hasExtra("weatherTest")) { weatherTest = intent.getIntExtra("weatherTest", -1); sky.weather(weatherTest); }
        if (rowScroll != null) rowScroll.scrollTo(0, 0);
        if (firstApp != null) firstApp.requestFocus();
    }

    static class Entry {
        String pkg, label;
        Intent launch;
    }
}
