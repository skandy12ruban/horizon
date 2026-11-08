package tv.steroid.home;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;
import java.util.Random;

/**
 * Retro TV, built for a D-pad remote. Cartoons, music, TV shows, movies and comedy come from the
 * MyRetroTVs decade sites, which play underneath while this screen drives them (their channel
 * lineup, filters and player). Science, which those sites don't have, uses curated YouTube channels.
 * ◀ ▶ channel · ▲ ▼ category · OK guide · Back closes the guide, then goes home.
 */
public class Retro extends Activity {
    static final String[][] DECADES = {
        // short (also the site's subdomain), long, (unused), accent, blurb
        {"80s", "1980s", "", "#FF4FA8", "MTV, Saturday mornings and the video store."},
        {"90s", "1990s", "", "#8F6BFF", "Cartoon blocks, sitcoms and alternative rock."},
        {"00s", "2000s", "", "#3FA7FF", "Kids' TV, pop-punk and the DVD shelf."},
        {"10s", "2010s", "", "#37D6B4", "Viral hits, stand-up and YouTube science."},
    };
    /** Site filter checkboxes behind each category (Science has none: it plays our own channels). */
    static final String[][] FILTERS = {{"c"}, {"m"}, {"d", "z", "g", "t", "k"}, {"e", "f"}, {"s"}};
    static final int SCIENCE = 5;

    static final String WATCH = "(function(){if(window.__st)return;window.__st=1;"
        + "var l=document.getElementById('lcdbar');"
        + "new MutationObserver(function(){var t=l.textContent.trim();if(t)Native.label(t);})"
        + ".observe(l,{childList:true,characterData:true,subtree:true});"
        // The site picks the programme; we take its video id and play it in our own clean player,
        // so the site's player never runs (no YouTube overlays, far less work for the TV).
        + "var w=setInterval(function(){var p=window.YT&&YT.get&&YT.get('myytplayer');if(!p||!p.loadVideoById)return;"
        + "clearInterval(w);p.loadVideoById=function(a){Native.play(typeof a==='object'?a.videoId:a);};"
        + "p.playVideo=p.seekTo=function(){};p.mute();},300);"
        // The site never sees its own player start, so it would keep playing its "no signal" static
        // and click sounds forever: silence all of its sound effects.
        + "var m=setInterval(function(){if(window.Howler&&Howler.mute){Howler.mute(true);clearInterval(m);}},200);"
        + "})();";

    final Handler h = new Handler();
    SharedPreferences prefs;
    FrameLayout root;
    WebView web, tv;   // web: the decade site, hidden; tv: our player
    View full;
    WebChromeClient.CustomViewCallback fullCb;
    Static noise;
    View cover; // black until YouTube's start-up title bar and buttons have faded
    LinearLayout card, guide, chanRow;
    TextView cardDecade, cardCat, cardName, cardNow, bug;
    int decade, category, channel;
    int filtered = -1;
    String loaded = "";
    boolean playerReady, booted;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        if (Config.feature("debugWebViews")) WebView.setWebContentsDebuggingEnabled(true);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("retro", MODE_PRIVATE);
        decade = getIntent().hasExtra("decade") ? getIntent().getIntExtra("decade", 0) : prefs.getInt("decade", 0);
        category = prefs.getInt("category", 0);
        channel = prefs.getInt("channel", 0);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.setFocusable(true);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        // The decade sites are desktop pages; a desktop UA gets the full player and keyboard commands.
        s.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36");
        web.setBackgroundColor(0xFF000000);
        web.addJavascriptInterface(new Bridge(), "Native");
        web.setWebChromeClient(new WebChromeClient() {
            // The site's full-screen mode calls requestFullscreen(); show the view WebView hands back.
            @Override public void onShowCustomView(View v, CustomViewCallback cb) {
                if (full != null) { cb.onCustomViewHidden(); return; }
                full = v; fullCb = cb;
                root.addView(v, 1, new FrameLayout.LayoutParams(-1, -1));
            }
            @Override public void onHideCustomView() { dropFull(); }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                // Channel changes rewrite the URL hash and re-fire this; boot the set once per load.
                if (!url.contains("myretrotvs.com") || booted) return;
                booted = true;
                v.evaluateJavascript(WATCH, null);
                h.postDelayed(() -> js(key(32)), 1500);                    // power on
                h.postDelayed(() -> { applyFilter(); js(key(39)); }, 2500);
                h.postDelayed(Retro.this::ensurePower, 9000);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return !url.contains("myretrotvs.com");
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(Ui.px(2), Ui.px(2)));

        tv = new WebView(this);
        WebSettings ts = tv.getSettings();
        ts.setJavaScriptEnabled(true);
        ts.setMediaPlaybackRequiresUserGesture(false);
        tv.setBackgroundColor(0xFF000000);
        tv.setFocusable(false);
        tv.setWebChromeClient(new WebChromeClient());
        tv.setWebViewClient(new WebViewClient());
        tv.addJavascriptInterface(new Bridge(), "Native");
        root.addView(tv, new FrameLayout.LayoutParams(-1, -1));
        tv.loadDataWithBaseURL("https://steroid.home/", asset("retro.html"), "text/html", "utf-8", null);

        cover = new View(this);
        cover.setBackgroundColor(0xFF000000);
        root.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        noise = new Static(this);
        root.addView(noise, new FrameLayout.LayoutParams(-1, -1));
        buildBug();
        buildCard();
        buildGuide();
        setContentView(root);
        tune(0);
    }

