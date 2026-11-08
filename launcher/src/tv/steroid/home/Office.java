package tv.steroid.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Little Office client: finds the Mac's status service (mDNS "_littleoffice._tcp") and reads a
 * glance of Claude Code / Codex activity. Shared by the home card, the glance panel and the
 * notification service.
 */
final class Office {
    static final long STALE_MS = 75_000; // no answer for this long: show "last seen", never "working"

    static class Session {
        String agent, project, state, text;
        long at; // seconds since epoch, Mac clock
    }

    static class Event {
        long id;
        String agent, project, kind, text;
    }

    static List<Session> sessions = new ArrayList<>();
    static List<Event> events = new ArrayList<>();
    static List<String> pinned = new ArrayList<>();
    static long lastOk;      // phone clock, ms
    static double macNow;    // Mac clock at last snapshot, seconds
    static boolean everConnected;
    static String host;
    static int port;

    // ---------- Discovery ----------

    static NsdManager.DiscoveryListener discovery;

    static void discover(Context c) {
        SharedPreferences p = c.getSharedPreferences("office", Context.MODE_PRIVATE);
        if (host == null) { host = p.getString("host", null); port = p.getInt("port", 8765); }
        if (discovery != null) return;
        NsdManager nsd = (NsdManager) c.getSystemService(Context.NSD_SERVICE);
        discovery = new NsdManager.DiscoveryListener() {
            public void onStartDiscoveryFailed(String t, int e) { discovery = null; }
            public void onStopDiscoveryFailed(String t, int e) {}
            public void onDiscoveryStarted(String t) {}
            public void onDiscoveryStopped(String t) {}
            public void onServiceLost(NsdServiceInfo s) {}
            public void onServiceFound(NsdServiceInfo s) {
                nsd.resolveService(s, new NsdManager.ResolveListener() {
                    public void onResolveFailed(NsdServiceInfo s, int e) {}
                    public void onServiceResolved(NsdServiceInfo s) {
                        host = s.getHost().getHostAddress();
                        port = s.getPort();
                        p.edit().putString("host", host).putInt("port", port).apply();
                    }
                });
            }
        };
        try { nsd.discoverServices("_littleoffice._tcp", NsdManager.PROTOCOL_DNS_SD, discovery); }
        catch (Exception e) { discovery = null; }
    }

    // ---------- Polling (call off the main thread) ----------

    static synchronized boolean poll(Context c) {
        discover(c);
        if (host == null) return false;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("http://" + host + ":" + port + "/status").openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(6000);
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
            JSONObject o = new JSONObject(buf.toString("UTF-8"));
            macNow = o.optDouble("generated_at");
            List<Session> ss = new ArrayList<>();
            JSONArray a = o.optJSONArray("sessions");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject s = a.getJSONObject(i);
                Session x = new Session();
                x.agent = s.optString("agent"); x.project = s.optString("project");
                x.state = s.optString("state"); x.text = s.optString("text"); x.at = (long) s.optDouble("at");
                ss.add(x);
            }
            List<Event> es = new ArrayList<>();
            JSONArray e = o.optJSONArray("events");
            for (int i = 0; e != null && i < e.length(); i++) {
                JSONObject s = e.getJSONObject(i);
                Event x = new Event();
                x.id = s.optLong("id"); x.agent = s.optString("agent"); x.project = s.optString("project");
                x.kind = s.optString("kind"); x.text = s.optString("text");
                es.add(x);
            }
            List<String> pins = new ArrayList<>();
            JSONArray pa = o.optJSONArray("pinned");
            for (int i = 0; pa != null && i < pa.length(); i++) pins.add(pa.getString(i));
            sessions = ss; events = es; pinned = pins;
            lastOk = System.currentTimeMillis();
            everConnected = true;
            c.getSharedPreferences("office", Context.MODE_PRIVATE).edit().putLong("lastOk", lastOk).apply();
            return true;
        } catch (Exception ex) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ---------- Wording ----------

    static boolean connected() { return System.currentTimeMillis() - lastOk < STALE_MS; }

    static String lastSeen(Context c) {
        long t = lastOk > 0 ? lastOk : c.getSharedPreferences("office", Context.MODE_PRIVATE).getLong("lastOk", 0);
        if (t == 0) return "Waiting for the computer";
        long min = (System.currentTimeMillis() - t) / 60000;
        return min < 1 ? "Last seen just now" : min < 60 ? "Last seen " + min + (min == 1 ? " minute ago" : " minutes ago")
            : "Last seen " + (min / 60) + (min / 60 == 1 ? " hour ago" : " hours ago");
    }

    /** "Codex · 2 working · 1 needs you" or "Claude · All quiet". */
    static String line(String agent) {
        int working = 0, needs = 0;
        for (Session s : sessions) if (s.agent.equals(agent)) {
            if (s.state.equals("working")) working++;
            if (s.state.equals("needs_you")) needs++;
        }
        StringBuilder b = new StringBuilder(agent);
        if (working == 0 && needs == 0) return b.append(" · All quiet").toString();
        if (working > 0) b.append(" · ").append(working).append(" working");
        if (needs > 0) b.append(" · ").append(needs).append(" needs you");
        return b.toString();
    }

    /** The most interesting thing happening, e.g. "Forma — rendering video." */
    static String highlight() {
        Session best = null;
        for (Session s : sessions) if (rank(s) > rank(best)) best = s;
        if (best == null || rank(best) < 2) return null;
        return best.project + " — " + best.text;
    }

    static int rank(Session s) {
        if (s == null) return -1;
        switch (s.state) { case "needs_you": return 3; case "working": return 2; case "finished": return 1; default: return 0; }
    }

    static String lower(String t) {
        if (t == null || t.isEmpty()) return "";
        return t.length() > 1 && Character.isUpperCase(t.charAt(0)) && !Character.isUpperCase(t.charAt(1))
            ? Character.toLowerCase(t.charAt(0)) + t.substring(1) : t;
    }

    static String ago(Session s) {
        long min = (long) Math.max(0, (macNow - s.at) / 60);
        return min < 1 ? "just now" : min < 60 ? min + " min ago" : (min / 60) + " h ago";
    }

    static String stateLabel(Session s) {
        switch (s.state) {
            case "working": return "Working";
            case "needs_you": return "Needs you";
            case "finished": return "Finished · " + ago(s);
            default: return "Quiet · " + ago(s);
        }
    }

    static int stateColor(String state) {
        switch (state) {
            case "working": return 0xFF6FD49B;
            case "needs_you": return 0xFFFFB454;
            case "finished": return 0xFF8FB8FF;
            default: return 0x80FFFFFF;
        }
    }
}
