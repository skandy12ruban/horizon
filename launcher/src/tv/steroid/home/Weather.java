package tv.steroid.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Current weather for the TV's location from Open-Meteo (free, no key), cached for 30 minutes.
 * WMO weather codes are turned into a short phrase and one of a few drawn icons.
 */
final class Weather {
    static final long FRESH = 30 * 60 * 1000L;

    static final class Now {
        int temp, high, low, code;
        boolean day;
        String phrase() { return phrase(code); }

        static String phrase(int c) {
            if (c == 0) return "Clear";
            if (c == 1) return "Mainly clear";
            if (c == 2) return "Partly cloudy";
            if (c == 3) return "Overcast";
            if (c == 45 || c == 48) return "Fog";
            if (c >= 51 && c <= 57) return "Drizzle";
            if (c == 61 || c == 80) return "Light rain";
            if (c == 63 || c == 81) return "Rain";
            if (c == 65 || c == 82) return "Heavy rain";
            if (c == 66 || c == 67) return "Freezing rain";
            if (c >= 71 && c <= 77 || c == 85 || c == 86) return "Snow";
            if (c >= 95) return "Thunderstorm";
            return "—";
        }
    }

    /** Last known weather, or null if there's never been any. */
    static Now cached(Context c) {
        SharedPreferences p = c.getSharedPreferences("weather", Context.MODE_PRIVATE);
        if (!p.contains("temp")) return null;
        Now n = new Now();
        n.temp = p.getInt("temp", 0); n.high = p.getInt("high", 0); n.low = p.getInt("low", 0);
        n.code = p.getInt("code", 0); n.day = p.getBoolean("day", true);
        return n;
    }

    /** Fetches fresh weather if the cache is older than 30 minutes; calls back (off the main thread) when it changes. */
    static void refresh(Context c, Runnable changed) {
        SharedPreferences p = c.getSharedPreferences("weather", Context.MODE_PRIVATE);
        if (System.currentTimeMillis() - p.getLong("at", 0) < FRESH) return;
        double[] at = Locate.current(c);
        new Thread(() -> {
            HttpURLConnection h = null;
            try {
                String url = String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                    + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min"
                    + "&timezone=auto&forecast_days=1", at[0], at[1]);
                h = (HttpURLConnection) new URL(url).openConnection();
                h.setConnectTimeout(8000); h.setReadTimeout(8000);
                h.setRequestProperty("User-Agent", "Horizon-TV-launcher");
                if (h.getResponseCode() != 200) return;
                StringBuilder sb = new StringBuilder();
                try (InputStream in = h.getInputStream()) {
                    byte[] b = new byte[4096];
                    for (int n; (n = in.read(b)) > 0; ) sb.append(new String(b, 0, n, "UTF-8"));
                }
                JSONObject o = new JSONObject(sb.toString()), cur = o.getJSONObject("current"), d = o.getJSONObject("daily");
                p.edit()
                    .putInt("temp", (int) Math.round(cur.getDouble("temperature_2m")))
                    .putInt("code", cur.getInt("weather_code"))
                    .putBoolean("day", cur.optInt("is_day", 1) == 1)
                    .putInt("high", (int) Math.round(d.getJSONArray("temperature_2m_max").getDouble(0)))
                    .putInt("low", (int) Math.round(d.getJSONArray("temperature_2m_min").getDouble(0)))
                    .putLong("at", System.currentTimeMillis()).apply();
                if (changed != null) changed.run();
            } catch (Exception ignored) {
                // keep the last reading; try again next refresh
            } finally {
                if (h != null) h.disconnect();
            }
        }).start();
    }

    /** A small line-drawn weather icon in the launcher's white. */
    static class Icon extends View {
        final Paint s = new Paint(Paint.ANTI_ALIAS_FLAG), f = new Paint(Paint.ANTI_ALIAS_FLAG);
        int code; boolean day = true;

        Icon(Context c) {
            super(c);
            s.setStyle(Paint.Style.STROKE);
            s.setStrokeCap(Paint.Cap.ROUND);
            s.setColor(0xF2FFFFFF);
            f.setColor(0xF2FFFFFF);
        }

        void set(int code, boolean day) { this.code = code; this.day = day; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), u = Math.min(w, h) / 24f;
            s.setStrokeWidth(1.8f * u);
            boolean clear = code <= 1, partly = code == 2, fog = code == 45 || code == 48;
            boolean rain = code >= 51 && code <= 67 || code >= 80 && code <= 82, snow = code >= 71 && code <= 77 || code == 85 || code == 86;
            boolean storm = code >= 95;
            if (clear || partly) { // sun or moon, top-left when a cloud joins it
                float cx = partly ? 9 * u : w / 2, cy = partly ? 9 * u : h / 2, r = (partly ? 4.2f : 5.2f) * u;
                if (day) {
                    c.drawCircle(cx, cy, r, s);
                    for (int i = 0; i < 8; i++) {
                        double a = i * Math.PI / 4;
                        float r1 = r + 2.4f * u, r2 = r + 4.6f * u;
                        c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                            cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, s);
                    }
                } else {
                    RectF o = new RectF(cx - r - u, cy - r - u, cx + r + u, cy + r + u);
                    c.drawArc(o, 60, 240, false, s);
                    c.drawArc(new RectF(cx - r * 0.2f, cy - r - u * 1.2f, cx + r * 1.6f, cy + r + u * 0.4f), 120, 120, false, s);
                }
                if (clear) return;
            }
            if (fog) {
                for (int i = 0; i < 3; i++) c.drawLine(4 * u + (i % 2) * 2 * u, (9 + i * 4) * u, 20 * u - ((i + 1) % 2) * 2 * u, (9 + i * 4) * u, s);
                return;
            }
            // cloud
            float top = (rain || snow || storm) ? 5 * u : 8 * u;
            android.graphics.Path p = new android.graphics.Path();
            p.moveTo(7 * u, top + 11 * u);
            p.cubicTo(3 * u, top + 11 * u, 3 * u, top + 5 * u, 7.5f * u, top + 5 * u);
            p.cubicTo(8.5f * u, top + 0.5f * u, 16 * u, top + 0.5f * u, 16.5f * u, top + 4.5f * u);
            p.cubicTo(21 * u, top + 4.5f * u, 21.5f * u, top + 11 * u, 17 * u, top + 11 * u);
            p.close();
            c.drawPath(p, s);
            float y0 = top + 14 * u;
            if (rain) for (int i = 0; i < 3; i++) c.drawLine((8 + i * 4) * u, y0, (7 + i * 4) * u, y0 + 3.5f * u, s);
            if (snow) for (int i = 0; i < 3; i++) c.drawCircle((8 + i * 4) * u, y0 + 1.5f * u, 1.1f * u, f);
            if (storm) {
                android.graphics.Path b = new android.graphics.Path();
                b.moveTo(13 * u, y0 - u); b.lineTo(10 * u, y0 + 3 * u); b.lineTo(13 * u, y0 + 3 * u); b.lineTo(11 * u, y0 + 6.5f * u);
                c.drawPath(b, s);
            }
        }
    }
}
