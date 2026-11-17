package com.limelight.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Hardware-accelerated flowing dark-mode aurora backdrop for glassmorphic UI surfaces.
 * Automatically pauses animation when detached or not visible.
 */
public class FlowingAuroraView extends View {
    private static final long CYCLE_DURATION_MS = 20000L;

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blobPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blobPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blobPaint3 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix shaderMatrix = new Matrix();

    private RadialGradient gradient1;
    private RadialGradient gradient2;
    private RadialGradient gradient3;
    private float phase = 0f;
    private ValueAnimator animator;

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
        basePaint.setColor(0xFF070A12);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) {
            return;
        }
        float radius1 = Math.max(w, h) * 0.72f;
        float radius2 = Math.max(w, h) * 0.65f;
        float radius3 = Math.max(w, h) * 0.58f;

        // Deep indigo glow
        gradient1 = new RadialGradient(0f, 0f, radius1,
                new int[]{0x483B82F6, 0x1A1E40AF, 0x00070A12},
                new float[]{0f, 0.52f, 1f},
                Shader.TileMode.CLAMP);
        blobPaint1.setShader(gradient1);

        // Muted teal / cyan glow
        gradient2 = new RadialGradient(0f, 0f, radius2,
                new int[]{0x3806B6D4, 0x140E7490, 0x00070A12},
                new float[]{0f, 0.50f, 1f},
                Shader.TileMode.CLAMP);
        blobPaint2.setShader(gradient2);

        // Muted violet glow
        gradient3 = new RadialGradient(0f, 0f, radius3,
                new int[]{0x347C3AED, 0x124C1D95, 0x00070A12},
                new float[]{0f, 0.50f, 1f},
                Shader.TileMode.CLAMP);
        blobPaint3.setShader(gradient3);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        canvas.drawRect(0f, 0f, w, h, basePaint);

        if (gradient1 != null) {
            float x1 = w * (0.28f + 0.22f * (float) Math.sin(phase));
            float y1 = h * (0.24f + 0.18f * (float) Math.cos(phase * 0.8f));
            shaderMatrix.setTranslate(x1, y1);
            gradient1.setLocalMatrix(shaderMatrix);
            canvas.drawRect(0f, 0f, w, h, blobPaint1);
        }

        if (gradient2 != null) {
            float x2 = w * (0.72f + 0.20f * (float) Math.cos(phase + 1.2f));
            float y2 = h * (0.68f + 0.20f * (float) Math.sin(phase * 1.1f + 0.7f));
            shaderMatrix.setTranslate(x2, y2);
            gradient2.setLocalMatrix(shaderMatrix);
            canvas.drawRect(0f, 0f, w, h, blobPaint2);
        }

        if (gradient3 != null) {
            float x3 = w * (0.50f + 0.26f * (float) Math.sin(phase * 0.7f + 2.4f));
            float y3 = h * (0.48f + 0.24f * (float) Math.cos(phase * 1.2f + 1.8f));
            shaderMatrix.setTranslate(x3, y3);
            gradient3.setLocalMatrix(shaderMatrix);
            canvas.drawRect(0f, 0f, w, h, blobPaint3);
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
                invalidate();
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
        super.onDetachedFromWindow();
    }
}
