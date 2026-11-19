package com.limelight.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES30;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.utils.UiHelper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Full-screen 4-phase PlayStation Portal–style OpenGL ES 3.0 connecting & disconnecting overlay.
 *
 * Uses a transparent hardware-accelerated TextureView backed by a dedicated EGL14 GLES 3.0 render
 * thread with premultiplied alpha compositing so the circular cutout cleanly reveals the live
 * SurfaceView video stream underneath on all devices without SurfaceView Z-order issues.
 *
 * Also supports a faster reversed disconnecting sequence when leaving a stream.
 */
public class PortalConnectionOverlay extends FrameLayout implements TextureView.SurfaceTextureListener {

    private static final String VERTEX_SHADER_SRC =
            "#version 300 es\n" +
            "in vec2 a_pos;\n" +
            "void main() {\n" +
            "    gl_Position = vec4(a_pos, 0.0, 1.0);\n" +
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
            "uniform float u_swirlSpeed;\n" +
            "uniform float u_portalScale;\n" +
            "uniform float u_glowIntensity;\n" +
            "uniform float u_particleBrightness;\n" +
            "uniform float u_sonarSpeed;\n" +
            "uniform float u_cutoutOnly;\n" +
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
            "                float dist = length(gv - pos);\n" +
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
            "vec3 samplePortalSwirl(float d, float angle, float radius, float time, float swirlSpeed) {\n" +
            "    float radialOut = max(0.0, d - radius);\n" +
            "    float swirlAngle = angle - radialOut * 11.5 + time * 3.2 * swirlSpeed;\n" +
            "    float wisp1 = pow(0.5 + 0.5 * sin(swirlAngle * 14.0 + sin(swirlAngle * 5.0)), 4.0);\n" +
            "    float wisp2 = pow(0.5 + 0.5 * sin(swirlAngle * 23.0 + time * 4.5 * swirlSpeed), 5.0);\n" +
            "    float rimFade = exp(-radialOut * 24.0) * smoothstep(radius - 0.008, radius + 0.006, d);\n" +
            "    return vec3(0.22, 0.68, 1.0) * (wisp1 * 0.45 + wisp2 * 0.35) * rimFade;\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 uv = (gl_FragCoord.xy - 0.5 * u_resolution) / u_resolution.y;\n" +
            "    float d = length(uv);\n" +
            "    float angle = atan(uv.y, uv.x);\n" +
            "\n" +
            "    float halfDiag = length(0.5 * u_resolution / u_resolution.y);\n" +
            "    float maxRadius = halfDiag + 0.18;\n" +
            "    float baseRadius = 0.25 * clamp(u_portalScale, 0.5, 1.6);\n" +
            "    float expandEase = easeInOutCubic(clamp(u_expandProgress, 0.0, 1.0));\n" +
            "    float radius = mix(baseRadius, maxRadius, expandEase);\n" +
            "\n" +
            "    float aa = 2.0 / u_resolution.y;\n" +
            "    float outsideMask = smoothstep(radius - aa, radius + aa, d);\n" +
            "    float insideMask  = 1.0 - outsideMask;\n" +
            "\n" +
            "    vec3 edgeNavy   = vec3(0.071, 0.078, 0.118);\n" +
            "    vec3 centerGlow = vec3(0.110, 0.125, 0.220);\n" +
            "    float bgGrad    = exp(-d * 1.9);\n" +
            "    vec3 outerBg    = mix(edgeNavy, centerGlow, bgGrad);\n" +
            "    vec3 innerBg    = vec3(0.045, 0.055, 0.110);\n" +
            "\n" +
            "    float smoothPreview = easeInOutCubic(clamp(u_previewMix, 0.0, 1.0));\n" +
            "    float innerAlpha = (1.0 - smoothPreview) * (1.0 - clamp(u_cutoutOnly, 0.0, 1.0));\n" +
            "    float bgAlpha    = mix(innerAlpha, 1.0, outsideMask);\n" +
            "    vec3  bgColor    = mix(innerBg, outerBg, outsideMask);\n" +
            "\n" +
            "    float particleVis = insideMask * innerAlpha * smoothstep(radius, radius - 0.04, d);\n" +
            "    vec3 particles = vec3(0.0);\n" +
            "    if (particleVis > 0.001 && u_particleBrightness > 0.001) {\n" +
            "        particles = sampleParticles(uv, u_time) * particleVis * u_particleBrightness;\n" +
            "    }\n" +
            "\n" +
            "    vec3 sonar = vec3(0.0);\n" +
            "    float sonarVis = (1.0 - u_readyMix) * (1.0 - u_expandProgress) * (1.0 - clamp(u_cutoutOnly, 0.0, 1.0));\n" +
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
            "    float ringDist = abs(d - radius);\n" +
            "    float breathe  = 1.0 + 0.06 * sin(u_time * 2.6);\n" +
            "\n" +
            "    vec3 colIdle   = vec3(0.32, 0.38, 1.00);\n" +
            "    vec3 colReady  = vec3(0.40, 0.36, 1.00);\n" +
            "    vec3 colCharge = vec3(0.22, 0.71, 1.00);\n" +
            "    vec3 colCore   = vec3(0.95, 0.97, 1.00);\n" +
            "\n" +
            "    vec3 haloColor = mix(colIdle, colReady, u_readyMix);\n" +
            "    haloColor      = mix(haloColor, colCharge, smoothPreview);\n" +
            "\n" +
            "    float haloFalloff = mix(15.0, 30.0, smoothPreview);\n" +
            "    float idleGlow    = exp(-ringDist * 26.0) * 0.65;\n" +
            "    float readyHalo   = exp(-ringDist * haloFalloff) * mix(0.95, 0.75, smoothPreview) * breathe;\n" +
            "    float readyCore   = exp(-ringDist * 185.0) * 1.35 * breathe;\n" +
            "\n" +
            "    vec3 mainRing = mix(\n" +
            "        colIdle * idleGlow,\n" +
            "        haloColor * readyHalo + colCore * readyCore,\n" +
            "        u_readyMix\n" +
            "    );\n" +
            "\n" +
            "    float chargeVis = smoothPreview * (1.0 - smoothstep(0.6, 1.0, u_expandProgress));\n" +
            "    vec3 arcs = vec3(0.0);\n" +
            "    if (chargeVis > 0.001) {\n" +
            "        float a1 = 0.5 + 0.5 * sin(angle * 2.0 + u_time * 3.4 * u_swirlSpeed);\n" +
            "        float a2 = 0.5 + 0.5 * sin(angle * 3.0 - u_time * 2.1 * u_swirlSpeed + 1.2);\n" +
            "        float arcMask = pow(a1, 5.0) + 0.6 * pow(a2, 6.0);\n" +
            "        float rimBand = exp(-ringDist * 75.0);\n" +
            "        arcs = mix(colCharge, colCore, 0.45) * rimBand * arcMask * 1.0 * chargeVis;\n" +
            "    }\n" +
            "\n" +
            "    vec3 swirl = vec3(0.0);\n" +
            "    float swirlVis = smoothstep(0.20, 1.0, smoothPreview) * (1.0 - smoothstep(0.35, 0.90, u_expandProgress));\n" +
            "    if (swirlVis > 0.001 && d > radius - 0.015 && d < radius + 0.22) {\n" +
            "        swirl = samplePortalSwirl(d, angle, radius, u_time, u_swirlSpeed) * swirlVis;\n" +
            "    }\n" +
            "\n" +
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
            "    vec3 ringEnergy = (sonar + mainRing + arcs + swirl + shockwaves) * u_glowIntensity;\n" +
            "    vec3 additiveGlow = (particles + ringEnergy) * globalRingFade;\n" +
            "\n" +
            "    fragColor = vec4(bgColor * bgAlpha + additiveGlow, bgAlpha);\n" +
            "}\n";

