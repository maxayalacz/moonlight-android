package com.limelight.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.UiHelper;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Full-screen split-stage Custom Theme Studio with:
 * - Full-screen PortalShaderView backdrop (1:1 particle scale with the rest of the app)
 * - Live frosted glass cards, pop-up preview, and frosted bottom editor sheet
 * - 5-tab editor sheet:
 *   1. Colors (10 channels + Start from Preset + Dark/Light + Auto-Harmonize)
 *   2. Glass (Live Blur Amount, 0-100% Frosted Tint Amount, Card Outline Thickness, BG Quality)
 *   3. Portal FX (Particle Brightness, Particle Size, Particle Amount, Ring Glow, Scale, Sonar Speed)
 *   4. Animation (Play Launch & Play Disconnect + 9 exact per-phase transition speed sliders in ms)
 *   5. Themes (Save / Update / Load / Delete + Clipboard JSON Import & Export)
 * - Tappable slider value pills for entering custom numeric values beyond slider limits.
 */
public class ThemeStudioActivity extends AppCompatActivity {

    private static final int TAB_COLORS = 0;
    private static final int TAB_GLASS = 1;
    private static final int TAB_PORTAL = 2;
    private static final int TAB_ANIMATION = 3;
    private static final int TAB_SAVED = 4;

    private static final String[] TAB_LABELS = { "Colors", "Glass", "Portal FX", "Animation", "Themes" };

    private static final int CH_ACCENT = 0;
    private static final int CH_BG = 1;
    private static final int CH_SURFACE = 2;
    private static final int CH_POPUP = 3;
    private static final int CH_TEXT_PRIMARY = 4;
    private static final int CH_TEXT_SECONDARY = 5;
    private static final int CH_OUTLINE = 6;
    private static final int CH_HIGHLIGHT = 7;
    private static final int CH_PARTICLE_NEAR = 8;
    private static final int CH_PARTICLE_MID = 9;
    private static final int CH_PARTICLE_FAR = 10;
    private static final int CH_PULSE = 11;
    private static final int CH_RING = 12;
    private static final int CH_CHARGE = 13;

    private static final String[] CHANNEL_LABELS = {
            "Accent", "Background", "Surface", "Popup Tint",
            "Primary Text", "Secondary Text", "Card Outline", "Highlight",
            "Particles L1 (Near)", "Particles L2 (Mid)", "Particles L3 (Far)",
            "Energy Pulses", "Iris Rim", "Charge Ring"
    };

    private static final int[] QUICK_SWATCHES = {
            0xFF3B82F6, 0xFF7C3AED, 0xFFFF2D95, 0xFFEF4444,
            0xFFF97316, 0xFFFACC15, 0xFF10B981, 0xFF06B6D4,
            0xFFFFFFFF, 0xFFCBD5E1, 0xFF334155, 0xFF0D1117
    };

    private static final String[] BG_QUALITY_LABELS = { "480p", "720p", "1080p", "Native" };
    private static final String[] BG_QUALITY_VALUES = { "480", "720", "1080", "0" };
    private static final String[] UI_BATTERY_SAVER_LABELS = { "Off", "Auto", "On" };
    private static final String[] UI_BATTERY_SAVER_VALUES = { "off", "auto", "on" };

    private AppThemePalette.ThemeBundle bundle;
    private String activeSavedId = null;
    private int activeTab = TAB_COLORS;
    private int activeChannel = CH_ACCENT;
    private boolean updatingControls = false;
    private boolean playingIris = false;
    private boolean playingDisconnectMode = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ValueAnimator irisAnimator;

    // Preview stage views
    private FrameLayout outerRoot;
    private FrameLayout previewStage;
    private PortalShaderView previewPortal;
    private LinearLayout previewTopBar;
    private FlowingAuroraView.FrostedCardDrawable previewTopBarDrawable;
    private ImageButton btnBack;
    private TextView studioTitle;
    private TextView btnPlayIris;
    private TextView btnApply;
    private LinearLayout previewCardsRow;
    private LinearLayout previewGlassCard;
    private FlowingAuroraView.FrostedCardDrawable previewCardDrawable;
    private View previewHighlightDot;
    private TextView previewCardTitle;
    private TextView previewCardSub;
    private TextView previewCardPill;
    private LinearLayout previewPopupCard;
    private TextView previewPopupTitle;
    private TextView previewPopupSub;
    private TextView previewBlurStatusBadge;

    // Editor sheet views
    private LinearLayout editorSheet;
    private FlowingAuroraView.FrostedCardDrawable editorSheetDrawable;
    private TextView[] tabButtons;
    private View[] tabPages;

    // Tab 1: Colors
    private TextView btnDarkBase;
    private TextView btnLightBase;
    private TextView btnHarmonize;
    private LinearLayout[] chTabs;
    private View[] chDots;
    private TextView[] chTexts;
    private SeekBar[] rgbBars;
    private TextView[] rgbVals;
    private EditText hexInput;
    private TextView activeChannelHint;
    private TextView presetHeading;
    private TextView hexPrefix;

    // Tab 2: Glass & Blur
    private SliderRow rowBlurAmount;
    private SliderRow rowBlurSaturation;
    private SliderRow rowTintAmount;
    private TextView tintPerfHint;
    private SliderRow rowCardOutline;
    private TextView bgQualityHeading;
    private TextView[] bgQualityPills;
    private TextView uiBatterySaverHeading;
    private TextView[] uiBatterySaverPills;

    // Tab 3: Portal FX (Visual shader controls + Energy Pulses)
    private SliderRow rowParticleBright;
    private SliderRow rowParticleSize;
    private SliderRow rowParticleDensity;
    private SliderRow rowRingGlow;
    private SliderRow rowPortalScale;
    private SliderRow rowSonarSpeed;
    private TextView energyPulsesHeading;
    private SliderRow rowPulseAmount;
    private SliderRow rowPulseBrightness;
    private SliderRow rowPulseLength;
    private SliderRow rowPulseSpeed;
    private SliderRow rowWaveAmp;
    private TextView btnResetPortal;

    // Tab 4: Animation (Per-phase transition speeds & preview playback)
    private TextView btnAnimPlayLaunch;
    private TextView btnAnimPlayDisconnect;
    private TextView animLaunchHeading;
    private TextView animDisconnectHeading;
    private SliderRow rowLaunchCloseMs;
    private TextView btnDecouplePulseCoil;
    private LinearLayout pulseCoilContainer;
    private SliderRow rowPulseCoilMs;
    private SliderRow rowSonarHoldMs;
    private SliderRow rowReadyMs;
    private SliderRow rowChargeMs;
    private SliderRow rowFadeMs;
    private SliderRow rowExpandMs;
    private SliderRow rowDisconnectCloseMs;
    private SliderRow rowDisconnectFadeMs;
    private SliderRow rowDisconnectOpenMs;
    private TextView btnResetAnimation;

    // Tab 5: My Themes
    private TextView btnSaveNew;
    private TextView btnExportClipboard;
    private TextView btnImportClipboard;
    private LinearLayout savedThemesList;

    private static final class SliderRow {
        final TextView titleTv;
        final TextView valueTv;
        final SeekBar seekBar;
        final int min;
        final int max;
        final int step;
        final int customMin;
        final int customMax;
        final String suffix;
        int currentValue;

        SliderRow(TextView titleTv, TextView valueTv, SeekBar seekBar,
                  int min, int max, int step, int customMin, int customMax, String suffix) {
            this.titleTv = titleTv;
            this.valueTv = valueTv;
            this.seekBar = seekBar;
            this.min = min;
            this.max = max;
            this.step = step;
            this.customMin = customMin;
            this.customMax = customMax;
            this.suffix = suffix;
            this.currentValue = min;
        }

        void setValue(int val) {
            this.currentValue = val;
            int clampedForBar = Math.max(min, Math.min(max, val));
            int p = Math.max(0, Math.min(seekBar.getMax(), (clampedForBar - min) / step));
            if (seekBar.getProgress() != p) {
                seekBar.setProgress(p);
            }
            valueTv.setText(val + suffix);
        }

        void applyTheme(AppThemePalette p, int strokePx, int padH, int padV, int radiusPx) {
            titleTv.setTextColor(p.getPrimaryTextColor());
            int neon = p.getNeonAccent();
            valueTv.setTextColor(neon);
            valueTv.setPadding(padH, padV, padH, padV);
            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setShape(GradientDrawable.RECTANGLE);
            pillBg.setCornerRadius(radiusPx);
            pillBg.setColor(AppThemePalette.withAlpha(p.accent, p.isLight ? 0.14f : 0.22f));
            pillBg.setStroke(Math.max(1, strokePx), AppThemePalette.withAlpha(neon, 0.55f));
            valueTv.setBackground(pillBg);
            seekBar.setProgressTintList(ColorStateList.valueOf(neon));
            seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(p.isLight ? 0x280F172A : 0x36FFFFFF));
            seekBar.setThumbTintList(ColorStateList.valueOf(neon));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiHelper.setLocale(this);
        UiHelper.disableAllActivityTransitions(this);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overridePendingTransition(0, 0);
        }

        bundle = AppThemePalette.loadActiveBundle(this);
        String currentKey = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .getString(AppThemePalette.PREF_COLOR_SCHEME, AppThemePalette.DEFAULT_THEME_KEY);
        if (currentKey != null && currentKey.startsWith(AppThemePalette.SAVED_THEME_PREFIX)) {
            activeSavedId = currentKey.substring(AppThemePalette.SAVED_THEME_PREFIX.length());
        }

