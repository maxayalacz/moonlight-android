package com.limelight;

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
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
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
    private int countdown = AUTO_DISMISS_SEC;
    private boolean sharingInProgress = false;

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
            if (isFinishing() || isDestroyed() || sharingInProgress) {
                return;
            }
            countdown--;
            if (countdown <= 0) {
                finish();
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

        snapshot = SessionMetricsStore.getSnapshot();
        if (snapshot.points.size() < 2) {
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            WindowManager wm = getSystemService(WindowManager.class);
            if (wm != null && wm.isCrossWindowBlurEnabled() && getWindow() != null) {
                getWindow().setBackgroundBlurRadius(Math.round(dp(20f)));
            }
        }

        setContentView(R.layout.activity_session_metrics);

        AppThemePalette palette = AppThemePalette.getCurrent(this);
        View rootBackdrop = findViewById(R.id.metricsRootBackdrop);
        cardContainer = findViewById(R.id.metricsCardContainer);
        scrollView = findViewById(R.id.metricsScrollView);
        countdownText = findViewById(R.id.metricsCountdownText);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int maxCardWidth = Math.min(Math.round(dp(560f)), Math.max(Math.round(dp(280f)), dm.widthPixels - Math.round(dp(24f))));
        int maxCardHeight = Math.max(Math.round(dp(320f)), dm.heightPixels - Math.round(dp(24f)));
        FrameLayout.LayoutParams cardLp = (FrameLayout.LayoutParams) cardContainer.getLayoutParams();
        cardLp.width = maxCardWidth;
        cardContainer.setLayoutParams(cardLp);
        if (cardContainer instanceof ViewGroup) {
            cardContainer.post(() -> {
                if (cardContainer.getHeight() > maxCardHeight) {
                    ViewGroup.LayoutParams lp = cardContainer.getLayoutParams();
                    lp.height = maxCardHeight;
                    cardContainer.setLayoutParams(lp);
                }
            });
        }

        if (!UiHelper.applyFrostedCardBackground(
                cardContainer,
                com.limelight.ui.FlowingAuroraView.CornerMode.ALL,
                22f,
                0x78121826)) {
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setShape(GradientDrawable.RECTANGLE);
            cardBg.setCornerRadius(dp(22f));
            cardBg.setColor(AppThemePalette.withAlpha(palette.surface, 0.88f));
            cardBg.setStroke(Math.max(1, Math.round(dp(1f))), 0x38FFFFFF);
            cardContainer.setBackground(cardBg);
        }

        rootBackdrop.setOnClickListener(v -> finish());
        cardContainer.setOnClickListener(v -> resetCountdown());

        View shareButton = findViewById(R.id.metricsShareButton);
        View closeButton = findViewById(R.id.metricsCloseButton);
        shareButton.setOnClickListener(v -> {
            resetCountdown();
            shareSummary();
        });
        closeButton.setOnClickListener(v -> finish());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            scrollView.setOnScrollChangeListener((v, sx, sy, osx, osy) -> resetCountdown());
        }

        populateMetrics(palette);
        runEntranceAnimations();
        countdownText.setText(countdown + "s");
        handler.postDelayed(countdownTick, 1000L);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sharingInProgress = false;
        resetCountdown();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish();
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
        bg.setCornerRadius(dp(10f));
        bg.setColor(AppThemePalette.withAlpha(palette.background, 0.55f));
        bg.setStroke(Math.max(1, Math.round(dp(0.8f))), 0x16FFFFFF);
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
        bg.setCornerRadius(dp(12f));
        bg.setColor(AppThemePalette.withAlpha(palette.background, 0.42f));
        bg.setStroke(Math.max(1, Math.round(dp(0.8f))), 0x14FFFFFF);
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

    private void runEntranceAnimations() {
        View summaryScroll = findViewById(R.id.metricsSummaryScroll);
        View chartsGrid = findViewById(R.id.metricsChartsGrid);
        MetricChartView fpsChart = findViewById(R.id.metricsFpsChart);
        MetricChartView decodeChart = findViewById(R.id.metricsDecodeChart);
        MetricChartView bitrateChart = findViewById(R.id.metricsBitrateChart);
        MetricChartView dropChart = findViewById(R.id.metricsDropChart);

        summaryScroll.setAlpha(0f);
        summaryScroll.setTranslationY(dp(12f));
        summaryScroll.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(420L)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        chartsGrid.setAlpha(0f);
        chartsGrid.animate()
                .alpha(1f)
                .setStartDelay(180L)
                .setDuration(350L)
                .start();

        fpsChart.setDrawProgress(0f);
        decodeChart.setDrawProgress(0f);
        bitrateChart.setDrawProgress(0f);
        dropChart.setDrawProgress(0f);

        ValueAnimator chartAnim = ValueAnimator.ofFloat(0f, 1f);
        chartAnim.setStartDelay(220L);
        chartAnim.setDuration(850L);
        chartAnim.setInterpolator(new DecelerateInterpolator(1.6f));
        chartAnim.addUpdateListener(anim -> {
            float p = (float) anim.getAnimatedValue();
            fpsChart.setDrawProgress(p);
            decodeChart.setDrawProgress(p);
            bitrateChart.setDrawProgress(p);
            dropChart.setDrawProgress(p);
        });
        chartAnim.start();
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
