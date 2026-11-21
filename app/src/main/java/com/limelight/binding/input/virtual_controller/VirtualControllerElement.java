/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.limelight.Game;
import com.limelight.utils.UiHelper;

import org.json.JSONException;
import org.json.JSONObject;

public abstract class VirtualControllerElement extends View {
    protected static boolean _PRINT_DEBUG_INFORMATION = false;

    public static final int EID_DPAD = 1;
    public static final int EID_LT = 2;
    public static final int EID_RT = 3;
    public static final int EID_LB = 4;
    public static final int EID_RB = 5;
    public static final int EID_A = 6;
    public static final int EID_B = 7;
    public static final int EID_X = 8;
    public static final int EID_Y = 9;
    public static final int EID_BACK = 10;
    public static final int EID_START = 11;
    public static final int EID_LS = 12;
    public static final int EID_RS = 13;
    public static final int EID_LSB = 14;
    public static final int EID_RSB = 15;
    public static final int EID_GDB = 16;
    public static final int EID_TOUCHPAD = 65;

    protected VirtualController virtualController;
    protected final int elementId;

    private final Paint paint = new Paint();

    protected int normalColor = 0xF0888888;
    protected int pressedColor = 0xF07272ED;
    private int configMoveColor = 0xF0FF0000;
    private int configResizeColor = 0xF0FF00FF;
    private int configSelectedColor = 0xF000FF00;

    private int configDisabledColor = 0xF0AAAAAA;
    protected int elementOpacity = -1;
    protected int startSize_x;
    protected int startSize_y;

    float position_pressed_x = 0;
    float position_pressed_y = 0;
    private float startRawX = 0f;
    private float startRawY = 0f;
    private float startCenterX = 0f;
    private float startCenterY = 0f;

    public boolean enabled = true;

    private enum Mode {
        Normal,
        Resize,
        Move
    }

    private Mode currentMode = Mode.Normal;

    protected VirtualControllerElement(VirtualController controller, Context context, int elementId) {
        super(context);

        this.virtualController = controller;
        this.elementId = elementId;
    }

    public int getElementId() {
        return elementId;
    }

    public String getDisplayLabel() {
        switch (elementId) {
            case EID_DPAD: return "D-Pad";
            case EID_LT: return "LT";
            case EID_RT: return "RT";
            case EID_LB: return "LB";
            case EID_RB: return "RB";
            case EID_A: return "A";
            case EID_B: return "B";
            case EID_X: return "X";
            case EID_Y: return "Y";
            case EID_BACK: return "Back";
            case EID_START: return "Start";
            case EID_LS: return "LS";
            case EID_RS: return "RS";
            case EID_LSB: return "L3";
            case EID_RSB: return "R3";
            case EID_GDB: return "Guide";
            case EID_TOUCHPAD: return "Pad";
            default: return "Btn";
        }
    }

    /**
     * Returns the locked width/height aspect ratio for controls that should only scale uniformly
     * (never stretch), or 0f for controls that may be resized freely.
     */
    public float getLockedAspectRatio() {
        switch (elementId) {
            case EID_LS:
            case EID_RS:
            case EID_DPAD:
            case EID_A:
            case EID_B:
            case EID_X:
            case EID_Y:
                return 1.0f;
            case EID_GDB:
            case EID_START:
            case EID_BACK:
                return 11.0f / 7.0f;
            default:
                return 0f;
        }
    }

