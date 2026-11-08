package tv.steroid.home;

import android.content.Context;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;

/** Radio stations from config/stations.json (imported from CLI AMP, or the default list). */
final class Stations {
    static String[][] ALL = new String[0][];
    static int[] HUE = new int[0];
    static final int[] PALETTE = {0xFFE8915A, 0xFF8FB8A8, 0xFF5F7FB8, 0xFF8A6CE0, 0xFFFF4FA3, 0xFF39D0FF, 0xFF9BE15D, 0xFFFFB347};

    static synchronized void load(Context c) {
        if (ALL.length > 0) return;
        Config.load(c);
        JSONArray a = Config.stations.optJSONArray("stations");
        int n = a == null ? 0 : a.length();
        String[][] all = new String[n][];
        int[] hue = new int[n];
        for (int i = 0; i < n; i++) {
            JSONObject s = a.optJSONObject(i);
            all[i] = new String[]{s.optString("title"), s.optString("url"), s.optString("genre", "")};
            try { hue[i] = Color.parseColor(s.optString("color")); }
            catch (Exception e) { hue[i] = PALETTE[i % PALETTE.length]; }
        }
        ALL = all;
        HUE = hue;
    }

    static String name(int i) { return ALL[i][0]; }
    static String url(int i) { return ALL[i][1]; }
    static String genre(int i) { return ALL[i][2]; }
    static int count() { return ALL.length; }
}
