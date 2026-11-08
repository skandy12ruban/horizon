package tv.steroid.home;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

/** Little Office glance: pinned projects, other activity, and anything that needs you. Read-only. */
public class OfficePanel extends Activity {
    final Handler h = new Handler();
    Ambient ambient;
    LinearLayout list;
    TextView status;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        ambient = new Ambient(this);
        ambient.tint(0xFF6FD49B);
        root.addView(ambient);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.px(64), Ui.px(40), Ui.px(64), Ui.px(60));
        col.addView(Ui.label(this, "Little Office", 0x99FFFFFF));
        TextView t = Ui.text(this, Ui.serif, 44, 0xFFFFFFFF);
        t.setText("At the desk");
        t.setPadding(0, Ui.px(8), 0, Ui.px(6));
        col.addView(t);
        status = Ui.text(this, Ui.sans, 13, 0xB3FFFFFF);
        col.addView(status);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);
        scroll.addView(col);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        render();
    }

    void render() {
        list.removeAllViews();
        boolean live = Office.connected();
        status.setText(live ? Office.line("Codex") + "     " + Office.line("Claude")
            : Office.lastSeen(this) + " — the computer isn't answering, so nothing here is live.");

        // Needs you: the recent requests, newest first
        List<Office.Session> needs = new ArrayList<>();
        for (Office.Session s : Office.sessions) if (s.state.equals("needs_you")) needs.add(s);
        if (live && !needs.isEmpty()) {
            section("Needs you");
            for (Office.Session s : needs) list.addView(row(s, true));
        }

        section("Pinned");
        for (String p : Office.pinned) {
            Office.Session best = null;
            for (Office.Session s : Office.sessions) if (s.project.equalsIgnoreCase(p) && Office.rank(s) > Office.rank(best)) best = s;
            if (best == null) {
                Office.Session quiet = new Office.Session();
                quiet.project = p; quiet.agent = ""; quiet.state = "idle"; quiet.text = "Nothing recent."; quiet.at = (long) Office.macNow;
                list.addView(row(quiet, false));
            } else list.addView(row(best, false));
        }

        List<Office.Session> other = new ArrayList<>();
        for (Office.Session s : Office.sessions) {
            boolean pin = false;
            for (String p : Office.pinned) if (s.project.equalsIgnoreCase(p)) pin = true;
            if (!pin && !(live && s.state.equals("needs_you"))) other.add(s);
        }
        if (!other.isEmpty()) {
            section("Other activity");
            for (int i = 0; i < Math.min(8, other.size()); i++) list.addView(row(other.get(i), false));
        }
        for (int i = 0; i < list.getChildCount(); i++)
            if (list.getChildAt(i).isFocusable()) { list.getChildAt(i).requestFocus(); break; }
        if (Office.pinned.isEmpty() && !live) {
            TextView none = Ui.text(this, Ui.serifItalic, 18, 0x99FFFFFF);
            none.setText("Nothing to show until the computer answers.");
            list.addView(none);
        }
    }

    void section(String s) {
        TextView t = Ui.label(this, s, 0x80FFFFFF);
        t.setPadding(0, Ui.px(26), 0, Ui.px(10));
        list.addView(t);
    }

    /** One project: name, agent, state chip and one sentence. Focusable only so the D-pad can scroll. */
    View row(Office.Session s, boolean urgent) {
        boolean live = Office.connected();
        String state = live ? s.state : "idle";
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setPadding(Ui.px(20), Ui.px(14), Ui.px(20), Ui.px(14));
        GradientDrawable bg = Ui.round(urgent ? 0x26FFB454 : 0x14FFFFFF, 14);
        r.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.px(8);
        r.setLayoutParams(lp);

        View dot = new View(this);
        dot.setBackground(Ui.round(Office.stateColor(state), 5));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.px(10), Ui.px(10));
        dl.rightMargin = Ui.px(16);
        r.addView(dot, dl);

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.BOTTOM);
        TextView name = Ui.text(this, Ui.serif, 21, 0xFFFFFFFF);
        name.setText(s.project);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView who = Ui.label(this, (s.agent.isEmpty() ? "" : s.agent + "  ·  ")
            + (live ? Office.stateLabel(s) : "Last known: " + Office.stateLabel(s)), Office.stateColor(state));
        who.setPadding(Ui.px(16), 0, 0, Ui.px(3));
        top.addView(who);
        txt.addView(top);
        TextView line = Ui.text(this, Ui.sans, 13, 0xB3FFFFFF);
        line.setText(s.text.isEmpty() ? " " : s.text);
        line.setSingleLine(true);
        line.setEllipsize(android.text.TextUtils.TruncateAt.END);
        line.setPadding(0, Ui.px(4), 0, 0);
        txt.addView(line);
        r.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));
        r.setOnFocusChangeListener((v, f) -> bg.setColor(f ? 0x33FFFFFF : urgent ? 0x26FFB454 : 0x14FFFFFF));
        return r;
    }

    final Runnable refresh = new Runnable() { public void run() {
        new Thread(() -> { Office.poll(OfficePanel.this); h.post(() -> { if (!isFinishing()) render(); }); }).start();
        h.postDelayed(this, 15000);
    }};

    @Override protected void onResume() { super.onResume(); h.post(refresh); }
    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); ambient.run(f); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(refresh); ambient.run(false); }
}
