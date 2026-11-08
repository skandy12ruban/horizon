package tv.steroid.home;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Polls Little Office in the background and shows one small, temporary notice when an agent needs
 * you or finishes. Events are grouped (a short window, then a cool-down) so an evening isn't
 * interrupted repeatedly. OK on the notice opens the glance; any other key dismisses it.
 */
public class OfficeService extends Service {
    static final long POLL_MS = 20_000, GROUP_MS = 30_000, COOLDOWN_MS = 5 * 60_000, SHOW_MS = 9_000;

    final Handler h = new Handler(Looper.getMainLooper());
    SharedPreferences prefs;
    final List<Office.Event> pending = new ArrayList<>();
    long pendingSince, lastToast;
    View toast;

    static void start(Context c) {
        try { c.startForegroundService(new Intent(c, OfficeService.class)); } catch (Exception ignored) {}
    }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel("office") == null)
            nm.createNotificationChannel(new NotificationChannel("office", "Little Office", NotificationManager.IMPORTANCE_MIN));
        startForeground(2, new Notification.Builder(this, "office").setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Little Office").build());
        prefs = getSharedPreferences("office", MODE_PRIVATE);
        h.removeCallbacks(tick);
        h.post(tick);
        return START_STICKY;
    }

    final Runnable tick = new Runnable() { public void run() {
        new Thread(() -> {
            boolean ok = Office.poll(OfficeService.this);
            h.post(() -> { if (ok) collect(); maybeShow(); });
        }).start();
        h.postDelayed(this, POLL_MS);
    }};

    void collect() {
        long seen = prefs.getLong("seenEvent", -1);
        if (seen < 0) { // first contact: everything so far is history, not news
            long max = 0;
            for (Office.Event e : Office.events) max = Math.max(max, e.id);
            prefs.edit().putLong("seenEvent", max).apply();
            return;
        }
        long max = seen;
        for (Office.Event e : Office.events) {
            if (e.id > seen) {
                if (pending.isEmpty()) pendingSince = System.currentTimeMillis();
                pending.add(e);
            }
            max = Math.max(max, e.id);
        }
        if (max != seen) prefs.edit().putLong("seenEvent", max).apply();
    }

    void maybeShow() {
        long now = System.currentTimeMillis();
        if (pending.isEmpty() || toast != null) return;
        if (now - pendingSince < GROUP_MS || now - lastToast < COOLDOWN_MS) return;
        if (!Settings.canDrawOverlays(this)) { pending.clear(); return; }
        show(new ArrayList<>(pending));
        pending.clear();
        lastToast = now;
    }

    void show(List<Office.Event> evs) {
        boolean anyNeeds = false;
        LinkedHashSet<String> projects = new LinkedHashSet<>();
        for (Office.Event e : evs) { anyNeeds |= e.kind.equals("needs_you"); projects.add(e.project); }
        String headline;
        if (evs.size() == 1) {
            Office.Event e = evs.get(0);
            headline = e.project + (e.kind.equals("needs_you") ? " needs you" : " finished");
        } else {
            List<String> p = new ArrayList<>(projects);
            headline = evs.size() + " updates · " + p.get(0) + (p.size() > 1 ? ", " + p.get(1) : "") + (p.size() > 2 ? " +" + (p.size() - 2) : "");
        }

        Ui.init(this);
        FrameLayout frame = new FrameLayout(this) {
            @Override public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_UP) return true;
                int k = e.getKeyCode();
                if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BUTTON_A)
                    startActivity(new Intent(OfficeService.this, OfficePanel.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                hide();
                return true;
            }
        };
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.px(18), Ui.px(12), Ui.px(22), Ui.px(13));
        GradientDrawable bg = Ui.round(0xE6101014, 16);
        bg.setStroke(Ui.px(1), anyNeeds ? 0x99FFB454 : 0x4DFFFFFF);
        card.setBackground(bg);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dot.setBackground(Ui.round(Office.stateColor(anyNeeds ? "needs_you" : "finished"), 4));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.px(8), Ui.px(8));
        dl.rightMargin = Ui.px(10);
        top.addView(dot, dl);
        top.addView(Ui.label(this, "Little Office", 0x99FFFFFF));
        card.addView(top);
        TextView title = Ui.text(this, Ui.serif, 19, 0xFFFFFFFF);
        title.setText(headline);
        title.setPadding(0, Ui.px(4), 0, Ui.px(2));
        card.addView(title);
        TextView sub = Ui.text(this, Ui.sans, 11.5f, 0x99FFFFFF);
        sub.setText("OK to see why · any other key to dismiss");
        card.addView(sub);
        frame.addView(card);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-2, -2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = Ui.px(36);
        lp.y = Ui.px(30);
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(frame, lp);
            toast = frame;
            frame.setAlpha(0f);
            frame.animate().alpha(1f).setDuration(300).start();
            h.postDelayed(this::hide, SHOW_MS);
        } catch (Exception ignored) { toast = null; }
    }

    void hide() {
        if (toast == null) return;
        View v = toast;
        toast = null;
        h.removeCallbacksAndMessages(null);
        h.postDelayed(tick, POLL_MS);
        try { ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(v); } catch (Exception ignored) {}
    }

    @Override public void onDestroy() { h.removeCallbacksAndMessages(null); hide(); super.onDestroy(); }
    @Override public IBinder onBind(Intent i) { return null; }
}
