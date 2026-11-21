package com.limelight.binding.input.virtual_controller;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;

import com.limelight.utils.UiHelper;

import java.util.List;

/**
 * Captures a lightweight downscaled blurred snapshot of the live stream SurfaceView at ~20 FPS
 * and draws the exact region behind each on-screen controller element, giving all OSC buttons,
 * D-pad, and analog sticks a genuine live frosted-glass backdrop over the streaming video.
 */
public class OscLiveBlurHelper {
    private static final int CAPTURE_LONG_EDGE_CRISP = 560;
    private static final int CAPTURE_LONG_EDGE_MID = 320;
    private static final int CAPTURE_LONG_EDGE_HEAVY = 220;
    private static final long CAPTURE_INTERVAL_MS = 50L; // ~20 FPS
    private static final long PREF_REFRESH_INTERVAL_MS = 1000L;

    private final VirtualController controller;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final int[] surfaceLoc = new int[2];
    private final int[] elemLoc = new int[2];
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();

    private final Bitmap[] buffers = new Bitmap[2];
    private int writeIndex = 0;
    private volatile Bitmap readyBitmap = null;
    private volatile int surfaceScreenLeft = 0;
    private volatile int surfaceScreenTop = 0;
    private volatile int surfaceWidth = 0;
    private volatile int surfaceHeight = 0;

    private boolean running = false;
    private boolean inFlight = false;
    private int cachedBlurPct = 100;
    private int cachedBlurRadius = 4;
    private int cachedCaptureLongEdge = CAPTURE_LONG_EDGE_MID;
    private long lastPrefRefreshMs = 0L;

