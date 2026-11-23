/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;

import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a analog stick on screen element. It is used to get 2-Axis user input.
 */
public class AnalogStickFree extends VirtualControllerElement {

    /**
     * outer radius size in percent of the ui element
     */
    public static final int SIZE_RADIUS_COMPLETE = 90;
    /**
     * analog stick size in percent of the ui element
     */
    public static final int SIZE_RADIUS_ANALOG_STICK = 90;
    /**
     * dead zone size in percent of the ui element
     */
    public static final int SIZE_RADIUS_DEADZONE = 90;
    /**
     * time frame for a double click
     */
    public final static long timeoutDoubleClick = 350;

    /**
     * touch down time until the deadzone is lifted to allow precise movements with the analog sticks
     */
    public final static long timeoutDeadzone = 150;

    /**
     * Listener interface to update registered observers.
     */
    public interface AnalogStickListener {

        /**
         * onMovement event will be fired on real analog stick movement (outside of the deadzone).
         *
         * @param x horizontal position, value from -1.0 ... 0 .. 1.0
         * @param y vertical position, value from -1.0 ... 0 .. 1.0
         */
        void onMovement(float x, float y);

        /**
         * onClick event will be fired on click on the analog stick
         */
        void onClick();

        /**
         * onDoubleClick event will be fired on a double click in a short time frame on the analog
         * stick.
         */
        void onDoubleClick();

        /**
         * onRevoke event will be fired on unpress of the analog stick.
         */
        void onRevoke();
    }

    /**
     * Movement states of the analog sick.
     */
    private enum STICK_STATE {
        NO_MOVEMENT,
        MOVED_IN_DEAD_ZONE,
        MOVED_ACTIVE
    }

    /**
     * Click type states.
     */
    private enum CLICK_STATE {
        SINGLE,
        DOUBLE
    }

    /**
     * configuration if the analog stick should be displayed as circle or square
     */
    private boolean circle_stick = true; // TODO: implement square sick for simulations

    /**
     * outer radius, this size will be automatically updated on resize
     */
    private float radius_complete = 0;
    /**
     * analog stick radius, this size will be automatically updated on resize
     */
    private float radius_analog_stick = 0;
    /**
     * dead zone radius, this size will be automatically updated on resize
     */
    private float radius_dead_zone = 0;

    /**
     * horizontal position in relation to the center of the element
     */
    private float relative_x = 0;
    /**
     * vertical position in relation to the center of the element
     */
    private float relative_y = 0;

    private boolean bIsFingerOnScreen = false;

    private double movement_radius = 0;
    private double movement_angle = 0;

    private float position_stick_x = 0;
    private float position_stick_y = 0;

    private final Paint paint = new Paint();

    private STICK_STATE stick_state = STICK_STATE.NO_MOVEMENT;
    private CLICK_STATE click_state = CLICK_STATE.SINGLE;

    private List<AnalogStickListener> listeners = new ArrayList<>();
    private long timeLastClick = 0;

    private int touchID;
    private float touchStartX;
    private float touchStartY;
    private float touchX;
    private float touchY;

    private float touchMaxDistance = 120;
    private float touchDeadZone = 20;
    private float fDeadzoneSave = 0.01f;

    protected String strStickSide = "L";

    private static double getMovementRadius(float x, float y) {
        return Math.sqrt(x * x + y * y);
    }

    private static double getAngle(float way_x, float way_y) {
        // prevent divisions by zero for corner cases
        if (way_x == 0) {
            return way_y < 0 ? Math.PI : 0;
        } else if (way_y == 0) {
            if (way_x > 0) {
                return Math.PI * 3 / 2;
            } else if (way_x < 0) {
                return Math.PI * 1 / 2;
            }
        }
        // return correct calculated angle for each quadrant
        if (way_x > 0) {
            if (way_y < 0) {
                // first quadrant
                return 3 * Math.PI / 2 + Math.atan((double) (-way_y / way_x));
            } else {
                // second quadrant
                return Math.PI + Math.atan((double) (way_x / way_y));
            }
        } else {
            if (way_y > 0) {
                // third quadrant
                return Math.PI / 2 + Math.atan((double) (way_y / -way_x));
            } else {
                // fourth quadrant
                return 0 + Math.atan((double) (-way_x / -way_y));
            }
        }
    }