        buildUi();
        UiHelper.applyMenuImmersiveMode(this);
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiHelper.applyMenuImmersiveMode(this);
        if (previewPortal != null) {
            previewPortal.applyPreviewOverride(bundle);
            previewPortal.startRendering();
        }
    }

    @Override
    protected void onPause() {
        stopIrisDemo();
        if (previewPortal != null) {
            previewPortal.stopRenderingAsync();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopIrisDemo();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finishClean();
    }

    private void finishClean() {
        stopIrisDemo();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            UiHelper.disableActivityTransition(this, true);
        }
        finish();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overridePendingTransition(0, 0);
        }
    }

    private void buildUi() {
        outerRoot = new FrameLayout(this);
        outerRoot.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 0. FULL-SCREEN LIVE PORTAL SHADER BACKDROP (1:1 particle scale with outside Theme Studio!)
        previewPortal = new PortalShaderView(this);
        previewPortal.configureStarfieldBackgroundMode();
        previewPortal.applyPreviewOverride(bundle);
        outerRoot.addView(previewPortal, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        previewPortal.startRendering();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        outerRoot.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 1. TOP LIVE PREVIEW STAGE (~39% height)
        previewStage = new FrameLayout(this);
        LinearLayout.LayoutParams stageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.39f);
        root.addView(previewStage, stageLp);

        // Top floating bar inside preview stage (real FrostedCardDrawable!)
        previewTopBar = new LinearLayout(this);
        previewTopBar.setOrientation(LinearLayout.HORIZONTAL);
        previewTopBar.setGravity(Gravity.CENTER_VERTICAL);
        previewTopBar.setPadding(dp(10f), dp(8f), dp(10f), dp(8f));
        previewTopBarDrawable = new FlowingAuroraView.FrostedCardDrawable(
                previewTopBar, null, previewPortal,
                FlowingAuroraView.CornerMode.ALL, dp(20f), 0x52121826, dp(1f));
        previewTopBar.setBackground(previewTopBarDrawable);
        FrameLayout.LayoutParams topBarLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topBarLp.gravity = Gravity.TOP;
        topBarLp.topMargin = dp(24f);
        topBarLp.leftMargin = dp(12f);
        topBarLp.rightMargin = dp(12f);

        btnBack = new ImageButton(this);
        btnBack.setImageResource(R.drawable.ic_arrow_back);
        btnBack.setBackground(null);
        btnBack.setPadding(dp(8f), dp(8f), dp(8f), dp(8f));
        btnBack.setOnClickListener(v -> onBackPressed());
        previewTopBar.addView(btnBack, new LinearLayout.LayoutParams(dp(40f), dp(40f)));

        studioTitle = new TextView(this);
        studioTitle.setText(R.string.title_theme_studio);
        studioTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        studioTitle.setTypeface(Typeface.DEFAULT_BOLD);
        studioTitle.setSingleLine(true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMarginStart(dp(6f));
        previewTopBar.addView(studioTitle, titleLp);

        btnPlayIris = createPillButton("Play Iris");
        btnPlayIris.setOnClickListener(v -> toggleIrisDemo(false));
        LinearLayout.LayoutParams irisLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        irisLp.setMarginEnd(dp(8f));
        previewTopBar.addView(btnPlayIris, irisLp);

        btnApply = createPillButton("Apply");
        btnApply.setOnClickListener(v -> {
            String targetKey = (activeSavedId != null && AppThemePalette.findSavedThemeById(this, activeSavedId) != null)
                    ? (AppThemePalette.SAVED_THEME_PREFIX + activeSavedId)
                    : AppThemePalette.CUSTOM_THEME_KEY;
            if (targetKey.startsWith(AppThemePalette.SAVED_THEME_PREFIX)) {
                AppThemePalette.saveOrUpdateTheme(this, bundle);
            }
            AppThemePalette.applyThemeBundle(this, bundle, targetKey);
            Toast.makeText(this, R.string.theme_studio_applied, Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finishClean();
        });
        previewTopBar.addView(btnApply);
        previewStage.addView(previewTopBar, topBarLp);

        // Live preview cards centered in lower half of preview stage
        previewCardsRow = new LinearLayout(this);
        previewCardsRow.setOrientation(LinearLayout.HORIZONTAL);
        previewCardsRow.setGravity(Gravity.CENTER);
        previewCardsRow.setPadding(dp(16f), 0, dp(16f), dp(14f));
        FrameLayout.LayoutParams cardsRowLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardsRowLp.gravity = Gravity.BOTTOM;

        // Frosted surface card preview (real FrostedCardDrawable sampling live full-screen previewPortal!)
        previewGlassCard = new LinearLayout(this);
        previewGlassCard.setOrientation(LinearLayout.VERTICAL);
        previewGlassCard.setPadding(dp(14f), dp(11f), dp(14f), dp(11f));
        previewCardDrawable = new FlowingAuroraView.FrostedCardDrawable(
                previewGlassCard, null, previewPortal,
                FlowingAuroraView.CornerMode.ALL, dp(20f), 0x52121826, dp(1f));
        previewGlassCard.setBackground(previewCardDrawable);

        LinearLayout cTitleRow = new LinearLayout(this);
        cTitleRow.setOrientation(LinearLayout.HORIZONTAL);
        cTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        previewHighlightDot = new View(this);
        LinearLayout.LayoutParams hDotLp = new LinearLayout.LayoutParams(dp(9f), dp(9f));
        hDotLp.setMarginEnd(dp(7f));
        cTitleRow.addView(previewHighlightDot, hDotLp);

        previewCardTitle = new TextView(this);
        previewCardTitle.setText("Frosted Card");
        previewCardTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        previewCardTitle.setTypeface(Typeface.DEFAULT_BOLD);
        cTitleRow.addView(previewCardTitle);
        previewGlassCard.addView(cTitleRow);

        previewCardSub = new TextView(this);
        previewCardSub.setText("Live blur, tint & outline");
        previewCardSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        previewGlassCard.addView(previewCardSub);

        LinearLayout pillBadgeRow = new LinearLayout(this);
        pillBadgeRow.setOrientation(LinearLayout.HORIZONTAL);
        pillBadgeRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pbLp.topMargin = dp(7f);

        previewCardPill = new TextView(this);
        previewCardPill.setText("Play");
        previewCardPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        previewCardPill.setTypeface(Typeface.DEFAULT_BOLD);
        previewCardPill.setPadding(dp(12f), dp(3f), dp(12f), dp(3f));
        pillBadgeRow.addView(previewCardPill);

        previewBlurStatusBadge = new TextView(this);
        previewBlurStatusBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        previewBlurStatusBadge.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams bsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bsLp.setMarginStart(dp(8f));
        pillBadgeRow.addView(previewBlurStatusBadge, bsLp);
        previewGlassCard.addView(pillBadgeRow, pbLp);

        LinearLayout.LayoutParams gcLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.35f);
        gcLp.setMarginEnd(dp(10f));
        previewCardsRow.addView(previewGlassCard, gcLp);

        // Pop-up menu preview card
        previewPopupCard = new LinearLayout(this);
        previewPopupCard.setOrientation(LinearLayout.VERTICAL);
        previewPopupCard.setPadding(dp(12f), dp(11f), dp(12f), dp(11f));

        previewPopupTitle = new TextView(this);
        previewPopupTitle.setText("Pop-Up Menu");
        previewPopupTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        previewPopupTitle.setTypeface(Typeface.DEFAULT_BOLD);
        previewPopupCard.addView(previewPopupTitle);

        previewPopupSub = new TextView(this);
        previewPopupSub.setText("Popup Tint & Text");
        previewPopupSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        LinearLayout.LayoutParams psLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        psLp.topMargin = dp(3f);
        previewPopupCard.addView(previewPopupSub, psLp);

        previewCardsRow.addView(previewPopupCard, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        previewStage.addView(previewCardsRow, cardsRowLp);

        // 2. BOTTOM 5-TAB FROSTED EDITOR SHEET (~61% height, using Surface color + Frosted Card Tint)
        editorSheet = new LinearLayout(this);
        editorSheet.setOrientation(LinearLayout.VERTICAL);
        editorSheet.setPadding(dp(14f), dp(12f), dp(14f), dp(12f));
        editorSheetDrawable = new FlowingAuroraView.FrostedCardDrawable(
                editorSheet, null, previewPortal,
                FlowingAuroraView.CornerMode.TOP, dp(24f), 0x52121826, dp(1f));
        editorSheet.setBackground(editorSheetDrawable);
        root.addView(editorSheet, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.61f));

        // 5-Tab strip
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabButtons = new TextView[TAB_LABELS.length];
        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int idx = i;
            TextView tb = createPillButton(TAB_LABELS[i]);
            tb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            tb.setSingleLine(true);
            tb.setPadding(dp(4f), dp(8f), dp(4f), dp(8f));
            tb.setOnClickListener(v -> {
                activeTab = idx;
                updateTabVisibility();
                refreshAll();
            });
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) {
                tLp.setMarginStart(dp(5f));
            }
            tabBar.addView(tb, tLp);
            tabButtons[i] = tb;
        }
        editorSheet.addView(tabBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Tab content frame
        FrameLayout tabContent = new FrameLayout(this);
        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        tcLp.topMargin = dp(10f);
        editorSheet.addView(tabContent, tcLp);

        tabPages = new View[TAB_LABELS.length];
        tabPages[TAB_COLORS] = buildColorsTab();
        tabPages[TAB_GLASS] = buildGlassTab();
        tabPages[TAB_PORTAL] = buildPortalTab();
        tabPages[TAB_ANIMATION] = buildAnimationTab();
        tabPages[TAB_SAVED] = buildSavedThemesTab();
        for (View page : tabPages) {
            tabContent.addView(page, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        updateTabVisibility();

        setContentView(outerRoot);
    }

    private View buildColorsTab() {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(2f), 0, dp(16f));

        // Start from Preset strip
        presetHeading = new TextView(this);
        presetHeading.setText(R.string.custom_palette_start_preset);
        presetHeading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        presetHeading.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(presetHeading);

        HorizontalScrollView presetScroll = new HorizontalScrollView(this);
        presetScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        AppThemePalette[] presets = AppThemePalette.getPresets();
        for (int i = 0; i < presets.length; i++) {
            final AppThemePalette preset = presets[i];
            LinearLayout pill = new LinearLayout(this);
            pill.setOrientation(LinearLayout.HORIZONTAL);
            pill.setGravity(Gravity.CENTER_VERTICAL);
            pill.setPadding(dp(10f), dp(6f), dp(10f), dp(6f));
            pill.setClickable(true);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(999f));
            bg.setColor(AppThemePalette.withAlpha(preset.surface, 0.85f));
            bg.setStroke(dp(1f), AppThemePalette.withAlpha(preset.accent, 0.7f));
            pill.setBackground(bg);

            View dot = new View(this);
            GradientDrawable dd = new GradientDrawable();
            dd.setShape(GradientDrawable.OVAL);
            dd.setColor(preset.accent);
            dot.setBackground(dd);
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(dp(11f), dp(11f));
            dLp.setMarginEnd(dp(6f));
            pill.addView(dot, dLp);

            TextView tv = new TextView(this);
            tv.setText(preset.shortLabel);
            tv.setTextColor(preset.getPrimaryTextColor());
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            pill.addView(tv);

            pill.setOnClickListener(v -> {
                copySpec(bundle.colors, AppThemePalette.specFromPreset(preset));
                bundle.frostedTintAmountPercent = preset.isLight ? 76 : PreferenceConfiguration.DEFAULT_FROSTED_TINT_AMOUNT;
                activeSavedId = null;
                refreshAll();
            });
            LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) pLp.setMarginStart(dp(6f));
            presetRow.addView(pill, pLp);
        }
        presetScroll.addView(presetRow);
        LinearLayout.LayoutParams psLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        psLp.topMargin = dp(6f);
        col.addView(presetScroll, psLp);

        // Dark / Light / Auto-Harmonize
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams mrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mrLp.topMargin = dp(10f);
        btnDarkBase = createPillButton("Dark Base");
        btnLightBase = createPillButton("Light Base");
        btnHarmonize = createPillButton("Auto-Harmonize");
        btnDarkBase.setOnClickListener(v -> {
            if (bundle.colors.isLight) {
                copySpec(bundle.colors, AppThemePalette.harmonizeFromAccent(bundle.colors.accent, false));
                bundle.frostedTintAmountPercent = PreferenceConfiguration.DEFAULT_FROSTED_TINT_AMOUNT;
                refreshAll();
            }
        });
        btnLightBase.setOnClickListener(v -> {
            if (!bundle.colors.isLight) {
                copySpec(bundle.colors, AppThemePalette.harmonizeFromAccent(bundle.colors.accent, true));
                bundle.frostedTintAmountPercent = 76;
                refreshAll();
            }
        });
        btnHarmonize.setOnClickListener(v -> {
            copySpec(bundle.colors, AppThemePalette.harmonizeFromAccent(bundle.colors.accent, bundle.colors.isLight));
            refreshAll();
        });
        LinearLayout.LayoutParams m1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        m1.setMarginEnd(dp(6f));
        modeRow.addView(btnDarkBase, m1);
        LinearLayout.LayoutParams m2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        m2.setMarginEnd(dp(6f));
        modeRow.addView(btnLightBase, m2);
        modeRow.addView(btnHarmonize);
        col.addView(modeRow, mrLp);

        // 10-Channel tabs
        HorizontalScrollView chScroll = new HorizontalScrollView(this);
        chScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chRow = new LinearLayout(this);
        chRow.setOrientation(LinearLayout.HORIZONTAL);
        chTabs = new LinearLayout[CHANNEL_LABELS.length];
        chDots = new View[CHANNEL_LABELS.length];
        chTexts = new TextView[CHANNEL_LABELS.length];
        for (int i = 0; i < CHANNEL_LABELS.length; i++) {
            final int idx = i;
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.HORIZONTAL);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setPadding(dp(10f), dp(7f), dp(10f), dp(7f));
            tab.setClickable(true);
            View dot = new View(this);
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(dp(12f), dp(12f));
            dLp.setMarginEnd(dp(6f));
            tab.addView(dot, dLp);
            TextView tv = new TextView(this);
            tv.setText(CHANNEL_LABELS[i]);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tab.addView(tv);
            tab.setOnClickListener(v -> {
                activeChannel = idx;
                refreshAll();
            });
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) tLp.setMarginStart(dp(6f));
            chRow.addView(tab, tLp);
            chTabs[i] = tab;
            chDots[i] = dot;
            chTexts[i] = tv;
        }
        chScroll.addView(chRow);
        LinearLayout.LayoutParams csLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        csLp.topMargin = dp(10f);
        col.addView(chScroll, csLp);

        // Quick swatches
        HorizontalScrollView swScroll = new HorizontalScrollView(this);
        swScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout swRow = new LinearLayout(this);
        swRow.setOrientation(LinearLayout.HORIZONTAL);
        swRow.setPadding(dp(2f), dp(4f), dp(2f), dp(4f));
        for (int i = 0; i < QUICK_SWATCHES.length; i++) {
            final int swatchColor = QUICK_SWATCHES[i];
            View sw = new View(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(swatchColor);
            bg.setStroke(dp(1.5f), 0x77888888);
            sw.setBackground(bg);
            sw.setClickable(true);
            sw.setOnClickListener(v -> {
                setChannelColor(bundle.colors, activeChannel, swatchColor);
                refreshAll();
            });
            LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(dp(28f), dp(28f));
            if (i > 0) sLp.setMarginStart(dp(8f));
            swRow.addView(sw, sLp);
        }
        swScroll.addView(swRow);
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        swLp.topMargin = dp(10f);
        col.addView(swScroll, swLp);

        // RGB sliders with tappable value pills
        rgbBars = new SeekBar[3];
        rgbVals = new TextView[3];
        final String[] names = { "R", "G", "B" };
        int[] tints = { 0xFFFF5252, 0xFF10B981, 0xFF3B82F6 };
        SeekBar.OnSeekBarChangeListener rgbListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (updatingControls || !fromUser) return;
                int c = Color.rgb(rgbBars[0].getProgress(), rgbBars[1].getProgress(), rgbBars[2].getProgress());
                setChannelColor(bundle.colors, activeChannel, c);
                refreshAll();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        for (int c = 0; c < 3; c++) {
            final int chIdx = c;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rLp.topMargin = dp(6f);

            TextView lbl = new TextView(this);
            lbl.setText(names[c]);
            lbl.setTextColor(tints[c]);
            lbl.setTypeface(Typeface.DEFAULT_BOLD);
            lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            row.addView(lbl, new LinearLayout.LayoutParams(dp(22f), ViewGroup.LayoutParams.WRAP_CONTENT));

            SeekBar sb = new SeekBar(this);
            sb.setMax(255);
            sb.setProgressTintList(ColorStateList.valueOf(tints[c]));
            sb.setOnSeekBarChangeListener(rgbListener);
            row.addView(sb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView val = new TextView(this);
            val.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            val.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            val.setGravity(Gravity.CENTER);
            val.setClickable(true);
            val.setFocusable(true);
            val.setOnClickListener(v -> {
                int current = rgbBars[chIdx].getProgress();
                promptCustomSliderValue(CHANNEL_LABELS[activeChannel] + " — " + names[chIdx],
                        current, 0, 255, "", newVal -> {
                            rgbBars[chIdx].setProgress(newVal);
                            int color = Color.rgb(rgbBars[0].getProgress(), rgbBars[1].getProgress(), rgbBars[2].getProgress());
                            setChannelColor(bundle.colors, activeChannel, color);
                            refreshAll();
                        });
            });
            LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            valLp.setMarginStart(dp(6f));
            row.addView(val, valLp);

            rgbBars[c] = sb;
            rgbVals[c] = val;
            col.addView(row, rLp);
        }

        // Hex row
        LinearLayout hexRow = new LinearLayout(this);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hrLp.topMargin = dp(10f);

        hexPrefix = new TextView(this);
        hexPrefix.setText("HEX #");
        hexPrefix.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        hexPrefix.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        hexRow.addView(hexPrefix);

        hexInput = new EditText(this);
        hexInput.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        hexInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        hexInput.setSingleLine(true);
        hexInput.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(6) });
        hexInput.setPadding(dp(10f), dp(6f), dp(10f), dp(6f));
        hexInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (updatingControls || s == null || s.length() != 6) return;
                try {
                    setChannelColor(bundle.colors, activeChannel, Color.parseColor("#" + s.toString()));
                    refreshAll();
                } catch (IllegalArgumentException ignored) {}
            }
        });
        LinearLayout.LayoutParams hiLp = new LinearLayout.LayoutParams(dp(108f), ViewGroup.LayoutParams.WRAP_CONTENT);
        hiLp.setMarginStart(dp(8f));
        hexRow.addView(hexInput, hiLp);

        activeChannelHint = new TextView(this);
        activeChannelHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        activeChannelHint.setGravity(Gravity.END);
        hexRow.addView(activeChannelHint, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(hexRow, hrLp);

        sv.addView(col);
        return sv;
    }

    private View buildGlassTab() {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(4f), 0, dp(20f));

        rowBlurAmount = addSliderRow(col, "Live Blur Amount", 0, 200, 5, 0, 2000, "%",
                v -> { bundle.blurAmountPercent = v; refreshAll(); });

        rowBlurSaturation = addSliderRow(col, "Glass Saturation", 50, 250, 5, 0, 500, "%",
                v -> { bundle.blurSaturationPercent = v; refreshAll(); });

        rowTintAmount = addSliderRow(col, "Frosted Card Tint Amount", 0, 100, 1, 0, 100, "%",
                v -> { bundle.frostedTintAmountPercent = v; refreshAll(); });

        tintPerfHint = new TextView(this);
        tintPerfHint.setText(R.string.theme_studio_tint_perf_hint);
        tintPerfHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(2f);
        hintLp.bottomMargin = dp(8f);
        col.addView(tintPerfHint, hintLp);

        rowCardOutline = addSliderRow(col, "Card Outline Thickness", 0, 250, 5, 0, 2000, "%",
                v -> { bundle.cardOutlineThicknessPercent = v; refreshAll(); });

        bgQualityHeading = new TextView(this);
        bgQualityHeading.setText(R.string.title_bg_quality);
        bgQualityHeading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        bgQualityHeading.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams bqhLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bqhLp.topMargin = dp(12f);
        col.addView(bgQualityHeading, bqhLp);

        LinearLayout qRow = new LinearLayout(this);
        qRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams qrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qrLp.topMargin = dp(8f);
        bgQualityPills = new TextView[BG_QUALITY_LABELS.length];
        for (int i = 0; i < BG_QUALITY_LABELS.length; i++) {
            final String val = BG_QUALITY_VALUES[i];
            TextView pill = createPillButton(BG_QUALITY_LABELS[i]);
            pill.setOnClickListener(v -> {
                bundle.bgQuality = val;
                refreshAll();
            });
            LinearLayout.LayoutParams qpLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) qpLp.setMarginStart(dp(6f));
            qRow.addView(pill, qpLp);
            bgQualityPills[i] = pill;
        }
        col.addView(qRow, qrLp);

        uiBatterySaverHeading = new TextView(this);
        uiBatterySaverHeading.setText(R.string.title_ui_battery_saver);
        uiBatterySaverHeading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        uiBatterySaverHeading.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams bshLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bshLp.topMargin = dp(12f);
        col.addView(uiBatterySaverHeading, bshLp);

        LinearLayout bsRow = new LinearLayout(this);
        bsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bsrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bsrLp.topMargin = dp(8f);
        uiBatterySaverPills = new TextView[UI_BATTERY_SAVER_LABELS.length];
        for (int i = 0; i < UI_BATTERY_SAVER_LABELS.length; i++) {
            final String val = UI_BATTERY_SAVER_VALUES[i];
            TextView pill = createPillButton(UI_BATTERY_SAVER_LABELS[i]);
            pill.setOnClickListener(v -> {
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                        .edit()
                        .putString(PreferenceConfiguration.UI_BATTERY_SAVER_PREF_STRING, val)
                        .apply();
                refreshAll();
            });
            LinearLayout.LayoutParams bpLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) bpLp.setMarginStart(dp(6f));
            bsRow.addView(pill, bpLp);
            uiBatterySaverPills[i] = pill;
        }
        col.addView(bsRow, bsrLp);

        sv.addView(col);
        return sv;
    }

    private View buildPortalTab() {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(4f), 0, dp(20f));

        rowParticleBright = addSliderRow(col, "Particle Brightness", 0, 400, 5, 0, 2000, "%",
                v -> { bundle.portalParticleBrightnessPercent = v; refreshAll(); });
        rowParticleSize = addSliderRow(col, "Particle Size", 25, 400, 5, 1, 2000, "%",
                v -> { bundle.portalParticleSizePercent = v; refreshAll(); });
        rowParticleDensity = addSliderRow(col, "Particle Amount", 25, 400, 5, 0, 2000, "%",
                v -> { bundle.portalParticleDensityPercent = v; refreshAll(); });
        rowRingGlow = addSliderRow(col, "Portal Ring Glow", 20, 250, 5, 0, 2000, "%",
                v -> { bundle.portalGlowIntensityPercent = v; refreshAll(); });
        rowPortalScale = addSliderRow(col, "Portal Window Scale", 60, 150, 5, 10, 2000, "%",
                v -> { bundle.portalWindowScalePercent = v; refreshAll(); });
        rowSonarSpeed = addSliderRow(col, "Sonar Pulse Speed", 20, 250, 5, 0, 2000, "%",
                v -> { bundle.portalSonarSpeedPercent = v; refreshAll(); });

        energyPulsesHeading = new TextView(this);
        energyPulsesHeading.setText("ENERGY PULSES");
        energyPulsesHeading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        energyPulsesHeading.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams epLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        epLp.topMargin = dp(14f);
        col.addView(energyPulsesHeading, epLp);

        rowPulseAmount = addSliderRow(col, "Amount", 0, 10, 1, 0, 10, "",
                v -> { bundle.portalPulseAmount = v; refreshAll(); });
        rowPulseBrightness = addSliderRow(col, "Brightness", 0, 200, 5, 0, 500, "%",
                v -> { bundle.portalPulseBrightnessPercent = v; refreshAll(); });
        rowPulseLength = addSliderRow(col, "Length", 25, 220, 5, 10, 500, "%",
                v -> { bundle.portalPulseLengthPercent = v; refreshAll(); });
        rowPulseSpeed = addSliderRow(col, "Speed", 20, 250, 5, 0, 1000, "%",
                v -> { bundle.portalPulseSpeedPercent = v; refreshAll(); });
        rowWaveAmp = addSliderRow(col, "Wave", 0, 200, 5, 0, 500, "%",
                v -> { bundle.portalWaveAmpPercent = v; refreshAll(); });

        btnResetPortal = createPillButton("Reset Portal FX Defaults");
        btnResetPortal.setOnClickListener(v -> {
            bundle.portalParticleBrightnessPercent = PreferenceConfiguration.DEFAULT_PORTAL_PARTICLE_BRIGHTNESS;
            bundle.portalParticleSizePercent = PreferenceConfiguration.DEFAULT_PORTAL_PARTICLE_SIZE;
            bundle.portalParticleDensityPercent = PreferenceConfiguration.DEFAULT_PORTAL_PARTICLE_DENSITY;
            bundle.portalGlowIntensityPercent = PreferenceConfiguration.DEFAULT_PORTAL_GLOW_INTENSITY;
            bundle.portalWindowScalePercent = PreferenceConfiguration.DEFAULT_PORTAL_WINDOW_SCALE;
            bundle.portalSonarSpeedPercent = PreferenceConfiguration.DEFAULT_PORTAL_SONAR_SPEED;
            bundle.portalPulseAmount = PreferenceConfiguration.DEFAULT_PORTAL_PULSE_AMOUNT;
            bundle.portalPulseBrightnessPercent = PreferenceConfiguration.DEFAULT_PORTAL_PULSE_BRIGHTNESS;
            bundle.portalPulseLengthPercent = PreferenceConfiguration.DEFAULT_PORTAL_PULSE_LENGTH;
            bundle.portalPulseSpeedPercent = PreferenceConfiguration.DEFAULT_PORTAL_PULSE_SPEED;
            bundle.portalWaveAmpPercent = PreferenceConfiguration.DEFAULT_PORTAL_WAVE_AMP;
            refreshAll();
        });
        LinearLayout.LayoutParams rstLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rstLp.topMargin = dp(12f);
        rstLp.gravity = Gravity.END;
        col.addView(btnResetPortal, rstLp);

        sv.addView(col);
        return sv;
    }

    private View buildAnimationTab() {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(4f), 0, dp(20f));

        // Top preview buttons: Play Launch | Play Disconnect
        LinearLayout playRow = new LinearLayout(this);
        playRow.setOrientation(LinearLayout.HORIZONTAL);
        btnAnimPlayLaunch = createPillButton("Play Launch");
        btnAnimPlayDisconnect = createPillButton("Play Disconnect");
        btnAnimPlayLaunch.setOnClickListener(v -> toggleIrisDemo(false));
        btnAnimPlayDisconnect.setOnClickListener(v -> toggleIrisDemo(true));
        LinearLayout.LayoutParams p1Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p1Lp.setMarginEnd(dp(6f));
        playRow.addView(btnAnimPlayLaunch, p1Lp);
        playRow.addView(btnAnimPlayDisconnect, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(playRow);

        // Section 1: Launch Sequence Timings (6 phases — Phase 3 runs Charge & Stream Fade simultaneously)
        animLaunchHeading = new TextView(this);
        animLaunchHeading.setText("LAUNCH SEQUENCE TIMINGS");
        animLaunchHeading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        animLaunchHeading.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lhLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lhLp.topMargin = dp(12f);
        col.addView(animLaunchHeading, lhLp);

        rowLaunchCloseMs = addSliderRow(col, "1. Launch Iris Close & Spiral", 150, 2000, 25, 0, 30000, " ms",
                v -> { bundle.portalLaunchCloseMs = v; refreshAll(); });

        btnDecouplePulseCoil = createPillButton("Decouple Spiral Morph: OFF");
        btnDecouplePulseCoil.setOnClickListener(v -> {
            bundle.portalDecouplePulseCoil = !bundle.portalDecouplePulseCoil;
            refreshAll();
        });
        LinearLayout.LayoutParams decLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        decLp.topMargin = dp(8f);
        col.addView(btnDecouplePulseCoil, decLp);

        pulseCoilContainer = new LinearLayout(this);
        pulseCoilContainer.setOrientation(LinearLayout.VERTICAL);
        rowPulseCoilMs = addSliderRow(pulseCoilContainer, "1b. Independent Spiral Coil", 300, 4000, 50, 0, 30000, " ms",
                v -> { bundle.portalPulseCoilMs = v; refreshAll(); });
        col.addView(pulseCoilContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rowSonarHoldMs = addSliderRow(col, "2. Sonar Pulse Hold", 0, 2000, 50, 0, 30000, " ms",
                v -> { bundle.portalSonarHoldMs = v; refreshAll(); });
        rowReadyMs = addSliderRow(col, "3. Sonar \u2192 Ready Glow (Linear)", 50, 1500, 25, 0, 30000, " ms",
                v -> { bundle.portalReadyMs = v; refreshAll(); });
        rowChargeMs = addSliderRow(col, "4a. Ready \u2192 Charge (Linear)", 50, 2000, 25, 0, 30000, " ms",
                v -> { bundle.portalChargeMs = v; refreshAll(); });
        rowFadeMs = addSliderRow(col, "4b. Stream Fade-In (Linear)", 50, 2000, 25, 0, 30000, " ms",
                v -> { bundle.portalPreviewFadeMs = v; refreshAll(); });
        rowExpandMs = addSliderRow(col, "5. Iris Wipe Expand Open", 150, 1800, 25, 0, 30000, " ms",
                v -> { bundle.portalExpandMs = v; refreshAll(); });

        // Section 2: Disconnect Sequence Timings (3 steps)
        animDisconnectHeading = new TextView(this);
        animDisconnectHeading.setText("DISCONNECT SEQUENCE TIMINGS");
        animDisconnectHeading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        animDisconnectHeading.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams dhLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dhLp.topMargin = dp(14f);
        col.addView(animDisconnectHeading, dhLp);

        rowDisconnectCloseMs = addSliderRow(col, "1. Disconnect Iris Close", 100, 1500, 25, 0, 30000, " ms",
                v -> {
                    bundle.portalDisconnectCloseMs = v;
                    bundle.portalDisconnectMs = bundle.portalDisconnectCloseMs
                            + bundle.portalDisconnectFadeMs + bundle.portalDisconnectOpenMs;
                    refreshAll();
                });
        rowDisconnectFadeMs = addSliderRow(col, "2. Stream \u2192 Starfield Fade (Linear)", 100, 1500, 25, 0, 30000, " ms",
                v -> {
                    bundle.portalDisconnectFadeMs = v;
                    bundle.portalDisconnectMs = bundle.portalDisconnectCloseMs
                            + bundle.portalDisconnectFadeMs + bundle.portalDisconnectOpenMs;
                    refreshAll();
                });
        rowDisconnectOpenMs = addSliderRow(col, "3. Iris Spiral Uncoil to Menu", 100, 2500, 25, 0, 30000, " ms",
                v -> {
                    bundle.portalDisconnectOpenMs = v;
                    bundle.portalDisconnectMs = bundle.portalDisconnectCloseMs
                            + bundle.portalDisconnectFadeMs + bundle.portalDisconnectOpenMs;
                    refreshAll();
                });

        btnResetAnimation = createPillButton("Reset Animation Timings");
        btnResetAnimation.setOnClickListener(v -> {
            bundle.portalLaunchCloseMs = PreferenceConfiguration.DEFAULT_PORTAL_LAUNCH_CLOSE_MS;
            bundle.portalPulseCoilMs = PreferenceConfiguration.DEFAULT_PORTAL_PULSE_COIL_MS;
            bundle.portalDecouplePulseCoil = PreferenceConfiguration.DEFAULT_PORTAL_DECOUPLE_PULSE_COIL;
            bundle.portalSonarHoldMs = PreferenceConfiguration.DEFAULT_PORTAL_SONAR_HOLD_MS;
            bundle.portalReadyMs = PreferenceConfiguration.DEFAULT_PORTAL_READY_MS;
            bundle.portalChargeMs = PreferenceConfiguration.DEFAULT_PORTAL_CHARGE_MS;
            bundle.portalPreviewFadeMs = PreferenceConfiguration.DEFAULT_PORTAL_PREVIEW_FADE_MS;
            bundle.portalPreviewHoldMs = PreferenceConfiguration.DEFAULT_PORTAL_PREVIEW_HOLD_MS;
            bundle.portalExpandMs = PreferenceConfiguration.DEFAULT_PORTAL_EXPAND_MS;
            bundle.portalDisconnectCloseMs = PreferenceConfiguration.DEFAULT_PORTAL_DISCONNECT_CLOSE_MS;
            bundle.portalDisconnectFadeMs = PreferenceConfiguration.DEFAULT_PORTAL_DISCONNECT_FADE_MS;
            bundle.portalDisconnectOpenMs = PreferenceConfiguration.DEFAULT_PORTAL_DISCONNECT_OPEN_MS;
            bundle.portalDisconnectMs = PreferenceConfiguration.DEFAULT_PORTAL_DISCONNECT_MS;
            refreshAll();
        });
        LinearLayout.LayoutParams rstLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rstLp.topMargin = dp(12f);
        rstLp.gravity = Gravity.END;
        col.addView(btnResetAnimation, rstLp);

        sv.addView(col);
        return sv;
    }

    private View buildSavedThemesTab() {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(4f), 0, dp(20f));

        // Action buttons: Save As New | Export Clipboard | Import Clipboard
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        btnSaveNew = createPillButton("Save Theme");
        btnExportClipboard = createPillButton("Export");
        btnImportClipboard = createPillButton("Import");

        btnSaveNew.setOnClickListener(v -> promptSaveNewTheme());
        btnExportClipboard.setOnClickListener(v -> exportThemeToClipboard(bundle));
        btnImportClipboard.setOnClickListener(v -> importThemeFromClipboard());

        LinearLayout.LayoutParams a1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f);
        a1.setMarginEnd(dp(6f));
        actions.addView(btnSaveNew, a1);
        LinearLayout.LayoutParams a2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        a2.setMarginEnd(dp(6f));
        actions.addView(btnExportClipboard, a2);
        actions.addView(btnImportClipboard, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(actions);

        savedThemesList = new LinearLayout(this);
        savedThemesList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lLp.topMargin = dp(12f);
        col.addView(savedThemesList, lLp);

        sv.addView(col);
        return sv;
    }

    private void rebuildSavedThemesList(AppThemePalette p) {
        savedThemesList.removeAllViews();
        List<AppThemePalette.ThemeBundle> saved = AppThemePalette.loadSavedThemes(this);
        if (saved.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.theme_studio_no_saved_themes);
            empty.setTextColor(p.getSecondaryTextColor());
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16f), dp(24f), dp(16f), dp(24f));
            savedThemesList.addView(empty);
            return;
        }
        for (AppThemePalette.ThemeBundle item : saved) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12f), dp(10f), dp(12f), dp(10f));
            boolean isCurrent = item.id != null && item.id.equals(activeSavedId);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(16f));
            bg.setColor(AppThemePalette.withAlpha(p.surface, p.isLight ? 0.72f : 0.65f));
            bg.setStroke(dp(isCurrent ? 1.5f : 1f), isCurrent ? p.getNeonAccent() : p.getCardRimColor());
            card.setBackground(bg);

            // Top row: swatches + name
            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            int[] dots = { item.colors.background, item.colors.surface, item.colors.accent, item.colors.highlight };
            for (int c : dots) {
                View d = new View(this);
                GradientDrawable dg = new GradientDrawable();
                dg.setShape(GradientDrawable.OVAL);
                dg.setColor(c);
                dg.setStroke(dp(0.8f), p.getCardRimColor());
                d.setBackground(dg);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(12f), dp(12f));
                dlp.setMarginEnd(dp(4f));
                top.addView(d, dlp);
            }
            TextView nameTv = new TextView(this);
            nameTv.setText(item.name);
            nameTv.setTextColor(p.getPrimaryTextColor());
            nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            nameTv.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nLp.setMarginStart(dp(6f));
            top.addView(nameTv, nLp);

            TextView modeTv = new TextView(this);
            modeTv.setText(item.colors.isLight ? "Light" : "Dark");
            modeTv.setTextColor(p.getSecondaryTextColor());
            modeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            top.addView(modeTv);
            card.addView(top);

            // Bottom row: Load | Update | Copy | Delete
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.END);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rLp.topMargin = dp(8f);

            TextView btnLoad = createSmallChip("Load", p.accent, true, p.isLight);
            btnLoad.setOnClickListener(v -> {
                AppThemePalette.ThemeBundle loaded = AppThemePalette.ThemeBundle.fromJsonString(item.toJsonString());
                if (loaded != null) {
                    bundle = loaded;
                    activeSavedId = item.id;
                    refreshAll();
                    Toast.makeText(this, getString(R.string.theme_studio_loaded_fmt, item.name), Toast.LENGTH_SHORT).show();
                }
            });
            TextView btnUpdate = createSmallChip("Update", p.highlight, false, p.isLight);
            btnUpdate.setOnClickListener(v -> {
                bundle.id = item.id;
                bundle.name = item.name;
                activeSavedId = item.id;
                AppThemePalette.saveOrUpdateTheme(this, bundle);
                refreshAll();
                Toast.makeText(this, getString(R.string.theme_studio_updated_fmt, item.name), Toast.LENGTH_SHORT).show();
            });
            TextView btnCopy = createSmallChip("Copy", p.highlight, false, p.isLight);
            btnCopy.setOnClickListener(v -> exportThemeToClipboard(item));

            TextView btnDelete = createSmallChip("Delete", 0xFFFF5252, false, p.isLight);
            btnDelete.setOnClickListener(v -> {
                AppThemePalette.deleteSavedTheme(this, item.id);
                if (item.id.equals(activeSavedId)) {
                    activeSavedId = null;
                }
                refreshAll();
            });

            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cLp.setMarginEnd(dp(6f));
            row.addView(btnLoad, cLp);
            row.addView(btnUpdate, new LinearLayout.LayoutParams(cLp));
            row.addView(btnCopy, new LinearLayout.LayoutParams(cLp));
            row.addView(btnDelete);
            card.addView(row, rLp);

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.bottomMargin = dp(8f);
            savedThemesList.addView(card, cardLp);
        }
    }

    private void promptSaveNewTheme() {
        final EditText input = new EditText(this);
        input.setHint("My Theme");
        input.setSingleLine(true);
        input.setText(bundle.name != null && !bundle.name.isEmpty() ? bundle.name : "My Theme");
        int pad = dp(16f);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(pad, dp(8f), pad, 0);
        container.addView(input);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(R.string.theme_studio_save_title)
                .setView(container)
                .setPositiveButton("Save", (d, w) -> {
                    String name = input.getText() != null ? input.getText().toString().trim() : "";
                    if (name.isEmpty()) {
                        name = "Custom Theme";
                    }
                    String newId = UUID.randomUUID().toString();
                    AppThemePalette.ThemeBundle copy = AppThemePalette.ThemeBundle.fromJsonString(bundle.toJsonString());
                    if (copy != null) {
                        copy.id = newId;
                        copy.name = name;
                        AppThemePalette.saveOrUpdateTheme(this, copy);
                        bundle = copy;
                        activeSavedId = newId;
                        refreshAll();
                        Toast.makeText(this, getString(R.string.theme_studio_saved_fmt, name), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        UiHelper.styleDialogSquircle(dlg);
        dlg.show();
        UiHelper.styleDialogSquircle(dlg);
    }

    private void exportThemeToClipboard(AppThemePalette.ThemeBundle target) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Moonlight Theme", target.toJsonString()));
            Toast.makeText(this, R.string.theme_studio_exported_clipboard, Toast.LENGTH_SHORT).show();
        }
    }

    private void importThemeFromClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null
                || cm.getPrimaryClip().getItemCount() == 0) {
            Toast.makeText(this, R.string.theme_studio_clipboard_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence text = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
        AppThemePalette.ThemeBundle imported = AppThemePalette.ThemeBundle.fromJsonString(
                text != null ? text.toString() : null);
        if (imported == null) {
            Toast.makeText(this, R.string.theme_studio_import_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        imported.id = UUID.randomUUID().toString();
        bundle = imported;
        activeSavedId = null;
        refreshAll();
        Toast.makeText(this, getString(R.string.theme_studio_imported_fmt, imported.name), Toast.LENGTH_SHORT).show();
    }

    /**
     * Centers the portal iris & sonar ripples inside the top preview stage while keeping
     * u_resolution and minDim locked to the full-screen PortalShaderView (1:1 particle scale).
     */
    private void updatePortalStageCenterOffset() {
        if (previewPortal == null || outerRoot == null) {
            return;
        }
        int fullW = outerRoot.getWidth();
        int fullH = outerRoot.getHeight();
        int stageH = previewStage != null ? previewStage.getHeight() : Math.round(fullH * 0.39f);
        if (fullW <= 0 || fullH <= 0 || stageH <= 0) {
            previewPortal.setPortalCenterOffset(0f, 0.24f);
            return;
        }
        float minDim = Math.max(1f, Math.min(fullW, fullH));
        int topBarBottom = (previewTopBar != null && previewTopBar.getBottom() > 0)
                ? previewTopBar.getBottom()
                : dp(64f);
        float stageCenterFromTop = (topBarBottom + stageH) * 0.5f;
        float offsetY = (0.5f * fullH - stageCenterFromTop) / minDim;
        previewPortal.setPortalCenterOffset(0f, offsetY);
    }

    /**
     * Plays the exact sequential millisecond Launch or Disconnect state machine inside the
     * top preview stage using the real configured phase durations.
     */
    private void toggleIrisDemo(boolean disconnectMode) {
        if (playingIris) {
            boolean sameMode = (playingDisconnectMode == disconnectMode);
            stopIrisDemo();
            if (sameMode) {
                return;
            }
        }
        playingIris = true;
        playingDisconnectMode = disconnectMode;
        btnPlayIris.setText("Stop Iris");
        if (btnAnimPlayLaunch != null) {
            btnAnimPlayLaunch.setText(!disconnectMode ? "Stop Launch" : "Play Launch");
        }
        if (btnAnimPlayDisconnect != null) {
            btnAnimPlayDisconnect.setText(disconnectMode ? "Stop Disconnect" : "Play Disconnect");
        }
        refreshAll();
        previewCardsRow.animate().alpha(0f).setDuration(180L).start();
        updatePortalStageCenterOffset();

        if (!disconnectMode) {
            // Exact linear Launch state machine in milliseconds:
            // 1. Launch Iris Close & Spiral (1.0 -> 0.0 expand; optional decoupled coil with Ready/Charge catch-up)
            // 2. Sonar Pulse Hold (0.0 expand, 0.0 ready, 0.0 charge, 0.0 preview)
            // 3. Sonar -> Ready Glow (Linear 0.0 -> 1.0 ready)
            // 4. Simultaneous Ready -> Charge (Linear 0.0 -> 1.0) + Stream Fade-In (Linear 0.0 -> 1.0)
            // 5. Iris Wipe Expand Open (0.0 -> 1.0 expand)
            final long t0 = Math.max(0L, bundle.portalLaunchCloseMs);
            final boolean decoupled = bundle.portalDecouplePulseCoil;
            final long coilDur = Math.max(0L, bundle.portalPulseCoilMs);
            final long t1 = t0 + Math.max(0L, bundle.portalSonarHoldMs);
            final long t2 = t1 + Math.max(0L, bundle.portalReadyMs);
            final long chargeDur = Math.max(0L, bundle.portalChargeMs);
            final long fadeDur = Math.max(0L, bundle.portalPreviewFadeMs);
            final long phase3Dur = Math.max(chargeDur, fadeDur);
            final long t3 = t2 + phase3Dur;
            final long t4 = t3 + Math.max(0L, bundle.portalExpandMs);
            final long coilTargetEnd = t2 + Math.round(chargeDur * 0.85f);
            final long totalMs = Math.max(100L, t4);

            previewPortal.setIsDisconnecting(false);
            previewPortal.setUniforms(0f, 0f, 0f, 1f, 0f);
            irisAnimator = ValueAnimator.ofFloat(0f, (float) totalMs);
            irisAnimator.setDuration(totalMs);
            irisAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
            irisAnimator.addUpdateListener(anim -> {
                float elapsed = (float) anim.getAnimatedValue();
                float morph;
                if (!decoupled) {
                    morph = (elapsed < t0)
                            ? (t0 > 0L ? Math.max(0f, Math.min(1f, elapsed / (float) t0)) : 1f)
                            : 1f;
                } else if (coilDur <= 0L) {
                    morph = 1f;
                } else if (coilDur <= coilTargetEnd) {
                    morph = Math.max(0f, Math.min(1f, elapsed / (float) coilDur));
                } else if (elapsed <= t1) {
                    morph = Math.max(0f, Math.min(1f, elapsed / (float) coilDur));
                } else if (elapsed < coilTargetEnd) {
                    float mAtT1 = Math.max(0f, Math.min(1f, t1 / (float) coilDur));
                    float span = Math.max(1f, (float) (coilTargetEnd - t1));
                    float frac = Math.max(0f, Math.min(1f, (elapsed - t1) / span));
                    morph = mAtT1 + (1f - mAtT1) * frac;
                } else {
                    morph = 1f;
                }

                if (elapsed < t0) {
                    float prog = t0 > 0 ? (elapsed / (float) t0) : 1f;
                    previewPortal.setUniforms(0f, 0f, 0f, 1f - Math.max(0f, Math.min(1f, prog)), morph);
                } else if (elapsed < t1) {
                    previewPortal.setUniforms(0f, 0f, 0f, 0f, morph);
                } else if (elapsed < t2) {
                    float r = (t2 > t1) ? Math.max(0f, Math.min(1f, (elapsed - t1) / (float) (t2 - t1))) : 1f;
                    previewPortal.setUniforms(r, 0f, 0f, 0f, morph);
                } else if (elapsed < t3) {
                    float p3Elapsed = elapsed - t2;
                    float c = chargeDur > 0L ? Math.max(0f, Math.min(1f, p3Elapsed / (float) chargeDur)) : 1f;
                    float p = fadeDur > 0L ? Math.max(0f, Math.min(1f, p3Elapsed / (float) fadeDur)) : 1f;
                    previewPortal.setUniforms(1f, c, p, 0f, morph);
                } else {
                    float e = (t4 > t3) ? Math.max(0f, Math.min(1f, (elapsed - t3) / (float) (t4 - t3))) : 1f;
                    previewPortal.setUniforms(1f, 1f, 1f, e, 1f);
                }
            });
        } else {
            // Exact 3-step Disconnect state machine in milliseconds:
            // 1. Disconnect Iris Close Over Stream (1.0 -> 0.0 expand, ready=1, charge=1, preview=1, pulseMorph=1, isDisc=1)
            // 2. Stream -> Starfield Fade (Linear 1.0 -> 0.0 ready, charge & preview, pulseMorph=1, isDisc=1)
            // 3. Iris Spiral Uncoil to Menu (0.0 -> 1.0 expand, 1.0 -> 0.0 pulseMorph, isDisc=1 -> 0 on end)
            final long d1 = Math.max(0L, bundle.portalDisconnectCloseMs);
            final long d2 = d1 + Math.max(0L, bundle.portalDisconnectFadeMs);
            final long d3 = d2 + Math.max(0L, bundle.portalDisconnectOpenMs);
            final long totalMs = Math.max(100L, d3);

            previewPortal.setIsDisconnecting(true);
            previewPortal.setUniforms(1f, 1f, 1f, 1f, 1f);
            irisAnimator = ValueAnimator.ofFloat(0f, (float) totalMs);
            irisAnimator.setDuration(totalMs);
            irisAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
            irisAnimator.addUpdateListener(anim -> {
                float elapsed = (float) anim.getAnimatedValue();
                if (elapsed < d1) {
                    float prog = d1 > 0 ? (elapsed / (float) d1) : 1f;
                    previewPortal.setUniforms(1f, 1f, 1f, 1f - Math.max(0f, Math.min(1f, prog)), 1f);
                } else if (elapsed < d2) {
                    float raw = (d2 > d1) ? ((elapsed - d1) / (float) (d2 - d1)) : 1f;
                    float v = 1f - Math.max(0f, Math.min(1f, raw));
                    previewPortal.setUniforms(v, v, v, 0f, 1f);
                } else {
                    float e = (d3 > d2) ? Math.max(0f, Math.min(1f, (elapsed - d2) / (float) (d3 - d2))) : 1f;
                    previewPortal.setUniforms(0f, 0f, 0f, e, 1f - e);
                }
            });
        }

        irisAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                stopIrisDemo();
            }
        });
        irisAnimator.start();
    }

    private void stopIrisDemo() {
        if (irisAnimator != null) {
            irisAnimator.cancel();
            irisAnimator = null;
        }
        playingIris = false;
        playingDisconnectMode = false;
        if (btnPlayIris != null) {
            btnPlayIris.setText("Play Iris");
        }
        if (btnAnimPlayLaunch != null) {
            btnAnimPlayLaunch.setText("Play Launch");
        }
        if (btnAnimPlayDisconnect != null) {
            btnAnimPlayDisconnect.setText("Play Disconnect");
        }
        if (previewPortal != null) {
            previewPortal.setPortalCenterOffset(0f, 0f);
            previewPortal.setIsDisconnecting(false);
            previewPortal.setUniforms(0f, 0f, 0f, 1f, 0f);
        }
        if (previewCardsRow != null) {
            previewCardsRow.animate().alpha(1f).setDuration(200L).start();
        }
        if (!updatingControls && bundle != null) {
            refreshAll();
        }
    }

    private void updateTabVisibility() {
        for (int i = 0; i < tabPages.length; i++) {
            tabPages[i].setVisibility(i == activeTab ? View.VISIBLE : View.GONE);
        }
    }

    private void refreshAll() {
        updatingControls = true;
        AppThemePalette p = AppThemePalette.buildCustomPalette(bundle.colors, bundle.frostedTintAmountPercent);

        // Live preview shader update
        if (previewPortal != null) {
            previewPortal.applyPreviewOverride(bundle);
        }

        // System bars luminance
        if (getWindow() != null) {
            WindowInsetsControllerCompat ctrl = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (ctrl != null) {
                ctrl.setAppearanceLightStatusBars(p.isLight);
                ctrl.setAppearanceLightNavigationBars(p.isLight);
            }
        }

        int strokePx = Math.round(dp(1f) * (bundle.cardOutlineThicknessPercent / 100f));
        int frostedTint = p.getFrostedCardTint(0x56000000);

        // Preview top bar (FrostedCardDrawable sampling full-screen previewPortal)
        previewTopBarDrawable.updateStyle(
                FlowingAuroraView.CornerMode.ALL, dp(20f),
                frostedTint, null, previewPortal);
        previewTopBarDrawable.setStrokeWidthPx(strokePx);
        previewTopBarDrawable.setRimColor(p.getCardRimColor());
        btnBack.setColorFilter(p.getPrimaryTextColor(), PorterDuff.Mode.SRC_IN);
        studioTitle.setTextColor(p.getPrimaryTextColor());
        stylePillButton(btnPlayIris, playingIris, p.highlight, p.isLight);
        stylePillButton(btnApply, true, p.accent, p.isLight);

        // Live preview Frosted Card
        previewCardDrawable.updateStyle(
                FlowingAuroraView.CornerMode.ALL, dp(20f),
                frostedTint, null, previewPortal);
        previewCardDrawable.setStrokeWidthPx(strokePx);
        previewCardDrawable.setRimColor(p.getCardRimColor());

        GradientDrawable hlDot = new GradientDrawable();
        hlDot.setShape(GradientDrawable.OVAL);
        hlDot.setColor(p.highlight);
        previewHighlightDot.setBackground(hlDot);

        previewCardTitle.setTextColor(p.getPrimaryTextColor());
        previewCardSub.setTextColor(p.getSecondaryTextColor());
        GradientDrawable playBg = new GradientDrawable();
        playBg.setShape(GradientDrawable.RECTANGLE);
        playBg.setCornerRadius(dp(999f));
        playBg.setColor(p.accent);
        previewCardPill.setBackground(playBg);
        previewCardPill.setTextColor(Color.WHITE);

        boolean blurOff = bundle.blurAmountPercent <= 0 || bundle.frostedTintAmountPercent >= 100;
        previewBlurStatusBadge.setText(bundle.frostedTintAmountPercent >= 100 ? "Opaque (Blur Off)"
                : (bundle.blurAmountPercent <= 0 ? "Clear Glass" : ("Blur " + bundle.blurAmountPercent + "%")));
        previewBlurStatusBadge.setTextColor(blurOff ? p.getSecondaryTextColor() : p.getNeonAccent());

        // Live preview Pop-Up Menu
        GradientDrawable popBg = new GradientDrawable();
        popBg.setShape(GradientDrawable.RECTANGLE);
        popBg.setCornerRadius(dp(18f));
        popBg.setColor(p.getDialogSurfaceColor(!blurOff));
        if (strokePx > 0) {
            popBg.setStroke(strokePx, p.getCardRimColor());
        }
        previewPopupCard.setBackground(popBg);
        previewPopupTitle.setTextColor(p.getPrimaryTextColor());
        previewPopupSub.setTextColor(p.getSecondaryTextColor());

        // Frosted Editor Sheet surface (uses Surface color + Frosted Card Tint + live blur!)
        int sheetTint = AppThemePalette.withAlphaInt(
                p.surface,
                Math.max(p.isLight ? 0xCC : 0xB0, Math.round((bundle.frostedTintAmountPercent / 100f) * 255f)));
        editorSheetDrawable.updateStyle(
                FlowingAuroraView.CornerMode.TOP, dp(24f),
                sheetTint, null, previewPortal);
        editorSheetDrawable.setStrokeWidthPx(Math.max(1, strokePx));
        editorSheetDrawable.setRimColor(p.getCardRimColor());

        for (int i = 0; i < tabButtons.length; i++) {
            stylePillButton(tabButtons[i], i == activeTab, p.accent, p.isLight);
        }

        // Tab 1: Colors
        presetHeading.setTextColor(p.getSecondaryTextColor());
        stylePillButton(btnDarkBase, !bundle.colors.isLight, p.accent, p.isLight);
        stylePillButton(btnLightBase, bundle.colors.isLight, p.accent, p.isLight);
        stylePillButton(btnHarmonize, false, p.highlight, p.isLight);
        int chipFill = p.isLight ? 0x160F172A : 0x18FFFFFF;
        int chipStroke = p.isLight ? 0x300F172A : 0x36FFFFFF;
        for (int i = 0; i < chTabs.length; i++) {
            int cVal = getChannelColor(bundle.colors, i);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(cVal);
            dotBg.setStroke(dp(1f), p.getCardRimColor());
            chDots[i].setBackground(dotBg);
            boolean sel = (i == activeChannel);
            GradientDrawable tBg = new GradientDrawable();
            tBg.setShape(GradientDrawable.RECTANGLE);
            tBg.setCornerRadius(dp(12f));
            tBg.setColor(sel ? AppThemePalette.withAlpha(p.accent, p.isLight ? 0.22f : 0.30f) : chipFill);
            tBg.setStroke(dp(sel ? 1.5f : 1f), sel ? p.getNeonAccent() : chipStroke);
            chTabs[i].setBackground(tBg);
            chTexts[i].setTextColor(sel ? p.getPrimaryTextColor() : p.getSecondaryTextColor());
        }
        int cur = getChannelColor(bundle.colors, activeChannel);
        int r = Color.red(cur), g = Color.green(cur), b = Color.blue(cur);
        rgbBars[0].setProgress(r);
        rgbBars[1].setProgress(g);
        rgbBars[2].setProgress(b);
        rgbVals[0].setText(String.valueOf(r));
        rgbVals[1].setText(String.valueOf(g));
        rgbVals[2].setText(String.valueOf(b));
        int pillStroke = Math.max(1, dp(1f));
        int pillPadH = dp(9f);
        int pillPadV = dp(3f);
        int pillRad = dp(999f);
        for (int i = 0; i < 3; i++) {
            rgbVals[i].setTextColor(p.getNeonAccent());
            rgbVals[i].setPadding(pillPadH, pillPadV, pillPadH, pillPadV);
            GradientDrawable rgbPill = new GradientDrawable();
            rgbPill.setShape(GradientDrawable.RECTANGLE);
            rgbPill.setCornerRadius(pillRad);
            rgbPill.setColor(AppThemePalette.withAlpha(p.accent, p.isLight ? 0.14f : 0.22f));
            rgbPill.setStroke(pillStroke, AppThemePalette.withAlpha(p.getNeonAccent(), 0.55f));
            rgbVals[i].setBackground(rgbPill);
            rgbBars[i].setProgressBackgroundTintList(ColorStateList.valueOf(p.isLight ? 0x280F172A : 0x36FFFFFF));
            rgbBars[i].setThumbTintList(ColorStateList.valueOf(p.isLight ? 0xFF0F172A : Color.WHITE));
        }
        hexPrefix.setTextColor(p.getSecondaryTextColor());
        hexInput.setTextColor(p.getPrimaryTextColor());
        GradientDrawable hexBg = new GradientDrawable();
        hexBg.setShape(GradientDrawable.RECTANGLE);
        hexBg.setCornerRadius(dp(10f));
        hexBg.setColor(chipFill);
        hexBg.setStroke(dp(1f), chipStroke);
        hexInput.setBackground(hexBg);
        activeChannelHint.setText(CHANNEL_LABELS[activeChannel]);
        activeChannelHint.setTextColor(p.getSecondaryTextColor());
        String hexStr = String.format(Locale.US, "%02X%02X%02X", r, g, b);
        if (!hexStr.equalsIgnoreCase(hexInput.getText().toString())) {
            hexInput.setText(hexStr);
            hexInput.setSelection(hexStr.length());
        }

        // Tab 2: Glass & Blur
        rowBlurAmount.setValue(bundle.blurAmountPercent);
        rowBlurAmount.applyTheme(p, pillStroke, pillPadH, pillPadV, pillRad);
        rowBlurSaturation.setValue(bundle.blurSaturationPercent);
        rowBlurSaturation.applyTheme(p, pillStroke, pillPadH, pillPadV, pillRad);
        rowTintAmount.setValue(bundle.frostedTintAmountPercent);
        rowTintAmount.applyTheme(p, pillStroke, pillPadH, pillPadV, pillRad);
        tintPerfHint.setTextColor(bundle.frostedTintAmountPercent >= 100 ? p.getNeonAccent() : p.getSecondaryTextColor());
        rowCardOutline.setValue(bundle.cardOutlineThicknessPercent);
        rowCardOutline.applyTheme(p, pillStroke, pillPadH, pillPadV, pillRad);
        bgQualityHeading.setTextColor(p.getPrimaryTextColor());
        for (int i = 0; i < bgQualityPills.length; i++) {
            boolean active = BG_QUALITY_VALUES[i].equals(bundle.bgQuality);
            stylePillButton(bgQualityPills[i], active, p.accent, p.isLight);
        }
        uiBatterySaverHeading.setTextColor(p.getPrimaryTextColor());
        String savedSaverMode = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .getString(PreferenceConfiguration.UI_BATTERY_SAVER_PREF_STRING,
                        PreferenceConfiguration.DEFAULT_UI_BATTERY_SAVER);
        for (int i = 0; i < uiBatterySaverPills.length; i++) {
            boolean active = UI_BATTERY_SAVER_VALUES[i].equals(savedSaverMode);
            stylePillButton(uiBatterySaverPills[i], active, p.accent, p.isLight);
        }

        // Tab 3: Portal FX + Energy Pulses
        energyPulsesHeading.setTextColor(p.getSecondaryTextColor());
        SliderRow[] portalRows = {
                rowParticleBright, rowParticleSize, rowParticleDensity,
                rowRingGlow, rowPortalScale, rowSonarSpeed,
                rowPulseAmount, rowPulseBrightness, rowPulseLength,
                rowPulseSpeed, rowWaveAmp
        };
        int[] portalVals = {
                bundle.portalParticleBrightnessPercent, bundle.portalParticleSizePercent,
                bundle.portalParticleDensityPercent, bundle.portalGlowIntensityPercent,
                bundle.portalWindowScalePercent, bundle.portalSonarSpeedPercent,
                bundle.portalPulseAmount, bundle.portalPulseBrightnessPercent,
                bundle.portalPulseLengthPercent, bundle.portalPulseSpeedPercent,
                bundle.portalWaveAmpPercent
        };
        for (int i = 0; i < portalRows.length; i++) {
            portalRows[i].setValue(portalVals[i]);
            portalRows[i].applyTheme(p, pillStroke, pillPadH, pillPadV, pillRad);
        }
        stylePillButton(btnResetPortal, false, 0xFFFF5252, p.isLight);

        // Tab 4: Animation
        stylePillButton(btnAnimPlayLaunch, playingIris && !playingDisconnectMode, p.highlight, p.isLight);
        stylePillButton(btnAnimPlayDisconnect, playingIris && playingDisconnectMode, p.highlight, p.isLight);
        animLaunchHeading.setTextColor(p.getSecondaryTextColor());
        animDisconnectHeading.setTextColor(p.getSecondaryTextColor());
        if (btnDecouplePulseCoil != null) {
            btnDecouplePulseCoil.setText(bundle.portalDecouplePulseCoil
                    ? "Decouple Spiral Morph: ON"
                    : "Decouple Spiral Morph: OFF");
            stylePillButton(btnDecouplePulseCoil, bundle.portalDecouplePulseCoil, p.accent, p.isLight);
        }
        if (rowLaunchCloseMs != null) {
            rowLaunchCloseMs.titleTv.setText(bundle.portalDecouplePulseCoil
                    ? "1a. Launch Iris Close"
                    : "1. Launch Iris Close & Spiral");
        }
        if (pulseCoilContainer != null) {
            pulseCoilContainer.setVisibility(bundle.portalDecouplePulseCoil ? View.VISIBLE : View.GONE);
        }
        SliderRow[] animRows = {
                rowLaunchCloseMs, rowPulseCoilMs, rowSonarHoldMs, rowReadyMs,
                rowChargeMs, rowFadeMs, rowExpandMs,
                rowDisconnectCloseMs, rowDisconnectFadeMs, rowDisconnectOpenMs
        };
        int[] animVals = {
                bundle.portalLaunchCloseMs, bundle.portalPulseCoilMs, bundle.portalSonarHoldMs, bundle.portalReadyMs,
                bundle.portalChargeMs, bundle.portalPreviewFadeMs, bundle.portalExpandMs,
                bundle.portalDisconnectCloseMs, bundle.portalDisconnectFadeMs, bundle.portalDisconnectOpenMs
        };
        for (int i = 0; i < animRows.length; i++) {
            animRows[i].setValue(animVals[i]);
            animRows[i].applyTheme(p, pillStroke, pillPadH, pillPadV, pillRad);
        }
        stylePillButton(btnResetAnimation, false, 0xFFFF5252, p.isLight);

        // Tab 5: My Themes
        stylePillButton(btnSaveNew, true, p.accent, p.isLight);
        stylePillButton(btnExportClipboard, false, p.highlight, p.isLight);
        stylePillButton(btnImportClipboard, false, p.highlight, p.isLight);
        if (activeTab == TAB_SAVED) {
            rebuildSavedThemesList(p);
        }

        updatingControls = false;
    }

    private interface IntCallback {
        void onValue(int value);
    }

    private SliderRow addSliderRow(LinearLayout parent, final String label,
                                   final int min, final int max, final int step,
                                   final int customMin, final int customMax,
                                   final String suffix, final IntCallback onChanged) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.topMargin = dp(8f);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleTv = new TextView(this);
        titleTv.setText(label);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(titleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView valueTv = new TextView(this);
        valueTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        valueTv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        valueTv.setClickable(true);
        valueTv.setFocusable(true);
        header.addView(valueTv);
        box.addView(header);

        SeekBar sb = new SeekBar(this);
        sb.setMax((max - min) / step);

        final SliderRow row = new SliderRow(titleTv, valueTv, sb, min, max, step, customMin, customMax, suffix);

        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (updatingControls || !fromUser) return;
                int val = min + progress * step;
                row.currentValue = val;
                valueTv.setText(val + suffix);
                onChanged.onValue(val);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        valueTv.setOnClickListener(v -> promptCustomSliderValue(
                label, row.currentValue, customMin, customMax, suffix,
                customVal -> {
                    row.setValue(customVal);
                    onChanged.onValue(customVal);
                }));

        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbLp.topMargin = dp(2f);
        box.addView(sb, sbLp);
        parent.addView(box, bLp);
        return row;
    }

    /**
     * Opens a themed squircle dialog allowing the user to type a custom numeric value
     * that can exceed the normal SeekBar limit (up to customMax).
     */
    private void promptCustomSliderValue(String label, int currentVal, int customMin, int customMax,
                                         String suffix, IntCallback onAccepted) {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        String curStr = String.valueOf(currentVal);
        input.setText(curStr);
        input.setSelection(curStr.length());
        String unit = (suffix != null && !suffix.trim().isEmpty()) ? suffix.trim() : "";
        input.setHint(customMin + " – " + customMax + (unit.isEmpty() ? "" : (" " + unit)));

        int pad = dp(18f);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(pad, dp(10f), pad, 0);
        container.addView(input);

        String title = label + (unit.isEmpty() ? "" : (" (" + unit + ")"));
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("Enter custom value (" + customMin + " – " + customMax + (unit.isEmpty() ? "" : (" " + unit)) + "):")
                .setView(container)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String raw = input.getText() != null ? input.getText().toString().trim() : "";
                    if (raw.isEmpty()) {
                        return;
                    }
                    try {
                        int parsed = Integer.parseInt(raw);
                        int clamped = Math.max(customMin, Math.min(customMax, parsed));
                        onAccepted.onValue(clamped);
                    } catch (NumberFormatException ignored) {
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        UiHelper.styleDialogSquircle(dlg);
        dlg.show();
        UiHelper.styleDialogSquircle(dlg);
    }

    private TextView createPillButton(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(12f), dp(7f), dp(12f), dp(7f));
        tv.setClickable(true);
        return tv;
    }

    private TextView createSmallChip(String text, int accent, boolean active, boolean isLight) {
        TextView tv = createPillButton(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        tv.setPadding(dp(10f), dp(5f), dp(10f), dp(5f));
        stylePillButton(tv, active, accent, isLight);
        return tv;
    }

    private void stylePillButton(TextView tv, boolean active, int accent, boolean isLight) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(999f));
        if (active) {
            bg.setColor(accent);
            bg.setStroke(dp(1.2f), AppThemePalette.withAlpha(Color.WHITE, 0.45f));
            tv.setTextColor(Color.WHITE);
        } else {
            bg.setColor(isLight ? 0x160F172A : 0x1AFFFFFF);
            bg.setStroke(dp(1f), AppThemePalette.withAlpha(accent, isLight ? 0.50f : 0.45f));
            tv.setTextColor(isLight ? 0xFF0F172A : 0xE6FFFFFF);
        }
        tv.setBackground(bg);
    }

    private static void copySpec(AppThemePalette.CustomPaletteSpec dst, AppThemePalette.CustomPaletteSpec src) {
        dst.isLight = src.isLight;
        dst.background = src.background;
        dst.surface = src.surface;
        dst.popupTint = src.popupTint;
        dst.accent = src.accent;
        dst.highlight = src.highlight;
        dst.primaryText = src.primaryText;
        dst.secondaryText = src.secondaryText;
        dst.cardOutline = src.cardOutline;
        dst.particleColor = src.particleColor;
        dst.particleColorMid = src.particleColorMid;
        dst.particleColorFar = src.particleColorFar;
        dst.pulseColor = src.pulseColor;
        dst.ringColor = src.ringColor;
        dst.chargeColor = src.chargeColor;
    }

    private static int getChannelColor(AppThemePalette.CustomPaletteSpec spec, int ch) {
        switch (ch) {
            case CH_BG: return spec.background;
            case CH_SURFACE: return spec.surface;
            case CH_POPUP: return spec.popupTint;
            case CH_TEXT_PRIMARY: return spec.primaryText;
            case CH_TEXT_SECONDARY: return spec.secondaryText;
            case CH_OUTLINE: return spec.cardOutline;
            case CH_HIGHLIGHT: return spec.highlight;
            case CH_PARTICLE_NEAR: return spec.particleColor;
            case CH_PARTICLE_MID: return spec.particleColorMid;
            case CH_PARTICLE_FAR: return spec.particleColorFar;
            case CH_PULSE: return spec.pulseColor;
            case CH_RING: return spec.ringColor;
            case CH_CHARGE: return spec.chargeColor;
            case CH_ACCENT:
            default: return spec.accent;
        }
    }

    private static void setChannelColor(AppThemePalette.CustomPaletteSpec spec, int ch, int color) {
        int opaque = color | 0xFF000000;
        switch (ch) {
            case CH_BG: spec.background = opaque; break;
            case CH_SURFACE: spec.surface = opaque; break;
            case CH_POPUP: spec.popupTint = opaque; break;
            case CH_TEXT_PRIMARY: spec.primaryText = opaque; break;
            case CH_TEXT_SECONDARY: spec.secondaryText = opaque; break;
            case CH_OUTLINE: spec.cardOutline = opaque; break;
            case CH_HIGHLIGHT: spec.highlight = opaque; break;
            case CH_PARTICLE_NEAR: spec.particleColor = opaque; break;
            case CH_PARTICLE_MID: spec.particleColorMid = opaque; break;
            case CH_PARTICLE_FAR: spec.particleColorFar = opaque; break;
            case CH_PULSE: spec.pulseColor = opaque; break;
            case CH_RING: spec.ringColor = opaque; break;
            case CH_CHARGE: spec.chargeColor = opaque; break;
            case CH_ACCENT:
            default: spec.accent = opaque; break;
        }
    }

    private int dp(float val) {
        return Math.round(UiHelper.dpToPx(this, val));
    }
}
