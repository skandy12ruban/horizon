package tv.steroid.home;

import android.content.Context;
import java.io.InputStream;
import org.json.JSONObject;

/** Reads the build's config files (copied from /config into assets/config by build.sh). */
final class Config {
    static JSONObject apps, stations, windows, features, sky, festivals;

    static synchronized void load(Context c) {
        if (apps != null) return;
        apps = read(c, "apps.json");
        stations = read(c, "stations.json");
        windows = read(c, "windows.json");
        features = read(c, "features.json");
        sky = read(c, "sky.json");
        festivals = read(c, "festivals.json");
    }

    static boolean feature(String name) { return features.optBoolean(name, !name.startsWith("debug")); }

    static JSONObject read(Context c, String name) {
        try (InputStream in = c.getAssets().open("config/" + name)) {
            byte[] b = new byte[in.available()];
            int n = in.read(b);
            return new JSONObject(new String(b, 0, n, "UTF-8"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