    // ---------- Tuning ----------

    boolean science() { return category == SCIENCE; }

    void clamp() {
        decade = Math.max(0, Math.min(DECADES.length - 1, decade));
        category = ((category % Lineup.CATEGORIES.length) + Lineup.CATEGORIES.length) % Lineup.CATEGORIES.length;
        int n = science() ? Lineup.channels(decade, SCIENCE).length : 1;
        channel = ((channel % n) + n) % n;
    }

    /** dir: -1 previous channel, +1 next, 0 retune after a decade/category change. */
    void tune(int dir) {
        paused = false;
        clamp();
        prefs.edit().putInt("decade", decade).putInt("category", category).putInt("channel", channel).apply();
        noise.on(true);
        h.removeCallbacks(noiseOff);
        h.postDelayed(noiseOff, 400);
        cover.animate().cancel();
        cover.setAlpha(1f);
        h.removeCallbacks(reveal); // revealed only once a programme is really playing (never an idle player)
        cardName.setText(science() ? Lineup.channels(decade, SCIENCE)[channel][0] : "Channel surfing");
        cardNow.setText("Tuning in…");
        showCard(true);
        if (science()) {
            if (!playerReady) return; // tuned when the player reports ready
            String[] ch = Lineup.channels(decade, SCIENCE)[channel];
            StringBuilder ids = new StringBuilder("[");
            for (int i = 1; i < ch.length; i++) ids.append(i > 1 ? "," : "").append('\'').append(ch[i]).append('\'');
            tvJs("tune(" + ids.append(']') + ")");
        } else {
            String url = "https://" + DECADES[decade][0] + ".myretrotvs.com/";
            if (!loaded.equals(url)) {
                loaded = url; booted = false; filtered = -1;
                web.loadUrl(url); // the boot sequence applies the category and tunes a channel
                return;
            }
            if (!booted) return;
            if (filtered != category) { applyFilter(); js(key(39)); }
            else js(key(dir < 0 ? 37 : 39));
            h.removeCallbacks(ensurePowerRunnable);
            h.postDelayed(ensurePowerRunnable, 4000);
        }
    }

    void applyFilter() {
        if (science()) return;
        StringBuilder sb = new StringBuilder("document.getElementById('filter_none').click();");
        for (String id : FILTERS[category]) sb.append("document.getElementById('box_").append(id).append("').click();");
        js(sb.toString());
        filtered = category;
    }

    final Runnable noiseOff = () -> noise.on(false);
    final Runnable reveal = () -> cover.animate().alpha(0f).setDuration(500).start();

    final Runnable ensurePowerRunnable = this::ensurePower;

    /** If the set is still off (its label asks to turn it on), press power. */
    void ensurePower() {
        if (science() || !booted) return;
        web.evaluateJavascript("document.getElementById('lcdbar').textContent", v -> {
            if (v != null && v.toLowerCase().contains("turn on")) js(key(32));
        });
    }

    static String key(int which) {
        return "document.dispatchEvent(new KeyboardEvent('keydown',{keyCode:" + which + ",which:" + which + ",bubbles:true}));";
    }

    void js(String code) { web.evaluateJavascript(code, null); }
    void tvJs(String code) { tv.evaluateJavascript(code, null); }

