package com.limelight.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.limelight.utils.UiHelper;

/**
 * PlayStation Portal–inspired procedural connecting animation featuring smoothly
 * expanding portal ripple rings, counter-rotating orbital light sweeps, a luminous
 * central portal core, and converging energy motes & PlayStation micro-glyphs.
 *
 * Uses SystemClock.uptimeMillis() driven by an infinite ValueAnimator so phase progression
 * is strictly monotonic and completely free of repeat-boundary stutters.
 */
public class PortalConnectingView extends View {

    private static final int RIPPLE_COUNT = 4;
    private static final int PARTICLE_COUNT = 16;

    private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rippleGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rippleCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orbitSweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orbitInnerSweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coreFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coreRimGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coreRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coreSpecPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerEmblemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particleFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF orbitRect = new RectF();
    private final RectF innerOrbitRect = new RectF();
    private final RectF coreRect = new RectF();
    private final Path trianglePath = new Path();
    private final Matrix sweepMatrix = new Matrix();
    private final Matrix innerSweepMatrix = new Matrix();

    private SweepGradient outerSweepShader;
    private SweepGradient innerSweepShader;

    private AppThemePalette palette = AppThemePalette.MIDNIGHT;
    private int neonAccent;
    private int dp1;

    private ValueAnimator ticker;
    private long startMs = 0L;
    private int cachedW = -1;
    private int cachedH = -1;
    private String cachedPaletteKey = "";

    public PortalConnectingView(Context context) {
        super(context);
        init(context);
    }

    public PortalConnectingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        palette = AppThemePalette.getCurrent(context);
        neonAccent = palette.getNeonAccent();
        dp1 = Math.max(1, Math.round(UiHelper.dpToPx(context, 1f)));

        auraPaint.setStyle(Paint.Style.FILL);
        rippleGlowPaint.setStyle(Paint.Style.STROKE);
        rippleCorePaint.setStyle(Paint.Style.STROKE);

        orbitSweepPaint.setStyle(Paint.Style.STROKE);
        orbitSweepPaint.setStrokeCap(Paint.Cap.ROUND);
        orbitInnerSweepPaint.setStyle(Paint.Style.STROKE);
        orbitInnerSweepPaint.setStrokeCap(Paint.Cap.ROUND);

        coreFillPaint.setStyle(Paint.Style.FILL);
        coreRimGlowPaint.setStyle(Paint.Style.STROKE);
        coreRimPaint.setStyle(Paint.Style.STROKE);
        coreSpecPaint.setStyle(Paint.Style.STROKE);

        centerEmblemPaint.setStyle(Paint.Style.STROKE);
        centerEmblemPaint.setStrokeCap(Paint.Cap.ROUND);
        centerEmblemPaint.setStrokeJoin(Paint.Join.ROUND);

        particleStrokePaint.setStyle(Paint.Style.STROKE);
        particleStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        particleStrokePaint.setStrokeJoin(Paint.Join.ROUND);
        particleFillPaint.setStyle(Paint.Style.FILL);

