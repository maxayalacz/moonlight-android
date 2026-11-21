package com.limelight.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.limelight.R;
import com.limelight.utils.UiHelper;

import java.util.Locale;

/**
 * Interactive One UI custom palette studio allowing users to switch Dark/Light base,
 * auto-harmonize all 6 colors from a primary accent, or fine-tune Background, Surface,
 * Accent, Highlight, Particle, and Portal Ring colors using swatches, RGB sliders, and hex input
 * with an instant live preview card.
 */
public final class CustomPaletteDialog {

    private static final int CH_ACCENT = 0;
    private static final int CH_BG = 1;
    private static final int CH_SURFACE = 2;
    private static final int CH_HIGHLIGHT = 3;
    private static final int CH_PARTICLE = 4;
    private static final int CH_RING = 5;

    private static final String[] CHANNEL_LABELS = {
            "Accent", "Background", "Surface", "Highlight", "Particles", "Ring"
    };

    private static final int[] QUICK_SWATCHES = {
            0xFF3B82F6, // Cobalt Blue
            0xFF7C3AED, // Electric Violet
            0xFFFF2D95, // Neon Magenta
            0xFFEF4444, // Crimson Red
            0xFFF97316, // Vivid Orange
            0xFFFACC15, // Solar Gold
            0xFF10B981, // Emerald Green
            0xFF06B6D4, // Cyan Teal
            0xFF0D1117, // Midnight Dark
            0xFFF3F7FD  // Pearl Light
    };

    private CustomPaletteDialog() {}

