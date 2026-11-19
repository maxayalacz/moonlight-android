package com.limelight.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.limelight.R;
import com.limelight.utils.UiHelper;

/**
 * Full-screen bidirectional PlayStation Portal–style OpenGL ES 3.0 stream transition overlay.
 *
 * Wraps {@link PortalShaderView} with a minimal HUD layer and drives:
 *  - Launch sequence: Phase 0 (Iris Close-In 450 ms) -> Phase 1 (Connecting sonar ripples)
 *    -> Phase 2 (Ready Snap 300 ms) -> Phase 3 (Live Stream Peephole 400 ms + 900 ms hold)
 *    -> Phase 4 (Iris Wipe Open 550 ms -> stop GL & View.GONE).
 *  - Disconnect sequence: Step 1 (Iris Close Over Stream 450 ms)
 *    -> Step 2 (Fade Back to Starfield 350 ms) -> Step 3 (Iris Open to App Background 500 ms).
 *  - Cancel sequence: smoothly returns to full-screen synced starfield before finishing.
 */
public class PortalConnectionOverlay extends FrameLayout {

    private static final long PHASE3_HUD_FADE_MS = 250L;
    private static final long FIRST_FRAME_FALLBACK_MS = 700L;
    private static final long CANCEL_REOPEN_MS = 400L;

    // Configurable durations loaded from UI Settings -> Portal Animation
    private long phase0IrisCloseMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_LAUNCH_CLOSE_MS;
    private long phase2ReadyMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_READY_MS;
    private long phase3PreviewFadeMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_PREVIEW_FADE_MS;
    private long phase3HoldMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_PREVIEW_HOLD_MS;
    private long phase4ExpandMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_EXPAND_MS;
    private long disconnectStep1IrisCloseMs = 450L;
    private long disconnectStep2FadeStarfieldMs = 350L;
    private long disconnectStep3IrisOpenMs = 500L;