    public void applyBoundsFromEditor(int left, int top, int width, int height) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        if (lp == null) {
            return;
        }
        lp.leftMargin = Math.max(0, left);
        lp.topMargin = Math.max(0, top);
        lp.rightMargin = 0;
        lp.bottomMargin = 0;
        lp.width = Math.max(1, width);
        lp.height = Math.max(1, height);
        setLayoutParams(lp);
        virtualController.notifyLayoutEdited();
        invalidate();
    }

    protected void moveElement(int pressed_x, int pressed_y, int x, int y) {
        int newPos_x = (int) getX() + x - pressed_x;
        int newPos_y = (int) getY() + y - pressed_y;

        View parentView = getParent() instanceof View ? (View) getParent() : null;
        int maxX = parentView != null ? Math.max(0, parentView.getWidth() - getWidth()) : Integer.MAX_VALUE;
        int maxY = parentView != null ? Math.max(0, parentView.getHeight() - getHeight()) : Integer.MAX_VALUE;

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        layoutParams.leftMargin = Math.max(0, Math.min(maxX, newPos_x));
        layoutParams.topMargin = Math.max(0, Math.min(maxY, newPos_y));
        layoutParams.rightMargin = 0;
        layoutParams.bottomMargin = 0;

        virtualController.notifyLayoutEdited();
        requestLayout();
    }

    protected void resizeElementByRawDelta(float rawX, float rawY) {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
        View parentView = getParent() instanceof View ? (View) getParent() : null;
        int parentW = parentView != null ? parentView.getWidth() : getResources().getDisplayMetrics().widthPixels;
        int parentH = parentView != null ? parentView.getHeight() : getResources().getDisplayMetrics().heightPixels;
        int minDim = Math.max(40, (int) (getResources().getDisplayMetrics().heightPixels * 0.04f));

        float dx = rawX - startRawX;
        float dy = rawY - startRawY;
        float ar = getLockedAspectRatio();

        if (ar > 0f) {
            // Uniform aspect-ratio locked scaling anchored to the element's center
            float ox = position_pressed_x - startSize_x * 0.5f;
            float oy = position_pressed_y - startSize_y * 0.5f;
            float offsetLen = (float) Math.hypot(ox, oy);
            float delta;
            if (offsetLen > Math.min(startSize_x, startSize_y) * 0.12f) {
                // Dragging outward in any direction grows evenly; inward shrinks evenly
                delta = 2.0f * (dx * (ox / offsetLen) + dy * (oy / offsetLen));
            } else {
                delta = 1.5f * (dx + dy);
            }

            float baseW = (startSize_x + startSize_y * ar) * 0.5f;
            float minW = Math.max(minDim, minDim * ar);
            float maxW = Math.max(minW, Math.min(parentW, parentH * ar));
            float clampedW = Math.max(minW, Math.min(maxW, baseW + delta));
            int newW = Math.round(clampedW);
            int newH = Math.max(minDim, Math.round(clampedW / ar));
            if (newH > parentH) {
                newH = parentH;
                newW = Math.max(minDim, Math.round(newH * ar));
            }

            int newLeft = Math.round(startCenterX - newW * 0.5f);
            int newTop = Math.round(startCenterY - newH * 0.5f);
            layoutParams.width = newW;
            layoutParams.height = newH;
            layoutParams.leftMargin = Math.max(0, Math.min(Math.max(0, parentW - newW), newLeft));
            layoutParams.topMargin = Math.max(0, Math.min(Math.max(0, parentH - newH), newTop));
            layoutParams.rightMargin = 0;
            layoutParams.bottomMargin = 0;
        } else {
            int newWidth = startSize_x + Math.round(dx);
            int newHeight = startSize_y + Math.round(dy);
            int maxW = Math.max(minDim, parentW - layoutParams.leftMargin);
            int maxH = Math.max(minDim, parentH - layoutParams.topMargin);
            layoutParams.width = Math.max(minDim, Math.min(maxW, newWidth));
            layoutParams.height = Math.max(minDim, Math.min(maxH, newHeight));
        }

        virtualController.notifyLayoutEdited();
        requestLayout();
    }

    protected void resizeElement(int pressed_x, int pressed_y, int width, int height) {
        resizeElementByRawDelta(startRawX + (width - pressed_x), startRawY + (height - pressed_y));
    }

    protected void actionDisableEnableButton() {
        enabled = !enabled;
        virtualController.notifyLayoutEdited();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        onElementDraw(canvas);

        if (currentMode != Mode.Normal) {
            paint.setColor(configSelectedColor);
            paint.setStrokeWidth(getDefaultStrokeWidth());
            paint.setStyle(Paint.Style.STROKE);

            canvas.drawRect(paint.getStrokeWidth(), paint.getStrokeWidth(),
                    getWidth()-paint.getStrokeWidth(), getHeight()-paint.getStrokeWidth(),
                    paint);
        }

        super.onDraw(canvas);
    }

    /*
    protected void actionShowNormalColorChooser() {
        AmbilWarnaDialog colorDialog = new AmbilWarnaDialog(getContext(), normalColor, true, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onCancel(AmbilWarnaDialog dialog)
            {}

            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                normalColor = color;
                invalidate();
            }
        });
        colorDialog.show();
    }

    protected void actionShowPressedColorChooser() {
        AmbilWarnaDialog colorDialog = new AmbilWarnaDialog(getContext(), normalColor, true, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onCancel(AmbilWarnaDialog dialog) {
            }

            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                pressedColor = color;
                invalidate();
            }
        });
        colorDialog.show();
    }
    */

    protected void actionEnableMove() {
        currentMode = Mode.Move;
    }

    protected void actionEnableResize() {
        currentMode = Mode.Resize;
    }

    protected void actionCancel() {
        currentMode = Mode.Normal;
        invalidate();
    }

    protected int getDefaultColor() {
        if (virtualController.getControllerMode() == VirtualController.ControllerMode.MoveButtons)
            return configMoveColor;
        else if (virtualController.getControllerMode() == VirtualController.ControllerMode.ResizeButtons)
            return configResizeColor;
        else if (virtualController.getControllerMode() == VirtualController.ControllerMode.DisableEnableButtons)
            return enabled ? configSelectedColor: configDisabledColor;
        else return normalColor;
    }

    protected int getDefaultStrokeWidth() {
        DisplayMetrics screen = getResources().getDisplayMetrics();
        return (int)(screen.heightPixels*0.004f);
    }

    protected float getOutlineScaleFactor() {
        int pct = com.limelight.profiles.ProfilesManager.getInstance()
                .getOverlayingSharedPreferences(getContext())
                .getInt(com.limelight.preferences.PreferenceConfiguration.OSC_OUTLINE_THICKNESS_PREF_STRING,
                        com.limelight.preferences.PreferenceConfiguration.DEFAULT_OSC_OUTLINE_THICKNESS);
        return Math.max(0f, Math.min(2.5f, pct / 100f));
    }

    protected void showConfigurationDialog() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getContext());

        alertBuilder.setTitle("Configuration");

        CharSequence functions[] = new CharSequence[]{
                "Move",
                "Resize",
            /*election
            "Set n
            Disable color sormal color",
            "Set pressed color",
            */
                "Cancel"
        };

        alertBuilder.setItems(functions, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: { // move
                        actionEnableMove();
                        break;
                    }
                    case 1: { // resize
                        actionEnableResize();
                        break;
                    }
                /*
                case 2: { // set default color
                    actionShowNormalColorChooser();
                    break;
                }
                case 3: { // set pressed color
                    actionShowPressedColorChooser();
                    break;
                }
                */
                    default: { // cancel
                        actionCancel();
                        break;
                    }
                }
            }
        });
        AlertDialog alert = alertBuilder.create();
        UiHelper.styleDialogSquircle(alert);
        // show menu
        alert.show();
        UiHelper.styleDialogSquircle(alert);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Ignore secondary touches on controls
        //
        // NB: We can get an additional pointer down if the user touches a non-StreamView area
        // while also touching an OSC control, even if that pointer down doesn't correspond to
        // an area of the OSC control.
        if (event.getActionIndex() != 0) {
            return true;
        }

        if (virtualController.getControllerMode() == VirtualController.ControllerMode.Active) {
            return onElementTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                position_pressed_x = event.getX();
                position_pressed_y = event.getY();
                startRawX = event.getRawX();
                startRawY = event.getRawY();
                startSize_x = getWidth();
                startSize_y = getHeight();
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
                startCenterX = (lp != null ? lp.leftMargin : getX()) + startSize_x * 0.5f;
                startCenterY = (lp != null ? lp.topMargin : getY()) + startSize_y * 0.5f;

                if (virtualController.getControllerMode() == VirtualController.ControllerMode.MoveButtons)
                    actionEnableMove();
                else if (virtualController.getControllerMode() == VirtualController.ControllerMode.ResizeButtons)
                    actionEnableResize();
                else if (virtualController.getControllerMode() == VirtualController.ControllerMode.DisableEnableButtons)
                    actionDisableEnableButton();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                switch (currentMode) {
                    case Move: {
                        moveElement(
                                (int) position_pressed_x,
                                (int) position_pressed_y,
                                (int) event.getX(),
                                (int) event.getY());
                        break;
                    }
                    case Resize: {
                        resizeElementByRawDelta(event.getRawX(), event.getRawY());
                        break;
                    }
                    case Normal: {
                        break;
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                actionCancel();
                return true;
            }
            default: {
            }
        }
        return true;
    }

    abstract protected void onElementDraw(Canvas canvas);

    abstract public boolean onElementTouchEvent(MotionEvent event);

    protected static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
