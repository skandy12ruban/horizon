package tv.steroid.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;

/** Every app on the TV, A to Z, over the same sky as Home. Back returns home. */
public class AllApps extends Activity {
    static List<Home.Entry> list(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, Home.Entry> out = new TreeMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(c.getPackageName())) continue;
                Home.Entry e = new Home.Entry();
                e.pkg = pkg;
                e.label = String.valueOf(r.loadLabel(pm));
                e.launch = Home.launchIntent(pm, pkg);
                if (e.launch == null) continue;
                String key = e.label.toLowerCase(Locale.ROOT) + "\u0000" + pkg;
                boolean dup = false;
                for (Home.Entry x : out.values()) if (x.pkg.equals(pkg)) { dup = true; break; }
                if (!dup) out.put(key, e);
            }
        }
        return new ArrayList<>(out.values());
    }

    Sky sky;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        sky = new Sky(this);
        root.addView(sky, new FrameLayout.LayoutParams(-1, -1));
        View veil = new View(this);
        veil.setBackgroundColor(0x99030510);
        root.addView(veil, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.px(64), Ui.px(40), Ui.px(64), Ui.px(40));
        col.setClipToPadding(false);
        col.setClipChildren(false);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, Ui.face(this, 300, 75), 56, 0xFFFFFFFF);
        title.setText("All apps");
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(originalHomeButton());
        col.addView(head);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(0, Ui.px(20), 0, Ui.px(20));
        PackageManager pm = getPackageManager();
        View first = null;
        for (Home.Entry e : list(this)) {
            View t = tile(pm, e);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = Ui.px(150);
            lp.height = Ui.px(118);
            lp.setMargins(0, 0, Ui.px(16), Ui.px(16));
            grid.addView(t, lp);
            if (first == null) first = t;
        }
        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.setClipChildren(false);
        sc.addView(grid);
        col.addView(sc, new LinearLayout.LayoutParams(-1, -1));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        if (first != null) first.requestFocus();
    }

    View tile(PackageManager pm, Home.Entry e) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setFocusable(true);
        t.setClipChildren(false);
        FrameLayout art = new FrameLayout(this);
        art.setClipToOutline(true);
        GradientDrawable bg = Ui.round(0x26FFFFFF, 10);
        art.setBackground(bg);
        Drawable banner = null;
        try { banner = pm.getApplicationBanner(e.pkg); } catch (Exception ignored) {}
        ImageView iv = new ImageView(this);
        if (banner != null) {
            iv.setImageDrawable(banner);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            art.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            try { iv.setImageDrawable(pm.getApplicationIcon(e.pkg)); } catch (Exception ignored) {}
            art.addView(iv, new FrameLayout.LayoutParams(Ui.px(44), Ui.px(44), Gravity.CENTER));
        }
        t.addView(art, new LinearLayout.LayoutParams(-1, Ui.px(84)));
        TextView name = Ui.text(this, Ui.face(this, 500, 90), 13, 0xB3FFFFFF);
        name.setText(e.label);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(Ui.px(2), Ui.px(8), 0, 0);
        t.addView(name);
        t.setOnFocusChangeListener((v, f) -> {
            Ui.lift(art, f, 1.07f);
            bg.setStroke(f ? Ui.px(3) : 0, 0xFFFFFFFF);
            name.setTextColor(f ? 0xFFFFFFFF : 0xB3FFFFFF);
        });
        t.setOnClickListener(v -> {
            try { startActivity(e.launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception ex) { Toast.makeText(this, "This app can't be opened right now", Toast.LENGTH_SHORT).show(); }
        });
        return t;
    }

    /**
     * Way back to the TV's own home screen without a computer: opens the system's Home app
     * picker where the TV has one, otherwise Horizon's app page (Uninstall there restores the
     * stock launcher at once, since Horizon never disables it).
     */
    View originalHomeButton() {
        TextView t = Ui.text(this, Ui.face(this, 500, 95), 15, 0xD9FFFFFF);
        t.setText("Use the original home screen");
        t.setFocusable(true);
        t.setPadding(Ui.px(14), Ui.px(8), Ui.px(14), Ui.px(9));
        GradientDrawable bg = Ui.round(0x00FFFFFF, 18);
        bg.setStroke(Ui.px(1), 0x33FFFFFF);
        t.setBackground(bg);
        t.setOnFocusChangeListener((v, f) -> {
            bg.setColor(f ? 0xFFFFFFFF : 0x00FFFFFF);
            t.setTextColor(f ? 0xFF0C1020 : 0xD9FFFFFF);
        });
        t.setOnClickListener(v -> {
            // Normal case: Horizon catches the Home button; switching that off restores Google TV.
            if (HomeKey.enabled(this)) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    Toast.makeText(this, "Turn off \u201cHorizon Home button\u201d, then press Home", Toast.LENGTH_LONG).show();
                    return;
                } catch (Exception ignored) {}
            }
            // If a stock launcher was switched off for Horizon, its app page has the Enable button.
            String stock = null;
            for (String p : new String[]{"com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher",
                    "com.google.android.leanbacklauncher"}) {
                try {
                    int st = getPackageManager().getApplicationEnabledSetting(p);
                    if (st == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                        || st == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) { stock = p; break; }
                } catch (Exception ignored) {}
            }
            if (stock != null) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", stock, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    Toast.makeText(this, "Choose Enable, then press Home", Toast.LENGTH_LONG).show();
                    return;
                } catch (Exception ignored) {}
            }
            Intent[] tries = {
                new Intent(android.provider.Settings.ACTION_HOME_SETTINGS),
                new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", getPackageName(), null)),
            };
            for (Intent i : tries) {
                try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; } catch (Exception ignored) {}
            }
            Toast.makeText(this, "Open Settings › Apps › Horizon and choose Uninstall", Toast.LENGTH_LONG).show();
        });
        return t;
    }

    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); sky.run(f); }
}
