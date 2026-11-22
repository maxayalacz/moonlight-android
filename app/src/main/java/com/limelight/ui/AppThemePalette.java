package com.limelight.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Full Theme & Palette system supporting:
 * - 5 built-in presets (Midnight Blue, Frost Daylight Light, Cyberpunk Neon, Emerald Forest, Ember Sunset)
 * - 10-channel Custom Theme (Accent, Background, Surface, Popup Tint, Primary Text, Secondary Text,
 *   Card Outline, Highlight, Particles, Ring)
 * - Frosted Card Tint Amount (0-100%, where 100% is fully opaque and automatically disables live blur)
 * - Saved Custom Theme bundles (colors + glass/blur/outline + full Portal FX) with clipboard JSON import/export.
 */
public final class AppThemePalette {
    public static final String PREF_COLOR_SCHEME = "list_color_scheme";
    public static final String DEFAULT_THEME_KEY = "midnight";
    public static final String CUSTOM_THEME_KEY = "custom";
    public static final String SAVED_THEME_PREFIX = "saved:";
    public static final String PREF_SAVED_THEMES_JSON = "saved_custom_themes_v1";

    public static final String PREF_CUSTOM_IS_LIGHT = "custom_palette_is_light";
    public static final String PREF_CUSTOM_BG = "custom_palette_bg";
    public static final String PREF_CUSTOM_SURFACE = "custom_palette_surface";
    public static final String PREF_CUSTOM_POPUP_TINT = "custom_palette_popup_tint";
    public static final String PREF_CUSTOM_ACCENT = "custom_palette_accent";
    public static final String PREF_CUSTOM_HIGHLIGHT = "custom_palette_highlight";
    public static final String PREF_CUSTOM_TEXT_PRIMARY = "custom_palette_text_primary";
    public static final String PREF_CUSTOM_TEXT_SECONDARY = "custom_palette_text_secondary";
    public static final String PREF_CUSTOM_CARD_OUTLINE = "custom_palette_card_outline";
    public static final String PREF_CUSTOM_PARTICLE = "custom_palette_particle";
    public static final String PREF_CUSTOM_PARTICLE_MID = "custom_palette_particle_mid";
    public static final String PREF_CUSTOM_PARTICLE_FAR = "custom_palette_particle_far";
    public static final String PREF_CUSTOM_PULSE = "custom_palette_pulse";
    public static final String PREF_CUSTOM_RING = "custom_palette_ring";
    public static final String PREF_CUSTOM_CHARGE = "custom_palette_charge";

    public final String key;
    public final String label;
    public final String shortLabel;
    public final boolean isLight;
    public final int background;
    public final int surface;
    public final int popupTint;
    public final int accent;
    public final int accentLight;
    public final int secondary;
    public final int highlight;
    public final int primaryText;
    public final int secondaryText;
    public final int cardOutline;
    public final int surfaceVariant;
    public final int muted;
    public final int warm;
    public final int particleColor;
    public final int particleColorMid;
    public final int particleColorFar;
    public final int pulseColor;
    public final int ringColor;
    public final int chargeColor;
    public final int frostedTintAmount; // 0..100 (-1 = use preset balanced default)

    public static final class CustomPaletteSpec {
        public boolean isLight;
        public int background;
        public int surface;
        public int popupTint;
        public int accent;
        public int highlight;
        public int primaryText;
        public int secondaryText;
        public int cardOutline;
        public int particleColor;
        public int particleColorMid;
        public int particleColorFar;
        public int pulseColor;
        public int ringColor;
        public int chargeColor;

        public CustomPaletteSpec(
                boolean isLight,
                int background,
                int surface,
                int popupTint,
                int accent,
                int highlight,
                int primaryText,
                int secondaryText,
                int cardOutline,
                int particleColor,
                int particleColorMid,
                int particleColorFar,
                int pulseColor,
                int ringColor,
                int chargeColor) {
            this.isLight = isLight;
            this.background = background;
            this.surface = surface;
            this.popupTint = popupTint;
            this.accent = accent;
            this.highlight = highlight;
            this.primaryText = primaryText;
            this.secondaryText = secondaryText;
            this.cardOutline = cardOutline;
            this.particleColor = particleColor;
            this.particleColorMid = particleColorMid;
            this.particleColorFar = particleColorFar;
            this.pulseColor = pulseColor;
            this.ringColor = ringColor;
            this.chargeColor = chargeColor;
        }
    }

    /**
     * Complete portable theme bundle (colors + glass/blur/outline + Portal FX) used by
     * ThemeStudioActivity, Saved Custom Themes, and Clipboard JSON Import/Export.
     */
    public static final class ThemeBundle {
        public String id;
        public String name;
        public CustomPaletteSpec colors;
        public int blurAmountPercent;
        public int frostedTintAmountPercent; // 0..100 (100 = fully opaque + disables live blur)
        public int cardOutlineThicknessPercent;
        public String bgQuality;
        public int blurSaturationPercent;
        public int portalParticleBrightnessPercent;
        public int portalParticleSizePercent;
        public int portalParticleDensityPercent;
        public int portalGlowIntensityPercent;
        public int portalWindowScalePercent;
        public int portalSonarSpeedPercent;
        public int portalPulseAmount;
        public int portalPulseBrightnessPercent;
        public int portalPulseLengthPercent;
        public int portalPulseSpeedPercent;
        public int portalWaveAmpPercent;
        public int portalSonarHoldMs;
        public int portalReadyMs;
        public int portalChargeMs;
        public int portalPreviewFadeMs;
        public int portalPreviewHoldMs;
        public int portalExpandMs;
        public int portalDisconnectMs;
        public int portalDisconnectCloseMs;
        public int portalDisconnectFadeMs;
        public int portalDisconnectOpenMs;
        public int portalLaunchCloseMs;
        public int portalPulseCoilMs;
        public boolean portalDecouplePulseCoil;