//            System.out.println(text);
        }
    }

    public void setColors(int normalColor, int pressedColor) {
        this.normalColor = normalColor;
        this.pressedColor = pressedColor;

        invalidate();
    }


    public void setOpacity(int opacity) {
        this.elementOpacity = Math.max(0, Math.min(100, opacity));
        int hexOpacity = this.elementOpacity * 255 / 100;
        this.normalColor = (hexOpacity << 24) | (normalColor & 0x00FFFFFF);
        this.pressedColor = (hexOpacity << 24) | (pressedColor & 0x00FFFFFF);

        invalidate();
    }

    protected int getEffectiveOscOpacity() {
        if (elementOpacity >= 0) {
            return elementOpacity;
        }
        return com.limelight.preferences.PreferenceConfiguration.readPreferences(getContext()).oscOpacity;
    }

    protected final float getPercent(float value, float percent) {
        return value / 100 * percent;
    }

    protected final int getCorrectWidth() {
        return getWidth() > getHeight() ? getHeight() : getWidth();
    }


    public JSONObject getConfiguration() throws JSONException {
        JSONObject configuration = new JSONObject();

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        configuration.put("LEFT", layoutParams.leftMargin);
        configuration.put("TOP", layoutParams.topMargin);
        configuration.put("WIDTH", layoutParams.width);
        configuration.put("HEIGHT", layoutParams.height);
        configuration.put("ENABLED", enabled);
        return configuration;
    }

    public void loadConfiguration(JSONObject configuration) throws JSONException {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        layoutParams.leftMargin = configuration.getInt("LEFT");
        layoutParams.topMargin = configuration.getInt("TOP");
        layoutParams.width = configuration.getInt("WIDTH");
        layoutParams.height = configuration.getInt("HEIGHT");
        enabled = configuration.getBoolean("ENABLED");
        setVisibility(enabled ? VISIBLE: GONE);
        requestLayout();
    }
}
