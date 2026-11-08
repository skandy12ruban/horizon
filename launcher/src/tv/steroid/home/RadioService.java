package tv.steroid.home;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Streams one station with the platform MediaPlayer; keeps playing while you browse. */
public class RadioService extends Service {
    static final int IDLE = 0, BUFFERING = 1, PLAYING = 2, PAUSED = 3, ERROR = 4;
    static int station = -1, state = IDLE, sessionId = 0;
    static String track = "";
    static Runnable listener;
    /** Sleep timer: wall-clock time the radio stops (0 = off). The last minute fades out. */
    static long sleepAt = 0;
    static final int[] SLEEP_STEPS = {0, 15, 30, 60, 90};

    static final Handler main = new Handler(Looper.getMainLooper());
    MediaPlayer mp;
    WifiManager.WifiLock wifi;
    Thread meta;
    int retries, generation;

    static void play(Context c, int i) { send(c, "play", i); }
    static void toggle(Context c) { send(c, "toggle", station); }
    static void stop(Context c) { send(c, "stop", station); }

    /** Steps the sleep timer: off → 15 → 30 → 60 → 90 min → off. */
    static void cycleSleep(Context c) {
        int cur = sleepAt == 0 ? 0 : (int) Math.ceil((sleepAt - System.currentTimeMillis()) / 60000.0);
        int next = 0;
        for (int m : SLEEP_STEPS) if (m > cur + 1) { next = m; break; } // a running timer moves up a step
        if (sleepAt == 0) next = SLEEP_STEPS[1];
        send(c, "sleep", next);
    }

    /** Minutes left on the sleep timer, or 0 when it's off. */
    static int sleepMinutes() {
        return sleepAt == 0 ? 0 : Math.max(1, (int) Math.ceil((sleepAt - System.currentTimeMillis()) / 60000.0));
    }

    final Runnable sleepTick = new Runnable() { public void run() {
        if (sleepAt == 0) return;
        long left = sleepAt - System.currentTimeMillis();
        if (left <= 0) { sleepAt = 0; stopSelf(); return; }
        if (mp != null && left < 60000) { float v = left / 60000f; try { mp.setVolume(v, v); } catch (Exception ignored) {} }
        changed();
        main.postDelayed(this, left < 60000 ? 1000 : 15000);
    }};

    static void send(Context c, String action, int i) {
        Intent it = new Intent(c, RadioService.class).setAction(action).putExtra("i", i);
        c.startForegroundService(it);
    }

    static void changed() {
        android.util.Log.i("SteroidRadio", station + " state=" + state + " track=" + track);
        if (listener != null) main.post(listener); }

    @Override public int onStartCommand(Intent it, int flags, int id) {
        Stations.load(this);
        startForeground(1, note());
        String a = it == null ? "" : it.getAction();
        if ("play".equals(a)) { retries = 0; start(it.getIntExtra("i", 0)); }
        else if ("toggle".equals(a)) {
            if (mp != null && state == PLAYING) { mp.pause(); state = PAUSED; }
            else if (mp != null && state == PAUSED) { mp.start(); state = PLAYING; }
            else if (station >= 0) start(station);
            changed();
        } else if ("stop".equals(a)) { stopSelf(); }
        else if ("sleep".equals(a)) {
            int m = it.getIntExtra("i", 0);
            main.removeCallbacks(sleepTick);
            sleepAt = m <= 0 ? 0 : System.currentTimeMillis() + m * 60000L;
            if (mp != null) try { mp.setVolume(1f, 1f); } catch (Exception ignored) {}
            if (sleepAt > 0) main.post(sleepTick);
            changed();
        }
        return START_NOT_STICKY;
    }

    void start(int i) {
        release();
        station = i; state = BUFFERING; track = ""; changed();
        final int gen = ++generation;
        // MediaPlayer won't follow redirects to another host (Kalizo does that), so resolve first.
        new Thread(() -> {
            String u = resolve(Stations.url(i));
            main.post(() -> { if (gen == generation) open(i, u); });
        }).start();
        if (wifi == null) {
            wifi = ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE))
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "steroid-radio");
        }
        if (!wifi.isHeld()) wifi.acquire();
        startForeground(1, note());
        pollMetadata(i);
    }

    static String resolve(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(8000);
            c.getResponseCode();
            return c.getURL().toString();
        } catch (Exception e) { return url; }
        finally { if (c != null) c.disconnect(); }
    }

    void open(int i, String url) {
        release();
        mp = new MediaPlayer();
        mp.setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        mp.setOnPreparedListener(p -> { p.start(); state = PLAYING; retries = 0; changed(); });
        mp.setOnErrorListener((p, what, extra) -> { fail(i); return true; });
        mp.setOnCompletionListener(p -> fail(i)); // live streams shouldn't end: reconnect
        try {
            mp.setDataSource(url);
            sessionId = mp.getAudioSessionId();
            mp.prepareAsync();
        } catch (Exception e) { fail(i); }
        changed();
    }

    void fail(int i) {
        if (station != i) return;
        if (retries++ < 3) { main.postDelayed(() -> { if (station == i) start(i); }, 2000L * retries); return; }
        release();
        state = ERROR; changed();
    }

    /** Reads ICY "StreamTitle" every 20s on a side connection (a few KB each time). */
    void pollMetadata(int i) {
        if (meta != null) meta.interrupt();
        meta = new Thread(() -> {
            while (station == i && !Thread.currentThread().isInterrupted()) {
                String t = readIcy(Stations.url(i));
                if (station != i) break;
                if (t != null && !t.equals(track)) { track = t; changed(); }
                try { Thread.sleep(20000); } catch (InterruptedException e) { break; }
            }
        });
        meta.setDaemon(true);
        meta.start();
    }

    static String readIcy(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestProperty("Icy-MetaData", "1");
            c.setConnectTimeout(8000); c.setReadTimeout(8000);
            String mi = c.getHeaderField("icy-metaint");
            if (mi == null) return null;
            int metaint = Integer.parseInt(mi.trim());
            InputStream in = c.getInputStream();
            long skip = metaint;
            byte[] buf = new byte[8192];
            while (skip > 0) { int n = in.read(buf, 0, (int) Math.min(buf.length, skip)); if (n < 0) return null; skip -= n; }
            int len = in.read() * 16;
            if (len <= 0) return null;
            byte[] m = new byte[len];
            int off = 0;
            while (off < len) { int n = in.read(m, off, len - off); if (n < 0) break; off += n; }
            String s = new String(m, 0, off, "UTF-8");
            int a = s.indexOf("StreamTitle='");
            if (a < 0) return null;
            int b = s.indexOf("';", a + 13);
            String t = (b < 0 ? s.substring(a + 13) : s.substring(a + 13, b)).trim();
            return t.isEmpty() || t.equals("-") ? null : t;
        } catch (Exception e) { return null; }
        finally { if (c != null) c.disconnect(); }
    }

    Notification note() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel("radio") == null)
            nm.createNotificationChannel(new NotificationChannel("radio", "Radio", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0,
            new Intent(this, Radio.class).putExtra("nowPlaying", true), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "radio")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(station >= 0 ? Stations.name(station) : "Radio")
            .setContentText(track).setContentIntent(open).build();
    }

    void release() {
        if (mp != null) { mp.release(); mp = null; }
        sessionId = 0;
    }

    @Override public void onDestroy() {
        generation++;
        if (meta != null) meta.interrupt();
        main.removeCallbacksAndMessages(null);
        release();
        if (wifi != null && wifi.isHeld()) wifi.release();
        station = -1; state = IDLE; track = ""; sleepAt = 0;
        changed();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
