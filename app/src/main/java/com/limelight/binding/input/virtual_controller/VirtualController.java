/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

public class VirtualController {
    public static class ControllerInputContext {
//        public short inputMap = 0x0000;
        public int inputMap = 0;
        public byte leftTrigger = 0x00;
        public byte rightTrigger = 0x00;
        public short rightStickX = 0x0000;
        public short rightStickY = 0x0000;
        public short leftStickX = 0x0000;
        public short leftStickY = 0x0000;
    }

    public enum ControllerMode {
        Active,
        MoveButtons,
        ResizeButtons,
        DisableEnableButtons
    }

    private static final boolean _PRINT_DEBUG_INFORMATION = false;

    private final ControllerHandler controllerHandler;
    private final Context context;
    private final Handler handler;

    private final Runnable delayedRetransmitRunnable = new Runnable() {
        @Override
        public void run() {
            sendControllerInputContextInternal();
        }
    };

    public interface OnLayoutEditedListener {
        void onLayoutEdited();
    }

    private FrameLayout frame_layout = null;

    ControllerMode currentMode = ControllerMode.Active;
    ControllerInputContext inputContext = new ControllerInputContext();

    private Button buttonConfigure = null;
    private boolean visible = false;
    private boolean configureButtonVisible = false;
    private OnLayoutEditedListener layoutEditedListener = null;

    private List<VirtualControllerElement> elements = new ArrayList<>();

    private final OscLiveBlurHelper liveBlurHelper;

    private Vibrator vibrator;

    private final VibrationEffect pressVibrationEffect;
    private final VibrationEffect releaseVibrationEffect;

    public VirtualController(final ControllerHandler controllerHandler, FrameLayout layout, final Context context) {
        this.controllerHandler = controllerHandler;
        this.frame_layout = layout;
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
        this.liveBlurHelper = new OscLiveBlurHelper(this, context);

        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pressVibrationEffect = VibrationEffect.createOneShot(10, 90);
            releaseVibrationEffect = VibrationEffect.createOneShot(6, 42);
        } else {
            pressVibrationEffect = null;
            releaseVibrationEffect = null;
        }

        buttonConfigure = new Button(context);
        buttonConfigure.setAlpha(0.25f);
        buttonConfigure.setFocusable(false);
        buttonConfigure.setBackgroundResource(R.drawable.ic_settings);
        buttonConfigure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message;

