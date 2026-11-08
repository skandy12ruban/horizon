package tv.steroid.home;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

/** Shared type, sizing and small view helpers. */
final class Ui {
    static Typeface serif, serifItalic, sans, sansMedium, mono;
    static float dp;

    static void init(Context c) {
        dp = c.getResources().getDisplayMetrics().density;
        Config.load(c);
        Stations.load(c);
        Places.load(c);
        if (serif != null) return;
        AssetManager a = c.getAssets();
        serif = Typeface.createFromAsset(a, "fonts/serif.ttf");
        serifItalic = Typeface.createFromAsset(a, "fonts/serif-italic.ttf");
        sans = new Typeface.Builder(a, "fonts/sans.ttf").setFontVariationSettings("'wght' 400").build();
        sansMedium = new Typeface.Builder(a, "fonts/sans.ttf").setFontVariationSettings("'wght' 600").build();
        mono = Typeface.createFromAsset(a, "fonts/mono.ttf");
    }

    static final java.util.HashMap<String, Typeface> faces = new java.util.HashMap<>();

    /** Horizon's one family, Bricolage Grotesque, at a given weight (200–800) and width (75–100). */
    static Typeface face(Context c, int wght, int wdth) {
        String k = wght + "/" + wdth;
        Typeface t = faces.get(k);
        if (t == null) {
            t = new Typeface.Builder(c.getAssets(), "fonts/display.ttf")
                .setFontVariationSettings("'wght' " + wght + ", 'wdth' " + wdth).build();
            faces.put(k, t);
        }
        return t;
    }

    static int px(float v) { return Math.round(v * dp); }

    static TextView text(Context c, Typeface tf, float sp, int color) {
        TextView t = new TextView(c);
        t.setTypeface(tf);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView label(Context c, String s, int color) {
        TextView t = text(c, mono, 10.5f, color);
        t.setLetterSpacing(0.22f);
        t.setText(s.toUpperCase());
        return t;
    }

    static GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(radiusDp));
        return g;
    }

    static void lift(View v, boolean f, float scale) {
        v.animate().scaleX(f ? scale : 1f).scaleY(f ? scale : 1f)
            .translationZ(f ? px(14) : 0).setDuration(200).start();
    }

    static int withAlpha(int color, int alpha) { return (color & 0x00FFFFFF) | (alpha << 24); }
}
