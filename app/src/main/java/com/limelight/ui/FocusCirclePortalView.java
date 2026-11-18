package com.limelight.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.utils.UiHelper;

/**
 * Circular ambience portal drawn procedurally for Focus Mode (matching jujostream's
 * _FocusServerCircle), featuring a status-tinted outer ring, breathing accent bloom,
 * ambient radial interior gradient, glass specular rim, and crisp vector glyphs.
 */
public class FocusCirclePortalView extends View {

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint interiorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vignettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spinnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF spinnerRect = new RectF();

    private AppThemePalette palette = AppThemePalette.MIDNIGHT;
    private int statusColor = 0xFFFF5252;
    private boolean isOnline = false;
    private boolean isPaired = false;
    private boolean isDiscovering = false;
    private boolean isActivePage = true;
    private boolean isPressedOrFocused = false;

    private float glowPhase = 0.25f;
    private float floatPhase = 0f;
    private float spinAngle = 0f;

    private ValueAnimator breathAnimator;
    private ValueAnimator floatAnimator;

    private Drawable computerDrawable;
    private Drawable lockDrawable;

    public FocusCirclePortalView(Context context) {
        super(context);
        init(context);
    }

    public FocusCirclePortalView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        ringPaint.setStyle(Paint.Style.STROKE);
        specularPaint.setStyle(Paint.Style.STROKE);
        spinnerPaint.setStyle(Paint.Style.STROKE);
        spinnerPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStyle(Paint.Style.FILL);
        accentGlowPaint.setStyle(Paint.Style.FILL);
        innerBadgePaint.setStyle(Paint.Style.FILL);

