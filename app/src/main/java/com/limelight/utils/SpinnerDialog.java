package com.limelight.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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
import com.limelight.ui.PortalConnectingView;

import java.util.ArrayList;
import java.util.Iterator;

public class SpinnerDialog implements Runnable, OnCancelListener {
    private final String title;
    private volatile String currentMessage;
    private final Activity activity;
    private Dialog dialog;
    private PortalConnectingView portalView;
    private View topHudContainer;
    private View bottomCancelView;
    private TextView messageView;
    private final boolean finish;
    private volatile boolean dismissed = false;
    private volatile boolean playRevealOnDismiss = false;

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
                    entry.playRevealOnDismiss = false;
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
        playRevealOnDismiss = false;
        activity.runOnUiThread(this);
    }

    /**
     * Plays the PlayStation Portal stream-reveal transition (fading the circular window interior to
     * the live stream, igniting accelerating Dr. Strange rim sparks, and expanding the circular
     * window fullscreen) before dismissing the dialog.
     */
    public void dismissWithPortalReveal() {
        dismissed = true;
        playRevealOnDismiss = true;
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
            final Dialog targetDialog = dialog;
            if (playRevealOnDismiss && portalView != null && targetDialog.isShowing() && !activity.isFinishing()) {
                // Make window pass-through immediately so controller/touch input reaches the stream right away
                Window window = targetDialog.getWindow();
                if (window != null) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                }
                if (topHudContainer != null) {
                    topHudContainer.animate().alpha(0f).setDuration(240L).start();
                }
                if (bottomCancelView != null) {
                    bottomCancelView.animate().alpha(0f).setDuration(220L).start();
                }
                portalView.startStreamReveal(() -> dismissSafely(targetDialog));
            } else {
                dismissSafely(targetDialog);
            }
        }
    }

    private Dialog buildFullscreenDialog() {
        Dialog d = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout root = new FrameLayout(activity);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.TRANSPARENT);

        // Fullscreen PS Portal view draws the dark eclipse surround, circular aperture,
        // inward-flowing particles, and accelerating Dr. Strange spark expansion
        portalView = new PortalConnectingView(activity);
        portalView.setCompactCardMode(false);
        FrameLayout.LayoutParams portalLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(portalView, portalLp);

        // Top-left PS Portal status HUD ("Connecting to..." + stage message)
        LinearLayout topHud = new LinearLayout(activity);
        topHud.setOrientation(LinearLayout.VERTICAL);
        topHud.setGravity(Gravity.START);

        TextView titleView = new TextView(activity);
        titleView.setText(title != null ? title : "");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setLetterSpacing(0.01f);
        titleView.setShadowLayer(8f, 0f, 2f, 0xCC000000);
        topHud.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        messageView = new TextView(activity);
        messageView.setText(currentMessage != null ? currentMessage : "");
        messageView.setTextColor(0xD8E8F2FF);
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        messageView.setShadowLayer(6f, 0f, 1.5f, 0xCC000000);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        msgLp.topMargin = dp(4);
        topHud.addView(messageView, msgLp);

        FrameLayout.LayoutParams topHudLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        topHudLp.leftMargin = dp(32);
        topHudLp.topMargin = dp(26);
        root.addView(topHud, topHudLp);
        this.topHudContainer = topHud;

        // Bottom-left PS Portal Cancel control
        if (finish) {
            TextView cancelHint = new TextView(activity);
            cancelHint.setText(R.string.conn_cancel_hint);
            cancelHint.setTextColor(0xE6FFFFFF);
            cancelHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            cancelHint.setGravity(Gravity.CENTER_VERTICAL);
            cancelHint.setPadding(dp(16), dp(8), dp(16), dp(8));

            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setShape(GradientDrawable.RECTANGLE);
            pillBg.setCornerRadius(dp(999));
            pillBg.setColor(0x24141C2E);
            pillBg.setStroke(Math.max(1, dp(1)), 0x45FFFFFF);
            cancelHint.setBackground(pillBg);
            cancelHint.setOnClickListener(v -> {
                if (dialog != null && dialog.isShowing()) {
                    dialog.cancel();
                }
            });

            FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.START);
            hintLp.leftMargin = dp(32);
            hintLp.bottomMargin = dp(26);
            root.addView(cancelHint, hintLp);
            this.bottomCancelView = cancelHint;
        }

        d.setContentView(root);

        Window window = d.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setFormat(PixelFormat.TRANSLUCENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // Do NOT blur behind the fullscreen portal window so the live stream inside the circular
            // aperture is crystal-clear when revealed
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND
                    | WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
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
        int portalSize = Math.min(dp(150), Math.round(minDimPx * 0.36f));

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setMinimumWidth(dp(280));
        column.setPadding(dp(24), dp(22), dp(24), dp(22));

        portalView = new PortalConnectingView(activity);
        portalView.setCompactCardMode(true);
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
        playRevealOnDismiss = false;

        // This will only be called if finish was true, so we don't need to check again
        activity.finish();
    }
}
