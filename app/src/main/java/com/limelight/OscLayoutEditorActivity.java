package com.limelight;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.binding.input.virtual_controller.VirtualControllerConfigurationLoader;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.AppThemePalette;
import com.limelight.utils.UiHelper;

/**
 * Standalone fullscreen editor for customizing the on-screen controller layout
 * (move, resize, show/hide, reset, and save) directly from Settings without
 * starting an active stream.
 */
public class OscLayoutEditorActivity extends AppCompatActivity {

    private static final int MODE_MOVE_COLOR = 0xFFFF5252;
    private static final int MODE_RESIZE_COLOR = 0xFFFF40FF;
    private static final int MODE_TOGGLE_COLOR = 0xFF30D158;

    private FrameLayout editorCanvas;
    private VirtualController virtualController;
    private VirtualController.ControllerMode activeMode = VirtualController.ControllerMode.MoveButtons;
    private boolean hasUnsavedChanges = false;
    private int mountedCanvasW = -1;
    private int mountedCanvasH = -1;

    private TextView btnMove;
    private TextView btnResize;
    private TextView btnToggle;
    private TextView btnReset;
    private TextView btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiHelper.setLocale(this);

        PreferenceConfiguration prefConfig = PreferenceConfiguration.readPreferences(this);
        if (prefConfig.autoOrientation) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }

        setContentView(R.layout.activity_osc_layout_editor);
        UiHelper.notifyNewRootView(this);

        editorCanvas = findViewById(R.id.oscEditorCanvas);
        View toolbar = findViewById(R.id.oscEditorToolbar);
        UiHelper.applyLiquidGlassEffect(toolbar);

        btnMove = findViewById(R.id.oscBtnModeMove);
        btnResize = findViewById(R.id.oscBtnModeResize);
        btnToggle = findViewById(R.id.oscBtnModeToggle);
        btnReset = findViewById(R.id.oscBtnReset);
        btnSave = findViewById(R.id.oscBtnSave);

        btnMove.setOnClickListener(v -> selectMode(VirtualController.ControllerMode.MoveButtons));
        btnResize.setOnClickListener(v -> selectMode(VirtualController.ControllerMode.ResizeButtons));
        btnToggle.setOnClickListener(v -> selectMode(VirtualController.ControllerMode.DisableEnableButtons));
        btnReset.setOnClickListener(v -> confirmResetLayout());
        btnSave.setOnClickListener(v -> saveAndExit());

        virtualController = new VirtualController(null, editorCanvas, this);
        virtualController.setConfigureButtonVisible(false);
        virtualController.setOnLayoutEditedListener(() -> hasUnsavedChanges = true);

        // Wait until editorCanvas has final immersive dimensions so default layout matches Game 1:1
        editorCanvas.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = r - l;
            int h = b - t;
            if (w > 0 && h > 0 && !hasUnsavedChanges && (w != mountedCanvasW || h != mountedCanvasH)) {
                editorCanvas.post(this::mountController);
            }
        });
        editorCanvas.post(this::mountController);
    }

    private void mountController() {
        if (isFinishing() || virtualController == null || editorCanvas == null) {
            return;
        }
        int w = editorCanvas.getWidth();
        int h = editorCanvas.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        mountedCanvasW = w;
        mountedCanvasH = h;
        virtualController.refreshLayout();
        virtualController.setControllerMode(activeMode);
        virtualController.show();
        updateToolbarStyles();
    }

    private void selectMode(VirtualController.ControllerMode mode) {
        activeMode = mode;
        if (virtualController != null) {
            virtualController.setControllerMode(mode);
        }
        updateToolbarStyles();
    }

    private void updateToolbarStyles() {
        AppThemePalette palette = AppThemePalette.getCurrent(this);
        styleModeChip(btnMove, activeMode == VirtualController.ControllerMode.MoveButtons, MODE_MOVE_COLOR);
        styleModeChip(btnResize, activeMode == VirtualController.ControllerMode.ResizeButtons, MODE_RESIZE_COLOR);
        styleModeChip(btnToggle, activeMode == VirtualController.ControllerMode.DisableEnableButtons, MODE_TOGGLE_COLOR);

        GradientDrawable resetBg = new GradientDrawable();
        resetBg.setShape(GradientDrawable.RECTANGLE);
        resetBg.setCornerRadius(dp(999f));
        resetBg.setColor(0x1CFF5252);
        resetBg.setStroke(Math.max(1, dp(1f)), 0x55FF5252);
        btnReset.setBackground(resetBg);

        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setShape(GradientDrawable.RECTANGLE);
        saveBg.setCornerRadius(dp(999f));
        saveBg.setColor(AppThemePalette.withAlpha(palette.highlight, 0.32f));
        saveBg.setStroke(Math.max(1, dp(1.2f)), AppThemePalette.withAlpha(palette.highlight, 0.90f));
        btnSave.setBackground(saveBg);
    }

    private void styleModeChip(TextView chip, boolean selected, int accentColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(999f));
        if (selected) {
            bg.setColor(AppThemePalette.withAlpha(accentColor, 0.26f));
            bg.setStroke(Math.max(1, dp(1.3f)), AppThemePalette.withAlpha(accentColor, 0.92f));
            chip.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.TRANSPARENT);
            chip.setTextColor(0xA8FFFFFF);
        }
        chip.setBackground(bg);
    }

    private void confirmResetLayout() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_reset_osc)
                .setMessage(R.string.dialog_text_reset_osc)
                .setPositiveButton(R.string.yes, (d, which) -> {
                    getSharedPreferences(VirtualControllerConfigurationLoader.OSC_PREFERENCE, Context.MODE_PRIVATE)
                            .edit()
                            .clear()
                            .apply();
                    hasUnsavedChanges = false;
                    if (virtualController != null) {
                        virtualController.refreshLayout();
                        virtualController.setControllerMode(activeMode);
                        virtualController.show();
                    }
                    Toast.makeText(this, R.string.toast_reset_osc_success, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.no, null)
                .create();
        UiHelper.styleDialogSquircle(dialog);
        dialog.show();
        UiHelper.styleDialogSquircle(dialog);
    }

    private void saveAndExit() {
        if (virtualController != null) {
            VirtualControllerConfigurationLoader.saveProfile(virtualController, this);
        }
        hasUnsavedChanges = false;
        Toast.makeText(this, R.string.osc_editor_saved, Toast.LENGTH_SHORT).show();
        UiHelper.finishMenuWithDepthTransition(this, this::finish);
    }

    @Override
    public void onBackPressed() {
        if (!hasUnsavedChanges) {
            UiHelper.finishMenuWithDepthTransition(this, super::onBackPressed);
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.osc_editor_unsaved_title)
                .setMessage(R.string.osc_editor_unsaved_msg)
                .setPositiveButton(R.string.osc_editor_save, (d, which) -> saveAndExit())
                .setNegativeButton(R.string.osc_editor_discard, (d, which) ->
                        UiHelper.finishMenuWithDepthTransition(this, this::finish))
                .setNeutralButton(R.string.cancel, null)
                .create();
        UiHelper.styleDialogSquircle(dialog);
        dialog.show();
        UiHelper.styleDialogSquircle(dialog);
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiHelper.refreshBackdrop(this);
        UiHelper.restoreMenuForegroundIfNeeded(this);
        if (virtualController != null && !virtualController.getElements().isEmpty()) {
            virtualController.show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (virtualController != null) {
            virtualController.getLiveBlurHelper().stop();
        }
    }

    @Override
    protected void onDestroy() {
        if (virtualController != null) {
            virtualController.removeElements();
            virtualController = null;
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (virtualController != null && !hasUnsavedChanges) {
            editorCanvas.post(this::mountController);
        }
    }

    private int dp(float dpValue) {
        return Math.max(1, Math.round(UiHelper.dpToPx(this, dpValue)));
    }
}
