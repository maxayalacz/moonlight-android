package com.limelight.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
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
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;

import java.lang.ref.WeakReference;

/**
 * Circular ambience portal drawn procedurally for Focus Mode (matching jujostream's
 * _FocusServerCircle), featuring a frosted glass refracting backdrop, status-tinted
 * outer ring, seamless continuous breathing/floating loop, and crisp vector glyphs.
 */
public class FocusCirclePortalView extends View {

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frostedBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint frostedTintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint interiorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vignettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spinnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF spinnerRect = new RectF();
    private final RectF circleDstRect = new RectF();
    private final Rect auroraSrcRect = new Rect();
    private final Path circleClipPath = new Path();
    private final int[] hostLoc = new int[2];
    private final int[] auroraLoc = new int[2];

    private AppThemePalette palette = AppThemePalette.MIDNIGHT;
    private int statusColor = 0xFFFF5252;
    private boolean isOnline = false;
    private boolean isPaired = false;
    private boolean isDiscovering = false;
    private boolean isActivePage = true;
    private boolean isPressedOrFocused = false;

    private ValueAnimator continuousTicker;
    private WeakReference<FlowingAuroraView> auroraRef;
    private WeakReference<PortalShaderView> portalRef;

    private float density = 1f;
    private PorterDuffColorFilter onlineIconFilter;
    private PorterDuffColorFilter offlineIconFilter;
    private PorterDuffColorFilter lockIconFilter;

    private int cachedW = -1;
    private int cachedH = -1;
    private int cachedStatusColor = 0;
    private String cachedPaletteKey = "";
    private boolean cachedOnline = false;
    private boolean cachedPressed = false;

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
        density = context.getResources().getDisplayMetrics().density;
        ringPaint.setStyle(Paint.Style.STROKE);
        specularPaint.setStyle(Paint.Style.STROKE);
        spinnerPaint.setStyle(Paint.Style.STROKE);
        spinnerPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStyle(Paint.Style.FILL);
        accentGlowPaint.setStyle(Paint.Style.FILL);
        frostedTintPaint.setStyle(Paint.Style.FILL);
        frostedTintPaint.setColor(0x52121826);
        innerBadgePaint.setStyle(Paint.Style.FILL);

        onlineIconFilter = new PorterDuffColorFilter(0xF2FFFFFF, PorterDuff.Mode.SRC_IN);
        offlineIconFilter = new PorterDuffColorFilter(0x7AFFFFFF, PorterDuff.Mode.SRC_IN);
        lockIconFilter = new PorterDuffColorFilter(0xFFFFB300, PorterDuff.Mode.SRC_IN);

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

