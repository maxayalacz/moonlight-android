package com.limelight.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Real-time Midnight Blue bokeh particle void matching the interior of the
 * PlayStation Portal connection window, plus a live downscaled blurred buffer
 * for {@link FrostedCardDrawable} and {@link FocusCirclePortalView}.
 */
public class FlowingAuroraView extends View {
    public enum CornerMode {
        ALL,
        TOP,
        MIDDLE,
        BOTTOM
    }

    private static final int FIELD_W = 80;
    private static final int FIELD_H = 128;
    private static final int BLUR_RADIUS = 3;
    private static final long BLUR_INTERVAL_MS = 33L; // ~30 FPS frosted refraction updates
    private static final float TAU = (float) (Math.PI * 2.0);

    // Exact colors from inside the PS Portal animation window
    private static final int COLOR_CENTER_GLOW = 0xFF181E36;
    private static final int COLOR_MID_VOID = 0xFF0E1224;
    private static final int COLOR_EDGE_VOID = 0xFF0B0E1C;

    // Bokeh particle colors: vec3(0.369, 0.420, 1.0) -> vec3(0.780, 0.847, 1.0)
    private static final int PARTICLE_COL_A = 0xFF5E6BFF;
    private static final int PARTICLE_COL_B = 0xFFC7D8FF;

    private static final int[] LAYER_COUNTS = { 26, 38, 48 };
    private static final float[] LAYER_ROT_SPEED = { 0.040f, -0.058f, 0.076f };
    private static final float[] LAYER_OPACITY = { 0.50f, 0.38f, 0.28f };
    private static final float[] LAYER_MIN_R_FRAC = { 0.011f, 0.0075f, 0.0048f };
    private static final float[] LAYER_MAX_R_FRAC = { 0.028f, 0.019f, 0.012f };

    private static final class BokehParticle {
        final int layer;
        final float baseX;       // normalized [-0.95..0.95]
        final float baseY;       // normalized [-0.95..0.95]
        final float driftAmpX;
        final float driftAmpY;
        final float driftFreqX;
        final float driftFreqY;
        final float driftPhaseX;
        final float driftPhaseY;
        final float radiusFrac;
        final float twinkleFreq;
        final float twinklePhase;
        final int colorRgb;
        final PorterDuffColorFilter tintFilter;

        BokehParticle(int layer, Random rnd) {
            this.layer = layer;
            this.baseX = (rnd.nextFloat() * 2f - 1f) * 0.92f;
            this.baseY = (rnd.nextFloat() * 2f - 1f) * 0.92f;
            this.driftAmpX = 0.012f + rnd.nextFloat() * 0.018f;
            this.driftAmpY = 0.012f + rnd.nextFloat() * 0.018f;
            this.driftFreqX = 0.35f + rnd.nextFloat() * 0.50f;
            this.driftFreqY = 0.35f + rnd.nextFloat() * 0.50f;
            this.driftPhaseX = rnd.nextFloat() * TAU;
            this.driftPhaseY = rnd.nextFloat() * TAU;
            this.radiusFrac = LAYER_MIN_R_FRAC[layer]
                    + rnd.nextFloat() * (LAYER_MAX_R_FRAC[layer] - LAYER_MIN_R_FRAC[layer]);
            this.twinkleFreq = 1.6f + rnd.nextFloat() * 2.8f;
            this.twinklePhase = rnd.nextFloat() * TAU;

            float t = rnd.nextFloat();
            this.colorRgb = blendRgb(PARTICLE_COL_A, PARTICLE_COL_B, t);
            this.tintFilter = new PorterDuffColorFilter(this.colorRgb, PorterDuff.Mode.SRC_IN);
        }
    }

    private final AppThemePalette palette = AppThemePalette.MIDNIGHT;
    private final BokehParticle[] particles;
    private final Bitmap bokehSprite;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spritePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF drawRect = new RectF();

    // Live blurred offscreen buffer for FrostedCardDrawable & FocusCirclePortalView
    private final Bitmap fieldBitmap;
    private final Bitmap blurredBitmap;
    private final Canvas fieldCanvas;
    private final int[] blurPxA = new int[FIELD_W * FIELD_H];
    private final int[] blurPxB = new int[FIELD_W * FIELD_H];
    private final Paint fieldBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private long startTimeMs = 0L;
    private long lastBlurMs = 0L;
    private boolean animating = false;
    private int cachedW = 0;
    private int cachedH = 0;

    private final CopyOnWriteArrayList<WeakReference<Drawable>> frostedListeners = new CopyOnWriteArrayList<>();

    public FlowingAuroraView(Context context) {
        this(context, null);
    }

