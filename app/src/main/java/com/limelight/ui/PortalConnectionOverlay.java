package com.limelight.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
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
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.limelight.R;
import com.limelight.utils.UiHelper;

/**
 * Full-screen bidirectional PlayStation Portal–style OpenGL ES 3.0 stream transition overlay.
 *
 * Wraps {@link PortalShaderView} with a seamless starfield handoff backdrop & minimal HUD layer:
 *  - Launch sequence: Handoff starfield until first GL frame -> Phase 0 (Iris Close-In)
 *    -> Phase 1 (Connecting sonar ripples) -> Phase 2 (Ready Snap)
 *    -> Phase 3 (Live Stream Peephole + hold) -> Phase 4 (Iris Wipe Open -> stop GL & View.GONE).
 *  - Disconnect sequence: Wait for first transparent GL frame -> Step 1 (Iris Close Over Stream)
 *    -> Step 2 (Fade Back to Starfield) -> Step 3 (Iris Open to App Background + capture handoff).
 *  - Cancel sequence: smoothly returns to full-screen synced starfield and captures handoff frame.
 */
public class PortalConnectionOverlay extends FrameLayout {

    private static final long PHASE3_HUD_FADE_MS = 240L;
    private static final long FIRST_FRAME_FALLBACK_MS = 700L;
    private static final long GL_FIRST_FRAME_TIMEOUT_MS = 160L;
    private static final long CANCEL_REOPEN_MS = 380L;

    // Configurable durations loaded from Theme Studio -> Animation tab
    private long phase0IrisCloseMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_LAUNCH_CLOSE_MS;
    private long phase0PulseCoilMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_PULSE_COIL_MS;
    private boolean decouplePulseCoil = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_DECOUPLE_PULSE_COIL;
    private long phase1SonarHoldMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_SONAR_HOLD_MS;
    private long phase2ReadyMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_READY_MS;
    private long phase3ChargeMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_CHARGE_MS;
    private long phase3PreviewFadeMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_PREVIEW_FADE_MS;
    private long phase3HoldMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_PREVIEW_HOLD_MS;
    private long phase4ExpandMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_EXPAND_MS;
    private long disconnectStep1IrisCloseMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_DISCONNECT_CLOSE_MS;
    private long disconnectStep2FadeStarfieldMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_DISCONNECT_FADE_MS;
    private long disconnectStep3IrisOpenMs = com.limelight.preferences.PreferenceConfiguration.DEFAULT_PORTAL_DISCONNECT_OPEN_MS;

