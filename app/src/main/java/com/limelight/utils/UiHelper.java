package com.limelight.utils;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.app.GameManager;
import android.app.GameState;
import android.app.LocaleManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.LocaleList;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.AppView;
import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.Locale;

public class UiHelper {

    private static final int TV_VERTICAL_PADDING_DP = 15;
    private static final int TV_HORIZONTAL_PADDING_DP = 15;
    private static final float ONEUI_SQUIRCLE_RADIUS_DP = 28f;
    private static final float ONEUI_DIALOG_SQUIRCLE_RADIUS_DP = 32f;
    private static final float ONEUI_BLUR_RADIUS_DP = 28f;
    private static final int ONEUI_GLASS_OVERLAY_TINT_DARK = 0x40000000; // 25% translucent dark tint
    private static final int ONEUI_GLASS_OVERLAY_TINT_LIGHT = 0x40FFFFFF; // 25% translucent light tint

    public static void applyLiquidGlassEffect(View view) {
        if (view == null) {
            return;
        }

        Context context = view.getContext();
        boolean isNightMode = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        float cornerRadiusPx = dpToPx(context, ONEUI_SQUIRCLE_RADIUS_DP);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            float blurRadiusPx = dpToPx(context, ONEUI_BLUR_RADIUS_DP);
            int overlayTint = isNightMode ? ONEUI_GLASS_OVERLAY_TINT_DARK : ONEUI_GLASS_OVERLAY_TINT_LIGHT;

            // Ensure the foreground view/container itself is never blurred so all text and icons stay razor-sharp
            view.setRenderEffect(null);

            RenderEffect blurEffect = RenderEffect.createBlurEffect(
                    blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP);
            RenderEffect liquidGlassEffect = RenderEffect.createColorFilterEffect(
                    new PorterDuffColorFilter(overlayTint, PorterDuff.Mode.SRC_OVER),
                    blurEffect);

            GradientDrawable glassDrawable = new GradientDrawable();
            glassDrawable.setShape(GradientDrawable.RECTANGLE);
            glassDrawable.setCornerRadius(cornerRadiusPx);
            glassDrawable.setColor(overlayTint);
            glassDrawable.setStroke((int) Math.max(1f, dpToPx(context, 1f)), 0x33FFFFFF);

            if (view instanceof android.widget.FrameLayout && shouldAttachGlassBackdrop((android.view.ViewGroup) view)) {
                android.view.ViewGroup group = (android.view.ViewGroup) view;
                View backdrop = group.findViewWithTag("oneui_liquid_glass_backdrop");
                if (backdrop == null) {
                    backdrop = new View(context);
                    backdrop.setTag("oneui_liquid_glass_backdrop");
                    backdrop.setClickable(false);
                    backdrop.setFocusable(false);
                    backdrop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    GradientDrawable backdropFill = new GradientDrawable();
                    backdropFill.setShape(GradientDrawable.RECTANGLE);
                    backdropFill.setCornerRadius(cornerRadiusPx);
                    backdropFill.setColor(overlayTint);
                    backdrop.setBackground(backdropFill);
                    backdrop.setRenderEffect(liquidGlassEffect);

                    android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT);
                    group.addView(backdrop, 0, lp);
                } else {
                    backdrop.setRenderEffect(liquidGlassEffect);
                }
            } else {
                View explicitBg = view.findViewWithTag("oneui_liquid_glass_backdrop");
                if (explicitBg != null) {
                    explicitBg.setRenderEffect(liquidGlassEffect);
                }
            }

