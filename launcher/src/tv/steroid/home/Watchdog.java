package tv.steroid.home;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

/**
 * Keeps Horizon's Home button working on TCL TVs. TCL's memory guard kills apps shortly after
 * boot; if that hits while Android is starting HomeKey, Android marks the service "crashed" and
 * never restarts it. This re-writes the accessibility list (which makes Android bind it again),
 * but only when the user still has HomeKey switched on — it never turns the service on itself,
 * so switching it off in Settings or with horizon-off.sh always sticks.
 *
 * Needs WRITE_SECURE_SETTINGS, granted once over adb by scripts/horizon-on.sh.
 */
public class Watchdog extends BroadcastReceiver {
    static final String CHECK = "tv.steroid.home.CHECK";

    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            later(c, 20_000, 1);
            later(c, 60_000, 2);
        }
        ensure(c);
    }

    static void later(Context c, long ms, int id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(c, id, new Intent(c, Watchdog.class).setAction(CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (am != null) am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + ms, pi);
    }

    /** If HomeKey is switched on but not running, nudge Android to bind it again. */
    static void ensure(Context c) {
        if (c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return;
        if (!HomeKey.enabled(c) || running(c)) return;
        try {
            String key = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES;
            String cur = Settings.Secure.getString(c.getContentResolver(), key);
            Settings.Secure.putString(c.getContentResolver(), key, "");
            Settings.Secure.putString(c.getContentResolver(), key, cur);
            Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (Exception ignored) {}
    }

    static boolean running(Context c) {
        AccessibilityManager am = (AccessibilityManager) c.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        String me = c.getPackageName() + "/" + HomeKey.class.getName();
        for (AccessibilityServiceInfo s : am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK))
            if (s.getId() != null && (s.getId().equals(me) || s.getId().equals(c.getPackageName() + "/.HomeKey"))) return true;
        return false;
    }
}
