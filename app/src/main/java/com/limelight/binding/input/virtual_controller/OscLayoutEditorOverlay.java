package com.limelight.binding.input.virtual_controller;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.limelight.R;
import com.limelight.ui.AppThemePalette;
import com.limelight.utils.UiHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared precision On-Screen Controller layout editor used identically by both the standalone
 * Settings editor ({@link com.limelight.OscLayoutEditorActivity}) and live mid-stream editing
 * ({@link com.limelight.Game}).
 *
 * Features select-first editing (prevents accidental nudges), direct drag-to-move, bottom-right
 * scale handle (respecting aspect-locked controls), magnetic alignment guides, optional grid snap,
 * live X/Y/W/H readout, fine 1px nudge controls, scale steppers, and per-button show/hide.
 */
public class OscLayoutEditorOverlay {

    private enum SnapMode {
        GUIDES,
        GRID,
        OFF
    }

    private enum DragMode {
        NONE,
        MOVE,
        RESIZE
    }

    private static final int GUIDE_COLOR = 0xE038BDF8;
    private static final int GRID_COLOR = 0x16FFFFFF;
    private static final int HIDDEN_STROKE_COLOR = 0x70FFFFFF;
    private static final int RESET_COLOR = 0xFFFF5252;
    private static final PathInterpolator SPRING_EASE = new PathInterpolator(0.22f, 1f, 0.36f, 1f);

    private final Context context;
    private final FrameLayout hostContainer;
    private final VirtualController virtualController;
    private final Runnable onClosedCallback;
    private final AppThemePalette palette;
    private final Handler repeatHandler = new Handler(Looper.getMainLooper());

    private final FrameLayout overlayRoot;
    private final PrecisionCanvasView canvasView;
    private final LinearLayout toolbarCard;

    private TextView btnSnap;
    private TextView btnShowHidden;
    private TextView btnFlipToolbar;
    private TextView btnReset;
    private TextView btnSave;
    private TextView btnClose;

    private LinearLayout inspectorRow;
    private TextView hintLabel;
    private TextView readoutLabel;
    private TextView btnToggleSelectedVis;

    private SnapMode snapMode = SnapMode.GUIDES;
    private boolean showHiddenButtons = false;
    private boolean toolbarAtBottom = false;
    private boolean hasUnsavedChanges = false;
    private boolean attached = false;

    @Nullable
    private VirtualControllerElement selectedElement = null;

    private final List<Float> activeGuidesX = new ArrayList<>();
    private final List<Float> activeGuidesY = new ArrayList<>();

