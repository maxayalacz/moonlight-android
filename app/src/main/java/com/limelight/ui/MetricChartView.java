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
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    private float[] values = new float[0];
    private int accentColor = 0xFF30D158;
    private float fixedMaxY = -1f;
    private float drawProgress = 1f;
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
        linePaint.setStrokeWidth(dp(2f));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);
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
        invalidate();
    }

    public void setDrawProgress(float progress) {
        float clamped = Math.max(0f, Math.min(1f, progress));
        if (Math.abs(clamped - this.drawProgress) > 0.001f) {
            this.drawProgress = clamped;
            invalidate();
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
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

        for (int i = 1; i < 4; i++) {
            float y = chartH * i / 4f;
            canvas.drawLine(0f, y, w, y, gridPaint);
        }

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
        float range = Math.max(1f, maxVal - minVal);

        labelPaint.setTextAlign(Paint.Align.LEFT);
        for (int i = 1; i <= 3; i++) {
            float frac = i / 4f;
            float val = maxVal - (range * frac);
            if (val < 0f) continue;
            float yPos = chartH * frac;
            canvas.drawText(formatVal(val), 0f, yPos - dp(2.2f), labelPaint);
        }

        if (values.length == 0 || drawProgress <= 0f) {
            return;
        }

        float clipW = w * Math.max(0f, Math.min(1f, drawProgress));
        canvas.save();
        canvas.clipRect(0f, 0f, clipW, chartH);

        float stepX = values.length == 1 ? w : (float) w / (values.length - 1);
        float verticalPad = dp(3f);
        float usableH = Math.max(1f, chartH - verticalPad * 2f);

        linePath.reset();
        fillPath.reset();
        float lastY = chartH - verticalPad;
        for (int i = 0; i < values.length; i++) {
            float x = values.length == 1 ? w : stepX * i;
            float normalized = Math.max(0f, Math.min(1f, (values[i] - minVal) / range));
            float y = chartH - (normalized * usableH) - verticalPad;
            if (i == 0) {
                if (values.length == 1) {
                    linePath.moveTo(0f, y);
                    linePath.lineTo(w, y);
                    fillPath.moveTo(0f, chartH);
                    fillPath.lineTo(0f, y);
                    fillPath.lineTo(w, y);
                } else {
                    linePath.moveTo(x, y);
                    fillPath.moveTo(x, chartH);
                    fillPath.lineTo(x, y);
                }
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
            lastY = y;
        }
        fillPath.lineTo(w, chartH);
        fillPath.close();

        if (shaderWidth != w || shaderHeight != chartH || shaderColor != accentColor) {
            shaderWidth = w;
            shaderHeight = chartH;
            shaderColor = accentColor;
            fillPaint.setShader(new LinearGradient(
                    0f, 0f, 0f, chartH,
                    AppThemePalette.withAlpha(accentColor, 0.28f),
                    AppThemePalette.withAlpha(accentColor, 0.02f),
                    Shader.TileMode.CLAMP));
        }

        canvas.drawPath(fillPath, fillPaint);

        linePaint.setColor(accentColor);
        canvas.drawPath(linePath, linePaint);

        dotPaint.setColor(accentColor);
        canvas.drawCircle(w - dp(1.5f), lastY, dp(3.1f), dotPaint);
        canvas.restore();

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
