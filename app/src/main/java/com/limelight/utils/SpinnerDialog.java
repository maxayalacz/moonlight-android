package com.limelight.utils;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.limelight.R;
import com.limelight.ui.AppThemePalette;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Compact One UI frosted-glass squircle loading dialog used for manual PC addition,
 * network tests, app list refresh, help viewer, and shortcut handoffs.
 */
public class SpinnerDialog implements Runnable, OnCancelListener {
    private final String title;
    private volatile String currentMessage;
    private final Activity activity;
    private Dialog dialog;
    private TextView messageView;
    private final boolean finish;
    private volatile boolean dismissed = false;

    private static final ArrayList<SpinnerDialog> rundownDialogs = new ArrayList<>();

    private SpinnerDialog(Activity activity, String title, String message, boolean finish) {
        this.activity = activity;
        this.title = title;
        this.currentMessage = message;
        this.dialog = null;
        this.finish = finish;
    }

    public static SpinnerDialog displayDialog(Activity activity, String title, String message, boolean finish) {
        SpinnerDialog spinner = new SpinnerDialog(activity, title, message, finish);
        activity.runOnUiThread(spinner);
        return spinner;
    }

    public static void closeDialogs(Activity activity) {
        final ArrayList<Dialog> toDismiss = new ArrayList<>();
        synchronized (rundownDialogs) {
            Iterator<SpinnerDialog> i = rundownDialogs.iterator();
            while (i.hasNext()) {
                SpinnerDialog entry = i.next();
                if (entry.activity == activity) {
                    entry.dismissed = true;
                    i.remove();
                    if (entry.dialog != null) {
                        toDismiss.add(entry.dialog);
                    }
                }
            }
        }
        if (!toDismiss.isEmpty()) {
            Runnable dismissAction = () -> {
                for (Dialog d : toDismiss) {
                    dismissSafely(d);
                }
            };
            if (Looper.myLooper() == Looper.getMainLooper()) {
                dismissAction.run();
            } else if (!activity.isFinishing()) {
                activity.runOnUiThread(dismissAction);
            }
        }
    }

    public void dismiss() {
        dismissed = true;
        activity.runOnUiThread(this);
    }

    public void dismissWithPortalReveal() {
        dismiss();
    }

