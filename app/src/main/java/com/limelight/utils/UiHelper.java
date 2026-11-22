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
import com.limelight.SessionMetricsActivity;
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

    public static float getCardOutlineScaleFactor(Context context) {
        if (context == null) {
            return 1.0f;
        }
        int pct = com.limelight.profiles.ProfilesManager.getInstance()
                .getOverlayingSharedPreferences(context)
                .getInt(PreferenceConfiguration.CARD_OUTLINE_THICKNESS_PREF_STRING,
                        PreferenceConfiguration.DEFAULT_CARD_OUTLINE_THICKNESS);
        return Math.max(0f, Math.min(20.0f, pct / 100f));
    }

    public static float getCardOutlineWidthPx(Context context, float baseDp) {
        float scale = getCardOutlineScaleFactor(context);
        if (scale <= 0.01f) {
            return 0f;
        }
        return Math.max(1f, dpToPx(context, baseDp * scale));
    }

    public static android.graphics.drawable.Drawable createInteractiveCardForeground(Context context, float cornerRadiusDp) {
        float radiusPx = dpToPx(context, cornerRadiusDp);
        int restStroke = Math.round(getCardOutlineWidthPx(context, 1f));
        int activeStroke = Math.max(restStroke, Math.round(dpToPx(context, 1.5f)));
        com.limelight.ui.AppThemePalette palette = com.limelight.ui.AppThemePalette.getCurrent(context);
        int rim = palette.getCardRimColor();
        int neon = palette.getNeonAccent();
        int pressedFill = com.limelight.ui.AppThemePalette.withAlpha(palette.accent, palette.isLight ? 0.14f : 0.22f);
        int focusFill = com.limelight.ui.AppThemePalette.withAlpha(palette.accent, palette.isLight ? 0.10f : 0.16f);

        android.graphics.drawable.StateListDrawable sld = new android.graphics.drawable.StateListDrawable();

        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setCornerRadius(radiusPx);
        pressed.setColor(pressedFill);
        pressed.setStroke(activeStroke, com.limelight.ui.AppThemePalette.withAlpha(neon, 0.85f));
        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);

        GradientDrawable focused = new GradientDrawable();
        focused.setShape(GradientDrawable.RECTANGLE);
        focused.setCornerRadius(radiusPx);
        focused.setColor(focusFill);
        focused.setStroke(activeStroke, com.limelight.ui.AppThemePalette.withAlpha(neon, 0.92f));
        sld.addState(new int[]{android.R.attr.state_focused}, focused);

        GradientDrawable selected = new GradientDrawable();
        selected.setShape(GradientDrawable.RECTANGLE);
        selected.setCornerRadius(radiusPx);
        selected.setColor(focusFill);
        selected.setStroke(activeStroke, com.limelight.ui.AppThemePalette.withAlpha(neon, 0.78f));
        sld.addState(new int[]{android.R.attr.state_selected}, selected);

        GradientDrawable hovered = new GradientDrawable();
        hovered.setShape(GradientDrawable.RECTANGLE);
        hovered.setCornerRadius(radiusPx);
        hovered.setColor(com.limelight.ui.AppThemePalette.withAlpha(palette.accent, 0.08f));
        if (restStroke > 0) {
            hovered.setStroke(restStroke, com.limelight.ui.AppThemePalette.withAlpha(rim, 0.75f));
        }
        sld.addState(new int[]{android.R.attr.state_hovered}, hovered);

        GradientDrawable idle = new GradientDrawable();
        idle.setShape(GradientDrawable.RECTANGLE);
        idle.setCornerRadius(radiusPx);
        idle.setColor(android.graphics.Color.TRANSPARENT);
        if (restStroke > 0) {
            idle.setStroke(restStroke, rim);
        }
        sld.addState(new int[]{}, idle);

        return sld;
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
        com.limelight.ui.FlowingAuroraView aurora = (auroraCandidate instanceof com.limelight.ui.FlowingAuroraView)
                ? (com.limelight.ui.FlowingAuroraView) auroraCandidate
                : null;
        View portalCandidate = content.findViewWithTag("oneui_portal_starfield_bg");
        com.limelight.ui.PortalShaderView portal = (portalCandidate instanceof com.limelight.ui.PortalShaderView)
                ? (com.limelight.ui.PortalShaderView) portalCandidate
                : null;
        com.limelight.ui.AppThemePalette palette = com.limelight.ui.AppThemePalette.getCurrent(view.getContext());
        int themedFrostTint = palette.getFrostedCardTint(frostTintColor);
        int themedRimColor = palette.getCardRimColor();
        float radiusPx = dpToPx(view.getContext(), cornerRadiusDp);
        boolean isSettingsMenuCard = (activity instanceof com.limelight.preferences.StreamSettings);
        float strokeWidthPx = isSettingsMenuCard
                ? Math.max(1f, dpToPx(view.getContext(), 1f))
                : getCardOutlineWidthPx(view.getContext(), 1f);
        if (view.getBackground() instanceof com.limelight.ui.FlowingAuroraView.FrostedCardDrawable) {
            com.limelight.ui.FlowingAuroraView.FrostedCardDrawable existing =
                    (com.limelight.ui.FlowingAuroraView.FrostedCardDrawable) view.getBackground();
            existing.updateStyle(cornerMode, radiusPx, themedFrostTint, aurora, portal);
            existing.setStrokeWidthPx(strokeWidthPx);
            existing.setRimColor(themedRimColor);
            return true;
        }
        com.limelight.ui.FlowingAuroraView.FrostedCardDrawable created =
                new com.limelight.ui.FlowingAuroraView.FrostedCardDrawable(
                        view,
                        aurora,
                        portal,
                        cornerMode,
                        radiusPx,
                        themedFrostTint,
                        strokeWidthPx);
        created.setRimColor(themedRimColor);
        view.setBackground(created);
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

        View settingsHeaderBar = activity.findViewById(R.id.settingsHeaderBar);
        if (settingsHeaderBar != null && settingsHeaderBar.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) settingsHeaderBar.getLayoutParams();
            lp.topMargin = reachabilityTop;
            settingsHeaderBar.setLayoutParams(lp);
            View settingsRoot = activity.findViewById(R.id.settingsRootContainer);
            if (settingsRoot != null) {
                settingsRoot.setPadding(horizontalMargin, settingsRoot.getPaddingTop(),
                        horizontalMargin, settingsRoot.getPaddingBottom());
            }
        } else {
            View streamSettings = activity.findViewById(R.id.stream_settings);
            if (streamSettings != null) {
                streamSettings.setPadding(
                        horizontalMargin,
                        headerTitleTop,
                        horizontalMargin,
                        streamSettings.getPaddingBottom());
            }
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

        final com.limelight.ui.AppThemePalette menuPalette = com.limelight.ui.AppThemePalette.getCurrent(activity);
        final int itemTextColor = menuPalette.getPrimaryTextColor();
        final android.widget.ArrayAdapter<PopupMenuItem> adapter =
                new android.widget.ArrayAdapter<PopupMenuItem>(themedContext, R.layout.oneui_game_menu_item, items) {
                    @Override
                    public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                        TextView tv = (TextView) super.getView(position, convertView, parent);
                        tv.setTextColor(itemTextColor);
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

    private static GradientDrawable createDialogSquircleDrawable(Context context, boolean blurred) {
        com.limelight.ui.AppThemePalette palette = com.limelight.ui.AppThemePalette.getCurrent(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(context, ONEUI_DIALOG_SQUIRCLE_RADIUS_DP));
        bg.setColor(palette.getDialogSurfaceColor(blurred));
        int strokePx = Math.round(getCardOutlineWidthPx(context, 1f));
        if (strokePx > 0) {
            bg.setStroke(strokePx, palette.getCardRimColor());
        }
        return bg;
    }

    /**
     * Styles a dialog as a One UI 9 blurred squircle card using the active theme palette's
     * Popup Tint, Card Outline, Primary/Secondary Text, and global Blur Amount.
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
            window.setBackgroundDrawable(createDialogSquircleDrawable(dialog.getContext(), true));
            decorView.setRenderEffect(null);
            applyBoundedWindowBlur(window, dialog.getContext());
        } else {
            window.setBackgroundDrawable(createDialogSquircleDrawable(dialog.getContext(), false));
        }
        clearOpaquePanelBackgrounds(decorView);
        attachDialogBackdropBlur(dialog, decorView);
        applyDialogButtonPalette(dialog);
        applyDialogTextPalette(dialog, decorView);
        animateDialogEntranceIfNeeded(dialog, decorView);
    }

    private static void applyDialogTextPalette(final android.app.Dialog dialog, final View decorView) {
        if (dialog == null || decorView == null) {
            return;
        }
        Context ctx = dialog.getContext();
        com.limelight.ui.AppThemePalette palette = com.limelight.ui.AppThemePalette.getCurrent(ctx);
        int primaryText = palette.getPrimaryTextColor();
        int secondaryText = palette.getSecondaryTextColor();
        int accent = palette.getNeonAccent();

        int alertTitleId = ctx.getResources().getIdentifier("alertTitle", "id", ctx.getPackageName());
        int androidTitleId = ctx.getResources().getIdentifier("alertTitle", "id", "android");
        int[] titleIds = { alertTitleId, androidTitleId, android.R.id.title };
        for (int id : titleIds) {
            if (id != 0) {
                View v = decorView.findViewById(id);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(primaryText);
                }
            }
        }
        View msg = decorView.findViewById(android.R.id.message);
        if (msg instanceof TextView) {
            ((TextView) msg).setTextColor(secondaryText);
        }

        // Theme standard AlertDialog ListViews (e.g. ListPreference radio lists) & EditTexts
        View list = decorView.findViewById(android.R.id.list);
        if (!(list instanceof android.widget.ListView)) {
            int selListId = ctx.getResources().getIdentifier("select_dialog_listview", "id", ctx.getPackageName());
            if (selListId != 0) {
                list = decorView.findViewById(selListId);
            }
        }
        if (list instanceof android.widget.ListView) {
            android.widget.ListView lv = (android.widget.ListView) list;
            lv.setDivider(new android.graphics.drawable.ColorDrawable(palette.getDividerColor()));
            lv.setDividerHeight(Math.max(1, Math.round(dpToPx(ctx, 0.75f))));
            tintDialogListChildren(lv, primaryText, accent);
            lv.setOnHierarchyChangeListener(new android.view.ViewGroup.OnHierarchyChangeListener() {
                @Override
                public void onChildViewAdded(View parent, View child) {
                    tintDialogListChildren(child, primaryText, accent);
                }

                @Override
                public void onChildViewRemoved(View parent, View child) {}
            });
        }

        View edit = decorView.findViewById(android.R.id.edit);
        if (edit instanceof android.widget.EditText) {
            android.widget.EditText et = (android.widget.EditText) edit;
            et.setTextColor(primaryText);
            et.setHintTextColor(secondaryText);
        }
    }

    private static void tintDialogListChildren(View view, int primaryText, int accent) {
        if (view instanceof android.widget.CheckedTextView) {
            android.widget.CheckedTextView ctv = (android.widget.CheckedTextView) view;
            ctv.setTextColor(primaryText);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ctv.setCheckMarkTintList(android.content.res.ColorStateList.valueOf(accent));
            }
        } else if (view instanceof android.widget.CompoundButton) {
            android.widget.CompoundButton cb = (android.widget.CompoundButton) view;
            cb.setTextColor(primaryText);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cb.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));
            }
        } else if (view instanceof TextView) {
            ((TextView) view).setTextColor(primaryText);
        } else if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                tintDialogListChildren(vg.getChildAt(i), primaryText, accent);
            }
        }
    }

    private static void animateDialogEntranceIfNeeded(final android.app.Dialog dialog, final View decorView) {
        if (dialog == null || decorView == null || !dialog.isShowing()) {
            return;
        }
        if (Boolean.TRUE.equals(decorView.getTag(R.id.oneui_dialog_entered_tag))) {
            return;
        }
        decorView.setTag(R.id.oneui_dialog_entered_tag, Boolean.TRUE);
        decorView.setAlpha(0f);
        decorView.setScaleX(0.94f);
        decorView.setScaleY(0.94f);
        final Runnable runEnter = () -> {
            if (decorView.getWidth() > 0 && decorView.getHeight() > 0) {
                decorView.setPivotX(decorView.getWidth() * 0.5f);
                decorView.setPivotY(decorView.getHeight() * 0.5f);
            }
            decorView.animate().cancel();
            decorView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200L)
                    .setInterpolator(new android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                    .start();
        };
        if (decorView.getWidth() > 0 && decorView.getHeight() > 0) {
            decorView.postOnAnimation(runEnter);
        } else {
            decorView.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (decorView.getViewTreeObserver().isAlive()) {
                        decorView.getViewTreeObserver().removeOnPreDrawListener(this);
                    }
                    runEnter.run();
                    return true;
                }
            });
        }
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

        final int rawBlurPct = Math.max(0, Math.min(2000,
                PreferenceConfiguration.readPreferences(activity).blurAmountPercent));
        final int blurPct = com.limelight.ui.AppThemePalette.isLiveBlurEffective(activity) ? rawBlurPct : 0;
        if (!com.limelight.ui.AppThemePalette.isLiveBlurEffective(activity)) {
            return;
        }

        // Adaptive capture scale: higher res when blur is low/off, lower res when blur is heavy
        final float captureScale = blurPct == 0 ? 0.50f
                : (blurPct <= 35 ? 0.42f : (blurPct <= 80 ? 0.30f : 0.22f));

        final Runnable ensureBuffers = () -> {
            int dialogW = decorView.getWidth();
            int dialogH = decorView.getHeight();
            if (dialogW <= 0 || dialogH <= 0) {
                return;
            }
            int bw = Math.max(1, Math.round(dialogW * captureScale));
            int bh = Math.max(1, Math.round(dialogH * captureScale));
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
                    mainHandler.postDelayed(tickHolder[0], 16L);
                    return;
                }
                int dialogW = decorView.getWidth();
                int dialogH = decorView.getHeight();
                if (dialogW <= 0 || dialogH <= 0) {
                    mainHandler.postDelayed(tickHolder[0], 16L);
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
                    mainHandler.postDelayed(tickHolder[0], 16L);
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
                    mainHandler.postDelayed(tickHolder[0], 16L);
                    return;
                }

                final boolean fullCoverage = (interLeft == dialogLoc[0] && interTop == dialogLoc[1]
                        && interRight == dialogLoc[0] + dialogW && interBottom == dialogLoc[1] + dialogH);
                final int subW = Math.max(1, Math.round((interRight - interLeft) * captureScale));
                final int subH = Math.max(1, Math.round((interBottom - interTop) * captureScale));
                final android.graphics.Bitmap copyDest = fullCoverage
                        ? target
                        : android.graphics.Bitmap.createBitmap(subW, subH, android.graphics.Bitmap.Config.ARGB_8888);
                final float dstLeft = (interLeft - dialogLoc[0]) * captureScale;
                final float dstTop = (interTop - dialogLoc[1]) * captureScale;

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
                                    applyBitmapToDialogBackdrop(decorView, target, blurPct);
                                    bufferIndex[0] = 1 - idx;
                                } else if (!fullCoverage) {
                                    copyDest.recycle();
                                }
                                if (!stopped[0] && decorView.isAttachedToWindow()) {
                                    mainHandler.postDelayed(tickHolder[0], 16L);
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

        // Menu screens: when API 31+ cross-window blur is active (or blurPct == 0), let SurfaceFlinger
        // composite the live themed PortalShaderView directly through our translucent squircle window
        // so there is zero stale background bleed and 60fps real-time transparency!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            WindowManager wm = activity.getSystemService(WindowManager.class);
            if (blurPct == 0 || (wm != null && wm.isCrossWindowBlurEnabled())) {
                return;
            }
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
            decorView.getLocationOnScreen(dialogLoc);

            // Seed with active theme background (never hardcoded #0B0E1C)
            com.limelight.ui.AppThemePalette palette = com.limelight.ui.AppThemePalette.getCurrent(activity);
            target.eraseColor(palette.background | 0xFF000000);
            android.graphics.Canvas canvas = new android.graphics.Canvas(target);
            View portalCandidate = activityDecor.findViewWithTag("oneui_portal_starfield_bg");
            if (portalCandidate instanceof com.limelight.ui.PortalShaderView
                    && portalCandidate.getWidth() > 0 && portalCandidate.getHeight() > 0) {
                com.limelight.ui.PortalShaderView portal = (com.limelight.ui.PortalShaderView) portalCandidate;
                android.graphics.Bitmap portalBmp = portal.getBlurredBitmap();
                if (portalBmp != null && !portalBmp.isRecycled()) {
                    int[] portalLoc = new int[2];
                    portalCandidate.getLocationOnScreen(portalLoc);
                    float sx = portalBmp.getWidth() / (float) portalCandidate.getWidth();
                    float sy = portalBmp.getHeight() / (float) portalCandidate.getHeight();
                    int relX = dialogLoc[0] - portalLoc[0];
                    int relY = dialogLoc[1] - portalLoc[1];
                    android.graphics.Rect srcR = new android.graphics.Rect(
                            Math.max(0, Math.round(relX * sx)),
                            Math.max(0, Math.round(relY * sy)),
                            Math.min(portalBmp.getWidth(), Math.round((relX + decorView.getWidth()) * sx)),
                            Math.min(portalBmp.getHeight(), Math.round((relY + decorView.getHeight()) * sy)));
                    if (!srcR.isEmpty()) {
                        android.graphics.Rect dstR = new android.graphics.Rect(0, 0, target.getWidth(), target.getHeight());
                        android.graphics.Paint bmpPaint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
                        canvas.drawBitmap(portalBmp, srcR, dstR, bmpPaint);
                    }
                }
            }
            // Draw ONLY the foreground menu layer (not activityDecor's opaque windowBackground)
            View fgView = getMenuForegroundView(activity);
            if (fgView != null && fgView.getVisibility() == View.VISIBLE) {
                int[] fgLoc = new int[2];
                fgView.getLocationOnScreen(fgLoc);
                canvas.save();
                canvas.scale(captureScale, captureScale);
                canvas.translate(fgLoc[0] - dialogLoc[0], fgLoc[1] - dialogLoc[1]);
                fgView.draw(canvas);
                canvas.restore();
            }
            applyBitmapToDialogBackdrop(decorView, target, blurPct);
            bufferIndex[0] = 1 - idx;
            lastCaptureMs[0] = android.os.SystemClock.uptimeMillis();
        };

        final android.view.ViewTreeObserver.OnPreDrawListener preDrawListener = () -> {
            if (!stopped[0] && decorView.isAttachedToWindow() && !inFlight[0]) {
                long now = android.os.SystemClock.uptimeMillis();
                if (now - lastCaptureMs[0] >= 16L) {
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

    private static void applyBitmapToDialogBackdrop(View decorView, android.graphics.Bitmap bitmap, int blurPct) {
        if (!(decorView instanceof android.view.ViewGroup)) {
            return;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) decorView;
        Context ctx = decorView.getContext();
        com.limelight.ui.AppThemePalette palette = com.limelight.ui.AppThemePalette.getCurrent(ctx);
        int overlayTint = palette.getDialogOverlayTint();

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
                float radiusDp = 22f * (blurPct / 100f);
                if (radiusDp > 0.5f) {
                    float radiusPx = dpToPx(ctx, Math.min(64f, radiusDp));
                    RenderEffect blur = RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP);
                    RenderEffect tinted = RenderEffect.createColorFilterEffect(
                            new PorterDuffColorFilter(overlayTint, PorterDuff.Mode.SRC_OVER), blur);
                    backdrop.setRenderEffect(tinted);
                } else {
                    backdrop.setRenderEffect(null);
                    backdrop.setColorFilter(overlayTint, PorterDuff.Mode.SRC_OVER);
                }
            } else {
                backdrop.setColorFilter(overlayTint, PorterDuff.Mode.SRC_OVER);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int dialogStroke = Math.round(getCardOutlineWidthPx(ctx, 1f));
                if (dialogStroke > 0) {
                    GradientDrawable border = new GradientDrawable();
                    border.setShape(GradientDrawable.RECTANGLE);
                    border.setColor(android.graphics.Color.TRANSPARENT);
                    border.setCornerRadius(dpToPx(ctx, ONEUI_DIALOG_SQUIRCLE_RADIUS_DP));
                    border.setStroke(dialogStroke, palette.getCardRimColor());
                    decorView.setForeground(border);
                } else {
                    decorView.setForeground(null);
                }
            }
            decorView.setClipToOutline(true);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && blurPct > 0) {
            int boxRadius = Math.max(1, Math.min(18, Math.round(6f * (blurPct / 100f))));
            fastBoxBlur(bitmap, boxRadius);
        }
        backdrop.setImageBitmap(bitmap);
    }

    public static void fastBoxBlur(android.graphics.Bitmap bitmap, int radius) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w <= 1 || h <= 1 || radius <= 0) {
            return;
        }
        int needed = w * h;
        fastBoxBlur(bitmap, radius, new int[needed], new int[needed]);
    }

    public static void fastBoxBlur(android.graphics.Bitmap bitmap, int radius, int[] pix, int[] tmp) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int needed = w * h;
        if (w <= 1 || h <= 1 || radius <= 0 || pix == null || tmp == null
                || pix.length < needed || tmp.length < needed) {
            return;
        }
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);
        int div = radius * 2 + 1;
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < h; y++) {
                int row = y * w;
                int rSum = 0, gSum = 0, bSum = 0;
                for (int k = -radius; k <= radius; k++) {
                    int c = pix[row + Math.max(0, Math.min(w - 1, k))];
                    rSum += (c >> 16) & 0xFF;
                    gSum += (c >> 8) & 0xFF;
                    bSum += c & 0xFF;
                }
                for (int x = 0; x < w; x++) {
                    tmp[row + x] = 0xFF000000 | ((rSum / div) << 16) | ((gSum / div) << 8) | (bSum / div);
                    int outC = pix[row + Math.max(0, x - radius)];
                    int inC = pix[row + Math.min(w - 1, x + radius + 1)];
                    rSum += ((inC >> 16) & 0xFF) - ((outC >> 16) & 0xFF);
                    gSum += ((inC >> 8) & 0xFF) - ((outC >> 8) & 0xFF);
                    bSum += (inC & 0xFF) - (outC & 0xFF);
                }
            }
            for (int x = 0; x < w; x++) {
                int rSum = 0, gSum = 0, bSum = 0;
                for (int k = -radius; k <= radius; k++) {
                    int c = tmp[Math.max(0, Math.min(h - 1, k)) * w + x];
                    rSum += (c >> 16) & 0xFF;
                    gSum += (c >> 8) & 0xFF;
                    bSum += c & 0xFF;
                }
                for (int y = 0; y < h; y++) {
                    pix[y * w + x] = 0xFF000000 | ((rSum / div) << 16) | ((gSum / div) << 8) | (bSum / div);
                    int outC = tmp[Math.max(0, y - radius) * w + x];
                    int inC = tmp[Math.min(h - 1, y + radius + 1) * w + x];
                    rSum += ((inC >> 16) & 0xFF) - ((outC >> 16) & 0xFF);
                    gSum += ((inC >> 8) & 0xFF) - ((outC >> 8) & 0xFF);
                    bSum += (inC & 0xFF) - (outC & 0xFF);
                }
            }
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h);
    }

    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.S)
    private static void applyBoundedWindowBlur(final Window window, Context context) {
        final WindowManager wm = context.getSystemService(WindowManager.class);
        boolean blurEffective = com.limelight.ui.AppThemePalette.isLiveBlurEffective(context);
        int blurPct = blurEffective
                ? Math.max(0, Math.min(2000, PreferenceConfiguration.readPreferences(context).blurAmountPercent))
                : 0;
        final int blurRadiusPx = blurPct <= 0 ? 0
                : Math.max(1, Math.round(dpToPx(context, Math.min(64f, ONEUI_BLUR_RADIUS_DP * (blurPct / 100f)))));
        final java.util.function.Consumer<Boolean> updateBlur = enabled -> {
            boolean useBlurredStyle = enabled || blurRadiusPx == 0;
            window.setBackgroundDrawable(createDialogSquircleDrawable(context, useBlurredStyle));
            window.setBackgroundBlurRadius(enabled ? blurRadiusPx : 0);
        };

        boolean blurEnabled = wm != null && wm.isCrossWindowBlurEnabled();
        updateBlur.accept(blurEnabled);

        final View decorView = window.getDecorView();
        if (wm == null || decorView.getTag(R.id.oneui_window_blur_listener_tag) != null) {
            return;
        }
        decorView.setTag(R.id.oneui_window_blur_listener_tag, Boolean.TRUE);
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
        sStreamActive = true;
        Activity owner = sActivePortalOwner != null ? sActivePortalOwner.get() : null;
        if (owner != null) {
            setPortalBackdropRendering(owner, false);
        }
        setGameModeStatus(context, true, true);
    }

    public static void notifyStreamConnected(Context context) {
        sStreamActive = true;
        setGameModeStatus(context, true, false);
    }

    public static void notifyStreamEnteringPiP(Context context) {
        sStreamActive = true;
        setGameModeStatus(context, true, true);
    }

    public static void notifyStreamExitingPiP(Context context) {
        sStreamActive = true;
        setGameModeStatus(context, true, false);
    }

    public static void notifyStreamEnded(Context context) {
        sStreamActive = false;
        setGameModeStatus(context, false, false);
        final Activity resumed = sResumedMenuActivity != null ? sResumedMenuActivity.get() : null;
        if (resumed != null && !resumed.isFinishing() && !resumed.isDestroyed()) {
            resumed.runOnUiThread(() -> {
                if (!sStreamActive && !resumed.isFinishing() && !resumed.isDestroyed()
                        && sResumedMenuActivity.get() == resumed) {
                    sActivePortalOwner = new java.lang.ref.WeakReference<>(resumed);
                    setPortalBackdropRendering(resumed, true);
                }
            });
        }
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

    private static volatile boolean portalLifecycleRegistered = false;
    private static volatile boolean sStreamActive = false;
    private static java.lang.ref.WeakReference<Activity> sActivePortalOwner = new java.lang.ref.WeakReference<>(null);
    private static java.lang.ref.WeakReference<Activity> sResumedMenuActivity = new java.lang.ref.WeakReference<>(null);
    private static int animGenerationCounter = 0;

    private static int nextAnimGeneration() {
        return ++animGenerationCounter;
    }

    private static boolean isLandscapeActivity(Activity activity) {
        if (activity == null) {
            return false;
        }
        return activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private static void ensurePortalBackdropLifecycleRegistered(Activity activity) {
        if (portalLifecycleRegistered || activity == null || activity.getApplication() == null) {
            return;
        }
        portalLifecycleRegistered = true;
        activity.getApplication().registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity a, android.os.Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(Activity a) {
                if (a == null || a instanceof Game || a instanceof ExternalDisplayControlActivity) {
                    return;
                }
                if (!sStreamActive && sActivePortalOwner.get() == a) {
                    setPortalBackdropRendering(a, true);
                }
            }

            @Override
            public void onActivityResumed(Activity a) {
                if (a == null || a instanceof Game || a instanceof ExternalDisplayControlActivity) {
                    return;
                }
                sResumedMenuActivity = new java.lang.ref.WeakReference<>(a);
                com.limelight.ui.PortalShaderView.markUserInteraction();
                Activity prev = sActivePortalOwner.get();
                if (prev != null && prev != a) {
                    setPortalBackdropRendering(prev, false);
                }
                if (sStreamActive && !(a instanceof SessionMetricsActivity)) {
                    setPortalBackdropRendering(a, false);
                    return;
                }
                sActivePortalOwner = new java.lang.ref.WeakReference<>(a);
                setPortalBackdropRendering(a, true);
            }

            @Override
            public void onActivityPaused(Activity a) {
                if (sResumedMenuActivity.get() == a) {
                    sResumedMenuActivity.clear();
                }
                // Handoff to a new foreground Activity is handled cleanly in onActivityResumed
                // via sActivePortalOwner so translucent dialogs stay backed while double GL threads are prevented.
            }

            @Override
            public void onActivityStopped(Activity a) {
                setPortalBackdropRendering(a, false);
                if (sResumedMenuActivity.get() == a) {
                    sResumedMenuActivity.clear();
                }
                if (sActivePortalOwner.get() == a) {
                    sActivePortalOwner.clear();
                }
            }

            @Override
            public void onActivitySaveInstanceState(Activity a, android.os.Bundle outState) {}

            @Override
            public void onActivityDestroyed(Activity a) {
                setPortalBackdropRendering(a, false);
                if (sResumedMenuActivity.get() == a) {
                    sResumedMenuActivity.clear();
                }
                if (sActivePortalOwner.get() == a) {
                    sActivePortalOwner.clear();
                }
            }
        });
    }

    private static void syncHandoffBackdrop(Activity activity, View rootView, com.limelight.ui.PortalShaderView psv) {
        View handoffCandidate = rootView.findViewWithTag("oneui_portal_handoff_bg");
        if (!(handoffCandidate instanceof android.widget.ImageView)) {
            return;
        }
        final android.widget.ImageView handoffView = (android.widget.ImageView) handoffCandidate;
        if (psv.hasRenderedFrame()) {
            handoffView.setVisibility(View.GONE);
            return;
        }
        android.graphics.Bitmap snap = com.limelight.ui.PortalShaderView.getHandoffBitmap(isLandscapeActivity(activity));
        if (snap != null) {
            handoffView.setImageBitmap(snap);
        } else {
            handoffView.setImageDrawable(null);
        }
        handoffView.setVisibility(View.VISIBLE);
        psv.setFirstFrameListener(() -> handoffView.setVisibility(View.GONE));
    }

    private static void setPortalBackdropRendering(Activity activity, boolean running) {
        if (activity == null || activity instanceof Game || activity instanceof ExternalDisplayControlActivity) {
            return;
        }
        View rootView = activity.findViewById(android.R.id.content);
        if (rootView == null) {
            return;
        }
        View bg = rootView.findViewWithTag("oneui_portal_starfield_bg");
        if (bg instanceof com.limelight.ui.PortalShaderView) {
            com.limelight.ui.PortalShaderView psv = (com.limelight.ui.PortalShaderView) bg;
            if (running) {
                syncHandoffBackdrop(activity, rootView, psv);
                psv.startRendering();
            } else {
                psv.stopRenderingAsync();
            }
        }
    }

    public static void captureCurrentStarfieldHandoff(Activity activity) {
        if (activity == null) {
            return;
        }
        View rootView = activity.findViewById(android.R.id.content);
        if (rootView == null) {
            return;
        }
        View bg = rootView.findViewWithTag("oneui_portal_starfield_bg");
        if (bg instanceof com.limelight.ui.PortalShaderView) {
            com.limelight.ui.PortalShaderView.captureHandoffFrame((com.limelight.ui.PortalShaderView) bg);
        }
    }

    private static void attachFlowingAuroraBackground(Activity activity, View rootView) {
        if (activity instanceof Game || activity instanceof ExternalDisplayControlActivity) {
            return;
        }
        if (!(rootView instanceof android.widget.FrameLayout)) {
            return;
        }
        ensurePortalBackdropLifecycleRegistered(activity);
        android.widget.FrameLayout contentFrame = (android.widget.FrameLayout) rootView;
        if (contentFrame.findViewWithTag("oneui_portal_starfield_bg") != null) {
            return;
        }

        Activity prevOwner = sActivePortalOwner.get();
        if (prevOwner != null && prevOwner != activity) {
            setPortalBackdropRendering(prevOwner, false);
        }
        sActivePortalOwner = new java.lang.ref.WeakReference<>(activity);

        com.limelight.ui.AppThemePalette initPalette = com.limelight.ui.AppThemePalette.getCurrent(activity);
        // Seamless starfield handoff placeholder shown on Frame 0 while GL initializes,
        // automatically hidden the instant PortalShaderView swaps its first live frame.
        final android.widget.ImageView handoffView = new android.widget.ImageView(activity);
        handoffView.setTag("oneui_portal_handoff_bg");
        handoffView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        handoffView.setBackgroundColor(initPalette.background | 0xFF000000);
        handoffView.setClickable(false);
        handoffView.setFocusable(false);
        handoffView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        android.graphics.Bitmap snap = com.limelight.ui.PortalShaderView.getHandoffBitmap(isLandscapeActivity(activity));
        if (snap != null) {
            handoffView.setImageBitmap(snap);
        }
        contentFrame.addView(handoffView, 0, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));

        com.limelight.ui.PortalShaderView starfieldView = new com.limelight.ui.PortalShaderView(activity);
        starfieldView.setTag("oneui_portal_starfield_bg");
        starfieldView.configureStarfieldBackgroundMode();
        starfieldView.setFirstFrameListener(() -> handoffView.setVisibility(View.GONE));
        contentFrame.addView(starfieldView, 1, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        contentFrame.getViewTreeObserver().addOnScrollChangedListener(com.limelight.ui.PortalShaderView::markUserInteraction);
        contentFrame.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) ->
                com.limelight.ui.PortalShaderView.markUserInteraction());
        if (!sStreamActive || activity instanceof SessionMetricsActivity) {
            starfieldView.startRendering();
        }

        View fg = getMenuForegroundView(activity);
        if (fg != null) {
            fg.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }
    }

    public static void refreshBackdrop(Activity activity) {
        if (activity == null) {
            return;
        }
        com.limelight.ui.PortalShaderView.markUserInteraction();
        View rootView = activity.findViewById(android.R.id.content);
        if (rootView == null) {
            return;
        }
        View aurora = rootView.findViewWithTag("oneui_flowing_aurora");
        if (aurora instanceof com.limelight.ui.FlowingAuroraView) {
            ((com.limelight.ui.FlowingAuroraView) aurora).refreshFromPrefs();
        }
        View starfield = rootView.findViewWithTag("oneui_portal_starfield_bg");
        if (starfield instanceof com.limelight.ui.PortalShaderView) {
            com.limelight.ui.PortalShaderView psv = (com.limelight.ui.PortalShaderView) starfield;
            psv.refreshFromPrefs();
            if (!sStreamActive || activity instanceof SessionMetricsActivity) {
                syncHandoffBackdrop(activity, rootView, psv);
                psv.startRendering();
            }
        }
        applyPaletteChrome(activity, rootView);
    }

    public static void applyPaletteChrome(Activity activity, View rootView) {
        if (activity == null || rootView == null) {
            return;
        }
        com.limelight.ui.AppThemePalette palette = com.limelight.ui.AppThemePalette.getCurrent(activity);
        if (activity.getWindow() != null) {
            if (!(activity instanceof Game || activity instanceof ExternalDisplayControlActivity
                    || activity instanceof com.limelight.SessionMetricsActivity)) {
                activity.getWindow().setBackgroundDrawable(
                        new android.graphics.drawable.ColorDrawable(palette.background | 0xFF000000));
            }
            androidx.core.view.WindowInsetsControllerCompat ctrl =
                    androidx.core.view.WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
            if (ctrl != null) {
                ctrl.setAppearanceLightStatusBars(palette.isLight);
                ctrl.setAppearanceLightNavigationBars(palette.isLight);
            }
        }
        View handoff = rootView.findViewWithTag("oneui_portal_handoff_bg");
        if (handoff != null) {
            handoff.setBackgroundColor(palette.background | 0xFF000000);
        }
        int primaryText = palette.getPrimaryTextColor();
        View pcToolbar = rootView.findViewById(R.id.pcToolbarContainer);
        if (pcToolbar != null) {
            applyLiquidGlassEffect(pcToolbar);
            tintToolbarChrome(pcToolbar, primaryText);
        }
        View appListText = rootView.findViewById(R.id.appListText);
        if (appListText instanceof android.widget.TextView) {
            applyLiquidGlassEffect(appListText);
            ((android.widget.TextView) appListText).setTextColor(primaryText);
        }
        View noPcFound = rootView.findViewById(R.id.no_pc_found_layout);
        if (noPcFound != null) {
            applyLiquidGlassEffect(noPcFound);
            tintToolbarChrome(noPcFound, primaryText);
        }
        View manualPcText = rootView.findViewById(R.id.manuallyAddPcText);
        if (manualPcText instanceof android.widget.TextView) {
            ((android.widget.TextView) manualPcText).setTextColor(primaryText);
        }
        View profilesDesc = rootView.findViewById(R.id.profilesDescription);
        if (profilesDesc instanceof android.widget.TextView) {
            ((android.widget.TextView) profilesDesc).setTextColor(primaryText);
        }
    }

    private static void tintToolbarChrome(View view, int color) {
        if (view instanceof android.widget.TextView) {
            ((android.widget.TextView) view).setTextColor(color);
        } else if (view instanceof android.widget.ImageView) {
            ((android.widget.ImageView) view).setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        } else if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                tintToolbarChrome(vg.getChildAt(i), color);
            }
        }
    }

    /**
     * Disables system open & close transitions on this Activity. On Android 14+ (API 34+),
     * overrideActivityTransition() is a persistent property of the target Activity and must be
     * registered in onCreate() so WindowManager Shell never inserts a solid transition scrim.
     */
    public static void disableAllActivityTransitions(Activity activity) {
        if (activity == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0);
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0);
        }
    }

    public static void disableActivityTransition(Activity activity, boolean isClose) {
        if (activity == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(
                    isClose ? Activity.OVERRIDE_TRANSITION_CLOSE : Activity.OVERRIDE_TRANSITION_OPEN,
                    0,
                    0);
        } else {
            activity.overridePendingTransition(0, 0);
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
                    && !"oneui_portal_starfield_bg".equals(child.getTag())
                    && !"oneui_portal_handoff_bg".equals(child.getTag())
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
        if (Boolean.TRUE.equals(fg.getTag(R.id.oneui_enter_anim_running_tag))) {
            return;
        }
        final int gen = nextAnimGeneration();
        fg.animate().setListener(null).cancel();
        fg.setTag(R.id.oneui_anim_generation_tag, gen);
        fg.setTag(R.id.oneui_exit_anim_running_tag, null);
        fg.setTag(R.id.oneui_enter_anim_running_tag, Boolean.TRUE);
        final float startScale = returningFromDeeper ? 1.045f : 0.945f;
        final long duration = returningFromDeeper ? 270L : 250L;
        fg.setAlpha(0f);
        fg.setScaleX(startScale);
        fg.setScaleY(startScale);

        final Runnable startAnim = () -> {
            if (!Integer.valueOf(gen).equals(fg.getTag(R.id.oneui_anim_generation_tag))) {
                return;
            }
            if (fg.getWidth() > 0 && fg.getHeight() > 0) {
                fg.setPivotX(fg.getWidth() * 0.5f);
                fg.setPivotY(fg.getHeight() * 0.5f);
            }
            fg.animate().setListener(null).cancel();
            fg.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(duration)
                    .setInterpolator(new android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                    .withLayer()
                    .setListener(new android.animation.AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(android.animation.Animator animation) {
                            fg.animate().setListener(null);
                            if (Integer.valueOf(gen).equals(fg.getTag(R.id.oneui_anim_generation_tag))) {
                                fg.setTag(R.id.oneui_enter_anim_running_tag, null);
                            }
                        }

                        @Override
                        public void onAnimationCancel(android.animation.Animator animation) {
                            fg.animate().setListener(null);
                            if (Integer.valueOf(gen).equals(fg.getTag(R.id.oneui_anim_generation_tag))) {
                                fg.setTag(R.id.oneui_enter_anim_running_tag, null);
                            }
                        }
                    })
                    .start();
        };

        if (fg.getWidth() > 0 && fg.getHeight() > 0) {
            fg.postOnAnimation(startAnim);
        } else {
            fg.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (fg.getViewTreeObserver().isAlive()) {
                        fg.getViewTreeObserver().removeOnPreDrawListener(this);
                    }
                    fg.postOnAnimation(startAnim);
                    return true;
                }
            });
        }
    }

    private static volatile boolean returningFromStream = false;

    public static void markReturningFromStream() {
        returningFromStream = true;
    }

    public static void restoreMenuForegroundIfNeeded(Activity activity) {
        final View fg = getMenuForegroundView(activity);
        if (fg == null) {
            return;
        }
        fg.setTag(R.id.oneui_exit_anim_running_tag, null);
        if (Boolean.TRUE.equals(fg.getTag(R.id.oneui_enter_anim_running_tag))) {
            return;
        }
        if (returningFromStream) {
            returningFromStream = false;
            animateMenuForegroundEnter(activity, true);
            return;
        }
        if (fg.getAlpha() < 0.99f || Math.abs(fg.getScaleX() - 1f) > 0.005f) {
            animateMenuForegroundEnter(activity, true);
        }
    }

    public static void startMenuActivityWithDepth(final Activity from, final Intent intent) {
        if (from == null || intent == null || from.isFinishing()) {
            return;
        }
        final View fg = getMenuForegroundView(from);
        if (fg == null) {
            captureCurrentStarfieldHandoff(from);
            from.startActivity(intent);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                from.overridePendingTransition(0, 0);
            }
            return;
        }
        if (Boolean.TRUE.equals(fg.getTag(R.id.oneui_exit_anim_running_tag))) {
            return;
        }
        final int gen = nextAnimGeneration();
        fg.animate().setListener(null).cancel();
        fg.setTag(R.id.oneui_anim_generation_tag, gen);
        fg.setTag(R.id.oneui_enter_anim_running_tag, null);
        fg.setTag(R.id.oneui_exit_anim_running_tag, Boolean.TRUE);
        if (fg.getWidth() > 0 && fg.getHeight() > 0) {
            fg.setPivotX(fg.getWidth() * 0.5f);
            fg.setPivotY(fg.getHeight() * 0.5f);
        }
        fg.animate()
                .alpha(0f)
                .scaleX(1.045f)
                .scaleY(1.045f)
                .setDuration(145L)
                .setInterpolator(new android.view.animation.PathInterpolator(0.4f, 0f, 1f, 1f))
                .withLayer()
                .setListener(new android.animation.AnimatorListenerAdapter() {
                    private boolean canceled = false;

                    @Override
                    public void onAnimationCancel(android.animation.Animator animation) {
                        canceled = true;
                        fg.animate().setListener(null);
                        if (Integer.valueOf(gen).equals(fg.getTag(R.id.oneui_anim_generation_tag))) {
                            fg.setTag(R.id.oneui_exit_anim_running_tag, null);
                        }
                    }

                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        fg.animate().setListener(null);
                        if (Integer.valueOf(gen).equals(fg.getTag(R.id.oneui_anim_generation_tag))) {
                            fg.setTag(R.id.oneui_exit_anim_running_tag, null);
                        }
                        if (!canceled && !from.isFinishing()) {
                            captureCurrentStarfieldHandoff(from);
                            from.startActivity(intent);
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                from.overridePendingTransition(0, 0);
                            }
                        }
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
            captureCurrentStarfieldHandoff(activity);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                disableActivityTransition(activity, true);
            }
            superFinish.run();
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                activity.overridePendingTransition(0, 0);
            }
            return;
        }
        if (Boolean.TRUE.equals(fg.getTag(R.id.oneui_exit_anim_running_tag))) {
            return;
        }
        final int gen = nextAnimGeneration();
        fg.animate().setListener(null).cancel();
        fg.setTag(R.id.oneui_anim_generation_tag, gen);
        fg.setTag(R.id.oneui_enter_anim_running_tag, null);
        fg.setTag(R.id.oneui_exit_anim_running_tag, Boolean.TRUE);
        if (fg.getWidth() > 0 && fg.getHeight() > 0) {
            fg.setPivotX(fg.getWidth() * 0.5f);
            fg.setPivotY(fg.getHeight() * 0.5f);
        }
        fg.animate()
                .alpha(0f)
                .scaleX(0.945f)
                .scaleY(0.945f)
                .setDuration(155L)
                .setInterpolator(new android.view.animation.PathInterpolator(0.4f, 0f, 1f, 1f))
                .withLayer()
                .setListener(new android.animation.AnimatorListenerAdapter() {
                    private boolean canceled = false;

                    @Override
                    public void onAnimationCancel(android.animation.Animator animation) {
                        canceled = true;
                        fg.animate().setListener(null);
                        if (Integer.valueOf(gen).equals(fg.getTag(R.id.oneui_anim_generation_tag))) {
                            fg.setTag(R.id.oneui_exit_anim_running_tag, null);
                        }
                    }

                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        fg.animate().setListener(null);
                        if (Integer.valueOf(gen).equals(fg.getTag(R.id.oneui_anim_generation_tag))) {
                            fg.setTag(R.id.oneui_exit_anim_running_tag, null);
                        }
                        if (!canceled) {
                            captureCurrentStarfieldHandoff(activity);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                disableActivityTransition(activity, true);
                            }
                            superFinish.run();
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                activity.overridePendingTransition(0, 0);
                            }
                        }
                    }
                })
                .start();
    }

    public static void notifyNewRootView(final Activity activity)
    {
        View rootView = activity.findViewById(android.R.id.content);
        UiModeManager modeMgr = (UiModeManager) activity.getSystemService(Context.UI_MODE_SERVICE);

        // Set GameState.MODE_NONE initially for all activities
        setGameModeStatus(activity, false, false);

        // Disable system activity open/close animations on API 34+ before first frame
        if (!(activity instanceof Game || activity instanceof ExternalDisplayControlActivity)) {
            disableAllActivityTransitions(activity);
        }

        attachFlowingAuroraBackground(activity, rootView);
        applyReachabilitySpacing(activity);
        applyPaletteChrome(activity, rootView);
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