    private final Runnable captureTick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            requestFrameCapture();
            handler.postDelayed(this, CAPTURE_INTERVAL_MS);
        }
    };

    public OscLiveBlurHelper(VirtualController controller, Context context) {
        this.controller = controller;
        this.context = context;
    }

    private void refreshBlurPreferenceIfNeeded(boolean force) {
        long now = SystemClock.uptimeMillis();
        if (!force && now - lastPrefRefreshMs < PREF_REFRESH_INTERVAL_MS) {
            return;
        }
        lastPrefRefreshMs = now;
        int blurPct = Math.max(0, Math.min(200, com.limelight.profiles.ProfilesManager.getInstance()
                .getOverlayingSharedPreferences(context)
                .getInt(com.limelight.preferences.PreferenceConfiguration.OSC_BLUR_AMOUNT_PREF_STRING,
                        com.limelight.preferences.PreferenceConfiguration.DEFAULT_OSC_BLUR_AMOUNT)));
        cachedBlurPct = blurPct;

        // Higher capture resolution at low blur prevents coarse downscale fuzziness;
        // smoothly step down resolution at heavy blur to keep CPU cost minimal.
        if (blurPct <= 40) {
            cachedCaptureLongEdge = CAPTURE_LONG_EDGE_CRISP;
        } else if (blurPct <= 100) {
            float t = (blurPct - 40f) / 60f;
            cachedCaptureLongEdge = Math.round(CAPTURE_LONG_EDGE_CRISP + (CAPTURE_LONG_EDGE_MID - CAPTURE_LONG_EDGE_CRISP) * t);
        } else {
            float t = (blurPct - 100f) / 100f;
            cachedCaptureLongEdge = Math.round(CAPTURE_LONG_EDGE_MID + (CAPTURE_LONG_EDGE_HEAVY - CAPTURE_LONG_EDGE_MID) * t);
        }

        if (blurPct == 0) {
            cachedBlurRadius = 0;
        } else {
            // Perceptual curve: 1-35% gives ultra-fine crisp frost (radius 1-2 on 560px buffer),
            // 100% gives medium frost (radius ~4 on 320px), 200% gives rich deep blur (radius 9 on 220px).
            double norm = blurPct / 200.0;
            cachedBlurRadius = Math.max(1, Math.min(10, (int) Math.round(0.6 + 8.4 * Math.pow(norm, 1.35))));
        }
    }

    /**
     * Immediately reloads the OSC live-blur intensity preference so mid-stream slider adjustments
     * take effect on the very next frame instead of waiting for the periodic preference refresh.
     */
    public void refreshBlurPreferenceNow() {
        refreshBlurPreferenceIfNeeded(true);
        if (running && !inFlight) {
            requestFrameCapture();
        }
    }

    public void start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        if (running) {
            return;
        }
        running = true;
        refreshBlurPreferenceIfNeeded(true);
        handler.removeCallbacks(captureTick);
        handler.post(captureTick);
    }

    public void stop() {
        running = false;
        inFlight = false;
        handler.removeCallbacks(captureTick);
        readyBitmap = null;
    }

    private void requestFrameCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || inFlight) {
            return;
        }
        Activity activity = UiHelper.findActivity(context);
        if (activity == null || activity.isFinishing()) {
            return;
        }
        SurfaceView surfaceView = UiHelper.findStreamSurfaceView(activity);
        if (surfaceView == null || surfaceView.getWidth() <= 0 || surfaceView.getHeight() <= 0
                || !surfaceView.getHolder().getSurface().isValid()) {
            // Standalone OSC editor fallback: invalidate elements periodically against live starfield
            View root = activity.findViewById(android.R.id.content);
            View portalCandidate = root != null ? root.findViewWithTag("oneui_portal_starfield_bg") : null;
            if (portalCandidate instanceof com.limelight.ui.PortalShaderView) {
                List<VirtualControllerElement> elems = controller.getElements();
                for (int i = 0; i < elems.size(); i++) {
                    VirtualControllerElement el = elems.get(i);
                    if (el.getVisibility() == View.VISIBLE) {
                        el.invalidate();
                    }
                }
            }
            return;
        }

        refreshBlurPreferenceIfNeeded(false);

        final int sw = surfaceView.getWidth();
        final int sh = surfaceView.getHeight();
        surfaceView.getLocationOnScreen(surfaceLoc);
        final int sl = surfaceLoc[0];
        final int st = surfaceLoc[1];

        int longEdge = Math.min(Math.max(sw, sh), Math.max(120, cachedCaptureLongEdge));
        int bw;
        int bh;
        if (sw >= sh) {
            bw = longEdge;
            bh = Math.max(16, Math.round(longEdge * (sh / (float) sw)));
        } else {
            bh = longEdge;
            bw = Math.max(16, Math.round(longEdge * (sw / (float) sh)));
        }

        if (buffers[0] == null || buffers[0].getWidth() != bw || buffers[0].getHeight() != bh) {
            buffers[0] = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            buffers[1] = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            readyBitmap = null;
        }

        final int idx = writeIndex;
        final Bitmap target = buffers[idx];
        inFlight = true;
        try {
            PixelCopy.request(surfaceView, target, copyResult -> {
                inFlight = false;
                if (!running || copyResult != PixelCopy.SUCCESS) {
                    return;
                }
                int blurRadius = cachedBlurRadius;
                if (blurRadius > 0) {
                    UiHelper.fastBoxBlur(target, blurRadius);
                }
                surfaceScreenLeft = sl;
                surfaceScreenTop = st;
                surfaceWidth = sw;
                surfaceHeight = sh;
                readyBitmap = target;
                writeIndex = 1 - idx;

                List<VirtualControllerElement> elems = controller.getElements();
                for (int i = 0; i < elems.size(); i++) {
                    VirtualControllerElement el = elems.get(i);
                    if (el.getVisibility() == View.VISIBLE) {
                        el.invalidate();
                    }
                }
            }, handler);
        } catch (IllegalArgumentException | IllegalStateException e) {
            inFlight = false;
        }
    }

    /**
     * Draws the live blurred stream (or starfield backdrop in editor mode) located directly behind
     * {@code view}'s local {@code bounds}, clipped to {@code clipPath}. Returns true if drawn.
     */
    public boolean drawLiveBlurForBounds(Canvas canvas, View view, Path clipPath, RectF bounds, float alphaFactor) {
        if (alphaFactor <= 0.005f) {
            return false;
        }
        Bitmap bmp = readyBitmap;
        int sw = surfaceWidth;
        int sh = surfaceHeight;
        int originL = surfaceScreenLeft;
        int originT = surfaceScreenTop;

        if (bmp == null || bmp.isRecycled() || sw <= 0 || sh <= 0) {
            View rootView = view.getRootView();
            View portalCandidate = rootView != null ? rootView.findViewWithTag("oneui_portal_starfield_bg") : null;
            if (portalCandidate instanceof com.limelight.ui.PortalShaderView
                    && portalCandidate.getWidth() > 0 && portalCandidate.getHeight() > 0) {
                Bitmap portalBmp = ((com.limelight.ui.PortalShaderView) portalCandidate).getBlurredBitmap();
                if (portalBmp != null && !portalBmp.isRecycled()) {
                    portalCandidate.getLocationOnScreen(surfaceLoc);
                    bmp = portalBmp;
                    originL = surfaceLoc[0];
                    originT = surfaceLoc[1];
                    sw = portalCandidate.getWidth();
                    sh = portalCandidate.getHeight();
                }
            }
        }
        if (bmp == null || bmp.isRecycled() || sw <= 0 || sh <= 0) {
            return false;
        }

        view.getLocationOnScreen(elemLoc);
        float screenLeft = elemLoc[0] + bounds.left;
        float screenTop = elemLoc[1] + bounds.top;
        float screenRight = elemLoc[0] + bounds.right;
        float screenBottom = elemLoc[1] + bounds.bottom;

        int bw = bmp.getWidth();
        int bh = bmp.getHeight();
        float scaleX = bw / (float) sw;
        float scaleY = bh / (float) sh;

        int srcL = Math.max(0, Math.min(bw - 1, Math.round((screenLeft - originL) * scaleX)));
        int srcT = Math.max(0, Math.min(bh - 1, Math.round((screenTop - originT) * scaleY)));
        int srcR = Math.max(srcL + 1, Math.min(bw, Math.round((screenRight - originL) * scaleX)));
        int srcB = Math.max(srcT + 1, Math.min(bh, Math.round((screenBottom - originT) * scaleY)));

        srcRect.set(srcL, srcT, srcR, srcB);
        dstRect.set(bounds);
        bitmapPaint.setAlpha(Math.max(0, Math.min(255, Math.round(alphaFactor * 255f))));

        canvas.save();
        canvas.clipPath(clipPath);
        canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint);
        canvas.restore();
        return true;
    }
}