        public ThemeBundle(@NonNull String id, @NonNull String name, @NonNull CustomPaletteSpec colors) {
            this.id = id;
            this.name = name;
            this.colors = colors;
            this.blurAmountPercent = PreferenceConfiguration.DEFAULT_BLUR_AMOUNT;
            this.blurSaturationPercent = PreferenceConfiguration.DEFAULT_BLUR_SATURATION;
            this.frostedTintAmountPercent = colors.isLight ? 76 : PreferenceConfiguration.DEFAULT_FROSTED_TINT_AMOUNT;
            this.cardOutlineThicknessPercent = PreferenceConfiguration.DEFAULT_CARD_OUTLINE_THICKNESS;
            this.bgQuality = PreferenceConfiguration.DEFAULT_BG_QUALITY;
            this.portalParticleBrightnessPercent = PreferenceConfiguration.DEFAULT_PORTAL_PARTICLE_BRIGHTNESS;
            this.portalParticleSizePercent = PreferenceConfiguration.DEFAULT_PORTAL_PARTICLE_SIZE;
            this.portalParticleDensityPercent = PreferenceConfiguration.DEFAULT_PORTAL_PARTICLE_DENSITY;
            this.portalGlowIntensityPercent = PreferenceConfiguration.DEFAULT_PORTAL_GLOW_INTENSITY;
            this.portalWindowScalePercent = PreferenceConfiguration.DEFAULT_PORTAL_WINDOW_SCALE;
            this.portalSonarSpeedPercent = PreferenceConfiguration.DEFAULT_PORTAL_SONAR_SPEED;
            this.portalPulseAmount = PreferenceConfiguration.DEFAULT_PORTAL_PULSE_AMOUNT;
            this.portalPulseBrightnessPercent = PreferenceConfiguration.DEFAULT_PORTAL_PULSE_BRIGHTNESS;
            this.portalPulseLengthPercent = PreferenceConfiguration.DEFAULT_PORTAL_PULSE_LENGTH;
            this.portalPulseSpeedPercent = PreferenceConfiguration.DEFAULT_PORTAL_PULSE_SPEED;
            this.portalWaveAmpPercent = PreferenceConfiguration.DEFAULT_PORTAL_WAVE_AMP;
            this.portalSonarHoldMs = PreferenceConfiguration.DEFAULT_PORTAL_SONAR_HOLD_MS;
            this.portalReadyMs = PreferenceConfiguration.DEFAULT_PORTAL_READY_MS;
            this.portalChargeMs = PreferenceConfiguration.DEFAULT_PORTAL_CHARGE_MS;
            this.portalPreviewFadeMs = PreferenceConfiguration.DEFAULT_PORTAL_PREVIEW_FADE_MS;
            this.portalPreviewHoldMs = PreferenceConfiguration.DEFAULT_PORTAL_PREVIEW_HOLD_MS;
            this.portalExpandMs = PreferenceConfiguration.DEFAULT_PORTAL_EXPAND_MS;
            this.portalDisconnectMs = PreferenceConfiguration.DEFAULT_PORTAL_DISCONNECT_MS;
            this.portalDisconnectCloseMs = PreferenceConfiguration.DEFAULT_PORTAL_DISCONNECT_CLOSE_MS;
            this.portalDisconnectFadeMs = PreferenceConfiguration.DEFAULT_PORTAL_DISCONNECT_FADE_MS;
            this.portalDisconnectOpenMs = PreferenceConfiguration.DEFAULT_PORTAL_DISCONNECT_OPEN_MS;
            this.portalLaunchCloseMs = PreferenceConfiguration.DEFAULT_PORTAL_LAUNCH_CLOSE_MS;
            this.portalPulseCoilMs = PreferenceConfiguration.DEFAULT_PORTAL_PULSE_COIL_MS;
            this.portalDecouplePulseCoil = PreferenceConfiguration.DEFAULT_PORTAL_DECOUPLE_PULSE_COIL;
        }

        @NonNull
        public JSONObject toJsonObject() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("v", 2);
            obj.put("id", id != null ? id : UUID.randomUUID().toString());
            obj.put("name", name != null ? name : "Custom Theme");
            obj.put("isLight", colors.isLight);
            obj.put("bg", hex(colors.background));
            obj.put("surface", hex(colors.surface));
            obj.put("popup", hex(colors.popupTint));
            obj.put("accent", hex(colors.accent));
            obj.put("highlight", hex(colors.highlight));
            obj.put("textPrimary", hex(colors.primaryText));
            obj.put("textSecondary", hex(colors.secondaryText));
            obj.put("outline", hex(colors.cardOutline));
            obj.put("particle", hex(colors.particleColor));
            obj.put("particleMid", hex(colors.particleColorMid));
            obj.put("particleFar", hex(colors.particleColorFar));
            obj.put("pulse", hex(colors.pulseColor));
            obj.put("ring", hex(colors.ringColor));
            obj.put("charge", hex(colors.chargeColor));
            obj.put("blur", blurAmountPercent);
            obj.put("blurSat", blurSaturationPercent);
            obj.put("tint", frostedTintAmountPercent);
            obj.put("cardOutlinePx", cardOutlineThicknessPercent);
            obj.put("bgQuality", bgQuality != null ? bgQuality : PreferenceConfiguration.DEFAULT_BG_QUALITY);
            obj.put("pBright", portalParticleBrightnessPercent);
            obj.put("pSize", portalParticleSizePercent);
            obj.put("pDensity", portalParticleDensityPercent);
            obj.put("glow", portalGlowIntensityPercent);
            obj.put("scale", portalWindowScalePercent);
            obj.put("sonar", portalSonarSpeedPercent);
            obj.put("pulseAmt", portalPulseAmount);
            obj.put("pulseBright", portalPulseBrightnessPercent);
            obj.put("pulseLen", portalPulseLengthPercent);
            obj.put("pulseSpeed", portalPulseSpeedPercent);
            obj.put("waveAmp", portalWaveAmpPercent);
            obj.put("sonarHoldMs", portalSonarHoldMs);
            obj.put("readyMs", portalReadyMs);
            obj.put("chargeMs", portalChargeMs);
            obj.put("fadeMs", portalPreviewFadeMs);
            obj.put("holdMs", portalPreviewHoldMs);
            obj.put("expandMs", portalExpandMs);
            int totalDisc = Math.max(0, portalDisconnectCloseMs)
                    + Math.max(0, portalDisconnectFadeMs)
                    + Math.max(0, portalDisconnectOpenMs);
            obj.put("discMs", totalDisc > 0 ? totalDisc : portalDisconnectMs);
            obj.put("discCloseMs", portalDisconnectCloseMs);
            obj.put("discFadeMs", portalDisconnectFadeMs);
            obj.put("discOpenMs", portalDisconnectOpenMs);
            obj.put("launchMs", portalLaunchCloseMs);
            obj.put("pulseCoilMs", portalPulseCoilMs);
            obj.put("decoupleCoil", portalDecouplePulseCoil);
            return obj;
        }

        @NonNull
        public String toJsonString() {
            try {
                return toJsonObject().toString();
            } catch (JSONException e) {
                return "{}";
            }
        }

