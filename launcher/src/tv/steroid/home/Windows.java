package tv.steroid.home;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/** The World's Windows: choose somewhere to look out, then look. */
public class Windows extends Activity {
    static final int[] MOOD_HUE = {0xFF8FB8D8, 0xFFFF6B5A, 0xFFE8915A, 0xFFB8D4F0, 0xFF5FA8C8, 0xFF3FC1C9, 0xFFC9A46A, 0xFF7B6CFF};
    static final int[] ROTATE_MIN = {0, 10, 30, 60};

    final Handler h = new Handler();
    SharedPreferences prefs;
    FrameLayout root, browser, viewer;
    Ambient ambient;
    LinearLayout filterRow;
    GridLayout grid;
    TextView countLabel;
    WebView web;
    LinearLayout info, controls, offline;
    TextView badge, vName, vWhere, vNote, vSource, offTitle, offNext;
    TextView favBtn, soundBtn, rotateBtn;
    int mood, current = -1, rotate;
    int liveState = -1; // 1 live, 0 recorded, -1 unknown
    boolean playing, webReady, sound;
    Set<String> favs;
    List<Integer> visible = new ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        if (Config.feature("debugWebViews")) WebView.setWebContentsDebuggingEnabled(true);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        prefs = getSharedPreferences("windows", MODE_PRIVATE);
        if (prefs.getInt("v", 0) < 2) prefs.edit().clear().putInt("v", 2).apply(); // curated list changed: drop old positions
        favs = new HashSet<>(prefs.getStringSet("favs", new HashSet<>()));
        sound = prefs.getBoolean("sound", true);
        rotate = prefs.getInt("rotate", 0);

