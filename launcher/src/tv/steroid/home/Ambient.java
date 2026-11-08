package tv.steroid.home;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.Random;

/** Slow drifting aurora with film grain. ~12fps, paused when hidden. Purely decorative. */
class Ambient extends View {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint shade = new Paint();
    final Paint grain = new Paint();
    int tint = 0xFFE8915A, shown = tint, second = 0xFF5B3CC4;
    float t;
    boolean running;

    Ambient(Context c) {
        super(c);
        Bitmap noise = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
        Random r = new Random(7);
        for (int y = 0; y < 160; y++) for (int x = 0; x < 160; x++) {
            int v = r.nextInt(256);
            noise.setPixel(x, y, Color.argb(14, v, v, v));
        }
        grain.setShader(new BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    void tint(int c) { tint = c; }
    void run(boolean r) { running = r; if (r) invalidate(); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        shade.setShader(new LinearGradient(0, 0, 0, h,
            new int[]{0x22000000, 0x00000000, 0x99000000, 0xF2050508},
            new float[]{0f, 0.3f, 0.62f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawColor(0xFF08080C);
        shown = blend(shown, tint, 0.07f);
        blob(c, w * (0.30f + 0.07f * (float) Math.sin(t * 0.7)), h * (0.30f + 0.08f * (float) Math.cos(t * 0.5)), w * 0.62f, shown, 0x78);
        blob(c, w * (0.86f + 0.05f * (float) Math.cos(t * 0.4)), h * (0.22f + 0.07f * (float) Math.sin(t * 0.6)), w * 0.5f, second, 0x52);
        blob(c, w * (0.60f + 0.10f * (float) Math.sin(t * 0.3)), h * 0.95f, w * 0.55f, blend(shown, 0xFF000000, 0.4f), 0x40);
        c.drawRect(0, 0, w, h, shade);
        c.drawRect(0, 0, w, h, grain);
        t += 0.014f;
        if (running) postInvalidateDelayed(83);
    }

    void blob(Canvas c, float x, float y, float r, int color, int alpha) {
        p.setShader(new RadialGradient(x, y, r, Ui.withAlpha(color, alpha), color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
    }

    static int blend(int a, int b, float f) {
        return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * f),
            (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * f),
            (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }
}
