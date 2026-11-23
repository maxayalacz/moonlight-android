/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;

import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a digital button on screen element. It is used to get click and double click user input.
 */
public class DigitalButton extends VirtualControllerElement {

    /**
     * Listener interface to update registered observers.
     */
    public interface DigitalButtonListener {

        /**
         * onClick event will be fired on button click.
         */
        void onClick();

        /**
         * onLongClick event will be fired on button long click.
         */
        void onLongClick();

        /**
         * onRelease event will be fired on button unpress.
         */
        void onRelease();
    }

    private List<DigitalButtonListener> listeners = new ArrayList<>();
    private String text = "";
    private int icon = -1;

    private int iconPress=-1;
    private long timerLongClickTimeout = 3000;
    private final Runnable longClickRunnable = new Runnable() {
        @Override
        public void run() {
            onLongClickCallback();
        }
    };

    private final Paint paint = new Paint();
    private final RectF rect = new RectF();

    private int layer;
    private DigitalButton movingButton = null;

    boolean inRange(float x, float y) {
        return (this.getX() < x && this.getX() + this.getWidth() > x) &&
                (this.getY() < y && this.getY() + this.getHeight() > y);
    }

    public boolean checkMovement(float x, float y, DigitalButton movingButton) {
        // check if the movement happened in the same layer
        if (movingButton.layer != this.layer) {
            return false;
        }

        // save current pressed state
        boolean wasPressed = isPressed();

        // check if the movement directly happened on the button
        if ((this.movingButton == null || movingButton == this.movingButton)
                && this.inRange(x, y)) {
            // set button pressed state depending on moving button pressed state
            if (this.isPressed() != movingButton.isPressed()) {
                this.setPressed(movingButton.isPressed());
            }
        }
        // check if the movement is outside of the range and the movement button
        // is the saved moving button
        else if (movingButton == this.movingButton) {
            this.setPressed(false);
        }

        // check if a change occurred
        if (wasPressed != isPressed()) {
            if (isPressed()) {
                // is pressed set moving button and emit click event
                this.movingButton = movingButton;
                onClickCallback();
            } else {
                // no longer pressed reset moving button and emit release event
                this.movingButton = null;
                onReleaseCallback();
            }

            invalidate();

            return true;
        }

        return false;
    }

    private void checkMovementForAllButtons(float x, float y) {
        for (VirtualControllerElement element : virtualController.getElements()) {
            if (element != this && element instanceof DigitalButton) {
                ((DigitalButton) element).checkMovement(x, y, this);
            }
        }
    }

    public DigitalButton(VirtualController controller, int elementId, int layer, Context context) {
        super(controller, context, elementId);
        this.layer = layer;
    }

    public void addDigitalButtonListener(DigitalButtonListener listener) {
        listeners.add(listener);
    }

    public void setText(String text) {
        this.text = text;
        invalidate();
    }

    public void setIcon(int id) {
        this.icon = id;
        invalidate();
    }

