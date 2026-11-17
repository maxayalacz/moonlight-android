package com.limelight.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Continuous real-time motion-graphics backdrop featuring organic merging/separating
 * gradient metaballs and a live 2D plexus particle network, with configurable presets,
 * metaball sharpness, plexus dot density, and a shared pre-blurred buffer for
 * real-time frosted glass cards.
 */
public class FlowingAuroraView extends View implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String PREF_BACKDROP_STYLE = "list_backdrop_style";
    private static final String PREF_METABALL_SHARPNESS = "list_metaball_sharpness";
    private static final String PREF_PLEXUS_DENSITY = "list_plexus_density";
    private static final int FIELD_W = 64;
    private static final int FIELD_H = 128;
    private static final int MAX_NODES = 80;
    private static final int BLOB_COUNT = 5;

    public enum CornerMode {
        ALL,
        TOP,
        MIDDLE,
        BOTTOM
    }

    public enum BackdropStyle {
        BALANCED,
        BOLD_PLEXUS,
        PLEXUS_HERO
    }

    public enum MetaballSharpness {
        SOFT,
        MEDIUM,
        SHARP
    }

    public enum PlexusDensity {
        NORMAL,
        HIGH,
        ULTRA
    }

    private static class Blob {
        float x, y;     // normalized 0..1
        float vx, vy;   // normalized units/sec
        float radius;   // normalized
        int r, g, b;
    }

    private static class Node {
        float x, y;     // normalized 0..1
        float vx, vy;   // normalized units/sec
        float sizeDp;
    }

    private final Blob[] blobs = new Blob[BLOB_COUNT];
    private final Node[] nodes = new Node[MAX_NODES];

    private final Bitmap rawField = Bitmap.createBitmap(FIELD_W, FIELD_H, Bitmap.Config.ARGB_8888);
    private final Bitmap blurredField = Bitmap.createBitmap(FIELD_W, FIELD_H, Bitmap.Config.ARGB_8888);
    private final int[] rawPix = new int[FIELD_W * FIELD_H];
    private final int[] blurPix = new int[FIELD_W * FIELD_H];
    private final int[] blurTmp = new int[FIELD_W * FIELD_H];

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodeGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF fullDst = new RectF();

    private BackdropStyle style = BackdropStyle.BALANCED;
    private MetaballSharpness sharpness = MetaballSharpness.SOFT;
    private PlexusDensity plexusDensity = PlexusDensity.NORMAL;
    private ValueAnimator ticker;
    private long lastTickMs = 0L;
    private boolean fieldDirty = true;

    private final CopyOnWriteArrayList<WeakReference<Drawable>> frostedListeners = new CopyOnWriteArrayList<>();

    public FlowingAuroraView(Context context) {
        super(context);
        init(context);
    }

    public FlowingAuroraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        linePaint.setStyle(Paint.Style.STROKE);
        nodePaint.setStyle(Paint.Style.FILL);
        nodeGlowPaint.setStyle(Paint.Style.FILL);

        // 5 organic gradient metaballs (cyan, electric blue, violet, teal, indigo)
        int[][] colors = {
                {0, 212, 255},   // electric cyan
                {59, 130, 246},  // electric blue
                {139, 92, 246},  // vivid violet
                {6, 182, 212},   // teal cyan
                {99, 102, 241}   // indigo
        };
        float[][] initBlobs = {
                {0.25f, 0.25f, 0.042f, 0.028f, 0.17f},
                {0.75f, 0.35f, -0.036f, 0.034f, 0.16f},
                {0.45f, 0.68f, 0.032f, -0.038f, 0.18f},
                {0.22f, 0.78f, 0.045f, -0.026f, 0.14f},
                {0.78f, 0.72f, -0.030f, -0.032f, 0.15f}
        };
        for (int i = 0; i < BLOB_COUNT; i++) {
            Blob b = new Blob();
            b.x = initBlobs[i][0];
            b.y = initBlobs[i][1];
            b.vx = initBlobs[i][2];
            b.vy = initBlobs[i][3];
            b.radius = initBlobs[i][4];
            b.r = colors[i][0];
            b.g = colors[i][1];
            b.b = colors[i][2];
            blobs[i] = b;
        }

        // Randomize starting positions and continuous velocities for plexus nodes on every launch
        Random rng = new Random();
        for (int i = 0; i < MAX_NODES; i++) {
            Node n = new Node();
            n.x = rng.nextFloat();
            n.y = rng.nextFloat();
            float angle = (float) (rng.nextFloat() * Math.PI * 2.0);
            float speed = 0.016f + rng.nextFloat() * 0.022f;
            n.vx = (float) Math.cos(angle) * speed;
            n.vy = (float) Math.sin(angle) * speed;
            n.sizeDp = 1.6f + rng.nextFloat() * 1.5f;
            nodes[i] = n;
        }

        loadStyleFromPrefs(context);
    }

    private void loadStyleFromPrefs(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String val = prefs.getString(PREF_BACKDROP_STYLE, "balanced");
        if ("bold_plexus".equals(val)) {
            style = BackdropStyle.BOLD_PLEXUS;
        } else if ("plexus_hero".equals(val)) {
            style = BackdropStyle.PLEXUS_HERO;
        } else {
            style = BackdropStyle.BALANCED;
        }

        String sharpVal = prefs.getString(PREF_METABALL_SHARPNESS, "soft");
        if ("sharp".equals(sharpVal)) {
            sharpness = MetaballSharpness.SHARP;
        } else if ("medium".equals(sharpVal)) {
            sharpness = MetaballSharpness.MEDIUM;
        } else {
            sharpness = MetaballSharpness.SOFT;
        }

        String densityVal = prefs.getString(PREF_PLEXUS_DENSITY, "normal");
        if ("ultra".equals(densityVal)) {
            plexusDensity = PlexusDensity.ULTRA;
        } else if ("high".equals(densityVal)) {
            plexusDensity = PlexusDensity.HIGH;
        } else {
            plexusDensity = PlexusDensity.NORMAL;
        }

        fieldDirty = true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        if (PREF_BACKDROP_STYLE.equals(key)
                || PREF_METABALL_SHARPNESS.equals(key)
                || PREF_PLEXUS_DENSITY.equals(key)) {
            loadStyleFromPrefs(getContext());
            invalidate();
            notifyFrostedListeners();
        }
    }

    private void stepSimulation(float dt) {
        if (dt <= 0f) {
            return;
        }
        // Step metaballs with soft boundary bounce
        for (Blob b : blobs) {
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            if (b.x < 0.08f) {
                b.x = 0.08f;
                b.vx = Math.abs(b.vx);
            } else if (b.x > 0.92f) {
                b.x = 0.92f;
                b.vx = -Math.abs(b.vx);
            }
            if (b.y < 0.08f) {
                b.y = 0.08f;
                b.vy = Math.abs(b.vy);
            } else if (b.y > 0.92f) {
                b.y = 0.92f;
                b.vy = -Math.abs(b.vy);
            }
        }

        // Step plexus nodes continuously
        int activeNodes = getActiveNodeCount();
        for (int i = 0; i < activeNodes; i++) {
            Node n = nodes[i];
            n.x += n.vx * dt;
            n.y += n.vy * dt;
            if (n.x < 0f) {
                n.x = 0f;
                n.vx = Math.abs(n.vx);
            } else if (n.x > 1f) {
                n.x = 1f;
                n.vx = -Math.abs(n.vx);
            }
            if (n.y < 0f) {
                n.y = 0f;
                n.vy = Math.abs(n.vy);
            } else if (n.y > 1f) {
                n.y = 1f;
                n.vy = -Math.abs(n.vy);
            }
        }
        fieldDirty = true;
    }

    private int getActiveNodeCount() {
        int base;
        switch (style) {
            case BOLD_PLEXUS:
                base = 38;
                break;
            case PLEXUS_HERO:
                base = 34;
                break;
            case BALANCED:
            default:
                base = 26;
                break;
        }
        if (plexusDensity == PlexusDensity.ULTRA) {
            return Math.min(MAX_NODES, Math.round(base * 2.1f));
        } else if (plexusDensity == PlexusDensity.HIGH) {
            return Math.min(MAX_NODES, Math.round(base * 1.5f));
        }
        return base;
    }

    private void renderMetaballField() {
        float blobStrength;
        float blobSoftness;
        if (style == BackdropStyle.PLEXUS_HERO) {
            blobStrength = 0.42f;
            blobSoftness = 0.85f;
        } else if (style == BackdropStyle.BOLD_PLEXUS) {
            blobStrength = 0.65f;
            blobSoftness = 1.05f;
        } else {
            blobStrength = 1.0f;
            blobSoftness = 1.0f;
        }

        float eps;
        float threshLow;
        float threshSpan;
        float lowSlope;
        if (sharpness == MetaballSharpness.SHARP) {
            eps = 0.0006f;
            threshLow = 0.78f;
            threshSpan = 0.24f;
            lowSlope = 0.04f;
        } else if (sharpness == MetaballSharpness.MEDIUM) {
            eps = 0.0012f;
            threshLow = 0.56f;
            threshSpan = 0.48f;
            lowSlope = 0.12f;
        } else {
            eps = 0.0025f;
            threshLow = 0.35f;
            threshSpan = 0.95f;
            lowSlope = 0.25f;
        }

        final float aspect = (float) getHeight() / Math.max(1f, (float) getWidth());
        final int baseR = 5, baseG = 8, baseB = 18;

        for (int py = 0; py < FIELD_H; py++) {
            float ny = (py + 0.5f) / FIELD_H;
            int row = py * FIELD_W;
            for (int px = 0; px < FIELD_W; px++) {
                float nx = (px + 0.5f) / FIELD_W;

                float fieldSum = 0f;
                float rAcc = 0f, gAcc = 0f, bAcc = 0f;

                for (int i = 0; i < BLOB_COUNT; i++) {
                    Blob b = blobs[i];
                    float dx = nx - b.x;
                    float dy = (ny - b.y) * Math.min(2.2f, Math.max(0.5f, aspect));
                    float r = b.radius * blobSoftness;
                    float distSq = dx * dx + dy * dy + eps;
                    float contrib = (r * r) / distSq;
                    fieldSum += contrib;
                    rAcc += b.r * contrib;
                    gAcc += b.g * contrib;
                    bAcc += b.b * contrib;
                }

                // Smooth metaball threshold: merges blobs cleanly when fieldSum crosses ~1.0
                float intensity;
                if (fieldSum <= threshLow) {
                    intensity = fieldSum * lowSlope;
                } else {
                    float baseInt = threshLow * lowSlope;
                    float t = Math.min(1f, (fieldSum - threshLow) / threshSpan);
                    // Smoothstep for organic metaball edge
                    intensity = baseInt + (t * t * (3f - 2f * t)) * (1f - baseInt);
                }
                intensity = Math.min(1f, intensity * blobStrength);

                float invSum = fieldSum > 0.0001f ? (1f / fieldSum) : 0f;
                float cr = rAcc * invSum;
                float cg = gAcc * invSum;
                float cb = bAcc * invSum;

                int outR = Math.min(255, (int) (baseR + (cr - baseR) * intensity));
                int outG = Math.min(255, (int) (baseG + (cg - baseG) * intensity));
                int outB = Math.min(255, (int) (baseB + (cb - baseB) * intensity));
                rawPix[row + px] = 0xFF000000 | (outR << 16) | (outG << 8) | outB;
            }
        }
        rawField.setPixels(rawPix, 0, FIELD_W, 0, 0, FIELD_W, FIELD_H);
    }

    private void updateBlurredField() {
        if (!fieldDirty) {
            return;
        }
        fieldDirty = false;
        renderMetaballField();

        // Box blur on 64x128 buffer -> silky frosted diffusion for FrostedCardDrawable and soft/medium backdrops
        System.arraycopy(rawPix, 0, blurPix, 0, rawPix.length);
        int radius = (sharpness == MetaballSharpness.MEDIUM) ? 2 : 5;
        int passes = (sharpness == MetaballSharpness.MEDIUM) ? 1 : 2;
        int div = radius * 2 + 1;
        for (int pass = 0; pass < passes; pass++) {
            for (int y = 0; y < FIELD_H; y++) {
                int row = y * FIELD_W;
                for (int x = 0; x < FIELD_W; x++) {
                    int r = 0, g = 0, b = 0;
                    for (int k = -radius; k <= radius; k++) {
                        int sxIdx = Math.max(0, Math.min(FIELD_W - 1, x + k));
                        int c = blurPix[row + sxIdx];
                        r += (c >> 16) & 0xFF;
                        g += (c >> 8) & 0xFF;
                        b += c & 0xFF;
                    }
                    blurTmp[row + x] = 0xFF000000 | ((r / div) << 16) | ((g / div) << 8) | (b / div);
                }
            }
            for (int x = 0; x < FIELD_W; x++) {
                for (int y = 0; y < FIELD_H; y++) {
                    int r = 0, g = 0, b = 0;
                    for (int k = -radius; k <= radius; k++) {
                        int syIdx = Math.max(0, Math.min(FIELD_H - 1, y + k));
                        int c = blurTmp[syIdx * FIELD_W + x];
                        r += (c >> 16) & 0xFF;
                        g += (c >> 8) & 0xFF;
                        b += c & 0xFF;
                    }
                    blurPix[y * FIELD_W + x] = 0xFF000000 | ((r / div) << 16) | ((g / div) << 8) | (b / div);
                }
            }
        }
        blurredField.setPixels(blurPix, 0, FIELD_W, 0, 0, FIELD_W, FIELD_H);
    }

    public Bitmap getBlurredFieldBitmap() {
        updateBlurredField();
        return blurredField;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        if (fieldDirty) {
            updateBlurredField();
        }

        fullDst.set(0f, 0f, w, h);
        // Draw crisp or diffused metaball field depending on user sharpness setting
        canvas.drawBitmap(sharpness == MetaballSharpness.SHARP ? rawField : blurredField, null, fullDst, bitmapPaint);

        // Draw real-time 2D plexus network on top
        drawPlexusNetwork(canvas, w, h);
    }

    private void drawPlexusNetwork(Canvas canvas, int w, int h) {
        int activeNodes = getActiveNodeCount();
        float minDim = Math.min(w, h);
        float maxDist = minDim * (style == BackdropStyle.BALANCED ? 0.28f : 0.33f);
        float maxDistSq = maxDist * maxDist;
        float density = getResources().getDisplayMetrics().density;

        int maxLineAlpha;
        int nodeAlpha;
        float lineWidthPx;
        if (style == BackdropStyle.BOLD_PLEXUS) {
            maxLineAlpha = 165;
            nodeAlpha = 225;
            lineWidthPx = 1.4f * density;
        } else if (style == BackdropStyle.PLEXUS_HERO) {
            maxLineAlpha = 195;
            nodeAlpha = 240;
            lineWidthPx = 1.5f * density;
        } else {
            maxLineAlpha = 95;
            nodeAlpha = 160;
            lineWidthPx = 1.1f * density;
        }
        linePaint.setStrokeWidth(lineWidthPx);

        for (int i = 0; i < activeNodes; i++) {
            float x1 = nodes[i].x * w;
            float y1 = nodes[i].y * h;
            for (int j = i + 1; j < activeNodes; j++) {
                float x2 = nodes[j].x * w;
                float y2 = nodes[j].y * h;
                float dx = x2 - x1;
                float dy = y2 - y1;
                float distSq = dx * dx + dy * dy;
                if (distSq < maxDistSq) {
                    float t = 1f - (float) Math.sqrt(distSq) / maxDist;
                    int alpha = Math.max(4, Math.round(maxLineAlpha * t * t));
                    linePaint.setColor((alpha << 24) | 0x5CE1FF);
                    canvas.drawLine(x1, y1, x2, y2, linePaint);
                }
            }
        }

        for (int i = 0; i < activeNodes; i++) {
            float x = nodes[i].x * w;
            float y = nodes[i].y * h;
            float r = nodes[i].sizeDp * density;
            nodeGlowPaint.setColor(((nodeAlpha / 4) << 24) | 0x38BDF8);
            canvas.drawCircle(x, y, r * 2.4f, nodeGlowPaint);
            nodePaint.setColor((nodeAlpha << 24) | 0xE0F7FF);
            canvas.drawCircle(x, y, r, nodePaint);
        }
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
        lastTickMs = SystemClock.uptimeMillis();
        if (ticker == null) {
            ticker = ValueAnimator.ofFloat(0f, 1f);
            ticker.setDuration(1000L);
            ticker.setRepeatCount(ValueAnimator.INFINITE);
            ticker.setInterpolator(new LinearInterpolator());
            ticker.addUpdateListener(anim -> {
                long now = SystemClock.uptimeMillis();
                float dt = Math.min(0.05f, Math.max(0f, (now - lastTickMs) / 1000f));
                lastTickMs = now;
                stepSimulation(dt);
                invalidate();
                notifyFrostedListeners();
            });
        }
        if (!ticker.isStarted()) {
            ticker.start();
        } else if (ticker.isPaused()) {
            ticker.resume();
        }
    }

    private void pauseFlow() {
        if (ticker != null && ticker.isRunning()) {
            ticker.pause();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        PreferenceManager.getDefaultSharedPreferences(getContext())
                .registerOnSharedPreferenceChangeListener(this);
        loadStyleFromPrefs(getContext());
        if (getWindowVisibility() == VISIBLE) {
            startFlow();
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && isAttachedToWindow()) {
            loadStyleFromPrefs(getContext());
            startFlow();
        } else {
            pauseFlow();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        PreferenceManager.getDefaultSharedPreferences(getContext())
                .unregisterOnSharedPreferenceChangeListener(this);
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
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
                float sx = FIELD_W / (float) aurora.getWidth();
                float sy = FIELD_H / (float) aurora.getHeight();
                srcRect.set(
                        Math.max(0, Math.round(relX * sx)),
                        Math.max(0, Math.round(relY * sy)),
                        Math.min(FIELD_W, Math.round((relX + bounds.width()) * sx)),
                        Math.min(FIELD_H, Math.round((relY + bounds.height()) * sy)));
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