    public static void displayDisconnectPortal(final Activity activity, final Runnable onComplete) {
        if (onComplete == null) {
            return;
        }
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            onComplete.run();
            return;
        }
        activity.runOnUiThread(onComplete);
    }

    public void setMessage(final String message) {
        this.currentMessage = message;
        activity.runOnUiThread(() -> {
            if (messageView != null) {
                messageView.setText(currentMessage != null ? currentMessage : "");
            }
        });
    }

    private static void dismissSafely(Dialog d) {
        if (d == null) {
            return;
        }
        try {
            if (d.isShowing()) {
                d.dismiss();
            }
        } catch (IllegalArgumentException | IllegalStateException ignored) {
        }
    }

    @Override
    public void run() {
        if (activity.isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed())) {
            return;
        }

        if (dialog == null) {
            if (dismissed) {
                return;
            }

            dialog = buildLoadingCardDialog();
            dialog.setOnCancelListener(this);
            dialog.setCancelable(finish);
            dialog.setCanceledOnTouchOutside(false);

            synchronized (rundownDialogs) {
                if (dismissed) {
                    return;
                }
                rundownDialogs.add(this);
                UiHelper.styleDialogSquircle(dialog);
                dialog.show();
                UiHelper.styleDialogSquircle(dialog);
            }
        } else {
            synchronized (rundownDialogs) {
                rundownDialogs.remove(this);
            }
            dismissSafely(dialog);
        }
    }

    private Dialog buildLoadingCardDialog() {
        Dialog d = new Dialog(activity, R.style.OneUI_DialogTheme);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int cardWidth = Math.min(dp(320), Math.max(dp(260), dm.widthPixels - dp(48)));

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(dp(24), dp(24), dp(24), dp(22));
        column.setLayoutParams(new ViewGroup.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        LoadingRingView ring = new LoadingRingView(activity);
        int ringSize = dp(48);
        LinearLayout.LayoutParams ringLp = new LinearLayout.LayoutParams(ringSize, ringSize);
        ring.setLayoutParams(ringLp);
        column.addView(ring);

        if (title != null && !title.isEmpty()) {
            TextView titleView = new TextView(activity);
            titleView.setText(title);
            titleView.setTextColor(Color.WHITE);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
            titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            titleView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleLp.topMargin = dp(16);
            titleView.setLayoutParams(titleLp);
            column.addView(titleView);
        }

        messageView = new TextView(activity);
        messageView.setText(currentMessage != null ? currentMessage : "");
        messageView.setTextColor(0xC8DCE8FF);
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        messageView.setLineSpacing(dp(2), 1.0f);
        messageView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        msgLp.topMargin = dp(8);
        messageView.setLayoutParams(msgLp);
        column.addView(messageView);

        if (finish) {
            TextView cancelBtn = new TextView(activity);
            cancelBtn.setText(R.string.cancel);
            cancelBtn.setTextColor(0xEBFFFFFF);
            cancelBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            cancelBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            cancelBtn.setGravity(Gravity.CENTER);
            cancelBtn.setPadding(dp(22), dp(9), dp(22), dp(9));

            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setShape(GradientDrawable.RECTANGLE);
            pillBg.setCornerRadius(dp(999));
            pillBg.setColor(0x24FFFFFF);
            pillBg.setStroke(Math.max(1, dp(1)), 0x3DFFFFFF);
            cancelBtn.setBackground(pillBg);
            cancelBtn.setOnClickListener(v -> {
                if (dialog != null && dialog.isShowing()) {
                    dialog.cancel();
                }
            });

            LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cancelLp.topMargin = dp(18);
            cancelBtn.setLayoutParams(cancelLp);
            column.addView(cancelBtn);
        }

        d.setContentView(column);
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }
        return d;
    }

    private int dp(int dp) {
        return Math.max(1, Math.round(UiHelper.dpToPx(activity, dp)));
    }

    @Override
    public void onCancel(DialogInterface dialogInterface) {
        synchronized (rundownDialogs) {
            rundownDialogs.remove(this);
        }
        dismissed = true;

        // This will only be called if finish was true, so we don't need to check again
        activity.finish();
    }

    /**
     * Lightweight, smooth rotating gradient loading ring styled for One UI frosted cards.
     */
    private static final class LoadingRingView extends View {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arcBounds = new RectF();
        private final int accentColor;
        private final int highlightColor;
        private final float strokePx;
        private ValueAnimator spinAnimator;
        private float rotationDeg = 0f;
        private int cachedSize = -1;

        LoadingRingView(Context context) {
            super(context);
            AppThemePalette palette = AppThemePalette.getCurrent(context);
            accentColor = palette.accentLight;
            highlightColor = palette.highlight;
            strokePx = UiHelper.dpToPx(context, 3.6f);

            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeWidth(strokePx);
            trackPaint.setColor(0x24FFFFFF);

            arcPaint.setStyle(Paint.Style.STROKE);
            arcPaint.setStrokeWidth(strokePx);
            arcPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (spinAnimator == null) {
                spinAnimator = ValueAnimator.ofFloat(0f, 360f);
                spinAnimator.setDuration(920L);
                spinAnimator.setRepeatCount(ValueAnimator.INFINITE);
                spinAnimator.setInterpolator(new LinearInterpolator());
                spinAnimator.addUpdateListener(anim -> {
                    rotationDeg = (float) anim.getAnimatedValue();
                    invalidate();
                });
                spinAnimator.start();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            if (spinAnimator != null) {
                spinAnimator.cancel();
                spinAnimator = null;
            }
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            int size = Math.min(w, h);
            if (size <= 0) {
                return;
            }
            float cx = w * 0.5f;
            float cy = h * 0.5f;
            float r = (size - strokePx) * 0.5f - 1f;
            if (r <= 1f) {
                return;
            }

            if (size != cachedSize) {
                cachedSize = size;
                arcBounds.set(cx - r, cy - r, cx + r, cy + r);
                arcPaint.setShader(new SweepGradient(
                        cx, cy,
                        new int[]{
                                AppThemePalette.withAlpha(accentColor, 0.0f),
                                AppThemePalette.withAlpha(accentColor, 0.45f),
                                highlightColor,
                                0xFFFFFFFF
                        },
                        new float[]{0.0f, 0.35f, 0.72f, 0.96f}));
            }

            canvas.drawCircle(cx, cy, r, trackPaint);
            canvas.save();
            canvas.rotate(rotationDeg, cx, cy);
            canvas.drawArc(arcBounds, 18f, 324f, false, arcPaint);
            canvas.restore();
        }
    }
}