        computerDrawable = ContextCompat.getDrawable(context, R.drawable.ic_computer);
        if (computerDrawable != null) {
            computerDrawable = computerDrawable.mutate();
        }
        lockDrawable = ContextCompat.getDrawable(context, R.drawable.ic_lock);
        if (lockDrawable != null) {
            lockDrawable = lockDrawable.mutate();
        }
        palette = AppThemePalette.getCurrent(context);
    }

    public void bind(ComputerDetails details, boolean isActivePage) {
        this.palette = AppThemePalette.getCurrent(getContext());
        this.isActivePage = isActivePage;
        this.isOnline = details.state == ComputerDetails.State.ONLINE;
        this.isDiscovering = details.state == ComputerDetails.State.UNKNOWN;
        this.isPaired = details.pairState == PairingManager.PairState.PAIRED;

        if (isOnline && isPaired) {
            statusColor = palette.highlight;
        } else if (isOnline) {
            statusColor = 0xFFFFB300;
        } else {
            statusColor = 0xFFFF5252;
        }

        syncAnimators();
        invalidate();
    }

    public void setPressedOrFocused(boolean highlighted) {
        if (this.isPressedOrFocused != highlighted) {
            this.isPressedOrFocused = highlighted;
            invalidate();
        }
    }

    private void syncAnimators() {
        if (!isAttachedToWindow()) {
            return;
        }
        if (breathAnimator == null) {
            breathAnimator = ValueAnimator.ofFloat(0.18f, 0.42f);
            breathAnimator.setDuration(1800L);
            breathAnimator.setRepeatCount(ValueAnimator.INFINITE);
            breathAnimator.setRepeatMode(ValueAnimator.REVERSE);
            breathAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            breathAnimator.addUpdateListener(anim -> {
                glowPhase = (float) anim.getAnimatedValue();
                spinAngle = (spinAngle + 6f) % 360f;
                invalidate();
            });
        }
        if (floatAnimator == null) {
            floatAnimator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2.0));
            floatAnimator.setDuration(5500L);
            floatAnimator.setRepeatCount(ValueAnimator.INFINITE);
            floatAnimator.setRepeatMode(ValueAnimator.RESTART);
            floatAnimator.setInterpolator(new LinearInterpolator());
            floatAnimator.addUpdateListener(anim -> {
                floatPhase = (float) anim.getAnimatedValue();
                float offsetPx = UiHelper.dpToPx(getContext(), 6f) * (float) Math.sin(floatPhase);
                setTranslationY(isActivePage ? offsetPx : 0f);
            });
        }

        if (isActivePage) {
            if (!breathAnimator.isStarted()) {
                breathAnimator.start();
            }
            if (!floatAnimator.isStarted()) {
                floatAnimator.start();
            }
        } else {
            breathAnimator.cancel();
            floatAnimator.cancel();
            setTranslationY(0f);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        syncAnimators();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (breathAnimator != null) {
            breathAnimator.cancel();
            breathAnimator = null;
        }
        if (floatAnimator != null) {
            floatAnimator.cancel();
            floatAnimator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float ringStroke = UiHelper.dpToPx(getContext(), isPressedOrFocused ? 4.5f : 3.5f);
        float glowMargin = UiHelper.dpToPx(getContext(), 20f);
        float outerRadius = Math.max(16f, Math.min(w, h) * 0.5f - glowMargin);
        float innerRadius = outerRadius - ringStroke * 0.5f;

        // 1. Status perimeter halo
        glowPaint.setShader(new RadialGradient(
                cx, cy, outerRadius + glowMargin,
                new int[]{
                        AppThemePalette.withAlpha(statusColor, 0.34f),
                        AppThemePalette.withAlpha(statusColor, 0.12f),
                        0x00000000
                },
                new float[]{0.62f, 0.82f, 1.0f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, outerRadius + glowMargin, glowPaint);

        // 2. Active page breathing accent bloom
        if (isActivePage || isPressedOrFocused) {
            float bloomAlpha = isPressedOrFocused ? 0.45f : glowPhase;
            accentGlowPaint.setShader(new RadialGradient(
                    cx, cy, outerRadius + glowMargin * 0.9f,
                    new int[]{
                            AppThemePalette.withAlpha(palette.accent, bloomAlpha),
                            0x00000000
                    },
                    new float[]{0.55f, 1.0f},
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, outerRadius + glowMargin * 0.9f, accentGlowPaint);
        }

        // 3. Ambient glass interior gradient
        interiorPaint.setShader(new RadialGradient(
                cx - innerRadius * 0.22f,
                cy - innerRadius * 0.25f,
                innerRadius * 1.45f,
                new int[]{
                        AppThemePalette.withAlpha(palette.accent, isOnline ? 0.36f : 0.16f),
                        AppThemePalette.withAlpha(palette.secondary, 0.72f),
                        AppThemePalette.withAlpha(palette.surface, 0.92f)
                },
                new float[]{0.0f, 0.52f, 1.0f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, innerRadius, interiorPaint);

        // 4. Diagonal frosted glass sheen
        sheenPaint.setShader(new LinearGradient(
                cx - innerRadius, cy - innerRadius,
                cx + innerRadius, cy + innerRadius,
                new int[]{
                        0x26FFFFFF,
                        0x08FFFFFF,
                        0x00FFFFFF,
                        0x14FFFFFF
                },
                new float[]{0.0f, 0.35f, 0.7f, 1.0f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, innerRadius, sheenPaint);

        // 5. Soft inner vignette (matching jujostream RadialGradient 0.6 -> 1.0 black alpha 0.3)
        vignettePaint.setShader(new RadialGradient(
                cx, cy, innerRadius,
                new int[]{0x00000000, 0x4D000000},
                new float[]{0.6f, 1.0f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, innerRadius, vignettePaint);

        // 6. Center PC emblem
        if (computerDrawable != null) {
            int iconSize = Math.round(innerRadius * 0.62f);
            int half = iconSize / 2;
            innerBadgePaint.setColor(AppThemePalette.withAlpha(
                    isOnline ? palette.accentLight : 0xFFFFFFFF,
                    isOnline ? 0.16f : 0.08f));
            canvas.drawCircle(cx, cy, innerRadius * 0.44f, innerBadgePaint);

            computerDrawable.setBounds(
                    Math.round(cx) - half,
                    Math.round(cy) - half,
                    Math.round(cx) + half,
                    Math.round(cy) + half);
            int iconTint = isOnline ? 0xF2FFFFFF : 0x7AFFFFFF;
            computerDrawable.setColorFilter(new PorterDuffColorFilter(iconTint, PorterDuff.Mode.SRC_IN));
            computerDrawable.draw(canvas);
        }

        // 7. Unpaired lock overlay or discovering spinner arc
        if (isDiscovering) {
            float spinR = innerRadius * 0.56f;
            spinnerRect.set(cx - spinR, cy - spinR, cx + spinR, cy + spinR);
            spinnerPaint.setColor(AppThemePalette.withAlpha(palette.accentLight, 0.85f));
            spinnerPaint.setStrokeWidth(UiHelper.dpToPx(getContext(), 2.5f));
            canvas.drawArc(spinnerRect, spinAngle, 110f, false, spinnerPaint);
        } else if (isOnline && !isPaired && lockDrawable != null) {
            int badgeSize = Math.round(innerRadius * 0.24f);
            float bx = cx + innerRadius * 0.28f;
            float by = cy + innerRadius * 0.24f;
            innerBadgePaint.setColor(0xD91A1200);
            canvas.drawCircle(bx, by, badgeSize * 0.72f, innerBadgePaint);
            lockDrawable.setBounds(
                    Math.round(bx - badgeSize * 0.5f),
                    Math.round(by - badgeSize * 0.5f),
                    Math.round(bx + badgeSize * 0.5f),
                    Math.round(by + badgeSize * 0.5f));
            lockDrawable.setColorFilter(new PorterDuffColorFilter(0xFFFFB300, PorterDuff.Mode.SRC_IN));
            lockDrawable.draw(canvas);
        }

        // 8. Crisp 3.5dp status ring & top-left glass specular highlight
        ringPaint.setColor(statusColor);
        ringPaint.setStrokeWidth(ringStroke);
        canvas.drawCircle(cx, cy, outerRadius, ringPaint);

        if (isPressedOrFocused) {
            specularPaint.setColor(0xD9FFFFFF);
            specularPaint.setStrokeWidth(UiHelper.dpToPx(getContext(), 2f));
            canvas.drawCircle(cx, cy, outerRadius + ringStroke * 0.65f, specularPaint);
        } else {
            specularPaint.setShader(new LinearGradient(
                    cx - innerRadius, cy - innerRadius,
                    cx + innerRadius, cy + innerRadius,
                    new int[]{0x59FFFFFF, 0x00FFFFFF, 0x1AFFFFFF},
                    new float[]{0.0f, 0.5f, 1.0f},
                    Shader.TileMode.CLAMP));
            specularPaint.setStrokeWidth(UiHelper.dpToPx(getContext(), 1.2f));
            canvas.drawCircle(cx, cy, innerRadius - UiHelper.dpToPx(getContext(), 0.6f), specularPaint);
        }
    }
}
