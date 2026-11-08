package tv.steroid.home;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The World's Windows, from config/windows.json: long recorded views with natural sound and motion.
 * Each entry: YouTube video id, place, where, mood, time zone, source, honesty note, first-visit start.
 */
final class Places {
    static String[] MOODS = {"All", "Favourites"};
    static String[][] ALL = new String[0][];
    static int[] START = new int[0];

    static synchronized void load(Context c) {
        if (ALL.length > 0) return;
        Config.load(c);
        JSONArray m = Config.windows.optJSONArray("moods");
        String[] moods = new String[2 + (m == null ? 0 : m.length())];
        moods[0] = "All";
        moods[1] = "Favourites";
        for (int i = 0; m != null && i < m.length(); i++) moods[2 + i] = m.optString(i);
        JSONArray w = Config.windows.optJSONArray("windows");
        int n = w == null ? 0 : w.length();
        String[][] all = new String[n][];
        int[] start = new int[n];
        for (int i = 0; i < n; i++) {
            JSONObject o = w.optJSONObject(i);
            all[i] = new String[]{o.optString("youtube"), o.optString("name"), o.optString("where"), o.optString("mood"),
                o.optString("timezone", "UTC"), o.optString("source"), o.optString("note", "")};
            start[i] = o.optInt("start", 60);
        }
        MOODS = moods;
        ALL = all;
        START = start;
    }

    static String id(int i) { return ALL[i][0]; }
    static String name(int i) { return ALL[i][1]; }
    static String where(int i) { return ALL[i][2]; }
    static String mood(int i) { return ALL[i][3]; }
    static String tz(int i) { return ALL[i][4]; }
    static String source(int i) { return ALL[i][5]; }
    static String note(int i) { return ALL[i][6]; }
    static int count() { return ALL.length; }

    /** First-visit start point: past intros, title cards and taxiing. */
    static int firstStart(int i) { return START[i]; }

    static int indexOf(String id) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i][0].equals(id)) return i;
        return -1;
    }
}
