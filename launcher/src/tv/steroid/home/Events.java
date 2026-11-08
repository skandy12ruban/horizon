package tv.steroid.home;

import android.content.Context;
import android.text.format.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * What's special about today, in at most two short lines: festivals from config/festivals.json,
 * then the sky: full/new moon (with moonrise), supermoon, meteor-shower peaks, solstices and equinoxes.
 */
final class Events {
    /** Annual meteor-shower peaks {month (1-12), day, name}. Peaks shift by about a day year to year. */
    static final Object[][] SHOWERS = {
        {1, 3, "Quadrantids"}, {4, 22, "Lyrids"}, {5, 6, "Eta Aquariids"}, {7, 30, "Delta Aquariids"},
        {8, 12, "Perseids"}, {10, 8, "Draconids"}, {10, 21, "Orionids"}, {11, 17, "Leonids"}, {12, 14, "Geminids"},
    };

    static List<String> today(Context c, double lat, double lng) {
        List<String> out = new ArrayList<>();
        String f = festival(c);
        if (f != null) out.add(f);
        String s = sky(c, lat, lng);
        if (s != null) out.add(s);
        return out;
    }

    static String festival(Context c) {
        if (Config.festivals == null) return null;
        JSONArray a = Config.festivals.optJSONArray("festivals");
        if (a == null) return null;
        Calendar today = midnight(Calendar.getInstance());
        for (int days = 0; days <= 3; days++) {
            Calendar d = (Calendar) today.clone();
            d.add(Calendar.DAY_OF_YEAR, days);
            String key = String.format(Locale.ROOT, "%04d-%02d-%02d", d.get(Calendar.YEAR), d.get(Calendar.MONTH) + 1, d.get(Calendar.DAY_OF_MONTH));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || !key.equals(o.optString("date"))) continue;
                String name = o.optString("name"), when = days == 0 ? "today" : days == 1 ? "tomorrow"
                    : "on " + DateFormat.format("EEEE", d);
                return o.optBoolean("expected") ? name + " expected " + when : name + " " + when;
            }
        }
        return null;
    }

    static String sky(Context c, double lat, double lng) {
        Calendar now = Calendar.getInstance();
        Calendar noon = midnight(now);
        noon.set(Calendar.HOUR_OF_DAY, 12);
        long a = noon.getTimeInMillis(), b = a + 24 * 3600 * 1000L;

        // full / new moon between noon today and noon tomorrow ("tonight")
        double p0 = Astro.moonLight(a).phase, p1 = Astro.moonLight(b).phase;
        boolean full = p0 < 0.5 && p1 >= 0.5, fresh = p1 < p0; // phase wraps 1 → 0 at new moon
        if (full) {
            boolean superMoon = Astro.moon(a + 12 * 3600 * 1000L, lat, lng).distance < 361000;
            String rise = moonrise(c, a + 3 * 3600 * 1000L, lat, lng); // search from 15:00
            return (superMoon ? "Supermoon tonight" : "Full moon tonight") + (rise == null ? "" : ", rising " + rise);
        }

        // solstices and equinoxes: the sun's longitude crosses a quarter today
        Calendar mid = midnight(now);
        double l0 = Astro.sunLongitude(mid.getTimeInMillis()), l1 = Astro.sunLongitude(mid.getTimeInMillis() + 24 * 3600 * 1000L);
        for (int q = 0; q < 4; q++) {
            double t = q * Math.PI / 2;
            boolean crossed = q == 0 ? l1 < l0 : l0 < t && l1 >= t;
            if (!crossed) continue;
            boolean north = lat >= 0;
            if (q == 0 || q == 2) return (q == 0 ? "March" : "September") + " equinox: day and night are nearly equal";
            boolean longest = (q == 1) == north;
            return (q == 1 ? "June" : "December") + " solstice: the " + (longest ? "longest" : "shortest") + " day of the year";
        }

        int month = now.get(Calendar.MONTH) + 1, day = now.get(Calendar.DAY_OF_MONTH);
        for (Object[] s : SHOWERS) {
            if ((int) s[0] != month || (int) s[1] != day) continue;
            boolean bright = Astro.moonLight(a + 12 * 3600 * 1000L).fraction > 0.6;
            return s[2] + " meteor shower peaks tonight" + (bright ? ", though the moon is bright" : ", look up after midnight");
        }
        if (fresh) return "New moon tonight: the darkest skies for stars";
        return null;
    }

    /** First moonrise after `from`, formatted like the TV's clock, or null within 15 hours. */
    static String moonrise(Context c, long from, double lat, double lng) {
        double prev = Astro.moon(from, lat, lng).altitude;
        for (long t = from + 5 * 60000L; t < from + 15 * 3600 * 1000L; t += 5 * 60000L) {
            double alt = Astro.moon(t, lat, lng).altitude;
            if (prev < 0 && alt >= 0) return DateFormat.getTimeFormat(c).format(new java.util.Date(t));
            prev = alt;
        }
        return null;
    }

    static Calendar midnight(Calendar c) {
        Calendar m = (Calendar) c.clone();
        m.set(Calendar.HOUR_OF_DAY, 0); m.set(Calendar.MINUTE, 0); m.set(Calendar.SECOND, 0); m.set(Calendar.MILLISECOND, 0);
        return m;
    }
}
