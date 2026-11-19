package com.limelight.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES30;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.limelight.LimeLog;
import com.limelight.preferences.PreferenceConfiguration;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared OpenGL ES 3.0 TextureView rendering the PS Portal cosmic starfield and bidirectional
 * circular iris shader.
 *
 * - Menu background mode: renders full highp starfield into a 0.70x offscreen GL FBO and blits
 *   fullscreen with GL_LINEAR filtering (100% centered, identical star density, ~50% lower GPU
 *   fragment cost on Snapdragon 865+, pure vsync without manual sleeps).
 * - Game overlay mode: renders at full native resolution directly onto the transparent surface.
 * - Live frosted glass refraction: performs a tiny 64x112 FBO readback & box blur on the GL
 *   thread at ~15 FPS (0 ms UI-thread stall) for in-app cards, toolbars, and FocusCirclePortalView.
 */
public class PortalShaderView extends TextureView implements TextureView.SurfaceTextureListener {

    private static final int OVERLAY_MAX_SHORT_EDGE = 1080;
    private static final int BLUR_SHORT_EDGE = 64;
    private static final int BLUR_LONG_EDGE = 112;
    private static final long BLUR_INTERVAL_MS = 66L; // ~15 FPS off-UI-thread refraction updates

    private static final String VERTEX_SHADER_SRC =
            "#version 300 es\n" +
            "in vec2 a_pos;\n" +
            "void main() {\n" +
            "    gl_Position = vec4(a_pos, 0.0, 1.0);\n" +
            "}\n";

    private static final String BLIT_VERTEX_SHADER_SRC =
            "#version 300 es\n" +
            "in vec2 a_pos;\n" +
            "out vec2 v_uv;\n" +
            "void main() {\n" +
            "    v_uv = a_pos * 0.5 + 0.5;\n" +
            "    gl_Position = vec4(a_pos, 0.0, 1.0);\n" +
            "}\n";

    private static final String BLIT_FRAGMENT_SHADER_SRC =
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "in vec2 v_uv;\n" +
            "uniform sampler2D u_tex;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    fragColor = texture(u_tex, v_uv);\n" +
            "}\n";

