package com.limelight.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.GameManager;
import android.app.GameState;
import android.app.LocaleManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Insets;
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

            View pcToolbar = activity.findViewById(R.id.pcToolbarContainer);
            if (pcToolbar != null && pcToolbar.getLayoutParams() != null) {
                android.view.ViewGroup.LayoutParams toolbarLp = pcToolbar.getLayoutParams();
                toolbarLp.height = (int) dpToPx(activity, isLandscape ? 54f : 64f);
                pcToolbar.setLayoutParams(toolbarLp);
            }
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
            lp.leftMargin = horizontalMargin;
            lp.rightMargin = horizontalMargin;
            profilesDesc.setLayoutParams(lp);
            int pad = (int) dpToPx(activity, isLandscape ? 12f : 18f);
            profilesDesc.setPadding(pad, pad, pad, pad);
        }

        View profilesRecycler = activity.findViewById(R.id.profilesRecyclerView);
        if (profilesRecycler != null && profilesRecycler.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) profilesRecycler.getLayoutParams();
            lp.leftMargin = horizontalMargin;
            lp.rightMargin = horizontalMargin;
            profilesRecycler.setLayoutParams(lp);
        }

        View manualPcText = activity.findViewById(R.id.manuallyAddPcText);
        if (manualPcText != null && manualPcText.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) manualPcText.getLayoutParams();
            lp.topMargin = reachabilityTop;
            lp.bottomMargin = headerBottom;
            manualPcText.setLayoutParams(lp);
        }

        View addPcCard = activity.findViewById(R.id.addPcCardContainer);
        if (addPcCard != null && addPcCard.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) addPcCard.getLayoutParams();
            lp.leftMargin = horizontalMargin;
            lp.rightMargin = horizontalMargin;
            addPcCard.setLayoutParams(lp);
        }

        View editToolbar = activity.findViewById(R.id.toolbar);
        if (editToolbar != null && editToolbar.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) editToolbar.getLayoutParams();
            lp.topMargin = headerTitleTop;
            lp.bottomMargin = headerBottom;
            lp.leftMargin = horizontalMargin;
            lp.rightMargin = horizontalMargin;
            editToolbar.setLayoutParams(lp);
        }

        View prefsContainer = activity.findViewById(R.id.preferences_container);
        if (prefsContainer != null && prefsContainer.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) prefsContainer.getLayoutParams();
            lp.leftMargin = horizontalMargin;
            lp.rightMargin = horizontalMargin;
            prefsContainer.setLayoutParams(lp);
        }

        View streamSettings = activity.findViewById(R.id.stream_settings);
        if (streamSettings != null) {
            streamSettings.setPadding(
                    horizontalMargin,
                    headerTitleTop,
                    horizontalMargin,
                    streamSettings.getPaddingBottom());
        }
    }

    public static class PopupMenuItem {
        public final int id;
        public final CharSequence title;
        public final boolean checkable;
        public final boolean checked;

        public PopupMenuItem(int id, CharSequence title) {
            this(id, title, false, false);
        }

        public PopupMenuItem(int id, CharSequence title, boolean checkable, boolean checked) {
            this.id = id;
            this.title = title;
            this.checkable = checkable;
            this.checked = checked;
        }
    }

    public static androidx.appcompat.app.AlertDialog showBlurredPopupMenu(
            final Activity activity,
            CharSequence title,
            final java.util.List<PopupMenuItem> items,
            final java.util.function.IntConsumer onItemSelected,
            final Runnable onDismiss) {
        if (activity == null || activity.isFinishing() || items == null || items.isEmpty()) {
            if (onDismiss != null) {
                onDismiss.run();
            }
            return null;
        }

        Context themedContext = new android.view.ContextThemeWrapper(activity, R.style.OneUI_DialogTheme);
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(themedContext, R.style.OneUI_DialogTheme);
        if (title != null && title.length() > 0) {
            builder.setTitle(title);
        }

        final android.widget.ArrayAdapter<PopupMenuItem> adapter =
                new android.widget.ArrayAdapter<PopupMenuItem>(themedContext, R.layout.oneui_game_menu_item, items) {
                    @Override
                    public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                        TextView tv = (TextView) super.getView(position, convertView, parent);
                        PopupMenuItem item = getItem(position);
                        if (item != null) {
                            if (item.checkable) {
                                tv.setText((item.checked ? "\u2713  " : "     ") + item.title);
                            } else {
                                tv.setText(item.title);
                            }
                        }
                        return tv;
                    }
                };

        builder.setAdapter(adapter, (dialog, which) -> {
            PopupMenuItem selected = adapter.getItem(which);
            if (selected != null && onItemSelected != null) {
                onItemSelected.accept(selected.id);
            }
        });

        if (onDismiss != null) {
            builder.setOnDismissListener(dialog -> onDismiss.run());
        }

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        styleDialogSquircle(dialog);
        dialog.show();
        styleDialogSquircle(dialog);

        if (dialog.getListView() != null) {
            dialog.getListView().requestFocus();
        }
        return dialog;
    }

    /**
     * Styles a dialog as a One UI 9 blurred squircle card.
     *
     * Combines Window.setBackgroundBlurRadius (when supported by the WM/SurfaceFlinger)
     * with an in-dialog squircle-clipped blurred backdrop (RenderEffect on API 31+,
     * downscaled box blur on older APIs, and PixelCopy for SurfaceView in GameActivity)
     * so localized blur strictly within the dialog/popup bounds is always visible on
     * One UI and other OEM ROMs.
     */
    public static void styleDialogSquircle(final android.app.Dialog dialog) {
        if (dialog == null) {
            return;
        }
        final Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setFormat(android.graphics.PixelFormat.TRANSLUCENT);
        // Never blur the whole screen behind the dialog; only the card's own bounds.
        window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

        final View decorView = window.getDecorView();
        decorView.setClipToOutline(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setBackgroundDrawableResource(R.drawable.bg_oneui_dialog_squircle);
            decorView.setRenderEffect(null);
            applyBoundedWindowBlur(window, dialog.getContext());
        } else {
            window.setBackgroundDrawableResource(R.drawable.bg_oneui_dialog_squircle_opaque);
        }
        clearOpaquePanelBackgrounds(decorView);
        attachDialogBackdropBlur(dialog, decorView);
    }

    private static Activity unwrapActivity(Context context) {
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private static void attachDialogBackdropBlur(final android.app.Dialog dialog, final View decorView) {
        if (!(decorView instanceof android.view.ViewGroup)) {
            return;
        }
        final Activity activity = unwrapActivity(dialog.getContext());
        if (activity == null || activity.isFinishing() || activity.getWindow() == null) {
            return;
        }
        if (decorView.getTag(R.id.oneui_dialog_blur_tag) != null) {
            return;
        }
        decorView.setTag(R.id.oneui_dialog_blur_tag, Boolean.TRUE);

        final Runnable captureAndApply = () -> captureDialogBackdrop(activity, decorView);
        if (decorView.getWidth() > 0 && decorView.getHeight() > 0) {
            decorView.post(captureAndApply);
        } else {
            decorView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (right - left > 0 && bottom - top > 0) {
                        v.removeOnLayoutChangeListener(this);
                        v.post(captureAndApply);
                    }
                }
            });
        }
    }

    private static void captureDialogBackdrop(final Activity activity, final View decorView) {
        if (activity.isFinishing() || !decorView.isAttachedToWindow()) {
            return;
        }
        final int dialogW = decorView.getWidth();
        final int dialogH = decorView.getHeight();
        if (dialogW <= 0 || dialogH <= 0) {
            return;
        }

        final float scale = 0.25f;
        final int bw = Math.max(1, Math.round(dialogW * scale));
        final int bh = Math.max(1, Math.round(dialogH * scale));
        final int[] dialogLoc = new int[2];
        decorView.getLocationOnScreen(dialogLoc);

        // If we're in GameActivity over a live SurfaceView, capture it with PixelCopy on API 24+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.view.SurfaceView surfaceView = findStreamSurfaceView(activity);
            if (surfaceView != null && surfaceView.getWidth() > 0 && surfaceView.getHeight() > 0
                    && surfaceView.getHolder().getSurface().isValid()) {
                int[] svLoc = new int[2];
                surfaceView.getLocationOnScreen(svLoc);
                android.graphics.Rect srcRect = new android.graphics.Rect(
                        Math.max(0, dialogLoc[0] - svLoc[0]),
                        Math.max(0, dialogLoc[1] - svLoc[1]),
                        Math.min(surfaceView.getWidth(), dialogLoc[0] - svLoc[0] + dialogW),
                        Math.min(surfaceView.getHeight(), dialogLoc[1] - svLoc[1] + dialogH));
                if (!srcRect.isEmpty()) {
                    final android.graphics.Bitmap svBitmap = android.graphics.Bitmap.createBitmap(
                            bw, bh, android.graphics.Bitmap.Config.ARGB_8888);
                    try {
                        android.view.PixelCopy.request(
                                surfaceView,
                                srcRect,
                                svBitmap,
                                result -> {
                                    if (result == android.view.PixelCopy.SUCCESS && decorView.isAttachedToWindow()) {
                                        applyBitmapToDialogBackdrop(decorView, svBitmap);
                                    } else {
                                        svBitmap.recycle();
                                    }
                                },
                                new android.os.Handler(android.os.Looper.getMainLooper()));
                        return;
                    } catch (IllegalArgumentException ignored) {
                        svBitmap.recycle();
                    }
                }
            }
        }

        View activityDecor = activity.getWindow().getDecorView();
        if (activityDecor.getWidth() <= 0 || activityDecor.getHeight() <= 0) {
            return;
        }
        int[] activityLoc = new int[2];
        activityDecor.getLocationOnScreen(activityLoc);
        int offsetX = dialogLoc[0] - activityLoc[0];
        int offsetY = dialogLoc[1] - activityLoc[1];

        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                bw, bh, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        canvas.scale(scale, scale);
        canvas.translate(-offsetX, -offsetY);
        activityDecor.draw(canvas);
        applyBitmapToDialogBackdrop(decorView, bitmap);
    }

    private static android.view.SurfaceView findStreamSurfaceView(Activity activity) {
        View container = activity.findViewById(R.id.streamContainer);
        if (!(container instanceof android.view.ViewGroup)) {
            return null;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) container;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof android.view.SurfaceView && child.getVisibility() == View.VISIBLE) {
                return (android.view.SurfaceView) child;
            }
        }
        return null;
    }

    private static void applyBitmapToDialogBackdrop(View decorView, android.graphics.Bitmap bitmap) {
        if (!(decorView instanceof android.view.ViewGroup)) {
            return;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) decorView;
        Context ctx = decorView.getContext();

        android.widget.ImageView backdrop = group.findViewWithTag("oneui_dialog_blur_backdrop");
        if (backdrop == null) {
            backdrop = new android.widget.ImageView(ctx);
            backdrop.setTag("oneui_dialog_blur_backdrop");
            backdrop.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            backdrop.setClickable(false);
            backdrop.setFocusable(false);
            backdrop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            group.addView(backdrop, 0, new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            float radiusPx = dpToPx(ctx, 22f);
            RenderEffect blur = RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP);
            RenderEffect tinted = RenderEffect.createColorFilterEffect(
                    new PorterDuffColorFilter(0x73141820, PorterDuff.Mode.SRC_OVER), blur);
            backdrop.setRenderEffect(tinted);
            backdrop.setImageBitmap(bitmap);
        } else {
            fastBoxBlur(bitmap, 6);
            backdrop.setColorFilter(0x73141820, PorterDuff.Mode.SRC_OVER);
            backdrop.setImageBitmap(bitmap);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            GradientDrawable border = new GradientDrawable();
            border.setShape(GradientDrawable.RECTANGLE);
            border.setColor(android.graphics.Color.TRANSPARENT);
            border.setCornerRadius(dpToPx(ctx, ONEUI_DIALOG_SQUIRCLE_RADIUS_DP));
            border.setStroke((int) Math.max(1f, dpToPx(ctx, 1f)), 0x33FFFFFF);
            decorView.setForeground(border);
        }
        decorView.setClipToOutline(true);
    }

    private static void fastBoxBlur(android.graphics.Bitmap bitmap, int radius) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w <= 1 || h <= 1 || radius <= 0) {
            return;
        }
        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);
        int[] tmp = new int[w * h];
        int div = radius * 2 + 1;
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < h; y++) {
                int row = y * w;
                for (int x = 0; x < w; x++) {
                    int r = 0, g = 0, b = 0;
                    for (int k = -radius; k <= radius; k++) {
                        int sx = Math.max(0, Math.min(w - 1, x + k));
                        int c = pix[row + sx];
                        r += (c >> 16) & 0xFF;
                        g += (c >> 8) & 0xFF;
                        b += c & 0xFF;
                    }
                    tmp[row + x] = 0xFF000000 | ((r / div) << 16) | ((g / div) << 8) | (b / div);
                }
            }
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    int r = 0, g = 0, b = 0;
                    for (int k = -radius; k <= radius; k++) {
                        int sy = Math.max(0, Math.min(h - 1, y + k));
                        int c = tmp[sy * w + x];
                        r += (c >> 16) & 0xFF;
                        g += (c >> 8) & 0xFF;
                        b += c & 0xFF;
                    }
                    pix[y * w + x] = 0xFF000000 | ((r / div) << 16) | ((g / div) << 8) | (b / div);
                }
            }
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h);
    }

    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.S)
    private static void applyBoundedWindowBlur(final Window window, Context context) {
        final WindowManager wm = context.getSystemService(WindowManager.class);
        final int blurRadiusPx = (int) dpToPx(context, ONEUI_BLUR_RADIUS_DP);
        final java.util.function.Consumer<Boolean> updateBlur = enabled -> {
            window.setBackgroundDrawableResource(enabled
                    ? R.drawable.bg_oneui_dialog_squircle
                    : R.drawable.bg_oneui_dialog_squircle_opaque);
            window.setBackgroundBlurRadius(enabled ? blurRadiusPx : 0);
        };

        boolean blurEnabled = wm != null && wm.isCrossWindowBlurEnabled();
        if (wm == null) {
            LimeLog.warning("OneUI: WindowManager unavailable, using opaque dialog surface");
        } else if (!blurEnabled) {
            LimeLog.info("OneUI: cross-window blur is disabled by the system, using opaque dialog surface");
        }
        updateBlur.accept(blurEnabled);

        final View decorView = window.getDecorView();
        if (wm == null || decorView.getTag(R.id.oneuiHeaderContainer) != null) {
            return;
        }
        decorView.setTag(R.id.oneuiHeaderContainer, Boolean.TRUE);
        decorView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                wm.addCrossWindowBlurEnabledListener(updateBlur);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                wm.removeCrossWindowBlurEnabledListener(updateBlur);
            }
        });
        if (decorView.isAttachedToWindow()) {
            wm.addCrossWindowBlurEnabledListener(updateBlur);
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
        for (int i = 0; i < group.getChildCount(); i++) {
            group.getChildAt(i).setBackground(null);
        }
        View content = decorView.findViewById(android.R.id.content);
        if (content != null) {
            content.setBackground(null);
        }
        Context ctx = decorView.getContext();
        int[] panelIds = {
                ctx.getResources().getIdentifier("parentPanel", "id", "android"),
                ctx.getResources().getIdentifier("parentPanel", "id", ctx.getPackageName()),
        };
        for (int id : panelIds) {
            if (id != 0) {
                View panel = decorView.findViewById(id);
                if (panel != null) {
                    panel.setBackground(null);
                }
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