                if (currentMode == ControllerMode.Active) {
                    setControllerMode(ControllerMode.DisableEnableButtons);
                    message = context.getString(R.string.configuration_mode_disable_enable_buttons);
                } else if (currentMode == ControllerMode.DisableEnableButtons) {
                    setControllerMode(ControllerMode.MoveButtons);
                    message = context.getString(R.string.configuration_mode_move_buttons);
                } else if (currentMode == ControllerMode.MoveButtons) {
                    setControllerMode(ControllerMode.ResizeButtons);
                    message = context.getString(R.string.configuration_mode_resize_buttons);
                } else {
                    setControllerMode(ControllerMode.Active);
                    VirtualControllerConfigurationLoader.saveProfile(VirtualController.this, context);
                    message = context.getString(R.string.configuration_mode_exiting);
                }

                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });

    }

    public void setOnLayoutEditedListener(OnLayoutEditedListener listener) {
        this.layoutEditedListener = listener;
    }

    void notifyLayoutEdited() {
        if (layoutEditedListener != null) {
            layoutEditedListener.onLayoutEdited();
        }
    }

    public void setConfigureButtonVisible(boolean showConfigureButton) {
        this.configureButtonVisible = showConfigureButton;
        if (buttonConfigure != null) {
            buttonConfigure.setVisibility((visible && configureButtonVisible) ? View.VISIBLE : View.GONE);
        }
    }

    public void setControllerMode(ControllerMode mode) {
        this.currentMode = mode;
        if (visible) {
            if (mode == ControllerMode.DisableEnableButtons) {
                showElements();
            } else {
                showEnabledElements();
            }
        }
        if (buttonConfigure != null) {
            buttonConfigure.invalidate();
        }
        for (VirtualControllerElement element : elements) {
            element.invalidate();
        }
    }

    public boolean isVisible() {
        return visible;
    }

    Handler getHandler() {
        return handler;
    }

    public void triggerHapticPress() {
        if (vibrator == null || frame_layout == null) {
            return;
        }
        if (!PreferenceConfiguration.readPreferences(context).vibrateOsc) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && pressVibrationEffect != null) {
            vibrator.vibrate(pressVibrationEffect);
        } else {
            vibrator.vibrate(10);
        }
    }

    public void triggerHapticRelease() {
        if (vibrator == null || frame_layout == null) {
            return;
        }
        if (!PreferenceConfiguration.readPreferences(context).vibrateOsc) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && releaseVibrationEffect != null) {
            vibrator.vibrate(releaseVibrationEffect);
        } else {
            vibrator.vibrate(5);
        }
    }

    public OscLiveBlurHelper getLiveBlurHelper() {
        return liveBlurHelper;
    }

    public void hide() {
        visible = false;
        liveBlurHelper.stop();
        for (VirtualControllerElement element : elements) {
            element.setVisibility(View.GONE);
        }

        buttonConfigure.setVisibility(View.GONE);
    }

    public void show() {
        visible = true;
        if (currentMode == ControllerMode.DisableEnableButtons) {
            showElements();
        } else {
            showEnabledElements();
        }

        buttonConfigure.setVisibility(configureButtonVisible ? View.VISIBLE : View.GONE);
        liveBlurHelper.start();
    }

    public int switchShowHide() {
        if (visible) {
            hide();
            return 0;
        } else {
            show();
            return 1;
        }
    }

    public void showElements(){
        for(VirtualControllerElement element : elements){
            element.setVisibility(View.VISIBLE);
        }
    }

    public void showEnabledElements(){
        for(VirtualControllerElement element: elements){
            element.setVisibility( element.enabled ? View.VISIBLE : View.GONE );
        }
    }

    public void removeElements() {
        for (VirtualControllerElement element : elements) {
            frame_layout.removeView(element);
        }
        elements.clear();

        frame_layout.removeView(buttonConfigure);
    }

    public void setOpacity(int opacity) {
        for (VirtualControllerElement element : elements) {
            element.setOpacity(opacity);
        }
    }


    public void addElement(VirtualControllerElement element, int x, int y, int width, int height) {
        elements.add(element);
        if (frame_layout != null) {
            frame_layout.setClipChildren(false);
            frame_layout.setClipToPadding(false);
        }
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);

        frame_layout.addView(element, layoutParams);
    }

    public List<VirtualControllerElement> getElements() {
        return elements;
    }

    private static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            LimeLog.info("VirtualController: " + text);
        }
    }

    public void refreshLayout() {
        removeElements();

        DisplayMetrics screen = context.getResources().getDisplayMetrics();

        int buttonSize = (int)(screen.heightPixels*0.06f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        params.leftMargin = 15;
        params.topMargin = 15;
        frame_layout.addView(buttonConfigure, params);

        // Start with the default layout
        VirtualControllerConfigurationLoader.createDefaultLayout(this, context);

        // Apply user preferences onto the default layout
        VirtualControllerConfigurationLoader.loadFromPreferences(this, context);
    }

    public ControllerMode getControllerMode() {
        return currentMode;
    }

    public ControllerInputContext getControllerInputContext() {
        return inputContext;
    }

    private void sendControllerInputContextInternal() {
        _DBG("INPUT_MAP + " + inputContext.inputMap);
        _DBG("LEFT_TRIGGER " + inputContext.leftTrigger);
        _DBG("RIGHT_TRIGGER " + inputContext.rightTrigger);
        _DBG("LEFT STICK X: " + inputContext.leftStickX + " Y: " + inputContext.leftStickY);
        _DBG("RIGHT STICK X: " + inputContext.rightStickX + " Y: " + inputContext.rightStickY);

        if (controllerHandler != null) {
            controllerHandler.reportOscState(
                    inputContext.inputMap,
                    inputContext.leftStickX,
                    inputContext.leftStickY,
                    inputContext.rightStickX,
                    inputContext.rightStickY,
                    inputContext.leftTrigger,
                    inputContext.rightTrigger
            );
        }
    }

    public void sendControllerInputContext(long vibrationDuration, int vibrationAmplitude) {
        // Cancel retransmissions of prior gamepad inputs
        handler.removeCallbacks(delayedRetransmitRunnable);

        sendControllerInputContextInternal();
        // HACK: GFE sometimes discards gamepad packets when they are received
        // very shortly after another. This can be critical if an axis zeroing packet
        // is lost and causes an analog stick to get stuck. To avoid this, we retransmit
        // the gamepad state a few times unless another input event happens before then.
        handler.postDelayed(delayedRetransmitRunnable, 25);
        handler.postDelayed(delayedRetransmitRunnable, 50);
        handler.postDelayed(delayedRetransmitRunnable, 75);
    }

    public void sendControllerInputContext() {
        sendControllerInputContext(0, 0);
    }
}
