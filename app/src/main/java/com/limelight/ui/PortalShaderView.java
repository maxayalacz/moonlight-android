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

    private static final int OVERLAY_MAX_SHORT_EDGE = 720;
    private static final int PERF_MODE_BG_SHORT_EDGE = 480;
    private static final int PERF_MODE_OVERLAY_SHORT_EDGE = 540;
    private static final long MENU_ACTIVE_FRAME_MS = 16L;
    private static final long MENU_IDLE_FRAME_MS = 33L;
    private static final long MENU_IDLE_THRESHOLD_MS = 3000L;
    private static final long BATTERY_SAVER_FRAME_MS = 33L;
    private static final int BATTERY_SAVER_BG_SHORT_EDGE = 360;
    private static final long BLUR_IRIS_INTERVAL_MS = 16L;
    private static final long BLUR_ACTIVE_INTERVAL_MS = 33L;
    private static final long BLUR_IDLE_INTERVAL_MS = 66L;
    private static final long BLUR_PERF_IRIS_INTERVAL_MS = 33L;
    private static final long BLUR_PERF_ACTIVE_INTERVAL_MS = 50L;
    private static final long BLUR_PERF_IDLE_INTERVAL_MS = 100L;

    private static volatile long sLastUserInteractionMs = SystemClock.uptimeMillis();

    public static void markUserInteraction() {
        sLastUserInteractionMs = SystemClock.uptimeMillis();
    }

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

    private static final String BLUR_VERTEX_SHADER_SRC =
            "#version 300 es\n" +
            "in vec2 a_pos;\n" +
            "uniform float u_flipY;\n" +
            "out vec2 v_uv;\n" +
            "void main() {\n" +
            "    vec2 uv = a_pos * 0.5 + 0.5;\n" +
            "    if (u_flipY > 0.5) {\n" +
            "        uv.y = 1.0 - uv.y;\n" +
            "    }\n" +
            "    v_uv = uv;\n" +
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

    // 9-tap separable Gaussian blur shader executed on GPU at adaptive inverse resolution
    // Includes One UI vibrancy/saturation boost on the final vertical pass (0 extra FBO passes)
    private static final String BLUR_FRAGMENT_SHADER_SRC =
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "in vec2 v_uv;\n" +
            "uniform sampler2D u_tex;\n" +
            "uniform vec2 u_texelStep;\n" +
            "uniform float u_saturation;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    vec4 sum = texture(u_tex, v_uv) * 0.227027;\n" +
            "    sum += texture(u_tex, clamp(v_uv + u_texelStep * 1.384615, vec2(0.0), vec2(1.0))) * 0.316216;\n" +
            "    sum += texture(u_tex, clamp(v_uv - u_texelStep * 1.384615, vec2(0.0), vec2(1.0))) * 0.316216;\n" +
            "    sum += texture(u_tex, clamp(v_uv + u_texelStep * 3.230769, vec2(0.0), vec2(1.0))) * 0.070270;\n" +
            "    sum += texture(u_tex, clamp(v_uv - u_texelStep * 3.230769, vec2(0.0), vec2(1.0))) * 0.070270;\n" +
            "    if (abs(u_saturation - 1.0) > 0.001) {\n" +
            "        float luma = dot(sum.rgb, vec3(0.2126, 0.7152, 0.0722));\n" +
            "        sum.rgb = clamp(mix(vec3(luma), sum.rgb, u_saturation), 0.0, 1.0);\n" +
            "    }\n" +
            "    fragColor = vec4(sum.rgb, 1.0);\n" +
            "}\n";

    private static final String FRAGMENT_SHADER_SRC =
            "#version 300 es\n" +
            "precision highp float;\n" +
            "\n" +
            "out vec4 fragColor;\n" +
            "\n" +
            "uniform vec2  u_resolution;\n" +
            "uniform vec2  u_portalCenterOffset;\n" +
            "uniform float u_time;\n" +
            "uniform float u_readyMix;\n" +
            "uniform float u_chargeMix;\n" +
            "uniform float u_previewMix;\n" +
            "uniform float u_expandProgress;\n" +
            "uniform float u_pulseMorph;\n" +
            "uniform float u_isDisconnecting;\n" +
            "uniform float u_portalScale;\n" +
            "uniform float u_glowIntensity;\n" +
            "uniform float u_particleBrightness;\n" +
            "uniform float u_particleSize;\n" +
            "uniform float u_particleDensity;\n" +
            "uniform float u_sonarSpeed;\n" +
            "uniform float u_pulseAmount;\n" +
            "uniform float u_pulseBrightness;\n" +
            "uniform float u_pulseLength;\n" +
            "uniform float u_pulseSpeed;\n" +
            "uniform float u_waveAmp;\n" +
            "uniform vec3  u_bgEdge;\n" +
            "uniform vec3  u_bgCenter;\n" +
            "uniform vec3  u_bgInner;\n" +
            "uniform vec3  u_particleCols[3];\n" +
            "uniform vec3  u_pulseCol;\n" +
            "uniform vec3  u_ringCol;\n" +
            "uniform vec3  u_chargeCol;\n" +
            "uniform float u_isLight;\n" +
            "uniform vec4  u_layerRot[3];\n" +
            "uniform vec2  u_layerShift[3];\n" +
            "uniform int   u_maxLayers;\n" +
            "uniform int   u_maxSubParticles;\n" +
            "uniform int   u_activeStrands;\n" +
            "uniform int   u_maxSpiralTurns;\n" +
            "uniform vec4  u_spiralCtrl[4];\n" +
            "uniform vec4  u_strandRot[10];\n" +
            "uniform vec4  u_strandGeom[10];\n" +
            "uniform vec4  u_strandWave[10];\n" +
            "uniform vec4  u_strandPhase[10];\n" +
            "\n" +
            "const float PI  = 3.14159265359;\n" +
            "const float TAU = 6.28318530718;\n" +
            "\n" +
            "float safePow(float x, float p) {\n" +
            "    return x <= 0.0 ? 0.0 : pow(x, p);\n" +
            "}\n" +
            "\n" +
            "vec2 hash22(vec2 p) {\n" +
            "    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));\n" +
            "    p3 += dot(p3, p3.yzx + 33.33);\n" +
            "    return fract((p3.xx + p3.yz) * p3.zy);\n" +
            "}\n" +
            "\n" +
            "float easeInOutCubic(float t) {\n" +
            "    return t < 0.5 ? 4.0 * t * t * t : 1.0 - safePow(-2.0 * t + 2.0, 3.0) * 0.5;\n" +
            "}\n" +
            "\n" +
            "vec3 sampleParticles(vec2 uv, float time) {\n" +
            "    vec3 col = vec3(0.0);\n" +
            "    float amount = clamp(u_particleDensity, 0.0, 20.0);\n" +
            "    if (amount <= 0.001) return col;\n" +
            "    float sz = clamp(u_particleSize, 0.1, 8.0);\n" +
            "    float totalExpected = 0.45 * amount;\n" +
            "    float bound = min(0.48, max(0.13, 0.13 * sz));\n" +
            "    float maxReach = 0.495 + bound;\n" +
            "    float preDriftBound = bound + 0.22;\n" +
            "\n" +
            "    for (int layer = 0; layer < 3; layer++) {\n" +
            "        if (layer >= u_maxLayers) break;\n" +
            "        float fl = float(layer);\n" +
            "        vec3 layerCol = u_particleCols[layer];\n" +
            "        vec3 pTintA = layerCol * 0.78;\n" +
            "        vec3 pTintB = min(layerCol * 1.15 + vec3(0.08), vec3(1.0));\n" +
            "        // Constant grid scale so increasing Particle Amount never zooms out!\n" +
            "        float scale = 14.0 + fl * 9.0;\n" +
            "        vec4 lr = u_layerRot[layer];\n" +
            "        vec2 p = mat2(lr.x, lr.y, lr.z, lr.w) * uv + u_layerShift[layer];\n" +
            "        p *= scale;\n" +
            "\n" +
            "        vec2 id = floor(p);\n" +
            "        vec2 gv = fract(p) - 0.5;\n" +
            "\n" +
            "        // Exact lossless neighbor cell range culling (~5.7x fewer cell evaluations)\n" +
            "        int xMin = (gv.x <= maxReach - 1.0) ? -1 : 0;\n" +
            "        int xMax = (gv.x >= 1.0 - maxReach) ?  1 : 0;\n" +
            "        int yMin = (gv.y <= maxReach - 1.0) ? -1 : 0;\n" +
            "        int yMax = (gv.y >= 1.0 - maxReach) ?  1 : 0;\n" +
            "\n" +
            "        for (int y = -1; y <= 1; y++) {\n" +
            "            if (y < yMin || y > yMax) continue;\n" +
            "            for (int x = -1; x <= 1; x++) {\n" +
            "                if (x < xMin || x > xMax) continue;\n" +
            "                vec2 offs = vec2(float(x), float(y));\n" +
            "                vec2 cellId = id + offs;\n" +
            "\n" +
            "                for (int sub = 0; sub < 6; sub++) {\n" +
            "                    if (sub >= u_maxSubParticles) break;\n" +
            "                    float fSub = float(sub);\n" +
            "                    float slotProb = clamp(totalExpected - fSub, 0.0, 1.0);\n" +
            "                    if (slotProb <= 0.001) break;\n" +
            "\n" +
            "                    vec2 rnd = hash22(cellId + vec2(fl * 37.0 + fSub * 71.0, fSub * 113.0));\n" +
            "                    if (rnd.x > slotProb) continue;\n" +
            "\n" +
            "                    vec2 basePos = offs + (rnd - 0.5) * 0.55;\n" +
            "                    vec2 preDiff = gv - basePos;\n" +
            "                    if (abs(preDiff.x) > preDriftBound || abs(preDiff.y) > preDriftBound) continue;\n" +
            "\n" +
            "                    vec2 drift = vec2(\n" +
            "                        sin(time * (0.4 + rnd.y * 0.5) + rnd.x * TAU),\n" +
            "                        cos(time * (0.35 + rnd.x * 0.5) + rnd.y * TAU)\n" +
            "                    ) * 0.22;\n" +
            "\n" +
            "                    vec2 diff = preDiff - drift;\n" +
            "                    if (abs(diff.x) > bound || abs(diff.y) > bound) continue;\n" +
            "\n" +
            "                    float radius = mix(0.035, 0.11, fract(rnd.x * 13.7)) * sz;\n" +
            "                    float distSq = dot(diff, diff);\n" +
            "                    if (distSq >= radius * radius) continue;\n" +
            "                    float dist = sqrt(distSq);\n" +
            "                    float bokeh = smoothstep(radius, radius * 0.15, dist);\n" +
            "                    float twinkle = 0.55 + 0.45 * sin(time * (1.8 + rnd.y * 3.0) + rnd.x * TAU);\n" +
            "\n" +
            "                    vec3 pCol = mix(pTintA, pTintB, rnd.y);\n" +
            "                    col += pCol * bokeh * twinkle * (0.45 - fl * 0.08);\n" +
            "                }\n" +
            "            }\n" +
            "        }\n" +
            "    }\n" +
            "    return col;\n" +
            "}\n" +
            "\n" +
            "float evalCometPhase(float pos, float headLen, float tailLen) {\n" +
            "    float rel = fract(pos + 0.5) - 0.5;\n" +
            "    if (rel > headLen || rel < -tailLen) return 0.0;\n" +
            "    float ahead = 1.0 - smoothstep(0.0, headLen, rel);\n" +
            "    float tail  = safePow(smoothstep(-tailLen, 0.0, rel), 1.65);\n" +
            "    return ahead * tail;\n" +
            "}\n" +
            "\n" +
            "// --- Combined Bidirectional Spiral Morph (CPU-precomputed ctrl + Menu Fast-Path + Pre-Wave Comet Cull) ---\n" +
            "vec3 sampleSpiralingEnergyPaths(vec2 uv, float morph, float readyT, vec3 pulseCol, vec3 rimHaloCol, vec3 rimCoreCol) {\n" +
            "    if (u_pulseBrightness <= 0.001 || u_activeStrands <= 0) return vec3(0.0);\n" +
            "\n" +
            "    vec4 c0 = u_spiralCtrl[0]; // (handoffFade, pitch, thetaLimit, waveFade)\n" +
            "    vec4 c1 = u_spiralCtrl[1]; // (ringFuse, stackNorm, coreBaseW, haloBaseW)\n" +
            "    vec4 c2 = u_spiralCtrl[2]; // (rejectDist, curHead, curTail, coreGain)\n" +
            "    vec4 c3 = u_spiralCtrl[3]; // (maxSpiralDr, dirSign, kStart, unused)\n" +
            "\n" +
            "    float handoffFade = c0.x;\n" +
            "    if (handoffFade <= 0.001) return vec3(0.0);\n" +
            "\n" +
            "    float coreBaseW  = c1.z;\n" +
            "    float haloBaseW  = c1.w;\n" +
            "    float rejectDist = c2.x;\n" +
            "    float curHead    = c2.y;\n" +
            "    float curTail    = c2.z;\n" +
            "\n" +
            "    // Zero-quality-loss Menu Fast-Path (morph == 0): skips spiral loop, exp/log softplus & endFade\n" +
            "    if (morph <= 0.0005) {\n" +
            "        vec3 col = vec3(0.0);\n" +
            "        for (int i = 0; i < 10; i++) {\n" +
            "            if (i >= u_activeStrands) break;\n" +
            "            vec4 sr = u_strandRot[i];\n" +
            "            vec2 p = mat2(sr.x, sr.y, sr.z, sr.w) * uv;\n" +
            "\n" +
            "            vec4 g = u_strandGeom[i];\n" +
            "            vec4 w = u_strandWave[i];\n" +
            "            float targetR = g.y - 40.0;\n" +
            "            float maxSlope = w.x * w.z + w.y * w.w;\n" +
            "            float bandMargin = rejectDist * (1.0 + maxSlope) + w.z + w.w + 0.06;\n" +
            "            if (abs((p.y - targetR) - g.z) > bandMargin) continue;\n" +
            "\n" +
            "            vec2 v = vec2(p.x, p.y + 40.0);\n" +
            "            float rLocal = length(v);\n" +
            "            float s = atan(v.x, v.y) * g.y;\n" +
            "\n" +
            "            vec4 ph = u_strandPhase[i];\n" +
            "            float pos = fract(s * g.w + ph.z);\n" +
            "            float energy = evalCometPhase(pos, curHead, curTail);\n" +
            "            if (energy < 0.002) continue;\n" +
            "\n" +
            "            float ph1 = s * w.x + ph.x;\n" +
            "            float ph2 = s * w.y + ph.y;\n" +
            "            float waveY = g.z + sin(ph1) * w.z + sin(ph2) * w.w;\n" +
            "            float dyds  = cos(ph1) * w.x * w.z + cos(ph2) * w.y * w.w;\n" +
            "            float dist  = abs((rLocal - g.y) - waveY) * inversesqrt(1.0 + dyds * dyds);\n" +
            "            if (dist > rejectDist) continue;\n" +
            "\n" +
            "            float taper = mix(0.45, 1.0, safePow(energy, 0.45));\n" +
            "            float core  = exp(-dist / (coreBaseW * taper));\n" +
            "            float halo  = exp(-dist / (haloBaseW * taper)) * 0.78;\n" +
            "            vec3 turnCol = (pulseCol * halo + rimCoreCol * core * 1.10) * energy;\n" +
            "            col += turnCol * (u_pulseBrightness * ph.w);\n" +
            "        }\n" +
            "        return col;\n" +
            "    }\n" +
            "\n" +
            "    float pitch       = c0.y;\n" +
            "    float thetaLimit  = c0.z;\n" +
            "    float waveFade    = c0.w;\n" +
            "    float ringFuse    = c1.x;\n" +
            "    float stackNorm   = c1.y;\n" +
            "    float coreGain    = c2.w;\n" +
            "    float maxSpiralDr = c3.x;\n" +
            "    float dirSign     = c3.y;\n" +
            "    int   kStart      = int(floor(c3.z + 0.5));\n" +
            "\n" +
            "    vec3 activeHaloCol = mix(pulseCol, rimHaloCol, ringFuse);\n" +
            "    const float SOFT_K    = 0.62;\n" +
            "    const float SOFT_KNEE = 1.25;\n" +
            "    vec3 col = vec3(0.0);\n" +
            "\n" +
            "    for (int i = 0; i < 10; i++) {\n" +
            "        if (i >= u_activeStrands) break;\n" +
            "        vec4 sr = u_strandRot[i];\n" +
            "        vec2 p = mat2(sr.x, sr.y, sr.z, sr.w) * uv;\n" +
            "\n" +
            "        vec4 g = u_strandGeom[i];\n" +
            "        vec4 w = u_strandWave[i];\n" +
            "        vec2 v = vec2(p.x, p.y + g.x);\n" +
            "        float rLocal = length(v);\n" +
            "\n" +
            "        // Conservative radial envelope cull before atan() and multi-turn loop\n" +
            "        float envMargin = rejectDist * (2.7 + w.x * w.z + w.y * w.w) + abs(g.z) + w.z + w.w;\n" +
            "        if (rLocal < g.y - envMargin || rLocal > g.y + maxSpiralDr + envMargin) continue;\n" +
            "\n" +
            "        float phi = atan(v.x, v.y);\n" +
            "        vec4 ph = u_strandPhase[i];\n" +
            "        vec3 strandCol = vec3(0.0);\n" +
            "\n" +
            "        for (int j = 0; j < 4; j++) {\n" +
            "            if (j >= u_maxSpiralTurns) break;\n" +
            "            int k = kStart + j;\n" +
            "            float theta = phi + TAU * float(k);\n" +
            "\n" +
            "            float endFade = 1.0 - smoothstep(thetaLimit - 0.85, thetaLimit, abs(theta));\n" +
            "            if (endFade <= 0.001) continue;\n" +
            "\n" +
            "            // Check comet visibility BEFORE exp/log softplus & wave trig (~75% early rejection)\n" +
            "            float s = theta * g.y;\n" +
            "            float pos = fract(s * g.w + ph.z);\n" +
            "            float cometE = evalCometPhase(pos, curHead, curTail);\n" +
            "            float energy = mix(cometE, 1.0, ringFuse) * endFade;\n" +
            "            if (energy < 0.002) continue;\n" +
            "\n" +
            "            float x = clamp(dirSign * SOFT_K * (theta + dirSign * SOFT_KNEE), -16.0, 16.0);\n" +
            "            float ex = (x > 10.0) ? 0.0 : exp(x);\n" +
            "            float soft = (x > 10.0) ? x : log(1.0 + ex);\n" +
            "            float spiralDr = pitch * (soft / SOFT_K);\n" +
            "\n" +
            "            float rBase = g.y + spiralDr;\n" +
            "            if (abs(rLocal - rBase) > rejectDist + 0.22 * waveFade) continue;\n" +
            "\n" +
            "            float waveY = 0.0;\n" +
            "            float dyds  = 0.0;\n" +
            "            if (waveFade > 0.001) {\n" +
            "                float ph1 = s * w.x + ph.x;\n" +
            "                float ph2 = s * w.y + ph.y;\n" +
            "                waveY = g.z + sin(ph1) * w.z + sin(ph2) * w.w;\n" +
            "                dyds  = cos(ph1) * w.x * w.z + cos(ph2) * w.y * w.w;\n" +
            "            }\n" +
            "\n" +
            "            float rCurve = rBase + waveY;\n" +
            "\n" +
            "            float sig = (x > 10.0) ? 1.0 : (ex / (1.0 + ex));\n" +
            "            float dSpiralDTheta = dirSign * pitch * sig;\n" +
            "            float slope = (dSpiralDTheta / max(rCurve, 0.06)) + dyds;\n" +
            "            float dist = abs(rLocal - rCurve) * inversesqrt(1.0 + slope * slope);\n" +
            "\n" +
            "            if (dist > rejectDist) continue;\n" +
            "\n" +
            "            float taper = mix(0.45, 1.0, safePow(energy, 0.45));\n" +
            "            float core  = exp(-dist / (coreBaseW * taper));\n" +
            "            float halo  = exp(-dist / (haloBaseW * taper)) * 0.78;\n" +
            "            vec3 turnCol = (activeHaloCol * halo + rimCoreCol * core * coreGain) * energy;\n" +
            "\n" +
            "            strandCol = max(strandCol, turnCol);\n" +
            "        }\n" +
            "\n" +
            "        col += strandCol * (u_pulseBrightness * ph.w * stackNorm);\n" +
            "    }\n" +
            "\n" +
            "    return col * handoffFade;\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    // Normalize by shortest screen dimension so iris & particles stay identical in portrait and landscape\n" +
            "    float minDim = min(u_resolution.x, u_resolution.y);\n" +
            "    vec2 uv = (gl_FragCoord.xy - 0.5 * u_resolution) / minDim;\n" +
            "    vec2 portalUv = uv - u_portalCenterOffset;\n" +
            "    float d = length(portalUv);\n" +
            "\n" +
            "    float readyT   = clamp(u_readyMix, 0.0, 1.0);\n" +
            "    float chargeT  = clamp(u_chargeMix, 0.0, 1.0);\n" +
            "    float previewT = clamp(u_previewMix, 0.0, 1.0);\n" +
            "    float expandT  = clamp(u_expandProgress, 0.0, 1.0);\n" +
            "    bool  isDisc   = (u_isDisconnecting > 0.5);\n" +
            "\n" +
            "    float halfDiag = length(0.5 * u_resolution / minDim) + length(u_portalCenterOffset);\n" +
            "    float maxRadius = halfDiag + 0.18;\n" +
            "    float baseRadius = 0.25 * clamp(u_portalScale, 0.2, 4.0);\n" +
            "    float expandEase = (isDisc && previewT < 0.5)\n" +
            "        ? (1.0 - safePow(1.0 - expandT, 2.6))\n" +
            "        : easeInOutCubic(expandT);\n" +
            "    float radius = mix(baseRadius, maxRadius, expandEase);\n" +
            "\n" +
            "    float aa = 2.0 / minDim;\n" +
            "    float outsideMask = smoothstep(radius - aa, radius + aa, d);\n" +
            "    float insideMask  = 1.0 - outsideMask;\n" +
            "\n" +
            "    // --- 1. Themed Matte Backdrop & Subtle Atmospheric Sheen ---\n" +
            "    float bgGrad    = exp(-d * 1.9);\n" +
            "    vec3 outerBg    = mix(u_bgEdge, u_bgCenter, bgGrad);\n" +
            "    vec3 innerBg    = mix(u_bgInner, u_bgCenter, bgGrad * 0.55);\n" +
            "\n" +
            "    float innerAlpha = 1.0 - previewT;\n" +
            "    float bgAlpha    = mix(innerAlpha, 1.0, outsideMask);\n" +
            "\n" +
            "    float menuOpenVis = insideMask * innerAlpha * smoothstep(0.55, 1.0, expandT);\n" +
            "    vec2 sheenDir     = vec2(cos(u_time * 0.07 + 0.45), sin(u_time * 0.07 + 0.45));\n" +
            "    float sheenGrad   = smoothstep(-0.45, 0.95, dot(portalUv, sheenDir));\n" +
            "    vec3  menuInner   = innerBg + (u_pulseCol * mix(0.11, 0.06, u_isLight)) * sheenGrad * menuOpenVis;\n" +
            "    vec3  bgColor     = mix(menuInner, outerBg, outsideMask);\n" +
            "\n" +
            "    // --- 2. Iris Rim & Charge Ring Colors (Independent from Energy Pulses & UI Highlight) ---\n" +
            "    vec3 colIdle   = u_ringCol;\n" +
            "    vec3 colReady  = mix(u_ringCol, vec3(0.45, 0.36, 1.00), 0.35);\n" +
            "    vec3 colCharge = u_chargeCol;\n" +
            "    vec3 colCore   = mix(vec3(0.95, 0.97, 1.00), u_ringCol, u_isLight * 0.35);\n" +
            "\n" +
            "    vec3 haloColor = mix(colIdle, colReady, readyT);\n" +
            "    haloColor      = mix(haloColor, colCharge, chargeT);\n" +
            "\n" +
            "    // --- 3. Interior Starfield (3 Independent Particle Layer Colors) ---\n" +
            "    float streamParticleFade = 1.0 - smoothstep(0.0, 1.0, expandEase);\n" +
            "    float particleOpacity = mix(1.0, streamParticleFade, previewT);\n" +
            "    float particleVis = insideMask * particleOpacity * smoothstep(radius, radius - 0.04, d);\n" +
            "    vec3 particles = vec3(0.0);\n" +
            "    if (particleVis > 0.001 && u_particleBrightness > 0.001) {\n" +
            "        particles = sampleParticles(uv, u_time) * particleVis * u_particleBrightness;\n" +
            "    }\n" +
            "\n" +
            "    // --- 4. Bidirectional Forward-Flowing Spiral Pulses ---\n" +
            "    float morph = clamp(u_pulseMorph, 0.0, 1.0);\n" +
            "    vec3 menuPulses = vec3(0.0);\n" +
            "    if (innerAlpha > 0.001) {\n" +
            "        menuPulses = sampleSpiralingEnergyPaths(portalUv, morph, readyT, u_pulseCol, haloColor, colCore) * innerAlpha;\n" +
            "    }\n" +
            "\n" +
            "    // --- 5-7. Portal Sonar, Ring & Shockwaves (Skipped completely when iris is fully open) ---\n" +
            "    float globalRingFade = 1.0 - smoothstep(0.82, 1.0, expandT);\n" +
            "    vec3 ringGlow = vec3(0.0);\n" +
            "    if (globalRingFade > 0.001 && u_glowIntensity > 0.001) {\n" +
            "        // --- 5. Phase 1 Connecting Sonar Ripples (Linearly Fades Out Across Sonar -> Ready) ---\n" +
            "        vec3 sonar = vec3(0.0);\n" +
            "        float sonarVis = (1.0 - readyT) * (1.0 - smoothstep(0.0, 0.35, expandT));\n" +
            "        if (sonarVis > 0.001 && d > radius) {\n" +
            "            for (int i = 0; i < 2; i++) {\n" +
            "                float phase = fract(u_time * 0.42 * u_sonarSpeed + float(i) * 0.5);\n" +
            "                float rSonar = radius + phase * 0.22;\n" +
            "                float ring = exp(-abs(d - rSonar) * 65.0);\n" +
            "                float fade = sin(phase * PI) * (1.0 - phase);\n" +
            "                sonar += haloColor * ring * fade * 0.35 * sonarVis;\n" +
            "            }\n" +
            "        }\n" +
            "\n" +
            "        // --- 6. Primary Portal Ring & Linearly Ramping Charge Arcs ---\n" +
            "        float ringDist = abs(d - radius);\n" +
            "        float breathe  = 1.0 + 0.06 * sin(u_time * 2.6);\n" +
            "\n" +
            "        float haloFalloff = mix(15.0, 30.0, chargeT);\n" +
            "        float idleGlow    = exp(-ringDist * 26.0) * 0.72;\n" +
            "        float readyHalo   = exp(-ringDist * haloFalloff) * mix(0.95, 0.75, chargeT) * breathe;\n" +
            "        float readyCore   = exp(-ringDist * 185.0) * 1.35 * breathe;\n" +
            "\n" +
            "        vec3 mainRing = mix(\n" +
            "            haloColor * idleGlow,\n" +
            "            haloColor * readyHalo + colCore * readyCore,\n" +
            "            readyT\n" +
            "        );\n" +
            "\n" +
            "        // Let mainRing emerge smoothly as the spiraling strands converge onto the iris\n" +
            "        float hasPulses = clamp(u_pulseAmount, 0.0, 1.0) * smoothstep(0.0, 0.15, u_pulseBrightness);\n" +
            "        float ringConverge = mix(\n" +
            "            1.0,\n" +
            "            isDisc ? smoothstep(0.78, 0.98, morph) : smoothstep(0.72, 0.98, morph),\n" +
            "            hasPulses * innerAlpha\n" +
            "        );\n" +
            "        mainRing *= ringConverge;\n" +
            "\n" +
            "        float chargeVis = chargeT * (1.0 - smoothstep(0.6, 1.0, expandT));\n" +
            "        vec3 arcs = vec3(0.0);\n" +
            "        if (chargeVis > 0.001) {\n" +
            "            float angle = atan(portalUv.y, portalUv.x);\n" +
            "            float a1 = 0.5 + 0.5 * sin(angle * 2.0 + u_time * 3.4);\n" +
            "            float a2 = 0.5 + 0.5 * sin(angle * 3.0 - u_time * 2.1 + 1.2);\n" +
            "            float arcMask = safePow(a1, 5.0) + 0.6 * safePow(a2, 6.0);\n" +
            "            float rimBand = exp(-ringDist * 75.0);\n" +
            "            arcs = mix(haloColor, colCore, 0.45) * rimBand * arcMask * 1.0 * chargeVis;\n" +
            "        }\n" +
            "\n" +
            "        // --- 7. Iris Open/Close Trailing Concentric Shockwaves (Stream Wipe Only) ---\n" +
            "        vec3 shockwaves = vec3(0.0);\n" +
            "        if (expandT > 0.001) {\n" +
            "            float expandFade = sin(expandT * PI);\n" +
            "            float shockBoost = mix(0.0, 1.0, previewT);\n" +
            "            if (shockBoost > 0.001) {\n" +
            "                for (int i = 1; i <= 3; i++) {\n" +
            "                    float fi = float(i);\n" +
            "                    float lag = fi * 0.045 * (0.4 + 0.6 * expandT);\n" +
            "                    float rShock = max(0.0, radius - lag);\n" +
            "                    float w = exp(-abs(d - rShock) * (85.0 - fi * 12.0));\n" +
            "                    vec3 sCol = mix(haloColor, colCore, 0.15 * (1.0 - fi / 3.0));\n" +
            "                    shockwaves += sCol * w * (0.65 / fi) * expandFade * shockBoost;\n" +
            "                }\n" +
            "            }\n" +
            "        }\n" +
            "\n" +
            "        ringGlow = (sonar + mainRing + arcs + shockwaves) * u_glowIntensity * globalRingFade;\n" +
            "    }\n" +
            "\n" +
            "    if (u_isLight > 0.5) {\n" +
            "        // Light Mode: softly tint motes, energy pulses & ring into airy backdrop when opaque, additive over live stream\n" +
            "        float pMax = max(particles.r, max(particles.g, particles.b));\n" +
            "        float pStrength = clamp(pMax * 0.85, 0.0, 0.65);\n" +
            "        vec3 pTint = pMax > 0.001 ? clamp(particles / pMax, 0.0, 1.0) : u_particleCols[0];\n" +
            "        vec3 lightBgWithMotes = mix(bgColor, pTint, pStrength);\n" +
            "        vec3 combinedGlow = menuPulses + ringGlow;\n" +
            "        float rStrength = clamp(max(combinedGlow.r, max(combinedGlow.g, combinedGlow.b)) * 0.85, 0.0, 0.92);\n" +
            "        vec3 lightComposed = mix(lightBgWithMotes, clamp(combinedGlow, 0.0, 1.0), rStrength);\n" +
            "        vec3 overStream = particles * previewT + ringGlow * (1.0 - bgAlpha);\n" +
            "        fragColor = vec4(lightComposed * bgAlpha + overStream, bgAlpha);\n" +
            "    } else {\n" +
            "        fragColor = vec4(bgColor * bgAlpha + particles + menuPulses + ringGlow, bgAlpha);\n" +
            "    }\n" +
            "}\n";

    private static final float[] FULLSCREEN_QUAD = {
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
            -1f,  1f,
             1f, -1f,
             1f,  1f
    };

    private static volatile long sClockPausedGapMs = 0L;
    private static volatile long sHandoffPauseStartMs = 0L;
    private static volatile boolean sHandoffPending = false;

    /**
     * Shared monotonic clock across all screens so the starfield never jumps or resets
     * between PcView, AppView, Settings, and Game. Absorbs brief activity/EGL handoff gaps
     * so the incoming screen resumes from the exact handoff timestamp with zero rubberband.
     */
    public static float getSharedTimeSec() {
        long effectiveMs = SystemClock.uptimeMillis() - sClockPausedGapMs;
        if (effectiveMs < 0L) {
            effectiveMs = 0L;
        }
        return (effectiveMs % 1_000_000L) / 1000.0f;
    }

    private static void settleHandoffClockIfNeeded() {
        if (!sHandoffPending) {
            return;
        }
        sHandoffPending = false;
        long pauseStart = sHandoffPauseStartMs;
        if (pauseStart > 0L) {
            long gapMs = SystemClock.uptimeMillis() - pauseStart;
            if (gapMs > 16L && gapMs < 1500L) {
                sClockPausedGapMs += (gapMs - 16L);
            }
        }
    }

    private static final int HANDOFF_SHORT_EDGE = 360;
    private static volatile Bitmap sHandoffBitmap;
    private static volatile boolean sHandoffLandscape;

    /**
     * Captures a lightweight snapshot of the live GL starfield right before an activity transition
     * so the incoming or returning activity displays the exact starfield from Frame 0 while its
     * EGL context & shaders initialize (zero solid-color flash).
     */
    public static void captureHandoffFrame(@Nullable PortalShaderView view) {
        if (view == null || !view.isAvailable()) {
            return;
        }
        int w = view.getWidth();
        int h = view.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        int shortEdge = Math.min(w, h);
        float scale = shortEdge > HANDOFF_SHORT_EDGE ? (HANDOFF_SHORT_EDGE / (float) shortEdge) : 1.0f;
        int tw = Math.max(1, Math.round(w * scale));
        int th = Math.max(1, Math.round(h * scale));
        try {
            Bitmap bmp = view.getBitmap(tw, th);
            if (bmp != null) {
                sHandoffBitmap = bmp;
                sHandoffLandscape = (tw >= th);
                sHandoffPauseStartMs = SystemClock.uptimeMillis();
                sHandoffPending = true;
            }
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    public static Bitmap getHandoffBitmap(boolean landscape) {
        Bitmap bmp = sHandoffBitmap;
        if (bmp == null || bmp.isRecycled() || sHandoffLandscape != landscape) {
            return null;
        }
        return bmp;
    }

    private volatile float uReadyMix = 0f;
    private volatile float uChargeMix = 0f;
    private volatile float uPreviewMix = 0f;
    private volatile float uExpandProgress = 1f;
    private volatile float uPulseMorph = 0f;
    private volatile float uIsDisconnecting = 0f;
    private volatile float uPortalCenterOffsetX = 0f;
    private volatile float uPortalCenterOffsetY = 0f;
    private volatile float uPortalScale = 1f;
    private volatile float uGlowIntensity = 1f;
    private volatile float uParticleBrightness = 1f;
    private volatile float uParticleSize = 1f;
    private volatile float uParticleDensity = 1f;
    private volatile float uSonarSpeed = 1f;
    private volatile float uPulseAmount = 7f;
    private volatile float uPulseBrightness = 0.95f;
    private volatile float uPulseLength = 1f;
    private volatile float uPulseSpeed = 1f;
    private volatile float uWaveAmp = 1f;
    private volatile int blurAmountPercent = 100;
    private volatile int blurSaturationPercent = 140;
    private volatile int frostedTintAmountPercent = 35;
    private volatile boolean blurDirty = true;
    private volatile int bgMaxShortEdge = 720;
    private volatile int bgResolutionPercent = PreferenceConfiguration.DEFAULT_BG_RESOLUTION_PERCENT;
    private volatile int blurResolutionPercent = PreferenceConfiguration.DEFAULT_BLUR_RESOLUTION_PERCENT;
    private volatile String uiFramerate = PreferenceConfiguration.DEFAULT_UI_FRAMERATE;
    private volatile boolean batterySaverEffective = false;
    private volatile boolean performanceModeEffective = false;
    private volatile boolean backgroundMode = false;
    private volatile boolean renderingDesired = false;
    private volatile boolean firstFrameDispatched = false;

    public boolean isBatterySaverEffective() {
        return batterySaverEffective;
    }

    public boolean isPerformanceModeEffective() {
        return performanceModeEffective;
    }

    private PortalGlThread renderThread;
    private PortalGlThread stoppingThread;
    private SurfaceTexture activeSurfaceTexture;
    private int activeWidth = 0;
    private int activeHeight = 0;
    private Runnable firstFrameListener;

    // Off-UI-thread blurred refraction bitmap (adaptive inverse-resolution @ ~60 FPS)
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<WeakReference<Drawable>> frostedDrawables = new CopyOnWriteArrayList<>();
    private volatile Bitmap publishedBlurBitmap;

    private final Runnable invalidateFrostedRunnable = () -> {
        if (frostedDrawables.isEmpty()) {
            return;
        }
        for (WeakReference<Drawable> ref : frostedDrawables) {
            Drawable d = ref.get();
            if (d == null) {
                frostedDrawables.remove(ref);
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

    private volatile int uBgEdgeColor = 0xFF12141E;
    private volatile int uBgCenterColor = 0xFF1C2038;
    private volatile int uBgInnerColor = 0xFF0C0E1C;
    private volatile int uParticleNearRgb = 0xFFC7D8FF;
    private volatile int uParticleMidRgb = 0xFF8AA8FF;
    private volatile int uParticleFarRgb = 0xFF5E6BFF;
    private volatile int uPulseRgb = 0xFF5C72FF;
    private volatile int uRingRgb = 0xFF5C72FF;
    private volatile int uChargeRgb = 0xFF38B5FF;
    private volatile float uIsLight = 0f;

    public int getBlurAmountPercent() {
        return (batterySaverEffective || frostedTintAmountPercent >= 100) ? 0 : blurAmountPercent;
    }

    private void applyPaletteUniforms(@NonNull AppThemePalette palette) {
        uIsLight = palette.isLight ? 1f : 0f;
        // Strictly derive background gradient from palette.background (never palette.surface!)
        // so Background and Surface remain 100% independent channels.
        if (palette.isLight) {
            uBgEdgeColor = AppThemePalette.blendColors(palette.background, palette.accent, 0.08f);
            uBgCenterColor = AppThemePalette.blendColors(palette.background, 0xFFFFFFFF, 0.22f);
            uBgInnerColor = palette.background;
        } else {
            uBgEdgeColor = palette.background;
            uBgCenterColor = AppThemePalette.blendColors(palette.background, palette.accent, 0.14f);
            uBgInnerColor = AppThemePalette.blendColors(palette.background, 0xFF04060C, 0.28f);
        }
        uParticleNearRgb = palette.particleColor;
        uParticleMidRgb = palette.particleColorMid;
        uParticleFarRgb = palette.particleColorFar;
        uPulseRgb = palette.pulseColor;
        uRingRgb = palette.ringColor;
        uChargeRgb = palette.chargeColor;
    }

    public void applyPreviewOverride(@NonNull AppThemePalette.ThemeBundle bundle) {
        markUserInteraction();
        uPortalScale = Math.max(0.2f, Math.min(20.0f, bundle.portalWindowScalePercent / 100f));
        uGlowIntensity = Math.max(0.0f, Math.min(20.0f, bundle.portalGlowIntensityPercent / 100f));
        uParticleBrightness = Math.max(0.0f, Math.min(20.0f, bundle.portalParticleBrightnessPercent / 100f));
        uParticleSize = Math.max(0.1f, Math.min(20.0f, bundle.portalParticleSizePercent / 100f));
        uParticleDensity = Math.max(0.0f, Math.min(20.0f, bundle.portalParticleDensityPercent / 100f));
        uSonarSpeed = Math.max(0.0f, Math.min(20.0f, bundle.portalSonarSpeedPercent / 100f));
        uPulseAmount = Math.max(0.0f, Math.min(10.0f, bundle.portalPulseAmount));
        uPulseBrightness = Math.max(0.0f, Math.min(5.0f, bundle.portalPulseBrightnessPercent / 100f));
        uPulseLength = Math.max(0.1f, Math.min(5.0f, bundle.portalPulseLengthPercent / 100f));
        uPulseSpeed = Math.max(0.0f, Math.min(10.0f, bundle.portalPulseSpeedPercent / 100f));
        uWaveAmp = Math.max(0.0f, Math.min(5.0f, bundle.portalWaveAmpPercent / 100f));
        blurAmountPercent = Math.max(0, Math.min(2000, bundle.blurAmountPercent));
        blurSaturationPercent = Math.max(0, Math.min(500, bundle.blurSaturationPercent));
        frostedTintAmountPercent = Math.max(0, Math.min(100, bundle.frostedTintAmountPercent));
        batterySaverEffective = PreferenceConfiguration.isUiBatterySaverEffective(getContext());
        performanceModeEffective = !batterySaverEffective && PreferenceConfiguration.isUiPerformanceModeEffective(getContext());
        blurDirty = true;
        if (batterySaverEffective || blurAmountPercent <= 0 || frostedTintAmountPercent >= 100) {
            publishedBlurBitmap = null;
            mainHandler.post(invalidateFrostedRunnable);
        }
        try {
            bgMaxShortEdge = Math.max(0, Integer.parseInt(bundle.bgQuality));
        } catch (Exception e) {
            bgMaxShortEdge = 720;
        }
        bgResolutionPercent = Math.max(25, Math.min(100, bundle.bgResolutionPercent));
        blurResolutionPercent = Math.max(5, Math.min(50, bundle.blurResolutionPercent));
        uiFramerate = bundle.uiFramerate != null ? bundle.uiFramerate : PreferenceConfiguration.DEFAULT_UI_FRAMERATE;
        applyPaletteUniforms(AppThemePalette.buildCustomPalette(bundle.colors, bundle.frostedTintAmountPercent));
    }

    public void refreshFromPrefs() {
        PreferenceConfiguration cfg = PreferenceConfiguration.readPreferences(getContext());
        AppThemePalette palette = AppThemePalette.getCurrent(getContext());
        uPortalScale = Math.max(0.2f, Math.min(20.0f, cfg.portalWindowScalePercent / 100f));
        uGlowIntensity = Math.max(0.0f, Math.min(20.0f, cfg.portalGlowIntensityPercent / 100f));
        uParticleBrightness = Math.max(0.0f, Math.min(20.0f, cfg.portalParticleBrightnessPercent / 100f));
        uParticleSize = Math.max(0.1f, Math.min(20.0f, cfg.portalParticleSizePercent / 100f));
        uParticleDensity = Math.max(0.0f, Math.min(20.0f, cfg.portalParticleDensityPercent / 100f));
        uSonarSpeed = Math.max(0.0f, Math.min(20.0f, cfg.portalSonarSpeedPercent / 100f));
        uPulseAmount = Math.max(0.0f, Math.min(10.0f, cfg.portalPulseAmount));
        uPulseBrightness = Math.max(0.0f, Math.min(5.0f, cfg.portalPulseBrightnessPercent / 100f));
        uPulseLength = Math.max(0.1f, Math.min(5.0f, cfg.portalPulseLengthPercent / 100f));
        uPulseSpeed = Math.max(0.0f, Math.min(10.0f, cfg.portalPulseSpeedPercent / 100f));
        uWaveAmp = Math.max(0.0f, Math.min(5.0f, cfg.portalWaveAmpPercent / 100f));
        blurAmountPercent = Math.max(0, Math.min(2000, cfg.blurAmountPercent));
        blurSaturationPercent = Math.max(0, Math.min(500, cfg.blurSaturationPercent));
        frostedTintAmountPercent = palette.getEffectiveTintAmount();
        batterySaverEffective = cfg.isUiBatterySaverEffective;
        performanceModeEffective = cfg.isUiPerformanceModeEffective;
        blurDirty = true;
        if (batterySaverEffective || blurAmountPercent <= 0 || frostedTintAmountPercent >= 100) {
            publishedBlurBitmap = null;
            mainHandler.post(invalidateFrostedRunnable);
        }
        bgMaxShortEdge = Math.max(0, cfg.bgQualityMaxShortEdge);
        bgResolutionPercent = Math.max(25, Math.min(100, cfg.bgResolutionPercent));
        blurResolutionPercent = Math.max(5, Math.min(50, cfg.blurResolutionPercent));
        uiFramerate = cfg.uiFramerate != null ? cfg.uiFramerate : PreferenceConfiguration.DEFAULT_UI_FRAMERATE;
        applyPaletteUniforms(palette);
    }

    /**
     * Configures this view to render the full-screen cosmic starfield background
     * via a capped offscreen GL FBO + fullscreen linear blit.
     */
    public void configureStarfieldBackgroundMode() {
        backgroundMode = true;
        uReadyMix = 0f;
        uChargeMix = 0f;
        uPreviewMix = 0f;
        uExpandProgress = 1f;
        uPulseMorph = 0f;
        uIsDisconnecting = 0f;
        refreshFromPrefs();
    }

    public void setPortalCenterOffset(float offsetX, float offsetY) {
        this.uPortalCenterOffsetX = offsetX;
        this.uPortalCenterOffsetY = offsetY;
    }

    public void setUniforms(float readyMix, float previewMix, float expandProgress) {
        setUniforms(readyMix, previewMix, previewMix, expandProgress);
    }

    public void setUniforms(float readyMix, float chargeMix, float previewMix, float expandProgress) {
        markUserInteraction();
        this.uReadyMix = readyMix;
        this.uChargeMix = chargeMix;
        this.uPreviewMix = previewMix;
        this.uExpandProgress = expandProgress;
    }

    public void setUniforms(float readyMix, float chargeMix, float previewMix, float expandProgress, float pulseMorph) {
        markUserInteraction();
        this.uReadyMix = readyMix;
        this.uChargeMix = chargeMix;
        this.uPreviewMix = previewMix;
        this.uExpandProgress = expandProgress;
        this.uPulseMorph = pulseMorph;
    }

    public void setReadyMix(float readyMix) {
        this.uReadyMix = readyMix;
    }

    public void setChargeMix(float chargeMix) {
        this.uChargeMix = chargeMix;
    }

    public void setPreviewMix(float previewMix) {
        this.uPreviewMix = previewMix;
    }

    public void setExpandProgress(float expandProgress) {
        this.uExpandProgress = expandProgress;
    }

    public void setPulseMorph(float pulseMorph) {
        this.uPulseMorph = pulseMorph;
    }

    public void setIsDisconnecting(boolean disconnecting) {
        this.uIsDisconnecting = disconnecting ? 1f : 0f;
    }

    public float getReadyMix() {
        return uReadyMix;
    }

    public float getChargeMix() {
        return uChargeMix;
    }

    public float getPreviewMix() {
        return uPreviewMix;
    }

    public float getExpandProgress() {
        return uExpandProgress;
    }

    public float getPulseMorph() {
        return uPulseMorph;
    }

    public boolean isDisconnecting() {
        return uIsDisconnecting > 0.5f;
    }

    public void registerFrostedDrawable(@NonNull Drawable drawable) {
        for (WeakReference<Drawable> ref : frostedDrawables) {
            if (ref.get() == drawable) {
                return;
            }
        }
        frostedDrawables.add(new WeakReference<>(drawable));
    }

    @Nullable
    public Bitmap getBlurredBitmap() {
        if (batterySaverEffective || blurAmountPercent <= 0 || frostedTintAmountPercent >= 100) {
            return null;
        }
        Bitmap bmp = publishedBlurBitmap;
        if (bmp == null) {
            blurDirty = true;
        }
        return bmp;
    }

    public boolean hasRenderedFrame() {
        return firstFrameDispatched && renderThread != null && renderThread.isRunning();
    }

    /**
     * Invokes {@code listener} on the UI thread as soon as the first live GL frame has been
     * swapped to this TextureView (or immediately if a live frame is already on screen).
     */
    public void setFirstFrameListener(@Nullable Runnable listener) {
        if (listener == null) {
            this.firstFrameListener = null;
            return;
        }
        if (hasRenderedFrame()) {
            this.firstFrameListener = null;
            listener.run();
        } else {
            this.firstFrameListener = listener;
        }
    }

    private void joinStoppingThreadIfNeeded() {
        PortalGlThread prev = stoppingThread;
        stoppingThread = null;
        if (prev != null && prev.isAlive()) {
            prev.requestStop();
            try {
                prev.join(450L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
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
            joinStoppingThreadIfNeeded();
            firstFrameDispatched = false;
            renderThread = new PortalGlThread(st, activeWidth, activeHeight);
            renderThread.start();
        }
    }

    /**
     * Signals the GL render loop to exit and release resources without blocking the UI thread.
     * Ideal for Activity.onStop() so backgrounded menus free GPU memory with 0 ms UI stall.
     */
    public void stopRenderingAsync() {
        renderingDesired = false;
        firstFrameDispatched = false;
        PortalGlThread t = renderThread;
        renderThread = null;
        if (t != null) {
            t.requestStop();
            stoppingThread = t;
        }
    }

    public void stopRendering() {
        renderingDesired = false;
        firstFrameDispatched = false;
        firstFrameListener = null;
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
        joinStoppingThreadIfNeeded();
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
            joinStoppingThreadIfNeeded();
            firstFrameDispatched = false;
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
        firstFrameDispatched = false;
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
        joinStoppingThreadIfNeeded();
        activeSurfaceTexture = null;
        activeWidth = 0;
        activeHeight = 0;
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
        if (!firstFrameDispatched && renderingDesired) {
            firstFrameDispatched = true;
            Runnable listener = firstFrameListener;
            firstFrameListener = null;
            if (listener != null) {
                listener.run();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopRendering();
        super.onDetachedFromWindow();
    }

    private static final float[] STRAND_BASE_ANGLES = {
            -0.38f, 2.76f, 1.12f, -2.02f, 0.07f, -0.68f, 2.46f, -1.42f, 0.54f, -2.96f
    };
    private static final float[] STRAND_BASE_OFFSETS = {
            0.28f, 0.24f, 0.18f, 0.22f, 0.06f, 0.34f, 0.32f, 0.10f, 0.14f, 0.38f
    };
    private static final float[] STRAND_RND_X = new float[10];
    private static final float[] STRAND_RND_Y = new float[10];
    static {
        for (int i = 0; i < 10; i++) {
            float fi = (float) i;
            float px = fi * 5.31f + 1.7f;
            float py = 13.37f + fi * 2.1f;
            float x = px * 0.1031f;
            x -= (float) Math.floor(x);
            float y = py * 0.1030f;
            y -= (float) Math.floor(y);
            float z = px * 0.0973f;
            z -= (float) Math.floor(z);
            float d = x * (y + 33.33f) + y * (z + 33.33f) + z * (x + 33.33f);
            x += d;
            y += d;
            z += d;
            float rx = (x + y) * z;
            rx -= (float) Math.floor(rx);
            float ry = (x + z) * y;
            ry -= (float) Math.floor(ry);
            STRAND_RND_X[i] = rx;
            STRAND_RND_Y[i] = ry;
        }
    }

    private static float clampF(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float mixF(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smoothstepF(float e0, float e1, float x) {
        float t = clampF((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float safePowF(float x, float p) {
        return x <= 0f ? 0f : (float) Math.pow(x, p);
    }

    private static float easeInOutCubicF(float t) {
        return t < 0.5f ? 4f * t * t * t : 1f - safePowF(-2f * t + 2f, 3f) * 0.5f;
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
        private int uPortalCenterOffsetLoc = -1;
        private int uTimeLoc = -1;
        private int uReadyMixLoc = -1;
        private int uChargeMixLoc = -1;
        private int uPreviewMixLoc = -1;
        private int uExpandProgressLoc = -1;
        private int uPulseMorphLoc = -1;
        private int uIsDisconnectingLoc = -1;
        private int uPortalScaleLoc = -1;
        private int uGlowIntensityLoc = -1;
        private int uParticleBrightnessLoc = -1;
        private int uParticleSizeLoc = -1;
        private int uParticleDensityLoc = -1;
        private int uSonarSpeedLoc = -1;
        private int uPulseAmountLoc = -1;
        private int uPulseBrightnessLoc = -1;
        private int uPulseLengthLoc = -1;
        private int uPulseSpeedLoc = -1;
        private int uWaveAmpLoc = -1;
        private int uBgEdgeLoc = -1;
        private int uBgCenterLoc = -1;
        private int uBgInnerLoc = -1;
        private int uParticleColsLoc = -1;
        private int uPulseColLoc = -1;
        private int uRingColLoc = -1;
        private int uChargeColLoc = -1;
        private int uIsLightLoc = -1;
        private int uLayerRotLoc = -1;
        private int uLayerShiftLoc = -1;
        private int uMaxLayersLoc = -1;
        private int uMaxSubParticlesLoc = -1;
        private int uActiveStrandsLoc = -1;
        private int uMaxSpiralTurnsLoc = -1;
        private int uSpiralCtrlLoc = -1;
        private int uStrandRotLoc = -1;
        private int uStrandGeomLoc = -1;
        private int uStrandWaveLoc = -1;
        private int uStrandPhaseLoc = -1;
        private final float[] particleColsBuf = new float[9];
        private final float[] layerRotBuf = new float[12];
        private final float[] layerShiftBuf = new float[6];
        private final float[] spiralCtrlBuf = new float[16];
        private final float[] strandRotBuf = new float[40];
        private final float[] strandGeomBuf = new float[40];
        private final float[] strandWaveBuf = new float[40];
        private final float[] strandPhaseBuf = new float[40];

        // Fullscreen linear blit program for scaled background FBO
        private int blitProgram = 0;
        private int blitPosLoc = -1;
        private int blitTexLoc = -1;

        // GPU 9-tap separable Gaussian blur program
        private int blurProgram = 0;
        private int blurPosLoc = -1;
        private int blurTexLoc = -1;
        private int blurStepLoc = -1;
        private int blurFlipYLoc = -1;
        private int blurSatLoc = -1;

        // Offscreen scaled background FBO
        private int bgFbo = 0;
        private int bgFboTex = 0;
        private int bgFboW = 0;
        private int bgFboH = 0;

        // Adaptive inverse-resolution ping-pong blur FBOs (high-res at low blur, low-res at high blur)
        private int blurFbo = 0;
        private int blurFboTex = 0;
        private int blurFboB = 0;
        private int blurFboTexB = 0;
        private int blurW = 0;
        private int blurH = 0;
        private ByteBuffer readbackBuf;
        private final int[] blurPbos = new int[2];
        private int pboWriteIndex = 0;
        private boolean pboPrimed = false;
        private final Bitmap[] blurBitmapRing = new Bitmap[3];
        private int blurRingIndex = 0;
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
            if (!backgroundMode) {
                try {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DISPLAY);
                } catch (Throwable ignored) {
                }
            }
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

                long frameStartMs = SystemClock.uptimeMillis();
                if (!firstFrameDispatched && backgroundMode) {
                    settleHandoffClockIfNeeded();
                }
                float sharedTime = getSharedTimeSec();

                int shortEdge = Math.min(w, h);
                boolean irisTransitioning = (uExpandProgress < 0.999f || uPulseMorph > 0.001f);
                boolean saverActive = batterySaverEffective;
                boolean perfActive = !saverActive && performanceModeEffective;
                if (backgroundMode) {
                    float scale = Math.max(0.25f, Math.min(1.0f, bgResolutionPercent / 100f));
                    if (saverActive) {
                        if (shortEdge > BATTERY_SAVER_BG_SHORT_EDGE) {
                            scale = Math.min(scale, BATTERY_SAVER_BG_SHORT_EDGE / (float) shortEdge);
                        }
                    } else if (perfActive) {
                        scale = Math.max(0.25f, scale * 0.75f);
                    }
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

                    // 3. Live GPU blurred refraction bitmap synced to the exact background refresh rate
                    int blurPct = (saverActive || frostedTintAmountPercent >= 100) ? 0 : blurAmountPercent;
                    if (blurPct <= 0) {
                        if (publishedBlurBitmap != null) {
                            publishedBlurBitmap = null;
                            mainHandler.post(invalidateFrostedRunnable);
                        }
                        blurDirty = false;
                    } else if (blurDirty || !frostedDrawables.isEmpty()) {
                        blurDirty = false;
                        lastBlurUpdateMs = frameStartMs;
                        updateBlurBitmapOnGlThread(w, h, blurPct, blurResolutionPercent, perfActive);
                    }
                } else {
                    // Game overlay mode: render into adaptive RGBA FBO + GL_LINEAR blit (preserves 100% alpha over stream)
                    int cap;
                    if (saverActive) {
                        cap = BATTERY_SAVER_BG_SHORT_EDGE;
                    } else if (perfActive) {
                        cap = PERF_MODE_OVERLAY_SHORT_EDGE;
                    } else {
                        int userOverlayCap = Math.max(240, Math.round(shortEdge * (bgResolutionPercent / 100f)));
                        cap = Math.min(OVERLAY_MAX_SHORT_EDGE, userOverlayCap);
                    }
                    if (shortEdge > cap) {
                        float scale = cap / (float) shortEdge;
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
                        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
                        GLES30.glViewport(0, 0, w, h);
                        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
                        drawPortalShader(w, h, sharedTime);
                    }
                }

                if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
                    int err = EGL14.eglGetError();
                    if (err == EGL14.EGL_BAD_SURFACE || err == EGL14.EGL_BAD_NATIVE_WINDOW) {
                        break;
                    }
                }

                if (backgroundMode && !irisTransitioning) {
                    long targetFrameMs;
                    if (saverActive) {
                        targetFrameMs = BATTERY_SAVER_FRAME_MS;
                    } else {
                        targetFrameMs = resolveUiFrameIntervalMs(uiFramerate);
                    }
                    if (targetFrameMs > 0L) {
                        long elapsedMs = SystemClock.uptimeMillis() - frameStartMs;
                        long sleepMs = targetFrameMs - elapsedMs;
                        if (sleepMs > 0L) {
                            try {
                                Thread.sleep(sleepMs);
                            } catch (InterruptedException ignored) {
                                break;
                            }
                        }
                    }
                }
            }

            releaseGl();
            releaseEgl();
        }

        private void drawPortalShader(int w, int h, float sharedTime) {
            GLES30.glUseProgram(program);
            GLES30.glUniform2f(uResolutionLoc, (float) w, (float) h);
            if (uPortalCenterOffsetLoc >= 0) {
                GLES30.glUniform2f(uPortalCenterOffsetLoc, uPortalCenterOffsetX, uPortalCenterOffsetY);
            }
            GLES30.glUniform1f(uTimeLoc, sharedTime);
            GLES30.glUniform1f(uReadyMixLoc, uReadyMix);
            if (uChargeMixLoc >= 0) {
                GLES30.glUniform1f(uChargeMixLoc, uChargeMix);
            }
            GLES30.glUniform1f(uPreviewMixLoc, uPreviewMix);
            GLES30.glUniform1f(uExpandProgressLoc, uExpandProgress);
            if (uPulseMorphLoc >= 0) {
                GLES30.glUniform1f(uPulseMorphLoc, uPulseMorph);
            }
            if (uIsDisconnectingLoc >= 0) {
                GLES30.glUniform1f(uIsDisconnectingLoc, uIsDisconnecting);
            }
            GLES30.glUniform1f(uPortalScaleLoc, uPortalScale);
            GLES30.glUniform1f(uGlowIntensityLoc, uGlowIntensity);
            GLES30.glUniform1f(uParticleBrightnessLoc, uParticleBrightness);
            GLES30.glUniform1f(uParticleSizeLoc, uParticleSize);
            GLES30.glUniform1f(uParticleDensityLoc, uParticleDensity);
            GLES30.glUniform1f(uSonarSpeedLoc, uSonarSpeed);
            boolean saver = batterySaverEffective;
            boolean perf = !saver && performanceModeEffective;
            float effectivePulseAmount = saver
                    ? Math.min(uPulseAmount, 3.0f)
                    : (perf ? Math.min(uPulseAmount, 5.0f) : uPulseAmount);
            if (uPulseAmountLoc >= 0) {
                GLES30.glUniform1f(uPulseAmountLoc, effectivePulseAmount);
            }
            if (uPulseBrightnessLoc >= 0) {
                GLES30.glUniform1f(uPulseBrightnessLoc, uPulseBrightness);
            }
            if (uPulseLengthLoc >= 0) {
                GLES30.glUniform1f(uPulseLengthLoc, uPulseLength);
            }
            if (uPulseSpeedLoc >= 0) {
                GLES30.glUniform1f(uPulseSpeedLoc, uPulseSpeed);
            }
            if (uWaveAmpLoc >= 0) {
                GLES30.glUniform1f(uWaveAmpLoc, uWaveAmp);
            }
            uploadColor3f(uBgEdgeLoc, uBgEdgeColor);
            uploadColor3f(uBgCenterLoc, uBgCenterColor);
            uploadColor3f(uBgInnerLoc, uBgInnerColor);
            if (uParticleColsLoc >= 0) {
                fillColor3(particleColsBuf, 0, uParticleNearRgb);
                fillColor3(particleColsBuf, 3, uParticleMidRgb);
                fillColor3(particleColsBuf, 6, uParticleFarRgb);
                GLES30.glUniform3fv(uParticleColsLoc, 3, particleColsBuf, 0);
            }
            uploadColor3f(uPulseColLoc, uPulseRgb);
            uploadColor3f(uRingColLoc, uRingRgb);
            uploadColor3f(uChargeColLoc, uChargeRgb);
            GLES30.glUniform1f(uIsLightLoc, uIsLight);

            for (int layer = 0; layer < 3; layer++) {
                float fl = layer;
                float rotSpeed = (0.04f + fl * 0.018f) * (layer == 1 ? -1.0f : 1.0f);
                double angle = sharedTime * rotSpeed + fl * 1.7;
                float s = (float) Math.sin(angle);
                float c = (float) Math.cos(angle);
                int rIdx = layer * 4;
                layerRotBuf[rIdx] = c;
                layerRotBuf[rIdx + 1] = -s;
                layerRotBuf[rIdx + 2] = s;
                layerRotBuf[rIdx + 3] = c;
                int sIdx = layer * 2;
                layerShiftBuf[sIdx] = (float) Math.sin(sharedTime * 0.15 + fl) * 0.04f;
                layerShiftBuf[sIdx + 1] = (float) Math.cos(sharedTime * 0.12 + fl) * 0.04f;
            }
            if (uLayerRotLoc >= 0) {
                GLES30.glUniform4fv(uLayerRotLoc, 3, layerRotBuf, 0);
            }
            if (uLayerShiftLoc >= 0) {
                GLES30.glUniform2fv(uLayerShiftLoc, 3, layerShiftBuf, 0);
            }
            if (uMaxLayersLoc >= 0) {
                GLES30.glUniform1i(uMaxLayersLoc, saver ? 2 : 3);
            }
            if (uMaxSubParticlesLoc >= 0) {
                GLES30.glUniform1i(uMaxSubParticlesLoc, saver ? 2 : (perf ? 3 : 6));
            }

            // CPU-precomputed per-frame spiral controls & strand uniforms (zero per-pixel pow/smoothstep/hash/trig overhead)
            float m = clampF(uPulseMorph, 0f, 1f);
            boolean isDisc = (uIsDisconnecting > 0.5f);
            float handoffFade = (m <= 0.0005f) ? 1.0f
                    : (isDisc ? (1.0f - smoothstepF(0.90f, 1.0f, m)) : (1.0f - smoothstepF(0.88f, 1.0f, m)));
            int activeStrands = (uPreviewMix >= 0.999f || uPulseBrightness <= 0.001f
                    || effectivePulseAmount <= 0.001f || handoffFade <= 0.001f)
                    ? 0
                    : Math.min(10, (int) Math.ceil(effectivePulseAmount));
            int maxTurns = (saver || perf) ? 3 : 4;
            if (uActiveStrandsLoc >= 0) {
                GLES30.glUniform1i(uActiveStrandsLoc, activeStrands);
            }
            if (uMaxSpiralTurnsLoc >= 0) {
                GLES30.glUniform1i(uMaxSpiralTurnsLoc, maxTurns);
            }
            if (activeStrands > 0) {
                float pi = (float) Math.PI;
                float baseRadius = 0.25f * clampF(uPortalScale, 0.2f, 4.0f);
                float readyT = clampF(uReadyMix, 0f, 1f);
                float waveAmp = uWaveAmp;
                float pulseSpeed = uPulseSpeed;
                double t = sharedTime;
                double tau = Math.PI * 2.0;

                float baseTail = clampF(0.16f * uPulseLength, 0.035f, 0.44f);
                float baseHead = clampF(0.022f * (float) Math.sqrt(Math.max(0.1f, uPulseLength)), 0.008f, 0.06f);
                float pitch;
                float thetaLimit;
                float waveFade;
                float targetBlend;
                float ringFuse;
                float stackNorm;
                float coreBaseW;
                float haloBaseW;
                float curHead;
                float curTail;
                float coreGain;
                float frameSpin;
                float chaseOffset;
                float spanScale;

                if (m <= 0.0005f) {
                    pitch = 0f;
                    thetaLimit = 0.85f * pi;
                    waveFade = 1.0f;
                    targetBlend = 0.0f;
                    ringFuse = 0.0f;
                    stackNorm = 1.0f;
                    coreBaseW = 0.00234f;
                    haloBaseW = 0.02025f;
                    curHead = baseHead;
                    curTail = baseTail;
                    coreGain = 1.10f;
                    frameSpin = 0.0f;
                    chaseOffset = 0.0f;
                    spanScale = 1.0f;
                } else {
                    pitch = isDisc
                            ? ((1.0f - smoothstepF(0.74f, 0.98f, m)) * safePowF(m, 2.0f) * 1.75f * 0.056f)
                            : (smoothstepF(0.08f, 0.36f, m) * safePowF(1.0f - smoothstepF(0.32f, 0.97f, m), 1.45f) * 0.056f);
                    thetaLimit = isDisc
                            ? mixF(0.85f * pi, 3.65f * pi, safePowF(m, 1.85f))
                            : mixF(0.85f * pi, 3.65f * pi, smoothstepF(0.18f, 0.62f, m));
                    waveFade = isDisc ? (1.0f - safePowF(m, 2.2f)) : (1.0f - smoothstepF(0.04f, 0.55f, m));
                    targetBlend = isDisc ? safePowF(m, 2.1f) : smoothstepF(0.0f, 0.55f, m);
                    ringFuse = isDisc ? smoothstepF(0.76f, 0.97f, m) : smoothstepF(0.68f, 0.96f, m);
                    float activeCount = clampF(effectivePulseAmount, 1.0f, 10.0f);
                    stackNorm = isDisc
                            ? mixF(1.0f, 1.0f / Math.max(1.0f, activeCount * 0.58f), smoothstepF(0.62f, 0.97f, m))
                            : mixF(1.0f, 1.0f / Math.max(1.0f, activeCount * 0.58f), smoothstepF(0.55f, 0.96f, m));
                    float glowMorph = isDisc ? safePowF(m, 2.0f) : smoothstepF(0.4f, 1.0f, m);
                    coreBaseW = mixF(0.00234f, 0.0046f, glowMorph);
                    haloBaseW = mixF(0.02025f, 0.0360f, glowMorph);
                    float stretchT = isDisc ? safePowF(m, 2.2f) : smoothstepF(0.40f, 0.92f, m);
                    curTail = mixF(baseTail, 0.46f, stretchT);
                    curHead = mixF(baseHead, 0.14f, stretchT);
                    coreGain = mixF(1.10f, mixF(0.28f, 1.20f, readyT), ringFuse);
                    frameSpin = isDisc ? (1.35f * safePowF(m, 2.8f)) : (-1.35f * easeInOutCubicF(m));
                    chaseOffset = isDisc ? (1.0f - safePowF(m, 2.9f)) : safePowF(m, 1.8f);
                    spanScale = isDisc
                            ? mixF(1.0f, 0.76f, safePowF(m, 2.2f))
                            : mixF(1.0f, 0.72f, smoothstepF(0.35f, 0.90f, m));
                }

                float rejectDist = haloBaseW * 5.5f;
                float maxSpiralDr = pitch * (thetaLimit + 1.25f + 1.20f);
                float dirSign = isDisc ? 1.0f : -1.0f;
                int kStart = (maxTurns <= 3) ? -1 : (isDisc ? -1 : -2);

                spiralCtrlBuf[0] = handoffFade;
                spiralCtrlBuf[1] = pitch;
                spiralCtrlBuf[2] = thetaLimit;
                spiralCtrlBuf[3] = waveFade;
                spiralCtrlBuf[4] = ringFuse;
                spiralCtrlBuf[5] = stackNorm;
                spiralCtrlBuf[6] = coreBaseW;
                spiralCtrlBuf[7] = haloBaseW;
                spiralCtrlBuf[8] = rejectDist;
                spiralCtrlBuf[9] = curHead;
                spiralCtrlBuf[10] = curTail;
                spiralCtrlBuf[11] = coreGain;
                spiralCtrlBuf[12] = maxSpiralDr;
                spiralCtrlBuf[13] = dirSign;
                spiralCtrlBuf[14] = (float) kStart;
                spiralCtrlBuf[15] = 0f;
                if (uSpiralCtrlLoc >= 0) {
                    GLES30.glUniform4fv(uSpiralCtrlLoc, 4, spiralCtrlBuf, 0);
                }

                float tCurve = smoothstepF(0f, 0.45f, m);
                float launchL = 40.0f * safePowF(1.0f - tCurve, 3.4f);
                float discK = safePowF(m, 2.35f);

                for (int i = 0; i < activeStrands; i++) {
                    float fi = (float) i;
                    float rndX = STRAND_RND_X[i];
                    float rndY = STRAND_RND_Y[i];
                    float pathAlpha = clampF(effectivePulseAmount - fi, 0f, 1f);

                    double ang = STRAND_BASE_ANGLES[i]
                            + Math.sin(t * (0.045 + rndX * 0.025) + fi * 1.9) * 0.16 * waveFade
                            + frameSpin * (0.85f + 0.30f * rndX);
                    float sAng = (float) Math.sin(ang);
                    float cAng = (float) Math.cos(ang);

                    float targetR = mixF(STRAND_BASE_OFFSETS[i], baseRadius, targetBlend);
                    float rc;
                    float lVal;
                    if (m <= 0.0005f) {
                        lVal = 40.0f;
                        rc = 40.0f + STRAND_BASE_OFFSETS[i];
                    } else if (isDisc) {
                        float kappa = mixF(1.0f / (40.0f + targetR), 1.0f / Math.max(targetR, 0.05f), discK);
                        rc = 1.0f / kappa;
                        lVal = Math.max(0.0f, rc - targetR);
                    } else {
                        lVal = launchL;
                        rc = lVal + targetR;
                    }

                    float baseSpan = mixF(3.6f, 5.0f, rndY);
                    float invCurSpan = 1.0f / (baseSpan * spanScale);
                    float speed = mixF(0.28f, 0.50f, rndX) * pulseSpeed;
                    double baseFreq = speed / (double) baseSpan;
                    double phaseOff = fi * 0.143 + rndX * 0.4;

                    float f1 = mixF(1.45f, 2.35f, rndX);
                    float f2 = mixF(2.80f, 4.10f, rndY);
                    float a1 = mixF(0.075f, 0.130f, rndY) * waveAmp * waveFade;
                    float a2 = mixF(0.028f, 0.055f, rndX) * waveAmp * waveFade;
                    float driftY = (waveFade > 0.0005f)
                            ? (float) (Math.sin(t * (0.09 + rndY * 0.05) + fi * 2.7) * 0.06 * waveAmp * waveFade)
                            : 0f;

                    float wavePh1 = (float) ((t * (0.24 + rndX * 0.14) + fi * 2.3) % tau);
                    float wavePh2 = (float) ((-t * (0.18 + rndY * 0.12) + fi * 4.1) % tau);
                    double rawComet = -t * baseFreq - chaseOffset + phaseOff;
                    float cometPhase = (float) (rawComet - Math.floor(rawComet));
                    float shimmerGain = (float) (0.90 + 0.10 * Math.sin(t * 3.1 + fi * 2.4)) * pathAlpha;

                    int idx = i * 4;
                    strandRotBuf[idx] = cAng;
                    strandRotBuf[idx + 1] = -sAng;
                    strandRotBuf[idx + 2] = sAng;
                    strandRotBuf[idx + 3] = cAng;

                    strandGeomBuf[idx] = lVal;
                    strandGeomBuf[idx + 1] = rc;
                    strandGeomBuf[idx + 2] = driftY;
                    strandGeomBuf[idx + 3] = invCurSpan;

                    strandWaveBuf[idx] = f1;
                    strandWaveBuf[idx + 1] = f2;
                    strandWaveBuf[idx + 2] = a1;
                    strandWaveBuf[idx + 3] = a2;

                    strandPhaseBuf[idx] = wavePh1;
                    strandPhaseBuf[idx + 1] = wavePh2;
                    strandPhaseBuf[idx + 2] = cometPhase;
                    strandPhaseBuf[idx + 3] = shimmerGain;
                }
                if (uStrandRotLoc >= 0) {
                    GLES30.glUniform4fv(uStrandRotLoc, activeStrands, strandRotBuf, 0);
                }
                if (uStrandGeomLoc >= 0) {
                    GLES30.glUniform4fv(uStrandGeomLoc, activeStrands, strandGeomBuf, 0);
                }
                if (uStrandWaveLoc >= 0) {
                    GLES30.glUniform4fv(uStrandWaveLoc, activeStrands, strandWaveBuf, 0);
                }
                if (uStrandPhaseLoc >= 0) {
                    GLES30.glUniform4fv(uStrandPhaseLoc, activeStrands, strandPhaseBuf, 0);
                }
            }

            quadBuffer.position(0);
            GLES30.glEnableVertexAttribArray(aPosLoc);
            GLES30.glVertexAttribPointer(aPosLoc, 2, GLES30.GL_FLOAT, false, 0, quadBuffer);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6);
            GLES30.glDisableVertexAttribArray(aPosLoc);
        }

        private void fillColor3(float[] buf, int offset, int argb) {
            buf[offset] = ((argb >> 16) & 0xFF) / 255f;
            buf[offset + 1] = ((argb >> 8) & 0xFF) / 255f;
            buf[offset + 2] = (argb & 0xFF) / 255f;
        }

        private void uploadColor3f(int loc, int argb) {
            if (loc < 0) {
                return;
            }
            GLES30.glUniform3f(loc,
                    ((argb >> 16) & 0xFF) / 255f,
                    ((argb >> 8) & 0xFF) / 255f,
                    (argb & 0xFF) / 255f);
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

        private void drawBlurPass(int texId, float stepX, float stepY, float flipY, float saturation) {
            GLES30.glUseProgram(blurProgram);
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId);
            GLES30.glUniform1i(blurTexLoc, 0);
            GLES30.glUniform2f(blurStepLoc, stepX, stepY);
            if (blurFlipYLoc >= 0) {
                GLES30.glUniform1f(blurFlipYLoc, flipY);
            }
            if (blurSatLoc >= 0) {
                GLES30.glUniform1f(blurSatLoc, saturation);
            }

            quadBuffer.position(0);
            GLES30.glEnableVertexAttribArray(blurPosLoc);
            GLES30.glVertexAttribPointer(blurPosLoc, 2, GLES30.GL_FLOAT, false, 0, quadBuffer);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6);
            GLES30.glDisableVertexAttribArray(blurPosLoc);
        }

        private void ensureBlurFbo(int w, int h) {
            if (blurFbo != 0 && blurFboB != 0 && blurW == w && blurH == h) {
                return;
            }
            releaseBlurFbo();
            blurW = w;
            blurH = h;

            int[] tex = new int[2];
            GLES30.glGenTextures(2, tex, 0);
            blurFboTex = tex[0];
            blurFboTexB = tex[1];
            for (int i = 0; i < 2; i++) {
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[i]);
                GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0,
                        GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
            }

            int[] fbo = new int[2];
            GLES30.glGenFramebuffers(2, fbo, 0);
            blurFbo = fbo[0];
            blurFboB = fbo[1];
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, blurFbo);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                    GLES30.GL_TEXTURE_2D, blurFboTex, 0);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, blurFboB);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                    GLES30.GL_TEXTURE_2D, blurFboTexB, 0);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);

            int byteSize = w * h * 4;
            readbackBuf = ByteBuffer.allocateDirect(byteSize).order(ByteOrder.nativeOrder());
            GLES30.glGenBuffers(2, blurPbos, 0);
            for (int i = 0; i < 2; i++) {
                if (blurPbos[i] != 0) {
                    GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, blurPbos[i]);
                    GLES30.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, byteSize, null, GLES30.GL_STREAM_READ);
                }
            }
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
            pboWriteIndex = 0;
            pboPrimed = false;

            for (int i = 0; i < blurBitmapRing.length; i++) {
                blurBitmapRing[i] = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            }
            blurRingIndex = 0;
        }

        private long resolveUiFrameIntervalMs(String fpsPref) {
            if ("max".equalsIgnoreCase(fpsPref)) return 0L;
            if ("120".equals(fpsPref)) return 8L;
            if ("90".equals(fpsPref)) return 11L;
            if ("30".equals(fpsPref)) return 33L;
            return 16L; // default 60 FPS
        }

        private void syncReadbackFallback(int tw, int th) {
            readbackBuf.position(0);
            GLES30.glReadPixels(0, 0, tw, th, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, readbackBuf);
            readbackBuf.position(0);
            Bitmap target = blurBitmapRing[blurRingIndex];
            blurRingIndex = (blurRingIndex + 1) % blurBitmapRing.length;
            target.copyPixelsFromBuffer(readbackBuf);
            publishedBlurBitmap = target;
            mainHandler.post(invalidateFrostedRunnable);
        }

        private void updateBlurBitmapOnGlThread(int viewW, int viewH, int blurPct, int blurResPct, boolean perfActive) {
            boolean landscape = viewW >= viewH;
            int minView = Math.max(1, Math.min(viewW, viewH));
            int maxView = Math.max(minView, Math.max(viewW, viewH));
            float aspect = Math.max(1.0f, Math.min(2.6f, maxView / (float) minView));

            float resFraction = Math.max(0.05f, Math.min(0.50f, blurResPct / 100f));
            if (perfActive) {
                resFraction *= 0.75f;
            }
            int shortEdge = Math.max(24, (Math.round(minView * resFraction) + 1) & ~1);
            int longEdge = Math.max(shortEdge, (Math.round(shortEdge * aspect) + 1) & ~1);
            int tw = landscape ? longEdge : shortEdge;
            int th = landscape ? shortEdge : longEdge;
            ensureBlurFbo(tw, th);

            // 1. Downsample bgFboTex into adaptive-resolution blurFbo
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, blurFbo);
            GLES30.glViewport(0, 0, tw, th);
            drawBlitTexture(bgFboTex);

            // 2. GPU 9-tap separable Gaussian blur passes scaled to keep perceived blur continuous.
            // Performance Mode uses a single separable pass (1xH + 1xV) to retain live saturated
            // frost at ~50% lower blur GPU cost; Standard Mode uses two passes for ultra-smooth gradients.
            // On the final vertical pass, flip Y on the GPU so glReadPixels directly matches Android top-left origin!
            float normRadius = (blurPct / 100f) * 0.024f;
            float texelRadius = Math.max(0.35f, normRadius * shortEdge);
            float finalSat = Math.max(0f, Math.min(5f, blurSaturationPercent / 100f));
            float[] passScales = perfActive ? new float[]{ 0.48f } : new float[]{ 0.28f, 0.56f };
            for (int pIdx = 0; pIdx < passScales.length; pIdx++) {
                float step = texelRadius * passScales[pIdx];
                boolean isLastVertical = (pIdx == passScales.length - 1);
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, blurFboB);
                drawBlurPass(blurFboTex, step / tw, 0f, 0f, 1.0f);
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, blurFbo);
                drawBlurPass(blurFboTexB, 0f, step / th, isLastVertical ? 1f : 0f, isLastVertical ? finalSat : 1.0f);
            }

            // 3. Double-buffered async GLES 3.0 PBO readback (maps previous frame DMA with zero CPU stall)
            int writeIdx = pboWriteIndex;
            int readIdx = 1 - writeIdx;
            if (blurPbos[0] != 0 && blurPbos[1] != 0) {
                GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, blurPbos[writeIdx]);
                GLES30.glReadPixels(0, 0, tw, th, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, 0);
                GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);

                if (pboPrimed) {
                    int byteSize = tw * th * 4;
                    GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, blurPbos[readIdx]);
                    java.nio.Buffer mapped = GLES30.glMapBufferRange(
                            GLES30.GL_PIXEL_PACK_BUFFER, 0, byteSize, GLES30.GL_MAP_READ_BIT);
                    if (mapped instanceof ByteBuffer) {
                        ByteBuffer bb = (ByteBuffer) mapped;
                        bb.order(ByteOrder.nativeOrder());
                        bb.position(0);
                        Bitmap target = blurBitmapRing[blurRingIndex];
                        blurRingIndex = (blurRingIndex + 1) % blurBitmapRing.length;
                        target.copyPixelsFromBuffer(bb);
                        GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER);
                        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
                        publishedBlurBitmap = target;
                        mainHandler.post(invalidateFrostedRunnable);
                    } else {
                        GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER);
                        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
                        syncReadbackFallback(tw, th);
                    }
                } else if (publishedBlurBitmap == null) {
                    syncReadbackFallback(tw, th);
                }
                pboPrimed = true;
                pboWriteIndex = readIdx;
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
            } else {
                syncReadbackFallback(tw, th);
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
            }
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
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
            if (blurPbos[0] != 0 || blurPbos[1] != 0) {
                GLES30.glDeleteBuffers(2, blurPbos, 0);
                blurPbos[0] = 0;
                blurPbos[1] = 0;
            }
            pboPrimed = false;
            pboWriteIndex = 0;
            if (blurFbo != 0) {
                GLES30.glDeleteFramebuffers(1, new int[]{blurFbo}, 0);
                blurFbo = 0;
            }
            if (blurFboB != 0) {
                GLES30.glDeleteFramebuffers(1, new int[]{blurFboB}, 0);
                blurFboB = 0;
            }
            if (blurFboTex != 0) {
                GLES30.glDeleteTextures(1, new int[]{blurFboTex}, 0);
                blurFboTex = 0;
            }
            if (blurFboTexB != 0) {
                GLES30.glDeleteTextures(1, new int[]{blurFboTexB}, 0);
                blurFboTexB = 0;
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
            blurProgram = linkProgram(BLUR_VERTEX_SHADER_SRC, BLUR_FRAGMENT_SHADER_SRC);
            if (blurProgram == 0) {
                return false;
            }

            aPosLoc = GLES30.glGetAttribLocation(program, "a_pos");
            uResolutionLoc = GLES30.glGetUniformLocation(program, "u_resolution");
            uPortalCenterOffsetLoc = GLES30.glGetUniformLocation(program, "u_portalCenterOffset");
            uTimeLoc = GLES30.glGetUniformLocation(program, "u_time");
            uReadyMixLoc = GLES30.glGetUniformLocation(program, "u_readyMix");
            uChargeMixLoc = GLES30.glGetUniformLocation(program, "u_chargeMix");
            uPreviewMixLoc = GLES30.glGetUniformLocation(program, "u_previewMix");
            uExpandProgressLoc = GLES30.glGetUniformLocation(program, "u_expandProgress");
            uPulseMorphLoc = GLES30.glGetUniformLocation(program, "u_pulseMorph");
            uIsDisconnectingLoc = GLES30.glGetUniformLocation(program, "u_isDisconnecting");
            uPortalScaleLoc = GLES30.glGetUniformLocation(program, "u_portalScale");
            uGlowIntensityLoc = GLES30.glGetUniformLocation(program, "u_glowIntensity");
            uParticleBrightnessLoc = GLES30.glGetUniformLocation(program, "u_particleBrightness");
            uParticleSizeLoc = GLES30.glGetUniformLocation(program, "u_particleSize");
            uParticleDensityLoc = GLES30.glGetUniformLocation(program, "u_particleDensity");
            uSonarSpeedLoc = GLES30.glGetUniformLocation(program, "u_sonarSpeed");
            uPulseAmountLoc = GLES30.glGetUniformLocation(program, "u_pulseAmount");
            uPulseBrightnessLoc = GLES30.glGetUniformLocation(program, "u_pulseBrightness");
            uPulseLengthLoc = GLES30.glGetUniformLocation(program, "u_pulseLength");
            uPulseSpeedLoc = GLES30.glGetUniformLocation(program, "u_pulseSpeed");
            uWaveAmpLoc = GLES30.glGetUniformLocation(program, "u_waveAmp");
            uBgEdgeLoc = GLES30.glGetUniformLocation(program, "u_bgEdge");
            uBgCenterLoc = GLES30.glGetUniformLocation(program, "u_bgCenter");
            uBgInnerLoc = GLES30.glGetUniformLocation(program, "u_bgInner");
            uParticleColsLoc = GLES30.glGetUniformLocation(program, "u_particleCols");
            uPulseColLoc = GLES30.glGetUniformLocation(program, "u_pulseCol");
            uRingColLoc = GLES30.glGetUniformLocation(program, "u_ringCol");
            uChargeColLoc = GLES30.glGetUniformLocation(program, "u_chargeCol");
            uIsLightLoc = GLES30.glGetUniformLocation(program, "u_isLight");
            uLayerRotLoc = GLES30.glGetUniformLocation(program, "u_layerRot");
            uLayerShiftLoc = GLES30.glGetUniformLocation(program, "u_layerShift");
            uMaxLayersLoc = GLES30.glGetUniformLocation(program, "u_maxLayers");
            uMaxSubParticlesLoc = GLES30.glGetUniformLocation(program, "u_maxSubParticles");
            uActiveStrandsLoc = GLES30.glGetUniformLocation(program, "u_activeStrands");
            uStrandRotLoc = GLES30.glGetUniformLocation(program, "u_strandRot");
            uStrandGeomLoc = GLES30.glGetUniformLocation(program, "u_strandGeom");
            uStrandWaveLoc = GLES30.glGetUniformLocation(program, "u_strandWave");
            uStrandPhaseLoc = GLES30.glGetUniformLocation(program, "u_strandPhase");
            uMaxSpiralTurnsLoc = GLES30.glGetUniformLocation(program, "u_maxSpiralTurns");
            uSpiralCtrlLoc = GLES30.glGetUniformLocation(program, "u_spiralCtrl");

            blitPosLoc = GLES30.glGetAttribLocation(blitProgram, "a_pos");
            blitTexLoc = GLES30.glGetUniformLocation(blitProgram, "u_tex");

            blurPosLoc = GLES30.glGetAttribLocation(blurProgram, "a_pos");
            blurTexLoc = GLES30.glGetUniformLocation(blurProgram, "u_tex");
            blurStepLoc = GLES30.glGetUniformLocation(blurProgram, "u_texelStep");
            blurFlipYLoc = GLES30.glGetUniformLocation(blurProgram, "u_flipY");
            blurSatLoc = GLES30.glGetUniformLocation(blurProgram, "u_saturation");

            quadBuffer = ByteBuffer.allocateDirect(FULLSCREEN_QUAD.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            quadBuffer.put(FULLSCREEN_QUAD).position(0);

            GLES30.glDisable(GLES30.GL_BLEND);
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
            if (blurProgram != 0) {
                GLES30.glDeleteProgram(blurProgram);
                blurProgram = 0;
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