        @Nullable
        public static ThemeBundle fromJsonObject(@Nullable JSONObject obj) {
            if (obj == null) {
                return null;
            }
            try {
                boolean isLight = obj.optBoolean("isLight", false);
                int accent = parseColorOr(obj.optString("accent", ""), 0xFF3B6EF5);
                CustomPaletteSpec def = harmonizeFromAccent(accent, isLight);
                int highlight = parseColorOr(obj.optString("highlight", ""), def.highlight);
                int particleNear = parseColorOr(obj.optString("particle", ""), def.particleColor);
                int ring = parseColorOr(obj.optString("ring", ""), def.ringColor);
                int particleMid = parseColorOr(obj.optString("particleMid", ""),
                        blendColors(particleNear, ring, 0.45f));
                int particleFar = parseColorOr(obj.optString("particleFar", ""), ring);
                int pulse = parseColorOr(obj.optString("pulse", ""), ring);
                int charge = parseColorOr(obj.optString("charge", ""), highlight);
                CustomPaletteSpec spec = new CustomPaletteSpec(
                        isLight,
                        parseColorOr(obj.optString("bg", ""), def.background),
                        parseColorOr(obj.optString("surface", ""), def.surface),
                        parseColorOr(obj.optString("popup", ""), def.popupTint),
                        accent,
                        highlight,
                        parseColorOr(obj.optString("textPrimary", ""), def.primaryText),
                        parseColorOr(obj.optString("textSecondary", ""), def.secondaryText),
                        parseColorOr(obj.optString("outline", ""), def.cardOutline),
                        particleNear,
                        particleMid,
                        particleFar,
                        pulse,
                        ring,
                        charge
                );
                String id = obj.optString("id", "");
                if (id.trim().isEmpty()) {
                    id = UUID.randomUUID().toString();
                }
                String name = obj.optString("name", "Imported Theme").trim();
                if (name.isEmpty()) {
                    name = "Imported Theme";
                }
                ThemeBundle b = new ThemeBundle(id, name, spec);
                b.blurAmountPercent = clamp(obj.optInt("blur", PreferenceConfiguration.DEFAULT_BLUR_AMOUNT), 0, 2000);
                b.blurSaturationPercent = clamp(obj.optInt("blurSat", PreferenceConfiguration.DEFAULT_BLUR_SATURATION), 0, 500);
                b.frostedTintAmountPercent = clamp(obj.optInt("tint", isLight ? 76 : PreferenceConfiguration.DEFAULT_FROSTED_TINT_AMOUNT), 0, 100);
                b.cardOutlineThicknessPercent = clamp(obj.optInt("cardOutlinePx", PreferenceConfiguration.DEFAULT_CARD_OUTLINE_THICKNESS), 0, 2000);
                b.bgQuality = obj.optString("bgQuality", PreferenceConfiguration.DEFAULT_BG_QUALITY);
                b.portalParticleBrightnessPercent = clamp(obj.optInt("pBright", PreferenceConfiguration.DEFAULT_PORTAL_PARTICLE_BRIGHTNESS), 0, 2000);
                b.portalParticleSizePercent = clamp(obj.optInt("pSize", PreferenceConfiguration.DEFAULT_PORTAL_PARTICLE_SIZE), 1, 2000);
                b.portalParticleDensityPercent = clamp(obj.optInt("pDensity", PreferenceConfiguration.DEFAULT_PORTAL_PARTICLE_DENSITY), 0, 2000);
                b.portalGlowIntensityPercent = clamp(obj.optInt("glow", PreferenceConfiguration.DEFAULT_PORTAL_GLOW_INTENSITY), 0, 2000);
                b.portalWindowScalePercent = clamp(obj.optInt("scale", PreferenceConfiguration.DEFAULT_PORTAL_WINDOW_SCALE), 10, 2000);
                b.portalSonarSpeedPercent = clamp(obj.optInt("sonar", PreferenceConfiguration.DEFAULT_PORTAL_SONAR_SPEED), 0, 2000);
                b.portalPulseAmount = clamp(obj.optInt("pulseAmt", PreferenceConfiguration.DEFAULT_PORTAL_PULSE_AMOUNT), 0, 10);
                b.portalPulseBrightnessPercent = clamp(obj.optInt("pulseBright", PreferenceConfiguration.DEFAULT_PORTAL_PULSE_BRIGHTNESS), 0, 500);
                b.portalPulseLengthPercent = clamp(obj.optInt("pulseLen", PreferenceConfiguration.DEFAULT_PORTAL_PULSE_LENGTH), 10, 500);
                b.portalPulseSpeedPercent = clamp(obj.optInt("pulseSpeed", PreferenceConfiguration.DEFAULT_PORTAL_PULSE_SPEED), 0, 1000);
                b.portalWaveAmpPercent = clamp(obj.optInt("waveAmp", PreferenceConfiguration.DEFAULT_PORTAL_WAVE_AMP), 0, 500);
                b.portalSonarHoldMs = clamp(obj.optInt("sonarHoldMs", PreferenceConfiguration.DEFAULT_PORTAL_SONAR_HOLD_MS), 0, 30000);
                b.portalReadyMs = clamp(obj.optInt("readyMs", PreferenceConfiguration.DEFAULT_PORTAL_READY_MS), 0, 30000);
                b.portalChargeMs = clamp(obj.optInt("chargeMs", PreferenceConfiguration.DEFAULT_PORTAL_CHARGE_MS), 0, 30000);
                b.portalPreviewFadeMs = clamp(obj.optInt("fadeMs", PreferenceConfiguration.DEFAULT_PORTAL_PREVIEW_FADE_MS), 0, 30000);
                b.portalPreviewHoldMs = clamp(obj.optInt("holdMs", PreferenceConfiguration.DEFAULT_PORTAL_PREVIEW_HOLD_MS), 0, 30000);
                b.portalExpandMs = clamp(obj.optInt("expandMs", PreferenceConfiguration.DEFAULT_PORTAL_EXPAND_MS), 0, 30000);
                int legacyDisc = clamp(obj.optInt("discMs", PreferenceConfiguration.DEFAULT_PORTAL_DISCONNECT_MS), 0, 90000);
                b.portalDisconnectCloseMs = clamp(
                        obj.has("discCloseMs") ? obj.optInt("discCloseMs") : Math.round(legacyDisc * 0.346f),
                        0, 30000);
                b.portalDisconnectFadeMs = clamp(
                        obj.has("discFadeMs") ? obj.optInt("discFadeMs") : Math.round(legacyDisc * 0.269f),
                        0, 30000);
                b.portalDisconnectOpenMs = clamp(
                        obj.has("discOpenMs") ? obj.optInt("discOpenMs") : Math.max(0, legacyDisc - b.portalDisconnectCloseMs - b.portalDisconnectFadeMs),
                        0, 30000);
                b.portalDisconnectMs = b.portalDisconnectCloseMs + b.portalDisconnectFadeMs + b.portalDisconnectOpenMs;
                b.portalLaunchCloseMs = clamp(obj.optInt("launchMs", PreferenceConfiguration.DEFAULT_PORTAL_LAUNCH_CLOSE_MS), 0, 30000);
                b.portalPulseCoilMs = clamp(obj.optInt("pulseCoilMs", PreferenceConfiguration.DEFAULT_PORTAL_PULSE_COIL_MS), 0, 30000);
                b.portalDecouplePulseCoil = obj.optBoolean("decoupleCoil", PreferenceConfiguration.DEFAULT_PORTAL_DECOUPLE_PULSE_COIL);
                return b;
            } catch (Exception e) {
                return null;
            }
        }