    public static void show(@NonNull Context context, @Nullable Runnable onApplied) {
        final Context themedCtx = new ContextThemeWrapper(context, R.style.OneUI_DialogTheme);
        final Dialog dialog = new Dialog(themedCtx, R.style.OneUI_DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final AppThemePalette.CustomPaletteSpec spec = AppThemePalette.loadCustomSpec(context);
        final int[] activeChannel = { CH_ACCENT };
        final boolean[] updatingControls = { false };

        ScrollView scrollView = new ScrollView(themedCtx);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(themedCtx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(themedCtx, 18f);
        root.setPadding(pad, pad, pad, pad);

        // Title
        TextView title = new TextView(themedCtx);
        title.setText(R.string.title_custom_palette);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        // 1. Live Preview Card
        final FrameLayout previewBackdrop = new FrameLayout(themedCtx);
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(themedCtx, 96f));
        prevLp.topMargin = dp(themedCtx, 12f);
        root.addView(previewBackdrop, prevLp);

        final View previewRing = new View(themedCtx);
        FrameLayout.LayoutParams ringLp = new FrameLayout.LayoutParams(dp(themedCtx, 58f), dp(themedCtx, 58f));
        ringLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        ringLp.setMarginEnd(dp(themedCtx, 14f));
        previewBackdrop.addView(previewRing, ringLp);

        final View previewParticleDot1 = new View(themedCtx);
        FrameLayout.LayoutParams d1Lp = new FrameLayout.LayoutParams(dp(themedCtx, 7f), dp(themedCtx, 7f));
        d1Lp.gravity = Gravity.END | Gravity.TOP;
        d1Lp.topMargin = dp(themedCtx, 16f);
        d1Lp.setMarginEnd(dp(themedCtx, 38f));
        previewBackdrop.addView(previewParticleDot1, d1Lp);

        final View previewParticleDot2 = new View(themedCtx);
        FrameLayout.LayoutParams d2Lp = new FrameLayout.LayoutParams(dp(themedCtx, 5f), dp(themedCtx, 5f));
        d2Lp.gravity = Gravity.END | Gravity.BOTTOM;
        d2Lp.bottomMargin = dp(themedCtx, 22f);
        d2Lp.setMarginEnd(dp(themedCtx, 24f));
        previewBackdrop.addView(previewParticleDot2, d2Lp);

        final LinearLayout previewCard = new LinearLayout(themedCtx);
        previewCard.setOrientation(LinearLayout.VERTICAL);
        previewCard.setPadding(dp(themedCtx, 14f), dp(10f, themedCtx), dp(themedCtx, 14f), dp(10f, themedCtx));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        cardLp.setMarginStart(dp(themedCtx, 12f));
        previewBackdrop.addView(previewCard, cardLp);

        final TextView previewTitle = new TextView(themedCtx);
        previewTitle.setText("Moonlight Preview");
        previewTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        previewTitle.setTypeface(Typeface.DEFAULT_BOLD);
        previewCard.addView(previewTitle);

        final TextView previewSub = new TextView(themedCtx);
        previewSub.setText("Frosted glass & accent glow");
        previewSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        previewCard.addView(previewSub);

        final TextView previewPill = new TextView(themedCtx);
        previewPill.setText("Play");
        previewPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        previewPill.setTypeface(Typeface.DEFAULT_BOLD);
        previewPill.setPadding(dp(themedCtx, 12f), dp(themedCtx, 3f), dp(themedCtx, 12f), dp(themedCtx, 3f));
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pillLp.topMargin = dp(themedCtx, 6f);
        previewCard.addView(previewPill, pillLp);

        // 2. Dark / Light Base Toggle + Auto-Harmonize Button
        LinearLayout modeRow = new LinearLayout(themedCtx);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams modeRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        modeRowLp.topMargin = dp(themedCtx, 12f);
        root.addView(modeRow, modeRowLp);

        final TextView btnDarkBase = createPillTab(themedCtx, "Dark Base");
        final TextView btnLightBase = createPillTab(themedCtx, "Light Base");
        final TextView btnHarmonize = createPillTab(themedCtx, "Auto-Harmonize");

        LinearLayout.LayoutParams tabLp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tabLp1.setMarginEnd(dp(themedCtx, 6f));
        modeRow.addView(btnDarkBase, tabLp1);

        LinearLayout.LayoutParams tabLp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tabLp2.setMarginEnd(dp(themedCtx, 6f));
        modeRow.addView(btnLightBase, tabLp2);

        modeRow.addView(btnHarmonize, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 3. 6-Channel Selector Tabs
        HorizontalScrollView chScroll = new HorizontalScrollView(themedCtx);
        chScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chRow = new LinearLayout(themedCtx);
        chRow.setOrientation(LinearLayout.HORIZONTAL);
        final LinearLayout[] chTabs = new LinearLayout[CHANNEL_LABELS.length];
        final View[] chDots = new View[CHANNEL_LABELS.length];
        final TextView[] chTexts = new TextView[CHANNEL_LABELS.length];

        for (int i = 0; i < CHANNEL_LABELS.length; i++) {
            LinearLayout tab = new LinearLayout(themedCtx);
            tab.setOrientation(LinearLayout.HORIZONTAL);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setPadding(dp(themedCtx, 10f), dp(themedCtx, 7f), dp(themedCtx, 10f), dp(themedCtx, 7f));
            tab.setClickable(true);

            View dot = new View(themedCtx);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(themedCtx, 12f), dp(themedCtx, 12f));
            dotLp.setMarginEnd(dp(themedCtx, 6f));
            tab.addView(dot, dotLp);

            TextView tv = new TextView(themedCtx);
            tv.setText(CHANNEL_LABELS[i]);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tab.addView(tv);

            chTabs[i] = tab;
            chDots[i] = dot;
            chTexts[i] = tv;

            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                tLp.setMarginStart(dp(themedCtx, 6f));
            }
            chRow.addView(tab, tLp);
        }
        chScroll.addView(chRow);
        LinearLayout.LayoutParams chScrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chScrollLp.topMargin = dp(themedCtx, 12f);
        root.addView(chScroll, chScrollLp);

        // 4. Quick Color Swatches
        HorizontalScrollView swatchScroll = new HorizontalScrollView(themedCtx);
        swatchScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout swatchRow = new LinearLayout(themedCtx);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        swatchRow.setPadding(dp(themedCtx, 2f), dp(themedCtx, 4f), dp(themedCtx, 2f), dp(themedCtx, 4f));
        final View[] swatchViews = new View[QUICK_SWATCHES.length];
        for (int i = 0; i < QUICK_SWATCHES.length; i++) {
            View sw = new View(themedCtx);
            GradientDrawable swBg = new GradientDrawable();
            swBg.setShape(GradientDrawable.OVAL);
            swBg.setColor(QUICK_SWATCHES[i]);
            swBg.setStroke(dp(themedCtx, 1.5f), 0x88FFFFFF);
            sw.setBackground(swBg);
            sw.setClickable(true);
            int size = dp(themedCtx, 28f);
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(size, size);
            if (i > 0) {
                swLp.setMarginStart(dp(themedCtx, 8f));
            }
            swatchRow.addView(sw, swLp);
            swatchViews[i] = sw;
        }
        swatchScroll.addView(swatchRow);
        LinearLayout.LayoutParams swScrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        swScrollLp.topMargin = dp(themedCtx, 10f);
        root.addView(swatchScroll, swScrollLp);

        // 5. RGB Sliders + Hex Input
        final SeekBar[] rgbBars = new SeekBar[3];
        final TextView[] rgbVals = new TextView[3];
        final String[] rgbNames = { "R", "G", "B" };
        final int[] rgbTints = { 0xFFFF5252, 0xFF34D399, 0xFF60A5FA };

        for (int c = 0; c < 3; c++) {
            LinearLayout row = new LinearLayout(themedCtx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rLp.topMargin = dp(themedCtx, 6f);

            TextView lbl = new TextView(themedCtx);
            lbl.setText(rgbNames[c]);
            lbl.setTextColor(rgbTints[c]);
            lbl.setTypeface(Typeface.DEFAULT_BOLD);
            lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            row.addView(lbl, new LinearLayout.LayoutParams(dp(themedCtx, 20f), ViewGroup.LayoutParams.WRAP_CONTENT));

            SeekBar sb = new SeekBar(themedCtx);
            sb.setMax(255);
            sb.setProgressTintList(ColorStateList.valueOf(rgbTints[c]));
            sb.setProgressBackgroundTintList(ColorStateList.valueOf(0x38FFFFFF));
            sb.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
            row.addView(sb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView val = new TextView(themedCtx);
            val.setTextColor(0xD8FFFFFF);
            val.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            val.setTypeface(Typeface.MONOSPACE);
            val.setGravity(Gravity.END);
            row.addView(val, new LinearLayout.LayoutParams(dp(themedCtx, 34f), ViewGroup.LayoutParams.WRAP_CONTENT));

            rgbBars[c] = sb;
            rgbVals[c] = val;
            root.addView(row, rLp);
        }

        // Hex input row
        LinearLayout hexRow = new LinearLayout(themedCtx);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hexRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hexRowLp.topMargin = dp(themedCtx, 10f);

        TextView hexPrefix = new TextView(themedCtx);
        hexPrefix.setText("HEX #");
        hexPrefix.setTextColor(0xB8FFFFFF);
        hexPrefix.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        hexPrefix.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        hexRow.addView(hexPrefix);

        final EditText hexInput = new EditText(themedCtx);
        hexInput.setTextColor(Color.WHITE);
        hexInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        hexInput.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        hexInput.setSingleLine(true);
        hexInput.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(6) });
        hexInput.setPadding(dp(themedCtx, 10f), dp(themedCtx, 6f), dp(themedCtx, 10f), dp(themedCtx, 6f));
        GradientDrawable hexBg = new GradientDrawable();
        hexBg.setShape(GradientDrawable.RECTANGLE);
        hexBg.setCornerRadius(dp(themedCtx, 10f));
        hexBg.setColor(0x1EFFFFFF);
        hexBg.setStroke(dp(themedCtx, 1f), 0x44FFFFFF);
        hexInput.setBackground(hexBg);
        LinearLayout.LayoutParams hexEditLp = new LinearLayout.LayoutParams(
                dp(themedCtx, 108f), ViewGroup.LayoutParams.WRAP_CONTENT);
        hexEditLp.setMarginStart(dp(themedCtx, 8f));
        hexRow.addView(hexInput, hexEditLp);
        root.addView(hexRow, hexRowLp);

        // 6. Action Buttons (Reset | Cancel | Apply)
        LinearLayout actionsRow = new LinearLayout(themedCtx);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actLp.topMargin = dp(themedCtx, 16f);

        TextView btnReset = createPillTab(themedCtx, "Reset");
        TextView btnCancel = createPillTab(themedCtx, context.getString(R.string.cancel));
        TextView btnApply = createPillTab(themedCtx, "Apply");

        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsRow.addView(btnReset, resetLp);

        actionsRow.addView(new View(themedCtx), new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLp.setMarginEnd(dp(themedCtx, 8f));
        actionsRow.addView(btnCancel, cancelLp);
        actionsRow.addView(btnApply);
        root.addView(actionsRow, actLp);

        // State synchronization logic
        final Runnable refreshAll = () -> {
            updatingControls[0] = true;
            AppThemePalette p = AppThemePalette.buildCustomPalette(spec);

            // Preview backdrop
            GradientDrawable bgGrad = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{ p.background, AppThemePalette.blendColors(p.background, p.secondary, 0.55f) });
            bgGrad.setCornerRadius(dp(themedCtx, 18f));
            bgGrad.setStroke(dp(themedCtx, 1f), p.getCardRimColor());
            previewBackdrop.setBackground(bgGrad);

            // Preview portal ring & particles
            GradientDrawable ringBg = new GradientDrawable();
            ringBg.setShape(GradientDrawable.OVAL);
            ringBg.setColor(Color.TRANSPARENT);
            ringBg.setStroke(dp(themedCtx, 3f), p.ringColor);
            previewRing.setBackground(ringBg);

            GradientDrawable pDot = new GradientDrawable();
            pDot.setShape(GradientDrawable.OVAL);
            pDot.setColor(p.particleColor);
            previewParticleDot1.setBackground(pDot);
            previewParticleDot2.setBackground(pDot);

            // Preview frosted card
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setShape(GradientDrawable.RECTANGLE);
            cardBg.setCornerRadius(dp(themedCtx, 14f));
            cardBg.setColor(p.getFrostedCardTint(0x64000000));
            cardBg.setStroke(dp(themedCtx, 1f), p.getCardRimColor());
            previewCard.setBackground(cardBg);
            previewTitle.setTextColor(p.getPrimaryTextColor());
            previewSub.setTextColor(p.getSecondaryTextColor());

            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setShape(GradientDrawable.RECTANGLE);
            pillBg.setCornerRadius(dp(themedCtx, 999f));
            pillBg.setColor(p.accent);
            previewPill.setBackground(pillBg);
            previewPill.setTextColor(Color.WHITE);

            // Dark / Light base buttons
            stylePillButton(themedCtx, btnDarkBase, !spec.isLight, p.highlight);
            stylePillButton(themedCtx, btnLightBase, spec.isLight, p.highlight);
            stylePillButton(themedCtx, btnHarmonize, false, p.highlight);

            // Channel tabs
            for (int i = 0; i < CHANNEL_LABELS.length; i++) {
                int cVal = getChannelColor(spec, i);
                GradientDrawable dotBg = new GradientDrawable();
                dotBg.setShape(GradientDrawable.OVAL);
                dotBg.setColor(cVal);
                dotBg.setStroke(dp(themedCtx, 1f), 0xAAFFFFFF);
                chDots[i].setBackground(dotBg);

                boolean sel = (i == activeChannel[0]);
                GradientDrawable tabBg = new GradientDrawable();
                tabBg.setShape(GradientDrawable.RECTANGLE);
                tabBg.setCornerRadius(dp(themedCtx, 12f));
                tabBg.setColor(sel ? AppThemePalette.withAlpha(p.highlight, 0.28f) : 0x16FFFFFF);
                tabBg.setStroke(dp(themedCtx, sel ? 1.4f : 1f), sel ? Color.WHITE : 0x33FFFFFF);
                chTabs[i].setBackground(tabBg);
                chTexts[i].setTextColor(sel ? Color.WHITE : 0xB8FFFFFF);
            }

            // RGB sliders & Hex
            int cur = getChannelColor(spec, activeChannel[0]);
            int r = Color.red(cur);
            int g = Color.green(cur);
            int b = Color.blue(cur);
            rgbBars[0].setProgress(r);
            rgbBars[1].setProgress(g);
            rgbBars[2].setProgress(b);
            rgbVals[0].setText(String.valueOf(r));
            rgbVals[1].setText(String.valueOf(g));
            rgbVals[2].setText(String.valueOf(b));

            String hexStr = String.format(Locale.US, "%02X%02X%02X", r, g, b);
            if (!hexStr.equalsIgnoreCase(hexInput.getText().toString())) {
                hexInput.setText(hexStr);
                hexInput.setSelection(hexStr.length());
            }

            stylePillButton(themedCtx, btnReset, false, 0xFFFF5252);
            stylePillButton(themedCtx, btnCancel, false, p.highlight);
            stylePillButton(themedCtx, btnApply, true, p.accent);

            updatingControls[0] = false;
        };

        // Listeners
        btnDarkBase.setOnClickListener(v -> {
            if (spec.isLight) {
                AppThemePalette.CustomPaletteSpec h = AppThemePalette.harmonizeFromAccent(spec.accent, false);
                copySpec(spec, h);
                refreshAll.run();
            }
        });
        btnLightBase.setOnClickListener(v -> {
            if (!spec.isLight) {
                AppThemePalette.CustomPaletteSpec h = AppThemePalette.harmonizeFromAccent(spec.accent, true);
                copySpec(spec, h);
                refreshAll.run();
            }
        });
        btnHarmonize.setOnClickListener(v -> {
            AppThemePalette.CustomPaletteSpec h = AppThemePalette.harmonizeFromAccent(spec.accent, spec.isLight);
            copySpec(spec, h);
            refreshAll.run();
        });

        for (int i = 0; i < CHANNEL_LABELS.length; i++) {
            final int ch = i;
            chTabs[i].setOnClickListener(v -> {
                activeChannel[0] = ch;
                refreshAll.run();
            });
        }

        for (int i = 0; i < QUICK_SWATCHES.length; i++) {
            final int swColor = QUICK_SWATCHES[i];
            swatchViews[i].setOnClickListener(v -> {
                setChannelColor(spec, activeChannel[0], swColor);
                refreshAll.run();
            });
        }

        SeekBar.OnSeekBarChangeListener rgbListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || updatingControls[0]) {
                    return;
                }
                int color = Color.rgb(rgbBars[0].getProgress(), rgbBars[1].getProgress(), rgbBars[2].getProgress());
                setChannelColor(spec, activeChannel[0], color);
                refreshAll.run();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        for (SeekBar sb : rgbBars) {
            sb.setOnSeekBarChangeListener(rgbListener);
        }

        hexInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (updatingControls[0]) {
                    return;
                }
                String txt = s.toString().trim();
                if (txt.length() == 6) {
                    try {
                        int parsed = Color.parseColor("#" + txt);
                        setChannelColor(spec, activeChannel[0], parsed);
                        refreshAll.run();
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        });

        btnReset.setOnClickListener(v -> {
            copySpec(spec, AppThemePalette.harmonizeFromAccent(0xFF7C3AED, false));
            refreshAll.run();
        });
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnApply.setOnClickListener(v -> {
            AppThemePalette.saveCustomSpec(context, spec);
            dialog.dismiss();
            if (onApplied != null) {
                onApplied.run();
            }
        });

        refreshAll.run();

        scrollView.addView(root);
        dialog.setContentView(scrollView);
        UiHelper.styleDialogSquircle(dialog);
        dialog.show();
        UiHelper.styleDialogSquircle(dialog);
    }

    private static void copySpec(AppThemePalette.CustomPaletteSpec dst, AppThemePalette.CustomPaletteSpec src) {
        dst.isLight = src.isLight;
        dst.background = src.background;
        dst.surface = src.surface;
        dst.accent = src.accent;
        dst.highlight = src.highlight;
        dst.particleColor = src.particleColor;
        dst.ringColor = src.ringColor;
    }

    private static int getChannelColor(AppThemePalette.CustomPaletteSpec spec, int ch) {
        switch (ch) {
            case CH_BG: return spec.background;
            case CH_SURFACE: return spec.surface;
            case CH_HIGHLIGHT: return spec.highlight;
            case CH_PARTICLE: return spec.particleColor;
            case CH_RING: return spec.ringColor;
            case CH_ACCENT:
            default:
                return spec.accent;
        }
    }

    private static void setChannelColor(AppThemePalette.CustomPaletteSpec spec, int ch, int color) {
        int opaque = color | 0xFF000000;
        switch (ch) {
            case CH_BG: spec.background = opaque; break;
            case CH_SURFACE: spec.surface = opaque; break;
            case CH_HIGHLIGHT: spec.highlight = opaque; break;
            case CH_PARTICLE: spec.particleColor = opaque; break;
            case CH_RING: spec.ringColor = opaque; break;
            case CH_ACCENT:
            default:
                spec.accent = opaque;
                break;
        }
    }

    private static TextView createPillTab(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(ctx, 12f), dp(ctx, 8f), dp(ctx, 12f), dp(ctx, 8f));
        tv.setClickable(true);
        tv.setFocusable(true);
        return tv;
    }

    private static void stylePillButton(Context ctx, TextView tv, boolean active, int accentRgb) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(ctx, 999f));
        if (active) {
            bg.setColor(AppThemePalette.withAlpha(accentRgb, 0.36f));
            bg.setStroke(dp(ctx, 1.3f), Color.WHITE);
            tv.setTextColor(Color.WHITE);
        } else {
            bg.setColor(0x1EFFFFFF);
            bg.setStroke(dp(ctx, 1f), 0x44FFFFFF);
            tv.setTextColor(0xD8FFFFFF);
        }
        tv.setBackground(bg);
    }

    private static int dp(Context ctx, float dpVal) {
        return Math.max(1, Math.round(UiHelper.dpToPx(ctx, dpVal)));
    }

    private static int dp(float dpVal, Context ctx) {
        return dp(ctx, dpVal);
    }
}