        startMs = SystemClock.uptimeMillis();
    }

    public void refreshPalette() {
        palette = AppThemePalette.getCurrent(getContext());
        neonAccent = palette.getNeonAccent();
        cachedW = -1;
        invalidate();
    }

    private void startTicker() {
        if (ticker == null) {
            ticker = ValueAnimator.ofFloat(0f, 1f);
            ticker.setDuration(1000L);
            ticker.setRepeatCount(ValueAnimator.INFINITE);
            ticker.setInterpolator(new LinearInterpolator());
            ticker.addUpdateListener(animation -> invalidate());
        }
        if (!ticker.isStarted()) {
            if (startMs == 0L) {
                startMs = SystemClock.uptimeMillis();
            }
            ticker.start();
        }
    }

    private void stopTicker() {
        if (ticker != null && ticker.isStarted()) {
            ticker.cancel();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshPalette();
        if (getVisibility() == VISIBLE) {
            startTicker();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopTicker();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && isAttachedToWindow()) {
            startTicker();
        } else {
            stopTicker();
        }
    }

    private void ensureShaders(int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        if (w == cachedW && h == cachedH && palette.key.equals(cachedPaletteKey)) {
            return;
        }
        cachedW = w;
        cachedH = h;
        cachedPaletteKey = palette.key;

        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float maxR = Math.min(w, h) * 0.48f;

        auraPaint.setShader(new RadialGradient(
                cx, cy, Math.max(1f, maxR),
                new int[]{
                        AppThemePalette.withAlpha(neonAccent, 0.36f),
                        AppThemePalette.withAlpha(palette.accent, 0.22f),
                        AppThemePalette.withAlpha(palette.secondary, 0.08f),
                        0x00000000
                },
                new float[]{0f, 0.35f, 0.72f, 1f},
                Shader.TileMode.CLAMP));

        outerSweepShader = new SweepGradient(
                cx, cy,
                new int[]{
                        0x00000000,
                        AppThemePalette.withAlpha(palette.accentLight, 0.18f),
                        AppThemePalette.withAlpha(palette.highlight, 0.92f),
                        0xFFFFFFFF,
                        0x00000000,
                        0x00000000,
                        AppThemePalette.withAlpha(neonAccent, 0.20f),
                        AppThemePalette.withAlpha(palette.accentLight, 0.78f),
                        0x00000000
                },
                new float[]{0f, 0.10f, 0.22f, 0.27f, 0.31f, 0.50f, 0.62f, 0.75f, 0.80f});
        orbitSweepPaint.setShader(outerSweepShader);

        innerSweepShader = new SweepGradient(
                cx, cy,
                new int[]{
                        0x00000000,
                        AppThemePalette.withAlpha(neonAccent, 0.22f),
                        AppThemePalette.withAlpha(palette.accentLight, 0.88f),
                        0x00000000,
                        0x00000000,
                        AppThemePalette.withAlpha(palette.highlight, 0.65f),
                        0x00000000
                },
                new float[]{0f, 0.14f, 0.30f, 0.36f, 0.58f, 0.78f, 0.85f});
        orbitInnerSweepPaint.setShader(innerSweepShader);

        float coreR = maxR * 0.26f;
        coreFillPaint.setShader(new RadialGradient(
                cx, cy - coreR * 0.2f, Math.max(1f, coreR * 1.15f),
                new int[]{
                        AppThemePalette.withAlpha(palette.accentLight, 0.34f),
                        AppThemePalette.withAlpha(palette.surface, 0.86f),
                        0xE60B101D
                },
                new float[]{0f, 0.58f, 1f},
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        ensureShaders(w, h);

        float t = (SystemClock.uptimeMillis() - startMs) / 1000f;
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float maxR = Math.min(w, h) * 0.46f;

        // 1. Breathing ambient radial aura
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.4f);
        auraPaint.setAlpha(Math.round(190 + 65 * pulse));
        canvas.drawCircle(cx, cy, maxR * (0.94f + 0.06f * pulse), auraPaint);

        // 2. Smoothly expanding PlayStation Portal ripple waves
        float coreBaseR = maxR * 0.26f;
        for (int i = 0; i < RIPPLE_COUNT; i++) {
            float phase = (t * 0.38f + (i / (float) RIPPLE_COUNT)) % 1f;
            float eased = 1f - (float) Math.pow(1f - phase, 1.85);
            float r = coreBaseR + (maxR * 0.96f - coreBaseR) * eased;

            // Smooth bell fade-in at core and gentle fade-out at rim
            float fadeIn = Math.min(1f, phase / 0.14f);
            float fadeOut = (float) Math.pow(1f - phase, 1.35);
            float alpha = fadeIn * fadeOut;

            int ringColor = (i % 2 == 0) ? palette.highlight : palette.accentLight;
            rippleGlowPaint.setColor(ringColor);
            rippleGlowPaint.setAlpha(Math.round(alpha * 78f));
            rippleGlowPaint.setStrokeWidth(Math.max(dp1 * 4f, maxR * 0.045f * (1f - 0.35f * phase)));
            canvas.drawCircle(cx, cy, r, rippleGlowPaint);

            rippleCorePaint.setColor(Color.WHITE);
            rippleCorePaint.setAlpha(Math.round(alpha * 165f));
            rippleCorePaint.setStrokeWidth(Math.max(dp1 * 1.1f, dp1 * (2.1f - 1.0f * phase)));
            canvas.drawCircle(cx, cy, r, rippleCorePaint);
        }

        // 3. Converging energy particles & PlayStation micro-glyphs
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float seed = (i * 0.6180339f) % 1f;
            float speed = 0.34f + (i % 4) * 0.06f;
            float prog = (t * speed + seed) % 1f; // 0 = outer edge, 1 = absorbed into core

            float baseAngle = (float) (i * (Math.PI * 2.0 / PARTICLE_COUNT));
            float spiralAngle = baseAngle + prog * 1.15f + (float) Math.sin(t * 0.9f + i) * 0.12f;
            float dist = maxR * (0.95f - 0.72f * (float) Math.pow(prog, 1.15));

            float px = cx + (float) Math.cos(spiralAngle) * dist;
            float py = cy + (float) Math.sin(spiralAngle) * dist;

            float pFadeIn = Math.min(1f, prog / 0.18f);
            float pFadeOut = Math.min(1f, (1f - prog) / 0.22f);
            float pAlpha = pFadeIn * pFadeOut;
            if (pAlpha <= 0.01f) {
                continue;
            }

            float symSize = Math.max(dp1 * 2.5f, maxR * (0.032f + 0.014f * (1f - prog)));
            int symColor = (i % 3 == 0) ? 0xFFFFFFFF : ((i % 2 == 0) ? palette.highlight : neonAccent);
            int alphaInt = Math.round(pAlpha * 210f);

            int kind = i % 5;
            if (kind == 4) {
                // Soft glowing energy orb
                particleFillPaint.setColor(symColor);
                particleFillPaint.setAlpha(Math.round(alphaInt * 0.38f));
                canvas.drawCircle(px, py, symSize * 1.65f, particleFillPaint);
                particleFillPaint.setAlpha(alphaInt);
                canvas.drawCircle(px, py, symSize * 0.62f, particleFillPaint);
            } else {
                canvas.save();
                canvas.translate(px, py);
                canvas.rotate((t * 45f + i * 33f) % 360f);
                particleStrokePaint.setColor(symColor);
                particleStrokePaint.setAlpha(alphaInt);
                particleStrokePaint.setStrokeWidth(Math.max(dp1 * 1.1f, symSize * 0.26f));

                if (kind == 0) {
                    // Triangle
                    float hTri = symSize * 1.05f;
                    trianglePath.reset();
                    trianglePath.moveTo(0f, -hTri);
                    trianglePath.lineTo(hTri * 0.88f, hTri * 0.65f);
                    trianglePath.lineTo(-hTri * 0.88f, hTri * 0.65f);
                    trianglePath.close();
                    canvas.drawPath(trianglePath, particleStrokePaint);
                } else if (kind == 1) {
                    // Circle
                    canvas.drawCircle(0f, 0f, symSize * 0.85f, particleStrokePaint);
                } else if (kind == 2) {
                    // Cross
                    float s = symSize * 0.78f;
                    canvas.drawLine(-s, -s, s, s, particleStrokePaint);
                    canvas.drawLine(-s, s, s, -s, particleStrokePaint);
                } else {
                    // Square
                    float s = symSize * 0.76f;
                    canvas.drawRect(-s, -s, s, s, particleStrokePaint);
                }
                canvas.restore();
            }
        }

        // 4. Counter-rotating orbital light sweeps
        float outerOrbitR = maxR * 0.56f;
        orbitRect.set(cx - outerOrbitR, cy - outerOrbitR, cx + outerOrbitR, cy + outerOrbitR);
        sweepMatrix.setRotate((t * 135f) % 360f, cx, cy);
        if (outerSweepShader != null) {
            outerSweepShader.setLocalMatrix(sweepMatrix);
        }
        orbitSweepPaint.setStrokeWidth(Math.max(dp1 * 2.2f, maxR * 0.026f));
        canvas.drawOval(orbitRect, orbitSweepPaint);

        float innerOrbitR = maxR * 0.40f;
        innerOrbitRect.set(cx - innerOrbitR, cy - innerOrbitR, cx + innerOrbitR, cy + innerOrbitR);
        innerSweepMatrix.setRotate((-t * 98f) % 360f, cx, cy);
        if (innerSweepShader != null) {
            innerSweepShader.setLocalMatrix(innerSweepMatrix);
        }
        orbitInnerSweepPaint.setStrokeWidth(Math.max(dp1 * 1.8f, maxR * 0.020f));
        canvas.drawOval(innerOrbitRect, orbitInnerSweepPaint);

        // 5. Breathing luminous central portal core
        float coreScale = 1f + 0.045f * (float) Math.sin(t * 3.1f);
        float coreR = coreBaseR * coreScale;
        coreRect.set(cx - coreR, cy - coreR, cx + coreR, cy + coreR);

        canvas.drawCircle(cx, cy, coreR, coreFillPaint);

        coreRimGlowPaint.setColor(neonAccent);
        coreRimGlowPaint.setAlpha(Math.round(135 + 55 * pulse));
        coreRimGlowPaint.setStrokeWidth(Math.max(dp1 * 5f, maxR * 0.055f));
        canvas.drawCircle(cx, cy, coreR, coreRimGlowPaint);

        coreRimPaint.setColor(Color.WHITE);
        coreRimPaint.setAlpha(220);
        coreRimPaint.setStrokeWidth(Math.max(dp1 * 1.6f, maxR * 0.020f));
        canvas.drawCircle(cx, cy, coreR, coreRimPaint);

        // Specular top-left glass rim highlight
        coreSpecPaint.setColor(0x99FFFFFF);
        coreSpecPaint.setStrokeWidth(Math.max(dp1, dp1 * 1.2f));
        canvas.drawArc(coreRect, 200f, 110f, false, coreSpecPaint);

        // Central portal aperture ring & inner bright node
        float innerPortalR = coreR * 0.44f;
        centerEmblemPaint.setColor(palette.highlight);
        centerEmblemPaint.setAlpha(235);
        centerEmblemPaint.setStrokeWidth(Math.max(dp1 * 2f, coreR * 0.11f));
        canvas.drawCircle(cx, cy, innerPortalR, centerEmblemPaint);

        particleFillPaint.setColor(0xFFFFFFFF);
        particleFillPaint.setAlpha(Math.round(165 + 75 * pulse));
        canvas.drawCircle(cx, cy, innerPortalR * (0.34f + 0.06f * pulse), particleFillPaint);
    }
}