            view.setBackground(glassDrawable);
            view.setClipToOutline(true);
        } else {
            view.setBackgroundResource(isNightMode
                    ? R.drawable.bg_oneui_liquid_glass_dark
                    : R.drawable.bg_oneui_liquid_glass_light);
        }
    }

    private static boolean shouldAttachGlassBackdrop(android.view.ViewGroup group) {
        // Only attach a full-bleed backdrop child to overlay/positional containers (FrameLayout / RelativeLayout)
        // that are not root fragment hosts, so child layout rules and weights are never disrupted.
        if (!(group instanceof android.widget.FrameLayout || group instanceof android.widget.RelativeLayout)) {
            return false;
        }
        int id = group.getId();
        return id != R.id.stream_settings
                && id != R.id.preferences_container
                && id != R.id.pcFragmentContainer
                && id != R.id.appFragmentContainer;
    }

    public static Activity findActivity(Context context) {
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public static void applyReachabilitySpacing(Activity activity) {
        if (activity == null) {
            return;
        }
        android.content.res.Resources res = activity.getResources();
        boolean isLandscape = res.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int reachabilityTop = res.getDimensionPixelSize(R.dimen.oneui_reachability_top_margin);
        int headerTitleTop = res.getDimensionPixelSize(R.dimen.oneui_header_title_top_margin);
        int headerBottom = res.getDimensionPixelSize(R.dimen.oneui_header_bottom_margin);
        int horizontalMargin = res.getDimensionPixelSize(R.dimen.activity_horizontal_margin);

        View oneuiHeader = activity.findViewById(R.id.oneuiHeaderContainer);
        if (oneuiHeader != null && oneuiHeader.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) oneuiHeader.getLayoutParams();
            lp.topMargin = reachabilityTop;
            lp.bottomMargin = headerBottom;
            oneuiHeader.setLayoutParams(lp);
            oneuiHeader.setPadding(horizontalMargin, oneuiHeader.getPaddingTop(), horizontalMargin, oneuiHeader.getPaddingBottom());
        }

        View pcToolbar = activity.findViewById(R.id.pcToolbarContainer);
        if (pcToolbar != null && pcToolbar.getLayoutParams() != null) {
            android.view.ViewGroup.LayoutParams lp = pcToolbar.getLayoutParams();
            lp.height = (int) dpToPx(activity, isLandscape ? 54f : 64f);
            pcToolbar.setLayoutParams(lp);
        }

        View appListText = activity.findViewById(R.id.appListText);
        if (appListText != null && appListText.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) appListText.getLayoutParams();
            lp.topMargin = reachabilityTop;
            lp.bottomMargin = headerBottom;
            lp.leftMargin = horizontalMargin;
            lp.rightMargin = horizontalMargin;
            appListText.setLayoutParams(lp);
            int vertPad = (int) dpToPx(activity, isLandscape ? 8f : 12f);
            appListText.setPadding(appListText.getPaddingLeft(), vertPad, appListText.getPaddingRight(), vertPad);
        }

        View profilesDesc = activity.findViewById(R.id.profilesDescription);
        if (profilesDesc != null && profilesDesc.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) profilesDesc.getLayoutParams();
            lp.topMargin = reachabilityTop;
            lp.bottomMargin = headerBottom;
            profilesDesc.setLayoutParams(lp);
            int pad = (int) dpToPx(activity, isLandscape ? 12f : 18f);
            profilesDesc.setPadding(pad, pad, pad, pad);
        }

        View manualPcText = activity.findViewById(R.id.manuallyAddPcText);
        if (manualPcText != null && manualPcText.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) manualPcText.getLayoutParams();
            lp.topMargin = reachabilityTop;
            lp.bottomMargin = headerBottom;
            manualPcText.setLayoutParams(lp);
        }

        View editToolbar = activity.findViewById(R.id.toolbar);
        if (editToolbar != null && editToolbar.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) editToolbar.getLayoutParams();
            lp.topMargin = headerTitleTop;
            lp.bottomMargin = headerBottom;
            editToolbar.setLayoutParams(lp);
        }

        View streamSettings = activity.findViewById(R.id.stream_settings);
        if (streamSettings != null) {
            streamSettings.setPadding(
                    streamSettings.getPaddingLeft(),
                    headerTitleTop,
                    streamSettings.getPaddingRight(),
                    streamSettings.getPaddingBottom());
        }
    }

    /**
     * Styles a dialog as a One UI "Liquid Glass" squircle card.
     *
     * On API 31+ the blur is a window *background* blur: SurfaceFlinger blurs only the
     * content behind the dialog window, clipped to the window background drawable's
     * rounded bounds. Nothing outside the card is blurred and the dialog's own text is
     * never blurred.
     */
    public static void styleDialogSquircle(final android.app.Dialog dialog) {
        if (dialog == null) {
            return;
        }
        final Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setFormat(PixelFormat.TRANSLUCENT);
        window.setBackgroundDrawableResource(R.drawable.bg_oneui_dialog_squircle);
        // Never blur the whole screen behind the dialog; only the card's own bounds.
        window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

        View decorView = window.getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            decorView.setRenderEffect(null);
            applyBoundedWindowBlur(window, dialog.getContext());
        }
        clearOpaquePanelBackgrounds(decorView);
    }

    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.S)
    private static void applyBoundedWindowBlur(final Window window, Context context) {
        final WindowManager wm = context.getSystemService(WindowManager.class);
        final int blurRadiusPx = (int) dpToPx(context, ONEUI_BLUR_RADIUS_DP);

        // Always retain the translucent squircle glass drawable
        window.setBackgroundDrawableResource(R.drawable.bg_oneui_dialog_squircle);
        window.setBackgroundBlurRadius(blurRadiusPx);

        if (wm == null) {
            return;
        }

        final View decorView = window.getDecorView();
        if (decorView.getTag(R.id.oneuiHeaderContainer) != null) {
            return;
        }
        decorView.setTag(R.id.oneuiHeaderContainer, Boolean.TRUE);

        final java.util.function.Consumer<Boolean> updateBlur = enabled -> {
            window.setBackgroundBlurRadius(enabled ? blurRadiusPx : 0);
        };

        decorView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                try {
                    wm.addCrossWindowBlurEnabledListener(updateBlur);
                } catch (Exception ignored) {}
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                try {
                    wm.removeCrossWindowBlurEnabledListener(updateBlur);
                } catch (Exception ignored) {}
            }
        });
        if (decorView.isAttachedToWindow()) {
            try {
                wm.addCrossWindowBlurEnabledListener(updateBlur);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Material/AppCompat alert layouts may paint their own opaque surface on top of the
     * window background, which hides the blurred glass. Make those inner panels transparent.
     */
    private static void clearOpaquePanelBackgrounds(View decorView) {
        if (!(decorView instanceof android.view.ViewGroup)) {
            return;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) decorView;
        // Do NOT clear decorView itself, as decorView holds the window's background drawable!
        for (int i = 0; i < group.getChildCount(); i++) {
            clearViewGroupPanelBackgrounds(group.getChildAt(i));
        }
        View content = decorView.findViewById(android.R.id.content);
        if (content != null) {
            content.setBackground(null);
        }
    }

    private static void clearViewGroupPanelBackgrounds(View view) {
        if (view == null) {
            return;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            group.setBackground(null);
            for (int i = 0; i < group.getChildCount(); i++) {
                clearViewGroupPanelBackgrounds(group.getChildAt(i));
            }
        }
    }

    private static void setGameModeStatus(Context context, boolean streaming, boolean interruptible) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GameManager gameManager = context.getSystemService(GameManager.class);

            if (gameManager == null) {
                LimeLog.warning("GameManager is null, maybe your system does not support it?");
                return;
            }

            if (streaming) {
                gameManager.setGameState(new GameState(false, interruptible ? GameState.MODE_GAMEPLAY_INTERRUPTIBLE : GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE));
            }
            else {
                gameManager.setGameState(new GameState(false, GameState.MODE_NONE));
            }
        }
    }

    public static void notifyStreamConnecting(Context context) {
        setGameModeStatus(context, true, true);
    }

    public static void notifyStreamConnected(Context context) {
        setGameModeStatus(context, true, false);
    }

    public static void notifyStreamEnteringPiP(Context context) {
        setGameModeStatus(context, true, true);
    }

    public static void notifyStreamExitingPiP(Context context) {
        setGameModeStatus(context, true, false);
    }

    public static void notifyStreamEnded(Context context) {
        setGameModeStatus(context, false, false);
    }

    public static void setLocale(Activity activity)
    {
        String locale = PreferenceConfiguration.readPreferences(activity).language;
        Configuration config = new Configuration(activity.getResources().getConfiguration());
        if (locale.equals(PreferenceConfiguration.DEFAULT_LANGUAGE)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // On Android 13, migrate this non-default language setting into the OS native API
                LocaleManager localeManager = activity.getSystemService(LocaleManager.class);
                LocaleList systemLocales = localeManager.getSystemLocales();
                if (!systemLocales.isEmpty()) {
                    config.locale = systemLocales.get(0);
                }
            }
        } else {
            // We're handling some nasty non-standard devices which cannot set locale using system config correctly
            // Some locales include both language and country which must be separated
            // before calling the Locale constructor.
            if (locale.contains("-"))
            {
                config.locale = new Locale(locale.substring(0, locale.indexOf('-')),
                        locale.substring(locale.indexOf('-') + 1));
            }
            else
            {
                config.locale = new Locale(locale);
            }
        }

        activity.getResources().updateConfiguration(config, activity.getResources().getDisplayMetrics());
    }

    public static void applyStatusBarPadding(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // This applies the padding that we omitted in notifyNewRootView() on Q
            view.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
                    view.setPadding(view.getPaddingLeft(),
                            view.getPaddingTop(),
                            view.getPaddingRight(),
                            windowInsets.getTappableElementInsets().bottom);
                    return windowInsets;
                }
            });
            view.requestApplyInsets();
        }
    }

    public static void notifyNewRootView(final Activity activity)
    {
        View rootView = activity.findViewById(android.R.id.content);
        UiModeManager modeMgr = (UiModeManager) activity.getSystemService(Context.UI_MODE_SERVICE);

        // Set GameState.MODE_NONE initially for all activities
        setGameModeStatus(activity, false, false);

        applyReachabilitySpacing(activity);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Allow this non-streaming activity to layout under notches.
            //
            // We should NOT do this for the Game activity unless
            // the user specifically opts in, because it can obscure
            // parts of the streaming surface.
            activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        if (modeMgr.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            // Increase view padding on TVs
            float scale = activity.getResources().getDisplayMetrics().density;
            int verticalPaddingPixels = (int) (TV_VERTICAL_PADDING_DP*scale + 0.5f);
            int horizontalPaddingPixels = (int) (TV_HORIZONTAL_PADDING_DP*scale + 0.5f);

            rootView.setPadding(horizontalPaddingPixels, verticalPaddingPixels,
                    horizontalPaddingPixels, verticalPaddingPixels);
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Draw under the status bar on Android Q devices

            // Using getDecorView() here breaks the translucent status/navigation bar when gestures are disabled
            activity.findViewById(android.R.id.content).setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
                    // Use the tappable insets so we can draw under the status bar in gesture mode
                    Insets tappableInsets = windowInsets.getTappableElementInsets();
                    view.setPadding(tappableInsets.left,
                            tappableInsets.top,
                            tappableInsets.right,
                            tappableInsets.bottom);

                    // Show a translucent navigation bar if we can't tap there
                    if (tappableInsets.bottom != 0) {
                        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
                    }
                    else {
                        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
                    }

                    return windowInsets;
                }
            });

            activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    public static void showDecoderCrashDialog(Activity activity) {
        final SharedPreferences prefs = activity.getSharedPreferences("DecoderTombstone", 0);
        final int crashCount = prefs.getInt("CrashCount", 0);
        int lastNotifiedCrashCount = prefs.getInt("LastNotifiedCrashCount", 0);

        // Remember the last crash count we notified at, so we don't
        // display the crash dialog every time the app is started until
        // they stream again
        if (crashCount != 0 && crashCount != lastNotifiedCrashCount) {
            if (crashCount % 3 == 0) {
                // At 3 consecutive crashes, we'll forcefully reset their settings
                PreferenceConfiguration.resetStreamingSettings(activity);
                Dialog.displayDialog(activity,
                        activity.getResources().getString(R.string.title_decoding_reset),
                        activity.getResources().getString(R.string.message_decoding_reset),
                        new Runnable() {
                            @Override
                            public void run() {
                                // Mark notification as acknowledged on dismissal
                                prefs.edit().putInt("LastNotifiedCrashCount", crashCount).apply();
                            }
                        });
            }
            else {
                Dialog.displayDialog(activity,
                        activity.getResources().getString(R.string.title_decoding_error),
                        activity.getResources().getString(R.string.message_decoding_error),
                        new Runnable() {
                            @Override
                            public void run() {
                                // Mark notification as acknowledged on dismissal
                                prefs.edit().putInt("LastNotifiedCrashCount", crashCount).apply();
                            }
                        });
            }
        }
    }

    public static <T> void displayConfirmationDialog(Activity parent, String title, String message, String btnYesText, String btnNoText, final Runnable onYes, final Runnable onNo) {
        DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    if (onYes != null) {
                        onYes.run();
                    }
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    if (onNo != null) {
                        onNo.run();
                    }
                    break;
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(parent);
        builder.setMessage(Html.fromHtml(message));
        if (title != null) {
            builder.setTitle(title);
        }
        if (btnYesText != null) {
            builder.setPositiveButton(btnYesText, dialogClickListener);
        }
        if (btnNoText != null) {
            builder.setNegativeButton(btnNoText, dialogClickListener);
        }
        AlertDialog dialog = builder.create();
        styleDialogSquircle(dialog);
        dialog.show();
        styleDialogSquircle(dialog);
        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    public static void displayVdisplayConfirmationDialog(Activity parent, ComputerDetails computer, final Runnable onYes, final Runnable onNo) {
        String message = computer.vDisplaySupported ?
                parent.getResources().getString(R.string.vdisplay_not_ready) :
                parent.getResources().getString(R.string.vdisplay_not_supported);
        UiHelper.displayConfirmationDialog(
                parent,
                null,
                message,
                parent.getResources().getString(R.string.proceed),
                parent.getResources().getString(R.string.cancel),
                onYes,
                onNo
        );
    }

    public static void displayQuitConfirmationDialog(Activity parent, final Runnable onYes, final Runnable onNo) {
        displayConfirmationDialog(
                parent,
                null,
                parent.getResources().getString(R.string.applist_quit_confirmation),
                parent.getResources().getString(R.string.yes),
                parent.getResources().getString(R.string.no),
                onYes,
                onNo
        );
    }

    public static void displayDeletePcConfirmationDialog(Activity parent, ComputerDetails computer, final Runnable onYes, final Runnable onNo) {
        displayConfirmationDialog(
                parent,
                computer.name,
                parent.getResources().getString(R.string.delete_pc_msg),
                parent.getResources().getString(R.string.yes),
                parent.getResources().getString(R.string.no),
                onYes,
                onNo
        );
    }

    public static float dpToPx(Context context, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}