    /** Real key input: the browser only allows full screen from a user gesture. */
    void nativeKey(int code) {
        web.requestFocus();
        web.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        web.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    /** Keyboard focus left inside YouTube's frame keeps its title bar and pause button showing. */
    void unfocus() {
        js("if(document.activeElement)document.activeElement.blur();window.focus();");
        web.clearFocus();
        if (full != null) full.clearFocus();
        root.requestFocus();
    }

    void dropFull() {
        if (full == null) return;
        root.removeView(full);
        full = null;
        if (fullCb != null) fullCb.onCustomViewHidden();
        fullCb = null;
    }

    class Bridge {
        // science player (assets/retro.html)
        @JavascriptInterface public void ready() { h.post(() -> { playerReady = true; if (science()) tune(0); }); }
        // the decade site chose a programme
        @JavascriptInterface public void play(String id) {
            h.post(() -> { if (!science() && id != null && !id.isEmpty()) tvJs("playId('" + id.replaceAll("[^A-Za-z0-9_-]", "") + "')"); });
        }
        // our player couldn't play it (removed / not embeddable) or it finished: next channel
        @JavascriptInterface public void skip() { h.post(() -> { if (!science()) js(key(39)); }); }
        @JavascriptInterface public void now(String title, String author) {
            h.post(() -> {
                noise.on(false);
                cardNow.setText(title.isEmpty() ? " " : title);
                showCard(false);
                h.removeCallbacks(reveal);
                h.postDelayed(reveal, 2800); // YouTube's start-up title bar has cleared by then
            });
        }
        @JavascriptInterface public void state(int s) { }
        // decade sites: their channel label, e.g. "CH  6217 CARTOON:Chilly Willy …"
        @JavascriptInterface public void label(String t) { h.post(() -> onLabel(t)); }
    }

    void onLabel(String t) {
        if (science()) return;
        String lower = t.toLowerCase();
        if (lower.contains("turn on") || lower.contains("begin")) return;
        int colon = t.indexOf(':');
        if (!t.startsWith("CH") || colon < 0) return; // status lines ("SHUFFLE MODE: ON" etc.)
        String num = t.substring(2, colon).trim().split("\\s+")[0];
        cardName.setText("Channel " + num);
        cardNow.setText(t.substring(colon + 1).trim());
        noise.on(false);
        showCard(false);
    }

    // ---------- On-screen pieces ----------

    void buildBug() {
        bug = Ui.label(this, "", 0x99FFFFFF);
        bug.setShadowLayer(Ui.px(6), 0, 0, 0xCC000000);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        lp.setMargins(0, Ui.px(28), Ui.px(36), 0);
        root.addView(bug, lp);
    }

    void buildCard() {
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.px(26), Ui.px(20), Ui.px(30), Ui.px(20));
        card.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xF00A0A0E, 0xB30A0A0E}));
        ((GradientDrawable) card.getBackground()).setCornerRadius(Ui.px(20));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.BOTTOM);
        cardDecade = Ui.text(this, Ui.serifItalic, 26, 0xFFFFFFFF);
        top.addView(cardDecade);
        cardCat = Ui.label(this, "", 0xB3FFFFFF);
        cardCat.setPadding(Ui.px(12), 0, 0, Ui.px(5));
        top.addView(cardCat);
        card.addView(top);
        cardName = Ui.text(this, Ui.serif, 34, 0xFFFFFFFF);
        cardName.setPadding(0, Ui.px(6), 0, Ui.px(4));
        card.addView(cardName);
        cardNow = Ui.text(this, Ui.sans, 14, 0xD9FFFFFF);
        cardNow.setMaxLines(2);
        cardNow.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(cardNow);
        TextView keys = Ui.label(this, "◀ ▶ channel   ▲ ▼ category   OK pause   hold OK guide   ← home", 0x80FFFFFF);
        keys.setPadding(0, Ui.px(14), 0, 0);
        card.addView(keys);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.px(600), -2, Gravity.BOTTOM | Gravity.START);
        lp.setMargins(Ui.px(40), 0, 0, Ui.px(36));
        root.addView(card, lp);
    }

    void showCard(boolean tuning) {
        int accent = Color.parseColor(DECADES[decade][3]);
        cardDecade.setText("’" + DECADES[decade][0]);
        cardDecade.setTextColor(accent);
        String cat = Lineup.CATEGORIES[category].toUpperCase();
        cardCat.setText(science() ? cat + "  ·  CH " + (channel + 1) + " OF " + Lineup.channels(decade, SCIENCE).length : cat);
        bug.setText("’" + DECADES[decade][0] + "  ·  " + cat);
        card.animate().cancel();
        card.setAlpha(1f);
        h.removeCallbacks(hideCard);
        if (guide.getVisibility() != View.VISIBLE) h.postDelayed(hideCard, tuning ? 9000 : 6000);
    }

    final Runnable hideCard = () -> card.animate().alpha(0f).setDuration(800).start();

    // ---------- Guide: every choice tunes straight away ----------

    void buildGuide() {
        guide = new LinearLayout(this);
        guide.setOrientation(LinearLayout.VERTICAL);
        guide.setPadding(Ui.px(52), Ui.px(40), Ui.px(40), Ui.px(30));
        guide.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xF70A0A0E, 0xEB0A0A0E, 0x400A0A0E}));
        guide.setClipChildren(false);
        guide.addView(Ui.label(this, "Retro TV · guide", 0x99FFFFFF));
        TextView t = Ui.text(this, Ui.serif, 38, 0xFFFFFFFF);
        t.setText("What's on?");
        t.setPadding(0, Ui.px(8), 0, Ui.px(8));
        guide.addView(t);

        guide.addView(section("Decade"));
        LinearLayout decs = row();
        for (int i = 0; i < DECADES.length; i++) {
            final int d = i;
            decs.addView(chip("’" + DECADES[i][0], 84, () -> { decade = d; channel = 0; closeGuide(); tune(0); },
                () -> d == decade, Color.parseColor(DECADES[i][3])));
        }
        guide.addView(decs);

        guide.addView(section("Category"));
        LinearLayout cats = row();
        for (int i = 0; i < Lineup.CATEGORIES.length; i++) {
            final int c = i;
            cats.addView(chip(Lineup.CATEGORIES[i], 104, () -> { category = c; channel = 0; closeGuide(); tune(0); },
                () -> c == category, 0xFFFFFFFF));
        }
        guide.addView(cats);

        guide.addView(section("Channels"));
        chanRow = row();
        guide.addView(chanRow);

        guide.setVisibility(View.GONE);
        root.addView(guide, new FrameLayout.LayoutParams(Ui.px(820), -1, Gravity.START));
    }

    TextView section(String s) {
        TextView t = Ui.label(this, s, 0x80FFFFFF);
        t.setPadding(0, Ui.px(14), 0, Ui.px(8));
        return t;
    }

    LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setClipChildren(false);
        return r;
    }

    interface Check { boolean on(); }

    TextView chip(String label, int widthDp, Runnable action, Check selected, int focusColor) {
        boolean serif = label.startsWith("’");
        TextView c = Ui.text(this, serif ? Ui.serifItalic : Ui.sans, serif ? 22 : 13, 0xE6FFFFFF);
        c.setText(label);
        c.setGravity(Gravity.CENTER);
        c.setSingleLine(true);
        c.setEllipsize(android.text.TextUtils.TruncateAt.END);
        c.setFocusable(true);
        c.setTag(selected);
        c.setBackground(Ui.round(0x14FFFFFF, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(widthDp), Ui.px(serif ? 46 : 38));
        lp.rightMargin = Ui.px(8);
        c.setLayoutParams(lp);
        c.setPadding(Ui.px(8), 0, Ui.px(8), 0);
        c.setOnFocusChangeListener((v, f) -> { Ui.lift(v, f, 1.08f); paint(c, focusColor); });
        c.setOnClickListener(v -> action.run());
        paint(c, focusColor);
        return c;
    }

    void paint(TextView c, int focusColor) {
        GradientDrawable bg = (GradientDrawable) c.getBackground();
        boolean f = c.isFocused(), sel = ((Check) c.getTag()).on();
        bg.setColor(f ? focusColor : sel ? 0x38FFFFFF : 0x14FFFFFF);
        bg.setStroke(sel && !f ? Ui.px(1.5f) : 0, 0xB3FFFFFF);
        c.setTextColor(f ? 0xFF111114 : 0xE6FFFFFF);
    }

    void refreshGuide() {
        for (int r = 0; r < guide.getChildCount(); r++) {
            View row = guide.getChildAt(r);
            if (!(row instanceof LinearLayout) || row == chanRow) continue;
            for (int i = 0; i < ((LinearLayout) row).getChildCount(); i++) {
                TextView c = (TextView) ((LinearLayout) row).getChildAt(i);
                if (!c.isFocused()) paint(c, 0xFFFFFFFF);
            }
        }
        chanRow.removeAllViews();
        if (!science()) {
            TextView note = Ui.text(this, Ui.serifItalic, 18, 0xB3FFFFFF);
            note.setText("Hundreds of channels from MyRetroTVs — flip through them with ◀ ▶.");
            chanRow.addView(note);
            return;
        }
        String[][] chans = Lineup.channels(decade, SCIENCE);
        int accent = Color.parseColor(DECADES[decade][3]);
        for (int i = 0; i < chans.length; i++) {
            final int k = i;
            chanRow.addView(chip(chans[i][0], 128, () -> { channel = k; closeGuide(); tune(0); }, () -> k == channel, accent));
        }
    }

    void openGuide() {
        refreshGuide();
        guide.setVisibility(View.VISIBLE);
        guide.setAlpha(0f);
        guide.setTranslationX(-Ui.px(40));
        guide.animate().alpha(1f).translationX(0).setDuration(200).start();
        View target = chanRow.getChildCount() > 0 && chanRow.getChildAt(0).isFocusable()
            ? chanRow.getChildAt(Math.min(channel, chanRow.getChildCount() - 1))
            : ((LinearLayout) guide.getChildAt(5)).getChildAt(category);
        target.requestFocus();
        card.setAlpha(0f);
    }

    void closeGuide() {
        guide.setVisibility(View.GONE);
        showCard(false);
    }

    // ---------- Pause ----------

    boolean paused, okHeld;
    boolean swallowUp; // the release of the long press that opened the guide must not click a chip
    final Runnable holdForGuide = () -> { okHeld = true; swallowUp = true; openGuide(); };

    void togglePause() {
        paused = !paused;
        tvJs("pause(" + paused + ")");
        cardNow.setTag(paused ? cardNow.getText() : null);
        // YouTube shows its title bar and buttons on a paused video: keep the picture covered
        h.removeCallbacks(reveal);
        cover.animate().cancel();
        if (paused) cover.setAlpha(1f); else h.postDelayed(reveal, 2800);
        if (paused) {
            cardName.setText("Paused");
            showCard(false);
            h.removeCallbacks(hideCard); // stays up while paused
        } else {
            cardName.setText(science() ? Lineup.channels(decade, SCIENCE)[channel][0] : "Channel surfing");
            showCard(false);
        }
    }

    // ---------- Remote ----------

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE)
            return super.dispatchKeyEvent(e);
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        if (swallowUp && ok && !down) { swallowUp = false; return true; }
        boolean guideOpen = guide.getVisibility() == View.VISIBLE;
        if (k == KeyEvent.KEYCODE_BACK) {
            if (!down) { if (guideOpen) closeGuide(); else finish(); }
            return true;
        }
        if (guideOpen) return super.dispatchKeyEvent(e);
        if (!down && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            h.removeCallbacks(holdForGuide);
            if (!okHeld) togglePause();
            return true;
        }
        if (!down || e.getRepeatCount() > 0) return true;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_CHANNEL_UP: case KeyEvent.KEYCODE_MEDIA_NEXT:
                channel++; tune(1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_CHANNEL_DOWN: case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                channel--; tune(-1); return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                category++; channel = 0; tune(0); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                category--; channel = 0; tune(0); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                // tap: pause / resume; hold: guide
                okHeld = false;
                h.postDelayed(holdForGuide, 600);
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_INFO: case KeyEvent.KEYCODE_GUIDE:
                openGuide(); return true;
        }
        return true;
    }

    String asset(String name) {
        try (InputStream in = getAssets().open(name)) {
            byte[] b = new byte[in.available()];
            int n = in.read(b);
            return new String(b, 0, n, "UTF-8");
        } catch (Exception e) { return ""; }
    }

    @Override protected void onPause() { super.onPause(); web.onPause(); noise.on(false); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        web.loadUrl("about:blank");
        web.destroy();
        super.onDestroy();
    }

    /** Analogue snow between channels: a small noise tile redrawn at random offsets. */
    static class Static extends View {
        final Bitmap tile = Bitmap.createBitmap(192, 108, Bitmap.Config.ARGB_8888);
        final Paint p = new Paint();
        final Random r = new Random();
        final Rect src = new Rect(), dst = new Rect();
        boolean on;

        Static(Context c) {
            super(c);
            int[] px = new int[192 * 108];
            for (int i = 0; i < px.length; i++) { int v = r.nextInt(256); px[i] = Color.rgb(v, v, v); }
            tile.setPixels(px, 0, 192, 0, 0, 192, 108);
        }

        void on(boolean v) {
            if (v) { on = true; animate().cancel(); setAlpha(1f); setVisibility(VISIBLE); invalidate(); }
            else if (on) animate().alpha(0f).setDuration(350).withEndAction(() -> { on = false; setVisibility(GONE); }).start();
        }

        void flicker() { if (!on) { setAlpha(0.35f); setVisibility(VISIBLE); on = true; postDelayed(() -> on(false), 180); } }

        @Override protected void onDraw(Canvas c) {
            int ox = r.nextInt(96), oy = r.nextInt(54);
            src.set(ox, oy, ox + 96, oy + 54);
            dst.set(0, 0, getWidth(), getHeight());
            c.drawBitmap(tile, src, dst, p);
            if (on) postInvalidateDelayed(40);
        }
    }
}
