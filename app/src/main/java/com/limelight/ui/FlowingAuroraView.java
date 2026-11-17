package com.limelight.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hardware-accelerated flowing dark atmospheric backdrop with vivid cyan/blue/violet
 * light waves and orbs, plus a shared pre-blurred light-field buffer for real-time
 * frosted glass cards, pills, and Settings sections.
 */
public class FlowingAuroraView extends View {
    private static final long CYCLE_DURATION_MS = 16000L;
    private static final int BLUR_BUF_W = 96;
    private static final int BLUR_BUF_H = 192;

    public enum CornerMode {
        ALL,
        TOP,
        MIDDLE,
        BOTTOM
    }

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orbPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orbPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orbPaint3 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wavePath1 = new Path();
    private final Path wavePath2 = new Path();
    private final Matrix shaderMatrix = new Matrix();

    private RadialGradient orbGrad1;
    private RadialGradient orbGrad2;
    private RadialGradient orbGrad3;
    private float phase = 0f;
    private ValueAnimator animator;

    private final Bitmap rawField = Bitmap.createBitmap(BLUR_BUF_W, BLUR_BUF_H, Bitmap.Config.ARGB_8888);
    private final Bitmap blurredField = Bitmap.createBitmap(BLUR_BUF_W, BLUR_BUF_H, Bitmap.Config.ARGB_8888);
    private final Canvas rawCanvas = new Canvas(rawField);
    private final int[] blurPix = new int[BLUR_BUF_W * BLUR_BUF_H];
    private final int[] blurTmp = new int[BLUR_BUF_W * BLUR_BUF_H];
    private boolean fieldDirty = true;

    private final CopyOnWriteArrayList<WeakReference<Drawable>> frostedListeners = new CopyOnWriteArrayList<>();

    public FlowingAuroraView(Context context) {
        super(context);
        init();
    }

    public FlowingAuroraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        basePaint.setColor(0xFF050811);

        wavePaint1.setStyle(Paint.Style.STROKE);
        wavePaint1.setStrokeCap(Paint.Cap.ROUND);

