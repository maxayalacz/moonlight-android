package com.limelight.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

/**
 * Color palette system supporting Default Dark (Midnight), Full Light Mode (Frost Daylight),
 * 3 curated dark themes (Cyberpunk Neon, Emerald Forest, Ember Sunset), and a 10-channel
 * customizable user palette with preset base seeding, Dark/Light base, and Auto-Harmonize.
 */
public final class AppThemePalette {
    public static final String PREF_COLOR_SCHEME = "list_color_scheme";
    public static final String DEFAULT_THEME_KEY = "midnight";
    public static final String CUSTOM_THEME_KEY = "custom";

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
    public static final String PREF_CUSTOM_RING = "custom_palette_ring";

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
    public final int ringColor;

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
        public int ringColor;

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
                int ringColor) {
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
            this.ringColor = ringColor;
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
            int ringColor) {
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
        this.ringColor = ringColor;
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
            0xFF8AA8FF,
            0xFF5C72FF
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
            0xFF4F7CF2,
            0xFF2563EB
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
            0xFFFF2D95
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
            0xFF6EE7B7,
            0xFF10B981
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
            0xFFFDBA74,
            0xFFF97316
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
        if (CUSTOM_THEME_KEY.equals(key)) {
            return buildCustomPalette(loadCustomSpec(context));
        }
        return fromKey(key);
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
                preset.ringColor
        );
    }

    @NonNull
    public static CustomPaletteSpec loadCustomSpec(@NonNull Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean light = prefs.getBoolean(PREF_CUSTOM_IS_LIGHT, false);
        int savedAccent = prefs.getInt(PREF_CUSTOM_ACCENT, 0xFF7C3AED);
        CustomPaletteSpec defaults = harmonizeFromAccent(savedAccent, light);
        return new CustomPaletteSpec(
                light,
                prefs.getInt(PREF_CUSTOM_BG, defaults.background),
                prefs.getInt(PREF_CUSTOM_SURFACE, defaults.surface),
                prefs.getInt(PREF_CUSTOM_POPUP_TINT, defaults.popupTint),
                savedAccent,
                prefs.getInt(PREF_CUSTOM_HIGHLIGHT, defaults.highlight),
                prefs.getInt(PREF_CUSTOM_TEXT_PRIMARY, defaults.primaryText),
                prefs.getInt(PREF_CUSTOM_TEXT_SECONDARY, defaults.secondaryText),
                prefs.getInt(PREF_CUSTOM_CARD_OUTLINE, defaults.cardOutline),
                prefs.getInt(PREF_CUSTOM_PARTICLE, defaults.particleColor),
                prefs.getInt(PREF_CUSTOM_RING, defaults.ringColor)
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
                .putInt(PREF_CUSTOM_RING, spec.ringColor | 0xFF000000)
                .apply();
    }

    /**
     * Derives a cohesive 10-channel palette from a single primary accent color
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
        int particle;
        int ring;

        if (isLight) {
            bg = Color.HSVToColor(new float[]{ hue, Math.min(0.14f, sat * 0.16f), 0.94f });
            surf = Color.HSVToColor(new float[]{ hue, Math.min(0.06f, sat * 0.08f), 0.99f });
            popup = Color.HSVToColor(new float[]{ hue, Math.min(0.09f, sat * 0.12f), 0.97f });
            highlight = Color.HSVToColor(new float[]{ (hue + 24f) % 360f, Math.min(0.88f, sat), 0.82f });
            textPrimary = Color.HSVToColor(new float[]{ hue, Math.min(0.28f, sat * 0.35f), 0.14f });
            textSecondary = Color.HSVToColor(new float[]{ hue, Math.min(0.22f, sat * 0.28f), 0.38f });
            outline = Color.HSVToColor(new float[]{ hue, Math.min(0.32f, sat * 0.40f), 0.50f });
            particle = Color.HSVToColor(new float[]{ (hue + 14f) % 360f, Math.min(0.70f, sat * 0.85f), 0.90f });
            ring = Color.HSVToColor(new float[]{ hue, Math.min(0.85f, sat), 0.88f });
        } else {
            bg = Color.HSVToColor(new float[]{ hue, Math.min(0.45f, sat * 0.55f), 0.09f });
            surf = Color.HSVToColor(new float[]{ hue, Math.min(0.38f, sat * 0.48f), 0.16f });
            popup = Color.HSVToColor(new float[]{ hue, Math.min(0.42f, sat * 0.52f), 0.14f });
            highlight = Color.HSVToColor(new float[]{ (hue + 28f) % 360f, Math.min(0.82f, sat), 0.98f });
            textPrimary = 0xFFFFFFFF;
            textSecondary = Color.HSVToColor(new float[]{ hue, Math.min(0.16f, sat * 0.22f), 0.80f });
            outline = Color.HSVToColor(new float[]{ hue, Math.min(0.28f, sat * 0.35f), 0.92f });
            particle = Color.HSVToColor(new float[]{ (hue + 18f) % 360f, Math.min(0.55f, sat * 0.72f), 1.0f });
            ring = Color.HSVToColor(new float[]{ hue, Math.min(0.85f, sat), 0.96f });
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
                particle,
                ring);
    }

    @NonNull
    public static AppThemePalette buildCustomPalette(@NonNull CustomPaletteSpec spec) {
        float[] accentHsv = new float[3];
        Color.colorToHSV(spec.accent, accentHsv);
        int accentLight = spec.isLight
                ? Color.HSVToColor(new float[]{ accentHsv[0], Math.max(0.55f, accentHsv[1]), Math.min(0.86f, Math.max(0.65f, accentHsv[2])) })
                : Color.HSVToColor(new float[]{ accentHsv[0], Math.max(0.42f, accentHsv[1] * 0.78f), 0.98f });

        int secondary = blendColors(spec.background, spec.accent, spec.isLight ? 0.18f : 0.28f);
        int surfaceVariant = blendColors(spec.surface, spec.accent, spec.isLight ? 0.10f : 0.14f);
        int muted = blendColors(spec.surface, spec.background, 0.5f);

        return new AppThemePalette(
                CUSTOM_THEME_KEY,
                "Custom",
                "Custom",
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
                spec.ringColor | 0xFF000000
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

    public int getFrostedCardTint(int defaultDarkTint) {
        int alpha = (defaultDarkTint >>> 24) & 0xFF;
        if (isLight) {
            int lightAlpha = Math.max(0xB4, Math.min(0xE8, alpha + 0x68));
            return withAlphaInt(surface, lightAlpha);
        }
        return withAlphaInt(surface, alpha);
    }

    public int getDialogSurfaceColor(boolean blurred) {
        if (isLight) {
            return withAlphaInt(popupTint, blurred ? 0xCC : 0xF0);
        }
        return withAlphaInt(popupTint, blurred ? 0x9E : 0xE6);
    }

    public int getDialogOverlayTint() {
        return withAlphaInt(popupTint, isLight ? 0xA8 : 0x78);
    }

    public int getQuickMenuCardTint(boolean withBlur) {
        if (isLight) {
            return withAlphaInt(popupTint, withBlur ? 0xC4 : 0xEB);
        }
        return withAlphaInt(popupTint, withBlur ? 0xA4 : 0xDE);
    }

    /**
     * Compact signature covering every visible color channel so cached drawables/shaders
     * invalidate immediately when custom palette values change.
     */
    @NonNull
    public String getSignature() {
        return key + ":" + isLight + ":" + background + ":" + surface + ":" + popupTint
                + ":" + accent + ":" + highlight + ":" + primaryText + ":" + secondaryText
                + ":" + cardOutline + ":" + particleColor + ":" + ringColor;
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
}