    public OscLayoutEditorOverlay(
            Context context,
            FrameLayout hostContainer,
            VirtualController virtualController,
            @Nullable Runnable onClosedCallback) {
        this.context = context;
        this.hostContainer = hostContainer;
        this.virtualController = virtualController;
        this.onClosedCallback = onClosedCallback;
        this.palette = AppThemePalette.getCurrent(context);

        this.overlayRoot = new FrameLayout(context);
        this.overlayRoot.setClipChildren(false);
        this.overlayRoot.setClipToPadding(false);

        this.canvasView = new PrecisionCanvasView(context);
        this.overlayRoot.addView(this.canvasView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        this.toolbarCard = buildToolbar();
        FrameLayout.LayoutParams tbLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tbLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        tbLp.topMargin = dp(12f);
        tbLp.bottomMargin = dp(12f);
        tbLp.leftMargin = dp(12f);
        tbLp.rightMargin = dp(12f);
        this.overlayRoot.addView(this.toolbarCard, tbLp);
    }

    public void attach() {
        if (attached) {
            return;
        }
        attached = true;
        virtualController.setConfigureButtonVisible(false);
        virtualController.setControllerMode(VirtualController.ControllerMode.Active);
        if (!virtualController.isVisible()) {
            virtualController.show();
        }
        applyHiddenElementsVisibility();

        hostContainer.addView(overlayRoot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        toolbarCard.setAlpha(0f);
        toolbarCard.setTranslationY(-dp(16f));
        toolbarCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200L)
                .setInterpolator(SPRING_EASE)
                .start();

        refreshToolbarState();
        refreshInspectorState();
    }

    public void detach() {
        if (!attached) {
            return;
        }
        attached = false;
        repeatHandler.removeCallbacksAndMessages(null);
        selectedElement = null;
        activeGuidesX.clear();
        activeGuidesY.clear();
        restoreElementAlphasAndVisibility();
        hostContainer.removeView(overlayRoot);
    }

    public boolean isAttached() {
        return attached;
    }

    public boolean hasUnsavedChanges() {
        return hasUnsavedChanges;
    }

    public void refreshAfterControllerRemount() {
        if (!attached) {
            return;
        }
        selectedElement = null;
        activeGuidesX.clear();
        activeGuidesY.clear();
        applyHiddenElementsVisibility();
        // Keep overlay above newly added VirtualController views
        overlayRoot.bringToFront();
        refreshInspectorState();
        canvasView.invalidate();
    }

    public void requestClose() {
        if (!attached) {
            return;
        }
        if (!hasUnsavedChanges) {
            detach();
            if (onClosedCallback != null) {
                onClosedCallback.run();
            }
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.osc_editor_unsaved_title)
                .setMessage(R.string.osc_editor_unsaved_msg)
                .setPositiveButton(R.string.osc_editor_save, (d, which) -> saveAndClose())
                .setNegativeButton(R.string.osc_editor_discard, (d, which) -> discardAndClose())
                .setNeutralButton(R.string.cancel, null)
                .create();
        UiHelper.styleDialogSquircle(dialog);
        dialog.show();
        UiHelper.styleDialogSquircle(dialog);
    }

    private void saveAndClose() {
        VirtualControllerConfigurationLoader.saveProfile(virtualController, context);
        hasUnsavedChanges = false;
        Toast.makeText(context, R.string.osc_editor_saved, Toast.LENGTH_SHORT).show();
        detach();
        if (onClosedCallback != null) {
            onClosedCallback.run();
        }
    }

    private void discardAndClose() {
        hasUnsavedChanges = false;
        detach();
        virtualController.refreshLayout();
        virtualController.show();
        if (onClosedCallback != null) {
            onClosedCallback.run();
        }
    }

    private void confirmResetLayout() {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.dialog_title_reset_osc)
                .setMessage(R.string.dialog_text_reset_osc)
                .setPositiveButton(R.string.yes, (d, which) -> {
                    context.getSharedPreferences(VirtualControllerConfigurationLoader.OSC_PREFERENCE, Context.MODE_PRIVATE)
                            .edit()
                            .clear()
                            .apply();
                    hasUnsavedChanges = false;
                    selectedElement = null;
                    virtualController.refreshLayout();
                    virtualController.setConfigureButtonVisible(false);
                    virtualController.show();
                    refreshAfterControllerRemount();
                    Toast.makeText(context, R.string.toast_reset_osc_success, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.no, null)
                .create();
        UiHelper.styleDialogSquircle(dialog);
        dialog.show();
        UiHelper.styleDialogSquircle(dialog);
    }

    private LinearLayout buildToolbar() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(10f), dp(8f), dp(10f), dp(8f));
        card.setClickable(true);
        card.setElevation(dp(10f));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setCornerRadius(dp(22f));
        cardBg.setColor(0xD40E131F);
        int outlinePx = Math.round(UiHelper.getCardOutlineWidthPx(context, 1f));
        if (outlinePx > 0) {
            cardBg.setStroke(outlinePx, 0x44FFFFFF);
        }
        card.setBackground(cardBg);

        // Row 1: Primary editor actions (scrollable horizontally on very narrow portrait screens)
        HorizontalScrollView row1Scroll = new HorizontalScrollView(context);
        row1Scroll.setHorizontalScrollBarEnabled(false);

        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        btnSnap = createChip(getString(R.string.osc_editor_snap_guides));
        btnSnap.setOnClickListener(v -> {
            if (snapMode == SnapMode.GUIDES) {
                snapMode = SnapMode.GRID;
            } else if (snapMode == SnapMode.GRID) {
                snapMode = SnapMode.OFF;
            } else {
                snapMode = SnapMode.GUIDES;
            }
            refreshToolbarState();
            canvasView.invalidate();
        });

        btnShowHidden = createChip(getString(R.string.osc_editor_show_hidden));
        btnShowHidden.setOnClickListener(v -> {
            showHiddenButtons = !showHiddenButtons;
            applyHiddenElementsVisibility();
            if (!showHiddenButtons && selectedElement != null && !selectedElement.enabled) {
                selectedElement = null;
                refreshInspectorState();
            }
            refreshToolbarState();
            canvasView.invalidate();
        });

        btnFlipToolbar = createChip("\u21C5");
        btnFlipToolbar.setOnClickListener(v -> toggleToolbarPosition());

        btnReset = createChip(getString(R.string.osc_editor_reset));
        btnReset.setOnClickListener(v -> confirmResetLayout());

        btnSave = createChip(getString(R.string.osc_editor_save));
        btnSave.setOnClickListener(v -> saveAndClose());

        btnClose = createChip("\u2715");
        btnClose.setOnClickListener(v -> requestClose());

        addChipWithGap(row1, btnSnap, 0);
        addChipWithGap(row1, btnShowHidden, dp(6f));
        addChipWithGap(row1, btnFlipToolbar, dp(6f));
        addChipWithGap(row1, btnReset, dp(6f));
        addChipWithGap(row1, btnSave, dp(6f));
        addChipWithGap(row1, btnClose, dp(6f));

        row1Scroll.addView(row1);
        card.addView(row1Scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Divider between primary toolbar and contextual inspector
        View divider = new View(context);
        divider.setBackgroundColor(0x20FFFFFF);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1f)));
        divLp.topMargin = dp(7f);
        divLp.bottomMargin = dp(6f);
        card.addView(divider, divLp);

        // Idle hint when no element is selected
        hintLabel = new TextView(context);
        hintLabel.setText(R.string.osc_editor_select_hint);
        hintLabel.setTextColor(0xA6FFFFFF);
        hintLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        hintLabel.setGravity(Gravity.CENTER);
        hintLabel.setPadding(dp(6f), dp(2f), dp(6f), dp(2f));
        card.addView(hintLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Row 2: Contextual Selected-Button Precision Inspector
        HorizontalScrollView inspectorScroll = new HorizontalScrollView(context);
        inspectorScroll.setHorizontalScrollBarEnabled(false);

        inspectorRow = new LinearLayout(context);
        inspectorRow.setOrientation(LinearLayout.HORIZONTAL);
        inspectorRow.setGravity(Gravity.CENTER_VERTICAL);
        inspectorRow.setVisibility(View.GONE);

        readoutLabel = new TextView(context);
        readoutLabel.setTextColor(palette.getNeonAccent());
        readoutLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        readoutLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        readoutLabel.setPadding(dp(4f), dp(2f), dp(8f), dp(2f));
        inspectorRow.addView(readoutLabel);

        addRepeatablePrecisionButton(inspectorRow, "\u25C0", () -> nudgeSelected(-1, 0));
        addRepeatablePrecisionButton(inspectorRow, "\u25B2", () -> nudgeSelected(0, -1));
        addRepeatablePrecisionButton(inspectorRow, "\u25BC", () -> nudgeSelected(0, 1));
        addRepeatablePrecisionButton(inspectorRow, "\u25B6", () -> nudgeSelected(1, 0));

        View spacer = new View(context);
        inspectorRow.addView(spacer, new LinearLayout.LayoutParams(dp(6f), 1));

        addRepeatablePrecisionButton(inspectorRow, "\u2212", () -> scaleSelected(-4));
        addRepeatablePrecisionButton(inspectorRow, "+", () -> scaleSelected(4));

        btnToggleSelectedVis = createChip(getString(R.string.osc_editor_hide_btn));
        btnToggleSelectedVis.setOnClickListener(v -> toggleSelectedEnabled());
        addChipWithGap(inspectorRow, btnToggleSelectedVis, dp(6f));

        inspectorScroll.addView(inspectorRow);
        card.addView(inspectorScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return card;
    }

    private TextView createChip(String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(11f), dp(6f), dp(11f), dp(6f));
        tv.setClickable(true);
        tv.setFocusable(true);
        return tv;
    }

    private void addChipWithGap(LinearLayout row, View chip, int startMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(startMargin);
        row.addView(chip, lp);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addRepeatablePrecisionButton(LinearLayout row, String symbol, Runnable action) {
        TextView btn = new TextView(context);
        btn.setText(symbol);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        int size = dp(30f);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(9f));
        bg.setColor(0x22FFFFFF);
        bg.setStroke(Math.max(1, dp(1f)), 0x42FFFFFF);
        btn.setBackground(bg);

        final Runnable repeatRunnable = new Runnable() {
            @Override
            public void run() {
                action.run();
                repeatHandler.postDelayed(this, 65L);
            }
        };

        btn.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setAlpha(0.7f);
                    action.run();
                    repeatHandler.removeCallbacks(repeatRunnable);
                    repeatHandler.postDelayed(repeatRunnable, 280L);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setAlpha(1f);
                    repeatHandler.removeCallbacks(repeatRunnable);
                    return true;
                default:
                    return false;
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginStart(dp(4f));
        row.addView(btn, lp);
    }

    private void toggleToolbarPosition() {
        toolbarAtBottom = !toolbarAtBottom;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) toolbarCard.getLayoutParams();
        lp.gravity = (toolbarAtBottom ? Gravity.BOTTOM : Gravity.TOP) | Gravity.CENTER_HORIZONTAL;
        toolbarCard.setLayoutParams(lp);
        toolbarCard.setAlpha(0.7f);
        toolbarCard.animate().alpha(1f).setDuration(150L).start();
    }

    private void refreshToolbarState() {
        if (snapMode == SnapMode.GUIDES) {
            btnSnap.setText(R.string.osc_editor_snap_guides);
            styleChip(btnSnap, true, GUIDE_COLOR);
        } else if (snapMode == SnapMode.GRID) {
            btnSnap.setText(R.string.osc_editor_snap_grid);
            styleChip(btnSnap, true, palette.highlight);
        } else {
            btnSnap.setText(R.string.osc_editor_snap_off);
            styleChip(btnSnap, false, palette.highlight);
        }

        styleChip(btnShowHidden, showHiddenButtons, 0xFF30D158);
        styleChip(btnFlipToolbar, false, palette.highlight);

        GradientDrawable resetBg = new GradientDrawable();
        resetBg.setShape(GradientDrawable.RECTANGLE);
        resetBg.setCornerRadius(dp(999f));
        resetBg.setColor(0x22FF5252);
        resetBg.setStroke(Math.max(1, dp(1f)), 0x66FF5252);
        btnReset.setBackground(resetBg);
        btnReset.setTextColor(0xFFFF8A8A);

        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setShape(GradientDrawable.RECTANGLE);
        saveBg.setCornerRadius(dp(999f));
        saveBg.setColor(AppThemePalette.withAlpha(palette.highlight, 0.34f));
        saveBg.setStroke(Math.max(1, dp(1.2f)), AppThemePalette.withAlpha(Color.WHITE, 0.85f));
        btnSave.setBackground(saveBg);
        btnSave.setTextColor(Color.WHITE);

        styleChip(btnClose, false, palette.highlight);
    }

    private void styleChip(TextView chip, boolean active, int accentColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(999f));
        if (active) {
            bg.setColor(AppThemePalette.withAlpha(accentColor, 0.28f));
            bg.setStroke(Math.max(1, dp(1.2f)), AppThemePalette.withAlpha(accentColor, 0.92f));
            chip.setTextColor(Color.WHITE);
        } else {
            bg.setColor(0x18FFFFFF);
            bg.setStroke(Math.max(1, dp(1f)), 0x35FFFFFF);
            chip.setTextColor(0xCCFFFFFF);
        }
        chip.setBackground(bg);
    }

    private void refreshInspectorState() {
        if (selectedElement == null) {
            inspectorRow.setVisibility(View.GONE);
            hintLabel.setVisibility(View.VISIBLE);
            return;
        }
        hintLabel.setVisibility(View.GONE);
        inspectorRow.setVisibility(View.VISIBLE);

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) selectedElement.getLayoutParams();
        int x = lp != null ? lp.leftMargin : Math.round(selectedElement.getX());
        int y = lp != null ? lp.topMargin : Math.round(selectedElement.getY());
        int w = lp != null && lp.width > 0 ? lp.width : selectedElement.getWidth();
        int h = lp != null && lp.height > 0 ? lp.height : selectedElement.getHeight();
        readoutLabel.setText(selectedElement.getDisplayLabel() + "  " + x + "," + y + "  " + w + "\u00D7" + h);

        boolean isEnabled = selectedElement.enabled;
        btnToggleSelectedVis.setText(isEnabled ? R.string.osc_editor_hide_btn : R.string.osc_editor_show_btn);
        styleChip(btnToggleSelectedVis, !isEnabled, isEnabled ? RESET_COLOR : 0xFF30D158);
    }

    private void applyHiddenElementsVisibility() {
        for (VirtualControllerElement el : virtualController.getElements()) {
            if (el.enabled) {
                el.setVisibility(View.VISIBLE);
                el.setAlpha(1.0f);
            } else {
                el.setVisibility(showHiddenButtons ? View.VISIBLE : View.GONE);
                el.setAlpha(showHiddenButtons ? 0.30f : 1.0f);
            }
        }
    }

    private void restoreElementAlphasAndVisibility() {
        for (VirtualControllerElement el : virtualController.getElements()) {
            el.setAlpha(1.0f);
            el.setVisibility((virtualController.isVisible() && el.enabled) ? View.VISIBLE : View.GONE);
        }
    }

    private void toggleSelectedEnabled() {
        if (selectedElement == null) {
            return;
        }
        selectedElement.enabled = !selectedElement.enabled;
        hasUnsavedChanges = true;
        if (!selectedElement.enabled && !showHiddenButtons) {
            showHiddenButtons = true;
            refreshToolbarState();
        }
        applyHiddenElementsVisibility();
        refreshInspectorState();
        canvasView.invalidate();
    }

    private void nudgeSelected(int dx, int dy) {
        if (selectedElement == null) {
            return;
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) selectedElement.getLayoutParams();
        if (lp == null) {
            return;
        }
        int parentW = Math.max(1, canvasView.getWidth());
        int parentH = Math.max(1, canvasView.getHeight());
        int w = lp.width > 0 ? lp.width : selectedElement.getWidth();
        int h = lp.height > 0 ? lp.height : selectedElement.getHeight();
        int newX = Math.max(0, Math.min(Math.max(0, parentW - w), lp.leftMargin + dx));
        int newY = Math.max(0, Math.min(Math.max(0, parentH - h), lp.topMargin + dy));
        selectedElement.applyBoundsFromEditor(newX, newY, w, h);
        hasUnsavedChanges = true;
        refreshInspectorState();
        canvasView.invalidate();
    }

    private void scaleSelected(int deltaPx) {
        if (selectedElement == null) {
            return;
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) selectedElement.getLayoutParams();
        if (lp == null) {
            return;
        }
        int parentW = Math.max(1, canvasView.getWidth());
        int parentH = Math.max(1, canvasView.getHeight());
        int minDim = Math.max(40, (int) (context.getResources().getDisplayMetrics().heightPixels * 0.04f));
        int curW = lp.width > 0 ? lp.width : selectedElement.getWidth();
        int curH = lp.height > 0 ? lp.height : selectedElement.getHeight();
        float centerX = lp.leftMargin + curW * 0.5f;
        float centerY = lp.topMargin + curH * 0.5f;

        float ar = selectedElement.getLockedAspectRatio();
        int newW;
        int newH;
        if (ar > 0f) {
            float minW = Math.max(minDim, minDim * ar);
            float maxW = Math.max(minW, Math.min(parentW, parentH * ar));
            float targetW = Math.max(minW, Math.min(maxW, curW + deltaPx));
            newW = Math.round(targetW);
            newH = Math.max(minDim, Math.round(targetW / ar));
        } else {
            newW = Math.max(minDim, Math.min(parentW, curW + deltaPx));
            newH = Math.max(minDim, Math.min(parentH, curH + deltaPx));
        }

        int newL = Math.max(0, Math.min(Math.max(0, parentW - newW), Math.round(centerX - newW * 0.5f)));
        int newT = Math.max(0, Math.min(Math.max(0, parentH - newH), Math.round(centerY - newH * 0.5f)));
        selectedElement.applyBoundsFromEditor(newL, newT, newW, newH);
        hasUnsavedChanges = true;
        refreshInspectorState();
        canvasView.invalidate();
    }

    private int dp(float dpValue) {
        return Math.max(1, Math.round(UiHelper.dpToPx(context, dpValue)));
    }

    private String getString(int resId) {
        return context.getString(resId);
    }

    /**
     * Fullscreen transparent touch & drawing layer handling select-first picking, direct move,
     * bottom-right scale handle dragging, magnetic alignment guides, and optional grid snap.
     */
    private final class PrecisionCanvasView extends View {
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint guideDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint selectFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint selectStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hiddenStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handleFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handleRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF tempRect = new RectF();

        private DragMode dragMode = DragMode.NONE;
        private float downX = 0f;
        private float downY = 0f;
        private int startLeft = 0;
        private int startTop = 0;
        private int startWidth = 0;
        private int startHeight = 0;
        private float startCenterX = 0f;
        private float startCenterY = 0f;

        PrecisionCanvasView(Context ctx) {
            super(ctx);
            gridPaint.setColor(GRID_COLOR);
            gridPaint.setStrokeWidth(Math.max(1f, UiHelper.dpToPx(ctx, 0.75f)));

            guidePaint.setColor(GUIDE_COLOR);
            guidePaint.setStrokeWidth(Math.max(1f, UiHelper.dpToPx(ctx, 1.25f)));

            guideDotPaint.setColor(GUIDE_COLOR);
            guideDotPaint.setStyle(Paint.Style.FILL);

            selectFillPaint.setColor(AppThemePalette.withAlpha(palette.highlight, 0.14f));
            selectFillPaint.setStyle(Paint.Style.FILL);

            selectStrokePaint.setColor(palette.getNeonAccent());
            selectStrokePaint.setStyle(Paint.Style.STROKE);
            selectStrokePaint.setStrokeWidth(Math.max(2f, UiHelper.dpToPx(ctx, 2f)));

            hiddenStrokePaint.setColor(HIDDEN_STROKE_COLOR);
            hiddenStrokePaint.setStyle(Paint.Style.STROKE);
            hiddenStrokePaint.setStrokeWidth(Math.max(1f, UiHelper.dpToPx(ctx, 1.2f)));
            hiddenStrokePaint.setPathEffect(new DashPathEffect(new float[]{dp(5f), dp(4f)}, 0f));

            handleFillPaint.setColor(Color.WHITE);
            handleFillPaint.setStyle(Paint.Style.FILL);

            handleRingPaint.setColor(palette.getNeonAccent());
            handleRingPaint.setStyle(Paint.Style.STROKE);
            handleRingPaint.setStrokeWidth(Math.max(2f, UiHelper.dpToPx(ctx, 2.2f)));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }

            // 1. Subtle grid overlay when Grid Snap is active
            if (snapMode == SnapMode.GRID) {
                float step = UiHelper.dpToPx(context, 24f);
                for (float x = step; x < w; x += step) {
                    canvas.drawLine(x, 0f, x, h, gridPaint);
                }
                for (float y = step; y < h; y += step) {
                    canvas.drawLine(0f, y, w, y, gridPaint);
                }
            }

            // 2. Dashed indicator around disabled/hidden buttons when Show Hidden is active
            if (showHiddenButtons) {
                List<VirtualControllerElement> elements = virtualController.getElements();
                for (int i = 0; i < elements.size(); i++) {
                    VirtualControllerElement el = elements.get(i);
                    if (!el.enabled && el != selectedElement) {
                        fillElementRect(el, tempRect);
                        canvas.drawRoundRect(tempRect, dp(10f), dp(10f), hiddenStrokePaint);
                    }
                }
            }

            // 3. Active magnetic alignment guide lines
            for (int i = 0; i < activeGuidesX.size(); i++) {
                float gx = activeGuidesX.get(i);
                canvas.drawLine(gx, 0f, gx, h, guidePaint);
            }
            for (int i = 0; i < activeGuidesY.size(); i++) {
                float gy = activeGuidesY.get(i);
                canvas.drawLine(0f, gy, w, gy, guidePaint);
            }

            // 4. Selected element bounding box + bottom-right scale handle
            if (selectedElement != null) {
                fillElementRect(selectedElement, tempRect);
                float cornerR = dp(12f);
                canvas.drawRoundRect(tempRect, cornerR, cornerR, selectFillPaint);
                canvas.drawRoundRect(tempRect, cornerR, cornerR, selectStrokePaint);

                // Center crosshair dot on selected button
                canvas.drawCircle(tempRect.centerX(), tempRect.centerY(), dp(2.8f), guideDotPaint);

                // Bottom-right circular scale handle
                float handleR = dp(10f);
                float hx = tempRect.right;
                float hy = tempRect.bottom;
                canvas.drawCircle(hx, hy, handleR, handleFillPaint);
                canvas.drawCircle(hx, hy, handleR, handleRingPaint);
            }
        }

        private void fillElementRect(VirtualControllerElement el, RectF out) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) el.getLayoutParams();
            float left = lp != null ? lp.leftMargin : el.getX();
            float top = lp != null ? lp.topMargin : el.getY();
            float width = (lp != null && lp.width > 0) ? lp.width : el.getWidth();
            float height = (lp != null && lp.height > 0) ? lp.height : el.getHeight();
            out.set(left, top, left + width, top + height);
        }

        @Nullable
        private VirtualControllerElement hitTestElement(float x, float y) {
            VirtualControllerElement best = null;
            float bestArea = Float.MAX_VALUE;
            List<VirtualControllerElement> elements = virtualController.getElements();
            for (int i = 0; i < elements.size(); i++) {
                VirtualControllerElement el = elements.get(i);
                if (!el.enabled && !showHiddenButtons) {
                    continue;
                }
                fillElementRect(el, tempRect);
                if (tempRect.contains(x, y)) {
                    float area = tempRect.width() * tempRect.height();
                    if (area < bestArea) {
                        bestArea = area;
                        best = el;
                    }
                }
            }
            return best;
        }

        @Override
        @SuppressLint("ClickableViewAccessibility")
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    activeGuidesX.clear();
                    activeGuidesY.clear();
                    downX = x;
                    downY = y;
                    dragMode = DragMode.NONE;

                    if (selectedElement != null) {
                        fillElementRect(selectedElement, tempRect);
                        float handleHitRadius = UiHelper.dpToPx(context, 30f);
                        float distToHandle = (float) Math.hypot(x - tempRect.right, y - tempRect.bottom);
                        if (distToHandle <= handleHitRadius) {
                            captureStartBounds(selectedElement);
                            dragMode = DragMode.RESIZE;
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            return true;
                        }
                        if (tempRect.contains(x, y)) {
                            captureStartBounds(selectedElement);
                            dragMode = DragMode.MOVE;
                            return true;
                        }
                    }

                    // Select-first: tapping an unselected button selects it cleanly without nudging
                    VirtualControllerElement tapped = hitTestElement(x, y);
                    if (tapped != selectedElement) {
                        selectedElement = tapped;
                        if (tapped != null) {
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        }
                        refreshInspectorState();
                        invalidate();
                    }
                    return true;
                }

                case MotionEvent.ACTION_MOVE: {
                    if (selectedElement == null || dragMode == DragMode.NONE) {
                        return true;
                    }
                    if (dragMode == DragMode.MOVE) {
                        handlePrecisionMove(x - downX, y - downY);
                    } else if (dragMode == DragMode.RESIZE) {
                        handlePrecisionResize(x, y);
                    }
                    return true;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    dragMode = DragMode.NONE;
                    activeGuidesX.clear();
                    activeGuidesY.clear();
                    invalidate();
                    return true;
                }
                default:
                    return true;
            }
        }

        private void captureStartBounds(VirtualControllerElement el) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) el.getLayoutParams();
            startLeft = lp != null ? lp.leftMargin : Math.round(el.getX());
            startTop = lp != null ? lp.topMargin : Math.round(el.getY());
            startWidth = (lp != null && lp.width > 0) ? lp.width : el.getWidth();
            startHeight = (lp != null && lp.height > 0) ? lp.height : el.getHeight();
            startCenterX = startLeft + startWidth * 0.5f;
            startCenterY = startTop + startHeight * 0.5f;
        }

        private void handlePrecisionMove(float dx, float dy) {
            if (selectedElement == null) {
                return;
            }
            int parentW = Math.max(1, getWidth());
            int parentH = Math.max(1, getHeight());
            int maxL = Math.max(0, parentW - startWidth);
            int maxT = Math.max(0, parentH - startHeight);

            float rawL = Math.max(0f, Math.min(maxL, startLeft + dx));
            float rawT = Math.max(0f, Math.min(maxT, startTop + dy));

            activeGuidesX.clear();
            activeGuidesY.clear();

            if (snapMode == SnapMode.GRID) {
                float gridStep = UiHelper.dpToPx(context, 12f);
                rawL = Math.round(rawL / gridStep) * gridStep;
                rawT = Math.round(rawT / gridStep) * gridStep;
            }

            if (snapMode == SnapMode.GUIDES || snapMode == SnapMode.GRID) {
                float snapThresh = UiHelper.dpToPx(context, 9f);
                float[] snappedX = snapAxisPosition(rawL, startWidth, parentW, true, snapThresh);
                float[] snappedY = snapAxisPosition(rawT, startHeight, parentH, false, snapThresh);
                rawL = snappedX[0];
                if (snappedX[1] >= 0f) {
                    activeGuidesX.add(snappedX[1]);
                }
                rawT = snappedY[0];
                if (snappedY[1] >= 0f) {
                    activeGuidesY.add(snappedY[1]);
                }
            }

            int finalL = Math.max(0, Math.min(maxL, Math.round(rawL)));
            int finalT = Math.max(0, Math.min(maxT, Math.round(rawT)));
            selectedElement.applyBoundsFromEditor(finalL, finalT, startWidth, startHeight);
            hasUnsavedChanges = true;
            refreshInspectorState();
            invalidate();
        }

        /**
         * Snaps Left, Center, or Right (for X) or Top, Center, or Bottom (for Y) against screen
         * centerlines and all other visible controller elements. Returns {snappedOrigin, guidePos}.
         */
        private float[] snapAxisPosition(float origin, int size, int parentExtent, boolean isX, float threshold) {
            float bestOrigin = origin;
            float bestDist = threshold + 1f;
            float bestGuide = -1f;

            float myStart = origin;
            float myCenter = origin + size * 0.5f;
            float myEnd = origin + size;

            // 1. Screen center guideline
            float screenMid = parentExtent * 0.5f;
            float dCenterScreen = Math.abs(myCenter - screenMid);
            if (dCenterScreen < bestDist) {
                bestDist = dCenterScreen;
                bestOrigin = screenMid - size * 0.5f;
                bestGuide = screenMid;
            }

            // 2. Other visible buttons' start, center, and end edges
            List<VirtualControllerElement> elements = virtualController.getElements();
            for (int i = 0; i < elements.size(); i++) {
                VirtualControllerElement other = elements.get(i);
                if (other == selectedElement || (!other.enabled && !showHiddenButtons)) {
                    continue;
                }
                fillElementRect(other, tempRect);
                float oStart = isX ? tempRect.left : tempRect.top;
                float oCenter = isX ? tempRect.centerX() : tempRect.centerY();
                float oEnd = isX ? tempRect.right : tempRect.bottom;

                float[] candidates = { oStart, oCenter, oEnd };
                for (float target : candidates) {
                    float dS = Math.abs(myStart - target);
                    if (dS < bestDist) {
                        bestDist = dS;
                        bestOrigin = target;
                        bestGuide = target;
                    }
                    float dC = Math.abs(myCenter - target);
                    if (dC < bestDist) {
                        bestDist = dC;
                        bestOrigin = target - size * 0.5f;
                        bestGuide = target;
                    }
                    float dE = Math.abs(myEnd - target);
                    if (dE < bestDist) {
                        bestDist = dE;
                        bestOrigin = target - size;
                        bestGuide = target;
                    }
                }
            }

            return new float[]{ bestOrigin, bestGuide };
        }

        private void handlePrecisionResize(float curX, float curY) {
            if (selectedElement == null) {
                return;
            }
            int parentW = Math.max(1, getWidth());
            int parentH = Math.max(1, getHeight());
            int minDim = Math.max(40, (int) (context.getResources().getDisplayMetrics().heightPixels * 0.04f));

            float dx = curX - downX;
            float dy = curY - downY;
            float ar = selectedElement.getLockedAspectRatio();

            activeGuidesX.clear();
            activeGuidesY.clear();

            int newL;
            int newT;
            int newW;
            int newH;

            if (ar > 0f) {
                // Aspect-locked uniform center scaling (Joysticks, D-pad, A/B/X/Y, Guide, Start, Back)
                float vx = downX - startCenterX;
                float vy = downY - startCenterY;
                float vLen = (float) Math.hypot(vx, vy);
                float delta;
                if (vLen > 12f) {
                    delta = 2f * ((dx * vx + dy * vy) / vLen);
                } else {
                    delta = (dx + dy * ar) * 0.5f;
                }
                float baseW = (startWidth + startHeight * ar) * 0.5f;
                float targetW = baseW + delta;

                if (snapMode == SnapMode.GRID) {
                    float gridStep = UiHelper.dpToPx(context, 8f);
                    targetW = Math.round(targetW / gridStep) * gridStep;
                } else if (snapMode == SnapMode.GUIDES) {
                    // Size-matching magnetic snap to other visible buttons of similar width
                    float sizeThresh = UiHelper.dpToPx(context, 6f);
                    List<VirtualControllerElement> elements = virtualController.getElements();
                    for (int i = 0; i < elements.size(); i++) {
                        VirtualControllerElement other = elements.get(i);
                        if (other == selectedElement || !other.enabled) {
                            continue;
                        }
                        if (Math.abs(other.getLockedAspectRatio() - ar) < 0.05f) {
                            FrameLayout.LayoutParams olp = (FrameLayout.LayoutParams) other.getLayoutParams();
                            int ow = (olp != null && olp.width > 0) ? olp.width : other.getWidth();
                            if (Math.abs(targetW - ow) <= sizeThresh) {
                                targetW = ow;
                                break;
                            }
                        }
                    }
                }

                float minW = Math.max(minDim, minDim * ar);
                float maxW = Math.max(minW, Math.min(parentW, parentH * ar));
                float clampedW = Math.max(minW, Math.min(maxW, targetW));
                newW = Math.round(clampedW);
                newH = Math.max(minDim, Math.round(clampedW / ar));
                if (newH > parentH) {
                    newH = parentH;
                    newW = Math.max(minDim, Math.round(newH * ar));
                }
                newL = Math.max(0, Math.min(Math.max(0, parentW - newW), Math.round(startCenterX - newW * 0.5f)));
                newT = Math.max(0, Math.min(Math.max(0, parentH - newH), Math.round(startCenterY - newH * 0.5f)));
            } else {
                // Freely resizable controls (LT/RT/LB/RB/L3/R3/Trackpad)
                float targetW = startWidth + dx;
                float targetH = startHeight + dy;
                if (snapMode == SnapMode.GRID) {
                    float gridStep = UiHelper.dpToPx(context, 8f);
                    targetW = Math.round(targetW / gridStep) * gridStep;
                    targetH = Math.round(targetH / gridStep) * gridStep;
                }
                int maxW = Math.max(minDim, parentW - startLeft);
                int maxH = Math.max(minDim, parentH - startTop);
                newW = Math.max(minDim, Math.min(maxW, Math.round(targetW)));
                newH = Math.max(minDim, Math.min(maxH, Math.round(targetH)));
                newL = startLeft;
                newT = startTop;
            }

            selectedElement.applyBoundsFromEditor(newL, newT, newW, newH);
            hasUnsavedChanges = true;
            refreshInspectorState();
            invalidate();
        }
    }
}