    private static final float[] FULLSCREEN_QUAD = {
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
            -1f,  1f,
             1f, -1f,
             1f,  1f
    };

    private static final long PHASE3_HUD_FADE_MS = 350L;
    private static final long FIRST_FRAME_FALLBACK_MS = 700L;

    // Configurable durations loaded from UI Settings -> Portal Animation
    private long phase2ReadyMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_READY_MS;
    private long phase3PreviewFadeMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_PREVIEW_FADE_MS;
    private long phase3HoldMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_PREVIEW_HOLD_MS;
    private long phase4ExpandMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_EXPAND_MS;
    private long disconnectIrisCloseMs = Math.round(com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_DISCONNECT_MS * 0.60f);
    private long disconnectEclipseCloseMs = Math.round(com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_DISCONNECT_MS * 0.40f);
    private long launchCloseMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_LAUNCH_CLOSE_MS;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextureView glTextureView;
    private LinearLayout topHudContainer;
    private TextView statusTitleView;
    private TextView stageSubtitleView;
    private TextView cancelButton;

    private PortalGlThread renderThread;
    private SurfaceTexture activeSurfaceTexture;
    private int activeSurfaceWidth = 0;
    private int activeSurfaceHeight = 0;

    // Shader uniforms updated from main thread, read on GL thread
    private volatile float uReadyMix = 0f;
    private volatile float uPreviewMix = 0f;
    private volatile float uExpandProgress = 0f;
    private volatile float uSwirlSpeed = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_SWIRL_SPEED / 100f;
    private volatile float uPortalScale = 1.0f;
    private volatile float uGlowIntensity = 1.0f;
    private volatile float uParticleBrightness = 1.0f;
    private volatile float uSonarSpeed = 1.0f;
    private volatile float uCutoutOnly = 0f;

