package com.limelight.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
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
 * PlayStation Portal–inspired connecting & stream-reveal animation:
 * 1. Solar Eclipse Circular Window: A circular aperture in a dark surround with a multi-layered
 *    solar-eclipse corona glow around the rim.
 * 2. Clipped Inward-Flowing Particles: Glowing nebula/dust particles inside/behind the circular
 *    window flowing toward the center, strictly clipped so they are only visible through the window.
 * 3. Stream Reveal & Dr. Strange Portal Rim Sparks + Expansion: When the stream is ready, the dark
 *    interior of the circular window fades out to reveal the live stream inside the circle, while
 *    tangential Dr. Strange–style spark particles ignite around the rim, starting slow and spinning
 *    faster and faster as the circular window expands to fullscreen.
 */
public class PortalConnectingView extends View {

    private static final int INNER_PARTICLE_COUNT = 96;
    private static final int RIM_SPARK_COUNT = 64;

    private final Paint surroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coronaOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coronaInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coronaSweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint introRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint windowInteriorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint windowCenterGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particleGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particleCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particleTrailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint sparkGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sparkCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path outerEclipseMaskPath = new Path();
    private final Path windowCirclePath = new Path();
    private final Matrix sweepMatrix = new Matrix();

    private SweepGradient coronaSweepShader;

    private AppThemePalette palette = AppThemePalette.MIDNIGHT;
    private int neonAccent;
    private float dp1;

    private boolean compactCardMode = false;
    private ValueAnimator ticker;
    private ValueAnimator revealAnimator;
    private long startMs = 0L;

    // 0.0 during normal connecting; 0.0 -> 1.0 during stream reveal & expansion
    private float revealProgress = 0f;
    private boolean revealing = false;

    private int cachedW = -1;
    private int cachedH = -1;
    private float cachedWindowR = -1f;
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
        dp1 = Math.max(1f, UiHelper.dpToPx(context, 1f));

        outerEclipseMaskPath.setFillType(Path.FillType.EVEN_ODD);
        windowCirclePath.setFillType(Path.FillType.WINDING);

        surroundPaint.setStyle(Paint.Style.FILL);
        coronaOuterPaint.setStyle(Paint.Style.FILL);
        coronaInnerPaint.setStyle(Paint.Style.FILL);

        coronaSweepPaint.setStyle(Paint.Style.STROKE);
        coronaSweepPaint.setStrokeCap(Paint.Cap.ROUND);

        rimGlowPaint.setStyle(Paint.Style.STROKE);
        rimCorePaint.setStyle(Paint.Style.STROKE);
        introRingPaint.setStyle(Paint.Style.STROKE);

        windowInteriorPaint.setStyle(Paint.Style.FILL);
        windowCenterGlowPaint.setStyle(Paint.Style.FILL);

        particleGlowPaint.setStyle(Paint.Style.FILL);
        particleCorePaint.setStyle(Paint.Style.FILL);
        particleTrailPaint.setStyle(Paint.Style.STROKE);
        particleTrailPaint.setStrokeCap(Paint.Cap.ROUND);

        sparkGlowPaint.setStyle(Paint.Style.STROKE);
        sparkGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        sparkCorePaint.setStyle(Paint.Style.STROKE);
        sparkCorePaint.setStrokeCap(Paint.Cap.ROUND);
        emberPaint.setStyle(Paint.Style.FILL);