        @Nullable
        public static ThemeBundle fromJsonString(@Nullable String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }
            try {
                return fromJsonObject(new JSONObject(raw.trim()));
            } catch (JSONException e) {
                return null;
            }
        }
    }

    private AppThemePalette(
            String key,
            String label,
            String shortLabel,
            boolean isLight,
            int background,
            int surface,
            int popupTint,
            int accent,
            int accentLight,
            int secondary,
            int highlight,
            int primaryText,
            int secondaryText,
            int cardOutline,
            int surfaceVariant,
            int muted,
            int warm,
            int particleColor,
            int particleColorMid,
            int particleColorFar,
            int pulseColor,
            int ringColor,
            int chargeColor,
            int frostedTintAmount) {
        this.key = key;
        this.label = label;
        this.shortLabel = shortLabel;
        this.isLight = isLight;
        this.background = background;
        this.surface = surface;
        this.popupTint = popupTint;
        this.accent = accent;
        this.accentLight = accentLight;
        this.secondary = secondary;
        this.highlight = highlight;
        this.primaryText = primaryText;
        this.secondaryText = secondaryText;
        this.cardOutline = cardOutline;
        this.surfaceVariant = surfaceVariant;
        this.muted = muted;
        this.warm = warm;
        this.particleColor = particleColor;
        this.particleColorMid = particleColorMid;
        this.particleColorFar = particleColorFar;
        this.pulseColor = pulseColor;
        this.ringColor = ringColor;
        this.chargeColor = chargeColor;
        this.frostedTintAmount = frostedTintAmount;
    }

    // 1. Default Dark — Midnight Blue (original PS Portal cosmic aesthetic)
    public static final AppThemePalette MIDNIGHT = new AppThemePalette(
            "midnight",
            "Midnight Blue (Default)",
            "Midnight",
            false,
            0xFF0D1117,
            0xFF161B27,
            0xFF141B2B,
            0xFF3B6EF5,
            0xFF58A6FF,
            0xFF1C2C4A,
            0xFF79C0FF,
            0xFFFFFFFF,
            0xFFB0BCD0,
            0xFFC8D8F8,
            0xFF1B2440,
            0xFF193860,
            0xFF2EA043,
            0xFFC7D8FF,
            0xFF8AA8FF,
            0xFF5E6BFF,
            0xFF5C72FF,
            0xFF5C72FF,
            0xFF38B5FF,
            -1
    );

    // 2. Full Light Mode — Frost Daylight
    public static final AppThemePalette DAYLIGHT = new AppThemePalette(
            "daylight",
            "Frost Daylight (Light)",
            "Daylight",
            true,
            0xFFE7EFFA,
            0xFFF7FAFF,
            0xFFF2F7FF,
            0xFF2563EB,
            0xFF3B82F6,
            0xFFD3E2F7,
            0xFF0284C7,
            0xFF0F172A,
            0xFF475569,
            0xFF64748B,
            0xFFEBF2FC,
            0xFFCBD9EE,
            0xFF10B981,
            0xFF2563EB,
            0xFF4F7CF2,
            0xFF818CF8,
            0xFF3B82F6,
            0xFF2563EB,
            0xFF0284C7,
            -1
    );

    // 3. Curated Dark #1 — Cyberpunk Neon
    public static final AppThemePalette CYBERPUNK = new AppThemePalette(
            "cyberpunk",
            "Cyberpunk Neon",
            "Cyberpunk",
            false,
            0xFF090716,
            0xFF14102A,
            0xFF181134,
            0xFFFF2D95,
            0xFFFF72C6,
            0xFF0B2239,
            0xFF00E5FF,
            0xFFFDF2FF,
            0xFFC4B5DF,
            0xFFE879F9,
            0xFF1B153A,
            0xFF33113B,
            0xFFFFE000,
            0xFF00E5FF,
            0xFFA855F7,
            0xFFFF2D95,
            0xFFFF2D95,
            0xFFFF2D95,
            0xFF00E5FF,
            -1
    );

    // 4. Curated Dark #2 — Emerald Forest
    public static final AppThemePalette FOREST = new AppThemePalette(
            "forest",
            "Emerald Forest",
            "Forest",
            false,
            0xFF07130E,
            0xFF10221A,
            0xFF0E241B,
            0xFF10B981,
            0xFF4ADE80,
            0xFF123326,
            0xFF6EE7B7,
            0xFFF0FDF4,
            0xFFA7C9B9,
            0xFF86EFAC,
            0xFF142B22,
            0xFF1B4332,
            0xFFF59E0B,
            0xFFA7F3D0,
            0xFF34D399,
            0xFF059669,
            0xFF10B981,
            0xFF10B981,
            0xFF6EE7B7,
            -1
    );

    // 5. Curated Dark #3 — Ember Sunset
    public static final AppThemePalette SUNSET = new AppThemePalette(
            "sunset",
            "Ember Sunset",
            "Sunset",
            false,
            0xFF160B07,
            0xFF25140D,
            0xFF29150D,
            0xFFF97316,
            0xFFFB923C,
            0xFF3E1C10,
            0xFFFBBF24,
            0xFFFFF7ED,
            0xFFD9B6A3,
            0xFFFDBA74,
            0xFF2E1810,
            0xFF4A2214,
            0xFFFF5072,
            0xFFFDE68A,
            0xFFFB923C,
            0xFFF43F5E,
            0xFFF97316,
            0xFFF97316,
            0xFFFBBF24,
            -1
    );

    private static final AppThemePalette[] PRESETS = {
            MIDNIGHT,
            DAYLIGHT,
            CYBERPUNK,
            FOREST,
            SUNSET
    };

    @NonNull
    public static AppThemePalette[] getPresets() {
        return PRESETS;
    }

    @NonNull
    public static AppThemePalette fromKey(String key) {
        if (key != null) {
            for (AppThemePalette p : PRESETS) {
                if (p.key.equals(key)) {
                    return p;
                }
            }
        }
        return MIDNIGHT;
    }

    @NonNull
    public static AppThemePalette getCurrent(@NonNull Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String key = prefs.getString(PREF_COLOR_SCHEME, DEFAULT_THEME_KEY);
        int savedTint = prefs.getInt(PreferenceConfiguration.FROSTED_TINT_AMOUNT_PREF_STRING, -1);

        if (key != null && key.startsWith(SAVED_THEME_PREFIX)) {
            String id = key.substring(SAVED_THEME_PREFIX.length());
            ThemeBundle found = findSavedThemeById(context, id);
            if (found != null) {
                return buildCustomPalette(found.colors, key, found.name, found.frostedTintAmountPercent);
            }
        }
        if (CUSTOM_THEME_KEY.equals(key)) {
            return buildCustomPalette(loadCustomSpec(context), CUSTOM_THEME_KEY, "Custom", savedTint);
        }
        AppThemePalette preset = fromKey(key);
        if (savedTint >= 0) {
            return preset.withFrostedTintAmount(savedTint);
        }
        return preset;
    }

    @NonNull
    public AppThemePalette withFrostedTintAmount(int tintAmount) {
        return new AppThemePalette(
                key, label, shortLabel, isLight, background, surface, popupTint,
                accent, accentLight, secondary, highlight, primaryText, secondaryText,
                cardOutline, surfaceVariant, muted, warm,
                particleColor, particleColorMid, particleColorFar, pulseColor, ringColor, chargeColor,
                clamp(tintAmount, 0, 100));
    }

    /**
     * Returns true when live frosted blur should actively render (blurAmount > 0 AND tint < 100%).
     * At 100% tint, cards are completely opaque so live blur is skipped to save GPU/CPU performance.
     */
    public static boolean isLiveBlurEffective(@NonNull Context context) {
        PreferenceConfiguration cfg = PreferenceConfiguration.readPreferences(context);
        if (cfg.blurAmountPercent <= 0) {
            return false;
        }
        AppThemePalette palette = getCurrent(context);
        return palette.getEffectiveTintAmount() < 100;
    }

    public int getEffectiveTintAmount() {
        if (frostedTintAmount >= 0) {
            return clamp(frostedTintAmount, 0, 100);
        }
        return isLight ? 76 : PreferenceConfiguration.DEFAULT_FROSTED_TINT_AMOUNT;
    }

    @NonNull
    public static CustomPaletteSpec specFromPreset(@NonNull AppThemePalette preset) {
        return new CustomPaletteSpec(
                preset.isLight,
                preset.background,
                preset.surface,
                preset.popupTint,
                preset.accent,
                preset.highlight,
                preset.primaryText,
                preset.secondaryText,
                preset.cardOutline,
                preset.particleColor,
                preset.particleColorMid,
                preset.particleColorFar,
                preset.pulseColor,
                preset.ringColor,
                preset.chargeColor
        );
    }

    @NonNull
    public static CustomPaletteSpec loadCustomSpec(@NonNull Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean light = prefs.getBoolean(PREF_CUSTOM_IS_LIGHT, false);
        int savedAccent = prefs.getInt(PREF_CUSTOM_ACCENT, 0xFF7C3AED);
        CustomPaletteSpec defaults = harmonizeFromAccent(savedAccent, light);
        int savedHighlight = prefs.getInt(PREF_CUSTOM_HIGHLIGHT, defaults.highlight);
        int savedParticleNear = prefs.getInt(PREF_CUSTOM_PARTICLE, defaults.particleColor);
        int savedRing = prefs.getInt(PREF_CUSTOM_RING, defaults.ringColor);
        int savedParticleMid = prefs.contains(PREF_CUSTOM_PARTICLE_MID)
                ? prefs.getInt(PREF_CUSTOM_PARTICLE_MID, defaults.particleColorMid)
                : blendColors(savedParticleNear, savedRing, 0.45f);
        int savedParticleFar = prefs.contains(PREF_CUSTOM_PARTICLE_FAR)
                ? prefs.getInt(PREF_CUSTOM_PARTICLE_FAR, defaults.particleColorFar)
                : savedRing;
        int savedPulse = prefs.contains(PREF_CUSTOM_PULSE)
                ? prefs.getInt(PREF_CUSTOM_PULSE, defaults.pulseColor)
                : savedRing;
        int savedCharge = prefs.contains(PREF_CUSTOM_CHARGE)
                ? prefs.getInt(PREF_CUSTOM_CHARGE, defaults.chargeColor)
                : savedHighlight;
        return new CustomPaletteSpec(
                light,
                prefs.getInt(PREF_CUSTOM_BG, defaults.background),
                prefs.getInt(PREF_CUSTOM_SURFACE, defaults.surface),
                prefs.getInt(PREF_CUSTOM_POPUP_TINT, defaults.popupTint),
                savedAccent,
                savedHighlight,
                prefs.getInt(PREF_CUSTOM_TEXT_PRIMARY, defaults.primaryText),
                prefs.getInt(PREF_CUSTOM_TEXT_SECONDARY, defaults.secondaryText),
                prefs.getInt(PREF_CUSTOM_CARD_OUTLINE, defaults.cardOutline),
                savedParticleNear,
                savedParticleMid,
                savedParticleFar,
                savedPulse,
                savedRing,
                savedCharge
        );
    }

    public static void saveCustomSpec(@NonNull Context context, @NonNull CustomPaletteSpec spec) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_COLOR_SCHEME, CUSTOM_THEME_KEY)
                .putBoolean(PREF_CUSTOM_IS_LIGHT, spec.isLight)
                .putInt(PREF_CUSTOM_BG, spec.background | 0xFF000000)
                .putInt(PREF_CUSTOM_SURFACE, spec.surface | 0xFF000000)
                .putInt(PREF_CUSTOM_POPUP_TINT, spec.popupTint | 0xFF000000)
                .putInt(PREF_CUSTOM_ACCENT, spec.accent | 0xFF000000)
                .putInt(PREF_CUSTOM_HIGHLIGHT, spec.highlight | 0xFF000000)
                .putInt(PREF_CUSTOM_TEXT_PRIMARY, spec.primaryText | 0xFF000000)
                .putInt(PREF_CUSTOM_TEXT_SECONDARY, spec.secondaryText | 0xFF000000)
                .putInt(PREF_CUSTOM_CARD_OUTLINE, spec.cardOutline | 0xFF000000)
                .putInt(PREF_CUSTOM_PARTICLE, spec.particleColor | 0xFF000000)
                .putInt(PREF_CUSTOM_PARTICLE_MID, spec.particleColorMid | 0xFF000000)
                .putInt(PREF_CUSTOM_PARTICLE_FAR, spec.particleColorFar | 0xFF000000)
                .putInt(PREF_CUSTOM_PULSE, spec.pulseColor | 0xFF000000)
                .putInt(PREF_CUSTOM_RING, spec.ringColor | 0xFF000000)
                .putInt(PREF_CUSTOM_CHARGE, spec.chargeColor | 0xFF000000)
                .apply();
    }

    /**
     * Loads the currently active full ThemeBundle (combining current palette + glass + Portal FX prefs).
     */
    @NonNull
    public static ThemeBundle loadActiveBundle(@NonNull Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        PreferenceConfiguration cfg = PreferenceConfiguration.readPreferences(context);
        AppThemePalette current = getCurrent(context);
        String key = prefs.getString(PREF_COLOR_SCHEME, DEFAULT_THEME_KEY);

        if (key != null && key.startsWith(SAVED_THEME_PREFIX)) {
            ThemeBundle saved = findSavedThemeById(context, key.substring(SAVED_THEME_PREFIX.length()));
            if (saved != null) {
                return saved;
            }
        }

        CustomPaletteSpec spec = CUSTOM_THEME_KEY.equals(key)
                ? loadCustomSpec(context)
                : specFromPreset(current);
        ThemeBundle b = new ThemeBundle(UUID.randomUUID().toString(), current.label, spec);
        b.blurAmountPercent = clamp(cfg.blurAmountPercent, 0, 2000);
        b.blurSaturationPercent = clamp(cfg.blurSaturationPercent, 0, 500);
        b.frostedTintAmountPercent = current.getEffectiveTintAmount();
        b.cardOutlineThicknessPercent = clamp(cfg.cardOutlineThicknessPercent, 0, 2000);
        b.bgQuality = prefs.getString(PreferenceConfiguration.BG_QUALITY_PREF_STRING, PreferenceConfiguration.DEFAULT_BG_QUALITY);
        b.portalParticleBrightnessPercent = clamp(cfg.portalParticleBrightnessPercent, 0, 2000);
        b.portalParticleSizePercent = clamp(cfg.portalParticleSizePercent, 1, 2000);
        b.portalParticleDensityPercent = clamp(cfg.portalParticleDensityPercent, 0, 2000);
        b.portalGlowIntensityPercent = clamp(cfg.portalGlowIntensityPercent, 0, 2000);
        b.portalWindowScalePercent = clamp(cfg.portalWindowScalePercent, 10, 2000);
        b.portalSonarSpeedPercent = clamp(cfg.portalSonarSpeedPercent, 0, 2000);
        b.portalPulseAmount = clamp(cfg.portalPulseAmount, 0, 10);
        b.portalPulseBrightnessPercent = clamp(cfg.portalPulseBrightnessPercent, 0, 500);
        b.portalPulseLengthPercent = clamp(cfg.portalPulseLengthPercent, 10, 500);
        b.portalPulseSpeedPercent = clamp(cfg.portalPulseSpeedPercent, 0, 1000);
        b.portalWaveAmpPercent = clamp(cfg.portalWaveAmpPercent, 0, 500);
        b.portalSonarHoldMs = clamp(cfg.portalSonarHoldMs, 0, 30000);
        b.portalReadyMs = clamp(cfg.portalReadyMs, 0, 30000);
        b.portalChargeMs = clamp(cfg.portalChargeMs, 0, 30000);
        b.portalPreviewFadeMs = clamp(cfg.portalPreviewFadeMs, 0, 30000);
        b.portalPreviewHoldMs = clamp(cfg.portalPreviewHoldMs, 0, 30000);
        b.portalExpandMs = clamp(cfg.portalExpandMs, 0, 30000);
        b.portalDisconnectCloseMs = clamp(cfg.portalDisconnectCloseMs, 0, 30000);
        b.portalDisconnectFadeMs = clamp(cfg.portalDisconnectFadeMs, 0, 30000);
        b.portalDisconnectOpenMs = clamp(cfg.portalDisconnectOpenMs, 0, 30000);
        b.portalDisconnectMs = b.portalDisconnectCloseMs + b.portalDisconnectFadeMs + b.portalDisconnectOpenMs;
        b.portalLaunchCloseMs = clamp(cfg.portalLaunchCloseMs, 0, 30000);
        b.portalPulseCoilMs = clamp(cfg.portalPulseCoilMs, 0, 30000);
        b.portalDecouplePulseCoil = cfg.portalDecouplePulseCoil;
        return b;
    }

    /**
     * Applies a full ThemeBundle to SharedPreferences and sets the active theme key.
     */
    public static void applyThemeBundle(@NonNull Context context, @NonNull ThemeBundle bundle, @NonNull String targetKey) {
        SharedPreferences.Editor ed = PreferenceManager.getDefaultSharedPreferences(context).edit();
        ed.putString(PREF_COLOR_SCHEME, targetKey);
        ed.putBoolean(PREF_CUSTOM_IS_LIGHT, bundle.colors.isLight);
        ed.putInt(PREF_CUSTOM_BG, bundle.colors.background | 0xFF000000);
        ed.putInt(PREF_CUSTOM_SURFACE, bundle.colors.surface | 0xFF000000);
        ed.putInt(PREF_CUSTOM_POPUP_TINT, bundle.colors.popupTint | 0xFF000000);
        ed.putInt(PREF_CUSTOM_ACCENT, bundle.colors.accent | 0xFF000000);
        ed.putInt(PREF_CUSTOM_HIGHLIGHT, bundle.colors.highlight | 0xFF000000);
        ed.putInt(PREF_CUSTOM_TEXT_PRIMARY, bundle.colors.primaryText | 0xFF000000);
        ed.putInt(PREF_CUSTOM_TEXT_SECONDARY, bundle.colors.secondaryText | 0xFF000000);
        ed.putInt(PREF_CUSTOM_CARD_OUTLINE, bundle.colors.cardOutline | 0xFF000000);
        ed.putInt(PREF_CUSTOM_PARTICLE, bundle.colors.particleColor | 0xFF000000);
        ed.putInt(PREF_CUSTOM_PARTICLE_MID, bundle.colors.particleColorMid | 0xFF000000);
        ed.putInt(PREF_CUSTOM_PARTICLE_FAR, bundle.colors.particleColorFar | 0xFF000000);
        ed.putInt(PREF_CUSTOM_PULSE, bundle.colors.pulseColor | 0xFF000000);
        ed.putInt(PREF_CUSTOM_RING, bundle.colors.ringColor | 0xFF000000);
        ed.putInt(PREF_CUSTOM_CHARGE, bundle.colors.chargeColor | 0xFF000000);

        ed.putInt(PreferenceConfiguration.BLUR_AMOUNT_PREF_STRING, clamp(bundle.blurAmountPercent, 0, 2000));
        ed.putInt(PreferenceConfiguration.BLUR_SATURATION_PREF_STRING, clamp(bundle.blurSaturationPercent, 0, 500));
        ed.putInt(PreferenceConfiguration.FROSTED_TINT_AMOUNT_PREF_STRING, clamp(bundle.frostedTintAmountPercent, 0, 100));
        ed.putInt(PreferenceConfiguration.CARD_OUTLINE_THICKNESS_PREF_STRING, clamp(bundle.cardOutlineThicknessPercent, 0, 2000));
        if (bundle.bgQuality != null) {
            ed.putString(PreferenceConfiguration.BG_QUALITY_PREF_STRING, bundle.bgQuality);
        }
        ed.putInt(PreferenceConfiguration.PORTAL_PARTICLE_BRIGHTNESS_PREF_STRING, clamp(bundle.portalParticleBrightnessPercent, 0, 2000));
        ed.putInt(PreferenceConfiguration.PORTAL_PARTICLE_SIZE_PREF_STRING, clamp(bundle.portalParticleSizePercent, 1, 2000));
        ed.putInt(PreferenceConfiguration.PORTAL_PARTICLE_DENSITY_PREF_STRING, clamp(bundle.portalParticleDensityPercent, 0, 2000));
        ed.putInt(PreferenceConfiguration.PORTAL_GLOW_INTENSITY_PREF_STRING, clamp(bundle.portalGlowIntensityPercent, 0, 2000));
        ed.putInt(PreferenceConfiguration.PORTAL_WINDOW_SCALE_PREF_STRING, clamp(bundle.portalWindowScalePercent, 10, 2000));
        ed.putInt(PreferenceConfiguration.PORTAL_SONAR_SPEED_PREF_STRING, clamp(bundle.portalSonarSpeedPercent, 0, 2000));
        ed.putInt(PreferenceConfiguration.PORTAL_PULSE_AMOUNT_PREF_STRING, clamp(bundle.portalPulseAmount, 0, 10));
        ed.putInt(PreferenceConfiguration.PORTAL_PULSE_BRIGHTNESS_PREF_STRING, clamp(bundle.portalPulseBrightnessPercent, 0, 500));
        ed.putInt(PreferenceConfiguration.PORTAL_PULSE_LENGTH_PREF_STRING, clamp(bundle.portalPulseLengthPercent, 10, 500));
        ed.putInt(PreferenceConfiguration.PORTAL_PULSE_SPEED_PREF_STRING, clamp(bundle.portalPulseSpeedPercent, 0, 1000));
        ed.putInt(PreferenceConfiguration.PORTAL_WAVE_AMP_PREF_STRING, clamp(bundle.portalWaveAmpPercent, 0, 500));
        ed.putInt(PreferenceConfiguration.PORTAL_SONAR_HOLD_MS_PREF_STRING, clamp(bundle.portalSonarHoldMs, 0, 30000));
        ed.putInt(PreferenceConfiguration.PORTAL_READY_MS_PREF_STRING, clamp(bundle.portalReadyMs, 0, 30000));
        ed.putInt(PreferenceConfiguration.PORTAL_CHARGE_MS_PREF_STRING, clamp(bundle.portalChargeMs, 0, 30000));
        ed.putInt(PreferenceConfiguration.PORTAL_PREVIEW_FADE_MS_PREF_STRING, clamp(bundle.portalPreviewFadeMs, 0, 30000));
        ed.putInt(PreferenceConfiguration.PORTAL_PREVIEW_HOLD_MS_PREF_STRING, clamp(bundle.portalPreviewHoldMs, 0, 30000));
        ed.putInt(PreferenceConfiguration.PORTAL_EXPAND_MS_PREF_STRING, clamp(bundle.portalExpandMs, 0, 30000));
        int closeMs = clamp(bundle.portalDisconnectCloseMs, 0, 30000);
        int fadeMs = clamp(bundle.portalDisconnectFadeMs, 0, 30000);
        int openMs = clamp(bundle.portalDisconnectOpenMs, 0, 30000);
        ed.putInt(PreferenceConfiguration.PORTAL_DISCONNECT_CLOSE_MS_PREF_STRING, closeMs);
        ed.putInt(PreferenceConfiguration.PORTAL_DISCONNECT_FADE_MS_PREF_STRING, fadeMs);
        ed.putInt(PreferenceConfiguration.PORTAL_DISCONNECT_OPEN_MS_PREF_STRING, openMs);
        ed.putInt(PreferenceConfiguration.PORTAL_DISCONNECT_MS_PREF_STRING, closeMs + fadeMs + openMs);
        ed.putInt(PreferenceConfiguration.PORTAL_LAUNCH_CLOSE_MS_PREF_STRING, clamp(bundle.portalLaunchCloseMs, 0, 30000));
        ed.putInt(PreferenceConfiguration.PORTAL_PULSE_COIL_MS_PREF_STRING, clamp(bundle.portalPulseCoilMs, 0, 30000));
        ed.putBoolean(PreferenceConfiguration.PORTAL_DECOUPLE_PULSE_COIL_PREF_STRING, bundle.portalDecouplePulseCoil);
        ed.apply();
    }

    /**
     * When the user selects a saved theme directly from Settings' Theme list, apply all of its
     * glass/blur/outline/Portal FX parameters as well.
     */
    public static void onThemeKeySelected(@NonNull Context context, @Nullable String key) {
        if (key != null && key.startsWith(SAVED_THEME_PREFIX)) {
            ThemeBundle saved = findSavedThemeById(context, key.substring(SAVED_THEME_PREFIX.length()));
            if (saved != null) {
                applyThemeBundle(context, saved, key);
            }
        } else if (key != null && !CUSTOM_THEME_KEY.equals(key)) {
            // Built-in preset selected: reset frosted tint to balanced default for that preset's Light/Dark mode
            AppThemePalette preset = fromKey(key);
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putInt(PreferenceConfiguration.FROSTED_TINT_AMOUNT_PREF_STRING,
                            preset.isLight ? 76 : PreferenceConfiguration.DEFAULT_FROSTED_TINT_AMOUNT)
                    .apply();
        }
    }

    @NonNull
    public static List<ThemeBundle> loadSavedThemes(@NonNull Context context) {
        List<ThemeBundle> result = new ArrayList<>();
        String raw = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_SAVED_THEMES_JSON, "[]");
        if (raw == null || raw.trim().isEmpty()) {
            return result;
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                ThemeBundle b = ThemeBundle.fromJsonObject(arr.optJSONObject(i));
                if (b != null) {
                    result.add(b);
                }
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    @Nullable
    public static ThemeBundle findSavedThemeById(@NonNull Context context, @Nullable String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (ThemeBundle b : loadSavedThemes(context)) {
            if (id.equals(b.id)) {
                return b;
            }
        }
        return null;
    }

    public static void saveOrUpdateTheme(@NonNull Context context, @NonNull ThemeBundle bundle) {
        List<ThemeBundle> list = loadSavedThemes(context);
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (bundle.id != null && bundle.id.equals(list.get(i).id)) {
                list.set(i, bundle);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            list.add(0, bundle);
        }
        persistSavedThemes(context, list);
    }

    public static void deleteSavedTheme(@NonNull Context context, @NonNull String id) {
        List<ThemeBundle> list = loadSavedThemes(context);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (id.equals(list.get(i).id)) {
                list.remove(i);
            }
        }
        persistSavedThemes(context, list);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if ((SAVED_THEME_PREFIX + id).equals(prefs.getString(PREF_COLOR_SCHEME, DEFAULT_THEME_KEY))) {
            prefs.edit().putString(PREF_COLOR_SCHEME, CUSTOM_THEME_KEY).apply();
        }
    }

    private static void persistSavedThemes(@NonNull Context context, @NonNull List<ThemeBundle> list) {
        JSONArray arr = new JSONArray();
        for (ThemeBundle b : list) {
            try {
                arr.put(b.toJsonObject());
            } catch (JSONException ignored) {
            }
        }
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_SAVED_THEMES_JSON, arr.toString())
                .apply();
    }

    /**
     * Derives a cohesive 14-channel palette from a single primary accent color
     * in either Dark or Light base mode.
     */
    @NonNull
    public static CustomPaletteSpec harmonizeFromAccent(int accentColor, boolean isLight) {
        float[] hsv = new float[3];
        Color.colorToHSV(accentColor | 0xFF000000, hsv);
        float hue = hsv[0];
        float sat = Math.max(0.45f, hsv[1]);

        int bg;
        int surf;
        int popup;
        int highlight;
        int textPrimary;
        int textSecondary;
        int outline;
        int particleNear;
        int particleMid;
        int particleFar;
        int pulse;
        int ring;
        int charge;

        if (isLight) {
            bg = Color.HSVToColor(new float[]{ hue, Math.min(0.14f, sat * 0.16f), 0.94f });
            surf = Color.HSVToColor(new float[]{ hue, Math.min(0.06f, sat * 0.08f), 0.99f });
            popup = Color.HSVToColor(new float[]{ hue, Math.min(0.09f, sat * 0.12f), 0.97f });
            highlight = Color.HSVToColor(new float[]{ (hue + 24f) % 360f, Math.min(0.88f, sat), 0.82f });
            textPrimary = Color.HSVToColor(new float[]{ hue, Math.min(0.28f, sat * 0.35f), 0.14f });
            textSecondary = Color.HSVToColor(new float[]{ hue, Math.min(0.22f, sat * 0.28f), 0.38f });
            outline = Color.HSVToColor(new float[]{ hue, Math.min(0.32f, sat * 0.40f), 0.50f });
            particleNear = Color.HSVToColor(new float[]{ hue, Math.min(0.82f, sat * 0.92f), 0.86f });
            particleMid = Color.HSVToColor(new float[]{ (hue + 14f) % 360f, Math.min(0.70f, sat * 0.82f), 0.90f });
            particleFar = Color.HSVToColor(new float[]{ (hue + 28f) % 360f, Math.min(0.58f, sat * 0.72f), 0.94f });
            pulse = Color.HSVToColor(new float[]{ (hue + 8f) % 360f, Math.min(0.85f, sat), 0.90f });
            ring = Color.HSVToColor(new float[]{ hue, Math.min(0.85f, sat), 0.88f });
            charge = Color.HSVToColor(new float[]{ (hue + 32f) % 360f, Math.min(0.90f, sat), 0.84f });
        } else {
            bg = Color.HSVToColor(new float[]{ hue, Math.min(0.45f, sat * 0.55f), 0.09f });
            surf = Color.HSVToColor(new float[]{ hue, Math.min(0.38f, sat * 0.48f), 0.16f });
            popup = Color.HSVToColor(new float[]{ hue, Math.min(0.42f, sat * 0.52f), 0.14f });
            highlight = Color.HSVToColor(new float[]{ (hue + 28f) % 360f, Math.min(0.82f, sat), 0.98f });
            textPrimary = 0xFFFFFFFF;
            textSecondary = Color.HSVToColor(new float[]{ hue, Math.min(0.16f, sat * 0.22f), 0.80f });
            outline = Color.HSVToColor(new float[]{ hue, Math.min(0.28f, sat * 0.35f), 0.92f });
            particleNear = Color.HSVToColor(new float[]{ (hue + 16f) % 360f, Math.min(0.42f, sat * 0.55f), 1.0f });
            particleMid = Color.HSVToColor(new float[]{ (hue + 10f) % 360f, Math.min(0.62f, sat * 0.78f), 0.98f });
            particleFar = Color.HSVToColor(new float[]{ hue, Math.min(0.80f, sat * 0.92f), 0.92f });
            pulse = Color.HSVToColor(new float[]{ hue, Math.min(0.85f, sat), 0.96f });
            ring = Color.HSVToColor(new float[]{ hue, Math.min(0.85f, sat), 0.96f });
            charge = Color.HSVToColor(new float[]{ (hue + 34f) % 360f, Math.min(0.86f, sat), 1.0f });
        }

        return new CustomPaletteSpec(
                isLight,
                bg,
                surf,
                popup,
                accentColor | 0xFF000000,
                highlight,
                textPrimary,
                textSecondary,
                outline,
                particleNear,
                particleMid,
                particleFar,
                pulse,
                ring,
                charge);
    }

    @NonNull
    public static AppThemePalette buildCustomPalette(@NonNull CustomPaletteSpec spec) {
        return buildCustomPalette(spec, CUSTOM_THEME_KEY, "Custom", -1);
    }

    @NonNull
    public static AppThemePalette buildCustomPalette(@NonNull CustomPaletteSpec spec, int frostedTintAmount) {
        return buildCustomPalette(spec, CUSTOM_THEME_KEY, "Custom", frostedTintAmount);
    }

    @NonNull
    public static AppThemePalette buildCustomPalette(
            @NonNull CustomPaletteSpec spec,
            @NonNull String key,
            @NonNull String label,
            int frostedTintAmount) {
        float[] accentHsv = new float[3];
        Color.colorToHSV(spec.accent, accentHsv);
        int accentLight = spec.isLight
                ? Color.HSVToColor(new float[]{ accentHsv[0], Math.max(0.55f, accentHsv[1]), Math.min(0.86f, Math.max(0.65f, accentHsv[2])) })
                : Color.HSVToColor(new float[]{ accentHsv[0], Math.max(0.42f, accentHsv[1] * 0.78f), 0.98f });

        int secondary = blendColors(spec.background, spec.accent, spec.isLight ? 0.18f : 0.28f);
        int surfaceVariant = blendColors(spec.surface, spec.accent, spec.isLight ? 0.10f : 0.14f);
        int muted = blendColors(spec.surface, spec.background, 0.5f);

        return new AppThemePalette(
                key,
                label,
                label,
                spec.isLight,
                spec.background | 0xFF000000,
                spec.surface | 0xFF000000,
                spec.popupTint | 0xFF000000,
                spec.accent | 0xFF000000,
                accentLight,
                secondary,
                spec.highlight | 0xFF000000,
                spec.primaryText | 0xFF000000,
                spec.secondaryText | 0xFF000000,
                spec.cardOutline | 0xFF000000,
                surfaceVariant,
                muted,
                0xFF2EA043,
                spec.particleColor | 0xFF000000,
                spec.particleColorMid | 0xFF000000,
                spec.particleColorFar | 0xFF000000,
                spec.pulseColor | 0xFF000000,
                spec.ringColor | 0xFF000000,
                spec.chargeColor | 0xFF000000,
                frostedTintAmount
        );
    }

    public int getPrimaryTextColor() {
        return primaryText | 0xFF000000;
    }

    public int getSecondaryTextColor() {
        return secondaryText | 0xFF000000;
    }

    public int getCardRimColor() {
        return withAlphaInt(cardOutline, isLight ? 0x56 : 0x58);
    }

    public int getDividerColor() {
        return withAlphaInt(cardOutline, isLight ? 0x2A : 0x26);
    }

    /**
     * Computes frosted card fill tint using the configured Frosted Card Tint Amount (0-100%).
     * 0% = completely clear glass (alpha 0), 100% = fully opaque solid card (alpha 255).
     */
    public int getFrostedCardTint(int defaultDarkTint) {
        int tintPct = getEffectiveTintAmount();
        int alpha = Math.round((tintPct / 100f) * 255f);
        return withAlphaInt(surface, alpha);
    }

    public int getDialogSurfaceColor(boolean blurred) {
        int tintPct = getEffectiveTintAmount();
        int alpha = Math.round((tintPct / 100f) * 255f);
        if (!blurred && tintPct > 0 && tintPct < 100) {
            alpha = Math.max(isLight ? 0xE6 : 0xD8, alpha);
        }
        return withAlphaInt(popupTint, alpha);
    }

    public int getDialogOverlayTint() {
        int tintPct = getEffectiveTintAmount();
        int alpha = Math.round((tintPct / 100f) * 255f);
        return withAlphaInt(popupTint, alpha);
    }

    public int getQuickMenuCardTint(boolean withBlur) {
        int tintPct = getEffectiveTintAmount();
        int alpha = Math.round((tintPct / 100f) * 255f);
        if (!withBlur && tintPct > 0 && tintPct < 100) {
            alpha = Math.max(isLight ? 0xE6 : 0xD8, alpha);
        }
        return withAlphaInt(surface, alpha);
    }

    @NonNull
    public String getSignature() {
        return key + ":" + isLight + ":" + background + ":" + surface + ":" + popupTint
                + ":" + accent + ":" + highlight + ":" + primaryText + ":" + secondaryText
                + ":" + cardOutline + ":" + particleColor + ":" + particleColorMid
                + ":" + particleColorFar + ":" + pulseColor + ":" + ringColor
                + ":" + chargeColor + ":" + frostedTintAmount;
    }

    public int getNeonAccent() {
        float[] hsv = new float[3];
        Color.colorToHSV(isLight ? accent : accentLight, hsv);
        if (isLight) {
            if (hsv[1] > 0.05f) {
                hsv[1] = Math.min(1.0f, Math.max(hsv[1], 0.72f));
            }
            hsv[2] = Math.min(0.82f, Math.max(hsv[2], 0.58f));
        } else {
            if (hsv[1] > 0.05f) {
                hsv[1] = Math.min(1.0f, Math.max(hsv[1], 0.78f));
            }
            hsv[2] = Math.min(1.0f, Math.max(hsv[2], 0.98f));
        }
        return Color.HSVToColor(0xFF, hsv);
    }

    public int getNeonTextAccent() {
        float[] hsv = new float[3];
        Color.colorToHSV(isLight ? accent : accentLight, hsv);
        if (isLight) {
            if (hsv[1] > 0.05f) {
                hsv[1] = Math.min(1.0f, Math.max(hsv[1], 0.70f));
            }
            hsv[2] = Math.min(0.75f, Math.max(hsv[2], 0.45f));
        } else {
            if (hsv[1] > 0.05f) {
                hsv[1] = Math.min(1.0f, Math.max(hsv[1] * 0.95f, 0.52f));
            }
            hsv[2] = 1.0f;
        }
        return Color.HSVToColor(0xFF, hsv);
    }

    public static int blendColors(int c1, int c2, float ratio) {
        float inv = 1f - ratio;
        int r = Math.round(Color.red(c1) * inv + Color.red(c2) * ratio);
        int g = Math.round(Color.green(c1) * inv + Color.green(c2) * ratio);
        int b = Math.round(Color.blue(c1) * inv + Color.blue(c2) * ratio);
        return Color.rgb(
                Math.max(0, Math.min(255, r)),
                Math.max(0, Math.min(255, g)),
                Math.max(0, Math.min(255, b)));
    }

    public static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public static int withAlphaInt(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static String hex(int color) {
        return String.format(Locale.US, "#%06X", color & 0x00FFFFFF);
    }

    private static int parseColorOr(String raw, int fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            String s = raw.trim();
            if (!s.startsWith("#")) {
                s = "#" + s;
            }
            return Color.parseColor(s) | 0xFF000000;
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