    private void loadTimingPreferences() {
        com.limelight.preferences.PreferenceConfiguration cfg =
                com.limelight.preferences.PreferenceConfiguration.readPreferences(getContext());
        phase2ReadyMs = Math.max(100L, cfg.portalReadyMs);
        phase3PreviewFadeMs = Math.max(100L, cfg.portalPreviewFadeMs);
        phase3HoldMs = Math.max(0L, cfg.portalPreviewHoldMs);
        phase4ExpandMs = Math.max(200L, cfg.portalExpandMs);
        uSwirlSpeed = Math.max(0.05f, cfg.portalSwirlSpeedPercent / 100f);
        uPortalScale = Math.max(0.6f, Math.min(1.5f, cfg.portalWindowScalePercent / 100f));
        uGlowIntensity = Math.max(0.2f, Math.min(2.0f, cfg.portalGlowIntensityPercent / 100f));
        uParticleBrightness = Math.max(0.0f, Math.min(2.0f, cfg.portalParticleBrightnessPercent / 100f));
        uSonarSpeed = Math.max(0.2f, Math.min(2.5f, cfg.portalSonarSpeedPercent / 100f));
        launchCloseMs = Math.max(150L, cfg.portalLaunchCloseMs);
        long totalDisconnect = Math.max(200L, cfg.portalDisconnectMs);
        disconnectIrisCloseMs = Math.max(100L, Math.round(totalDisconnect * 0.60f));
        disconnectEclipseCloseMs = Math.max(100L, totalDisconnect - disconnectIrisCloseMs);
    }

    private boolean active = false;
    private boolean disconnecting = false;
    private boolean phase2Started = false;
    private boolean phase2Completed = false;
    private boolean firstFrameReceived = false;
    private boolean phase3Started = false;

    private ValueAnimator activeAnimator;
    private Runnable onCancelAction;
    private Runnable onRevealCompleteAction;

    private final Runnable firstFrameFallbackRunnable = () -> {
        if (active && !disconnecting && phase2Completed && !phase3Started) {
            startPhase3();
        }
    };

    private final Runnable phase3HoldRunnable = () -> {
        if (active && !disconnecting) {
            startPhase4();
        }
    };

    public PortalConnectionOverlay(@NonNull Context context) {
        super(context);
        init(context);
    }

