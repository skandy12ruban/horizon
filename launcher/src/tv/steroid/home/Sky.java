package tv.steroid.home;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.Random;

/**
 * Horizon's background: the real sky over the configured place (config/sky.json).
 * Sky colour follows the sun's actual altitude; the sun and moon sit where they really are
 * (looking towards the equator, east on the left for the northern hemisphere), and the moon
 * shows its real phase and tilt with its familiar dark seas. Three layers of hills are tinted
 * from the sky, and the focused app's colour lights the rim of the nearest hill.
 */
class Sky extends View {
    /** Fraction of the height where the horizon sits; the near hills start a little below it. */
    static final float GROUND = 0.50f;
    /** Near-hill edge as a fraction of height; Home puts the app row just under it. */
    static final float NEAR = GROUND + 0.04f;

    // Sun altitude (degrees) → {top, middle, horizon}, separately for morning and evening light.
    static final float[] KEY_ALT = {-18, -12, -4, 4, 15, 40};
    static final int[][] MORNING = {
        {0xFF060A18, 0xFF0C1430, 0xFF14234A}, // night
        {0xFF121842, 0xFF262C62, 0xFF3C4A7E}, // blue hour
        {0xFF2E3266, 0xFF8E6488, 0xFFF0B08A}, // first light
        {0xFF4A6FB0, 0xFFA9B4CC, 0xFFF7D9AE}, // golden hour
        {0xFF3A78C6, 0xFF6FA6DD, 0xFFA8D2F0}, // day
        {0xFF2F6CC0, 0xFF5E9CDB, 0xFFA2CDF0}, // high sun
    };
    static final int[][] EVENING = {
        {0xFF060A18, 0xFF0C1430, 0xFF14234A},
        {0xFF0E1232, 0xFF2A2558, 0xFF4A3A6A},
        {0xFF221C44, 0xFF8A4470, 0xFFF2905E},
        {0xFF3A4F8E, 0xFFB0708A, 0xFFF2A070},
        {0xFF3A78C6, 0xFF6FA6DD, 0xFFA8D2F0},
        {0xFF2F6CC0, 0xFF5E9CDB, 0xFFA2CDF0},
    };
    /** Lunar seas on the near side, as seen from the north with north up: {x, y, radius} of the disc. */
    static final float[][] MARIA = {
        {-0.30f, -0.36f, 0.27f}, {0.12f, -0.30f, 0.15f}, {0.27f, -0.05f, 0.18f}, {0.62f, -0.22f, 0.10f},
        {-0.55f, 0.02f, 0.33f}, {-0.16f, 0.40f, 0.15f}, {0.50f, 0.17f, 0.12f}, {-0.02f, 0.10f, 0.10f},
    };

    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), star = new Paint(Paint.ANTI_ALIAS_FLAG),
        rim = new Paint(Paint.ANTI_ALIAS_FLAG), orb = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path lit = new Path();
    Path far, mid, near, nearEdge;
    float[] stars = new float[0]; // x, y, radius, phase
    int top, middle, horizon;
    double lat = 13.08, lng = 80.27;
    float glowX = -1, glowTarget = -1;
    int glowColor = 0xFFFFFFFF, glowShown = 0x00FFFFFF;
    boolean running;
    /** Sun and moon areas on screen (empty when down); read by Home to keep panels clear. */
    final RectF sunBody = new RectF(), moonBody = new RectF();

    // ---- weather in the sky (from Weather's WMO code) ----
    int wcode = -1;
    float cloud, rain, greyness; boolean fog, storm, snow;
    Bitmap[] puffs = new Bitmap[0];
    float[] puffX = new float[0], puffY = new float[0], puffS = new float[0];
    float[] drops = new float[0]; // x, y, speed, length
    final Paint puffPaint = new Paint(Paint.FILTER_BITMAP_FLAG), dropPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    long lastFrame, flashAt, nextFlash;
    final Random rnd = new Random();

    /** Shows the current weather in the sky: clouds, grey overcast, rain, snow, fog, lightning. */
    void weather(int code) {
        if (code == wcode) return;
        wcode = code;
        cloud = code <= 0 ? 0 : code == 1 ? 0.15f : code == 2 ? 0.45f : code == 3 ? 0.85f
            : (code == 45 || code == 48) ? 0.5f : code >= 95 ? 0.95f : 0.8f;
        rain = (code >= 51 && code <= 57) ? 0.3f : (code == 61 || code == 80 || code == 66) ? 0.5f
            : (code == 63 || code == 81) ? 0.75f : (code == 65 || code == 82 || code == 67 || code >= 95) ? 1f : 0f;
        snow = code >= 71 && code <= 77 || code == 85 || code == 86;
        fog = code == 45 || code == 48;
        storm = code >= 95;
        greyness = Math.max(0, cloud - 0.4f) / 0.6f;
        layoutWeather();
        invalidate();
    }

    void layoutWeather() {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        float g = h * GROUND;
        int n = cloud <= 0 ? 0 : Math.round(2 + cloud * 10);
        Random r = new Random(5);
        if (puffs.length == 0) { // three soft cloud shapes, white with alpha, tinted when drawn
            puffs = new Bitmap[3];
            for (int k = 0; k < 3; k++) puffs[k] = puff(Math.round(w * 0.26f), Math.round(h * 0.11f), new Random(20 + k));
        }
        puffX = new float[n]; puffY = new float[n]; puffS = new float[n];
        for (int i = 0; i < n; i++) {
            puffX[i] = r.nextFloat() * (w + w * 0.3f) - w * 0.3f;
            puffY[i] = g * (0.04f + r.nextFloat() * 0.5f);
            puffS[i] = 0.6f + r.nextFloat() * 0.8f;
        }
        int d = snow ? 90 : 0; // rainy days get the grey sky and clouds, without falling streaks
        drops = new float[d * 4];
        for (int i = 0; i < d; i++) {
            drops[i * 4] = r.nextFloat() * w;
            drops[i * 4 + 1] = r.nextFloat() * h;
            drops[i * 4 + 2] = snow ? Ui.px(25 + r.nextFloat() * 25) : Ui.px(500 + r.nextFloat() * 300); // px per second
            drops[i * 4 + 3] = snow ? Ui.px(1.2f + r.nextFloat()) : Ui.px(10 + r.nextFloat() * 10);
        }
    }

    static Bitmap puff(int w, int h, Random r) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // a soft flat base, then heavily overlapping billows that are tallest in the middle
        p.setShader(new RadialGradient(w / 2f, h * 0.7f, w * 0.42f, 0x70FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.save(); c.scale(1f, 0.35f, w / 2f, h * 0.7f); c.drawCircle(w / 2f, h * 0.7f, w * 0.42f, p); c.restore();
        int n = 9;
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            float bulge = (float) Math.sin(Math.PI * (0.15 + 0.7 * t));
            float rad = h * (0.30f + 0.24f * bulge) * (0.9f + r.nextFloat() * 0.2f);
            float cx = w * (0.22f + 0.56f * t) + (r.nextFloat() - 0.5f) * w * 0.04f;
            float cy = h * 0.66f - rad * 0.45f * bulge;
            p.setShader(new RadialGradient(cx, cy, rad, 0x9CFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, rad, p);
        }
        return b;
    }

    Sky(Context c) {
        super(c);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeCap(Paint.Cap.ROUND);
        relocate();
        Locate.refresh(c, () -> post(() -> { relocate(); invalidate(); }));
    }

    /** Picks up the TV's latest known location (see Locate). */
    void relocate() {
        double[] l = Locate.current(getContext());
        lat = l[0];
        lng = l[1];
    }

    void run(boolean r) { running = r; if (r) invalidate(); }

    /** Light the horizon above a focused card: x in this view's coordinates. */
    void glow(int color, float x) {
        glowColor = color;
        glowTarget = x;
        if (glowX < 0) glowX = x;
        invalidate();
    }

    /** 1 when the sun is well below the horizon, 0 in daylight. */
    static float nightness(double altDeg) {
        return (float) Math.max(0, Math.min(1, (-2 - altDeg) / 10));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float g = h * GROUND;
        far = hills(w, h, g - h * 0.06f, h * 0.035f, 2.1f, 0.7f);
        mid = hills(w, h, g - h * 0.015f, h * 0.03f, 3.3f, 2.4f);
        near = hills(w, h, h * NEAR, h * 0.022f, 1.6f, 4.1f);
        nearEdge = edge(w, h * NEAR, h * 0.022f, 1.6f, 4.1f);
        Random r = new Random(11);
        stars = new float[140 * 4];
        for (int i = 0; i < 140; i++) {
            stars[i * 4] = r.nextFloat() * w;
            stars[i * 4 + 1] = (float) Math.pow(r.nextFloat(), 1.4) * g * 0.92f;
            stars[i * 4 + 2] = Ui.px(r.nextFloat() < 0.12f ? 1.6f : 0.9f);
            stars[i * 4 + 3] = r.nextFloat() * 6.28f;
        }
        layoutWeather();
    }

    float ridge(float x, float w, float amp, float f, float phase) {
        double u = x / w * Math.PI * 2;
        return (float) (amp * (0.6 * Math.sin(u * f + phase) + 0.3 * Math.sin(u * f * 2.3 + phase * 1.7)
            + 0.1 * Math.sin(u * f * 5.1 + phase * 0.3)));
    }

    Path hills(int w, int h, float base, float amp, float f, float phase) {
        Path p = edge(w, base, amp, f, phase);
        p.lineTo(w, h);
        p.lineTo(0, h);
        p.close();
        return p;
    }

    Path edge(int w, float base, float amp, float f, float phase) {
        Path p = new Path();
        int step = Math.max(4, w / 160);
        for (int x = 0; x <= w + step; x += step) {
            float y = base - ridge(x, w, amp, f, phase);
            if (x == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        return p;
    }

    /** Screen position for a body: x from azimuth (east left when looking south), y from altitude. */
    float skyX(double az, int w) { return w * (0.5f + 0.44f * (float) Math.sin(az) * (lat >= 0 ? 1 : -1)); }
    float skyY(double alt, float g) { return g - (float) Math.sin(Math.max(alt, -0.2)) * g * 0.86f; }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (far == null) return;
        long now = System.currentTimeMillis();
        Astro.Pos sun = Astro.sun(now, lat, lng);
        double sunAlt = Math.toDegrees(sun.altitude);
        sample(sunAlt, sun.azimuth < 0);
        float night = nightness(sunAlt), g = h * GROUND;
        if (greyness > 0) { // overcast washes the colour out of the sky
            int grey = Ambient.blend(0xFF8A94A3, 0xFF161A22, night);
            float k = greyness * 0.6f;
            top = Ambient.blend(top, grey, k); middle = Ambient.blend(middle, grey, k); horizon = Ambient.blend(horizon, grey, k * 0.8f);
        }
        long frame = now - lastFrame; lastFrame = now;
        float dt = Math.min(0.25f, Math.max(0, frame / 1000f));

        fill.setShader(new LinearGradient(0, 0, 0, g + h * 0.05f, new int[]{top, middle, horizon},
            new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, fill);
        fill.setShader(null);

        if (night > 0.02f) { // stars, gently twinkling
            double t = now / 1000.0;
            for (int i = 0; i < stars.length; i += 4) {
                float tw = 0.65f + 0.35f * (float) Math.sin(t * 0.5 + stars[i + 3]);
                star.setColor(Color.argb((int) (230 * night * (1f - cloud) * tw * (1f - stars[i + 1] / g * 0.6f)), 255, 250, 240));
                c.drawCircle(stars[i], stars[i + 1], stars[i + 2], star);
            }
        }

        Astro.Pos moon = Astro.moon(now, lat, lng);
        Astro.Light ml = Astro.moonLight(now);
        float sx = skyX(sun.azimuth, w), sy = skyY(sun.altitude, g), mx = skyX(moon.azimuth, w), my = skyY(moon.altitude, g);
        boolean sunUp = sunAlt > -1 && cloud < 0.98f;
        // a sliver under ~4% lit sits beside the sun and can't be seen in a real sky either
        boolean moonUp = moon.altitude > -0.01 && ml.fraction > 0.04;
        if (sunUp) drawSun(c, sx, sy, sunAlt);
        if (moonUp) drawMoon(c, mx, my, moon, ml, night, g);
        // where the sun and moon are (with their glow), so Home can keep panels clear of them
        sunBody.set(sunUp ? sx - Ui.px(70) : 0, sunUp ? sy - Ui.px(70) : 0, sunUp ? sx + Ui.px(70) : 0, sunUp ? sy + Ui.px(70) : 0);
        moonBody.set(moonUp ? mx - Ui.px(40) : 0, moonUp ? my - Ui.px(40) : 0, moonUp ? mx + Ui.px(40) : 0, moonUp ? my + Ui.px(40) : 0);

        // clouds drift slowly east to west across the sky, tinted by the light of the hour
        if (puffX.length > 0) {
            int tint = Ambient.blend(Ambient.blend(0xFFFFFFFF, horizon, 0.25f), Ambient.blend(top, 0xFF8C96AA, 0.35f), night);
            if (greyness > 0) tint = Ambient.blend(tint, Ambient.blend(0xFFB4BCC8, 0xFF2A303C, night), greyness * 0.6f);
            puffPaint.setColorFilter(new PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN));
            puffPaint.setAlpha((int) (255 * (0.55f + 0.4f * cloud) * (1f - 0.35f * night)));
            for (int i = 0; i < puffX.length; i++) {
                Bitmap b = puffs[i % puffs.length];
                float sw = b.getWidth() * puffS[i];
                puffX[i] += dt * Ui.px(6) * (0.6f + puffS[i] * 0.5f);
                if (puffX[i] > w) puffX[i] = -sw;
                c.save();
                c.translate(puffX[i], puffY[i]);
                c.scale(puffS[i], puffS[i] * (0.8f + 0.2f * cloud));
                c.drawBitmap(b, 0, 0, puffPaint);
                c.restore();
            }
        }
        if (storm) { // now and then the sky flashes
            if (now > nextFlash) { flashAt = now; nextFlash = now + 7000 + rnd.nextInt(14000); }
            long since = now - flashAt;
            if (since < 450) {
                fill.setColor(Color.argb((int) (90 * (1 - since / 450f) * (since < 80 || since > 160 ? 1 : 0.3f)), 230, 235, 255));
                c.drawRect(0, 0, w, g + h * 0.05f, fill);
            }
        }

        // haze along the horizon
        fill.setShader(new LinearGradient(0, g - h * 0.14f, 0, g + h * 0.02f, horizon & 0x00FFFFFF,
            Ui.withAlpha(horizon, 0x99), Shader.TileMode.CLAMP));
        c.drawRect(0, g - h * 0.14f, w, g + h * 0.02f, fill);
        fill.setShader(null);

        // hills: further = closer to the haze, nearer = darker
        float dark = 0.45f + 0.35f * night;
        fill.setColor(Ambient.blend(Ambient.blend(horizon, middle, 0.5f), 0xFF05060C, dark * 0.55f));
        c.drawPath(far, fill);
        fill.setColor(Ambient.blend(Ambient.blend(middle, top, 0.4f), 0xFF05060C, dark * 0.8f));
        c.drawPath(mid, fill);
        fill.setShader(new LinearGradient(0, g, 0, h, Ambient.blend(top, 0xFF04050A, 0.82f), 0xFF020306, Shader.TileMode.CLAMP));
        c.drawPath(near, fill);
        fill.setShader(null);

        if (fog) { // a pale bank of fog over the hills
            int fc = Ambient.blend(0xFFDCE2EA, 0xFF3A4252, night);
            fill.setShader(new LinearGradient(0, g - h * 0.2f, 0, h * NEAR + h * 0.05f, fc & 0x00FFFFFF,
                Ui.withAlpha(fc, 0x8C), Shader.TileMode.CLAMP));
            c.drawRect(0, g - h * 0.2f, w, h * NEAR + h * 0.05f, fill);
            fill.setShader(null);
        }
        if (drops.length > 0) { // rain streaks or snowflakes, behind the app row
            dropPaint.setColor(snow ? Color.argb(200, 255, 255, 255) : Ui.withAlpha(Ambient.blend(0xFFD8E2F0, top, 0.2f), 0x60));
            dropPaint.setStrokeWidth(Ui.px(snow ? 1 : 1.2f));
            dropPaint.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < drops.length; i += 4) {
                drops[i + 1] += drops[i + 2] * dt;
                drops[i] -= drops[i + 2] * dt * (snow ? 0.15f * (float) Math.sin(now / 900.0 + i) : 0.12f);
                if (drops[i + 1] > h) { drops[i + 1] = -drops[i + 3]; drops[i] = rnd.nextFloat() * w; }
                if (drops[i] < 0) drops[i] += w;
                if (snow) c.drawCircle(drops[i], drops[i + 1], drops[i + 3], dropPaint);
                else c.drawLine(drops[i], drops[i + 1], drops[i] - drops[i + 3] * 0.12f, drops[i + 1] + drops[i + 3], dropPaint);
            }
        }

        boolean moving = false;
        if (glowTarget >= 0) { // rim light above the focused card
            glowX += (glowTarget - glowX) * 0.18f;
            glowShown = Ambient.blend(glowShown | 0xFF000000, glowColor, 0.18f);
            moving = Math.abs(glowTarget - glowX) > 1;
            float spread = w * 0.16f;
            rim.setShader(new RadialGradient(glowX, h * NEAR, spread, Ui.withAlpha(glowShown, 0x66),
                glowShown & 0x00FFFFFF, Shader.TileMode.CLAMP));
            rim.setStrokeWidth(Ui.px(34));
            c.drawPath(nearEdge, rim);
            rim.setShader(new LinearGradient(glowX - spread, 0, glowX + spread, 0,
                new int[]{glowShown & 0x00FFFFFF, Ui.withAlpha(glowShown, 0xF0), glowShown & 0x00FFFFFF},
                null, Shader.TileMode.CLAMP));
            rim.setStrokeWidth(Ui.px(3));
            c.drawPath(nearEdge, rim);
            rim.setShader(null);
        }

        long next = moving ? 33 : drops.length > 0 ? 50 : storm ? 60 : puffX.length > 0 ? 100 : night > 0.02f ? 120 : 4000;
        if (running) postInvalidateDelayed(next);
    }

    void drawSun(Canvas c, float x, float y, double altDeg) {
        float r = Ui.px(30), low = (float) Math.max(0, Math.min(1, 1 - altDeg / 25));
        int halo = Ambient.blend(0xFFFFE6B0, horizon, low * 0.6f);
        orb.setShader(new RadialGradient(x, y, r * 6, Ui.withAlpha(halo, 0x70), halo & 0x00FFFFFF, Shader.TileMode.CLAMP));
        float veil = Math.max(0, (cloud - 0.5f) / 0.5f); // only real overcast hides the sun
        orb.setAlpha((int) (255 * (1f - 0.6f * veil)));
        c.drawCircle(x, y, r * 6, orb);
        orb.setShader(null);
        orb.setColor(Ambient.blend(0xFFFFF6DE, 0xFFFFA860, low));
        orb.setAlpha((int) (255 * (1f - 0.85f * veil)));
        c.drawCircle(x, y, r, orb);
        orb.setAlpha(255);
    }

    /**
     * The moon with its real phase: the lit part is a half disc plus or minus a half ellipse,
     * turned so the bright limb faces the sun as it does in the sky over this place.
     */
    void drawMoon(Canvas c, float x, float y, Astro.Pos pos, Astro.Light l, float night, float g) {
        float r = Ui.px(24);
        float vis = (0.45f + 0.55f * night) * (1f - 0.75f * cloud); // pale by day, bright at night, veiled by cloud
        int under = Ambient.blend(top, middle, Math.max(0, Math.min(1, y / g)));

        if (night > 0.2f) { // soft glow, stronger nearer full
            int a = (int) (0x40 * l.fraction * night);
            orb.setShader(new RadialGradient(x, y, r * 5, Ui.withAlpha(0xFFDDE4FF, a), 0x00DDE4FF, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r * 5, orb);
            orb.setShader(null);
        }
        // the unlit side: faint earthshine at night, invisible against a day sky
        orb.setColor(Ui.withAlpha(Ambient.blend(under, 0xFF8090B0, 0.25f), (int) (0xFF * night)));
        c.drawCircle(x, y, r, orb);

        // bright limb direction on screen: zenith angle measured anticlockwise from up
        double z = l.angle - pos.parallactic;
        float rot = (float) Math.toDegrees(Math.atan2(-Math.cos(z), -Math.sin(z)));
        float k = (float) (2 * l.fraction - 1); // -1 new … 0 half … 1 full

        c.save();
        c.translate(x, y);
        c.rotate(rot);
        lit.reset();
        lit.arcTo(-r, -r, r, r, -90, 180, true);          // right half, top to bottom
        float ex = r * Math.abs(k);
        if (k >= 0) lit.arcTo(-ex, -r, ex, r, 90, 180, false);   // gibbous: bulge out on the left
        else lit.arcTo(-ex, -r, ex, r, 90, -180, false);         // crescent: cut back in on the right
        lit.close();
        orb.setColor(Ui.withAlpha(0xFFF4F1E6, (int) (0xFF * vis)));
        c.drawPath(lit, orb);
        // seas, fixed to the moon's face; the face turns with the parallactic angle, not the phase
        c.save();
        c.clipPath(lit);
        c.rotate((float) -Math.toDegrees(pos.parallactic) - rot);
        if (lat < 0) c.rotate(180);
        for (float[] m : MARIA) { // soft-edged seas, like the real moon's grey patches
            orb.setShader(new RadialGradient(m[0] * r, m[1] * r, m[2] * r,
                new int[]{Ui.withAlpha(0xFF96969E, (int) (0x56 * vis)), Ui.withAlpha(0xFF96969E, (int) (0x38 * vis)), 0x0096969E},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(m[0] * r, m[1] * r, m[2] * r, orb);
        }
        orb.setShader(null);
        c.restore();
        c.restore();
    }

    void sample(double alt, boolean morning) {
        int[][] keys = morning ? MORNING : EVENING;
        int i = 0;
        if (alt <= KEY_ALT[0]) { set(keys[0], keys[0], 0); return; }
        if (alt >= KEY_ALT[KEY_ALT.length - 1]) { int n = keys.length - 1; set(keys[n], keys[n], 0); return; }
        while (alt >= KEY_ALT[i + 1]) i++;
        float f = (float) ((alt - KEY_ALT[i]) / (KEY_ALT[i + 1] - KEY_ALT[i]));
        set(keys[i], keys[i + 1], f * f * (3 - 2 * f));
    }

    void set(int[] a, int[] b, float f) {
        top = Ambient.blend(a[0], b[0], f);
        middle = Ambient.blend(a[1], b[1], f);
        horizon = Ambient.blend(a[2], b[2], f);
    }
}