    private void loadPreferences() {
        com.limelight.preferences.PreferenceConfiguration cfg =
                com.limelight.preferences.PreferenceConfiguration.readPreferences(getContext());
        phase0IrisCloseMs = Math.max(0L, cfg.portalLaunchCloseMs);
        phase0PulseCoilMs = Math.max(0L, cfg.portalPulseCoilMs);
        decouplePulseCoil = cfg.portalDecouplePulseCoil;
        phase1SonarHoldMs = Math.max(0L, cfg.portalSonarHoldMs);
        phase2ReadyMs = Math.max(0L, cfg.portalReadyMs);
        phase3ChargeMs = Math.max(0L, cfg.portalChargeMs);
        phase3PreviewFadeMs = Math.max(0L, cfg.portalPreviewFadeMs);
        phase3HoldMs = Math.max(0L, cfg.portalPreviewHoldMs);
        phase4ExpandMs = Math.max(0L, cfg.portalExpandMs);
        disconnectStep1IrisCloseMs = Math.max(0L, cfg.portalDisconnectCloseMs);
        disconnectStep2FadeStarfieldMs = Math.max(0L, cfg.portalDisconnectFadeMs);
        disconnectStep3IrisOpenMs = Math.max(0L, cfg.portalDisconnectOpenMs);
        if (handoffBackdropView != null) {
            handoffBackdropView.setBackgroundColor(AppThemePalette.getCurrent(getContext()).background);
        }
        if (shaderView != null) {
            shaderView.refreshFromPrefs();
        }
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ImageView handoffBackdropView;
    private PortalShaderView shaderView;
    private LinearLayout topHudContainer;
    private TextView statusTitleView;
    private TextView stageSubtitleView;
    private TextView cancelButton;

    private boolean active = false;
    private boolean disconnecting = false;
    private boolean canceling = false;
    private boolean phase0AnimatorStarted = false;
    private boolean phase0Completed = false;
    private boolean sonarHoldCompleted = false;
    private boolean connectionStartedPending = false;
    private boolean phase2Started = false;
    private boolean phase3ChargeStarted = false;
    private boolean phase3ChargeCompleted = false;
    private boolean firstFrameReceived = false;
    private boolean phase3Started = false;
    private boolean disconnectStep1Started = false;

    private ValueAnimator activeAnimator;
    private ValueAnimator pulseMorphAnimator;
    private long pulseMorphTargetEndMs = 0L;
    private Runnable onCancelAction;
    private Runnable onRevealCompleteAction;
    private Runnable pendingFirstFrameFallback;

    private final Runnable sonarHoldRunnable = () -> {
        if (active && !disconnecting && !canceling) {
            sonarHoldCompleted = true;
            if (connectionStartedPending && !phase2Started) {
                startPhase2();
            }
        }
    };

    private final Runnable firstFrameFallbackRunnable = () -> {
        if (active && !disconnecting && !canceling && phase3ChargeCompleted && !phase3Started) {
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

        // Seamless starfield handoff frame rendered behind PortalShaderView on Frame 0 while
        // EGL/GLES 3.0 shaders initialize so launch never flashes solid black.
        handoffBackdropView = new ImageView(context);
        handoffBackdropView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        handoffBackdropView.setBackgroundColor(0xFF0B0E1C);
        handoffBackdropView.setVisibility(GONE);
        handoffBackdropView.setClickable(false);
        handoffBackdropView.setFocusable(false);
        addView(handoffBackdropView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

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
     * Launch Sequence: holds the synced starfield handoff until the first GL frame renders, then
     * runs Phase 0 (Iris Close-In) followed automatically by Phase 1 (Connecting sonar ripples).
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
        this.phase0AnimatorStarted = false;
        this.phase0Completed = false;
        this.sonarHoldCompleted = false;
        this.connectionStartedPending = false;
        this.phase2Started = false;
        this.phase3ChargeStarted = false;
        this.phase3ChargeCompleted = false;
        this.firstFrameReceived = false;
        this.phase3Started = false;
        this.disconnectStep1Started = false;
        this.onCancelAction = onCancel;
        this.onRevealCompleteAction = onRevealComplete;

        // Start visually identical to the app's synced starfield background
        shaderView.setIsDisconnecting(false);
        shaderView.setUniforms(0f, 0f, 0f, 1f, 0f);

        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        Bitmap snap = PortalShaderView.getHandoffBitmap(landscape);
        if (snap != null) {
            handoffBackdropView.setImageBitmap(snap);
        } else {
            handoffBackdropView.setImageDrawable(null);
        }
        handoffBackdropView.setVisibility(VISIBLE);

        String titleText = (hostName != null && !hostName.trim().isEmpty())
                ? "Connecting to \"" + hostName.trim() + "\""
                : getContext().getString(R.string.conn_establishing_title);
        statusTitleView.setText(titleText);
        stageSubtitleView.setText(initialStage != null ? initialStage : "");

        topHudContainer.setVisibility(VISIBLE);
        topHudContainer.setAlpha(0f);
        topHudContainer.setTranslationY(dp(8));
        statusTitleView.setAlpha(1f);
        stageSubtitleView.setAlpha(1f);
        cancelButton.setVisibility(VISIBLE);
        cancelButton.setAlpha(0f);
        cancelButton.setTranslationY(dp(8));

        setClickable(true);
        setFocusable(true);
        setVisibility(VISIBLE);
        bringToFront();

        final Runnable triggerPhase0 = this::beginPhase0Animator;
        pendingFirstFrameFallback = triggerPhase0;
        shaderView.setFirstFrameListener(triggerPhase0);
        mainHandler.postDelayed(triggerPhase0, GL_FIRST_FRAME_TIMEOUT_MS);

        shaderView.startRendering();
    }

    private void beginPhase0Animator() {
        if (phase0AnimatorStarted || !active || disconnecting || canceling) {
            return;
        }
        phase0AnimatorStarted = true;
        clearPendingFirstFrameFallback();
        shaderView.setFirstFrameListener(null);
        handoffBackdropView.setVisibility(GONE);

        PathInterpolator hudEase = new PathInterpolator(0.22f, 1f, 0.36f, 1f);
        topHudContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(Math.min(360L, Math.max(80L, phase0IrisCloseMs)))
                .setInterpolator(hudEase)
                .start();
        cancelButton.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(Math.min(360L, Math.max(80L, phase0IrisCloseMs)))
                .setInterpolator(hudEase)
                .start();

        // Optional decoupled pulse coil; by default morph is coupled directly to Launch Iris Close (1 - expand)
        if (decouplePulseCoil) {
            startPulseCoilAnimator(phase0PulseCoilMs);
        }

        activeAnimator = ValueAnimator.ofFloat(1f, 0f);
        activeAnimator.setDuration(phase0IrisCloseMs);
        activeAnimator.setInterpolator(new LinearInterpolator());
        activeAnimator.addUpdateListener(anim -> {
            float e = (float) anim.getAnimatedValue();
            shaderView.setExpandProgress(e);
            if (!decouplePulseCoil) {
                shaderView.setPulseMorph(1f - e);
            }
        });
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
                if (!decouplePulseCoil) {
                    shaderView.setPulseMorph(1f);
                }
                phase0Completed = true;
                if (phase1SonarHoldMs <= 0L) {
                    sonarHoldCompleted = true;
                    if (connectionStartedPending && !phase2Started) {
                        startPhase2();
                    }
                } else {
                    mainHandler.postDelayed(sonarHoldRunnable, phase1SonarHoldMs);
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
            connectionStartedPending = true;
            if (!phase0Completed || !sonarHoldCompleted) {
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

        // When decoupled, gently speed up pulse coil if needed so it finishes before Charge completes (~85% of Charge)
        if (decouplePulseCoil) {
            acceleratePulseCoilIfNeeded(phase2ReadyMs + Math.round(phase3ChargeMs * 0.85f));
        }

        // Cross-fade status text to "Ready"
        statusTitleView.animate()
                .alpha(0f)
                .setDuration(110L)
                .setInterpolator(new PathInterpolator(0.4f, 0f, 1f, 1f))
                .withEndAction(() -> {
                    if (!active || disconnecting || canceling) {
                        return;
                    }
                    statusTitleView.setText("Ready");
                    statusTitleView.animate()
                            .alpha(1f)
                            .setDuration(160L)
                            .setInterpolator(new PathInterpolator(0.22f, 1f, 0.36f, 1f))
                            .start();
                })
                .start();
        stageSubtitleView.animate()
                .alpha(0f)
                .setDuration(150L)
                .setInterpolator(new PathInterpolator(0.4f, 0f, 1f, 1f))
                .start();

        cancelActiveAnimator();
        activeAnimator = ValueAnimator.ofFloat(shaderView.getReadyMix(), 1f);
        activeAnimator.setDuration(phase2ReadyMs);
        activeAnimator.setInterpolator(new LinearInterpolator());
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
                phase3ChargeCompleted = true;
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
            if (phase3ChargeCompleted && !phase3Started) {
                mainHandler.removeCallbacks(firstFrameFallbackRunnable);
                startPhase3();
            }
        });
    }

    /**
     * Phase 3 — Simultaneous linear Ready -> Charge + Stream Fade-In flowing straight into Phase 4.
     */
    private void startPhase3() {
        if (!active || disconnecting || canceling || phase3Started) {
            return;
        }
        phase3Started = true;
        phase3ChargeStarted = true;
        mainHandler.removeCallbacks(firstFrameFallbackRunnable);

        // Hard guarantee when decoupled: pulse coil must finish before the Charge sequence ends (~85% of Charge)
        if (decouplePulseCoil) {
            acceleratePulseCoilIfNeeded(Math.round(phase3ChargeMs * 0.85f));
        }

        // Fade all HUD elements to 0 alpha smoothly at the start of Phase 3
        PathInterpolator fadeOut = new PathInterpolator(0.4f, 0f, 1f, 1f);
        topHudContainer.animate()
                .alpha(0f)
                .translationY(-dp(6))
                .setDuration(PHASE3_HUD_FADE_MS)
                .setInterpolator(fadeOut)
                .start();
        cancelButton.animate()
                .alpha(0f)
                .translationY(dp(6))
                .setDuration(PHASE3_HUD_FADE_MS)
                .setInterpolator(fadeOut)
                .withEndAction(() -> cancelButton.setVisibility(GONE))
                .start();

        // Pass input through as soon as the live peephole begins opening
        setClickable(false);
        setFocusable(false);

        final long chargeDur = Math.max(0L, phase3ChargeMs);
        final long fadeDur = Math.max(0L, phase3PreviewFadeMs);
        final long totalPhase3Ms = Math.max(chargeDur, fadeDur);

        cancelActiveAnimator();
        if (totalPhase3Ms <= 0L) {
            shaderView.setChargeMix(1f);
            shaderView.setPreviewMix(1f);
            startPhase4();
            return;
        }

        final float startCharge = shaderView.getChargeMix();
        final float startPreview = shaderView.getPreviewMix();
        activeAnimator = ValueAnimator.ofFloat(0f, (float) totalPhase3Ms);
        activeAnimator.setDuration(totalPhase3Ms);
        activeAnimator.setInterpolator(new LinearInterpolator());
        activeAnimator.addUpdateListener(anim -> {
            float elapsed = (float) anim.getAnimatedValue();
            float cT = chargeDur > 0L ? Math.max(0f, Math.min(1f, elapsed / (float) chargeDur)) : 1f;
            float pT = fadeDur > 0L ? Math.max(0f, Math.min(1f, elapsed / (float) fadeDur)) : 1f;
            shaderView.setChargeMix(startCharge + (1f - startCharge) * cT);
            shaderView.setPreviewMix(startPreview + (1f - startPreview) * pT);
        });
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
                shaderView.setChargeMix(1f);
                shaderView.setPreviewMix(1f);
                startPhase4();
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
                final Runnable complete = onRevealCompleteAction;
                // Wait one animation frame so the fully-transparent expand=1.0 frame presents cleanly
                postOnAnimation(() -> {
                    if (!active || disconnecting || canceling) {
                        return;
                    }
                    releaseAndHide();
                    if (complete != null) {
                        complete.run();
                    }
                });
            }
        });
        activeAnimator.start();
    }