    public void setIconPress(int iconPress) {
        this.iconPress = iconPress;
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        paint.setAntiAlias(true);

        //皮肤选择 官方皮肤
        if(PreferenceConfiguration.readPreferences(getContext()).enableOnScreenStyleOfficial){
            paint.setTextSize(getPercent(getWidth(), 25));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setStrokeWidth(getDefaultStrokeWidth());
            paint.setColor(isPressed() ? pressedColor:getDefaultColor());
            rect.left = rect.top = paint.getStrokeWidth();
            rect.right = getWidth() - rect.left;
            rect.bottom = getHeight() - rect.top;
            paint.setStyle(Paint.Style.STROKE);
            //方形
            if(PreferenceConfiguration.readPreferences(getContext()).enableKeyboardSquare){
                canvas.drawRect(rect,paint);
            }else{
                canvas.drawOval(rect, paint);
            }
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setStrokeWidth(getDefaultStrokeWidth()/2);
            canvas.drawText(text, getPercent(getWidth(), 50), getPercent(getHeight(), 63), paint);
            return;
        }

        VirtualController.ControllerMode mode = virtualController.getControllerMode();
        boolean isEditMode = mode != VirtualController.ControllerMode.Active;
        int oscOpacity = getEffectiveOscOpacity();
        float opacityFactor = Math.max(0.0f, Math.min(1.0f, oscOpacity / 100f));
        if (isEditMode && opacityFactor < 0.35f) {
            opacityFactor = 0.35f;
        }
        if (mode == VirtualController.ControllerMode.DisableEnableButtons && !enabled) {
            opacityFactor *= 0.38f;
        }
        float w = getWidth();
        float h = getHeight();
        if (opacityFactor > 0.005f) {
            float minDim = Math.min(w, h);
            float outlineScale = getOutlineScaleFactor();
            float strokeW = outlineScale <= 0.01f ? 0f : Math.max(1f, minDim * 0.042f * outlineScale);
            if (elementId == EID_LT || elementId == EID_RT || elementId == EID_LB || elementId == EID_RB
                    || elementId == EID_BACK || elementId == EID_START || elementId == EID_GDB) {
                strokeW = outlineScale <= 0.01f ? 0f : Math.max(1f, minDim * 0.055f * outlineScale);
            }

            int baseRgb = resolveVectorButtonColor();
            boolean pressed = isPressed();
            float fgAlpha = (pressed ? 1.0f : 0.92f) * opacityFactor;

            rect.set(strokeW * 0.5f + 2f, strokeW * 0.5f + 2f, w - strokeW * 0.5f - 2f, h - strokeW * 0.5f - 2f);

            boolean isCircle = (elementId == EID_A || elementId == EID_B
                    || elementId == EID_X || elementId == EID_Y
                    || elementId == EID_LSB || elementId == EID_RSB);

            float cx = w * 0.5f;
            float cy = h * 0.5f;
            float r = Math.max(4f, (minDim - strokeW) * 0.5f - 2f);
            float cornerR = (elementId == EID_TOUCHPAD) ? minDim * 0.18f : minDim * 0.40f;

            boolean drewLiveBlur = false;
            OscLiveBlurHelper blurHelper = virtualController.getLiveBlurHelper();
            if (blurHelper != null) {
                blurClipPath.reset();
                if (isCircle) {
                    blurClipPath.addCircle(cx, cy, r, android.graphics.Path.Direction.CW);
                    blurBounds.set(cx - r, cy - r, cx + r, cy + r);
                } else {
                    blurClipPath.addRoundRect(rect, cornerR, cornerR, android.graphics.Path.Direction.CW);
                    blurBounds.set(rect);
                }
                drewLiveBlur = blurHelper.drawLiveBlurForBounds(canvas, this, blurClipPath, blurBounds, opacityFactor);
            }

            float tintAlpha = (pressed ? 0.56f : (drewLiveBlur ? 0.36f : 0.46f)) * opacityFactor;
            int fillColor = withAlpha(pressed ? 0xFF1A2236 : 0xFF101624, tintAlpha);
            float borderAlpha = (pressed ? 0.95f : 0.60f) * opacityFactor;

            paint.setShader(null);
            if (isCircle) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(fillColor);
                canvas.drawCircle(cx, cy, r, paint);

                if (strokeW > 0.1f) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(strokeW);
                    paint.setColor(withAlpha(baseRgb, borderAlpha));
                    canvas.drawCircle(cx, cy, r, paint);
                }
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(fillColor);
                canvas.drawRoundRect(rect, cornerR, cornerR, paint);

                if (strokeW > 0.1f) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(strokeW);
                    paint.setColor(withAlpha(baseRgb, borderAlpha));
                    canvas.drawRoundRect(rect, cornerR, cornerR, paint);
                }
            }

            // Vector glyph / label
            int fgColor = withAlpha(baseRgb, fgAlpha);
            if (elementId == EID_BACK) {
                drawBackVectorGlyph(canvas, cx, cy, minDim, fgColor);
            } else if (elementId == EID_START) {
                drawStartVectorGlyph(canvas, cx, cy, minDim, fgColor);
            } else if (elementId == EID_GDB) {
                drawGuideVectorGlyph(canvas, cx, cy, minDim, fgColor);
            } else if (elementId == EID_TOUCHPAD) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(withAlpha(baseRgb, fgAlpha * 0.45f));
                canvas.drawCircle(cx, cy, minDim * 0.06f, paint);
            } else if (text != null && !text.isEmpty()) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(fgColor);
                paint.setFakeBoldText(true);
                paint.setTextAlign(Paint.Align.CENTER);
                float textScale = text.length() <= 1 ? 0.42f : 0.36f;
                paint.setTextSize(minDim * textScale);
                Paint.FontMetrics fm = paint.getFontMetrics();
                float textY = cy - (fm.ascent + fm.descent) * 0.5f;
                canvas.drawText(text, cx, textY, paint);
                paint.setFakeBoldText(false);
            }
        }

        boolean bIsMoving = virtualController.getControllerMode() == VirtualController.ControllerMode.MoveButtons;
        boolean bIsResizing = virtualController.getControllerMode() == VirtualController.ControllerMode.ResizeButtons;
        boolean bIsEnable = virtualController.getControllerMode() == VirtualController.ControllerMode.DisableEnableButtons;

        if (bIsMoving || bIsResizing || bIsEnable) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(getDefaultStrokeWidth());
            paint.setColor(isPressed() ? pressedColor : getDefaultColor());
            rect.left = rect.top = paint.getStrokeWidth();
            rect.right = getWidth() - rect.left;
            rect.bottom = getHeight() - rect.top;
            canvas.drawRect(rect, paint);
        }
    }

    private int resolveVectorButtonColor() {
        return 0xFFFFFFFF;
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private void drawBackVectorGlyph(Canvas canvas, float cx, float cy, float minDim, int color) {
        float box = minDim * 0.28f;
        float offset = minDim * 0.08f;
        float r = minDim * 0.05f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, minDim * 0.06f));
        paint.setColor(color);
        rect.set(cx - box * 0.5f - offset, cy - box * 0.5f - offset,
                cx + box * 0.5f - offset, cy + box * 0.5f - offset);
        canvas.drawRoundRect(rect, r, r, paint);
        rect.set(cx - box * 0.5f + offset, cy - box * 0.5f + offset,
                cx + box * 0.5f + offset, cy + box * 0.5f + offset);
        canvas.drawRoundRect(rect, r, r, paint);
    }

    private void drawStartVectorGlyph(Canvas canvas, float cx, float cy, float minDim, int color) {
        float halfW = minDim * 0.22f;
        float dy = minDim * 0.14f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(2f, minDim * 0.07f));
        paint.setColor(color);
        canvas.drawLine(cx - halfW, cy - dy, cx + halfW, cy - dy, paint);
        canvas.drawLine(cx - halfW, cy, cx + halfW, cy, paint);
        canvas.drawLine(cx - halfW, cy + dy, cx + halfW, cy + dy, paint);
    }

    private void drawGuideVectorGlyph(Canvas canvas, float cx, float cy, float minDim, int color) {
        float rOuter = minDim * 0.26f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(2f, minDim * 0.065f));
        paint.setColor(color);
        canvas.drawCircle(cx, cy, rOuter, paint);
        float arm = rOuter * 0.55f;
        canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, paint);
        canvas.drawLine(cx + arm, cy - arm, cx - arm, cy + arm, paint);
    }

    private boolean pressedForHaptic = false;

    private void onClickCallback() {
        _DBG("clicked");
        if (!pressedForHaptic) {
            pressedForHaptic = true;
            virtualController.triggerHapticPress();
        }
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onClick();
        }

        virtualController.getHandler().removeCallbacks(longClickRunnable);
        virtualController.getHandler().postDelayed(longClickRunnable, timerLongClickTimeout);
    }

    private void onLongClickCallback() {
        _DBG("long click");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onLongClick();
        }
    }

    private void onReleaseCallback() {
        _DBG("released");
        if (pressedForHaptic) {
            pressedForHaptic = false;
            virtualController.triggerHapticRelease();
        }
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onRelease();
        }

        // We may be called for a release without a prior click
        virtualController.getHandler().removeCallbacks(longClickRunnable);
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        // get masked (not specific to a pointer) action
        float x = getX() + event.getX();
        float y = getY() + event.getY();
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                movingButton = null;
                setPressed(true);
                onClickCallback();

                invalidate();

                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                checkMovementForAllButtons(x, y);

                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                setPressed(false);
                onReleaseCallback();

                checkMovementForAllButtons(x, y);

                invalidate();

                return true;
            }
            default: {
            }
        }
        return true;
    }
}