    private static final String FRAGMENT_SHADER_SRC =
            "#version 300 es\n" +
            "precision highp float;\n" +
            "\n" +
            "out vec4 fragColor;\n" +
            "\n" +
            "uniform vec2  u_resolution;\n" +
            "uniform float u_time;\n" +
            "uniform float u_readyMix;\n" +
            "uniform float u_previewMix;\n" +
            "uniform float u_expandProgress;\n" +
            "uniform float u_portalScale;\n" +
            "uniform float u_glowIntensity;\n" +
            "uniform float u_particleBrightness;\n" +
            "uniform float u_sonarSpeed;\n" +
            "\n" +
            "const float PI  = 3.14159265359;\n" +
            "const float TAU = 6.28318530718;\n" +
            "\n" +
            "vec2 hash22(vec2 p) {\n" +
            "    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));\n" +
            "    p3 += dot(p3, p3.yzx + 33.33);\n" +
            "    return fract((p3.xx + p3.yz) * p3.zy);\n" +
            "}\n" +
            "\n" +
            "mat2 rot2d(float a) {\n" +
            "    float s = sin(a), c = cos(a);\n" +
            "    return mat2(c, -s, s, c);\n" +
            "}\n" +
            "\n" +
            "float easeInOutCubic(float t) {\n" +
            "    return t < 0.5 ? 4.0 * t * t * t : 1.0 - pow(-2.0 * t + 2.0, 3.0) * 0.5;\n" +
            "}\n" +
            "\n" +
            "vec3 sampleParticles(vec2 uv, float time) {\n" +
            "    vec3 col = vec3(0.0);\n" +
            "\n" +
            "    for (int layer = 0; layer < 3; layer++) {\n" +
            "        float fl = float(layer);\n" +
            "        float scale = 14.0 + fl * 9.0;\n" +
            "        float rotSpeed = (0.04 + fl * 0.018) * (layer == 1 ? -1.0 : 1.0);\n" +
            "\n" +
            "        vec2 p = rot2d(time * rotSpeed + fl * 1.7) * uv;\n" +
            "        p += vec2(sin(time * 0.15 + fl), cos(time * 0.12 + fl)) * 0.04;\n" +
            "        p *= scale;\n" +
            "\n" +
            "        vec2 id = floor(p);\n" +
            "        vec2 gv = fract(p) - 0.5;\n" +
            "\n" +
            "        for (int y = -1; y <= 1; y++) {\n" +
            "            for (int x = -1; x <= 1; x++) {\n" +
            "                vec2 offs = vec2(float(x), float(y));\n" +
            "                vec2 cellId = id + offs;\n" +
            "                vec2 rnd = hash22(cellId + fl * 37.0);\n" +
            "\n" +
            "                if (rnd.x < 0.55) continue;\n" +
            "\n" +
            "                vec2 drift = vec2(\n" +
            "                    sin(time * (0.4 + rnd.y * 0.5) + rnd.x * TAU),\n" +
            "                    cos(time * (0.35 + rnd.x * 0.5) + rnd.y * TAU)\n" +
            "                ) * 0.22;\n" +
            "\n" +
            "                vec2 pos = offs + (rnd - 0.5) * 0.55 + drift;\n" +
            "                vec2 diff = gv - pos;\n" +
            "                if (abs(diff.x) > 0.12 || abs(diff.y) > 0.12) continue;\n" +
            "                float dist = length(diff);\n" +
            "\n" +
            "                float radius = mix(0.035, 0.11, fract(rnd.x * 13.7));\n" +
            "                float bokeh = smoothstep(radius, radius * 0.15, dist);\n" +
            "                float twinkle = 0.55 + 0.45 * sin(time * (1.8 + rnd.y * 3.0) + rnd.x * TAU);\n" +
            "\n" +
            "                vec3 pCol = mix(vec3(0.369, 0.420, 1.0), vec3(0.780, 0.847, 1.0), rnd.y);\n" +
            "                col += pCol * bokeh * twinkle * (0.45 - fl * 0.08);\n" +
            "            }\n" +
            "        }\n" +
            "    }\n" +
            "    return col;\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    // Normalize by shortest screen dimension so iris & particles stay identical in portrait and landscape\n" +
            "    float minDim = min(u_resolution.x, u_resolution.y);\n" +
            "    vec2 uv = (gl_FragCoord.xy - 0.5 * u_resolution) / minDim;\n" +
            "    float d = length(uv);\n" +
            "    float angle = atan(uv.y, uv.x);\n" +
            "\n" +
            "    float halfDiag = length(0.5 * u_resolution / minDim);\n" +
            "    float maxRadius = halfDiag + 0.18;\n" +
            "    float baseRadius = 0.25 * clamp(u_portalScale, 0.5, 1.6);\n" +
            "    float expandEase = easeInOutCubic(clamp(u_expandProgress, 0.0, 1.0));\n" +
            "    float radius = mix(baseRadius, maxRadius, expandEase);\n" +
            "\n" +
            "    float aa = 2.0 / minDim;\n" +
            "    float outsideMask = smoothstep(radius - aa, radius + aa, d);\n" +
            "    float insideMask  = 1.0 - outsideMask;\n" +
            "\n" +
            "    // --- 1. Dark Matte Backdrop & Interior Starfield Base ---\n" +
            "    vec3 edgeNavy   = vec3(0.071, 0.078, 0.118); // #12141E\n" +
            "    vec3 centerGlow = vec3(0.110, 0.125, 0.220); // #1C2038\n" +
            "    float bgGrad    = exp(-d * 1.9);\n" +
            "    vec3 outerBg    = mix(edgeNavy, centerGlow, bgGrad);\n" +
            "    vec3 innerBg    = vec3(0.045, 0.055, 0.110); // Default app background base\n" +
            "\n" +
            "    float innerAlpha = 1.0 - clamp(u_previewMix, 0.0, 1.0);\n" +
            "    float bgAlpha    = mix(innerAlpha, 1.0, outsideMask);\n" +
            "    vec3  bgColor    = mix(innerBg, outerBg, outsideMask);\n" +
            "\n" +
            "    // --- 2. Interior Starfield (Also serves as full-screen app background when expand == 1 & preview == 0) ---\n" +
            "    float particleVis = insideMask * innerAlpha * smoothstep(radius, radius - 0.04, d);\n" +
            "    vec3 particles = vec3(0.0);\n" +
            "    if (particleVis > 0.001 && u_particleBrightness > 0.001) {\n" +
            "        particles = sampleParticles(uv, u_time) * particleVis * u_particleBrightness;\n" +
            "    }\n" +
            "\n" +
            "    // --- 3. Phase 1 Connecting Sonar Ripples Only ---\n" +
            "    vec3 sonar = vec3(0.0);\n" +
            "    float sonarVis = (1.0 - u_readyMix) * (1.0 - smoothstep(0.0, 0.45, u_expandProgress));\n" +
            "    if (sonarVis > 0.001 && d > radius) {\n" +
            "        for (int i = 0; i < 2; i++) {\n" +
            "            float phase = fract(u_time * 0.42 * u_sonarSpeed + float(i) * 0.5);\n" +
            "            float rSonar = radius + phase * 0.22;\n" +
            "            float ring = exp(-abs(d - rSonar) * 65.0);\n" +
            "            float fade = sin(phase * PI) * (1.0 - phase);\n" +
            "            sonar += vec3(0.32, 0.40, 1.0) * ring * fade * 0.35 * sonarVis;\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    // --- 4. Primary Portal Ring & Sweeping Rim Arcs ---\n" +
            "    float ringDist = abs(d - radius);\n" +
            "    float breathe  = 1.0 + 0.06 * sin(u_time * 2.6);\n" +
            "\n" +
            "    vec3 colIdle   = vec3(0.32, 0.38, 1.00);\n" +
            "    vec3 colReady  = vec3(0.40, 0.36, 1.00);\n" +
            "    vec3 colCharge = vec3(0.22, 0.71, 1.00);\n" +
            "    vec3 colCore   = vec3(0.95, 0.97, 1.00);\n" +
            "\n" +
            "    vec3 haloColor = mix(colIdle, colReady, u_readyMix);\n" +
            "    haloColor      = mix(haloColor, colCharge, u_previewMix);\n" +
            "\n" +
            "    float haloFalloff = mix(15.0, 30.0, u_previewMix);\n" +
            "    float idleGlow    = exp(-ringDist * 26.0) * 0.65;\n" +
            "    float readyHalo   = exp(-ringDist * haloFalloff) * mix(0.95, 0.75, u_previewMix) * breathe;\n" +
            "    float readyCore   = exp(-ringDist * 185.0) * 1.35 * breathe;\n" +
            "\n" +
            "    vec3 mainRing = mix(\n" +
            "        colIdle * idleGlow,\n" +
            "        haloColor * readyHalo + colCore * readyCore,\n" +
            "        u_readyMix\n" +
            "    );\n" +
            "\n" +
            "    float chargeVis = u_previewMix * (1.0 - smoothstep(0.6, 1.0, u_expandProgress));\n" +
            "    vec3 arcs = vec3(0.0);\n" +
            "    if (chargeVis > 0.001) {\n" +
            "        float a1 = 0.5 + 0.5 * sin(angle * 2.0 + u_time * 3.4);\n" +
            "        float a2 = 0.5 + 0.5 * sin(angle * 3.0 - u_time * 2.1 + 1.2);\n" +
            "        float arcMask = pow(a1, 5.0) + 0.6 * pow(a2, 6.0);\n" +
            "        float rimBand = exp(-ringDist * 75.0);\n" +
            "        arcs = mix(colCharge, colCore, 0.45) * rimBand * arcMask * 1.0 * chargeVis;\n" +
            "    }\n" +
            "\n" +
            "    // --- 5. Iris Open/Close Trailing Concentric Shockwaves ---\n" +
            "    vec3 shockwaves = vec3(0.0);\n" +
            "    if (u_expandProgress > 0.001) {\n" +
            "        float expandFade = sin(clamp(u_expandProgress, 0.0, 1.0) * PI);\n" +
            "        for (int i = 1; i <= 3; i++) {\n" +
            "            float fi = float(i);\n" +
            "            float lag = fi * 0.045 * (0.4 + 0.6 * u_expandProgress);\n" +
            "            float rShock = max(0.0, radius - lag);\n" +
            "            float w = exp(-abs(d - rShock) * (85.0 - fi * 12.0));\n" +
            "            vec3 sCol = mix(vec3(0.30, 0.65, 1.0), vec3(0.43, 0.48, 1.0), fi / 3.0);\n" +
            "            shockwaves += sCol * w * (0.65 / fi) * expandFade;\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    float globalRingFade = 1.0 - smoothstep(0.82, 1.0, u_expandProgress);\n" +
            "    vec3 ringGlow = (sonar + mainRing + arcs + shockwaves) * u_glowIntensity * globalRingFade;\n" +
            "\n" +
            "    fragColor = vec4(bgColor * bgAlpha + particles + ringGlow, bgAlpha);\n" +
            "}\n";