    private float dp(float dpValue) {
        return dpValue * density;
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

    private boolean shouldAnimate() {
        return isActivePage
                && isAttachedToWindow()
                && getWindowVisibility() == VISIBLE
                && isShown();
    }

    private void syncAnimators() {
        if (!isAttachedToWindow()) {
            return;
        }
        if (continuousTicker == null) {
            continuousTicker = ValueAnimator.ofFloat(0f, 1f);
            continuousTicker.setDuration(1000L);
            continuousTicker.setRepeatCount(ValueAnimator.INFINITE);
            continuousTicker.setRepeatMode(ValueAnimator.RESTART);
            continuousTicker.setInterpolator(new LinearInterpolator());
            continuousTicker.addUpdateListener(anim -> {
                if (shouldAnimate()) {
                    long now = SystemClock.uptimeMillis();
                    float floatCycle = (now % 5500L) / 5500f;
                    float offsetPx = dp(6f) * (float) Math.sin(floatCycle * Math.PI * 2.0);
                    setTranslationY(offsetPx);
                    invalidate();
                } else {
                    setTranslationY(0f);
                }
            });
        }

        if (shouldAnimate()) {
            if (!continuousTicker.isStarted()) {
                continuousTicker.start();
            }
        } else {
            if (continuousTicker.isStarted()) {
                continuousTicker.cancel();
            }
            setTranslationY(0f);
        }
    }

    @Nullable
    private PortalShaderView resolvePortalView() {
        PortalShaderView cached = portalRef != null ? portalRef.get() : null;
        if (cached != null && cached.isAttachedToWindow()) {
            return cached;
        }
        View root = getRootView();
        if (root != null) {
            View found = root.findViewWithTag("oneui_portal_starfield_bg");
            if (found instanceof PortalShaderView) {
                portalRef = new WeakReference<>((PortalShaderView) found);
                return (PortalShaderView) found;
            }
        }
        return null;
    }

    @Nullable
    private FlowingAuroraView resolveAuroraView() {
        FlowingAuroraView cached = auroraRef != null ? auroraRef.get() : null;
        if (cached != null && cached.isAttachedToWindow()) {
            return cached;
        }
        View root = getRootView();
        if (root != null) {
            View found = root.findViewWithTag("oneui_flowing_aurora");
            if (found instanceof FlowingAuroraView) {
                auroraRef = new WeakReference<>((FlowingAuroraView) found);
                return (FlowingAuroraView) found;
            }
        }
        return null;
    }

    private void ensureStaticShaders(int w, int h, float cx, float cy,
                                     float outerRadius, float innerRadius, float glowMargin) {
        String paletteSig = palette.getSignature();
        boolean dimsChanged = (w != cachedW || h != cachedH);
        boolean stateChanged = (statusColor != cachedStatusColor
                || !paletteSig.equals(cachedPaletteKey)
                || isOnline != cachedOnline
                || isPressedOrFocused != cachedPressed);
        if (!dimsChanged && !stateChanged) {
            return;
        }
        cachedW = w;
        cachedH = h;
        cachedStatusColor = statusColor;
        cachedPaletteKey = paletteSig;
        cachedOnline = isOnline;
        cachedPressed = isPressedOrFocused;

        onlineIconFilter = new PorterDuffColorFilter(
                AppThemePalette.withAlpha(palette.getPrimaryTextColor(), 0.94f), PorterDuff.Mode.SRC_IN);
        offlineIconFilter = new PorterDuffColorFilter(
                AppThemePalette.withAlpha(palette.getSecondaryTextColor(), 0.60f), PorterDuff.Mode.SRC_IN);

        glowPaint.setShader(new RadialGradient(
                cx, cy, outerRadius + glowMargin,
                new int[]{
                        AppThemePalette.withAlpha(statusColor, 0.34f),
                        AppThemePalette.withAlpha(statusColor, 0.12f),
                        0x00000000
                },
                new float[]{0.62f, 0.82f, 1.0f},
                Shader.TileMode.CLAMP));

        accentGlowPaint.setShader(new RadialGradient(
                cx, cy, outerRadius + glowMargin * 0.9f,
                new int[]{
                        AppThemePalette.withAlpha(palette.accent, 1.0f),
                        0x00000000
                },
                new float[]{0.55f, 1.0f},
                Shader.TileMode.CLAMP));

        interiorPaint.setShader(new RadialGradient(
                cx - innerRadius * 0.22f,
                cy - innerRadius * 0.25f,
                innerRadius * 1.45f,
                new int[]{
                        AppThemePalette.withAlpha(palette.accent, isOnline ? (palette.isLight ? 0.18f : 0.26f) : 0.10f),
                        AppThemePalette.withAlpha(palette.secondary, palette.isLight ? 0.22f : 0.36f),
                        AppThemePalette.withAlpha(palette.surface, palette.isLight ? 0.35f : 0.56f)
                },
                new float[]{0.0f, 0.52f, 1.0f},
                Shader.TileMode.CLAMP));

        frostedTintPaint.setColor(palette.getFrostedCardTint(0x56000000));
        vignettePaint.setShader(new RadialGradient(
                cx, cy, innerRadius,
                new int[]{0x00000000, palette.isLight ? 0x160F172A : 0x52000000},
                new float[]{0.58f, 1.0f},
                Shader.TileMode.CLAMP));

        specularPaint.setShader(null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        syncAnimators();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        syncAnimators();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        syncAnimators();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (continuousTicker != null) {
            continuousTicker.cancel();
            continuousTicker = null;
        }
        portalRef = null;
        auroraRef = null;
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

        long now = SystemClock.uptimeMillis();
        float breathCycle = (now % 3600L) / 3600f;
        float glowPhase = 0.28f + 0.12f * (float) Math.sin(breathCycle * Math.PI * 2.0);
        float spinAngle = ((now % 1300L) / 1300f) * 360f;

        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float ringStroke = dp(isPressedOrFocused ? 4.5f : 3.5f);
        float glowMargin = dp(20f);
        float outerRadius = Math.max(16f, Math.min(w, h) * 0.5f - glowMargin);
        float innerRadius = outerRadius - ringStroke * 0.5f;

        ensureStaticShaders(w, h, cx, cy, outerRadius, innerRadius, glowMargin);

        // 1. Status perimeter halo
        canvas.drawCircle(cx, cy, outerRadius + glowMargin, glowPaint);

        // 2. Active page breathing accent bloom (zero allocations per frame)
        if (isActivePage || isPressedOrFocused) {
            float bloomAlpha = isPressedOrFocused ? 0.45f : glowPhase;
            accentGlowPaint.setAlpha(Math.max(0, Math.min(255, Math.round(bloomAlpha * 255f))));
            canvas.drawCircle(cx, cy, outerRadius + glowMargin * 0.9f, accentGlowPaint);
        }

        // 3. Frosted glass refracting interior (clipped to circle)
        circleClipPath.reset();
        circleClipPath.addCircle(cx, cy, innerRadius, Path.Direction.CW);
        int saveCount = canvas.save();
        canvas.clipPath(circleClipPath);

        View backdropView = null;
        Bitmap blurred = null;
        PortalShaderView portal = resolvePortalView();
        if (portal != null) {
            backdropView = portal;
            blurred = portal.getBlurredBitmap();
        }
        if (backdropView == null || blurred == null) {
            FlowingAuroraView aurora = resolveAuroraView();
            if (aurora != null) {
                backdropView = aurora;
                blurred = aurora.getBlurredFieldBitmap();
            }
        }
        if (backdropView != null && backdropView.getWidth() > 0 && backdropView.getHeight() > 0
                && blurred != null && !blurred.isRecycled()) {
            getLocationInWindow(hostLoc);
            backdropView.getLocationInWindow(auroraLoc);
            float relX = (hostLoc[0] - auroraLoc[0]) + (cx - innerRadius);
            float relY = (hostLoc[1] - auroraLoc[1]) + (cy - innerRadius);
            float diameter = innerRadius * 2f;
            float sx = blurred.getWidth() / (float) backdropView.getWidth();
            float sy = blurred.getHeight() / (float) backdropView.getHeight();
            auroraSrcRect.set(
                    Math.max(0, Math.round(relX * sx)),
                    Math.max(0, Math.round(relY * sy)),
                    Math.min(blurred.getWidth(), Math.round((relX + diameter) * sx)),
                    Math.min(blurred.getHeight(), Math.round((relY + diameter) * sy)));
            if (!auroraSrcRect.isEmpty()) {
                circleDstRect.set(cx - innerRadius, cy - innerRadius, cx + innerRadius, cy + innerRadius);
                canvas.drawBitmap(blurred, auroraSrcRect, circleDstRect, frostedBitmapPaint);
            }
        }

        // Dark frosted glass tint + ambient radial interior + vignette
        canvas.drawCircle(cx, cy, innerRadius, frostedTintPaint);
        canvas.drawCircle(cx, cy, innerRadius, interiorPaint);
        canvas.drawCircle(cx, cy, innerRadius, vignettePaint);
        canvas.restoreToCount(saveCount);

        // 4. Center PC emblem
        if (computerDrawable != null) {
            int iconSize = Math.round(innerRadius * 0.62f);
            int half = iconSize / 2;
            innerBadgePaint.setColor(AppThemePalette.withAlpha(
                    isOnline ? (palette.isLight ? palette.accent : palette.accentLight)
                            : (palette.isLight ? 0xFF0F172A : 0xFFFFFFFF),
                    isOnline ? (palette.isLight ? 0.14f : 0.16f) : 0.08f));
            canvas.drawCircle(cx, cy, innerRadius * 0.44f, innerBadgePaint);

            computerDrawable.setBounds(
                    Math.round(cx) - half,
                    Math.round(cy) - half,
                    Math.round(cx) + half,
                    Math.round(cy) + half);
            computerDrawable.setColorFilter(isOnline ? onlineIconFilter : offlineIconFilter);
            computerDrawable.draw(canvas);
        }

        // 5. Unpaired lock overlay or discovering spinner arc
        if (isDiscovering) {
            float spinR = innerRadius * 0.56f;
            spinnerRect.set(cx - spinR, cy - spinR, cx + spinR, cy + spinR);
            spinnerPaint.setColor(AppThemePalette.withAlpha(palette.accentLight, 0.85f));
            spinnerPaint.setStrokeWidth(dp(2.5f));
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
            lockDrawable.setColorFilter(lockIconFilter);
            lockDrawable.draw(canvas);
        }

        // 6. Crisp 3.5dp status ring
        ringPaint.setColor(statusColor);
        ringPaint.setStrokeWidth(ringStroke);
        canvas.drawCircle(cx, cy, outerRadius, ringPaint);

        if (isPressedOrFocused) {
            specularPaint.setColor(0xD9FFFFFF);
            specularPaint.setStrokeWidth(dp(2f));
            canvas.drawCircle(cx, cy, outerRadius + ringStroke * 0.65f, specularPaint);
        }
    }
}
