package tv.steroid.home;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/**
 * Where the TV is, for the sky. TVs have no GPS, so in order:
 *  1. Android's own last known location (network/fused), if the TV has one and we may read it;
 *  2. the city of the TV's internet address (ipapi.co, then ipwho.is);
 *  3. whatever was found last time, then config/sky.json.
 * Refreshed at most once a day; runs off the main thread.
 */
final class Locate {
    static final long DAY = 24 * 3600 * 1000L;

    /** {lat, lon} to draw with right now. */
    static double[] current(Context c) {
        SharedPreferences p = c.getSharedPreferences("sky", Context.MODE_PRIVATE);
        if (p.contains("lat")) return new double[]{Double.longBitsToDouble(p.getLong("lat", 0)), Double.longBitsToDouble(p.getLong("lon", 0))};
        double lat = 13.08, lon = 80.27; // Chennai
        if (Config.sky != null) { lat = Config.sky.optDouble("lat", lat); lon = Config.sky.optDouble("lon", lon); }
        return new double[]{lat, lon};
    }

    /** Looks the location up again if it's more than a day old; calls back on a background thread when it changes. */
    static void refresh(Context c, Runnable changed) {
        SharedPreferences p = c.getSharedPreferences("sky", Context.MODE_PRIVATE);
        if (System.currentTimeMillis() - p.getLong("at", 0) < DAY) return;
        Context app = c.getApplicationContext();
        new Thread(() -> {
            double[] l = fromSystem(app);
            String src = "system";
            if (l == null) { l = fromIp("https://ipapi.co/json/", "latitude", "longitude"); src = "ipapi.co"; }
            if (l == null) { l = fromIp("https://ipwho.is/", "latitude", "longitude"); src = "ipwho.is"; }
            if (l == null) return; // keep what we had; try again next time Home opens
            p.edit().putLong("lat", Double.doubleToRawLongBits(l[0])).putLong("lon", Double.doubleToRawLongBits(l[1]))
                .putLong("at", System.currentTimeMillis()).putString("source", src).apply();
            if (changed != null) changed.run();
        }).start();
    }

    static double[] fromSystem(Context c) {
        if (c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null;
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;
            Location best = null;
            for (String pr : lm.getAllProviders()) {
                Location l = lm.getLastKnownLocation(pr);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best == null || System.currentTimeMillis() - best.getTime() > 30 * DAY) return null;
            return new double[]{best.getLatitude(), best.getLongitude()};
        } catch (Exception e) { return null; }
    }

    static double[] fromIp(String url, String latKey, String lonKey) {
        HttpURLConnection h = null;
        try {
            h = (HttpURLConnection) new URL(url).openConnection();
            h.setConnectTimeout(6000);
            h.setReadTimeout(6000);
            h.setRequestProperty("User-Agent", "Horizon-TV-launcher");
            if (h.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (InputStream in = h.getInputStream()) {
                byte[] b = new byte[4096];
                for (int n; (n = in.read(b)) > 0; ) sb.append(new String(b, 0, n, "UTF-8"));
            }
            JSONObject o = new JSONObject(sb.toString());
            if (!o.has(latKey) || !o.has(lonKey)) return null;
            double lat = o.getDouble(latKey), lon = o.getDouble(lonKey);
            if (Math.abs(lat) > 90 || Math.abs(lon) > 180 || (lat == 0 && lon == 0)) return null;
            return new double[]{lat, lon};
        } catch (Exception e) {
            return null;
        } finally {
            if (h != null) h.disconnect();
        }
    }
}