    private static final float[] FULLSCREEN_QUAD = {
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
            -1f,  1f,
             1f, -1f,
             1f,  1f
    };

    /**
     * Shared monotonic clock across all screens so the starfield never jumps or resets
     * between PcView, AppView, Settings, and Game.
     */
    public static float getSharedTimeSec() {
        return (SystemClock.uptimeMillis() % 1_000_000L) / 1000.0f;
    }

    private volatile float uReadyMix = 0f;
    private volatile float uPreviewMix = 0f;
    private volatile float uExpandProgress = 1f;
    private volatile float uPortalScale = 1f;
    private volatile float uGlowIntensity = 1f;
    private volatile float uParticleBrightness = 1f;
    private volatile float uSonarSpeed = 1f;
    private volatile int blurRadius = 4;
    private volatile int bgMaxShortEdge = 720;
    private volatile boolean backgroundMode = false;
    private volatile boolean renderingDesired = false;

    private PortalGlThread renderThread;
    private SurfaceTexture activeSurfaceTexture;
    private int activeWidth = 0;
    private int activeHeight = 0;

    // Off-UI-thread blurred refraction bitmap (~64x112 @ ~15 FPS)
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<WeakReference<Drawable>> frostedDrawables = new CopyOnWriteArrayList<>();
    private volatile Bitmap publishedBlurBitmap;

