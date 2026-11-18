package com.limelight.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Color;
import android.graphics.PixelFormat;
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
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.AppThemePalette;
import com.limelight.ui.PortalConnectingView;

import java.util.ArrayList;
import java.util.Iterator;

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

            PreferenceConfiguration config = PreferenceConfiguration.readPreferences(activity);
            boolean useCardMode = "card".equals(config.connectingAnimationStyle);

            if (useCardMode) {
                dialog = buildCardDialog();
            } else {
                dialog = buildFullscreenDialog();
            }

            dialog.setOnCancelListener(this);
            if (finish) {
                dialog.setCancelable(true);
                dialog.setCanceledOnTouchOutside(false);
            } else {
                dialog.setCancelable(false);
                dialog.setCanceledOnTouchOutside(false);
            }

            synchronized (rundownDialogs) {
                if (dismissed) {
                    return;
                }
                rundownDialogs.add(this);
                if (useCardMode) {
                    UiHelper.styleDialogSquircle(dialog);
                }
                dialog.show();
                if (useCardMode) {
                    UiHelper.styleDialogSquircle(dialog);
                }
            }
        } else {
            synchronized (rundownDialogs) {
                rundownDialogs.remove(this);
            }
            dismissSafely(dialog);
        }
    }

    private Dialog buildFullscreenDialog() {
        Dialog d = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        AppThemePalette palette = AppThemePalette.getCurrent(activity);
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int minDimPx = Math.min(dm.widthPixels, dm.heightPixels);
        int portalSize = Math.min(dp(220), Math.round(minDimPx * 0.44f));

        FrameLayout root = new FrameLayout(activity);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        GradientDrawable backdrop = new GradientDrawable();
        backdrop.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        backdrop.setGradientCenter(0.5f, 0.46f);
        backdrop.setGradientRadius(Math.max(dm.widthPixels, dm.heightPixels) * 0.65f);
        backdrop.setColors(new int[]{
                AppThemePalette.withAlphaInt(palette.surfaceVariant, 210),
                AppThemePalette.withAlphaInt(palette.background, 234),
                0xF204060D
        });
        root.setBackground(backdrop);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(dp(28), dp(20), dp(28), dp(20));

        PortalConnectingView portalView = new PortalConnectingView(activity);
        LinearLayout.LayoutParams portalLp = new LinearLayout.LayoutParams(portalSize, portalSize);
        portalView.setLayoutParams(portalLp);
        column.addView(portalView);

        TextView titleView = new TextView(activity);
        titleView.setText(title != null ? title : "");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleView.setLetterSpacing(0.01f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(12);
        titleView.setLayoutParams(titleLp);
        column.addView(titleView);

        messageView = new TextView(activity);
        messageView.setText(currentMessage != null ? currentMessage : "");
        messageView.setTextColor(0xD8E8F2FF);
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        messageView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        msgLp.topMargin = dp(6);
        messageView.setLayoutParams(msgLp);
        column.addView(messageView);

        if (finish) {
            TextView cancelHint = new TextView(activity);
            cancelHint.setText(R.string.conn_cancel_hint);
            cancelHint.setTextColor(0xB8FFFFFF);
            cancelHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            cancelHint.setGravity(Gravity.CENTER);
            cancelHint.setPadding(dp(14), dp(6), dp(14), dp(6));

            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setShape(GradientDrawable.RECTANGLE);
            pillBg.setCornerRadius(dp(999));
            pillBg.setColor(0x1EFFFFFF);
            pillBg.setStroke(Math.max(1, dp(1)), 0x33FFFFFF);
            cancelHint.setBackground(pillBg);

            LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hintLp.topMargin = dp(18);
            cancelHint.setLayoutParams(hintLp);
            column.addView(cancelHint);
        }

        FrameLayout.LayoutParams colLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(column, colLp);
        d.setContentView(root);

        Window window = d.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setFormat(PixelFormat.TRANSLUCENT);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                    WindowManager.LayoutParams lp = window.getAttributes();
                    lp.setBlurBehindRadius(dp(22));
                    window.setAttributes(lp);
                } catch (Throwable ignored) {
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.setStatusBarColor(Color.TRANSPARENT);
                window.setNavigationBarColor(Color.TRANSPARENT);
            }
            View decor = window.getDecorView();
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        return d;
    }

    private Dialog buildCardDialog() {
        Dialog d = new Dialog(activity, R.style.OneUI_DialogTheme);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int minDimPx = Math.min(dm.widthPixels, dm.heightPixels);
        int portalSize = Math.min(dp(140), Math.round(minDimPx * 0.34f));

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setMinimumWidth(dp(280));
        column.setPadding(dp(24), dp(22), dp(24), dp(22));

        PortalConnectingView portalView = new PortalConnectingView(activity);
        LinearLayout.LayoutParams portalLp = new LinearLayout.LayoutParams(portalSize, portalSize);
        portalView.setLayoutParams(portalLp);
        column.addView(portalView);

        TextView titleView = new TextView(activity);
        titleView.setText(title != null ? title : "");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(8);
        titleView.setLayoutParams(titleLp);
        column.addView(titleView);

        messageView = new TextView(activity);
        messageView.setText(currentMessage != null ? currentMessage : "");
        messageView.setTextColor(0xD0E4F0FF);
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        messageView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        msgLp.topMargin = dp(6);
        messageView.setLayoutParams(msgLp);
        column.addView(messageView);

        if (finish) {
            TextView cancelHint = new TextView(activity);
            cancelHint.setText(R.string.conn_cancel_hint);
            cancelHint.setTextColor(0x8AFFFFFF);
            cancelHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            cancelHint.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hintLp.topMargin = dp(10);
            cancelHint.setLayoutParams(hintLp);
            column.addView(cancelHint);
        }

        d.setContentView(column);
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
}
