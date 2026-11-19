package com.limelight;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.limelight.ui.AppThemePalette;
import com.limelight.ui.MetricChartView;
import com.limelight.ui.SessionMetricsStore;
import com.limelight.utils.UiHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Frosted floating post-session telemetry modal ported from jujostream's
 * SessionMetricsDialog: displays session duration, avg/peak FPS & decode latency,
 * bitrate, frame drop percentage, 2x2 animated MetricChartView grid, image/text
 * summary sharing, controller shortcuts (B close, Y share), and 20s auto-dismiss.
 */
public class SessionMetricsActivity extends AppCompatActivity {

    private static final int FPS_COLOR = 0xFF30D158;
    private static final int DECODE_COLOR = 0xFFFF9F0A;
    private static final int BITRATE_COLOR = 0xFF0A84FF;
    private static final int DROP_COLOR = 0xFFFF453A;
    private static final int DECODER_COLOR = 0xFFA78BFA;

    private static final int AUTO_DISMISS_SEC = 20;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ValueAnimator> activeChartAnimators = new ArrayList<>();
    private int countdown = AUTO_DISMISS_SEC;
    private boolean sharingInProgress = false;
    private boolean dismissing = false;

    private View rootBackdrop;
    private ScrollView scrollView;
    private View cardContainer;
    private TextView countdownText;
    private SessionMetricsStore.Snapshot snapshot;
    private int avgFpsRounded;
    private int avgBitrateRounded;
    private int durationSec;

