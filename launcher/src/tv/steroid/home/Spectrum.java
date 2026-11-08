package tv.steroid.home;

import android.content.Context;
import android.graphics.*;
import android.media.audiofx.Visualizer;
import android.view.View;

/**
 * Full-screen visualizer fed by android.media.audiofx.Visualizer on the radio's audio session.
 * Draws nothing reactive unless real FFT data arrives; {@link #live()} says which is the case.
 */
class Spectrum extends View {
    static final int BANDS = 48;
    final float[] level = new float[BANDS], target = new float[BANDS], peak = new float[BANDS];
    final float[] wave = new float[256];
    final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG),
        line = new Paint(Paint.ANTI_ALIAS_FLAG), cap = new Paint(Paint.ANTI_ALIAS_FLAG), mirror = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path wavePath = new Path();
    Visualizer vis;
    int accent = 0xFFE8915A, session = -1, rate = 44100000;
    long lastData;
    float bass, bassShown;
    boolean running;
    Runnable onLiveChange;
    boolean wasLive;

    Spectrum(Context c) {
        super(c);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Ui.px(1.5f));
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    void accent(int c) {
        accent = c;
        if (getHeight() > 0) shaders(getWidth(), getHeight());
    }

    boolean live() { return System.currentTimeMillis() - lastData < 1500; }

    /** Attaches to the audio session; returns false if the platform refuses (e.g. no permission). */
    boolean attach(int sessionId) {
        if (sessionId == session && vis != null) return true;
        detach();
        if (sessionId == 0) return false;
        try {
            vis = new Visualizer(sessionId);
            vis.setEnabled(false);
            int[] range = Visualizer.getCaptureSizeRange();
            vis.setCaptureSize(Math.min(1024, range[1]));
            rate = vis.getSamplingRate();
            vis.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                public void onWaveFormDataCapture(Visualizer v, byte[] w, int r) { onWave(w); }
                public void onFftDataCapture(Visualizer v, byte[] f, int r) { onFft(f); }
            }, Visualizer.getMaxCaptureRate(), true, true);
            vis.setEnabled(true);
            session = sessionId;
            return true;
        } catch (Throwable t) {
            detach();
            return false;
        }
    }

    void detach() {
        if (vis != null) { try { vis.setEnabled(false); vis.release(); } catch (Throwable ignored) {} }
        vis = null;
        session = -1;
    }

    void onFft(byte[] f) {
        int n = f.length / 2;
        float nyquist = rate / 2000f; // Hz
        float lo = 90f, hi = Math.min(16000f, nyquist);
        double energy = 0;
        for (int b = 0; b < BANDS; b++) {
            float f0 = (float) (lo * Math.pow(hi / lo, b / (float) BANDS));
            float f1 = (float) (lo * Math.pow(hi / lo, (b + 1) / (float) BANDS));
            int k0 = Math.max(1, (int) (f0 / nyquist * n)), k1 = Math.max(k0 + 1, (int) (f1 / nyquist * n));
            float m = 0;
            for (int k = k0; k < k1 && k < n; k++) {
                float re = f[2 * k], im = f[2 * k + 1];
                m = Math.max(m, (float) Math.hypot(re, im));
            }
            // magnitude (0..~181) -> dB -> 0..1, with a gentle tilt so highs aren't swamped by bass
            float db = (float) (20 * Math.log10(m + 1e-3)) + b * 0.18f;
            float v = Math.max(0f, Math.min(1f, (db - 18f) / 30f));
            v = v * v * (3 - 2 * v); // smoothstep: more contrast between quiet and loud bands
            target[b] = v;
            energy += m;
        }
        float low = (target[0] + target[1] + target[2] + target[3] + target[4]) / 5f;
        bass = low;
        if (energy > 0) lastData = System.currentTimeMillis();
    }

    void onWave(byte[] w) {
        int step = Math.max(1, w.length / wave.length);
        for (int i = 0; i < wave.length && i * step < w.length; i++) wave[i] = ((w[i * step] & 0xFF) - 128) / 128f;
    }

    void run(boolean r) { running = r; if (r) invalidate(); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { shaders(w, h); }

    void shaders(int w, int h) {
        float base = h * 0.80f;
        bar.setShader(new LinearGradient(0, base - h * 0.38f, 0, base,
            new int[]{Ambient.blend(accent, 0xFFFFFFFF, 0.55f), accent, Ui.withAlpha(accent, 0x70)}, new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        mirror.setShader(new LinearGradient(0, base, 0, base + h * 0.16f,
            Ui.withAlpha(accent, 0x55), Ui.withAlpha(accent, 0), Shader.TileMode.CLAMP));
        cap.setColor(0xF2FFFFFF);
        line.setColor(Ui.withAlpha(0xFFFFFFFF, 0x66));
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        boolean live = live();
        if (live != wasLive) { wasLive = live; if (onLiveChange != null) post(onLiveChange); }
        if (!live) for (int i = 0; i < BANDS; i++) target[i] = 0;
        float base = h * 0.80f, maxH = h * 0.38f;
        float margin = w * 0.06f, slot = (w - 2 * margin) / BANDS, bw = slot * 0.56f;

        bassShown += ((live ? bass : 0) - bassShown) * 0.25f;
        float gr = w * (0.28f + 0.16f * bassShown);
        glow.setShader(new RadialGradient(w * 0.5f, base, gr, Ui.withAlpha(accent, (int) (40 + 120 * bassShown)),
            Ui.withAlpha(accent, 0), Shader.TileMode.CLAMP));
        c.drawCircle(w * 0.5f, base, gr, glow);

        for (int i = 0; i < BANDS; i++) {
            float t = target[i];
            level[i] += (t - level[i]) * (t > level[i] ? 0.55f : 0.14f);
            peak[i] = Math.max(level[i], peak[i] - 0.008f);
            float x = margin + i * slot + (slot - bw) / 2f;
            float bh = Math.max(Ui.px(2), level[i] * maxH);
            float r = bw / 2f;
            c.drawRoundRect(x, base - bh, x + bw, base, r, r, bar);
            c.drawRoundRect(x, base + Ui.px(4), x + bw, base + Ui.px(4) + bh * 0.32f, r, r, mirror);
            if (live && peak[i] > 0.02f) {
                float py = base - peak[i] * maxH - Ui.px(6);
                c.drawRoundRect(x, py - Ui.px(2.5f), x + bw, py, r, r, cap);
            }
        }

        if (live) {
            wavePath.reset();
            float mid = h * 0.80f + Ui.px(2), amp = h * 0.03f;
            for (int i = 0; i < wave.length; i++) {
                float x = w * 0.1f + i * (w * 0.8f) / (wave.length - 1);
                float y = mid + wave[i] * amp;
                if (i == 0) wavePath.moveTo(x, y); else wavePath.lineTo(x, y);
            }
            c.drawPath(wavePath, line);
        }
        if (running) postInvalidateDelayed(33);
    }
}