        wavePaint2.setStyle(Paint.Style.STROKE);
        wavePaint2.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) {
            return;
        }
        float minDim = Math.min(w, h);

        // Vivid electric blue orb
        orbGrad1 = new RadialGradient(0f, 0f, minDim * 0.62f,
                new int[]{0xCC2563EB, 0x551D4ED8, 0x00050811},
                new float[]{0f, 0.48f, 1f},
                Shader.TileMode.CLAMP);
        orbPaint1.setShader(orbGrad1);

        // Vivid cyan orb
        orbGrad2 = new RadialGradient(0f, 0f, minDim * 0.56f,
                new int[]{0xBF06B6D4, 0x4D0891B2, 0x00050811},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP);
        orbPaint2.setShader(orbGrad2);

        // Vivid violet orb
        orbGrad3 = new RadialGradient(0f, 0f, minDim * 0.54f,
                new int[]{0xB38B5CF6, 0x446D28D9, 0x00050811},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP);
        orbPaint3.setShader(orbGrad3);

        wavePaint1.setStrokeWidth(minDim * 0.18f);
        wavePaint1.setShader(new LinearGradient(0f, 0f, w, h,
                new int[]{0x8800E5FF, 0x993B82F6, 0x778B5CF6},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));

        wavePaint2.setStrokeWidth(minDim * 0.14f);
        wavePaint2.setShader(new LinearGradient(w, 0f, 0f, h,
                new int[]{0x77A855F7, 0x8806B6D4, 0x662563EB},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));

        fieldDirty = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        drawAuroraScene(canvas, w, h);
    }

    private void drawAuroraScene(Canvas canvas, int w, int h) {
        canvas.drawRect(0f, 0f, w, h, basePaint);

        if (orbGrad1 != null) {
            float x1 = w * (0.24f + 0.24f * (float) Math.sin(phase));
            float y1 = h * (0.22f + 0.18f * (float) Math.cos(phase * 0.8f));
            shaderMatrix.setTranslate(x1, y1);
            orbGrad1.setLocalMatrix(shaderMatrix);
            canvas.drawRect(0f, 0f, w, h, orbPaint1);
        }

        if (orbGrad2 != null) {
            float x2 = w * (0.76f + 0.22f * (float) Math.cos(phase + 1.1f));
            float y2 = h * (0.66f + 0.20f * (float) Math.sin(phase * 1.1f + 0.6f));
            shaderMatrix.setTranslate(x2, y2);
            orbGrad2.setLocalMatrix(shaderMatrix);
            canvas.drawRect(0f, 0f, w, h, orbPaint2);
        }

        if (orbGrad3 != null) {
            float x3 = w * (0.48f + 0.28f * (float) Math.sin(phase * 0.7f + 2.3f));
            float y3 = h * (0.46f + 0.26f * (float) Math.cos(phase * 1.2f + 1.7f));
            shaderMatrix.setTranslate(x3, y3);
            orbGrad3.setLocalMatrix(shaderMatrix);
            canvas.drawRect(0f, 0f, w, h, orbPaint3);
        }

        // Flowing luminous wave 1
        float yWave1Start = h * (0.28f + 0.12f * (float) Math.sin(phase));
        float yWave1Mid1 = h * (0.14f + 0.18f * (float) Math.cos(phase * 1.2f));
        float yWave1Mid2 = h * (0.58f + 0.16f * (float) Math.sin(phase + 1.8f));
        float yWave1End = h * (0.40f + 0.14f * (float) Math.cos(phase * 0.9f));
        wavePath1.reset();
        wavePath1.moveTo(-w * 0.15f, yWave1Start);
        wavePath1.cubicTo(w * 0.28f, yWave1Mid1, w * 0.68f, yWave1Mid2, w * 1.15f, yWave1End);
        canvas.drawPath(wavePath1, wavePaint1);

        // Flowing luminous wave 2
        float yWave2Start = h * (0.72f + 0.12f * (float) Math.cos(phase + 0.8f));
        float yWave2Mid1 = h * (0.48f + 0.18f * (float) Math.sin(phase * 1.1f + 2.1f));
        float yWave2Mid2 = h * (0.86f + 0.12f * (float) Math.cos(phase + 2.7f));
        float yWave2End = h * (0.64f + 0.16f * (float) Math.sin(phase * 0.8f));
        wavePath2.reset();
        wavePath2.moveTo(-w * 0.15f, yWave2Start);
        wavePath2.cubicTo(w * 0.32f, yWave2Mid1, w * 0.72f, yWave2Mid2, w * 1.15f, yWave2End);
        canvas.drawPath(wavePath2, wavePaint2);
    }

    public Bitmap getBlurredFieldBitmap() {
        if (fieldDirty && getWidth() > 0 && getHeight() > 0) {
            fieldDirty = false;
            rawCanvas.save();
            float sx = BLUR_BUF_W / (float) getWidth();
            float sy = BLUR_BUF_H / (float) getHeight();
            rawCanvas.scale(sx, sy);
            drawAuroraScene(rawCanvas, getWidth(), getHeight());
            rawCanvas.restore();

            // Fast 2-pass box blur on 96x192 buffer -> silky frosted diffusion
            rawField.getPixels(blurPix, 0, BLUR_BUF_W, 0, 0, BLUR_BUF_W, BLUR_BUF_H);
            int radius = 7;
            int div = radius * 2 + 1;
            for (int pass = 0; pass < 2; pass++) {
                for (int y = 0; y < BLUR_BUF_H; y++) {
                    int row = y * BLUR_BUF_W;
                    for (int x = 0; x < BLUR_BUF_W; x++) {
                        int r = 0, g = 0, b = 0;
                        for (int k = -radius; k <= radius; k++) {
                            int sxIdx = Math.max(0, Math.min(BLUR_BUF_W - 1, x + k));
                            int c = blurPix[row + sxIdx];
                            r += (c >> 16) & 0xFF;
                            g += (c >> 8) & 0xFF;
                            b += c & 0xFF;
                        }
                        blurTmp[row + x] = 0xFF000000 | ((r / div) << 16) | ((g / div) << 8) | (b / div);
                    }
                }
                for (int x = 0; x < BLUR_BUF_W; x++) {
                    for (int y = 0; y < BLUR_BUF_H; y++) {
                        int r = 0, g = 0, b = 0;
                        for (int k = -radius; k <= radius; k++) {
                            int syIdx = Math.max(0, Math.min(BLUR_BUF_H - 1, y + k));
                            int c = blurTmp[syIdx * BLUR_BUF_W + x];
                            r += (c >> 16) & 0xFF;
                            g += (c >> 8) & 0xFF;
                            b += c & 0xFF;
                        }
                        blurPix[y * BLUR_BUF_W + x] = 0xFF000000 | ((r / div) << 16) | ((g / div) << 8) | (b / div);
                    }
                }
            }
            blurredField.setPixels(blurPix, 0, BLUR_BUF_W, 0, 0, BLUR_BUF_W, BLUR_BUF_H);
        }
        return blurredField;
    }

    public void registerFrostedDrawable(Drawable drawable) {
        if (drawable == null) {
            return;
        }
        frostedListeners.add(new WeakReference<>(drawable));
    }

    private void notifyFrostedListeners() {
        Iterator<WeakReference<Drawable>> it = frostedListeners.iterator();
        while (it.hasNext()) {
            WeakReference<Drawable> ref = it.next();
            Drawable d = ref.get();
            if (d == null) {
                frostedListeners.remove(ref);
            } else {
                d.invalidateSelf();
            }
        }
    }

    private void startFlow() {
        if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2.0));
            animator.setDuration(CYCLE_DURATION_MS);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(anim -> {
                phase = (float) anim.getAnimatedValue();
                fieldDirty = true;
                invalidate();
                notifyFrostedListeners();
            });
        }
        if (!animator.isStarted()) {
            animator.start();
        } else if (animator.isPaused()) {
            animator.resume();
        }
    }

    private void pauseFlow() {
        if (animator != null && animator.isRunning()) {
            animator.pause();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getWindowVisibility() == VISIBLE) {
            startFlow();
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && isAttachedToWindow()) {
            startFlow();
        } else {
            pauseFlow();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        frostedListeners.clear();
        super.onDetachedFromWindow();
    }

    /**
     * Drawable that renders a real-time frosted glass slice of the underlying FlowingAuroraView
     * at the target View's exact screen coordinates, with milky frost tint and specular rim.
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
            this.strokePaint.setColor(0x52FFFFFF);
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
                tintPaint.setColor(active ? 0x3DFFFFFF : frostTintColor);
                strokePaint.setColor(active ? 0xB3FFFFFF : 0x52FFFFFF);
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
                float sx = BLUR_BUF_W / (float) aurora.getWidth();
                float sy = BLUR_BUF_H / (float) aurora.getHeight();
                srcRect.set(
                        Math.max(0, Math.round(relX * sx)),
                        Math.max(0, Math.round(relY * sy)),
                        Math.min(BLUR_BUF_W, Math.round((relX + bounds.width()) * sx)),
                        Math.min(BLUR_BUF_H, Math.round((relY + bounds.height()) * sy)));
                if (!srcRect.isEmpty()) {
                    canvas.drawBitmap(aurora.getBlurredFieldBitmap(), srcRect, rectF, bitmapPaint);
                }
            }

            canvas.drawRect(rectF, tintPaint);
            canvas.restoreToCount(saveCount);

            if (cornerMode != CornerMode.MIDDLE) {
                float halfStroke = strokePaint.getStrokeWidth() * 0.5f;
                rectF.inset(halfStroke, halfStroke);
                clipPath.reset();
                clipPath.addRoundRect(rectF, radii, Path.Direction.CW);
                canvas.drawPath(clipPath, strokePaint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            bitmapPaint.setAlpha(alpha);
            tintPaint.setAlpha(alpha);
            strokePaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            bitmapPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
