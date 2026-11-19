package com.limelight.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.GameManager;
import android.app.GameState;
import android.app.LocaleManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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
import com.limelight.PcView;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Ensure the foreground view/container itself is never blurred so all text and icons stay razor-sharp
            view.setRenderEffect(null);
        }

        if (view.getId() == R.id.notificationOverlay) {
            view.setBackgroundResource(R.drawable.bg_oneui_notification_glass);
        } else if (view.getId() == R.id.pcToolbarContainer || view.getId() == R.id.appListText) {
            if (!applyFrostedCardBackground(view, com.limelight.ui.FlowingAuroraView.CornerMode.ALL,
                    ONEUI_SQUIRCLE_RADIUS_DP, 0x4A121826)) {
                view.setBackgroundResource(R.drawable.bg_oneui_floating_pill);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                view.setElevation(dpToPx(context, 8f));
            }
        } else {
            if (!applyFrostedCardBackground(view, com.limelight.ui.FlowingAuroraView.CornerMode.ALL,
                    ONEUI_SQUIRCLE_RADIUS_DP, 0x52121826)) {
                view.setBackgroundResource(R.drawable.bg_oneui_liquid_glass_dark);
            }
        }

        view.setClipToOutline(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && view.getElevation() == 0f) {
            view.setElevation(dpToPx(context, 4f));
        }
    }

    public static boolean applyFrostedCardBackground(
            View view,
            com.limelight.ui.FlowingAuroraView.CornerMode cornerMode,
            float cornerRadiusDp,
            int frostTintColor) {
        if (view == null) {
            return false;
        }
        Activity activity = unwrapActivity(view.getContext());
        if (activity == null || activity.getWindow() == null) {
            return false;
        }
        View content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return false;
        }
        View auroraCandidate = content.findViewWithTag("oneui_flowing_aurora");
        if (!(auroraCandidate instanceof com.limelight.ui.FlowingAuroraView)) {
            return false;
        }
        com.limelight.ui.FlowingAuroraView aurora = (com.limelight.ui.FlowingAuroraView) auroraCandidate;
        view.setBackground(new com.limelight.ui.FlowingAuroraView.FrostedCardDrawable(
                view,
                aurora,
                cornerMode,
                dpToPx(view.getContext(), cornerRadiusDp),
                frostTintColor,
                Math.max(1f, dpToPx(view.getContext(), 1f))));
        return true;
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
        applyDialogButtonPalette(dialog);
    }

    private static void applyDialogButtonPalette(final android.app.Dialog dialog) {
        if (dialog == null) {
            return;
        }
        Context ctx = dialog.getContext();
        com.limelight.ui.AppThemePalette palette = com.limelight.ui.AppThemePalette.getCurrent(ctx);
        int neonAccent = palette.getNeonAccent();
        int secondaryAccent = com.limelight.ui.AppThemePalette.withAlpha(neonAccent, 0.88f);

        int[] buttonIds = {
                DialogInterface.BUTTON_POSITIVE,
                DialogInterface.BUTTON_NEGATIVE,
                DialogInterface.BUTTON_NEUTRAL
        };
        if (dialog instanceof androidx.appcompat.app.AlertDialog) {
            androidx.appcompat.app.AlertDialog ad = (androidx.appcompat.app.AlertDialog) dialog;
            for (int which : buttonIds) {
                android.widget.Button btn = ad.getButton(which);
                if (btn != null) {
                    btn.setTextColor(which == DialogInterface.BUTTON_POSITIVE ? neonAccent : secondaryAccent);
                    btn.setAllCaps(false);
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        btn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    }
                }
            }
        } else if (dialog instanceof AlertDialog) {
            AlertDialog ad = (AlertDialog) dialog;
            for (int which : buttonIds) {
                android.widget.Button btn = ad.getButton(which);
                if (btn != null) {
                    btn.setTextColor(which == DialogInterface.BUTTON_POSITIVE ? neonAccent : secondaryAccent);
                    btn.setAllCaps(false);
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        btn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    }
                }
            }
        }
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

        final android.view.SurfaceView surfaceView = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? findStreamSurfaceView(activity)
                : null;

        final boolean[] stopped = { false };
        final boolean[] inFlight = { false };
        final android.graphics.Bitmap[] buffers = new android.graphics.Bitmap[2];
        final int[] bufferIndex = { 0 };
        final int[] bufferSize = { 0, 0 };
        final long[] lastCaptureMs = { 0L };
        final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

        final Runnable ensureBuffers = () -> {
            int dialogW = decorView.getWidth();
            int dialogH = decorView.getHeight();
            if (dialogW <= 0 || dialogH <= 0) {
                return;
            }
            int bw = Math.max(1, Math.round(dialogW * 0.25f));
            int bh = Math.max(1, Math.round(dialogH * 0.25f));
            if (buffers[0] == null || bufferSize[0] != bw || bufferSize[1] != bh) {
                buffers[0] = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888);
                buffers[1] = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888);
                bufferSize[0] = bw;
                bufferSize[1] = bh;
            }
        };

        if (surfaceView != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            final Runnable[] tickHolder = new Runnable[1];
            tickHolder[0] = () -> {
                if (stopped[0] || activity.isFinishing() || !decorView.isAttachedToWindow()) {
                    return;
                }
                if (inFlight[0] || surfaceView.getWidth() <= 0 || surfaceView.getHeight() <= 0
                        || !surfaceView.getHolder().getSurface().isValid()) {
                    mainHandler.postDelayed(tickHolder[0], 33L);
                    return;
                }
                int dialogW = decorView.getWidth();
                int dialogH = decorView.getHeight();
                if (dialogW <= 0 || dialogH <= 0) {
                    mainHandler.postDelayed(tickHolder[0], 33L);
                    return;
                }
                ensureBuffers.run();
                final int idx = bufferIndex[0];
                final android.graphics.Bitmap target = buffers[idx];
                int[] dialogLoc = new int[2];
                int[] svLoc = new int[2];
                decorView.getLocationOnScreen(dialogLoc);
                surfaceView.getLocationOnScreen(svLoc);

                int svW = surfaceView.getWidth();
                int svH = surfaceView.getHeight();
                int interLeft = Math.max(svLoc[0], dialogLoc[0]);
                int interTop = Math.max(svLoc[1], dialogLoc[1]);
                int interRight = Math.min(svLoc[0] + svW, dialogLoc[0] + dialogW);
                int interBottom = Math.min(svLoc[1] + svH, dialogLoc[1] + dialogH);
                if (interRight <= interLeft || interBottom <= interTop) {
                    mainHandler.postDelayed(tickHolder[0], 33L);
                    return;
                }

                android.graphics.Rect surfaceFrame = surfaceView.getHolder().getSurfaceFrame();
                int bufW = (surfaceFrame != null && surfaceFrame.width() > 0) ? surfaceFrame.width() : svW;
                int bufH = (surfaceFrame != null && surfaceFrame.height() > 0) ? surfaceFrame.height() : svH;
                float scaleX = bufW / (float) svW;
                float scaleY = bufH / (float) svH;

                android.graphics.Rect srcRect = new android.graphics.Rect(
                        Math.max(0, Math.round((interLeft - svLoc[0]) * scaleX)),
                        Math.max(0, Math.round((interTop - svLoc[1]) * scaleY)),
                        Math.min(bufW, Math.round((interRight - svLoc[0]) * scaleX)),
                        Math.min(bufH, Math.round((interBottom - svLoc[1]) * scaleY)));
                if (srcRect.isEmpty()) {
                    mainHandler.postDelayed(tickHolder[0], 33L);
                    return;
                }

                final boolean fullCoverage = (interLeft == dialogLoc[0] && interTop == dialogLoc[1]
                        && interRight == dialogLoc[0] + dialogW && interBottom == dialogLoc[1] + dialogH);
                final int subW = Math.max(1, Math.round((interRight - interLeft) * 0.25f));
                final int subH = Math.max(1, Math.round((interBottom - interTop) * 0.25f));
                final android.graphics.Bitmap copyDest = fullCoverage
                        ? target
                        : android.graphics.Bitmap.createBitmap(subW, subH, android.graphics.Bitmap.Config.ARGB_8888);
                final float dstLeft = (interLeft - dialogLoc[0]) * 0.25f;
                final float dstTop = (interTop - dialogLoc[1]) * 0.25f;

                inFlight[0] = true;
                try {
                    android.view.PixelCopy.request(
                            surfaceView,
                            srcRect,
                            copyDest,
                            result -> {
                                inFlight[0] = false;
                                if (!stopped[0] && result == android.view.PixelCopy.SUCCESS
                                        && decorView.isAttachedToWindow()) {
                                    if (!fullCoverage) {
                                        target.eraseColor(android.graphics.Color.BLACK);
                                        android.graphics.Canvas c = new android.graphics.Canvas(target);
                                        c.drawBitmap(copyDest, dstLeft, dstTop, null);
                                        copyDest.recycle();
                                    }
                                    applyBitmapToDialogBackdrop(decorView, target);
                                    bufferIndex[0] = 1 - idx;
                                } else if (!fullCoverage) {
                                    copyDest.recycle();
                                }
                                if (!stopped[0] && decorView.isAttachedToWindow()) {
                                    mainHandler.postDelayed(tickHolder[0], 33L);
                                }
                            },
                            mainHandler);
                } catch (IllegalArgumentException ignored) {
                    inFlight[0] = false;
                    if (!fullCoverage) {
                        copyDest.recycle();
                    }
                }
            };

            decorView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    stopped[0] = true;
                    mainHandler.removeCallbacks(tickHolder[0]);
                    android.widget.ImageView backdrop = decorView.findViewWithTag("oneui_dialog_blur_backdrop");
                    if (backdrop != null) {
                        backdrop.setImageDrawable(null);
                    }
                    v.removeOnAttachStateChangeListener(this);
                }
            });
            tickHolder[0].run();
            return;
        }

        final View activityDecor = activity.getWindow().getDecorView();
        final Runnable captureActivityFrame = () -> {
            if (stopped[0] || activity.isFinishing() || !decorView.isAttachedToWindow()) {
                return;
            }
            if (activityDecor.getWidth() <= 0 || activityDecor.getHeight() <= 0
                    || decorView.getWidth() <= 0 || decorView.getHeight() <= 0) {
                return;
            }
            ensureBuffers.run();
            int idx = bufferIndex[0];
            android.graphics.Bitmap target = buffers[idx];
            int[] dialogLoc = new int[2];
            int[] activityLoc = new int[2];
            decorView.getLocationOnScreen(dialogLoc);
            activityDecor.getLocationOnScreen(activityLoc);
            int offsetX = dialogLoc[0] - activityLoc[0];
            int offsetY = dialogLoc[1] - activityLoc[1];

            target.eraseColor(android.graphics.Color.TRANSPARENT);
            android.graphics.Canvas canvas = new android.graphics.Canvas(target);
            canvas.scale(0.25f, 0.25f);
            canvas.translate(-offsetX, -offsetY);
            activityDecor.draw(canvas);
            applyBitmapToDialogBackdrop(decorView, target);
            bufferIndex[0] = 1 - idx;
            lastCaptureMs[0] = android.os.SystemClock.uptimeMillis();
        };

        final android.view.ViewTreeObserver.OnPreDrawListener preDrawListener = () -> {
            if (!stopped[0] && decorView.isAttachedToWindow() && !inFlight[0]) {
                long now = android.os.SystemClock.uptimeMillis();
                if (now - lastCaptureMs[0] >= 33L) {
                    inFlight[0] = true;
                    decorView.post(() -> {
                        try {
                            captureActivityFrame.run();
                        } finally {
                            inFlight[0] = false;
                        }
                    });
                }
            }
            return true;
        };

        activityDecor.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
        decorView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                stopped[0] = true;
                if (activityDecor.getViewTreeObserver().isAlive()) {
                    activityDecor.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
                }
                android.widget.ImageView backdrop = decorView.findViewWithTag("oneui_dialog_blur_backdrop");
                if (backdrop != null) {
                    backdrop.setImageDrawable(null);
                }
                v.removeOnAttachStateChangeListener(this);
            }
        });
        captureActivityFrame.run();
    }

    public static android.view.SurfaceView findStreamSurfaceView(Activity activity) {
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                float radiusPx = dpToPx(ctx, 22f);
                RenderEffect blur = RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP);
                RenderEffect tinted = RenderEffect.createColorFilterEffect(
                        new PorterDuffColorFilter(0x73141820, PorterDuff.Mode.SRC_OVER), blur);
                backdrop.setRenderEffect(tinted);
            } else {
                backdrop.setColorFilter(0x73141820, PorterDuff.Mode.SRC_OVER);
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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            fastBoxBlur(bitmap, 6);
        }
        backdrop.setImageBitmap(bitmap);
    }

    public static void fastBoxBlur(android.graphics.Bitmap bitmap, int radius) {
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

    private static void attachFlowingAuroraBackground(Activity activity, View rootView) {
        if (activity instanceof Game || activity instanceof ExternalDisplayControlActivity) {
            return;
        }
        if (!(rootView instanceof android.widget.FrameLayout)) {
            return;
        }
        android.widget.FrameLayout contentFrame = (android.widget.FrameLayout) rootView;
        if (contentFrame.findViewWithTag("oneui_flowing_aurora") != null) {
            return;
        }
        com.limelight.ui.FlowingAuroraView auroraView = new com.limelight.ui.FlowingAuroraView(activity);
        auroraView.setTag("oneui_flowing_aurora");
        contentFrame.addView(auroraView, 0, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        if (contentFrame.getChildCount() > 1) {
            View firstContent = contentFrame.getChildAt(1);
            if (firstContent != null) {
                firstContent.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
        }
    }

    public static void refreshBackdrop(Activity activity) {
        if (activity == null) {
            return;
        }
        View rootView = activity.findViewById(android.R.id.content);
        if (rootView == null) {
            return;
        }
        View aurora = rootView.findViewWithTag("oneui_flowing_aurora");
        if (aurora instanceof com.limelight.ui.FlowingAuroraView) {
            ((com.limelight.ui.FlowingAuroraView) aurora).refreshFromPrefs();
        }
    }

    public static void applyMenuImmersiveMode(final Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return;
        }
        if (activity instanceof Game || activity instanceof ExternalDisplayControlActivity) {
            return;
        }
        android.view.Window window = activity.getWindow();
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        View decorView = window.getDecorView();
        androidx.core.view.WindowInsetsControllerCompat controller =
                androidx.core.view.WindowCompat.getInsetsController(window, decorView);
        if (controller != null) {
            controller.setSystemBarsBehavior(
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
        }
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    public static View getMenuForegroundView(Activity activity) {
        if (activity == null) {
            return null;
        }
        View rootView = activity.findViewById(android.R.id.content);
        if (!(rootView instanceof android.widget.FrameLayout)) {
            return null;
        }
        android.widget.FrameLayout contentFrame = (android.widget.FrameLayout) rootView;
        for (int i = 0; i < contentFrame.getChildCount(); i++) {
            View child = contentFrame.getChildAt(i);
            if (child != null
                    && !"oneui_flowing_aurora".equals(child.getTag())
                    && !"oneui_portal_overlay".equals(child.getTag())) {
                return child;
            }
        }
        return null;
    }

    public static void animateMenuForegroundEnter(Activity activity, boolean returningFromDeeper) {
        final View fg = getMenuForegroundView(activity);
        if (fg == null) {
            return;
        }
        fg.animate().cancel();
        float startScale = returningFromDeeper ? 1.05f : 0.93f;
        fg.setAlpha(0f);
        fg.setScaleX(startScale);
        fg.setScaleY(startScale);
        fg.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(260L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2.0f))
                .start();
    }

    public static void restoreMenuForegroundIfNeeded(Activity activity) {
        final View fg = getMenuForegroundView(activity);
        if (fg == null) {
            return;
        }
        if (fg.getAlpha() < 0.99f || Math.abs(fg.getScaleX() - 1f) > 0.005f) {
            animateMenuForegroundEnter(activity, true);
        }
    }

    public static void startMenuActivityWithDepth(final Activity from, final Intent intent) {
        if (from == null || intent == null) {
            return;
        }
        final View fg = getMenuForegroundView(from);
        if (fg == null) {
            from.startActivity(intent);
            from.overridePendingTransition(0, 0);
            return;
        }
        fg.animate().cancel();
        fg.animate()
                .alpha(0f)
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setDuration(150L)
                .setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f))
                .withEndAction(() -> {
                    if (!from.isFinishing()) {
                        from.startActivity(intent);
                        from.overridePendingTransition(0, 0);
                    }
                })
                .start();
    }

    public static void finishMenuWithDepthTransition(final Activity activity, final Runnable superFinish) {
        if (activity == null) {
            return;
        }
        final View fg = getMenuForegroundView(activity);
        if (fg == null) {
            superFinish.run();
            activity.overridePendingTransition(0, 0);
            return;
        }
        fg.animate().cancel();
        fg.animate()
                .alpha(0f)
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(160L)
                .setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f))
                .withEndAction(() -> {
                    superFinish.run();
                    activity.overridePendingTransition(0, 0);
                })
                .start();
    }

    public static void notifyNewRootView(final Activity activity)
    {
        View rootView = activity.findViewById(android.R.id.content);
        UiModeManager modeMgr = (UiModeManager) activity.getSystemService(Context.UI_MODE_SERVICE);

        // Set GameState.MODE_NONE initially for all activities
        setGameModeStatus(activity, false, false);

        attachFlowingAuroraBackground(activity, rootView);
        applyReachabilitySpacing(activity);
        applyMenuImmersiveMode(activity);

        // Re-apply sticky immersive mode whenever this menu regains window focus
        if (!(activity instanceof Game || activity instanceof ExternalDisplayControlActivity)
                && activity.getWindow() != null) {
            final View decorView = activity.getWindow().getDecorView();
            if (decorView.getTag(R.id.oneui_immersive_listener_tag) == null) {
                decorView.setTag(R.id.oneui_immersive_listener_tag, Boolean.TRUE);
                decorView.getViewTreeObserver().addOnWindowFocusChangeListener(hasFocus -> {
                    if (hasFocus) {
                        applyMenuImmersiveMode(activity);
                        restoreMenuForegroundIfNeeded(activity);
                    }
                });
            }
            // Depth-plane zoom/fade enter animation for menu foreground (keeping shared background still)
            if (!(activity instanceof PcView)) {
                animateMenuForegroundEnter(activity, false);
            }
        }

        if (modeMgr.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            // Increase view padding on TVs
            float scale = activity.getResources().getDisplayMetrics().density;
            int verticalPaddingPixels = (int) (TV_VERTICAL_PADDING_DP*scale + 0.5f);
            int horizontalPaddingPixels = (int) (TV_HORIZONTAL_PADDING_DP*scale + 0.5f);

            rootView.setPadding(horizontalPaddingPixels, verticalPaddingPixels,
                    horizontalPaddingPixels, verticalPaddingPixels);
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
