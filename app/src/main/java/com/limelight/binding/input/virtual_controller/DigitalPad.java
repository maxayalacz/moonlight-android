/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

public class DigitalPad extends VirtualControllerElement {
    public final static int DIGITAL_PAD_DIRECTION_NO_DIRECTION = 0;
    int direction = DIGITAL_PAD_DIRECTION_NO_DIRECTION;
    public final static int DIGITAL_PAD_DIRECTION_LEFT = 1;
    public final static int DIGITAL_PAD_DIRECTION_UP = 2;
    public final static int DIGITAL_PAD_DIRECTION_RIGHT = 4;
    public final static int DIGITAL_PAD_DIRECTION_DOWN = 8;
    List<DigitalPadListener> listeners = new ArrayList<>();

    private static final int DPAD_MARGIN = 5;
    private final RectF rect = new RectF();
    private final Path dpadPath = new Path();

    private final Paint paint = new Paint();

    public DigitalPad(VirtualController controller, Context context) {
        super(controller, context, EID_DPAD);
    }

    public void addDigitalPadListener(DigitalPadListener listener) {
        listeners.add(listener);
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        paint.setTextSize(getPercent(getCorrectWidth(), 20));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(getDefaultStrokeWidth());
        //虚拟手柄皮肤 (Vector style)
        if(!PreferenceConfiguration.readPreferences(getContext()).enableOnScreenStyleOfficial) {
            boolean bIsMoving = virtualController.getControllerMode() == VirtualController.ControllerMode.MoveButtons;
            boolean bIsResizing = virtualController.getControllerMode() == VirtualController.ControllerMode.ResizeButtons;
            boolean bIsEnable = virtualController.getControllerMode() == VirtualController.ControllerMode.DisableEnableButtons;
            boolean isEditMode = bIsMoving || bIsResizing || bIsEnable;

            int oscOpacity = getEffectiveOscOpacity();
            float opacityFactor = Math.max(0.0f, Math.min(1.0f, oscOpacity / 100f));
            if (isEditMode && opacityFactor < 0.35f) {
                opacityFactor = 0.35f;
            }
            if (bIsEnable && !enabled) {
                opacityFactor *= 0.38f;
            }
            if (opacityFactor <= 0.005f && !isEditMode) {
                return;
            }

            paint.setAntiAlias(true);
            float w = getWidth();
            float h = getHeight();
            float cx = w * 0.5f;
            float cy = h * 0.5f;
            float minDim = Math.min(w, h);
            float outlineScale = getOutlineScaleFactor();
            float strokeW = outlineScale <= 0.01f ? 0f : Math.max(1f, minDim * 0.018f * outlineScale);

            drawConnectedCrossDpad(canvas, cx, cy, minDim, opacityFactor, strokeW);

            if (isEditMode) {
                paint.setColor(isPressed() ? pressedColor : getDefaultColor());
                paint.setStrokeWidth(getDefaultStrokeWidth());
                rect.left = rect.top = paint.getStrokeWidth();
                rect.right = getWidth() - rect.left;
                rect.bottom = getHeight() - rect.top;
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawRect(rect, paint);
            }
            return;
        }
        //官方皮肤
        if (direction == DIGITAL_PAD_DIRECTION_NO_DIRECTION) {
            // draw no direction rect
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(getDefaultColor());
            canvas.drawRect(
                    getPercent(getWidth(), 36), getPercent(getHeight(), 36),
                    getPercent(getWidth(), 63), getPercent(getHeight(), 63),
                    paint
            );
        }

        // draw left rect
        paint.setColor(
                (direction & DIGITAL_PAD_DIRECTION_LEFT) > 0 ? pressedColor : getDefaultColor());
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                paint.getStrokeWidth()+DPAD_MARGIN, getPercent(getHeight(), 33),
                getPercent(getWidth(), 33), getPercent(getHeight(), 66),
                paint
        );


