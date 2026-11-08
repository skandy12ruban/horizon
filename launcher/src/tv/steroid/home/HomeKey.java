package tv.steroid.home;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Makes Horizon the home screen on Google TV without disabling anything. Google TV routes the
 * remote's Home button to its own launcher whatever the default is, so this service:
 *  - catches Home presses and opens Horizon instead;
 *  - swaps Horizon in when Google TV's home screen comes to the front (after a reboot, or when
 *    an app exits "to home").
 * Google TV stays installed and enabled. Turning this service off in Settings › Accessibility
 * (or uninstalling Horizon) gives the TV its original home screen back immediately.
 */
public class HomeKey extends AccessibilityService {
    static final String GOOGLE_HOME = "com.google.android.apps.tv.launcherx.home.HomeActivity";
    final android.os.Handler h = new android.os.Handler();
    long last;
    boolean googleOnTop;

    void openIfGoogleHome() { if (googleOnTop) { last = 0; openHorizon(); } }

    @Override protected void onServiceConnected() { openHorizon(); }

    @Override protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
            openHorizon();
            // Google TV may still bring its home up a moment later; its window event re-opens Horizon,
            // and these retries cover TVs where that event arrives late.
            h.postDelayed(this::openIfGoogleHome, 450);
            h.postDelayed(this::openIfGoogleHome, 1100);
        }
        return true; // consume both down and up so Google TV never sees it
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent ev) {
        if (ev.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            && ev.getClassName() != null && GOOGLE_HOME.contentEquals(ev.getClassName())) {
            googleOnTop = true;
            last = 0; // always answer Google TV's home screen, even right after a key press
            openHorizon();
        } else if (ev.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) googleOnTop = false;
    }

    void openHorizon() {
        long now = SystemClock.uptimeMillis();
        if (now - last < 150) return; // a key press and the window change it causes can arrive together
        last = now;
        try {
            startActivity(new Intent(this, Home.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        } catch (Exception ignored) {}
    }

    @Override public void onInterrupt() {}

    /** True while Settings › Accessibility has this service switched on. */
    static boolean enabled(Context c) {
        String on = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return on != null && on.contains(c.getPackageName() + "/" + HomeKey.class.getName());
    }
}