    public AnalogStickFree(VirtualController controller, Context context, int elementId) {
        super(controller, context, elementId);
        // reset stick position
        position_stick_x = getWidth() / 2;
        position_stick_y = getHeight() / 2;
    }

    public void addAnalogStickListener(AnalogStickListener listener) {
        listeners.add(listener);
    }

    private void notifyOnMovement(float x, float y) {
        _DBG("movement x: " + x + " movement y: " + y);
        // notify listeners
        for (AnalogStickListener listener : listeners) {
            listener.onMovement(x, y);
        }
    }

    private void notifyOnClick() {
        _DBG("click");
        // notify listeners
        for (AnalogStickListener listener : listeners) {
            listener.onClick();
        }
    }

    private void notifyOnDoubleClick() {
        _DBG("double click");
        // notify listeners
        for (AnalogStickListener listener : listeners) {
            listener.onDoubleClick();
        }
    }

    private void notifyOnRevoke() {
        _DBG("revoke");
        // notify listeners
        for (AnalogStickListener listener : listeners) {
            listener.onRevoke();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // calculate new radius sizes depending
        radius_complete = getPercent(getCorrectWidth() / 2, 100) - 2 * getDefaultStrokeWidth();
        radius_dead_zone = getPercent(getCorrectWidth() / 2, 30);
        radius_analog_stick = getPercent(getCorrectWidth() / 2, 20);

        super.onSizeChanged(w, h, oldw, oldh);
    }


    @Override
    protected void onElementDraw(Canvas canvas) {
        boolean bIsMoving = virtualController.getControllerMode() == VirtualController.ControllerMode.MoveButtons;
        boolean bIsResizing = virtualController.getControllerMode() == VirtualController.ControllerMode.ResizeButtons;
        boolean bIsEnable = virtualController.getControllerMode() == VirtualController.ControllerMode.DisableEnableButtons;
        boolean isEditMode = bIsMoving || bIsResizing || bIsEnable;

        paint.setAntiAlias(true);
        paint.setPathEffect(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(getDefaultStrokeWidth());

        PreferenceConfiguration cfg = PreferenceConfiguration.readPreferences(getContext());
        int oscOpacity = getEffectiveOscOpacity();
        float opacityFactor = Math.max(0.0f, Math.min(1.0f, oscOpacity / 100f));
        if (isEditMode && opacityFactor < 0.35f) {
            opacityFactor = 0.35f;
        }
        if (bIsEnable && !enabled) {
            opacityFactor *= 0.38f;
        }

        if (!bIsFingerOnScreen) {
            if (!cfg.enableOnScreenStyleOfficial && opacityFactor > 0.005f) {
                float cx = getWidth() * 0.5f;
                float cy = getHeight() * 0.5f;
                float outlineScale = getOutlineScaleFactor();
                float strokeW = outlineScale <= 0.01f ? 0f : Math.max(1f, getDefaultStrokeWidth() * outlineScale);

                boolean drewLiveBlur = false;
                OscLiveBlurHelper blurHelper = virtualController.getLiveBlurHelper();
                if (blurHelper != null) {
                    blurClipPath.reset();
                    blurClipPath.addCircle(cx, cy, radius_complete, android.graphics.Path.Direction.CW);
                    blurBounds.set(cx - radius_complete, cy - radius_complete, cx + radius_complete, cy + radius_complete);
                    drewLiveBlur = blurHelper.drawLiveBlurForBounds(canvas, this, blurClipPath, blurBounds, opacityFactor * 0.70f);
                }

                // Subtle translucent idle zone + dashed boundary ring + center dot
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(freeStickWithAlpha(0xFF101624, (drewLiveBlur ? 0.18f : 0.25f) * opacityFactor));
                canvas.drawCircle(cx, cy, radius_complete, paint);

                if (strokeW > 0.1f) {
                    float dashLen = Math.max(6f, radius_complete * 0.14f);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(1.5f, strokeW * 0.85f));
                    paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{dashLen, dashLen * 0.85f}, 0f));
                    paint.setColor(freeStickWithAlpha(0xFFFFFFFF, 0.45f * opacityFactor));
                    canvas.drawCircle(cx, cy, radius_complete, paint);
                    paint.setPathEffect(null);
                }

                float dotR = Math.max(4f, radius_complete * 0.095f);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(freeStickWithAlpha(0xFFFFFFFF, 0.55f * opacityFactor));
                canvas.drawCircle(cx, cy, dotR, paint);
            }

            if (isEditMode) {
                int nWidth = getWidth();
                int nHeight = getHeight();
                int editColor = getDefaultColor();
                float editStroke = Math.max(2f, getDefaultStrokeWidth());
                float pad = editStroke * 0.5f;
                float cornerR = Math.min(nWidth, nHeight) * 0.14f;

                paint.setShader(null);
                paint.setPathEffect(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(freeStickWithAlpha(editColor, 0.20f));
                canvas.drawRoundRect(pad, pad, nWidth - pad, nHeight - pad, cornerR, cornerR, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(editStroke);
                paint.setColor(editColor);
                canvas.drawRoundRect(pad, pad, nWidth - pad, nHeight - pad, cornerR, cornerR, paint);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(freeStickWithAlpha(Color.WHITE, (bIsEnable && !enabled) ? 0.50f : 0.92f));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setFakeBoldText(true);
                paint.setTextSize(Math.min(nWidth, nHeight) * 0.30f);
                Paint.FontMetrics fm = paint.getFontMetrics();
                float textY = nHeight * 0.5f - (fm.ascent + fm.descent) * 0.5f;
                canvas.drawText(strStickSide, nWidth * 0.5f, textY, paint);
                paint.setFakeBoldText(false);
            }
            return;
        }

        if (bIsFingerOnScreen) {
            if (!cfg.enableOnScreenStyleOfficial) {
                if (opacityFactor <= 0.005f && !isEditMode) {
                    return;
                }
                float outlineScale = getOutlineScaleFactor();
                float strokeW = outlineScale <= 0.01f ? 0f : Math.max(1f, getDefaultStrokeWidth() * outlineScale);
                switch (stick_state) {
                    case NO_MOVEMENT: {
                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(freeStickWithAlpha(0xFFFFFFFF, 0.55f * opacityFactor));
                        canvas.drawCircle(getWidth() * 0.5f, getHeight() * 0.5f, radius_analog_stick * 1.25f, paint);
                        break;
                    }
                    case MOVED_IN_DEAD_ZONE:
                    case MOVED_ACTIVE: {
                        if (opacityFactor > 0.005f) {
                            boolean drewLiveBlur = false;
                            OscLiveBlurHelper blurHelper = virtualController.getLiveBlurHelper();
                            if (blurHelper != null) {
                                blurClipPath.reset();
                                blurClipPath.addCircle(touchStartX, touchStartY, radius_complete, android.graphics.Path.Direction.CW);
                                blurBounds.set(
                                        touchStartX - radius_complete, touchStartY - radius_complete,
                                        touchStartX + radius_complete, touchStartY + radius_complete);
                                drewLiveBlur = blurHelper.drawLiveBlurForBounds(canvas, this, blurClipPath, blurBounds, opacityFactor);
                            }

                            paint.setShader(null);
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(freeStickWithAlpha(0xFF101624, (drewLiveBlur ? 0.34f : 0.44f) * opacityFactor));
                            canvas.drawCircle(touchStartX, touchStartY, radius_complete, paint);

                            if (strokeW > 0.1f) {
                                paint.setStyle(Paint.Style.STROKE);
                                paint.setStrokeWidth(strokeW);
                                paint.setColor(freeStickWithAlpha(0xFFFFFFFF, 0.56f * opacityFactor));
                                canvas.drawCircle(touchStartX, touchStartY, radius_complete, paint);

                                paint.setStrokeWidth(Math.max(1.5f, strokeW * 0.7f));
                                paint.setColor(freeStickWithAlpha(0xFFFFFFFF, 0.24f * opacityFactor));
                                canvas.drawCircle(touchStartX, touchStartY, radius_dead_zone, paint);
                            }

                            float knobR = Math.max(radius_analog_stick * 1.25f, radius_complete * 0.34f);
                            int knobRgb = (isPressed() && click_state == CLICK_STATE.DOUBLE)
                                    ? com.limelight.ui.AppThemePalette.getCurrent(getContext()).getNeonAccent()
                                    : 0xFFFFFFFF;
                            paint.setShader(null);
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(freeStickWithAlpha(0xFF1E283D, 0.78f * opacityFactor));
                            canvas.drawCircle(position_stick_x, position_stick_y, knobR, paint);

                            if (strokeW > 0.1f) {
                                paint.setStyle(Paint.Style.STROKE);
                                paint.setStrokeWidth(Math.max(1.5f, strokeW * 0.85f));
                                paint.setColor(freeStickWithAlpha(knobRgb, 0.92f * opacityFactor));
                                canvas.drawCircle(position_stick_x, position_stick_y, knobR, paint);
                            }
                        }
                        break;
                    }
                }
                return;
            }

            switch (stick_state) {
                case NO_MOVEMENT: {
                    paint.setColor(Color.MAGENTA);
                    canvas.drawCircle(getWidth() / 2, getHeight() / 2, radius_analog_stick, paint);
                    break;
                }

                case MOVED_IN_DEAD_ZONE:
                case MOVED_ACTIVE: {
                    paint.setColor(bgCircleColor);
                    paint.setStyle(Paint.Style.FILL_AND_STROKE);
                    canvas.drawCircle(touchStartX, touchStartY, radius_complete, paint);

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(strokeCircleColor);
                    canvas.drawCircle(touchStartX, touchStartY, radius_dead_zone, paint);
                    canvas.drawCircle(position_stick_x, position_stick_y, radius_analog_stick, paint);
                    break;
                }
            }
        }
    }

    private static int freeStickWithAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private int bgCircleColor=0x2BF5F5F9;
    private int strokeCircleColor=0xFF8F8F8F;
    public void setBgOpacity() {
        int hexOpacity = getEffectiveOscOpacity() * 255 / 100;
        this.bgCircleColor = (hexOpacity << 24) | (bgCircleColor & 0x00FFFFFF);
        this.strokeCircleColor = (hexOpacity << 24) | (pressedColor & 0x00FFFFFF);
        invalidate();
    }
    @Override
    public void setOpacity(int opacity) {
        super.setOpacity(opacity);
        setBgOpacity();
    }

    private void updatePosition(long eventTime) {
        float complete = radius_complete - radius_analog_stick;

        // calculate relative way
        float correlated_y = (float) (Math.sin(Math.PI / 2 - movement_angle) * (movement_radius));
        float correlated_x = (float) (Math.cos(Math.PI / 2 - movement_angle) * (movement_radius));

        // update positions
        position_stick_x = touchStartX - correlated_x;
        position_stick_y = touchStartY - correlated_y;

        // Stay active even if we're back in the deadzone because we know the user is actively
        // giving analog stick input and we don't want to snap back into the deadzone.
        // We also release the deadzone if the user keeps the stick pressed for a bit to allow
        // them to make precise movements.
        stick_state = (stick_state == AnalogStickFree.STICK_STATE.MOVED_ACTIVE ||
                eventTime - timeLastClick > timeoutDeadzone ||
                movement_radius > radius_dead_zone) ?
                AnalogStickFree.STICK_STATE.MOVED_ACTIVE : AnalogStickFree.STICK_STATE.MOVED_IN_DEAD_ZONE;

        //  trigger move event if state active
        if (stick_state == AnalogStickFree.STICK_STATE.MOVED_ACTIVE) {
            notifyOnMovement(-correlated_x / complete, correlated_y / complete);
        }
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        // save last click state
        CLICK_STATE lastClickState = click_state;
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        // Set touchStartX/Y BEFORE computing relative_x/y so ACTION_DOWN spawns cleanly at the finger
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (!bIsFingerOnScreen) {
                touchID = event.getPointerId(actionIndex);
                touchStartX = event.getX(actionIndex);
                touchStartY = event.getY(actionIndex);
                bIsFingerOnScreen = true;
            }
        }

        float curX = event.getX();
        float curY = event.getY();
        if (bIsFingerOnScreen) {
            int idx = event.findPointerIndex(touchID);
            if (idx >= 0) {
                curX = event.getX(idx);
                curY = event.getY(idx);
            }
        }

        // get absolute way for each axis
        relative_x = -(touchStartX - curX);
        relative_y = -(touchStartY - curY);

        // get radius and angel of movement from center
        movement_radius = getMovementRadius(relative_x, relative_y);
        movement_angle = getAngle(relative_x, relative_y);

        // chop radius if out of outer circle or near the edge
        if (movement_radius > (radius_complete - radius_analog_stick)) {
            movement_radius = radius_complete - radius_analog_stick;
        }
        // handle event depending on action
        switch (action) {
            // down event (touch event)
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (touchID == event.getPointerId(actionIndex)) {
                    touchX = curX;
                    touchY = curY;

                    // set to dead zoned, will be corrected in update position if necessary
                    stick_state = STICK_STATE.MOVED_IN_DEAD_ZONE;
                    // check for double click
                    if (lastClickState == CLICK_STATE.SINGLE &&
                            timeLastClick + timeoutDoubleClick > System.currentTimeMillis()) {
                        click_state = CLICK_STATE.DOUBLE;
                        notifyOnDoubleClick();
                    } else {
                        click_state = CLICK_STATE.SINGLE;
                        notifyOnClick();
                    }
                    virtualController.triggerHapticPress();
                    // reset last click timestamp
                    timeLastClick = System.currentTimeMillis();
                    // set item pressed and update
                    setPressed(true);

                    updatePosition(event.getEventTime());
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    if (touchID == event.getPointerId(i)) {
                        touchX = event.getX(i);
                        touchY = event.getY(i);

                        updatePosition(event.getEventTime());
                    }
                }
                break;
            }
            // up event (revoke touch)
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                if (action == MotionEvent.ACTION_CANCEL || touchID == event.getPointerId(actionIndex)) {
                    if (isPressed()) {
                        virtualController.triggerHapticRelease();
                    }
                    setPressed(false);
                    bIsFingerOnScreen = false;
                }
                break;
            }
        }

        if (isPressed()) {
            updatePosition(event.getEventTime());
            // when is pressed calculate new positions (will trigger movement if necessary)
        } else {
            stick_state = STICK_STATE.NO_MOVEMENT;
            notifyOnRevoke();

            // not longer pressed reset analog stick
            notifyOnMovement(0, 0);
        }
        // refresh view
        invalidate();
        // accept the touch event
        return true;
    }


