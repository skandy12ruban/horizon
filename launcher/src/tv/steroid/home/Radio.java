package tv.steroid.home;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Station browser and full-screen Now Playing with a live spectrum. */
public class Radio extends Activity {
    FrameLayout root, browser, nowPlaying;
    Ambient ambient;
    Spectrum spectrum;
    TextView npIndex, npName, npTrack, npGenre, npStatus, npVis, onAirName, onAirTrack;
    View onAir;
    GridLayout grid;
    View[] tiles = new View[Stations.count()];
    boolean showingNow;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);

        root = new FrameLayout(this);
        ambient = new Ambient(this);
        root.addView(ambient);
        buildBrowser();
        buildNowPlaying();
        setContentView(root);

        if (getIntent().getBooleanExtra("nowPlaying", false) && RadioService.station >= 0) showNow(true);
        else showNow(false);
    }

    // ---------- Browser ----------

    void buildBrowser() {
        browser = new FrameLayout(this);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(Ui.px(56), Ui.px(44), Ui.px(40), 0);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(Ui.label(this, "Radio · " + Stations.count() + " stations", 0x99FFFFFF));
        TextView title = Ui.text(this, Ui.serif, 58, 0xFFFFFFFF);
        title.setText("Tune in.");
        title.setPadding(0, Ui.px(14), 0, Ui.px(10));
        left.addView(title);
        TextView sub = Ui.text(this, Ui.sans, 13.5f, 0xB3FFFFFF);
        sub.setLineSpacing(Ui.px(4), 1f);
        sub.setText("Kollywood, Tollywood and Bollywood first, then lo-fi, ambient and electronic.");
        left.addView(sub);

        LinearLayout air = new LinearLayout(this);
        air.setOrientation(LinearLayout.VERTICAL);
        air.setFocusable(true);
        air.setPadding(Ui.px(18), Ui.px(14), Ui.px(18), Ui.px(16));
        GradientDrawable airBg = Ui.round(0x1FFFFFFF, 16);
        air.setBackground(airBg);
        air.addView(Ui.label(this, "● On air", 0xFFFF6B5A));
        onAirName = Ui.text(this, Ui.serifItalic, 24, 0xFFFFFFFF);
        onAirName.setPadding(0, Ui.px(8), 0, Ui.px(4));
        air.addView(onAirName);
        onAirTrack = Ui.text(this, Ui.sans, 12, 0xB3FFFFFF);
        onAirTrack.setMaxLines(2);
        air.addView(onAirTrack);
        air.setOnFocusChangeListener((v, f) -> { airBg.setColor(f ? 0x40FFFFFF : 0x1FFFFFFF); Ui.lift(v, f, 1.04f); });
        air.setOnClickListener(v -> showNow(true));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = Ui.px(28);
        left.addView(air, alp);
        onAir = air;
        row.addView(left, new LinearLayout.LayoutParams(Ui.px(250), -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(Ui.px(16), Ui.px(8), Ui.px(16), Ui.px(48));
        grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setClipChildren(false);
        for (int i = 0; i < Stations.count(); i++) grid.addView(tile(i));
        scroll.addView(grid);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -1, 1f);
        slp.leftMargin = Ui.px(28);
        row.addView(scroll, slp);
        browser.addView(row, new FrameLayout.LayoutParams(-1, -1));
        root.addView(browser);
    }

    View tile(int i) {
        int hue = Stations.HUE[i];
        FrameLayout t = new FrameLayout(this);
        t.setFocusable(true);
        t.setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Ui.withAlpha(hue, 0xE6), Ambient.blend(hue, 0xFF120E18, 0.72f)});
        bg.setCornerRadius(Ui.px(16));
        t.setBackground(bg);

        TextView num = Ui.text(this, Ui.mono, 10, 0x99FFFFFF);
        num.setText(String.format("%02d", i + 1));
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        nlp.setMargins(0, Ui.px(12), Ui.px(14), 0);
        t.addView(num, nlp);

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(Ui.px(16), 0, Ui.px(16), Ui.px(14));
        String[] parts = Stations.name(i).split(" — ");
        TextView name = Ui.text(this, Ui.serif, 22, 0xFFFFFFFF);
        name.setText(parts[0]);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        txt.addView(name);
        TextView g = Ui.label(this, parts.length > 1 ? parts[1] : Stations.genre(i).replace(" | ", " · "), 0xB3FFFFFF);
        g.setTextSize(9.5f);
        g.setMaxLines(1);
        g.setPadding(0, Ui.px(6), 0, 0);
        txt.addView(g);
        t.addView(txt, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = Ui.px(166); lp.height = Ui.px(96);
        lp.setMargins(Ui.px(7), Ui.px(7), Ui.px(7), Ui.px(7));
        t.setLayoutParams(lp);
        t.setOnFocusChangeListener((v, f) -> {
            Ui.lift(v, f, 1.07f);
            bg.setStroke(f ? Ui.px(2) : 0, 0xFFFFFFFF);
            if (f) ambient.tint(hue);
        });
        t.setOnClickListener(v -> {
            if (RadioService.station != i || RadioService.state == RadioService.ERROR) RadioService.play(this, i);
            showNow(true);
        });
        tiles[i] = t;
        return t;
    }

    // ---------- Now Playing ----------

    void buildNowPlaying() {
        nowPlaying = new FrameLayout(this);
        nowPlaying.setBackgroundColor(0xE6060609);
        spectrum = new Spectrum(this);
        spectrum.onLiveChange = this::refresh;
        nowPlaying.addView(spectrum, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(Ui.px(64), Ui.px(52), Ui.px(64), 0);
        npIndex = Ui.label(this, "", 0x99FFFFFF);
        info.addView(npIndex);
        npName = Ui.text(this, Ui.serif, 64, 0xFFFFFFFF);
        npName.setPadding(0, Ui.px(14), 0, Ui.px(8));
        info.addView(npName);
        npTrack = Ui.text(this, Ui.sans, 18, 0xE6FFFFFF);
        npTrack.setMaxLines(1);
        npTrack.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(npTrack);
        npGenre = Ui.label(this, "", 0x80FFFFFF);
        npGenre.setPadding(0, Ui.px(12), 0, 0);
        info.addView(npGenre);
        nowPlaying.addView(info, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setGravity(Gravity.END);
        npStatus = Ui.label(this, "", 0xFFFFFFFF);
        npStatus.setTextSize(12);
        status.addView(npStatus);
        npVis = Ui.label(this, "", 0x80FFFFFF);
        npVis.setPadding(0, Ui.px(8), 0, 0);
        status.addView(npVis);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        slp.setMargins(0, Ui.px(56), Ui.px(64), 0);
        nowPlaying.addView(status, slp);

        TextView keys = Ui.label(this, "◀ ▶  station      OK  pause      ▲  sleep timer      ▼  stop      BACK  stations", 0x66FFFFFF);
        FrameLayout.LayoutParams klp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        klp.bottomMargin = Ui.px(22);
        nowPlaying.addView(keys, klp);
        root.addView(nowPlaying);
    }

    void showNow(boolean now) {
        showingNow = now;
        nowPlaying.setVisibility(now ? View.VISIBLE : View.GONE);
        browser.setVisibility(now ? View.GONE : View.VISIBLE);
        spectrum.run(now && hasWindowFocus());
        ambient.run(!now);
        if (!now) {
            int s = RadioService.station;
            View target = onAir.getVisibility() == View.VISIBLE && s >= 0 ? tiles[s] : tiles[0];
            target.requestFocus();
        }
        refresh();
    }

    void refresh() {
        int s = RadioService.station;
        boolean on = s >= 0;
        onAir.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) {
            onAirName.setText(Stations.name(s));
            onAirTrack.setText(RadioService.track.isEmpty() ? stateText() : RadioService.track);
            npIndex.setText(String.format("NOW PLAYING · %02d / %02d", s + 1, Stations.count()));
            npName.setText(Stations.name(s));
            npTrack.setText(RadioService.track.isEmpty() ? " " : RadioService.track);
            npGenre.setText(Stations.genre(s).replace(" | ", " · ").toUpperCase());
            int sl = RadioService.sleepMinutes();
            npStatus.setText(stateText().toUpperCase() + (sl > 0 ? "   ·   SLEEP IN " + sl + " MIN" : ""));
            spectrum.accent(Stations.HUE[s]);
            ambient.tint(Stations.HUE[s]);
            boolean attached = RadioService.state == RadioService.PLAYING && hasAudioPermission()
                && spectrum.attach(RadioService.sessionId);
            npVis.setText(!hasAudioPermission() ? "VISUALIZER OFF · NO AUDIO PERMISSION"
                : spectrum.live() ? "LIVE SPECTRUM · FROM THE AUDIO OUTPUT"
                : attached ? "VISUALIZER · WAITING FOR AUDIO" : "VISUALIZER · IDLE");
        }
    }

    String stateText() {
        switch (RadioService.state) {
            case RadioService.BUFFERING: return "Tuning…";
            case RadioService.PLAYING: return "● Live";
            case RadioService.PAUSED: return "Paused";
            case RadioService.ERROR: return "Station unreachable";
            default: return "";
        }
    }

    boolean hasAudioPermission() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (showingNow && e.getAction() == KeyEvent.ACTION_DOWN) {
            int s = RadioService.station, n = Stations.count();
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_MEDIA_NEXT: case KeyEvent.KEYCODE_CHANNEL_UP:
                    if (s >= 0) RadioService.play(this, (s + 1) % n); return true;
                case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_MEDIA_PREVIOUS: case KeyEvent.KEYCODE_CHANNEL_DOWN:
                    if (s >= 0) RadioService.play(this, (s - 1 + n) % n); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    RadioService.toggle(this); return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (s >= 0) RadioService.cycleSleep(this); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_MEDIA_STOP:
                    spectrum.detach(); RadioService.stop(this); showNow(false); return true;
                case KeyEvent.KEYCODE_BACK:
                    showNow(false); return true;
            }
        }
        if (showingNow && e.getAction() == KeyEvent.ACTION_UP && e.getKeyCode() == KeyEvent.KEYCODE_BACK) return true;
        return super.dispatchKeyEvent(e);
    }

    @Override public void onRequestPermissionsResult(int c, String[] p, int[] r) { refresh(); }

    @Override protected void onResume() {
        super.onResume();
        RadioService.listener = this::refresh;
        refresh();
    }

    @Override public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        spectrum.run(f && showingNow);
        ambient.run(f && !showingNow);
    }

    @Override protected void onPause() {
        super.onPause();
        if (RadioService.listener != null) RadioService.listener = null;
        spectrum.run(false);
        ambient.run(false);
        spectrum.detach();
    }
}