        // draw up rect
        paint.setColor(
                (direction & DIGITAL_PAD_DIRECTION_UP) > 0 ? pressedColor : getDefaultColor());
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                getPercent(getWidth(), 33), paint.getStrokeWidth()+DPAD_MARGIN,
                getPercent(getWidth(), 66), getPercent(getHeight(), 33),
                paint
        );

        // draw right rect
        paint.setColor(
                (direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0 ? pressedColor : getDefaultColor());
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                getPercent(getWidth(), 66), getPercent(getHeight(), 33),
                getWidth() - (paint.getStrokeWidth()+DPAD_MARGIN), getPercent(getHeight(), 66),
                paint
        );

        // draw down rect
        paint.setColor(
                (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0 ? pressedColor : getDefaultColor());
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                getPercent(getWidth(), 33), getPercent(getHeight(), 66),
                getPercent(getWidth(), 66), getHeight() - (paint.getStrokeWidth()+DPAD_MARGIN),
                paint
        );

        // draw left up line
        paint.setColor((
                        (direction & DIGITAL_PAD_DIRECTION_LEFT) > 0 &&
                                (direction & DIGITAL_PAD_DIRECTION_UP) > 0
                ) ? pressedColor : getDefaultColor()
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                paint.getStrokeWidth()+DPAD_MARGIN, getPercent(getHeight(), 33),
                getPercent(getWidth(), 33), paint.getStrokeWidth()+DPAD_MARGIN,
                paint
        );

        // draw up right line
        paint.setColor((
                        (direction & DIGITAL_PAD_DIRECTION_UP) > 0 &&
                                (direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0
                ) ? pressedColor : getDefaultColor()
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                getPercent(getWidth(), 66), paint.getStrokeWidth()+DPAD_MARGIN,
                getWidth() - (paint.getStrokeWidth()+DPAD_MARGIN), getPercent(getHeight(), 33),
                paint
        );

        // draw right down line
        paint.setColor((
                        (direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0 &&
                                (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0
                ) ? pressedColor : getDefaultColor()
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                getWidth()-paint.getStrokeWidth(), getPercent(getHeight(), 66),
                getPercent(getWidth(), 66), getHeight()-(paint.getStrokeWidth()+DPAD_MARGIN),
                paint
        );

        // draw down left line
        paint.setColor((
                        (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0 &&
                                (direction & DIGITAL_PAD_DIRECTION_LEFT) > 0
                ) ? pressedColor : getDefaultColor()
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                getPercent(getWidth(), 33), getHeight()-(paint.getStrokeWidth()+DPAD_MARGIN),
                paint.getStrokeWidth()+DPAD_MARGIN, getPercent(getHeight(), 66),
                paint
        );
    }

    private void drawConnectedCrossDpad(Canvas canvas, float cx, float cy, float minDim,
                                        float opacityFactor, float strokeW) {
        float halfSpan = Math.max(12f, minDim * 0.44f);
        float armW = halfSpan * 0.34f;
        float tipR = armW * 0.34f;
        float filletR = armW * 0.24f;

        // Single connected classic '+' cross path centered cleanly at (cx, cy)
        dpadPath.reset();
        dpadPath.moveTo(cx - armW, cy - halfSpan + tipR);
        dpadPath.quadTo(cx - armW, cy - halfSpan, cx - armW + tipR, cy - halfSpan);
        dpadPath.lineTo(cx + armW - tipR, cy - halfSpan);
        dpadPath.quadTo(cx + armW, cy - halfSpan, cx + armW, cy - halfSpan + tipR);

        dpadPath.lineTo(cx + armW, cy - armW - filletR);
        dpadPath.quadTo(cx + armW, cy - armW, cx + armW + filletR, cy - armW);

        dpadPath.lineTo(cx + halfSpan - tipR, cy - armW);
        dpadPath.quadTo(cx + halfSpan, cy - armW, cx + halfSpan, cy - armW + tipR);
        dpadPath.lineTo(cx + halfSpan, cy + armW - tipR);
        dpadPath.quadTo(cx + halfSpan, cy + armW, cx + halfSpan - tipR, cy + armW);

        dpadPath.lineTo(cx + armW + filletR, cy + armW);
        dpadPath.quadTo(cx + armW, cy + armW, cx + armW, cy + armW + filletR);

        dpadPath.lineTo(cx + armW, cy + halfSpan - tipR);
        dpadPath.quadTo(cx + armW, cy + halfSpan, cx + armW - tipR, cy + halfSpan);
        dpadPath.lineTo(cx - armW + tipR, cy + halfSpan);
        dpadPath.quadTo(cx - armW, cy + halfSpan, cx - armW, cy + halfSpan - tipR);

        dpadPath.lineTo(cx - armW, cy + armW + filletR);
        dpadPath.quadTo(cx - armW, cy + armW, cx - armW - filletR, cy + armW);

        dpadPath.lineTo(cx - halfSpan + tipR, cy + armW);
        dpadPath.quadTo(cx - halfSpan, cy + armW, cx - halfSpan, cy + armW - tipR);
        dpadPath.lineTo(cx - halfSpan, cy - armW + tipR);
        dpadPath.quadTo(cx - halfSpan, cy - armW, cx - halfSpan + tipR, cy - armW);

        dpadPath.lineTo(cx - armW - filletR, cy - armW);
        dpadPath.quadTo(cx - armW, cy - armW, cx - armW, cy - armW - filletR);
        dpadPath.close();

        rect.set(cx - halfSpan, cy - halfSpan, cx + halfSpan, cy + halfSpan);
        OscLiveBlurHelper blurHelper = virtualController.getLiveBlurHelper();
        boolean drewLiveBlur = blurHelper != null
                && blurHelper.drawLiveBlurForBounds(canvas, this, dpadPath, rect, opacityFactor);

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(dpadWithAlpha(0xFF101624, (drewLiveBlur ? 0.36f : 0.46f) * opacityFactor));
        canvas.drawPath(dpadPath, paint);

        if (strokeW > 0.1f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeW);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(dpadWithAlpha(0xFFFFFFFF, 0.58f * opacityFactor));
            canvas.drawPath(dpadPath, paint);
        }

        boolean leftOn = (direction & DIGITAL_PAD_DIRECTION_LEFT) != 0;
        boolean upOn = (direction & DIGITAL_PAD_DIRECTION_UP) != 0;
        boolean rightOn = (direction & DIGITAL_PAD_DIRECTION_RIGHT) != 0;
        boolean downOn = (direction & DIGITAL_PAD_DIRECTION_DOWN) != 0;

        float armInset = Math.max(3f, strokeW * 1.4f);
        float chevDist = halfSpan * 0.67f;
        float chevSize = armW * 0.36f;

        drawArmHighlightAndChevron(canvas, cx, cy, armW, halfSpan, armInset, chevDist, chevSize, -90f, upOn, opacityFactor, strokeW);
        drawArmHighlightAndChevron(canvas, cx, cy, armW, halfSpan, armInset, chevDist, chevSize, 0f, rightOn, opacityFactor, strokeW);
        drawArmHighlightAndChevron(canvas, cx, cy, armW, halfSpan, armInset, chevDist, chevSize, 90f, downOn, opacityFactor, strokeW);
        drawArmHighlightAndChevron(canvas, cx, cy, armW, halfSpan, armInset, chevDist, chevSize, 180f, leftOn, opacityFactor, strokeW);
    }

    private void drawArmHighlightAndChevron(Canvas canvas, float cx, float cy,
                                            float armW, float halfSpan, float armInset,
                                            float chevDist, float chevSize, float angleDeg,
                                            boolean active, float opacityFactor, float strokeW) {
        canvas.save();
        canvas.rotate(angleDeg, cx, cy);

        int accentRgb = com.limelight.ui.AppThemePalette.getCurrent(getContext()).getNeonAccent();
        if (active) {
            rect.set(
                    cx + armW * 0.45f,
                    cy - armW + armInset,
                    cx + halfSpan - armInset,
                    cy + armW - armInset);
            float r = armW * 0.28f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(dpadWithAlpha(accentRgb, 0.42f * opacityFactor));
            canvas.drawRoundRect(rect, r, r, paint);
        }

        // Crisp directional chevron pointing along +X (centered at y = cy)
        float tipX = cx + chevDist + chevSize * 0.45f;
        float backX = cx + chevDist - chevSize * 0.55f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(Math.max(2f, strokeW * 1.15f));
        paint.setColor(dpadWithAlpha(active ? accentRgb : 0xFFFFFFFF, (active ? 1.0f : 0.85f) * opacityFactor));
        canvas.drawLine(backX, cy - chevSize, tipX, cy, paint);
        canvas.drawLine(tipX, cy, backX, cy + chevSize, paint);

        canvas.restore();
    }

    private static int dpadWithAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    public Drawable rotateDrawable(Drawable vectorDrawable, float angle) {
        int width = vectorDrawable.getIntrinsicWidth();
        int height = vectorDrawable.getIntrinsicHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        vectorDrawable.draw(canvas);

        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        return new BitmapDrawable(getResources(), rotatedBitmap);
    }

    private void newDirectionCallback(int direction) {
        _DBG("direction: " + direction);

        // notify listeners
        for (DigitalPadListener listener : listeners) {
            listener.onDirectionChange(direction);
        }
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        // get masked (not specific to a pointer) action
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                int prevDirection = direction;
                int newDirection = 0;

                if (event.getX() < getPercent(getWidth(), 33)) {
                    newDirection |= DIGITAL_PAD_DIRECTION_LEFT;
                }
                if (event.getX() > getPercent(getWidth(), 66)) {
                    newDirection |= DIGITAL_PAD_DIRECTION_RIGHT;
                }
                if (event.getY() > getPercent(getHeight(), 66)) {
                    newDirection |= DIGITAL_PAD_DIRECTION_DOWN;
                }
                if (event.getY() < getPercent(getHeight(), 33)) {
                    newDirection |= DIGITAL_PAD_DIRECTION_UP;
                }
                if (newDirection != prevDirection) {
                    direction = newDirection;
                    if (newDirection != DIGITAL_PAD_DIRECTION_NO_DIRECTION) {
                        virtualController.triggerHapticPress();
                    } else if (prevDirection != DIGITAL_PAD_DIRECTION_NO_DIRECTION) {
                        virtualController.triggerHapticRelease();
                    }
                    newDirectionCallback(direction);
                    invalidate();
                }

                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                int prevDirection = direction;
                direction = DIGITAL_PAD_DIRECTION_NO_DIRECTION;
                if (prevDirection != DIGITAL_PAD_DIRECTION_NO_DIRECTION) {
                    virtualController.triggerHapticRelease();
                }
                newDirectionCallback(direction);
                invalidate();

                return true;
            }
            default: {
            }
        }

        return true;
    }

    public interface DigitalPadListener {
        void onDirectionChange(int direction);
    }
}