    /**
     * Smooth cancellation during Phases 0–3: fades u_previewMix -> 0 and expands u_expandProgress -> 1
     * back into the synced full-screen starfield before invoking onCancelAction.
     * Captures the final starfield handoff frame and keeps the overlay visible until Game finishes.
     */
    public void cancelWithReopen() {
        mainHandler.post(() -> {
            if (!active || disconnecting || canceling) {
                return;
            }
            canceling = true;
            cancelAnimatorsAndCallbacks();
            handoffBackdropView.setVisibility(GONE);

            topHudContainer.animate().alpha(0f).setDuration(180L).start();
            cancelButton.animate().alpha(0f).setDuration(180L).start();

            shaderView.setIsDisconnecting(true);

            final float startPreview = shaderView.getPreviewMix();
            final float startCharge = shaderView.getChargeMix();
            final float startReady = shaderView.getReadyMix();
            final float startExpand = shaderView.getExpandProgress();
            final float startPulseMorph = shaderView.getPulseMorph();
            final Runnable cancel = onCancelAction;

            activeAnimator = ValueAnimator.ofFloat(0f, 1f);
            activeAnimator.setDuration(disconnectStep3IrisOpenMs);
            activeAnimator.setInterpolator(new LinearInterpolator());
            activeAnimator.addUpdateListener(anim -> {
                float t = (float) anim.getAnimatedValue();
                shaderView.setPreviewMix(startPreview * (1f - t));
                shaderView.setChargeMix(startCharge * (1f - t));
                shaderView.setReadyMix(startReady * (1f - t));
                shaderView.setExpandProgress(startExpand + (1f - startExpand) * t);
                shaderView.setPulseMorph(startPulseMorph * (1f - t));
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
                    shaderView.setIsDisconnecting(false);
                    shaderView.setUniforms(0f, 0f, 0f, 1f, 0f);
                    PortalShaderView.captureHandoffFrame(shaderView);
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
     *  - Wait for first transparent GL frame so Step 1 starts smoothly from expand = 1.0
     *  - Step 1: Iris close over live stream (u_expandProgress 1 -> 0, preview & charge & ready = 1, u_isDisconnecting = 1)
     *  - Step 2: Fade back to starfield (u_previewMix & u_chargeMix & u_readyMix 1 -> 0, LinearInterpolator)
     *  - Step 3: Iris open to full-screen synced starfield (u_expandProgress 0 -> 1, u_pulseMorph 1 -> 0) + capture handoff
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
            this.disconnectStep1Started = false;
            shaderView.setIsDisconnecting(true);
            shaderView.setUniforms(1f, 1f, 1f, 1f, 1f);

            handoffBackdropView.setVisibility(GONE);
            topHudContainer.setVisibility(GONE);
            cancelButton.setVisibility(GONE);
            setClickable(false);
            setFocusable(false);
            setVisibility(VISIBLE);
            bringToFront();

            final Runnable triggerStep1 = () -> beginDisconnectStep1(onBeginTeardown, onComplete);
            pendingFirstFrameFallback = triggerStep1;
            shaderView.setFirstFrameListener(triggerStep1);
            mainHandler.postDelayed(triggerStep1, GL_FIRST_FRAME_TIMEOUT_MS);

            shaderView.startRendering();
        });
    }

    private void beginDisconnectStep1(
            @Nullable final Runnable onBeginTeardown,
            @Nullable final Runnable onComplete) {
        if (disconnectStep1Started || !disconnecting) {
            return;
        }
        disconnectStep1Started = true;
        clearPendingFirstFrameFallback();
        shaderView.setFirstFrameListener(null);

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
    }

    private void runDisconnectStep2(@Nullable final Runnable onComplete) {
        // Step 2: Linearly fade back to starfield
        activeAnimator = ValueAnimator.ofFloat(1f, 0f);
        activeAnimator.setDuration(disconnectStep2FadeStarfieldMs);
        activeAnimator.setInterpolator(new LinearInterpolator());
        activeAnimator.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            shaderView.setPreviewMix(v);
            shaderView.setChargeMix(v);
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
                shaderView.setChargeMix(0f);
                shaderView.setReadyMix(0f);
                runDisconnectStep3(onComplete);
            }
        });
        activeAnimator.start();
    }

    private void runDisconnectStep3(@Nullable final Runnable onComplete) {
        // Step 3: Iris Open to App Background + synced forward spiral peel-off (1 -> 0)
        activeAnimator = ValueAnimator.ofFloat(0f, 1f);
        activeAnimator.setDuration(disconnectStep3IrisOpenMs);
        activeAnimator.setInterpolator(new LinearInterpolator());
        activeAnimator.addUpdateListener(a -> {
            float e = (float) a.getAnimatedValue();
            shaderView.setExpandProgress(e);
            shaderView.setPulseMorph(1f - e);
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
                // Keep overlay visible at full-screen synced starfield until Game.onStop()
                // and capture the handoff frame so AppView resumes with zero solid-color flash.
                shaderView.setIsDisconnecting(false);
                shaderView.setUniforms(0f, 0f, 0f, 1f, 0f);
                PortalShaderView.captureHandoffFrame(shaderView);
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
            shaderView.setIsDisconnecting(false);
            shaderView.stopRendering();
        }
        if (handoffBackdropView != null) {
            handoffBackdropView.setVisibility(GONE);
            handoffBackdropView.setImageDrawable(null);
        }

        active = false;
        disconnecting = false;
        canceling = false;
        phase0AnimatorStarted = false;
        phase0Completed = false;
        sonarHoldCompleted = false;
        connectionStartedPending = false;
        phase2Started = false;
        phase3ChargeStarted = false;
        phase3ChargeCompleted = false;
        firstFrameReceived = false;
        phase3Started = false;
        disconnectStep1Started = false;
        onCancelAction = null;
        onRevealCompleteAction = null;

        setClickable(false);
        setFocusable(false);
        setVisibility(GONE);
    }

    private void startPulseCoilAnimator(long durationMs) {
        cancelPulseMorphAnimator();
        if (shaderView == null) {
            return;
        }
        float currentMorph = shaderView.getPulseMorph();
        if (currentMorph >= 0.999f || durationMs <= 0L) {
            shaderView.setPulseMorph(1f);
            pulseMorphTargetEndMs = 0L;
            return;
        }
        pulseMorphTargetEndMs = android.os.SystemClock.uptimeMillis() + durationMs;
        pulseMorphAnimator = ValueAnimator.ofFloat(currentMorph, 1f);
        pulseMorphAnimator.setDuration(durationMs);
        pulseMorphAnimator.setInterpolator(new LinearInterpolator());
        pulseMorphAnimator.addUpdateListener(anim -> shaderView.setPulseMorph((float) anim.getAnimatedValue()));
        pulseMorphAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean canceled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!canceled && active && !disconnecting && !canceling) {
                    shaderView.setPulseMorph(1f);
                }
                pulseMorphTargetEndMs = 0L;
            }
        });
        pulseMorphAnimator.start();
    }

    private void acceleratePulseCoilIfNeeded(long maxRemainingMs) {
        if (shaderView == null) {
            return;
        }
        float currentMorph = shaderView.getPulseMorph();
        if (currentMorph >= 0.999f) {
            return;
        }
        long budgetMs = Math.max(0L, maxRemainingMs);
        long now = android.os.SystemClock.uptimeMillis();
        long currentRemaining = (pulseMorphAnimator != null && pulseMorphAnimator.isRunning())
                ? Math.max(0L, pulseMorphTargetEndMs - now)
                : Long.MAX_VALUE;
        if (currentRemaining > budgetMs) {
            startPulseCoilAnimator(budgetMs);
        }
    }

    private void cancelPulseMorphAnimator() {
        if (pulseMorphAnimator != null) {
            pulseMorphAnimator.cancel();
            pulseMorphAnimator = null;
        }
        pulseMorphTargetEndMs = 0L;
    }

    private void clearPendingFirstFrameFallback() {
        if (pendingFirstFrameFallback != null) {
            mainHandler.removeCallbacks(pendingFirstFrameFallback);
            pendingFirstFrameFallback = null;
        }
    }

    private void cancelActiveAnimator() {
        if (activeAnimator != null) {
            activeAnimator.cancel();
            activeAnimator = null;
        }
    }

    private void cancelAnimatorsAndCallbacks() {
        clearPendingFirstFrameFallback();
        if (shaderView != null) {
            shaderView.setFirstFrameListener(null);
        }
        mainHandler.removeCallbacks(sonarHoldRunnable);
        mainHandler.removeCallbacks(firstFrameFallbackRunnable);
        mainHandler.removeCallbacks(phase3HoldRunnable);
        cancelActiveAnimator();
        cancelPulseMorphAnimator();
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
