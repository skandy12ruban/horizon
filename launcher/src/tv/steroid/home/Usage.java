package tv.steroid.home;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orders the home row by how much each app is actually used: screen time over the last two
 * weeks from Android's usage stats (when Horizon may read them), plus Horizon's own launch counts,
 * which fade by half each week. Ties keep the order from config/apps.json.
 */
final class Usage {
    static final long DAY = 24 * 3600 * 1000L, HALF_LIFE = 7 * DAY;

    static void launched(Context c, String pkg) {
        SharedPreferences p = c.getSharedPreferences("usage", Context.MODE_PRIVATE);
        p.edit().putFloat(pkg, (float) (decayed(p, pkg) + 1)).putLong(pkg + "@", System.currentTimeMillis()).apply();
    }

    static double decayed(SharedPreferences p, String pkg) {
        long at = p.getLong(pkg + "@", 0);
        if (at == 0) return 0;
        return p.getFloat(pkg, 0) * Math.pow(0.5, (System.currentTimeMillis() - at) / (double) HALF_LIFE);
    }

    static boolean canReadStats(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    /** The given {package, label, accent} rows, most used first. */
    static List<String[]> sort(Context c, List<String[]> apps) {
        Map<String, Long> minutes = new HashMap<>();
        if (canReadStats(c)) {
            try {
                UsageStatsManager um = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
                long now = System.currentTimeMillis();
                Map<String, UsageStats> m = um.queryAndAggregateUsageStats(now - 14 * DAY, now);
                for (Map.Entry<String, UsageStats> e : m.entrySet())
                    minutes.put(e.getKey(), e.getValue().getTotalTimeInForeground() / 60000);
            } catch (Exception ignored) {}
        }
        SharedPreferences p = c.getSharedPreferences("usage", Context.MODE_PRIVATE);
        final Map<String, Double> score = new HashMap<>();
        for (String[] a : apps) {
            Long min = minutes.get(a[0]);
            score.put(a[0], (min == null ? 0 : min) + decayed(p, a[0]) * 20); // a launch ≈ 20 minutes
        }
        List<String[]> out = new ArrayList<>(apps);
        Collections.sort(out, (x, y) -> Double.compare(score.get(y[0]), score.get(x[0]))); // stable: ties keep order
        return out;
    }
}