        root = new FrameLayout(this);
        ambient = new Ambient(this);
        root.addView(ambient);
        buildBrowser();
        buildViewer();
        setContentView(root);
        showBrowser();
    }

    // ---------- Browser ----------

    void buildBrowser() {
        browser = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(Ui.px(52), Ui.px(36), Ui.px(52), 0);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.BOTTOM);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.label(this, "The World's Windows", 0x99FFFFFF));
        TextView t = Ui.text(this, Ui.serif, 44, 0xFFFFFFFF);
        t.setText("Where shall we look?");
        t.setPadding(0, Ui.px(8), 0, 0);
        titles.addView(t);
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        countLabel = Ui.label(this, "", 0x80FFFFFF);
        countLabel.setPadding(0, 0, 0, Ui.px(6));
        head.addView(countLabel);
        col.addView(head);

        filterRow = new LinearLayout(this);
        filterRow.setClipChildren(false);
        filterRow.setPadding(0, Ui.px(18), 0, Ui.px(6));
        for (int i = 0; i < Places.MOODS.length; i++) filterRow.addView(filterChip(i));
        col.addView(filterRow);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, Ui.px(10), 0, Ui.px(40));
        grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setClipChildren(false);
        scroll.addView(grid);
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        browser.addView(col, new FrameLayout.LayoutParams(-1, -1));
        root.addView(browser);
    }

    TextView filterChip(int i) {
        TextView c = Ui.text(this, Ui.sans, 13, 0xD9FFFFFF);
        c.setText(Places.MOODS[i]);
        c.setFocusable(true);
        c.setPadding(Ui.px(15), Ui.px(8), Ui.px(15), Ui.px(8));
        GradientDrawable bg = Ui.round(0x14FFFFFF, 18);
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.px(8);
        c.setLayoutParams(lp);
        c.setOnFocusChangeListener((v, f) -> {
            if (f && mood != i) { mood = i; fillGrid(); }
            paintFilters();
        });
        c.setOnClickListener(v -> { if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus(); });
        return c;
    }

    void paintFilters() {
        for (int i = 0; i < filterRow.getChildCount(); i++) {
            TextView c = (TextView) filterRow.getChildAt(i);
            GradientDrawable bg = (GradientDrawable) c.getBackground();
            boolean f = c.isFocused(), sel = i == mood;
            bg.setColor(f ? 0xFFFFFFFF : sel ? 0x40FFFFFF : 0x14FFFFFF);
            c.setTextColor(f ? 0xFF111114 : 0xE6FFFFFF);
        }
    }

    void fillGrid() {
        grid.removeAllViews();
        visible.clear();
        for (int i = 0; i < Places.count(); i++) {
            String m = Places.MOODS[mood];
            boolean in = m.equals("All") || (m.equals("Favourites") ? favs.contains(Places.id(i)) : Places.mood(i).equals(m));
            if (in) { visible.add(i); grid.addView(card(i)); }
        }
        if (visible.isEmpty()) {
            TextView e = Ui.text(this, Ui.serifItalic, 20, 0x99FFFFFF);
            e.setText(mood == 1 ? "No favourites yet — press OK while watching, then ♥." : "Nothing here.");
            e.setPadding(0, Ui.px(30), 0, 0);
            grid.addView(e);
        }
        countLabel.setText(visible.size() + (visible.size() == 1 ? " window" : " windows"));
        ambient.tint(MOOD_HUE[mood]);
    }

    View card(int i) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setFocusable(true);
        c.setClipChildren(false);

        FrameLayout art = new FrameLayout(this);
        art.setClipToOutline(true);
        GradientDrawable artBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Ui.withAlpha(moodHue(i), 0xCC), 0xFF101016});
        artBg.setCornerRadius(Ui.px(14));
        art.setBackground(artBg);
        Bitmap bm = preview(Places.id(i));
        if (bm != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(bm);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView glyph = Ui.text(this, Ui.serifItalic, 40, 0x66FFFFFF);
            glyph.setText(Places.name(i).substring(0, 1));
            art.addView(glyph, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        }
        if (favs.contains(Places.id(i))) {
            TextView heart = Ui.text(this, Ui.sans, 13, 0xFFFF6B5A);
            heart.setText("♥");
            FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            hl.setMargins(0, Ui.px(8), Ui.px(10), 0);
            art.addView(heart, hl);
        }
        View ring = new View(this);
        GradientDrawable rd = new GradientDrawable();
        rd.setCornerRadius(Ui.px(14));
        rd.setStroke(Ui.px(1), 0x1FFFFFFF);
        ring.setBackground(rd);
        art.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        c.addView(art, new LinearLayout.LayoutParams(-1, Ui.px(112)));

        TextView name = Ui.text(this, Ui.serif, 19, 0xFFFFFFFF);
        name.setText(Places.name(i));
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(Ui.px(2), Ui.px(10), 0, Ui.px(4));
        c.addView(name);
        TextView where = Ui.label(this, Places.where(i) + " · " + localTime(i), 0x99FFFFFF);
        where.setTextSize(9.5f);
        where.setSingleLine(true);
        where.setEllipsize(android.text.TextUtils.TruncateAt.END);
        where.setPadding(Ui.px(2), 0, 0, 0);
        c.addView(where);

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = Ui.px(200);
        lp.setMargins(0, Ui.px(8), Ui.px(16), Ui.px(14));
        c.setLayoutParams(lp);
        c.setOnFocusChangeListener((v, f) -> {
            Ui.lift(art, f, 1.07f);
            rd.setStroke(Ui.px(f ? 2.5f : 1), f ? 0xFFFFFFFF : 0x1FFFFFFF);
            if (f) ambient.tint(moodHue(i));
        });
        c.setOnClickListener(v -> open(i));
        c.setTag(i);
        return c;
    }

    int moodHue(int i) {
        for (int m = 0; m < Places.MOODS.length; m++) if (Places.MOODS[m].equals(Places.mood(i))) return MOOD_HUE[m];
        return MOOD_HUE[0];
    }

    Bitmap preview(String id) {
        try (InputStream in = getAssets().open("windows/" + id + ".jpg")) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2;
            return BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) { return null; }
    }

    static String localTime(int i) {
        TimeZone tz = TimeZone.getTimeZone(Places.tz(i));
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.US);
        f.setTimeZone(tz);
        return f.format(new java.util.Date()).toLowerCase();
    }

    void showBrowser() {
        viewer.setVisibility(View.GONE);
        browser.setVisibility(View.VISIBLE);
        h.removeCallbacks(rotateTick);
        h.removeCallbacks(watchdog);
        if (web != null) web.evaluateJavascript("player&&player.pauseVideo&&player.pauseVideo()", null);
        fillGrid();
        paintFilters();
        int last = Places.indexOf(prefs.getString("last", ""));
        View target = null;
        for (int k = 0; k < grid.getChildCount(); k++) {
            Object tag = grid.getChildAt(k).getTag();
            if (tag instanceof Integer && (Integer) tag == (current >= 0 ? current : last)) target = grid.getChildAt(k);
        }
        if (target == null && grid.getChildCount() > 0) target = grid.getChildAt(0);
        if (target != null) target.requestFocus();
        ambient.run(true);
    }

    // ---------- Viewer ----------

    void buildViewer() {
        viewer = new FrameLayout(this);
        viewer.setBackgroundColor(0xFF000000);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setDomStorageEnabled(true);
        web.setBackgroundColor(0xFF000000);
        web.setFocusable(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Native");
        viewer.addView(web, new FrameLayout.LayoutParams(-1, -1));

        // Soft shade behind the text so it reads over any picture.
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xCC000000, 0x00000000}));
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-1, Ui.px(260), Gravity.BOTTOM);
        viewer.addView(shade, sl);

        info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        badge = Ui.label(this, "", 0xFFFFFFFF);
        badge.setTextSize(10);
        badge.setPadding(Ui.px(10), Ui.px(5), Ui.px(10), Ui.px(5));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        info.addView(badge, bl);
        vName = Ui.text(this, Ui.serif, 46, 0xFFFFFFFF);
        vName.setShadowLayer(Ui.px(14), 0, 0, 0x99000000);
        vName.setPadding(0, Ui.px(10), 0, Ui.px(6));
        info.addView(vName);
        vWhere = Ui.label(this, "", 0xE6FFFFFF);
        vWhere.setTextSize(11.5f);
        info.addView(vWhere);
        vNote = Ui.text(this, Ui.sans, 12.5f, 0xB3FFFFFF);
        vNote.setPadding(0, Ui.px(10), 0, 0);
        info.addView(vNote);
        vSource = Ui.label(this, "", 0x80FFFFFF);
        vSource.setTextSize(9.5f);
        vSource.setPadding(0, Ui.px(10), 0, 0);
        info.addView(vSource);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(Ui.px(420), -2, Gravity.BOTTOM | Gravity.START);
        il.setMargins(Ui.px(52), 0, 0, Ui.px(40));
        viewer.addView(info, il);

        controls = new LinearLayout(this);
        controls.setGravity(Gravity.END);
        favBtn = control(() -> toggleFav());
        soundBtn = control(() -> toggleSound());
        rotateBtn = control(() -> cycleRotate());
        TextView next = control(() -> step(1));
        next.setText("Next window →");
        TextView all = control(() -> showBrowser());
        all.setText("All windows");
        controls.addView(favBtn); controls.addView(soundBtn); controls.addView(rotateBtn);
        controls.addView(next); controls.addView(all);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        cl.setMargins(0, 0, Ui.px(44), Ui.px(40));
        controls.setVisibility(View.GONE);
        viewer.addView(controls, cl);

        offline = new LinearLayout(this);
        offline.setOrientation(LinearLayout.VERTICAL);
        offline.setGravity(Gravity.CENTER);
        offline.setBackgroundColor(0xE6060609);
        offline.addView(Ui.label(this, "This window is closed", 0x99FFFFFF));
        offTitle = Ui.text(this, Ui.serifItalic, 34, 0xFFFFFFFF);
        offTitle.setGravity(Gravity.CENTER);
        offTitle.setPadding(Ui.px(60), Ui.px(12), Ui.px(60), Ui.px(26));
        offline.addView(offTitle);
        LinearLayout ob = new LinearLayout(this);
        TextView retry = control(() -> open(current));
        retry.setText("Try again");
        offNext = control(() -> step(1));
        ob.addView(retry); ob.addView(offNext);
        offline.addView(ob);
        offline.setVisibility(View.GONE);
        viewer.addView(offline, new FrameLayout.LayoutParams(-1, -1));

        root.addView(viewer);
    }

    TextView control(Runnable r) {
        TextView t = Ui.text(this, Ui.sans, 13, 0xE6FFFFFF);
        t.setFocusable(true);
        t.setPadding(Ui.px(16), Ui.px(9), Ui.px(16), Ui.px(9));
        GradientDrawable bg = Ui.round(0x33000000, 20);
        bg.setStroke(Ui.px(1), 0x33FFFFFF);
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Ui.px(8);
        t.setLayoutParams(lp);
        t.setOnFocusChangeListener((v, f) -> {
            bg.setColor(f ? 0xFFFFFFFF : 0x33000000);
            t.setTextColor(f ? 0xFF111114 : 0xE6FFFFFF);
            if (f) poke();
        });
        t.setOnClickListener(v -> { r.run(); poke(); });
        return t;
    }

    void open(int i) {
        current = i;
        prefs.edit().putString("last", Places.id(i)).apply();
        browser.setVisibility(View.GONE);
        viewer.setVisibility(View.VISIBLE);
        offline.setVisibility(View.GONE);
        ambient.run(false);
        playing = false;
        liveState = -1;
        if (!webReady) {
            webReady = true;
            web.loadDataWithBaseURL("https://steroid.home/#" + Places.id(i) + "@" + resumeAt(i) + "@" + (sound ? 0 : 1), asset("window.html"), "text/html", "utf-8", null);
        } else {
            web.evaluateJavascript("load('" + Places.id(i) + "'," + resumeAt(i) + ")", null);
        }
        web.evaluateJavascript("setMuted(" + !sound + ")", null);
        paintInfo();
        controls.setVisibility(View.GONE);
        showInfo(7000);
        h.removeCallbacks(watchdog);
        h.postDelayed(watchdog, 30000);
        scheduleRotate();
    }

    /** Where to start a window: where you left it, or a minute in on a first visit (skips intro title cards). */
    int resumeAt(int i) { return prefs.getInt("pos_" + Places.id(i), Places.firstStart(i)); }

    void step(int dir) {
        if (visible.isEmpty()) fillGrid();
        List<Integer> list = visible.isEmpty() ? allIndexes() : visible;
        int pos = list.indexOf(current);
        int n = list.size();
        open(list.get(((pos < 0 ? 0 : pos) + dir + n) % n));
    }

    List<Integer> allIndexes() {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < Places.count(); i++) l.add(i);
        return l;
    }

    void paintInfo() {
        int i = current;
        vName.setText(Places.name(i));
        vWhere.setText((Places.where(i) + "  ·  " + localTime(i) + " local").toUpperCase());
        vNote.setText(Places.note(i));
        vNote.setVisibility(Places.note(i).isEmpty() ? View.GONE : View.VISIBLE);
        vSource.setText(("Camera: " + Places.source(i) + " · via YouTube").toUpperCase());
        paintBadge();
        favBtn.setText(favs.contains(Places.id(i)) ? "♥ Favourite" : "♡ Favourite");
        soundBtn.setText(sound ? "Sound on" : "Sound off");
        rotateBtn.setText(rotate == 0 ? "Wander: off" : "Wander: every " + ROTATE_MIN[rotate] + " min");
        int list = visible.isEmpty() ? Places.count() : visible.size();
        offTitle.setText(Places.name(i) + " isn't streaming right now.");
        offNext.setText("Look somewhere else →");
    }

    /** Only says LIVE when the player itself reports a live broadcast. */
    void paintBadge() {
        GradientDrawable bg = Ui.round(0, 4);
        if (!playing) { badge.setText("CONNECTING…"); bg.setColor(0x66000000); }
        else if (liveState == 1) { badge.setText("● LIVE"); bg.setColor(0xFFE5383B); }
        else if (liveState == 0) { badge.setText("RECORDED · NOT LIVE"); bg.setColor(0x80000000); }
        else { badge.setText("STREAMING · LIVE STATUS UNKNOWN"); bg.setColor(0x66000000); }
        badge.setBackground(bg);
    }

    void showInfo(long ms) {
        info.animate().cancel();
        info.setAlpha(1f);
        h.removeCallbacks(fade);
        h.postDelayed(fade, ms);
    }

    /** Any interaction keeps the overlay up a little longer. */
    void poke() { showInfo(controls.getVisibility() == View.VISIBLE ? 8000 : 5000); }

    final Runnable fade = () -> {
        info.animate().alpha(0f).setDuration(900).start();
        if (controls.getVisibility() == View.VISIBLE && !offlineShown()) {
            controls.animate().alpha(0f).setDuration(900).withEndAction(() -> {
                controls.setVisibility(View.GONE); controls.setAlpha(1f);
            }).start();
        }
    };

    boolean offlineShown() { return offline.getVisibility() == View.VISIBLE; }

    void showControls() {
        controls.setAlpha(1f);
        controls.setVisibility(View.VISIBLE);
        controls.getChildAt(0).requestFocus();
        showInfo(8000);
    }

    void toggleFav() {
        String id = Places.id(current);
        if (!favs.remove(id)) favs.add(id);
        prefs.edit().putStringSet("favs", new HashSet<>(favs)).apply();
        paintInfo();
    }

    void toggleSound() {
        sound = !sound;
        prefs.edit().putBoolean("sound", sound).apply();
        web.evaluateJavascript("setMuted(" + !sound + ")", null);
        paintInfo();
    }

    void cycleRotate() {
        rotate = (rotate + 1) % ROTATE_MIN.length;
        prefs.edit().putInt("rotate", rotate).apply();
        scheduleRotate();
        paintInfo();
    }

    void scheduleRotate() {
        h.removeCallbacks(rotateTick);
        if (rotate > 0) h.postDelayed(rotateTick, ROTATE_MIN[rotate] * 60_000L);
    }

    final Runnable rotateTick = () -> step(1);

    final Runnable watchdog = () -> { if (!playing) goneOffline(); };

    void goneOffline() {
        offline.setVisibility(View.VISIBLE);
        controls.setVisibility(View.GONE);
        offline.findFocus();
        ((LinearLayout) offline.getChildAt(2)).getChildAt(1).requestFocus();
        h.removeCallbacks(fade);
        info.setAlpha(0f);
    }

    class Bridge {
        /** Remembers how far into each recording you were, so a window resumes where you left it. */
        @JavascriptInterface public void position(String id, int seconds) {
            if (seconds > 30) prefs.edit().putInt("pos_" + id, seconds).apply();
        }
        @JavascriptInterface public void state(int s, int live) {
            h.post(() -> {
                if (live != -1) liveState = live;
                if (s == 1 && !playing) { playing = true; offline.setVisibility(View.GONE); }
                if (current >= 0) paintBadge();
            });
        }
        @JavascriptInterface public void error(int code) {
            h.post(() -> goneOffline());
        }
    }

    String asset(String name) {
        try (InputStream in = getAssets().open(name)) {
            byte[] b = new byte[in.available()];
            int n = in.read(b);
            return new String(b, 0, n, "UTF-8");
        } catch (Exception e) { return ""; }
    }

    // ---------- Remote ----------

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (viewer.getVisibility() != View.VISIBLE) {
            if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) { finish(); return true; }
            return super.dispatchKeyEvent(e);
        }
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE)
            return super.dispatchKeyEvent(e);
        boolean ctrls = controls.getVisibility() == View.VISIBLE || offlineShown();
        if (k == KeyEvent.KEYCODE_BACK) {
            if (!down) {
                if (controls.getVisibility() == View.VISIBLE) { controls.setVisibility(View.GONE); showInfo(2500); }
                else showBrowser();
            }
            return true;
        }
        if (ctrls) {
            if (down) poke();
            return super.dispatchKeyEvent(e); // D-pad moves between the buttons
        }
        if (!down) return true;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_CHANNEL_UP: case KeyEvent.KEYCODE_MEDIA_NEXT:
                if (e.getRepeatCount() == 0) step(1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_CHANNEL_DOWN: case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                if (e.getRepeatCount() == 0) step(-1); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN:
                if (e.getRepeatCount() == 0) { paintInfo(); showControls(); } return true;
        }
        return true;
    }

    @Override protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web.onResume();
        if (viewer.getVisibility() != View.VISIBLE) ambient.run(true);
    }

    @Override protected void onPause() {
        super.onPause();
        web.onPause();
        ambient.run(false);
        h.removeCallbacks(rotateTick);
    }

    @Override protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        web.destroy();
        super.onDestroy();
    }
}
