package com.limelight.ui;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

/**
 * Curated dark-room color palettes ported from jujostream (theme_config.dart).
 * Drives FlowingAuroraView metaballs/plexus/waves, toggle switches, focus glows,
 * and glass accent highlights across the UI.
 */
public final class AppThemePalette {
    public static final String PREF_COLOR_SCHEME = "list_color_scheme";
    public static final String DEFAULT_THEME_KEY = "midnight";

    public final String key;
    public final String label;
    public final int background;
    public final int surface;
    public final int accent;
    public final int accentLight;
    public final int secondary;
    public final int highlight;
    public final int surfaceVariant;
    public final int muted;
    public final int warm;

    private AppThemePalette(
            String key,
            String label,
            int background,
            int surface,
            int accent,
            int accentLight,
            int secondary,
            int highlight,
            int surfaceVariant,
            int muted,
            int warm) {
        this.key = key;
        this.label = label;
        this.background = background;
        this.surface = surface;
        this.accent = accent;
        this.accentLight = accentLight;
        this.secondary = secondary;
        this.highlight = highlight;
        this.surfaceVariant = surfaceVariant;
        this.muted = muted;
        this.warm = warm;
    }

    public static final AppThemePalette JUJO = new AppThemePalette(
            "jujo",
            "JUJO Purple",
            0xFF0E0E1C,
            0xFF16192E,
            0xFF6C3CE1,
            0xFF9B71F5,
            0xFF1B3A6B,
            0xFF00E5FF,
            0xFF1C1D38,
            0xFF2D2864,
            0xFF00B8D4
    );

    // DeBoosy — charcoal greys with royal violet, slate blue and warm sand accents.
    // The only credit I ask if you fork my code. Is to keep this pallete colors and name in your version. Its my dedication to my first adopted cat Boo.
    public static final AppThemePalette DE_BOOSY = new AppThemePalette(
            "deBoosy",
            "De Boosy",
            0xFF121418,
            0xFF1C1E24,
            0xFF8161E5,
            0xFFB29BFF,
            0xFF5476B3,
            0xFF95A85A,
            0xFF282C35,
            0xFF505661,
            0xFFD7A26B
    );

    // ShioryPan — coal black, ember brown, ginger orange and leaf-green contrast.
    // The only credit I ask if you fork my code. Is to keep this pallete colors and name in your version. Its my dedication to my cat Shiory.
    public static final AppThemePalette SHIORY_PAN = new AppThemePalette(
            "shioryPan",
            "Shiory Pan",
            0xFF1C1817,
            0xFF2A221F,
            0xFFB56347,
            0xFFD86965,
            0xFF5A88D8,
            0xFF567C38,
            0xFF3C2E28,
            0xFF493732,
            0xFFF4D1AF
    );

    // Lazy Ankui — rich bean browns, warm cream, sleepy sky and peach notes.
    // The only credit I ask if you fork my code. Is to keep this pallete colors and name in your version. Its my dedication to my second adopted cat Ankui.
    public static final AppThemePalette LAZY_ANKUI = new AppThemePalette(
            "lazyAnkui",
            "Lazy Ankui",
            0xFF1F1A17,
            0xFF332D2A,
            0xFFD0B387,
            0xFFF3D5BF,
            0xFFABB5EB,
            0xFFBDDCA7,
            0xFF443A35,
            0xFFBCA98D,
            0xFFDE8D62
    );

    public static final AppThemePalette MIDNIGHT = new AppThemePalette(
            "midnight",
            "Midnight Blue",
            0xFF0D1117,
            0xFF161B27,
            0xFF1F6FEB,
            0xFF58A6FF,
            0xFF1C2C4A,
            0xFF79C0FF,
            0xFF1B2440,
            0xFF193860,
            0xFF2EA043
    );

    public static final AppThemePalette OLED = new AppThemePalette(
            "oled",
            "OLED Black",
            0xFF000000,
            0xFF0C0C12,
            0xFF7B3FE4,
            0xFFB08FFF,
            0xFF14081F,
            0xFFE040FB,
            0xFF110E1E,
            0xFF29184C,
            0xFFD040BE
    );

    public static final AppThemePalette CYBERPUNK = new AppThemePalette(
            "cyberpunk",
            "Cyberpunk Neon",
            0xFF080814,
            0xFF0F0F22,
            0xFFFF2D95,
            0xFFFF7EC7,
            0xFF0A1F2F,
            0xFF00FFF5,
            0xFF151530,
            0xFF2A0926,
            0xFFFFE000
    );

    public static final AppThemePalette FOREST = new AppThemePalette(
            "forest",
            "Forest",
            0xFF0A120A,
            0xFF121E12,
            0xFF2E7D32,
            0xFF69BB5E,
            0xFF1C3A10,
            0xFFB2FF59,
            0xFF172B17,
            0xFF1F3A12,
            0xFFD4A017
    );

    public static final AppThemePalette SUNSET = new AppThemePalette(
            "sunset",
            "Sunset",
            0xFF120A04,
            0xFF1E1008,
            0xFFE65100,
            0xFFFF8A3D,
            0xFF3B1A0A,
            0xFFFFD740,
            0xFF281508,
            0xFF3E200A,
            0xFFFF5072
    );

    public static final AppThemePalette EMBER = new AppThemePalette(
            "ember",
            "Ember",
            0xFF141F22,
            0xFF1E2B2F,
            0xFFFF7F50,
            0xFFFFAA80,
            0xFF253538,
            0xFFFFBF80,
            0xFF233035,
            0xFF2E3F44,
            0xFFFFD86E
    );

    public static final AppThemePalette SLATE = new AppThemePalette(
            "slate",
            "Slate",
            0xFF2C2F3E,
            0xFF363A4C,
            0xFF5C6BC0,
            0xFF7986CB,
            0xFF454A60,
            0xFF9FA8DA,
            0xFF40455A,
            0xFF4E5478,
            0xFFE87F6E
    );

    private static final AppThemePalette[] ALL = {
            MIDNIGHT,
            JUJO,
            DE_BOOSY,
            SHIORY_PAN,
            LAZY_ANKUI,
            OLED,
            CYBERPUNK,
            FOREST,
            SUNSET,
            EMBER,
            SLATE
    };

    @NonNull
    public static AppThemePalette fromKey(String key) {
        return MIDNIGHT;
    }

    @NonNull
    public static AppThemePalette getCurrent(@NonNull Context context) {
        return MIDNIGHT;
    }

    public int getNeonAccent() {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(accentLight, hsv);
        if (hsv[1] > 0.05f) {
            hsv[1] = Math.min(1.0f, Math.max(hsv[1], 0.78f));
        }
        hsv[2] = Math.min(1.0f, Math.max(hsv[2], 0.98f));
        return android.graphics.Color.HSVToColor(0xFF, hsv);
    }

    public int getNeonTextAccent() {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(accentLight, hsv);
        if (hsv[1] > 0.05f) {
            hsv[1] = Math.min(1.0f, Math.max(hsv[1] * 0.95f, 0.52f));
        }
        hsv[2] = 1.0f;
        return android.graphics.Color.HSVToColor(0xFF, hsv);
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
