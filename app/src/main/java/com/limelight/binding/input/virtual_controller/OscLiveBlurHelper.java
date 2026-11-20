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
    private static final int CAPTURE_LONG_EDGE = 160;
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
    private int cachedBlurRadius = 4;
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
        int blurPct = com.limelight.profiles.ProfilesManager.getInstance()
                .getOverlayingSharedPreferences(context)
                .getInt(com.limelight.preferences.PreferenceConfiguration.OSC_BLUR_AMOUNT_PREF_STRING,
                        com.limelight.preferences.PreferenceConfiguration.DEFAULT_OSC_BLUR_AMOUNT);
        cachedBlurRadius = Math.max(0, Math.min(10, Math.round(4f * (blurPct / 100f))));
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

        final int sw = surfaceView.getWidth();
        final int sh = surfaceView.getHeight();
        surfaceView.getLocationOnScreen(surfaceLoc);
        final int sl = surfaceLoc[0];
        final int st = surfaceLoc[1];

        int bw;
        int bh;
        if (sw >= sh) {
            bw = CAPTURE_LONG_EDGE;
            bh = Math.max(16, Math.round(CAPTURE_LONG_EDGE * (sh / (float) sw)));
        } else {
            bh = CAPTURE_LONG_EDGE;
            bw = Math.max(16, Math.round(CAPTURE_LONG_EDGE * (sw / (float) sh)));
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
                refreshBlurPreferenceIfNeeded(false);
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