    private void loadPreferences() {
        com.limelight.preferences.PreferenceConfiguration cfg =
                com.limelight.preferences.PreferenceConfiguration.readPreferences(getContext());
        phase0IrisCloseMs = Math.max(150L, cfg.portalLaunchCloseMs);
        phase2ReadyMs = Math.max(100L, cfg.portalReadyMs);
        phase3PreviewFadeMs = Math.max(100L, cfg.portalPreviewFadeMs);
        phase3HoldMs = Math.max(0L, cfg.portalPreviewHoldMs);
        phase4ExpandMs = Math.max(200L, cfg.portalExpandMs);
        long totalDisconnect = Math.max(300L, cfg.portalDisconnectMs);
        disconnectStep1IrisCloseMs = Math.max(100L, Math.round(totalDisconnect * 0.35f));
        disconnectStep2FadeStarfieldMs = Math.max(100L, Math.round(totalDisconnect * 0.27f));
        disconnectStep3IrisOpenMs = Math.max(100L, totalDisconnect - disconnectStep1IrisCloseMs - disconnectStep2FadeStarfieldMs);
        if (shaderView != null) {
            shaderView.refreshFromPrefs();
        }
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private PortalShaderView shaderView;
    private LinearLayout topHudContainer;
    private TextView statusTitleView;
    private TextView stageSubtitleView;
    private TextView cancelButton;

    private boolean active = false;
    private boolean disconnecting = false;
    private boolean canceling = false;
    private boolean phase0Completed = false;
    private boolean connectionStartedPending = false;
    private boolean phase2Started = false;
    private boolean phase2Completed = false;
    private boolean firstFrameReceived = false;
    private boolean phase3Started = false;

    private ValueAnimator activeAnimator;
    private Runnable onCancelAction;
    private Runnable onRevealCompleteAction;

    private final Runnable firstFrameFallbackRunnable = () -> {
        if (active && !disconnecting && !canceling && phase2Completed && !phase3Started) {
            startPhase3();
        }
    };

    private final Runnable phase3HoldRunnable = () -> {
        if (active && !disconnecting && !canceling) {
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

        shaderView = new PortalShaderView(context);
        addView(shaderView, new LayoutParams(
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
        cancelButton.setOnClickListener(v -> cancelWithReopen());

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
     * Launch Sequence: Phase 0 (Iris Close-In 450 ms from full-screen synced starfield)
     * followed automatically by Phase 1 (Connecting looping sonar ripples).
     */
    public void startConnecting(
            @Nullable String hostName,
            @Nullable String initialStage,
            @Nullable Runnable onCancel,
            @Nullable Runnable onRevealComplete) {
        cancelAnimatorsAndCallbacks();
        shaderView.stopRendering();
        loadPreferences();

        this.active = true;
        this.disconnecting = false;
        this.canceling = false;
        this.phase0Completed = false;
        this.connectionStartedPending = false;
        this.phase2Started = false;
        this.phase2Completed = false;
        this.firstFrameReceived = false;
        this.phase3Started = false;
        this.onCancelAction = onCancel;
        this.onRevealCompleteAction = onRevealComplete;

        // Start visually identical to the app's synced starfield background
        shaderView.setUniforms(0f, 0f, 1f);

        String titleText = (hostName != null && !hostName.trim().isEmpty())
                ? "Connecting to \"" + hostName.trim() + "\""
                : getContext().getString(R.string.conn_establishing_title);
        statusTitleView.setText(titleText);
        stageSubtitleView.setText(initialStage != null ? initialStage : "");

        topHudContainer.setVisibility(VISIBLE);
        topHudContainer.setAlpha(0f);
        statusTitleView.setAlpha(1f);
        stageSubtitleView.setAlpha(1f);
        cancelButton.setVisibility(VISIBLE);
        cancelButton.setAlpha(0f);

        setClickable(true);
        setFocusable(true);
        setVisibility(VISIBLE);
        bringToFront();

        shaderView.startRendering();

        // Fade in HUD while closing the iris from 1.0 -> 0.0
        topHudContainer.animate().alpha(1f).setDuration(phase0IrisCloseMs).start();
        cancelButton.animate().alpha(1f).setDuration(phase0IrisCloseMs).start();

        activeAnimator = ValueAnimator.ofFloat(1f, 0f);
        activeAnimator.setDuration(phase0IrisCloseMs);
        activeAnimator.setInterpolator(new LinearInterpolator());
        activeAnimator.addUpdateListener(anim -> shaderView.setExpandProgress((float) anim.getAnimatedValue()));
        activeAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean canceled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (canceled || !active || disconnecting || canceling) {
                    return;
                }
                shaderView.setExpandProgress(0f);
                phase0Completed = true;
                if (connectionStartedPending && !phase2Started) {
                    startPhase2();
                }
            }
        });
        activeAnimator.start();
    }

    /**
     * Updates the minimal stage subtitle while in Phases 0-1.
     */
    public void updateStageMessage(@Nullable final String stageText) {
        if (!active || disconnecting || canceling || phase2Started) {
            return;
        }
        mainHandler.post(() -> {
            if (active && !disconnecting && !canceling && !phase2Started && stageSubtitleView != null) {
                stageSubtitleView.setText(stageText != null ? stageText : "");
            }
        });
    }

    /**
     * Phase 2 — Connected / Ready Snap.
     */
    public void onConnectionStarted() {
        mainHandler.post(() -> {
            if (!active || disconnecting || canceling || phase2Started) {
                return;
            }
            if (!phase0Completed) {
                connectionStartedPending = true;
                return;
            }
            startPhase2();
        });
    }

    private void startPhase2() {
        if (!active || disconnecting || canceling || phase2Started) {
            return;
        }
        phase2Started = true;

        // Cross-fade status text to "Ready"
        statusTitleView.animate()
                .alpha(0f)
                .setDuration(110L)
                .withEndAction(() -> {
                    if (!active || disconnecting || canceling) {
                        return;
                    }
                    statusTitleView.setText("Ready");
                    statusTitleView.animate().alpha(1f).setDuration(140L).start();
                })
                .start();
        stageSubtitleView.animate().alpha(0f).setDuration(150L).start();

        cancelActiveAnimator();
        activeAnimator = ValueAnimator.ofFloat(shaderView.getReadyMix(), 1f);
        activeAnimator.setDuration(phase2ReadyMs);
        activeAnimator.setInterpolator(new DecelerateInterpolator());
        activeAnimator.addUpdateListener(anim -> shaderView.setReadyMix((float) anim.getAnimatedValue()));
        activeAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean canceled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (canceled || !active || disconnecting || canceling) {
                    return;
                }
                shaderView.setReadyMix(1f);
                phase2Completed = true;
                if (firstFrameReceived) {
                    startPhase3();
                } else {
                    mainHandler.postDelayed(firstFrameFallbackRunnable, FIRST_FRAME_FALLBACK_MS);
                }
            }
        });
        activeAnimator.start();
    }

    /**
     * Hooked from MediaCodecDecoderRenderer when the first decoded video frame is rendered.
     */
    public void onFirstVideoFrameRendered() {
        mainHandler.post(() -> {
            if (!active || disconnecting || canceling) {
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
     * Phase 3 — Live Stream Peephole.
     */
    private void startPhase3() {
        if (!active || disconnecting || canceling || phase3Started) {
            return;
        }
        phase3Started = true;
        mainHandler.removeCallbacks(firstFrameFallbackRunnable);

        // Fade all HUD elements to 0 alpha over 250 ms
        topHudContainer.animate().alpha(0f).setDuration(PHASE3_HUD_FADE_MS).start();
        cancelButton.animate().alpha(0f).setDuration(PHASE3_HUD_FADE_MS).withEndAction(() -> {
            cancelButton.setVisibility(GONE);
        }).start();

        // Pass input through as soon as the live peephole opens
        setClickable(false);
        setFocusable(false);

        cancelActiveAnimator();
        activeAnimator = ValueAnimator.ofFloat(shaderView.getPreviewMix(), 1f);
        activeAnimator.setDuration(phase3PreviewFadeMs);
        activeAnimator.setInterpolator(new DecelerateInterpolator());
        activeAnimator.addUpdateListener(anim -> shaderView.setPreviewMix((float) anim.getAnimatedValue()));
        activeAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean canceled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (canceled || !active || disconnecting || canceling) {
                    return;
                }
                shaderView.setPreviewMix(1f);
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
     * Phase 4 — Iris Wipe Open (linear; cubic easing handled inside shader).
     */
    private void startPhase4() {
        if (!active || disconnecting || canceling) {
            return;
        }
        cancelActiveAnimator();
        activeAnimator = ValueAnimator.ofFloat(shaderView.getExpandProgress(), 1f);
        activeAnimator.setDuration(phase4ExpandMs);
        activeAnimator.setInterpolator(new LinearInterpolator());
        activeAnimator.addUpdateListener(anim -> shaderView.setExpandProgress((float) anim.getAnimatedValue()));
        activeAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean canceled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (canceled || !active || disconnecting || canceling) {
                    return;
                }
                shaderView.setExpandProgress(1f);
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
     * Smooth cancellation during Phases 0–3: fades u_previewMix -> 0 and expands u_expandProgress -> 1
     * back into the synced full-screen starfield before invoking onCancelAction.
     * Keeps the overlay visible holding the starfield until Game finishes so no stream frame flashes.
     */
    public void cancelWithReopen() {
        mainHandler.post(() -> {
            if (!active || disconnecting || canceling) {
                return;
            }
            canceling = true;
            cancelAnimatorsAndCallbacks();

            topHudContainer.animate().alpha(0f).setDuration(180L).start();
            cancelButton.animate().alpha(0f).setDuration(180L).start();

            final float startPreview = shaderView.getPreviewMix();
            final float startReady = shaderView.getReadyMix();
            final float startExpand = shaderView.getExpandProgress();
            final Runnable cancel = onCancelAction;

            activeAnimator = ValueAnimator.ofFloat(0f, 1f);
            activeAnimator.setDuration(CANCEL_REOPEN_MS);
            activeAnimator.setInterpolator(new LinearInterpolator());
            activeAnimator.addUpdateListener(anim -> {
                float t = (float) anim.getAnimatedValue();
                shaderView.setPreviewMix(startPreview * (1f - t));
                shaderView.setReadyMix(startReady * (1f - t));
                shaderView.setExpandProgress(startExpand + (1f - startExpand) * t);
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
                    shaderView.setUniforms(0f, 0f, 1f);
                    if (cancel != null) {
                        cancel.run();
                    }
                }
            });
            activeAnimator.start();
        });
    }

    /**
     * Bidirectional Disconnect / Close Sequence (Live Stream -> App Menu):
     *  - Step 1: Iris close over live stream (u_expandProgress 1 -> 0, preview & ready = 1)
     *  - Step 2: Fade back to starfield (u_previewMix & u_readyMix 1 -> 0)
     *  - Step 3: Iris open to full-screen synced starfield (u_expandProgress 0 -> 1)
     * Keeps the overlay opaque holding the full-screen starfield through Game.finish() so the
     * underlying video SurfaceView never flashes a frame during window teardown.
     */
    public void startDisconnecting(@Nullable final Runnable onComplete) {
        startDisconnecting(null, onComplete);
    }

    public void startDisconnecting(
            @Nullable final Runnable onBeginTeardown,
            @Nullable final Runnable onComplete) {
        mainHandler.post(() -> {
            if (disconnecting) {
                return;
            }
            cancelAnimatorsAndCallbacks();
            shaderView.stopRendering();
            loadPreferences();

            this.active = true;
            this.disconnecting = true;
            this.canceling = false;
            shaderView.setUniforms(1f, 1f, 1f);

            topHudContainer.setVisibility(GONE);
            cancelButton.setVisibility(GONE);
            setClickable(false);
            setFocusable(false);
            setVisibility(VISIBLE);
            bringToFront();

            shaderView.startRendering();

            // Step 1: Iris Close Over Stream
            activeAnimator = ValueAnimator.ofFloat(1f, 0f);
            activeAnimator.setDuration(disconnectStep1IrisCloseMs);
            activeAnimator.setInterpolator(new LinearInterpolator());
            activeAnimator.addUpdateListener(anim -> shaderView.setExpandProgress((float) anim.getAnimatedValue()));
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
                    shaderView.setExpandProgress(0f);
                    if (onBeginTeardown != null) {
                        onBeginTeardown.run();
                    }
                    runDisconnectStep2(onComplete);
                }
            });
            activeAnimator.start();
        });
    }

    private void runDisconnectStep2(@Nullable final Runnable onComplete) {
        // Step 2: Fade Back to Starfield
        activeAnimator = ValueAnimator.ofFloat(1f, 0f);
        activeAnimator.setDuration(disconnectStep2FadeStarfieldMs);
        activeAnimator.setInterpolator(new DecelerateInterpolator());
        activeAnimator.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            shaderView.setPreviewMix(v);
            shaderView.setReadyMix(v);
        });
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
                shaderView.setPreviewMix(0f);
                shaderView.setReadyMix(0f);
                runDisconnectStep3(onComplete);
            }
        });
        activeAnimator.start();
    }

    private void runDisconnectStep3(@Nullable final Runnable onComplete) {
        // Step 3: Iris Open to App Background
        activeAnimator = ValueAnimator.ofFloat(0f, 1f);
        activeAnimator.setDuration(disconnectStep3IrisOpenMs);
        activeAnimator.setInterpolator(new LinearInterpolator());
        activeAnimator.addUpdateListener(a -> shaderView.setExpandProgress((float) a.getAnimatedValue()));
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
                // Keep overlay visible at full-screen synced starfield until Game.onDestroy()
                // so the video SurfaceView underneath never flashes a frame before AppView appears.
                shaderView.setUniforms(0f, 0f, 1f);
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
        activeAnimator.start();
    }

    /**
     * Stops the GL render loop, releases EGL/GL resources cleanly, and hides the overlay with
     * zero overdraw during gameplay.
     */
    public void releaseAndHide() {
        cancelAnimatorsAndCallbacks();
        if (shaderView != null) {
            shaderView.stopRendering();
        }

        active = false;
        disconnecting = false;
        canceling = false;
        phase0Completed = false;
        connectionStartedPending = false;
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

    @Override
    protected void onDetachedFromWindow() {
        releaseAndHide();
        super.onDetachedFromWindow();
    }

    private int dp(int dp) {
        return Math.max(1, Math.round(UiHelper.dpToPx(getContext(), dp)));
    }
}
