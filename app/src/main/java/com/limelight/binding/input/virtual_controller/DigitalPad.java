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
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        paint.setTextSize(getPercent(getCorrectWidth(), 20));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(getDefaultStrokeWidth());
        //虚拟手柄皮肤 (Vector style)
        if(!PreferenceConfiguration.readPreferences(getContext()).enableOnScreenStyleOfficial) {
            int oscOpacity = PreferenceConfiguration.readPreferences(getContext()).oscOpacity;
            float opacityFactor = Math.max(0.05f, Math.min(1.0f, oscOpacity / 100f));

            paint.setAntiAlias(true);
            float w = getWidth();
            float h = getHeight();
            float cx = w * 0.5f;
            float cy = h * 0.5f;
            float minDim = Math.min(w, h);
            float strokeW = Math.max(2f, minDim * 0.018f);
            float outerR = Math.max(8f, (minDim - strokeW) * 0.5f - 4f);

            // Outer translucent disc & crisp ring
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(dpadWithAlpha(0xFFFFFFFF, 0.16f * opacityFactor));
            canvas.drawCircle(cx, cy, outerR, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeW);
            paint.setColor(dpadWithAlpha(0xFFFFFFFF, 0.45f * opacityFactor));
            canvas.drawCircle(cx, cy, outerR, paint);

            // Subtle center pivot ring
            paint.setColor(dpadWithAlpha(0xFFFFFFFF, 0.25f * opacityFactor));
            paint.setStrokeWidth(Math.max(1.5f, strokeW * 0.75f));
            canvas.drawCircle(cx, cy, outerR * 0.18f, paint);

            // 4 crisp directional petals (handles cardinals & diagonals smoothly)
            boolean leftOn = (direction & DIGITAL_PAD_DIRECTION_LEFT) != 0;
            boolean upOn = (direction & DIGITAL_PAD_DIRECTION_UP) != 0;
            boolean rightOn = (direction & DIGITAL_PAD_DIRECTION_RIGHT) != 0;
            boolean downOn = (direction & DIGITAL_PAD_DIRECTION_DOWN) != 0;

            drawDpadPetal(canvas, cx, cy, outerR, -90f, upOn, opacityFactor, strokeW);
            drawDpadPetal(canvas, cx, cy, outerR, 0f, rightOn, opacityFactor, strokeW);
            drawDpadPetal(canvas, cx, cy, outerR, 90f, downOn, opacityFactor, strokeW);
            drawDpadPetal(canvas, cx, cy, outerR, 180f, leftOn, opacityFactor, strokeW);

            boolean bIsMoving = virtualController.getControllerMode() == VirtualController.ControllerMode.MoveButtons;
            boolean bIsResizing = virtualController.getControllerMode() == VirtualController.ControllerMode.ResizeButtons;
            boolean bIsEnable = virtualController.getControllerMode() == VirtualController.ControllerMode.DisableEnableButtons;

            if (bIsMoving || bIsResizing || bIsEnable) {
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

    private void drawDpadPetal(Canvas canvas, float cx, float cy, float outerR, float angleDeg,
                               boolean active, float opacityFactor, float strokeW) {
        canvas.save();
        canvas.rotate(angleDeg, cx, cy);

        // Sleek tapered rounded D-pad arm pointing along +X
        float startX = cx + outerR * 0.25f;
        float tipX = cx + outerR * 0.86f;
        float shoulderX = cx + outerR * 0.72f;
        float halfH = outerR * 0.21f;

        dpadPath.reset();
        dpadPath.moveTo(startX, -halfH * 0.78f);
        dpadPath.lineTo(shoulderX, -halfH);
        dpadPath.quadTo(tipX, -halfH * 0.35f, tipX, 0f);
        dpadPath.quadTo(tipX, halfH * 0.35f, shoulderX, halfH);
        dpadPath.lineTo(startX, halfH * 0.78f);
        dpadPath.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(dpadWithAlpha(0xFFFFFFFF, (active ? 0.62f : 0.16f) * opacityFactor));
        canvas.drawPath(dpadPath, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, strokeW * 0.9f));
        paint.setColor(dpadWithAlpha(0xFFFFFFFF, (active ? 0.95f : 0.45f) * opacityFactor));
        canvas.drawPath(dpadPath, paint);

        // Crisp inner directional chevron tick
        float chevX = cx + outerR * 0.68f;
        float chevBackX = cx + outerR * 0.56f;
        float chevH = outerR * 0.10f;
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(Math.max(2f, strokeW));
        paint.setColor(dpadWithAlpha(0xFFFFFFFF, (active ? 1.0f : 0.72f) * opacityFactor));
        canvas.drawLine(chevBackX, -chevH, chevX, 0f, paint);
        canvas.drawLine(chevX, 0f, chevBackX, chevH, paint);

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
                direction = 0;

                if (event.getX() < getPercent(getWidth(), 33)) {
                    direction |= DIGITAL_PAD_DIRECTION_LEFT;
                }
                if (event.getX() > getPercent(getWidth(), 66)) {
                    direction |= DIGITAL_PAD_DIRECTION_RIGHT;
                }
                if (event.getY() > getPercent(getHeight(), 66)) {
                    direction |= DIGITAL_PAD_DIRECTION_DOWN;
                }
                if (event.getY() < getPercent(getHeight(), 33)) {
                    direction |= DIGITAL_PAD_DIRECTION_UP;
                }
                newDirectionCallback(direction);
                invalidate();

                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                direction = 0;
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
