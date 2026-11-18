package com.limelight.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-safe per-session telemetry collector used to populate SessionMetricsActivity
 * after a stream terminates gracefully.
 */
public final class SessionMetricsStore {

    public static final class MetricPoint {
        public final int second;
        public final int fps;
        public final int decodeMs;
        public final int bitrateMbps;
        public final int dropRate;

        public MetricPoint(int second, int fps, int decodeMs, int bitrateMbps, int dropRate) {
            this.second = second;
            this.fps = fps;
            this.decodeMs = decodeMs;
            this.bitrateMbps = bitrateMbps;
            this.dropRate = dropRate;
        }
    }

    public static final class Snapshot {
        public final String appName;
        public final int width;
        public final int height;
        public final int targetFps;
        public final int targetBitrateMbps;
        public final boolean hdr;
        public final String framePacingName;
        public final String codecLabel;
        public final String decoderName;
        public final List<MetricPoint> points;

        Snapshot(
                String appName,
                int width,
                int height,
                int targetFps,
                int targetBitrateMbps,
                boolean hdr,
                String framePacingName,
                String codecLabel,
                String decoderName,
                List<MetricPoint> points) {
            this.appName = appName;
            this.width = width;
            this.height = height;
            this.targetFps = targetFps;
            this.targetBitrateMbps = targetBitrateMbps;
            this.hdr = hdr;
            this.framePacingName = framePacingName;
            this.codecLabel = codecLabel;
            this.decoderName = decoderName;
            this.points = points;
        }
    }

    private static final int MAX_POINTS = 28800; // 8 hours @ 1Hz

    private static final Object LOCK = new Object();
    private static String activeAppName = "Stream";
    private static int activeWidth;
    private static int activeHeight;
    private static int activeTargetFps;
    private static int activeTargetBitrateMbps;
    private static boolean activeHdr;
    private static String activeFramePacingName = "balanced";
    private static String activeCodecLabel = "";
    private static String activeDecoderName = "";
    private static final List<MetricPoint> activePoints = new ArrayList<>();

    private SessionMetricsStore() {
    }

    public static void startSession(
            @Nullable String appName,
            int width,
            int height,
            int targetFps,
            int targetBitrateMbps,
            boolean hdr,
            @Nullable String framePacingName) {
        synchronized (LOCK) {
            activeAppName = (appName != null && !appName.trim().isEmpty()) ? appName.trim() : "Stream";
            activeWidth = width;
            activeHeight = height;
            activeTargetFps = targetFps;
            activeTargetBitrateMbps = targetBitrateMbps;
            activeHdr = hdr;
            activeFramePacingName = framePacingName != null ? framePacingName : "balanced";
            activeCodecLabel = "";
            activeDecoderName = "";
            activePoints.clear();
        }
    }

    public static void recordSample(
            int fps,
            int decodeMs,
            int bitrateMbps,
            int dropRate,
            @Nullable String codecLabel,
            @Nullable String decoderName) {
        synchronized (LOCK) {
            if (activePoints.size() >= MAX_POINTS) {
                activePoints.remove(0);
            }
            int second = activePoints.isEmpty() ? 0 : activePoints.get(activePoints.size() - 1).second + 1;
            activePoints.add(new MetricPoint(second, fps, decodeMs, bitrateMbps, dropRate));
            if (codecLabel != null && !codecLabel.isEmpty()) {
                activeCodecLabel = codecLabel;
            }
            if (decoderName != null && !decoderName.isEmpty()) {
                activeDecoderName = decoderName;
            }
        }
    }

    public static boolean hasEnoughData() {
        synchronized (LOCK) {
            return activePoints.size() >= 2;
        }
    }

    @NonNull
    public static Snapshot getSnapshot() {
        synchronized (LOCK) {
            return new Snapshot(
                    activeAppName,
                    activeWidth,
                    activeHeight,
                    activeTargetFps,
                    activeTargetBitrateMbps,
                    activeHdr,
                    activeFramePacingName,
                    activeCodecLabel,
                    activeDecoderName,
                    Collections.unmodifiableList(new ArrayList<>(activePoints))
            );
        }
    }
}