    private final Runnable countdownTick = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed() || sharingInProgress || dismissing) {
                return;
            }
            countdown--;
            if (countdown <= 0) {
                dismissSmoothly();
                return;
            }
            if (countdownText != null) {
                countdownText.setText(countdown + "s");
            }
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiHelper.setLocale(this);
        UiHelper.disableAllActivityTransitions(this);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overridePendingTransition(0, 0);
        }

        snapshot = SessionMetricsStore.getSnapshot();
        if (snapshot.points.size() < 2) {
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            WindowManager wm = getSystemService(WindowManager.class);
            if (wm != null && wm.isCrossWindowBlurEnabled() && getWindow() != null) {
                getWindow().setBackgroundBlurRadius(Math.round(dp(24f)));
            }
        }

        setContentView(R.layout.activity_session_metrics);
        UiHelper.applyMenuImmersiveMode(this);
        if (getWindow() != null) {
            final View decorView = getWindow().getDecorView();
            decorView.getViewTreeObserver().addOnWindowFocusChangeListener(hasFocus -> {
                if (hasFocus) {
                    UiHelper.applyMenuImmersiveMode(this);
                }
            });
        }

        AppThemePalette palette = AppThemePalette.getCurrent(this);
        rootBackdrop = findViewById(R.id.metricsRootBackdrop);
        cardContainer = findViewById(R.id.metricsCardContainer);
        scrollView = findViewById(R.id.metricsScrollView);
        countdownText = findViewById(R.id.metricsCountdownText);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int maxCardWidth = Math.min(Math.round(dp(560f)), Math.max(Math.round(dp(280f)), dm.widthPixels - Math.round(dp(24f))));
        final int maxCardHeight = Math.max(Math.round(dp(320f)), dm.heightPixels - Math.round(dp(24f)));
        FrameLayout.LayoutParams cardLp = (FrameLayout.LayoutParams) cardContainer.getLayoutParams();
        cardLp.width = maxCardWidth;
        cardContainer.setLayoutParams(cardLp);

        // Rich frosted glass surface letting live GPU starfield & API 31+ window blur breathe through
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setCornerRadius(dp(24f));
        cardBg.setColor(0xC80F1526);
        cardBg.setStroke(Math.max(1, Math.round(dp(1f))), 0x42FFFFFF);
        cardContainer.setBackground(cardBg);

        rootBackdrop.setOnClickListener(v -> dismissSmoothly());
        cardContainer.setOnClickListener(v -> resetCountdown());

        View shareButton = findViewById(R.id.metricsShareButton);
        View closeButton = findViewById(R.id.metricsCloseButton);
        shareButton.setOnClickListener(v -> {
            resetCountdown();
            shareSummary();
        });
        closeButton.setOnClickListener(v -> dismissSmoothly());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            scrollView.setOnScrollChangeListener((v, sx, sy, osx, osy) -> resetCountdown());
        }

        populateMetrics(palette);
        prepareAndRunEntranceAnimations(maxCardHeight);
        countdownText.setText(countdown + "s");
        handler.postDelayed(countdownTick, 1000L);
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiHelper.applyMenuImmersiveMode(this);
        sharingInProgress = false;
        if (!dismissing) {
            resetCountdown();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        cancelChartAnimators();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        dismissSmoothly();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            dismissSmoothly();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
            resetCountdown();
            shareSummary();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            resetCountdown();
            scrollView.smoothScrollBy(0, Math.round(dp(100f)));
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            resetCountdown();
            scrollView.smoothScrollBy(0, -Math.round(dp(100f)));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void dismissSmoothly() {
        if (dismissing || isFinishing()) {
            return;
        }
        dismissing = true;
        handler.removeCallbacks(countdownTick);
        cancelChartAnimators();

        PathInterpolator exitEase = new PathInterpolator(0.4f, 0f, 1f, 1f);
        if (cardContainer != null) {
            cardContainer.animate().setListener(null).cancel();
            if (cardContainer.getWidth() > 0 && cardContainer.getHeight() > 0) {
                cardContainer.setPivotX(cardContainer.getWidth() * 0.5f);
                cardContainer.setPivotY(cardContainer.getHeight() * 0.5f);
            }
            cardContainer.animate()
                    .alpha(0f)
                    .scaleX(0.94f)
                    .scaleY(0.94f)
                    .translationY(dp(10f))
                    .setDuration(180L)
                    .setInterpolator(exitEase)
                    .withLayer()
                    .start();
        }
        if (rootBackdrop != null) {
            rootBackdrop.animate().setListener(null).cancel();
            rootBackdrop.animate()
                    .alpha(0f)
                    .setDuration(185L)
                    .setInterpolator(exitEase)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            rootBackdrop.animate().setListener(null);
                            finishWithoutTransition();
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            rootBackdrop.animate().setListener(null);
                            finishWithoutTransition();
                        }
                    })
                    .start();
        } else {
            finishWithoutTransition();
        }
    }

    private void finishWithoutTransition() {
        if (isFinishing()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            UiHelper.disableActivityTransition(this, true);
        }
        finish();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overridePendingTransition(0, 0);
        }
    }

    private void cancelChartAnimators() {
        for (ValueAnimator anim : activeChartAnimators) {
            anim.cancel();
        }
        activeChartAnimators.clear();
    }

    private void resetCountdown() {
        handler.removeCallbacks(countdownTick);
        countdown = AUTO_DISMISS_SEC;
        if (countdownText != null) {
            countdownText.setText(countdown + "s");
        }
        handler.postDelayed(countdownTick, 1000L);
    }

    private void populateMetrics(AppThemePalette palette) {
        List<SessionMetricsStore.MetricPoint> points = snapshot.points;
        durationSec = points.isEmpty() ? 0 : points.get(points.size() - 1).second + 1;

        long sumFps = 0;
        long sumDecode = 0;
        long sumBitrate = 0;
        int peakDecode = 0;
        int maxDrop = 0;

        float[] fpsValues = new float[points.size()];
        float[] decodeValues = new float[points.size()];
        float[] bitrateValues = new float[points.size()];
        float[] dropValues = new float[points.size()];

        for (int i = 0; i < points.size(); i++) {
            SessionMetricsStore.MetricPoint p = points.get(i);
            sumFps += p.fps;
            sumDecode += p.decodeMs;
            sumBitrate += p.bitrateMbps;
            if (p.decodeMs > peakDecode) peakDecode = p.decodeMs;
            if (p.dropRate > maxDrop) maxDrop = p.dropRate;
            fpsValues[i] = p.fps;
            decodeValues[i] = p.decodeMs;
            bitrateValues[i] = p.bitrateMbps;
            dropValues[i] = p.dropRate;
        }

        int count = Math.max(1, points.size());
        avgFpsRounded = Math.round(sumFps / (float) count);
        int avgDecodeRounded = Math.round(sumDecode / (float) count);
        avgBitrateRounded = Math.round(sumBitrate / (float) count);

        TextView appNameView = findViewById(R.id.metricsAppName);
        appNameView.setText(snapshot.appName);

        TextView configView = findViewById(R.id.metricsConfigText);
        configView.setText(buildConfigStrip());

        LinearLayout summaryRow = findViewById(R.id.metricsSummaryRow);
        summaryRow.removeAllViews();
        addSummaryCard(summaryRow, palette, getString(R.string.session_metrics_duration), formatDuration(durationSec), 0xFFFFFFFF, false);
        addSummaryCard(summaryRow, palette, getString(R.string.session_metrics_avg_fps), String.valueOf(avgFpsRounded), FPS_COLOR, false);
        addSummaryCard(summaryRow, palette, getString(R.string.session_metrics_avg_decode), avgDecodeRounded + " ms", DECODE_COLOR, false);
        addSummaryCard(summaryRow, palette, getString(R.string.session_metrics_peak_decode), peakDecode + " ms", peakDecode > 50 ? DROP_COLOR : DECODE_COLOR, false);
        addSummaryCard(summaryRow, palette, getString(R.string.session_metrics_bitrate), avgBitrateRounded + " Mbps", BITRATE_COLOR, false);
        addSummaryCard(summaryRow, palette, getString(R.string.session_metrics_peak_drop), maxDrop + "%", maxDrop > 5 ? DROP_COLOR : 0xFFFFFFFF, false);

        String decoderDisplay = !snapshot.codecLabel.isEmpty()
                ? snapshot.codecLabel
                : snapshot.decoderName;
        if (decoderDisplay != null && !decoderDisplay.isEmpty()) {
            addSummaryCard(summaryRow, palette, getString(R.string.session_metrics_decoder), decoderDisplay, DECODER_COLOR, true);
        }

        stylePanel(findViewById(R.id.metricsFpsPanel), palette);
        stylePanel(findViewById(R.id.metricsDecodePanel), palette);
        stylePanel(findViewById(R.id.metricsBitratePanel), palette);
        stylePanel(findViewById(R.id.metricsDropPanel), palette);

        styleBadge(findViewById(R.id.metricsFpsBadge), avgFpsRounded + " avg", FPS_COLOR);
        styleBadge(findViewById(R.id.metricsDecodeBadge), avgDecodeRounded + " ms avg", DECODE_COLOR);
        styleBadge(findViewById(R.id.metricsBitrateBadge), avgBitrateRounded + " Mbps avg", BITRATE_COLOR);

        boolean dropStable = maxDrop <= 1;
        String dropBadgeText = dropStable ? getString(R.string.session_metrics_stable) : (maxDrop + "% peak");
        styleBadge(findViewById(R.id.metricsDropBadge), dropBadgeText, DROP_COLOR);

        MetricChartView fpsChart = findViewById(R.id.metricsFpsChart);
        MetricChartView decodeChart = findViewById(R.id.metricsDecodeChart);
        MetricChartView bitrateChart = findViewById(R.id.metricsBitrateChart);
        MetricChartView dropChart = findViewById(R.id.metricsDropChart);

        fpsChart.setChartData(fpsValues, FPS_COLOR, -1f);
        decodeChart.setChartData(decodeValues, DECODE_COLOR, -1f);
        bitrateChart.setChartData(bitrateValues, BITRATE_COLOR, -1f);
        dropChart.setChartData(dropValues, DROP_COLOR, 100f);
    }

    private void addSummaryCard(
            LinearLayout parent,
            AppThemePalette palette,
            String label,
            String value,
            int accentColor,
            boolean isLast) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setMinimumWidth(Math.round(dp(68f)));
        card.setPadding(Math.round(dp(10f)), Math.round(dp(8f)), Math.round(dp(10f)), Math.round(dp(8f)));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(12f));
        bg.setColor(AppThemePalette.withAlpha(palette.background, 0.55f));
        bg.setStroke(Math.max(1, Math.round(dp(0.8f))), 0x1AFFFFFF);
        card.setBackground(bg);

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextColor(0x73FFFFFF);
        labelTv.setTextSize(10f);
        labelTv.setTypeface(Typeface.DEFAULT_BOLD);
        labelTv.setMaxLines(1);
        card.addView(labelTv);

        TextView valueTv = new TextView(this);
        valueTv.setText(value);
        valueTv.setTextColor(accentColor);
        valueTv.setTextSize(15f);
        valueTv.setTypeface(Typeface.DEFAULT_BOLD);
        valueTv.setMaxLines(1);
        LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valLp.topMargin = Math.round(dp(3f));
        valueTv.setLayoutParams(valLp);
        card.addView(valueTv);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (!isLast) {
            lp.setMarginEnd(Math.round(dp(9f)));
        }
        card.setLayoutParams(lp);
        parent.addView(card);
    }

    private void stylePanel(View panel, AppThemePalette palette) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(14f));
        bg.setColor(AppThemePalette.withAlpha(palette.background, 0.44f));
        bg.setStroke(Math.max(1, Math.round(dp(0.8f))), 0x18FFFFFF);
        panel.setBackground(bg);
    }

    private void styleBadge(TextView badge, String text, int color) {
        badge.setText(text);
        badge.setTextColor(color);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(6f));
        bg.setColor(AppThemePalette.withAlpha(color, 0.16f));
        badge.setBackground(bg);
    }

    private void prepareAndRunEntranceAnimations(final int maxCardHeight) {
        final LinearLayout summaryRow = findViewById(R.id.metricsSummaryRow);
        final View[] panels = {
                findViewById(R.id.metricsFpsPanel),
                findViewById(R.id.metricsDecodePanel),
                findViewById(R.id.metricsBitratePanel),
                findViewById(R.id.metricsDropPanel)
        };
        final MetricChartView[] charts = {
                findViewById(R.id.metricsFpsChart),
                findViewById(R.id.metricsDecodeChart),
                findViewById(R.id.metricsBitrateChart),
                findViewById(R.id.metricsDropChart)
        };

        rootBackdrop.setAlpha(0f);
        cardContainer.setAlpha(0f);
        cardContainer.setScaleX(0.93f);
        cardContainer.setScaleY(0.93f);
        cardContainer.setTranslationY(dp(16f));

        for (int i = 0; i < summaryRow.getChildCount(); i++) {
            View child = summaryRow.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(dp(8f));
        }
        for (View panel : panels) {
            panel.setAlpha(0f);
            panel.setTranslationY(dp(10f));
        }
        for (MetricChartView chart : charts) {
            chart.setDrawProgress(0f);
        }

        cardContainer.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            private boolean heightConstrained = false;

            @Override
            public boolean onPreDraw() {
                if (!heightConstrained && cardContainer.getHeight() > maxCardHeight) {
                    heightConstrained = true;
                    ViewGroup.LayoutParams lp = cardContainer.getLayoutParams();
                    lp.height = maxCardHeight;
                    cardContainer.setLayoutParams(lp);
                    return false;
                }
                if (cardContainer.getViewTreeObserver().isAlive()) {
                    cardContainer.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                cardContainer.postOnAnimation(() -> startChoreographedEntrance(summaryRow, panels, charts));
                return true;
            }
        });
    }

    private void startChoreographedEntrance(LinearLayout summaryRow, View[] panels, MetricChartView[] charts) {
        if (dismissing || isFinishing()) {
            return;
        }
        PathInterpolator easeOut = new PathInterpolator(0.22f, 1f, 0.36f, 1f);

        rootBackdrop.animate()
                .alpha(1f)
                .setDuration(240L)
                .setInterpolator(easeOut)
                .start();

        if (cardContainer.getWidth() > 0 && cardContainer.getHeight() > 0) {
            cardContainer.setPivotX(cardContainer.getWidth() * 0.5f);
            cardContainer.setPivotY(cardContainer.getHeight() * 0.5f);
        }
        cardContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(300L)
                .setInterpolator(easeOut)
                .withLayer()
                .start();

        for (int i = 0; i < summaryRow.getChildCount(); i++) {
            View child = summaryRow.getChildAt(i);
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(70L + i * 28L)
                    .setDuration(260L)
                    .setInterpolator(easeOut)
                    .start();
        }

        cancelChartAnimators();
        for (int i = 0; i < panels.length; i++) {
            long panelDelay = 120L + i * 50L;
            panels[i].animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(panelDelay)
                    .setDuration(280L)
                    .setInterpolator(easeOut)
                    .start();

            final MetricChartView chart = charts[i];
            ValueAnimator chartAnim = ValueAnimator.ofFloat(0f, 1f);
            chartAnim.setStartDelay(panelDelay + 60L);
            chartAnim.setDuration(760L);
            chartAnim.setInterpolator(easeOut);
            chartAnim.addUpdateListener(anim -> chart.setDrawProgress((float) anim.getAnimatedValue()));
            activeChartAnimators.add(chartAnim);
            chartAnim.start();
        }
    }

    private void shareSummary() {
        sharingInProgress = true;
        handler.removeCallbacks(countdownTick);

        String summary = String.format(
                Locale.US,
                "Session: %s \u2022 %s \u2022 Avg FPS %d \u2022 %d Mbps",
                snapshot.appName,
                formatDuration(durationSec),
                avgFpsRounded,
                avgBitrateRounded);

        try {
            int w = cardContainer.getWidth();
            int h = cardContainer.getHeight();
            if (w > 0 && h > 0) {
                Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                cardContainer.draw(canvas);
                File outFile = new File(getCacheDir(), "session_metrics.png");
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
                bitmap.recycle();

                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", outFile);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("image/png");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.putExtra(Intent.EXTRA_TEXT, summary);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent, getString(R.string.session_metrics_share)));
                return;
            }
        } catch (Throwable ignored) {
            // Fall through to plain text share
        }

        Intent textIntent = new Intent(Intent.ACTION_SEND);
        textIntent.setType("text/plain");
        textIntent.putExtra(Intent.EXTRA_TEXT, summary);
        startActivity(Intent.createChooser(textIntent, getString(R.string.session_metrics_share)));
    }

    private String buildConfigStrip() {
        StringBuilder sb = new StringBuilder();
        if (!snapshot.codecLabel.isEmpty()) {
            sb.append(snapshot.codecLabel);
        }
        if (snapshot.targetFps > 0) {
            appendSep(sb).append(snapshot.targetFps).append(" FPS");
        }
        if (snapshot.targetBitrateMbps > 0) {
            appendSep(sb).append(snapshot.targetBitrateMbps).append(" Mbps");
        }
        if (snapshot.width > 0 && snapshot.height > 0) {
            appendSep(sb).append(snapshot.width).append('x').append(snapshot.height);
        }
        if (snapshot.framePacingName != null && !snapshot.framePacingName.isEmpty()) {
            appendSep(sb).append(formatPacingName(snapshot.framePacingName));
        }
        if (snapshot.hdr && (snapshot.codecLabel.isEmpty() || !snapshot.codecLabel.contains("HDR"))) {
            appendSep(sb).append("HDR");
        }
        return sb.toString();
    }

    private static StringBuilder appendSep(StringBuilder sb) {
        if (sb.length() > 0) {
            sb.append("  \u2022  ");
        }
        return sb;
    }

    private static String formatPacingName(String raw) {
        switch (raw) {
            case "latency":
                return "Low Latency";
            case "balanced":
                return "Balanced";
            case "cap-fps":
                return "Cap FPS";
            case "smoothness":
                return "Smooth";
            case "warp":
            case "warp2":
                return "Warp";
            default:
                return raw;
        }
    }

    private static String formatDuration(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return m == 0 ? s + "s" : String.format(Locale.US, "%dm %02ds", m, s);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