    public PortalConnectionOverlay(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PortalConnectionOverlay(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setBackgroundColor(Color.TRANSPARENT);
        setVisibility(GONE);
        setClickable(false);
        setFocusable(false);

        glTextureView = new TextureView(context);
        glTextureView.setOpaque(false);
        glTextureView.setSurfaceTextureListener(this);
        addView(glTextureView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Minimal top-left HUD
        topHudContainer = new LinearLayout(context);
        topHudContainer.setOrientation(LinearLayout.VERTICAL);
        topHudContainer.setGravity(Gravity.START);

        statusTitleView = new TextView(context);
        statusTitleView.setTextColor(Color.WHITE);
        statusTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f);
        statusTitleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        statusTitleView.setLetterSpacing(0.01f);
        statusTitleView.setShadowLayer(8f, 0f, 2f, 0xAA000000);
        topHudContainer.addView(statusTitleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        stageSubtitleView = new TextView(context);
        stageSubtitleView.setTextColor(0xC8E6EEFF);
        stageSubtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        stageSubtitleView.setTypeface(Typeface.SANS_SERIF);
        stageSubtitleView.setShadowLayer(6f, 0f, 1.5f, 0xAA000000);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(4);
        topHudContainer.addView(stageSubtitleView, subLp);

        LayoutParams topHudLp = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        topHudLp.leftMargin = dp(32);
        topHudLp.topMargin = dp(26);
        addView(topHudContainer, topHudLp);

        // Minimal bottom-left Cancel action
        cancelButton = new TextView(context);
        cancelButton.setText(R.string.conn_cancel_hint);
        cancelButton.setTextColor(0xEBFFFFFF);
        cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        cancelButton.setTypeface(Typeface.SANS_SERIF);
        cancelButton.setPadding(dp(16), dp(8), dp(16), dp(8));

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setShape(GradientDrawable.RECTANGLE);
        pillBg.setCornerRadius(dp(999));
        pillBg.setColor(0x28121828);
        pillBg.setStroke(Math.max(1, dp(1)), 0x42FFFFFF);
        cancelButton.setBackground(pillBg);
        cancelButton.setOnClickListener(v -> {
            Runnable cancel = onCancelAction;
            releaseAndHide();
            if (cancel != null) {
                cancel.run();
            }
        });

        LayoutParams cancelLp = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        cancelLp.leftMargin = dp(32);
        cancelLp.bottomMargin = dp(26);
        addView(cancelButton, cancelLp);

        // Keep HUD clear of display cutouts while TextureView fills edge-to-edge under cutouts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setOnApplyWindowInsetsListener((v, insets) -> {
                android.view.DisplayCutout cutout = insets.getDisplayCutout();
                int safeLeft = cutout != null ? cutout.getSafeInsetLeft() : 0;
                int safeTop = cutout != null ? cutout.getSafeInsetTop() : 0;
                int safeBottom = cutout != null ? cutout.getSafeInsetBottom() : 0;

                LayoutParams tLp = (LayoutParams) topHudContainer.getLayoutParams();
                tLp.leftMargin = dp(32) + safeLeft;
                tLp.topMargin = dp(26) + safeTop;
                topHudContainer.setLayoutParams(tLp);

                LayoutParams cLp = (LayoutParams) cancelButton.getLayoutParams();
                cLp.leftMargin = dp(32) + safeLeft;
                cLp.bottomMargin = dp(26) + safeBottom;
                cancelButton.setLayoutParams(cLp);
                return insets;
            });
        }
    }

    public boolean isOverlayActive() {
        return active && getVisibility() == VISIBLE;
    }

    /**
     * Phase 1 — Connecting (Looping).
     */
    public void startConnecting(
            @Nullable String hostName,
            @Nullable String initialStage,
            @Nullable Runnable onCancel,
            @Nullable Runnable onRevealComplete) {
        cancelAnimatorsAndCallbacks();
        stopRenderThread();
        loadTimingPreferences();

        this.active = true;
        this.disconnecting = false;
        this.phase2Started = false;
        this.phase2Completed = false;
        this.firstFrameReceived = false;
        this.phase3Started = false;
        this.onCancelAction = onCancel;
        this.onRevealCompleteAction = onRevealComplete;

        this.uReadyMix = 0f;
        this.uPreviewMix = 0f;
        this.uExpandProgress = 0f;
        this.uCutoutOnly = 0f;

        String titleText = (hostName != null && !hostName.trim().isEmpty())
                ? "Connecting to \"" + hostName.trim() + "\""
                : getContext().getString(R.string.conn_establishing_title);
        statusTitleView.setText(titleText);
        stageSubtitleView.setText(initialStage != null ? initialStage : "");

        topHudContainer.setVisibility(VISIBLE);
        topHudContainer.setAlpha(1f);
        statusTitleView.setAlpha(1f);
        stageSubtitleView.setAlpha(1f);
        cancelButton.setVisibility(VISIBLE);
        cancelButton.setAlpha(1f);

        setClickable(true);
        setFocusable(true);
        setVisibility(VISIBLE);
        bringToFront();

        ensureRenderThreadRunning();
    }

    /**
     * Updates the minimal stage subtitle while in Phase 1.
     */
    public void updateStageMessage(@Nullable final String stageText) {
        if (!active || disconnecting || phase2Started) {
            return;
        }
        mainHandler.post(() -> {
            if (active && !disconnecting && !phase2Started && stageSubtitleView != null) {
                stageSubtitleView.setText(stageText != null ? stageText : "");
            }
        });
    }

    /**
     * Phase 2 — Connected / Ready Snap (300 ms).
     */
    public void onConnectionStarted() {
        mainHandler.post(() -> {
            if (!active || disconnecting || phase2Started) {
                return;
            }
            phase2Started = true;

            // Cross-fade status text to "Ready"
            statusTitleView.animate()
                    .alpha(0f)
                    .setDuration(110L)
                    .withEndAction(() -> {
                        if (!active || disconnecting) {
                            return;
                        }
                        statusTitleView.setText("Ready");
                        statusTitleView.animate().alpha(1f).setDuration(140L).start();
                    })
                    .start();
            stageSubtitleView.animate().alpha(0f).setDuration(150L).start();

            cancelActiveAnimator();
            activeAnimator = ValueAnimator.ofFloat(uReadyMix, 1f);
            activeAnimator.setDuration(phase2ReadyMs);
            activeAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            activeAnimator.addUpdateListener(anim -> uReadyMix = (float) anim.getAnimatedValue());
            activeAnimator.addListener(new AnimatorListenerAdapter() {
                private boolean canceled = false;

                @Override
                public void onAnimationCancel(Animator animation) {
                    canceled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (canceled || !active || disconnecting) {
                        return;
                    }
                    uReadyMix = 1f;
                    phase2Completed = true;
                    if (firstFrameReceived) {
                        startPhase3();
                    } else {
                        // Safety fallback in case first-frame callback is delayed
                        mainHandler.postDelayed(firstFrameFallbackRunnable, FIRST_FRAME_FALLBACK_MS);
                    }
                }
            });
            activeAnimator.start();
        });
    }

    /**
     * Hooked from MediaCodecDecoderRenderer when the first decoded video frame is rendered.
     */
    public void onFirstVideoFrameRendered() {
        mainHandler.post(() -> {
            if (!active || disconnecting) {
                return;
            }
            firstFrameReceived = true;
            if (phase2Completed && !phase3Started) {
                mainHandler.removeCallbacks(firstFrameFallbackRunnable);
                startPhase3();
            }
        });
    }

    /**
     * Phase 3 — Live Stream Peephole & Outward Swirl.
     */
    private void startPhase3() {
        if (!active || disconnecting || phase3Started) {
            return;
        }
        phase3Started = true;
        mainHandler.removeCallbacks(firstFrameFallbackRunnable);

        // Fade all HUD elements to 0 alpha smoothly
        topHudContainer.animate().alpha(0f).setDuration(PHASE3_HUD_FADE_MS).start();
        cancelButton.animate().alpha(0f).setDuration(PHASE3_HUD_FADE_MS).withEndAction(() -> {
            cancelButton.setVisibility(GONE);
        }).start();

        // Pass input through as soon as the live peephole opens
        setClickable(false);
        setFocusable(false);

        cancelActiveAnimator();
        activeAnimator = ValueAnimator.ofFloat(uPreviewMix, 1f);
        activeAnimator.setDuration(phase3PreviewFadeMs);
        // Linear driver; smooth easeInOutCubic curve is applied inside the fragment shader
        activeAnimator.setInterpolator(new LinearInterpolator());
        activeAnimator.addUpdateListener(anim -> uPreviewMix = (float) anim.getAnimatedValue());
        activeAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean canceled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (canceled || !active || disconnecting) {
                    return;
                }
                uPreviewMix = 1f;
                if (phase3HoldMs <= 0L) {
                    startPhase4();
                } else {
                    mainHandler.postDelayed(phase3HoldRunnable, phase3HoldMs);
                }
            }
        });
        activeAnimator.start();
    }

    /**
     * Phase 4 — Iris Wipe Reveal (linear; cubic easing inside the shader).
     */
    private void startPhase4() {
        if (!active || disconnecting) {
            return;
        }
        cancelActiveAnimator();
        activeAnimator = ValueAnimator.ofFloat(uExpandProgress, 1f);
        activeAnimator.setDuration(phase4ExpandMs);
        activeAnimator.setInterpolator(new LinearInterpolator());
        activeAnimator.addUpdateListener(anim -> uExpandProgress = (float) anim.getAnimatedValue());
        activeAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean canceled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (canceled || !active || disconnecting) {
                    return;
                }
                uExpandProgress = 1f;
                Runnable complete = onRevealCompleteAction;
                releaseAndHide();
                if (complete != null) {
                    complete.run();
                }
            }
        });
        activeAnimator.start();
    }

    /**
     * Reversed disconnecting portal animation:
     * Iris closes from fullscreen (u_expandProgress 1 -> 0), then fades the stream peephole back
     * into the starry solar-eclipse void (u_previewMix & u_readyMix 1 -> 0) before invoking onComplete.
     */
    public void startDisconnecting(@Nullable final Runnable onComplete) {
        mainHandler.post(() -> {
            if (disconnecting) {
                return;
            }
            cancelAnimatorsAndCallbacks();
            stopRenderThread();
            loadTimingPreferences();

            this.active = true;
            this.disconnecting = true;
            this.uReadyMix = 1f;
            this.uPreviewMix = 1f;
            this.uExpandProgress = 1f;
            this.uCutoutOnly = 0f;

            topHudContainer.setVisibility(GONE);
            cancelButton.setVisibility(GONE);
            setClickable(false);
            setFocusable(false);
            setVisibility(VISIBLE);
            bringToFront();

            ensureRenderThreadRunning();

            // Step 1: Reverse iris close (1.0 -> 0.0)
            activeAnimator = ValueAnimator.ofFloat(1f, 0f);
            activeAnimator.setDuration(disconnectIrisCloseMs);
            activeAnimator.setInterpolator(new LinearInterpolator());
            activeAnimator.addUpdateListener(anim -> uExpandProgress = (float) anim.getAnimatedValue());
            activeAnimator.addListener(new AnimatorListenerAdapter() {
                private boolean canceled = false;

                @Override
                public void onAnimationCancel(Animator animation) {
                    canceled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (canceled || !disconnecting) {
                        return;
                    }
                    uExpandProgress = 0f;
                    // Step 2: Fade live peephole back into dark eclipse void (1.0 -> 0.0)
                    activeAnimator = ValueAnimator.ofFloat(1f, 0f);
                    activeAnimator.setDuration(disconnectEclipseCloseMs);
                    activeAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                    activeAnimator.addUpdateListener(a -> {
                        float v = (float) a.getAnimatedValue();
                        uPreviewMix = v;
                        uReadyMix = v;
                    });
                    activeAnimator.addListener(new AnimatorListenerAdapter() {
                        private boolean innerCanceled = false;

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            innerCanceled = true;
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (innerCanceled) {
                                return;
                            }
                            releaseAndHide();
                            if (onComplete != null) {
                                onComplete.run();
                            }
                        }
                    });
                    activeAnimator.start();
                }
            });
            activeAnimator.start();
        });
    }

    /**
     * Reverse iris-close transition played over AppView when launching a stream:
     * Closes the portal iris (u_expandProgress 1 -> 0) WITHOUT activating ready or stream-fade
     * animations (u_readyMix = 0, u_previewMix = 0, u_cutoutOnly = 1) while fading out the
     * underlying app cards so Game seamlessly picks up at the closed portal circle in Phase 1.
     */
    public void startLaunchIrisClose(@Nullable final View contentToFadeOut, @Nullable final Runnable onComplete) {
        mainHandler.post(() -> {
            cancelAnimatorsAndCallbacks();
            stopRenderThread();
            loadTimingPreferences();

            this.active = true;
            this.disconnecting = false;
            this.phase2Started = false;
            this.phase2Completed = false;
            this.firstFrameReceived = false;
            this.phase3Started = false;

            this.uReadyMix = 0f;
            this.uPreviewMix = 0f;
            this.uExpandProgress = 1f;
            this.uCutoutOnly = 1f;

            topHudContainer.setVisibility(GONE);
            cancelButton.setVisibility(GONE);
            setClickable(true);
            setFocusable(true);
            setVisibility(VISIBLE);
            bringToFront();

            ensureRenderThreadRunning();

            if (contentToFadeOut != null) {
                contentToFadeOut.animate().cancel();
                contentToFadeOut.animate()
                        .alpha(0f)
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(Math.max(150L, Math.round(launchCloseMs * 0.85f)))
                        .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                        .start();
            }

            activeAnimator = ValueAnimator.ofFloat(1f, 0f);
            activeAnimator.setDuration(launchCloseMs);
            activeAnimator.setInterpolator(new LinearInterpolator());
            activeAnimator.addUpdateListener(anim -> uExpandProgress = (float) anim.getAnimatedValue());
            activeAnimator.addListener(new AnimatorListenerAdapter() {
                private boolean canceled = false;

                @Override
                public void onAnimationCancel(Animator animation) {
                    canceled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (canceled || !active) {
                        return;
                    }
                    uExpandProgress = 0f;
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            });
            activeAnimator.start();
        });
    }

    /**
     * Stops the GL render loop, releases EGL/GL resources cleanly, and hides the overlay with
     * zero overdraw during gameplay.
     */
    public void releaseAndHide() {
        cancelAnimatorsAndCallbacks();
        stopRenderThread();

        active = false;
        disconnecting = false;
        phase2Started = false;
        phase2Completed = false;
        firstFrameReceived = false;
        phase3Started = false;
        onCancelAction = null;
        onRevealCompleteAction = null;

        setClickable(false);
        setFocusable(false);
        setVisibility(GONE);
    }

    private void cancelActiveAnimator() {
        if (activeAnimator != null) {
            activeAnimator.cancel();
            activeAnimator = null;
        }
    }

    private void cancelAnimatorsAndCallbacks() {
        mainHandler.removeCallbacks(firstFrameFallbackRunnable);
        mainHandler.removeCallbacks(phase3HoldRunnable);
        cancelActiveAnimator();
        if (statusTitleView != null) {
            statusTitleView.animate().cancel();
        }
        if (stageSubtitleView != null) {
            stageSubtitleView.animate().cancel();
        }
        if (topHudContainer != null) {
            topHudContainer.animate().cancel();
        }
        if (cancelButton != null) {
            cancelButton.animate().cancel();
        }
    }

    private void ensureRenderThreadRunning() {
        if (!active) {
            return;
        }
        if (renderThread != null && renderThread.isRunning()) {
            return;
        }
        SurfaceTexture st = activeSurfaceTexture;
        if (st == null && glTextureView.isAvailable()) {
            st = glTextureView.getSurfaceTexture();
            activeSurfaceTexture = st;
            activeSurfaceWidth = glTextureView.getWidth();
            activeSurfaceHeight = glTextureView.getHeight();
        }
        if (st != null && activeSurfaceWidth > 0 && activeSurfaceHeight > 0) {
            renderThread = new PortalGlThread(st, activeSurfaceWidth, activeSurfaceHeight);
            renderThread.start();
        }
    }

    private void stopRenderThread() {
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
        activeSurfaceWidth = width;
        activeSurfaceHeight = height;
        if (active) {
            stopRenderThread();
            renderThread = new PortalGlThread(surface, width, height);
            renderThread.start();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        activeSurfaceTexture = surface;
        activeSurfaceWidth = width;
        activeSurfaceHeight = height;
        PortalGlThread t = renderThread;
        if (t != null) {
            t.updateSize(width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        stopRenderThread();
        activeSurfaceTexture = null;
        activeSurfaceWidth = 0;
        activeSurfaceHeight = 0;
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
        // No-op
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseAndHide();
        super.onDetachedFromWindow();
    }

    private int dp(int dp) {
        return Math.max(1, Math.round(UiHelper.dpToPx(getContext(), dp)));
    }

    // =============================================================================================
    // Dedicated EGL14 + OpenGL ES 3.0 Render Thread
    // =============================================================================================

    private final class PortalGlThread extends Thread {
        private static final int EGL_OPENGL_ES3_BIT_KHR = 0x0040;

        private final SurfaceTexture surfaceTexture;
        private volatile int width;
        private volatile int height;
        private volatile boolean running = true;

        private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
        private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
        private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;

        private int program = 0;
        private int aPosLoc = -1;
        private int uResolutionLoc = -1;
        private int uTimeLoc = -1;
        private int uReadyMixLoc = -1;
        private int uPreviewMixLoc = -1;
        private int uExpandProgressLoc = -1;
        private int uSwirlSpeedLoc = -1;
        private int uPortalScaleLoc = -1;
        private int uGlowIntensityLoc = -1;
        private int uParticleBrightnessLoc = -1;
        private int uSonarSpeedLoc = -1;
        private int uCutoutOnlyLoc = -1;
        private FloatBuffer quadBuffer;

        PortalGlThread(SurfaceTexture surfaceTexture, int width, int height) {
            super("PortalGlThread");
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

            final long startNs = SystemClock.elapsedRealtimeNanos();

            while (running) {
                int w = width;
                int h = height;
                if (w <= 0 || h <= 0) {
                    SystemClock.sleep(16L);
                    continue;
                }

                float elapsedSec = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000_000f;

                GLES30.glViewport(0, 0, w, h);
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

                GLES30.glUseProgram(program);
                GLES30.glUniform2f(uResolutionLoc, (float) w, (float) h);
                GLES30.glUniform1f(uTimeLoc, elapsedSec);
                GLES30.glUniform1f(uReadyMixLoc, uReadyMix);
                GLES30.glUniform1f(uPreviewMixLoc, uPreviewMix);
                GLES30.glUniform1f(uExpandProgressLoc, uExpandProgress);
                GLES30.glUniform1f(uSwirlSpeedLoc, uSwirlSpeed);
                GLES30.glUniform1f(uPortalScaleLoc, uPortalScale);
                GLES30.glUniform1f(uGlowIntensityLoc, uGlowIntensity);
                GLES30.glUniform1f(uParticleBrightnessLoc, uParticleBrightness);
                GLES30.glUniform1f(uSonarSpeedLoc, uSonarSpeed);
                GLES30.glUniform1f(uCutoutOnlyLoc, uCutoutOnly);

                quadBuffer.position(0);
                GLES30.glEnableVertexAttribArray(aPosLoc);
                GLES30.glVertexAttribPointer(aPosLoc, 2, GLES30.GL_FLOAT, false, 0, quadBuffer);
                GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6);
                GLES30.glDisableVertexAttribArray(aPosLoc);

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
            int vs = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER_SRC);
            if (vs == 0) {
                return false;
            }
            int fs = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_SRC);
            if (fs == 0) {
                GLES30.glDeleteShader(vs);
                return false;
            }

            program = GLES30.glCreateProgram();
            GLES30.glAttachShader(program, vs);
            GLES30.glAttachShader(program, fs);
            GLES30.glLinkProgram(program);

            GLES30.glDeleteShader(vs);
            GLES30.glDeleteShader(fs);

            int[] linked = new int[1];
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0);
            if (linked[0] == 0) {
                LimeLog.severe("PortalConnectionOverlay program link failed: " + GLES30.glGetProgramInfoLog(program));
                GLES30.glDeleteProgram(program);
                program = 0;
                return false;
            }

            aPosLoc = GLES30.glGetAttribLocation(program, "a_pos");
            uResolutionLoc = GLES30.glGetUniformLocation(program, "u_resolution");
            uTimeLoc = GLES30.glGetUniformLocation(program, "u_time");
            uReadyMixLoc = GLES30.glGetUniformLocation(program, "u_readyMix");
            uPreviewMixLoc = GLES30.glGetUniformLocation(program, "u_previewMix");
            uExpandProgressLoc = GLES30.glGetUniformLocation(program, "u_expandProgress");
            uSwirlSpeedLoc = GLES30.glGetUniformLocation(program, "u_swirlSpeed");
            uPortalScaleLoc = GLES30.glGetUniformLocation(program, "u_portalScale");
            uGlowIntensityLoc = GLES30.glGetUniformLocation(program, "u_glowIntensity");
            uParticleBrightnessLoc = GLES30.glGetUniformLocation(program, "u_particleBrightness");
            uSonarSpeedLoc = GLES30.glGetUniformLocation(program, "u_sonarSpeed");
            uCutoutOnlyLoc = GLES30.glGetUniformLocation(program, "u_cutoutOnly");

            quadBuffer = ByteBuffer.allocateDirect(FULLSCREEN_QUAD.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            quadBuffer.put(FULLSCREEN_QUAD).position(0);

            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA);
            GLES30.glClearColor(0f, 0f, 0f, 0f);
            return true;
        }

        private int compileShader(int type, String src) {
            int shader = GLES30.glCreateShader(type);
            GLES30.glShaderSource(shader, src);
            GLES30.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                LimeLog.severe("PortalConnectionOverlay shader compile failed: " + GLES30.glGetShaderInfoLog(shader));
                GLES30.glDeleteShader(shader);
                return 0;
            }
            return shader;
        }

        private void releaseGl() {
            if (program != 0) {
                GLES30.glDeleteProgram(program);
                program = 0;
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