    private final Runnable invalidateFrostedRunnable = () -> {
        if (frostedDrawables.isEmpty()) {
            return;
        }
        Iterator<WeakReference<Drawable>> it = frostedDrawables.iterator();
        while (it.hasNext()) {
            Drawable d = it.next().get();
            if (d == null) {
                frostedDrawables.remove(it);
            } else {
                d.invalidateSelf();
            }
        }
    };

    public PortalShaderView(@NonNull Context context) {
        super(context);
        init();
    }

    public PortalShaderView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PortalShaderView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOpaque(false);
        setSurfaceTextureListener(this);
        refreshFromPrefs();
    }

    public void refreshFromPrefs() {
        PreferenceConfiguration cfg = PreferenceConfiguration.readPreferences(getContext());
        uPortalScale = Math.max(0.6f, Math.min(1.5f, cfg.portalWindowScalePercent / 100f));
        uGlowIntensity = Math.max(0.2f, Math.min(2.0f, cfg.portalGlowIntensityPercent / 100f));
        uParticleBrightness = Math.max(0.0f, Math.min(2.0f, cfg.portalParticleBrightnessPercent / 100f));
        uSonarSpeed = Math.max(0.2f, Math.min(2.5f, cfg.portalSonarSpeedPercent / 100f));
        int blurPct = Math.max(0, Math.min(200, cfg.blurAmountPercent));
        blurRadius = blurPct == 0 ? 0 : Math.max(1, Math.min(10, Math.round(4f * (blurPct / 100f))));
        bgMaxShortEdge = Math.max(0, cfg.bgQualityMaxShortEdge);
    }

    /**
     * Configures this view to render the full-screen cosmic starfield background
     * via a 0.70x offscreen GL FBO + fullscreen linear blit.
     */
    public void configureStarfieldBackgroundMode() {
        backgroundMode = true;
        uReadyMix = 0f;
        uPreviewMix = 0f;
        uExpandProgress = 1f;
        refreshFromPrefs();
    }

    public void setUniforms(float readyMix, float previewMix, float expandProgress) {
        this.uReadyMix = readyMix;
        this.uPreviewMix = previewMix;
        this.uExpandProgress = expandProgress;
    }

    public void setReadyMix(float readyMix) {
        this.uReadyMix = readyMix;
    }

    public void setPreviewMix(float previewMix) {
        this.uPreviewMix = previewMix;
    }

    public void setExpandProgress(float expandProgress) {
        this.uExpandProgress = expandProgress;
    }

    public float getReadyMix() {
        return uReadyMix;
    }

    public float getPreviewMix() {
        return uPreviewMix;
    }

    public float getExpandProgress() {
        return uExpandProgress;
    }

    public void registerFrostedDrawable(@NonNull Drawable drawable) {
        frostedDrawables.add(new WeakReference<>(drawable));
    }

    @Nullable
    public Bitmap getBlurredBitmap() {
        return publishedBlurBitmap;
    }

    public void startRendering() {
        renderingDesired = true;
        if (renderThread != null && renderThread.isRunning()) {
            return;
        }
        SurfaceTexture st = activeSurfaceTexture;
        if (st == null && isAvailable()) {
            st = getSurfaceTexture();
            activeSurfaceTexture = st;
            activeWidth = getWidth();
            activeHeight = getHeight();
        }
        if (st != null && activeWidth > 0 && activeHeight > 0) {
            renderThread = new PortalGlThread(st, activeWidth, activeHeight);
            renderThread.start();
        }
    }

    public void stopRendering() {
        renderingDesired = false;
        PortalGlThread t = renderThread;
        renderThread = null;
        if (t != null) {
            t.requestStop();
            try {
                t.join(450L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        activeSurfaceTexture = surface;
        activeWidth = width;
        activeHeight = height;
        if (renderingDesired) {
            PortalGlThread old = renderThread;
            renderThread = null;
            if (old != null) {
                old.requestStop();
                try {
                    old.join(450L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            renderThread = new PortalGlThread(surface, width, height);
            renderThread.start();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        activeSurfaceTexture = surface;
        activeWidth = width;
        activeHeight = height;
        PortalGlThread t = renderThread;
        if (t != null) {
            t.updateSize(width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        PortalGlThread t = renderThread;
        renderThread = null;
        if (t != null) {
            t.requestStop();
            try {
                t.join(450L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        activeSurfaceTexture = null;
        activeWidth = 0;
        activeHeight = 0;
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
        // No-op
    }

    @Override
    protected void onDetachedFromWindow() {
        stopRendering();
        super.onDetachedFromWindow();
    }

    private static void boxBlurHorizontal(int[] src, int[] dst, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int rSum = 0, gSum = 0, bSum = 0;
            for (int i = -r; i <= r; i++) {
                int c = src[row + Math.max(0, Math.min(w - 1, i))];
                rSum += (c >> 16) & 0xFF;
                gSum += (c >> 8) & 0xFF;
                bSum += c & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                dst[row + x] = 0xFF000000 | ((rSum / div) << 16) | ((gSum / div) << 8) | (bSum / div);
                int outC = src[row + Math.max(0, x - r)];
                int inC = src[row + Math.min(w - 1, x + r + 1)];
                rSum += ((inC >> 16) & 0xFF) - ((outC >> 16) & 0xFF);
                gSum += ((inC >> 8) & 0xFF) - ((outC >> 8) & 0xFF);
                bSum += (inC & 0xFF) - (outC & 0xFF);
            }
        }
    }

    private static void boxBlurVertical(int[] src, int[] dst, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int x = 0; x < w; x++) {
            int rSum = 0, gSum = 0, bSum = 0;
            for (int i = -r; i <= r; i++) {
                int c = src[Math.max(0, Math.min(h - 1, i)) * w + x];
                rSum += (c >> 16) & 0xFF;
                gSum += (c >> 8) & 0xFF;
                bSum += c & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                dst[y * w + x] = 0xFF000000 | ((rSum / div) << 16) | ((gSum / div) << 8) | (bSum / div);
                int outC = src[Math.max(0, y - r) * w + x];
                int inC = src[Math.min(h - 1, y + r + 1) * w + x];
                rSum += ((inC >> 16) & 0xFF) - ((outC >> 16) & 0xFF);
                gSum += ((inC >> 8) & 0xFF) - ((outC >> 8) & 0xFF);
                bSum += (inC & 0xFF) - (outC & 0xFF);
            }
        }
    }

    private final class PortalGlThread extends Thread {
        private static final int EGL_OPENGL_ES3_BIT_KHR = 0x0040;

        private final SurfaceTexture surfaceTexture;
        private volatile int width;
        private volatile int height;
        private volatile boolean running = true;

        private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
        private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
        private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;

        // Primary portal shader program
        private int program = 0;
        private int aPosLoc = -1;
        private int uResolutionLoc = -1;
        private int uTimeLoc = -1;
        private int uReadyMixLoc = -1;
        private int uPreviewMixLoc = -1;
        private int uExpandProgressLoc = -1;
        private int uPortalScaleLoc = -1;
        private int uGlowIntensityLoc = -1;
        private int uParticleBrightnessLoc = -1;
        private int uSonarSpeedLoc = -1;

        // Fullscreen linear blit program for 0.70x background FBO
        private int blitProgram = 0;
        private int blitPosLoc = -1;
        private int blitTexLoc = -1;

        // Offscreen scaled background FBO
        private int bgFbo = 0;
        private int bgFboTex = 0;
        private int bgFboW = 0;
        private int bgFboH = 0;

        // Tiny offscreen blur readback FBO (~64x112)
        private int blurFbo = 0;
        private int blurFboTex = 0;
        private int blurW = 0;
        private int blurH = 0;
        private ByteBuffer readbackBuf;
        private byte[] readbackBytes;
        private int[] blurPixelsA;
        private int[] blurPixelsB;
        private Bitmap blurBitmapWorkA;
        private Bitmap blurBitmapWorkB;
        private boolean useWorkA = true;
        private long lastBlurUpdateMs = 0L;

        private FloatBuffer quadBuffer;

        PortalGlThread(SurfaceTexture surfaceTexture, int width, int height) {
            super("PortalShaderGlThread");
            this.surfaceTexture = surfaceTexture;
            this.width = width;
            this.height = height;
        }

        boolean isRunning() {
            return running && isAlive();
        }

        void updateSize(int w, int h) {
            this.width = w;
            this.height = h;
        }

        void requestStop() {
            running = false;
        }

        @Override
        public void run() {
            if (!initEgl()) {
                releaseEgl();
                return;
            }
            if (!initGl()) {
                releaseGl();
                releaseEgl();
                return;
            }

            while (running) {
                int w = width;
                int h = height;
                if (w <= 0 || h <= 0) {
                    SystemClock.sleep(16L);
                    continue;
                }

                float sharedTime = getSharedTimeSec();

                int shortEdge = Math.min(w, h);
                if (backgroundMode) {
                    int cap = bgMaxShortEdge;
                    float scale = (cap > 0 && shortEdge > cap) ? (cap / (float) shortEdge) : 1.0f;
                    int fboW = Math.max(1, Math.round(w * scale));
                    int fboH = Math.max(1, Math.round(h * scale));
                    ensureBgFbo(fboW, fboH);

                    // 1. Render full highp starfield into capped offscreen FBO (centered, full aspect)
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bgFbo);
                    GLES30.glViewport(0, 0, fboW, fboH);
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
                    drawPortalShader(fboW, fboH, sharedTime);

                    // 2. Blit FBO fullscreen with GL_LINEAR filtering onto native EGL surface
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
                    GLES30.glViewport(0, 0, w, h);
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
                    drawBlitTexture(bgFboTex);

                    // 3. Periodically update tiny blurred refraction bitmap on GL thread (~15 FPS)
                    long nowMs = SystemClock.uptimeMillis();
                    if (!frostedDrawables.isEmpty() && (nowMs - lastBlurUpdateMs) >= BLUR_INTERVAL_MS) {
                        lastBlurUpdateMs = nowMs;
                        updateBlurBitmapOnGlThread(w >= h);
                    }
                } else if (shortEdge > OVERLAY_MAX_SHORT_EDGE) {
                    // Game overlay mode on high-res tablets (>1080p short edge): cap to 1080p FBO
                    float scale = OVERLAY_MAX_SHORT_EDGE / (float) shortEdge;
                    int fboW = Math.max(1, Math.round(w * scale));
                    int fboH = Math.max(1, Math.round(h * scale));
                    ensureBgFbo(fboW, fboH);

                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bgFbo);
                    GLES30.glViewport(0, 0, fboW, fboH);
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
                    drawPortalShader(fboW, fboH, sharedTime);

                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
                    GLES30.glViewport(0, 0, w, h);
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
                    drawBlitTexture(bgFboTex);
                } else {
                    // Game overlay mode on <=1080p displays: direct native render with zero FBO overhead
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
                    GLES30.glViewport(0, 0, w, h);
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
                    drawPortalShader(w, h, sharedTime);
                }

                if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
                    int err = EGL14.eglGetError();
                    if (err == EGL14.EGL_BAD_SURFACE || err == EGL14.EGL_BAD_NATIVE_WINDOW) {
                        break;
                    }
                }
            }

            releaseGl();
            releaseEgl();
        }

        private void drawPortalShader(int w, int h, float sharedTime) {
            GLES30.glUseProgram(program);
            GLES30.glUniform2f(uResolutionLoc, (float) w, (float) h);
            GLES30.glUniform1f(uTimeLoc, sharedTime);
            GLES30.glUniform1f(uReadyMixLoc, uReadyMix);
            GLES30.glUniform1f(uPreviewMixLoc, uPreviewMix);
            GLES30.glUniform1f(uExpandProgressLoc, uExpandProgress);
            GLES30.glUniform1f(uPortalScaleLoc, uPortalScale);
            GLES30.glUniform1f(uGlowIntensityLoc, uGlowIntensity);
            GLES30.glUniform1f(uParticleBrightnessLoc, uParticleBrightness);
            GLES30.glUniform1f(uSonarSpeedLoc, uSonarSpeed);

            quadBuffer.position(0);
            GLES30.glEnableVertexAttribArray(aPosLoc);
            GLES30.glVertexAttribPointer(aPosLoc, 2, GLES30.GL_FLOAT, false, 0, quadBuffer);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6);
            GLES30.glDisableVertexAttribArray(aPosLoc);
        }

        private void drawBlitTexture(int texId) {
            GLES30.glUseProgram(blitProgram);
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId);
            GLES30.glUniform1i(blitTexLoc, 0);

            quadBuffer.position(0);
            GLES30.glEnableVertexAttribArray(blitPosLoc);
            GLES30.glVertexAttribPointer(blitPosLoc, 2, GLES30.GL_FLOAT, false, 0, quadBuffer);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6);
            GLES30.glDisableVertexAttribArray(blitPosLoc);
        }

        private void ensureBgFbo(int w, int h) {
            if (bgFbo != 0 && bgFboW == w && bgFboH == h) {
                return;
            }
            releaseBgFbo();
            bgFboW = w;
            bgFboH = h;

            int[] tex = new int[1];
            GLES30.glGenTextures(1, tex, 0);
            bgFboTex = tex[0];
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bgFboTex);
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0,
                    GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);

            int[] fbo = new int[1];
            GLES30.glGenFramebuffers(1, fbo, 0);
            bgFbo = fbo[0];
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bgFbo);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                    GLES30.GL_TEXTURE_2D, bgFboTex, 0);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        }

        private void ensureBlurFbo(int w, int h) {
            if (blurFbo != 0 && blurW == w && blurH == h) {
                return;
            }
            releaseBlurFbo();
            blurW = w;
            blurH = h;

            int[] tex = new int[1];
            GLES30.glGenTextures(1, tex, 0);
            blurFboTex = tex[0];
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, blurFboTex);
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0,
                    GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);

            int[] fbo = new int[1];
            GLES30.glGenFramebuffers(1, fbo, 0);
            blurFbo = fbo[0];
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, blurFbo);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                    GLES30.GL_TEXTURE_2D, blurFboTex, 0);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);

            readbackBuf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
            readbackBytes = new byte[w * h * 4];
            blurPixelsA = new int[w * h];
            blurPixelsB = new int[w * h];
            blurBitmapWorkA = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            blurBitmapWorkB = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        }

        private void updateBlurBitmapOnGlThread(boolean landscape) {
            int tw = landscape ? BLUR_LONG_EDGE : BLUR_SHORT_EDGE;
            int th = landscape ? BLUR_SHORT_EDGE : BLUR_LONG_EDGE;
            ensureBlurFbo(tw, th);

            // Downsample bgFboTex into tiny 64x112 FBO and read back off the UI thread
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, blurFbo);
            GLES30.glViewport(0, 0, tw, th);
            drawBlitTexture(bgFboTex);

            readbackBuf.position(0);
            GLES30.glReadPixels(0, 0, tw, th, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, readbackBuf);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);

            readbackBuf.position(0);
            readbackBuf.get(readbackBytes);

            // Flip Y (GL bottom-left -> Android top-left) & pack RGBA -> ARGB
            for (int y = 0; y < th; y++) {
                int srcRow = (th - 1 - y) * tw * 4;
                int dstRow = y * tw;
                for (int x = 0; x < tw; x++) {
                    int idx = srcRow + x * 4;
                    int r = readbackBytes[idx] & 0xFF;
                    int g = readbackBytes[idx + 1] & 0xFF;
                    int b = readbackBytes[idx + 2] & 0xFF;
                    blurPixelsA[dstRow + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            }

            int r = blurRadius;
            if (r > 0) {
                for (int pass = 0; pass < 2; pass++) {
                    boxBlurHorizontal(blurPixelsA, blurPixelsB, tw, th, r);
                    boxBlurVertical(blurPixelsB, blurPixelsA, tw, th, r);
                }
            }

            Bitmap target = useWorkA ? blurBitmapWorkA : blurBitmapWorkB;
            useWorkA = !useWorkA;
            target.setPixels(blurPixelsA, 0, tw, 0, 0, tw, th);
            publishedBlurBitmap = target;
            mainHandler.post(invalidateFrostedRunnable);
        }

        private void releaseBgFbo() {
            if (bgFbo != 0) {
                GLES30.glDeleteFramebuffers(1, new int[]{bgFbo}, 0);
                bgFbo = 0;
            }
            if (bgFboTex != 0) {
                GLES30.glDeleteTextures(1, new int[]{bgFboTex}, 0);
                bgFboTex = 0;
            }
            bgFboW = 0;
            bgFboH = 0;
        }

        private void releaseBlurFbo() {
            if (blurFbo != 0) {
                GLES30.glDeleteFramebuffers(1, new int[]{blurFbo}, 0);
                blurFbo = 0;
            }
            if (blurFboTex != 0) {
                GLES30.glDeleteTextures(1, new int[]{blurFboTex}, 0);
                blurFboTex = 0;
            }
            blurW = 0;
            blurH = 0;
        }

        private boolean initEgl() {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                return false;
            }
            int[] version = new int[2];
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                return false;
            }

            EGLConfig config = chooseConfig(EGL_OPENGL_ES3_BIT_KHR);
            if (config == null) {
                config = chooseConfig(EGL14.EGL_OPENGL_ES2_BIT);
            }
            if (config == null) {
                return false;
            }

            int[] ctxAttribs = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                    EGL14.EGL_NONE
            };
            eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0);
            if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT) {
                return false;
            }

            int[] surfaceAttribs = { EGL14.EGL_NONE };
            try {
                eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, surfaceTexture, surfaceAttribs, 0);
            } catch (IllegalArgumentException e) {
                return false;
            }
            if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
                return false;
            }

            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                return false;
            }

            EGL14.eglSwapInterval(eglDisplay, 1);
            return true;
        }

        @Nullable
        private EGLConfig chooseConfig(int renderableType) {
            int[] attribs = {
                    EGL14.EGL_RENDERABLE_TYPE, renderableType,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_DEPTH_SIZE, 0,
                    EGL14.EGL_STENCIL_SIZE, 0,
                    EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, numConfigs, 0)
                    || numConfigs[0] <= 0) {
                return null;
            }
            return configs[0];
        }

        private boolean initGl() {
            program = linkProgram(VERTEX_SHADER_SRC, FRAGMENT_SHADER_SRC);
            if (program == 0) {
                return false;
            }
            blitProgram = linkProgram(BLIT_VERTEX_SHADER_SRC, BLIT_FRAGMENT_SHADER_SRC);
            if (blitProgram == 0) {
                return false;
            }

            aPosLoc = GLES30.glGetAttribLocation(program, "a_pos");
            uResolutionLoc = GLES30.glGetUniformLocation(program, "u_resolution");
            uTimeLoc = GLES30.glGetUniformLocation(program, "u_time");
            uReadyMixLoc = GLES30.glGetUniformLocation(program, "u_readyMix");
            uPreviewMixLoc = GLES30.glGetUniformLocation(program, "u_previewMix");
            uExpandProgressLoc = GLES30.glGetUniformLocation(program, "u_expandProgress");
            uPortalScaleLoc = GLES30.glGetUniformLocation(program, "u_portalScale");
            uGlowIntensityLoc = GLES30.glGetUniformLocation(program, "u_glowIntensity");
            uParticleBrightnessLoc = GLES30.glGetUniformLocation(program, "u_particleBrightness");
            uSonarSpeedLoc = GLES30.glGetUniformLocation(program, "u_sonarSpeed");

            blitPosLoc = GLES30.glGetAttribLocation(blitProgram, "a_pos");
            blitTexLoc = GLES30.glGetUniformLocation(blitProgram, "u_tex");

            quadBuffer = ByteBuffer.allocateDirect(FULLSCREEN_QUAD.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            quadBuffer.put(FULLSCREEN_QUAD).position(0);

            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA);
            GLES30.glClearColor(0f, 0f, 0f, 0f);
            return true;
        }

        private int linkProgram(String vsSrc, String fsSrc) {
            int vs = compileShader(GLES30.GL_VERTEX_SHADER, vsSrc);
            if (vs == 0) {
                return 0;
            }
            int fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fsSrc);
            if (fs == 0) {
                GLES30.glDeleteShader(vs);
                return 0;
            }
            int prog = GLES30.glCreateProgram();
            GLES30.glAttachShader(prog, vs);
            GLES30.glAttachShader(prog, fs);
            GLES30.glLinkProgram(prog);
            GLES30.glDeleteShader(vs);
            GLES30.glDeleteShader(fs);

            int[] linked = new int[1];
            GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, linked, 0);
            if (linked[0] == 0) {
                LimeLog.severe("PortalShaderView program link failed: " + GLES30.glGetProgramInfoLog(prog));
                GLES30.glDeleteProgram(prog);
                return 0;
            }
            return prog;
        }

        private int compileShader(int type, String src) {
            int shader = GLES30.glCreateShader(type);
            GLES30.glShaderSource(shader, src);
            GLES30.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                LimeLog.severe("PortalShaderView shader compile failed: " + GLES30.glGetShaderInfoLog(shader));
                GLES30.glDeleteShader(shader);
                return 0;
            }
            return shader;
        }

        private void releaseGl() {
            releaseBgFbo();
            releaseBlurFbo();
            if (program != 0) {
                GLES30.glDeleteProgram(program);
                program = 0;
            }
            if (blitProgram != 0) {
                GLES30.glDeleteProgram(blitProgram);
                blitProgram = 0;
            }
        }

        private void releaseEgl() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface);
                    eglSurface = EGL14.EGL_NO_SURFACE;
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext);
                    eglContext = EGL14.EGL_NO_CONTEXT;
                }
                EGL14.eglTerminate(eglDisplay);
                eglDisplay = EGL14.EGL_NO_DISPLAY;
            }
        }
    }
}
