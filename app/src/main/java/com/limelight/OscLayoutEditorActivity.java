package com.limelight;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.limelight.binding.input.virtual_controller.OscLayoutEditorOverlay;
import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.UiHelper;

/**
 * Standalone fullscreen editor for customizing the on-screen controller layout
 * directly from Settings without starting an active stream.
 * Uses the exact same {@link OscLayoutEditorOverlay} precision editor as live mid-stream editing.
 */
public class OscLayoutEditorActivity extends AppCompatActivity {

    private FrameLayout editorCanvas;
    private VirtualController virtualController;
    private OscLayoutEditorOverlay editorOverlay;
    private int mountedCanvasW = -1;
    private int mountedCanvasH = -1;

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

        virtualController = new VirtualController(null, editorCanvas, this);
        virtualController.setConfigureButtonVisible(false);

        editorOverlay = new OscLayoutEditorOverlay(
                this,
                editorCanvas,
                virtualController,
                () -> UiHelper.finishMenuWithDepthTransition(this, this::finish));

        // Wait until editorCanvas has final immersive dimensions so default layout matches Game 1:1
        editorCanvas.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = r - l;
            int h = b - t;
            boolean edited = editorOverlay != null && editorOverlay.hasUnsavedChanges();
            if (w > 0 && h > 0 && !edited && (w != mountedCanvasW || h != mountedCanvasH)) {
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
        virtualController.setConfigureButtonVisible(false);
        virtualController.show();
        if (editorOverlay != null) {
            if (!editorOverlay.isAttached()) {
                editorOverlay.attach();
            } else {
                editorOverlay.refreshAfterControllerRemount();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (editorOverlay != null && editorOverlay.isAttached()) {
            editorOverlay.requestClose();
        } else {
            UiHelper.finishMenuWithDepthTransition(this, super::onBackPressed);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiHelper.refreshBackdrop(this);
        UiHelper.restoreMenuForegroundIfNeeded(this);
        if (virtualController != null && !virtualController.getElements().isEmpty()) {
            virtualController.show();
            if (editorOverlay != null && editorOverlay.isAttached()) {
                editorOverlay.refreshAfterControllerRemount();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (editorOverlay != null) {
            editorOverlay.detach();
            editorOverlay = null;
        }
        if (virtualController != null) {
            virtualController.removeElements();
            virtualController = null;
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (virtualController != null && (editorOverlay == null || !editorOverlay.hasUnsavedChanges())) {
            editorCanvas.post(this::mountController);
        }
    }
}
