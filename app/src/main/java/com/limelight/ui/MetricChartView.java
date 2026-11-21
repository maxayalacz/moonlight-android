package com.limelight.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Animated mini line + gradient-area chart ported from jujostream's _ChartPainter.
 * Draws 3 faint horizontal gridlines, Y-axis values, X-axis time markers, a smooth
 * progressive left-to-right reveal, and a terminal dot.
 */
public class MetricChartView extends View {

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    private float[] values = new float[0];
    private float[] cachedX = new float[0];
    private float[] cachedY = new float[0];
    private int accentColor = 0xFF30D158;
    private float fixedMaxY = -1f;
    private float drawProgress = 1f;

    private boolean pathDirty = true;
    private int cachedW = -1;
    private float cachedChartH = -1f;
    private float cachedMaxVal = 1f;
    private float cachedMinVal = 0f;
    private float cachedRange = 1f;
    private int shaderWidth = -1;
    private float shaderHeight = -1f;
    private int shaderColor = 0;

    public MetricChartView(Context context) {
        super(context);
        init();
    }

    public MetricChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MetricChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gridPaint.setColor(0x14FFFFFF);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(Math.max(1f, dp(1f)));

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2.1f));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);
        dotHaloPaint.setStyle(Paint.Style.FILL);
        dotPaint.setStyle(Paint.Style.FILL);

        labelPaint.setColor(0x78FFFFFF);
        labelPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 8.5f, getResources().getDisplayMetrics()));
        labelPaint.setFontFeatureSettings("tnum");
    }

    public void setChartData(@NonNull float[] values, int accentColor, float fixedMaxY) {
        this.values = values;
        this.accentColor = accentColor;
        this.fixedMaxY = fixedMaxY;
        this.shaderWidth = -1;
        this.pathDirty = true;
        AppThemePalette palette = AppThemePalette.getCurrent(getContext());
        gridPaint.setColor(palette.getDividerColor());
        labelPaint.setColor(AppThemePalette.withAlpha(palette.getSecondaryTextColor(), 0.78f));
        invalidate();
    }

    public void setDrawProgress(float progress) {
        float clamped = Math.max(0f, Math.min(1f, progress));
        if (Math.abs(clamped - this.drawProgress) > 0.0001f) {
            this.drawProgress = clamped;
            invalidate();
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private void rebuildPathsIfNeeded(int w, float chartH) {
        if (!pathDirty && cachedW == w && Math.abs(cachedChartH - chartH) < 0.5f) {
            return;
        }
        pathDirty = false;
        cachedW = w;
        cachedChartH = chartH;

        float maxVal = 1f;
        float minVal = 0f;
        if (values.length > 0) {
            float dataMax = values[0];
            float dataMin = values[0];
            for (float v : values) {
                if (v > dataMax) dataMax = v;
                if (v < dataMin) dataMin = v;
            }
            maxVal = fixedMaxY > 0f ? fixedMaxY : Math.max(dataMax, 1f);
            minVal = Math.min(0f, dataMin);
        } else if (fixedMaxY > 0f) {
            maxVal = fixedMaxY;
        }
        cachedMaxVal = maxVal;
        cachedMinVal = minVal;
        cachedRange = Math.max(1f, maxVal - minVal);

        linePath.reset();
        fillPath.reset();
        if (values.length == 0) {
            cachedX = new float[0];
            cachedY = new float[0];
            return;
        }

        float verticalPad = dp(4f);
        float minY = verticalPad;
        float maxY = Math.max(minY, chartH - verticalPad);
        float usableH = Math.max(1f, maxY - minY);

        int n = values.length;
        if (cachedX.length != n) {
            cachedX = new float[n];
            cachedY = new float[n];
        }
        float stepX = n == 1 ? w : (float) w / (n - 1);
        for (int i = 0; i < n; i++) {
            float x = n == 1 ? w : stepX * i;
            float normalized = Math.max(0f, Math.min(1f, (values[i] - minVal) / cachedRange));
            float y = chartH - (normalized * usableH) - verticalPad;
            cachedX[i] = x;
            cachedY[i] = y;
        }

        if (n == 1) {
            linePath.moveTo(0f, cachedY[0]);
            linePath.lineTo(w, cachedY[0]);
            fillPath.moveTo(0f, chartH);
            fillPath.lineTo(0f, cachedY[0]);
            fillPath.lineTo(w, cachedY[0]);
            fillPath.lineTo(w, chartH);
            fillPath.close();
            return;
        }

        linePath.moveTo(cachedX[0], cachedY[0]);
        fillPath.moveTo(cachedX[0], chartH);
        fillPath.lineTo(cachedX[0], cachedY[0]);

        for (int i = 0; i < n - 1; i++) {
            float x0 = cachedX[i];
            float y0 = cachedY[i];
            float x1 = cachedX[i + 1];
            float y1 = cachedY[i + 1];

            float prevY = cachedY[Math.max(0, i - 1)];
            float nextY = cachedY[Math.min(n - 1, i + 2)];

            float cx1 = x0 + (x1 - x0) * 0.33f;
            float cy1 = clamp(y0 + (y1 - prevY) * 0.14f, minY, maxY);
            float cx2 = x1 - (x1 - x0) * 0.33f;
            float cy2 = clamp(y1 - (nextY - y0) * 0.14f, minY, maxY);

            linePath.cubicTo(cx1, cy1, cx2, cy2, x1, y1);
            fillPath.cubicTo(cx1, cy1, cx2, cy2, x1, y1);
        }
        fillPath.lineTo(w, chartH);
        fillPath.close();
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private float sampleYAtX(float x, float w) {
        int n = cachedY.length;
        if (n == 0) {
            return 0f;
        }
        if (n == 1 || w <= 0f) {
            return cachedY[0];
        }
        float fIdx = clamp(x / w, 0f, 1f) * (n - 1);
        int i0 = Math.min(n - 1, (int) Math.floor(fIdx));
        int i1 = Math.min(n - 1, i0 + 1);
        float t = fIdx - i0;
        // Smoothstep blend matches cubic feel cleanly
        float smooth = t * t * (3f - 2f * t);
        return cachedY[i0] + (cachedY[i1] - cachedY[i0]) * smooth;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        float xLabelH = dp(13f);
        float chartH = Math.max(dp(12f), h - xLabelH);

        rebuildPathsIfNeeded(w, chartH);

        for (int i = 1; i < 4; i++) {
            float y = chartH * i / 4f;
            canvas.drawLine(0f, y, w, y, gridPaint);
        }

        labelPaint.setTextAlign(Paint.Align.LEFT);
        for (int i = 1; i <= 3; i++) {
            float frac = i / 4f;
            float val = cachedMaxVal - (cachedRange * frac);
            if (val < 0f) continue;
            float yPos = chartH * frac;
            canvas.drawText(formatVal(val), 0f, yPos - dp(2.2f), labelPaint);
        }

        if (values.length == 0 || drawProgress <= 0f) {
            return;
        }

        float progress = clamp(drawProgress, 0f, 1f);
        float clipW = w * progress;

        if (shaderWidth != w || shaderHeight != chartH || shaderColor != accentColor) {
            shaderWidth = w;
            shaderHeight = chartH;
            shaderColor = accentColor;
            fillPaint.setShader(new LinearGradient(
                    0f, 0f, 0f, chartH,
                    AppThemePalette.withAlpha(accentColor, 0.30f),
                    AppThemePalette.withAlpha(accentColor, 0.02f),
                    Shader.TileMode.CLAMP));
        }

        canvas.save();
        canvas.clipRect(0f, 0f, clipW, chartH);
        canvas.drawPath(fillPath, fillPaint);
        linePaint.setColor(accentColor);
        canvas.drawPath(linePath, linePaint);
        canvas.restore();

        // Glowing leading tracer dot rides cleanly along the curve tip during reveal
        float dotX = clamp(clipW, dp(2.5f), w - dp(2.5f));
        float dotY = sampleYAtX(clipW, w);
        dotHaloPaint.setColor(AppThemePalette.withAlpha(accentColor, 0.35f));
        canvas.drawCircle(dotX, dotY, dp(5.8f), dotHaloPaint);
        dotPaint.setColor(accentColor);
        canvas.drawCircle(dotX, dotY, dp(3.0f), dotPaint);

        if (values.length > 1) {
            int totalSec = values.length - 1;
            float labelY = chartH + dp(10.5f);

            labelPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("0", 0f, labelY, labelPaint);

            labelPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(formatSec(totalSec), w, labelY, labelPaint);

            if (totalSec >= 20) {
                labelPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(formatSec(totalSec / 2), w / 2f, labelY, labelPaint);
            }
        }
    }

    private static String formatVal(float v) {
        if (v >= 1000f) {
            return String.format(Locale.US, "%.1fk", v / 1000f);
        }
        if (v >= 1f) {
            return String.valueOf(Math.round(v));
        }
        return String.format(Locale.US, "%.1f", v);
    }

    private static String formatSec(int sec) {
        if (sec == 0) return "0";
        if (sec < 60) return sec + "s";
        int m = sec / 60;
        int s = sec % 60;
        return s == 0 ? m + "m" : String.format(Locale.US, "%d:%02d", m, s);
    }
}