//    @Override
//    public boolean onElementTouchEvent(MotionEvent event) {
//        // save last click state
//        AnalogStick2.CLICK_STATE lastClickState = click_state;
//
//        // get absolute way for each axis
//        relative_x = -(getWidth() / 2 - event.getX());
//        relative_y = -(getHeight() / 2 - event.getY());
//
//        // get radius and angel of movement from center
//        movement_radius = getMovementRadius(relative_x, relative_y);
//        movement_angle = getAngle(relative_x, relative_y);
//
//        // pass touch event to parent if out of outer circle
//        if (movement_radius > radius_complete && !isPressed())
//            return false;
//
//        // chop radius if out of outer circle or near the edge
//        if (movement_radius > (radius_complete - radius_analog_stick)) {
//            movement_radius = radius_complete - radius_analog_stick;
//        }
//
//        // handle event depending on action
//        switch (event.getActionMasked()) {
//            // down event (touch event)
//            case MotionEvent.ACTION_DOWN: {
//                // set to dead zoned, will be corrected in update position if necessary
//                stick_state = AnalogStick2.STICK_STATE.MOVED_IN_DEAD_ZONE;
//                // check for double click
//                if (lastClickState == AnalogStick2.CLICK_STATE.SINGLE &&
//                        event.getEventTime() - timeLastClick <= timeoutDoubleClick) {
//                    click_state = AnalogStick2.CLICK_STATE.DOUBLE;
//                    notifyOnDoubleClick();
//                } else {
//                    click_state = AnalogStick2.CLICK_STATE.SINGLE;
//                    notifyOnClick();
//                }
//                // reset last click timestamp
//                timeLastClick = event.getEventTime();
//                // set item pressed and update
//                setPressed(true);
//                break;
//            }
//            // up event (revoke touch)
//            case MotionEvent.ACTION_CANCEL:
//            case MotionEvent.ACTION_UP: {
//                setPressed(false);
//                break;
//            }
//        }
//
//        if (isPressed()) {
//            // when is pressed calculate new positions (will trigger movement if necessary)
//            updatePosition(event.getEventTime());
//        } else {
//            stick_state = AnalogStick2.STICK_STATE.NO_MOVEMENT;
//            notifyOnRevoke();
//
//            // not longer pressed reset analog stick
//            notifyOnMovement(0, 0);
//        }
//        // refresh view
//        invalidate();
//        // accept the touch event
//        return true;
//    }
}