        startMs = SystemClock.uptimeMillis();
    }

    public void setCompactCardMode(boolean compactCardMode) {
        this.compactCardMode = compactCardMode;
        cachedW = -1;
        invalidate();
    }

    public void refreshPalette() {
        palette = AppThemePalette.getCurrent(getContext());
        neonAccent = palette.getNeonAccent();
        cachedW = -1;
        invalidate();
    }

    public boolean isRevealing() {
        return revealing;
    }

    /**
     * Transitions the portal from the connecting state into the stream-reveal & fullscreen expansion:
     * - Phase 1 (0.00 -> 0.34): Dark window interior + inward particles fade out to reveal the live stream
     *   inside the circular window while Dr. Strange rim sparks ignite and start spinning slowly.
     * - Phase 2 (0.26 -> 1.00): Rim sparks accelerate rapidly around the circumference as the circular
     *   window expands outward past the screen edges to show the stream fullscreen.
     */
    public void startStreamReveal(@Nullable final Runnable onComplete) {
        if (revealing) {
            return;
        }
        revealing = true;
        startTicker();

        if (revealAnimator != null) {
            revealAnimator.cancel();
        }
        revealAnimator = ValueAnimator.ofFloat(0f, 1f);
        revealAnimator.setDuration(1450L);
        revealAnimator.setInterpolator(new LinearInterpolator());
        revealAnimator.addUpdateListener(anim -> {
            revealProgress = (float) anim.getAnimatedValue();
            invalidate();
        });
        revealAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean canceled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!canceled && onComplete != null) {
                    onComplete.run();
                }
            }
        });
        revealAnimator.start();
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
        if (revealAnimator != null && revealAnimator.isStarted()) {
            revealAnimator.cancel();
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

    private void updateWindowShaders(int w, int h, float cx, float cy, float windowR) {
        boolean sizeChanged = (w != cachedW || h != cachedH || !palette.key.equals(cachedPaletteKey));
        boolean radiusChanged = Math.abs(windowR - cachedWindowR) > 1.5f;
        if (!sizeChanged && !radiusChanged) {
            return;
        }
        cachedW = w;
        cachedH = h;
        cachedWindowR = windowR;
        cachedPaletteKey = palette.key;

        float safeWindowR = Math.max(8f, windowR);

        // Wide atmospheric solar-eclipse corona bloom around the circular aperture
        float outerBloomR = safeWindowR * 2.15f;
        float rimStop = Math.min(0.82f, safeWindowR / outerBloomR);
        coronaOuterPaint.setShader(new RadialGradient(
                cx, cy, Math.max(1f, outerBloomR),
                new int[]{
                        0x00000000,
                        AppThemePalette.withAlpha(neonAccent, 0.72f),
                        AppThemePalette.withAlpha(palette.accentLight, 0.46f),
                        AppThemePalette.withAlpha(palette.accent, 0.22f),
                        AppThemePalette.withAlpha(palette.secondary, 0.08f),
                        0x00000000
                },
                new float[]{
                        Math.max(0f, rimStop - 0.03f),
                        rimStop,
                        Math.min(0.92f, rimStop + 0.07f),
                        Math.min(0.96f, rimStop + 0.22f),
                        Math.min(0.99f, rimStop + 0.42f),
                        1f
                },
                Shader.TileMode.CLAMP));

        // Intense inner solar-eclipse corona ring tight against the circular edge
        float innerBloomR = safeWindowR * 1.28f;
        float innerStop = Math.min(0.90f, safeWindowR / innerBloomR);
        coronaInnerPaint.setShader(new RadialGradient(
                cx, cy, Math.max(1f, innerBloomR),
                new int[]{
                        0x00000000,
                        0xFFFFFFFF,
                        AppThemePalette.withAlpha(palette.highlight, 0.92f),
                        AppThemePalette.withAlpha(neonAccent, 0.54f),
                        0x00000000
                },
                new float[]{
                        Math.max(0f, innerStop - 0.02f),
                        innerStop,
                        Math.min(0.95f, innerStop + 0.04f),
                        Math.min(0.98f, innerStop + 0.12f),
                        1f
                },
                Shader.TileMode.CLAMP));

        if (sizeChanged || coronaSweepShader == null) {
            coronaSweepShader = new SweepGradient(
                    cx, cy,
                    new int[]{
                            AppThemePalette.withAlpha(neonAccent, 0.35f),
                            AppThemePalette.withAlpha(palette.highlight, 0.95f),
                            0xFFFFFFFF,
                            AppThemePalette.withAlpha(palette.accentLight, 0.55f),
                            AppThemePalette.withAlpha(palette.accent, 0.25f),
                            AppThemePalette.withAlpha(neonAccent, 0.88f),
                            0xFFFFFFFF,
                            AppThemePalette.withAlpha(palette.highlight, 0.45f),
                            AppThemePalette.withAlpha(neonAccent, 0.35f)
                    },
                    new float[]{0f, 0.14f, 0.20f, 0.34f, 0.50f, 0.68f, 0.75f, 0.88f, 1f});
            coronaSweepPaint.setShader(coronaSweepShader);
        }

        // Center nebula glow inside the circular window
        windowCenterGlowPaint.setShader(new RadialGradient(
                cx, cy, Math.max(1f, safeWindowR * 0.95f),
                new int[]{
                        AppThemePalette.withAlpha(neonAccent, 0.26f),
                        AppThemePalette.withAlpha(palette.accent, 0.14f),
                        0x00000000
                },
                new float[]{0f, 0.48f, 1f},
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

        float t = (SystemClock.uptimeMillis() - startMs) / 1000f;
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float minDim = Math.min(w, h);

        // Base circular portal window radius (matches PS Portal proportions)
        float baseWindowR = compactCardMode ? (minDim * 0.34f) : (minDim * 0.27f);

        // Compute expansion during stream reveal (Stage 2: revealProgress 0.26 -> 1.0)
        float maxExpandR = (float) Math.hypot(w * 0.5f, h * 0.5f) * 1.18f;
        float expandProgress = 0f;
        if (revealing && revealProgress > 0.26f) {
            float rawExpand = (revealProgress - 0.26f) / 0.74f;
            // Smoothly accelerating curve: starts slow, accelerates rapidly as the portal opens fullscreen
            expandProgress = (float) Math.pow(rawExpand, 2.45);
        }

        // Subtle breathing while waiting; expands to fullscreen when revealing
        float breathe = 1f + 0.012f * (float) Math.sin(t * 2.2f);
        float windowR = baseWindowR * breathe + (maxExpandR - baseWindowR * breathe) * expandProgress;

        // Interior dark void + inward particles opacity:
        // 1.0 while connecting, fades to 0.0 during revealProgress 0.00 -> 0.34 so the live stream appears inside the window
        float interiorOpacity = 1f;
        if (revealing) {
            interiorOpacity = Math.max(0f, 1f - (revealProgress / 0.34f));
            interiorOpacity = interiorOpacity * interiorOpacity;
        }

        // Overall surround & corona fade out only at the very end of expansion (0.85 -> 1.0)
        float surroundAlphaFactor = 1f;
        if (revealing && revealProgress > 0.85f) {
            surroundAlphaFactor = Math.max(0f, (1f - revealProgress) / 0.15f);
        }

        updateWindowShaders(w, h, cx, cy, windowR);

        // Build circular window path and exterior eclipse mask path
        windowCirclePath.reset();
        windowCirclePath.addCircle(cx, cy, windowR, Path.Direction.CW);

        outerEclipseMaskPath.reset();
        outerEclipseMaskPath.addRect(0f, 0f, w, h, Path.Direction.CW);
        outerEclipseMaskPath.addCircle(cx, cy, windowR, Path.Direction.CW);

        // =========================================================================================
        // 1. INSIDE THE CIRCULAR WINDOW: Clipped dark cosmic void + inward-flowing glowing particles
        //    (Fades smoothly to transparent when the stream is ready, revealing the live stream!)
        // =========================================================================================
        if (interiorOpacity > 0.004f) {
            canvas.save();
            canvas.clipPath(windowCirclePath);

            // Deep cosmic void backdrop inside the window
            windowInteriorPaint.setColor(0xFF04060E);
            windowInteriorPaint.setAlpha(Math.round(250 * interiorOpacity));
            canvas.drawRect(cx - windowR - 2f, cy - windowR - 2f, cx + windowR + 2f, cy + windowR + 2f, windowInteriorPaint);

            // Subtle central nebula glow where particles converge
            float centerPulse = 0.5f + 0.5f * (float) Math.sin(t * 2.8f);
            windowCenterGlowPaint.setAlpha(Math.round((150 + 65 * centerPulse) * interiorOpacity));
            canvas.drawCircle(cx, cy, windowR * 0.95f, windowCenterGlowPaint);

            // Glowing particles flowing inward from behind the circular window rim toward the center
            drawInwardFlowingParticles(canvas, cx, cy, windowR, t, interiorOpacity);

            canvas.restore();
        }

        // =========================================================================================
        // 2. OUTSIDE THE CIRCULAR WINDOW: Solar Eclipse Surround + Corona Glow
        //    Clipped to outerEclipseMaskPath so it never hazes over the live stream inside the window!
        // =========================================================================================
        canvas.save();
        if (!compactCardMode) {
            canvas.clipPath(outerEclipseMaskPath);
            surroundPaint.setColor(0xFF03040A);
            surroundPaint.setAlpha(Math.round(248 * surroundAlphaFactor));
            canvas.drawPath(outerEclipseMaskPath, surroundPaint);
        }

        float coronaPulse = 0.5f + 0.5f * (float) Math.sin(t * 2.6f);
        // Boost corona intensity slightly when the stream reveals and sparks ignite
        float revealCoronaBoost = revealing ? (float) Math.sin(Math.min(1f, revealProgress * 1.3f) * Math.PI) * 0.35f : 0f;
        float coronaAlpha = Math.min(1f, (0.82f + 0.18f * coronaPulse + revealCoronaBoost) * surroundAlphaFactor);

        // Wide atmospheric solar-eclipse corona bloom
        coronaOuterPaint.setAlpha(Math.round(255 * coronaAlpha));
        canvas.drawCircle(cx, cy, windowR * 2.15f, coronaOuterPaint);

        // Tight white-hot / neon solar-eclipse inner corona ring
        coronaInnerPaint.setAlpha(Math.round(255 * coronaAlpha));
        canvas.drawCircle(cx, cy, windowR * 1.28f, coronaInnerPaint);

        // Slowly undulating plasma corona shimmer along the rim
        if (coronaSweepShader != null) {
            sweepMatrix.setRotate((t * 28f) % 360f, cx, cy);
            coronaSweepShader.setLocalMatrix(sweepMatrix);
            coronaSweepPaint.setStrokeWidth(Math.max(dp1 * 6f, windowR * 0.085f));
            coronaSweepPaint.setAlpha(Math.round(165 * coronaAlpha));
            canvas.drawCircle(cx, cy, windowR + dp1 * 2f, coronaSweepPaint);
        }

        // Initial PS Portal entry contraction ring (0.0s -> 0.65s: wide glow ring contracts onto the eclipse rim)
        if (!revealing && t < 0.68f) {
            float introU = t / 0.68f;
            float easedIntro = 1f - (float) Math.pow(1f - introU, 2.6);
            float introR = windowR * (1.52f - 0.52f * easedIntro);
            float introAlpha = (float) Math.sin(introU * Math.PI);
            introRingPaint.setColor(palette.highlight);
            introRingPaint.setStrokeWidth(Math.max(dp1 * 3f, windowR * 0.045f * (1f - 0.5f * introU)));
            introRingPaint.setAlpha(Math.round(210 * introAlpha));
            canvas.drawCircle(cx, cy, introR, introRingPaint);
        }

        canvas.restore();

        // =========================================================================================
        // 3. SOLAR ECLIPSE RIM EDGE: Crisp luminous ring right on the boundary of the circular window
        // =========================================================================================
        rimGlowPaint.setColor(neonAccent);
        rimGlowPaint.setStrokeWidth(Math.max(dp1 * 4.5f, windowR * 0.048f));
        rimGlowPaint.setAlpha(Math.round(195 * surroundAlphaFactor));
        canvas.drawCircle(cx, cy, windowR, rimGlowPaint);

        rimCorePaint.setColor(0xFFFFFFFF);
        rimCorePaint.setStrokeWidth(Math.max(dp1 * 1.6f, windowR * 0.015f));
        rimCorePaint.setAlpha(Math.round(240 * surroundAlphaFactor));
        canvas.drawCircle(cx, cy, windowR, rimCorePaint);

        // =========================================================================================
        // 4. DR. STRANGE PORTAL RIM SPARKS:
        //    When the stream is ready, glowing spark particles spin around the rim of the window,
        //    starting slow and accelerating rapidly as the window expands to fullscreen!
        // =========================================================================================
        if (revealing && revealProgress >= 0.12f) {
            drawSpinningPortalRimSparks(canvas, cx, cy, windowR, t, revealProgress, surroundAlphaFactor);
        }
    }

    /**
     * Draws glowing dust/nebula particles inside the circular window flowing from beyond the rim
     * toward the center of the window. Strictly clipped to windowCirclePath by caller.
     */
    private void drawInwardFlowingParticles(Canvas canvas, float cx, float cy, float windowR, float t, float interiorOpacity) {
        for (int i = 0; i < INNER_PARTICLE_COUNT; i++) {
            float seed = (i * 0.6180339887f) % 1f;
            float speed = 0.20f + (i % 7) * 0.032f;
            float prog = (t * speed + seed) % 1f; // 0 = outside/at rim, 1 = reaches center

            // Ease radial flow so particles drift from behind the window rim (1.14 * windowR) into the center (0.02 * windowR)
            float radialFraction = 1.14f * (1f - (float) Math.pow(prog, 0.92));
            float dist = windowR * radialFraction;

            // Golden-angle distribution with gentle inward swirl
            float baseAngle = i * 2.3999632f;
            float wobble = (float) Math.sin(t * 1.1f + i * 0.7f) * 0.09f;
            float swirl = prog * 0.42f * ((i % 2 == 0) ? 1f : -0.65f);
            float angle = baseAngle + swirl + wobble;

            float cosA = (float) Math.cos(angle);
            float sinA = (float) Math.sin(angle);
            float px = cx + cosA * dist;
            float py = cy + sinA * dist;

            // Smooth fade as particles approach the very center so they dissolve into the core
            float centerFade = Math.min(1f, radialFraction / 0.20f);
            // Shimmering twinkle per particle
            float twinkle = 0.68f + 0.32f * (float) Math.sin(t * (3.2f + (i % 5) * 0.6f) + i);
            float alpha = centerFade * twinkle * interiorOpacity;
            if (alpha <= 0.01f) {
                continue;
            }

            // Pick palette-coordinated particle color
            int color;
            int mod = i % 4;
            if (mod == 0) {
                color = neonAccent;
            } else if (mod == 1) {
                color = palette.highlight;
            } else if (mod == 2) {
                color = palette.accentLight;
            } else {
                color = 0xFFFFFFFF;
            }

            float sizeScale = 0.55f + 0.45f * Math.min(1f, radialFraction);
            float radius = Math.max(dp1 * 1.3f, windowR * (0.012f + (i % 4) * 0.0045f) * sizeScale);

            // Subtle inward motion trail pointing toward the center
            float trailLen = windowR * (0.035f + 0.045f * prog) * sizeScale;
            float tailX = px + cosA * trailLen;
            float tailY = py + sinA * trailLen;
            particleTrailPaint.setColor(color);
            particleTrailPaint.setStrokeWidth(Math.max(dp1 * 0.9f, radius * 0.65f));
            particleTrailPaint.setAlpha(Math.round(alpha * 110f));
            canvas.drawLine(tailX, tailY, px, py, particleTrailPaint);

            // Soft outer particle glow halo
            particleGlowPaint.setColor(color);
            particleGlowPaint.setAlpha(Math.round(alpha * 95f));
            canvas.drawCircle(px, py, radius * 2.4f, particleGlowPaint);

            // Bright particle core
            particleCorePaint.setColor((mod == 3) ? 0xFFFFFFFF : AppThemePalette.withAlpha(0xFFFFFFFF, 0.92f));
            particleCorePaint.setAlpha(Math.round(alpha * 245f));
            canvas.drawCircle(px, py, radius * 0.78f, particleCorePaint);
        }
    }

    /**
     * Draws Dr. Strange–inspired tangential spinning sparks around the circular window rim.
     * Starts slow when the stream first fades in, then spins faster and faster with longer
     * tangential spark streaks and flying embers as the window expands to fullscreen.
     */
    private void drawSpinningPortalRimSparks(
            Canvas canvas,
            float cx,
            float cy,
            float windowR,
            float t,
            float revealProg,
            float surroundAlphaFactor) {

        float s = Math.max(0f, Math.min(1f, (revealProg - 0.12f) / 0.88f));
        // Smooth ignition fade-in
        float sparkMasterAlpha = Math.min(1f, s / 0.14f) * surroundAlphaFactor;
        if (sparkMasterAlpha <= 0.01f) {
            return;
        }

        // Angular position & velocity ramp: starts slow, accelerates dramatically as s -> 1
        // Derivative d(spinAngle)/ds = 1.1 + 3.2 * 9.8 * s^2.2 -> over 25x speedup!
        float spinAngle = 1.1f * s + 9.8f * (float) Math.pow(s, 3.2);
        float speedFactor = 0.18f + 0.82f * (float) Math.pow(s, 1.85);

        for (int i = 0; i < RIM_SPARK_COUNT; i++) {
            float seed = (i * 0.6180339887f) % 1f;
            // Each spark has its own speed multiplier so they overtake each other organically
            float indivSpeed = 0.82f + 0.38f * seed;
            float theta = (float) (i * (Math.PI * 2.0 / RIM_SPARK_COUNT))
                    + spinAngle * indivSpeed
                    + 0.08f * (float) Math.sin(t * 9f + i);

            float cosT = (float) Math.cos(theta);
            float sinT = (float) Math.sin(theta);
            // Unit tangent vector in direction of counter-clockwise / clockwise spin (+theta)
            float tanX = -sinT;
            float tanY = cosT;

            // Slight radial jitter around the rim
            float radialJitter = ((i % 5) - 2) * dp1 * 1.4f * (0.5f + 0.5f * speedFactor);
            float rimR = windowR + radialJitter;

            // Spark head on the rim
            float headX = cx + cosT * rimR;
            float headY = cy + sinT * rimR;

            // Tangential spark streak trailing backward (-tan) and flaring slightly outward (+radial)
            // like Dr. Strange's spinning Sling Ring portal sparks!
            float streakLen = Math.max(dp1 * 10f, windowR * (0.07f + 0.24f * speedFactor) * (0.65f + 0.7f * seed));
            float outwardFlare = streakLen * (0.16f + 0.14f * seed);

            float tailX = headX - tanX * streakLen + cosT * outwardFlare;
            float tailY = headY - tanY * streakLen + sinT * outwardFlare;

            int sparkColor = (i % 3 == 0) ? neonAccent : ((i % 3 == 1) ? palette.highlight : palette.accentLight);
            float flicker = 0.72f + 0.28f * (float) Math.sin(t * 24f + i * 1.9f);
            float alpha = sparkMasterAlpha * flicker;

            // Outer neon/accent spark glow streak
            sparkGlowPaint.setColor(sparkColor);
            sparkGlowPaint.setStrokeWidth(Math.max(dp1 * 3.2f, dp1 * 5.0f * speedFactor));
            sparkGlowPaint.setAlpha(Math.round(alpha * 185f));
            canvas.drawLine(tailX, tailY, headX, headY, sparkGlowPaint);

            // Inner white-hot spark core streak
            float midTailX = headX - tanX * (streakLen * 0.62f) + cosT * (outwardFlare * 0.5f);
            float midTailY = headY - tanY * (streakLen * 0.62f) + sinT * (outwardFlare * 0.5f);
            sparkCorePaint.setColor(0xFFFFFFFF);
            sparkCorePaint.setStrokeWidth(Math.max(dp1 * 1.3f, dp1 * 2.1f * speedFactor));
            sparkCorePaint.setAlpha(Math.round(alpha * 250f));
            canvas.drawLine(midTailX, midTailY, headX, headY, sparkCorePaint);

            // Bright spark head node
            emberPaint.setColor(0xFFFFFFFF);
            emberPaint.setAlpha(Math.round(alpha * 255f));
            canvas.drawCircle(headX, headY, Math.max(dp1 * 1.6f, dp1 * 2.4f * speedFactor), emberPaint);

            // Detached flying ember particles spraying off the tangent trail
            if (i % 2 == 0) {
                float emberLife = (t * (2.6f + seed * 1.8f) + seed) % 1f;
                float emberDist = streakLen * (0.85f + 0.75f * emberLife);
                float emberOut = outwardFlare * (1.2f + 1.6f * emberLife);
                float ex = headX - tanX * emberDist + cosT * emberOut;
                float ey = headY - tanY * emberDist + sinT * emberOut;
                float emberAlpha = alpha * (1f - emberLife);

                emberPaint.setColor(sparkColor);
                emberPaint.setAlpha(Math.round(emberAlpha * 170f));
                canvas.drawCircle(ex, ey, Math.max(dp1 * 1.2f, dp1 * 2.2f * (1f - 0.5f * emberLife)), emberPaint);
            }
        }
    }
}