    public FlowingAuroraView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FlowingAuroraView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);

        int total = LAYER_COUNTS[0] + LAYER_COUNTS[1] + LAYER_COUNTS[2];
        particles = new BokehParticle[total];
        Random rnd = new Random(SystemClock.elapsedRealtimeNanos() ^ System.identityHashCode(this));
        int idx = 0;
        for (int layer = 0; layer < 3; layer++) {
            for (int i = 0; i < LAYER_COUNTS[layer]; i++) {
                particles[idx++] = new BokehParticle(layer, rnd);
            }
        }

        bokehSprite = createBokehSprite(64);
        fieldBitmap = Bitmap.createBitmap(FIELD_W, FIELD_H, Bitmap.Config.ARGB_8888);
        blurredBitmap = Bitmap.createBitmap(FIELD_W, FIELD_H, Bitmap.Config.ARGB_8888);
        fieldCanvas = new Canvas(fieldBitmap);
        fieldBgPaint.setShader(new RadialGradient(
                FIELD_W * 0.5f, FIELD_H * 0.48f, Math.max(FIELD_W, FIELD_H) * 0.72f,
                new int[]{ COLOR_CENTER_GLOW, COLOR_MID_VOID, COLOR_EDGE_VOID },
                new float[]{ 0f, 0.48f, 1f },
                Shader.TileMode.CLAMP));

        startTimeMs = SystemClock.uptimeMillis();
        renderBlurField(0f);
    }

    private static Bitmap createBokehSprite(int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float half = size * 0.5f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(
                half, half, half,
                new int[]{ 0xFFFFFFFF, 0xE6FFFFFF, 0x55FFFFFF, 0x00FFFFFF },
                new float[]{ 0f, 0.25f, 0.62f, 1f },
                Shader.TileMode.CLAMP));
        c.drawCircle(half, half, half, p);
        return bmp;
    }

    private static int blendRgb(int c1, int c2, float t) {
        int r = Math.round(Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t);
        int g = Math.round(Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t);
        int b = Math.round(Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t);
        return Color.rgb(r, g, b);
    }

    public void refreshFromPrefs() {
        invalidate();
    }

    public void setForceWaves(boolean force) {
        // Unified PS Portal particle void is now used everywhere.
    }

    public Bitmap getBlurredFieldBitmap() {
        return blurredBitmap;
    }

    public void registerFrostedDrawable(Drawable drawable) {
        if (drawable == null) {
            return;
        }
        frostedListeners.add(new WeakReference<>(drawable));
    }

    public void unregisterFrostedDrawable(Drawable drawable) {
        Iterator<WeakReference<Drawable>> it = frostedListeners.iterator();
        while (it.hasNext()) {
            WeakReference<Drawable> ref = it.next();
            Drawable d = ref.get();
            if (d == null || d == drawable) {
                frostedListeners.remove(ref);
            }
        }
    }

    private void notifyFrostedListeners() {
        for (WeakReference<Drawable> ref : frostedListeners) {
            Drawable d = ref.get();
            if (d == null) {
                frostedListeners.remove(ref);
            } else {
                d.invalidateSelf();
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cachedW = w;
        cachedH = h;
        if (w > 0 && h > 0) {
            float maxDim = (float) Math.hypot(w * 0.5f, h * 0.5f);
            bgPaint.setShader(new RadialGradient(
                    w * 0.5f, h * 0.48f, Math.max(1f, maxDim),
                    new int[]{ COLOR_CENTER_GLOW, COLOR_MID_VOID, COLOR_EDGE_VOID },
                    new float[]{ 0f, 0.52f, 1f },
                    Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animating = true;
        if (startTimeMs == 0L) {
            startTimeMs = SystemClock.uptimeMillis();
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        animating = false;
        frostedListeners.clear();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        animating = (visibility == VISIBLE) && isAttachedToWindow();
        if (animating) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        long nowMs = SystemClock.uptimeMillis();
        float timeSec = (nowMs - startTimeMs) * 0.001f;

        // 1. Deep midnight radial void matching the PS Portal window interior
        canvas.drawRect(0f, 0f, w, h, bgPaint);

        // 2. 3 parallax rotating/drifting/twinkling bokeh layers
        drawParticlesOnCanvas(canvas, w, h, timeSec, 1.0f);

        // 3. Refresh blurred buffer for live frosted glass cards at ~30 FPS
        if (nowMs - lastBlurMs >= BLUR_INTERVAL_MS) {
            lastBlurMs = nowMs;
            renderBlurField(timeSec);
            notifyFrostedListeners();
        }

        if (animating) {
            postInvalidateOnAnimation();
        }
    }

    private void drawParticlesOnCanvas(Canvas canvas, float w, float h, float timeSec, float radiusBoost) {
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float span = (float) Math.hypot(w, h) * 0.58f;
        float minDim = Math.min(w, h);

        float[] cosL = new float[3];
        float[] sinL = new float[3];
        float[] driftLayerX = new float[3];
        float[] driftLayerY = new float[3];
        for (int l = 0; l < 3; l++) {
            float angle = timeSec * LAYER_ROT_SPEED[l] + l * 1.7f;
            cosL[l] = (float) Math.cos(angle);
            sinL[l] = (float) Math.sin(angle);
            driftLayerX[l] = (float) Math.sin(timeSec * 0.15f + l) * 0.035f;
            driftLayerY[l] = (float) Math.cos(timeSec * 0.12f + l) * 0.035f;
        }

        for (int i = 0; i < particles.length; i++) {
            BokehParticle p = particles[i];
            int l = p.layer;

            float lx = p.baseX + (float) Math.sin(timeSec * p.driftFreqX + p.driftPhaseX) * p.driftAmpX + driftLayerX[l];
            float ly = p.baseY + (float) Math.cos(timeSec * p.driftFreqY + p.driftPhaseY) * p.driftAmpY + driftLayerY[l];

            float rx = lx * cosL[l] - ly * sinL[l];
            float ry = lx * sinL[l] + ly * cosL[l];

            float px = cx + rx * span;
            float py = cy + ry * span;
            float r = p.radiusFrac * minDim * radiusBoost;

            if (px + r < 0f || px - r > w || py + r < 0f || py - r > h) {
                continue;
            }

            float twinkle = 0.55f + 0.45f * (float) Math.sin(timeSec * p.twinkleFreq + p.twinklePhase);
            int alpha = Math.max(0, Math.min(255, Math.round(twinkle * LAYER_OPACITY[l] * 255f)));
            if (alpha <= 2) {
                continue;
            }

            spritePaint.setColorFilter(p.tintFilter);
            spritePaint.setAlpha(alpha);
            drawRect.set(px - r, py - r, px + r, py + r);
            canvas.drawBitmap(bokehSprite, null, drawRect, spritePaint);
        }
    }

    private void renderBlurField(float timeSec) {
        fieldCanvas.drawRect(0f, 0f, FIELD_W, FIELD_H, fieldBgPaint);
        drawParticlesOnCanvas(fieldCanvas, FIELD_W, FIELD_H, timeSec, 1.35f);

        fieldBitmap.getPixels(blurPxA, 0, FIELD_W, 0, 0, FIELD_W, FIELD_H);
        boxBlurHorizontal(blurPxA, blurPxB, FIELD_W, FIELD_H, BLUR_RADIUS);
        boxBlurVertical(blurPxB, blurPxA, FIELD_W, FIELD_H, BLUR_RADIUS);
        blurredBitmap.setPixels(blurPxA, 0, FIELD_W, 0, 0, FIELD_W, FIELD_H);
    }

    private static void boxBlurHorizontal(int[] src, int[] dst, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int px = src[row + Math.max(0, Math.min(w - 1, i))];
                sr += (px >> 16) & 0xFF;
                sg += (px >> 8) & 0xFF;
                sb += px & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                dst[row + x] = 0xFF000000 | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int outPx = src[row + Math.max(0, x - r)];
                int inPx = src[row + Math.min(w - 1, x + r + 1)];
                sr += ((inPx >> 16) & 0xFF) - ((outPx >> 16) & 0xFF);
                sg += ((inPx >> 8) & 0xFF) - ((outPx >> 8) & 0xFF);
                sb += (inPx & 0xFF) - (outPx & 0xFF);
            }
        }
    }

    private static void boxBlurVertical(int[] src, int[] dst, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int x = 0; x < w; x++) {
            int sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int px = src[Math.max(0, Math.min(h - 1, i)) * w + x];
                sr += (px >> 16) & 0xFF;
                sg += (px >> 8) & 0xFF;
                sb += px & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                dst[y * w + x] = 0xFF000000 | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int outPx = src[Math.max(0, y - r) * w + x];
                int inPx = src[Math.min(h - 1, y + r + 1) * w + x];
                sr += ((inPx >> 16) & 0xFF) - ((outPx >> 16) & 0xFF);
                sg += ((inPx >> 8) & 0xFF) - ((outPx >> 8) & 0xFF);
                sb += (inPx & 0xFF) - (outPx & 0xFF);
            }
        }
    }

    /**
     * Drawable that renders a real-time dark frosted glass slice of the underlying
     * {@link FlowingAuroraView} at the target View's exact screen coordinates.
     */
    public static class FrostedCardDrawable extends Drawable {
        private final WeakReference<View> hostViewRef;
        private final WeakReference<FlowingAuroraView> auroraRef;
        private final CornerMode cornerMode;
        private final float cornerRadiusPx;
        private final int frostTintColor;
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clipPath = new Path();
        private final RectF rectF = new RectF();
        private final Rect srcRect = new Rect();
        private final int[] hostLoc = new int[2];
        private final int[] auroraLoc = new int[2];
        private boolean isHighlighted = false;

        public FrostedCardDrawable(View hostView, FlowingAuroraView auroraView,
                                   CornerMode cornerMode, float cornerRadiusPx,
                                   int frostTintColor, float strokeWidthPx) {
            this.hostViewRef = new WeakReference<>(hostView);
            this.auroraRef = new WeakReference<>(auroraView);
            this.cornerMode = cornerMode;
            this.cornerRadiusPx = cornerRadiusPx;
            this.frostTintColor = frostTintColor;
            this.tintPaint.setColor(frostTintColor);
            this.strokePaint.setStyle(Paint.Style.STROKE);
            this.strokePaint.setStrokeWidth(strokeWidthPx);
            this.strokePaint.setColor(0x42FFFFFF);
            auroraView.registerFrostedDrawable(this);
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(@NonNull int[] stateSet) {
            boolean active = false;
            for (int state : stateSet) {
                if (state == android.R.attr.state_pressed
                        || state == android.R.attr.state_focused
                        || state == android.R.attr.state_selected
                        || state == android.R.attr.state_hovered) {
                    active = true;
                    break;
                }
            }
            if (active != isHighlighted) {
                isHighlighted = active;
                FlowingAuroraView aurora = auroraRef.get();
                if (active && aurora != null) {
                    tintPaint.setColor(AppThemePalette.withAlpha(aurora.palette.accent, 0.24f));
                    strokePaint.setColor(AppThemePalette.withAlpha(aurora.palette.accentLight, 0.90f));
                } else {
                    tintPaint.setColor(active ? 0x681A2438 : frostTintColor);
                    strokePaint.setColor(active ? 0x9958A6FF : 0x42FFFFFF);
                }
                invalidateSelf();
                return true;
            }
            return false;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) {
                return;
            }
            rectF.set(bounds);
            float[] radii;
            switch (cornerMode) {
                case TOP:
                    radii = new float[]{cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, 0f, 0f, 0f, 0f};
                    break;
                case BOTTOM:
                    radii = new float[]{0f, 0f, 0f, 0f, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx};
                    break;
                case MIDDLE:
                    radii = new float[]{0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f};
                    break;
                case ALL:
                default:
                    radii = new float[]{cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx,
                            cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx};
                    break;
            }

            clipPath.reset();
            clipPath.addRoundRect(rectF, radii, Path.Direction.CW);

            int saveCount = canvas.save();
            canvas.clipPath(clipPath);

            View host = hostViewRef.get();
            FlowingAuroraView aurora = auroraRef.get();
            if (host != null && aurora != null && aurora.getWidth() > 0 && aurora.getHeight() > 0) {
                host.getLocationInWindow(hostLoc);
                aurora.getLocationInWindow(auroraLoc);
                int relX = hostLoc[0] - auroraLoc[0];
                int relY = hostLoc[1] - auroraLoc[1];
                float sx = FIELD_W / (float) aurora.getWidth();
                float sy = FIELD_H / (float) aurora.getHeight();
                srcRect.set(
                        Math.max(0, Math.round(relX * sx)),
                        Math.max(0, Math.round(relY * sy)),
                        Math.min(FIELD_W, Math.round((relX + bounds.width()) * sx)),
                        Math.min(FIELD_H, Math.round((relY + bounds.height()) * sy)));
                if (!srcRect.isEmpty()) {
                    canvas.drawBitmap(aurora.blurredBitmap, srcRect, rectF, bitmapPaint);
                }
            }

            // Dark midnight glass tint overlay
            canvas.drawRect(rectF, tintPaint);
            canvas.restoreToCount(saveCount);

            // Crisp specular rim
            float halfStroke = strokePaint.getStrokeWidth() * 0.5f;
            rectF.inset(halfStroke, halfStroke);
            clipPath.reset();
            clipPath.addRoundRect(rectF, radii, Path.Direction.CW);
            canvas.drawPath(clipPath, strokePaint);
        }

        @Override
        public void setAlpha(int alpha) {
            bitmapPaint.setAlpha(alpha);
            tintPaint.setAlpha(alpha);
            strokePaint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            bitmapPaint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
