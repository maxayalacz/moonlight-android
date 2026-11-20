package com.limelight;

import android.app.Activity;
import android.app.Dialog;
import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.limelight.binding.input.GameInputDevice;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.AppThemePalette;
import com.limelight.utils.KeyConfigHelper;
import com.limelight.utils.KeyMapper;
import com.limelight.utils.UiHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provide options for ongoing Game Stream.
 * <p>
 * Shown on back action in game activity. Supports both the revamped condensed icon-only
 * squircle live-blur quick menu and the classic text list menu (configurable in Settings).
 */
public class GameMenu implements Game.GameMenuCallbacks {

    public static final long KEY_UP_DELAY = 25;
    private static final long TEST_GAME_FOCUS_DELAY = 10;

    private static final int NEON_YELLOW = 0xFFFFE600;
    private static final int NEON_RED = 0xFFFF3B5C;
    private static final PathInterpolator SPRING_EASE = new PathInterpolator(0.22f, 1f, 0.36f, 1f);

    public static final String PREF_NAME = "specialPrefs"; // SharedPreferences的名称

    public static final String KEY_NAME = "special_key"; // 要保存的键名称

    public static class MenuOption {
        private final String label;
        private final boolean withGameFocus;
        private final Runnable runnable;

        public MenuOption(String label, boolean withGameFocus, Runnable runnable) {
            this.label = label;
            this.withGameFocus = withGameFocus;
            this.runnable = runnable;
        }

        public MenuOption(String label, Runnable runnable) {
            this(label, false, runnable);
        }
    }

    private final Game game;
    private final Context dialogScreenContext;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private Dialog currentDialog;

    public GameMenu(Game game, Context dialogScreenContext) {
        this.game = game;
        this.dialogScreenContext = dialogScreenContext;
    }

    public GameMenu(Game game) {
        this.game = game;
        this.dialogScreenContext = game;
    }

    private String getString(int id) {
        return game.getResources().getString(id);
    }

    private int dp(float dpValue) {
        return Math.max(1, Math.round(UiHelper.dpToPx(dialogScreenContext, dpValue)));
    }

    private void sendKeys(short[] keys) {
        game.sendKeys(keys);
    }

    private void runWithGameFocus(Runnable runnable) {
        // Ensure that the Game activity is still active (not finished)
        if (game.isFinishing()) {
            return;
        }
        // Check if the game window has focus again, if not try again after delay
        if (!game.hasWindowFocus() && dialogScreenContext instanceof Game) {
            uiHandler.postDelayed(() -> runWithGameFocus(runnable), TEST_GAME_FOCUS_DELAY);
            return;
        }
        // Game Activity has focus, run runnable
        runnable.run();
    }

    private void run(MenuOption option) {
        if (option.runnable == null) {
            return;
        }

        if (option.withGameFocus) {
            runWithGameFocus(option.runnable);
        } else {
            option.runnable.run();
        }
    }

    private void showMenuDialog(String title, MenuOption[] options) {
        Context themedContext = new ContextThemeWrapper(dialogScreenContext, R.style.OneUI_DialogTheme);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext, R.style.OneUI_DialogTheme);
        builder.setTitle(title);

        final ArrayAdapter<String> actions = new ArrayAdapter<>(themedContext, R.layout.oneui_game_menu_item);
        for (MenuOption option : options) {
            actions.add(option.label);
        }

        builder.setAdapter(actions, (dialog, which) -> {
            String label = actions.getItem(which);
            for (MenuOption option : options) {
                if (label != null && label.equals(option.label)) {
                    run(option);
                    break;
                }
            }
        });

        builder.setOnCancelListener(dialog -> hideMenu());

        hideMenu();
        AlertDialog alertDialog = builder.create();
        currentDialog = alertDialog;
        UiHelper.styleDialogSquircle(alertDialog);
        alertDialog.show();
        UiHelper.styleDialogSquircle(alertDialog);

        if (alertDialog.getListView() != null) {
            alertDialog.getListView().requestFocus();
        }
    }

    // --- Revamped Condensed Icon-Only Quick Menu ---

    private void showCondensedQuickMenu() {
        hideMenu();

        Context themedContext = new ContextThemeWrapper(dialogScreenContext, R.style.OneUI_DialogTheme);
        final Dialog dialog = new Dialog(themedContext, R.style.OneUI_DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnCancelListener(d -> hideMenu());
        dialog.setOnDismissListener(d -> {
            if (currentDialog == d) {
                currentDialog = null;
            }
        });
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && (keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_ESCAPE)) {
                hideMenu();
                return true;
            }
            return false;
        });

        final AppThemePalette palette = AppThemePalette.getCurrent(game);
        final int tileSize = dp(58f);
        final int tileGap = dp(12f);
        final int cardPad = dp(18f);

        LinearLayout rootColumn = new LinearLayout(themedContext);
        rootColumn.setOrientation(LinearLayout.VERTICAL);
        rootColumn.setGravity(Gravity.CENTER);
        rootColumn.setPadding(cardPad, cardPad, cardPad, cardPad);
        rootColumn.setClipChildren(false);
        rootColumn.setClipToPadding(false);

        LinearLayout row1 = new LinearLayout(themedContext);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER);
        row1.setClipChildren(false);
        row1.setClipToPadding(false);

        LinearLayout row2 = new LinearLayout(themedContext);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);
        row2.setClipChildren(false);
        row2.setClipToPadding(false);

        // 1. Disconnect (Neon Yellow icon & outline)
        ImageButton btnDisconnect = createSquircleIconButton(
                themedContext, R.drawable.ic_qm_disconnect, getString(R.string.game_menu_disconnect), tileSize);
        applyNeonTileStyle(btnDisconnect, NEON_YELLOW, false);
        attachFocusRing(btnDisconnect, () -> applyNeonTileStyle(btnDisconnect, NEON_YELLOW, btnDisconnect.isFocused()));
        btnDisconnect.setOnClickListener(v -> {
            animateTileTap(v);
            hideMenu();
            game.disconnect();
        });

        // 2. Quit Session (Neon Red icon & outline)
        ImageButton btnQuit = createSquircleIconButton(
                themedContext, R.drawable.ic_qm_quit, getString(R.string.game_menu_quit_session), tileSize);
        applyNeonTileStyle(btnQuit, NEON_RED, false);
        attachFocusRing(btnQuit, () -> applyNeonTileStyle(btnQuit, NEON_RED, btnQuit.isFocused()));
        btnQuit.setOnClickListener(v -> {
            animateTileTap(v);
            hideMenu();
            game.quit();
        });

        // 3. Mouse Mode Cycle (Multi touch -> Natural trackpad -> Gaming trackpad -> Absolute touch)
        ImageButton btnMouseMode = createSquircleIconButton(
                themedContext, resolveMouseModeIcon(game.getCurrentMouseMode()),
                getString(R.string.game_menu_select_mouse_mode), tileSize);
        Runnable refreshMouseTile = () -> {
            int mode = game.getCurrentMouseMode();
            btnMouseMode.setImageResource(resolveMouseModeIcon(mode));
            String[] modeNames = game.getResources().getStringArray(R.array.mouse_mode_names);
            String desc = (mode >= 0 && mode < modeNames.length)
                    ? modeNames[mode]
                    : getString(R.string.game_menu_select_mouse_mode);
            setTooltipAndDesc(btnMouseMode, desc);
            boolean active = (mode != 4);
            applyToggleTileStyle(btnMouseMode, active, palette.highlight, btnMouseMode.isFocused());
        };
        refreshMouseTile.run();
        attachFocusRing(btnMouseMode, refreshMouseTile);
        btnMouseMode.setEnabled(game.allowChangeMouseMode);
        if (!game.allowChangeMouseMode) {
            btnMouseMode.setAlpha(0.32f);
        }
        btnMouseMode.setOnClickListener(v -> {
            animateTileTap(v);
            game.cycleQuickMouseMode();
            refreshMouseTile.run();
        });

        // 4. On-Screen Controller Toggle
        ImageButton btnOsc = createSquircleIconButton(
                themedContext, R.drawable.ic_qm_osc, getString(R.string.game_menu_toggle_virtual_model), tileSize);
        Runnable refreshOscTile = () -> applyToggleTileStyle(
                btnOsc, game.isVirtualControllerVisible(), palette.highlight, btnOsc.isFocused());
        refreshOscTile.run();
        attachFocusRing(btnOsc, refreshOscTile);
        if (game.isOnExternalDisplay()) {
            btnOsc.setEnabled(false);
            btnOsc.setAlpha(0.32f);
        } else {
            btnOsc.setOnClickListener(v -> {
                animateTileTap(v);
                game.toggleVirtualController();
                refreshOscTile.run();
            });
        }

        // 5. On-Screen Full Keyboard Toggle
        ImageButton btnKeyboard = createSquircleIconButton(
                themedContext, R.drawable.ic_qm_keyboard, getString(R.string.game_menu_toggle_virtual_keyboard_model), tileSize);
        Runnable refreshKeyboardTile = () -> applyToggleTileStyle(
                btnKeyboard, game.isFullKeyboardVisible(), palette.highlight, btnKeyboard.isFocused());
        refreshKeyboardTile.run();
        attachFocusRing(btnKeyboard, refreshKeyboardTile);
        btnKeyboard.setOnClickListener(v -> {
            animateTileTap(v);
            game.toggleFullKeyboard();
            refreshKeyboardTile.run();
        });

        // 6. Performance Stats Toggle
        ImageButton btnPerf = createSquircleIconButton(
                themedContext, R.drawable.ic_qm_perf_stats, getString(R.string.game_menu_toggle_hud), tileSize);
        Runnable refreshPerfTile = () -> applyToggleTileStyle(
                btnPerf, game.isPerformanceOverlayVisible(), palette.highlight, btnPerf.isFocused());
        refreshPerfTile.run();
        attachFocusRing(btnPerf, refreshPerfTile);
        btnPerf.setOnClickListener(v -> {
            animateTileTap(v);
            game.toggleHUD();
            refreshPerfTile.run();
        });

        // 7. Upload Clipboard (Send to host)
        ImageButton btnUploadClip = createSquircleIconButton(
                themedContext, R.drawable.ic_qm_clipboard_upload, getString(R.string.game_menu_upload_clipboard), tileSize);
        Runnable refreshUploadTile = () -> applyActionTileStyle(btnUploadClip, false, palette.highlight, btnUploadClip.isFocused());
        refreshUploadTile.run();
        attachFocusRing(btnUploadClip, refreshUploadTile);
        btnUploadClip.setOnClickListener(v -> {
            animateTileTap(v);
            flashActionTile(btnUploadClip, palette.highlight, refreshUploadTile);
            game.sendClipboard(true);
        });

        // 8. Fetch Clipboard (Get from host — pass -1 so menu-open guard does not block)
        ImageButton btnFetchClip = createSquircleIconButton(
                themedContext, R.drawable.ic_qm_clipboard_fetch, getString(R.string.game_menu_fetch_clipboard), tileSize);
        Runnable refreshFetchTile = () -> applyActionTileStyle(btnFetchClip, false, palette.highlight, btnFetchClip.isFocused());
        refreshFetchTile.run();
        attachFocusRing(btnFetchClip, refreshFetchTile);
        btnFetchClip.setOnClickListener(v -> {
            animateTileTap(v);
            flashActionTile(btnFetchClip, palette.highlight, refreshFetchTile);
            game.getClipboard(-1);
        });

        addTileToRow(row1, btnDisconnect, tileSize, 0);
        addTileToRow(row1, btnQuit, tileSize, tileGap);
        addTileToRow(row1, btnMouseMode, tileSize, tileGap);
        addTileToRow(row1, btnOsc, tileSize, tileGap);

        addTileToRow(row2, btnKeyboard, tileSize, 0);
        addTileToRow(row2, btnPerf, tileSize, tileGap);
        addTileToRow(row2, btnUploadClip, tileSize, tileGap);
        addTileToRow(row2, btnFetchClip, tileSize, tileGap);

        rootColumn.addView(row1, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row2Lp.topMargin = tileGap;
        rootColumn.addView(row2, row2Lp);

        dialog.setContentView(rootColumn);
        currentDialog = dialog;

        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.CENTER);
            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.18f;
            window.setAttributes(lp);
            View decor = window.getDecorView();
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }

        UiHelper.styleDialogSquircle(dialog);
        dialog.show();
        UiHelper.styleDialogSquircle(dialog);
    }

    private ImageButton createSquircleIconButton(Context ctx, int iconRes, String contentDesc, int tileSizePx) {
        ImageButton btn = new ImageButton(ctx);
        btn.setImageResource(iconRes);
        btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int iconPad = dp(15f);
        btn.setPadding(iconPad, iconPad, iconPad, iconPad);
        btn.setFocusable(true);
        btn.setClickable(true);
        btn.setMinimumWidth(tileSizePx);
        btn.setMinimumHeight(tileSizePx);
        setTooltipAndDesc(btn, contentDesc);
        return btn;
    }

    private void setTooltipAndDesc(View v, String text) {
        v.setContentDescription(text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.setTooltipText(text);
        }
    }

    private void addTileToRow(LinearLayout row, View tile, int tileSizePx, int marginStartPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileSizePx, tileSizePx);
        lp.setMarginStart(marginStartPx);
        row.addView(tile, lp);
    }

    private int resolveMouseModeIcon(int mode) {
        switch (mode) {
            case 0:
                return R.drawable.ic_qm_mouse_multitouch;
            case 2:
                return R.drawable.ic_qm_mouse_trackpad_natural;
            case 3:
                return R.drawable.ic_qm_mouse_trackpad_gaming;
            case 1:
            case 5:
            default:
                return R.drawable.ic_qm_mouse_absolute;
        }
    }

    private void applyNeonTileStyle(ImageButton btn, int neonRgb, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(17f));
        bg.setColor(AppThemePalette.withAlpha(neonRgb, focused ? 0.28f : 0.16f));
        bg.setStroke(Math.max(1, dp(focused ? 2.2f : 1.6f)),
                AppThemePalette.withAlpha(neonRgb, focused ? 1.0f : 0.92f));
        btn.setBackground(bg);
        btn.setColorFilter(neonRgb, PorterDuff.Mode.SRC_IN);
    }

    private void applyToggleTileStyle(ImageButton btn, boolean isOn, int accentRgb, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(17f));
        if (isOn) {
            bg.setColor(AppThemePalette.withAlpha(accentRgb, focused ? 0.30f : 0.20f));
            int strokeColor = focused ? Color.WHITE : AppThemePalette.withAlpha(Color.WHITE, 0.88f);
            bg.setStroke(Math.max(1, dp(focused ? 2.1f : 1.6f)), strokeColor);
            btn.setBackground(bg);
            btn.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        } else {
            bg.setColor(focused ? 0x2AFFFFFF : 0x16FFFFFF);
            int strokeColor = focused ? 0x99FFFFFF : 0x3AFFFFFF;
            bg.setStroke(Math.max(1, dp(focused ? 1.8f : 1.15f)), strokeColor);
            btn.setBackground(bg);
            btn.setColorFilter(focused ? 0xB8FFFFFF : 0x6EFFFFFF, PorterDuff.Mode.SRC_IN);
        }
    }

    private void applyActionTileStyle(ImageButton btn, boolean lit, int accentRgb, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(17f));
        if (lit) {
            bg.setColor(AppThemePalette.withAlpha(accentRgb, 0.30f));
            bg.setStroke(Math.max(1, dp(1.8f)), Color.WHITE);
            btn.setBackground(bg);
            btn.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        } else {
            bg.setColor(focused ? 0x2EFFFFFF : 0x1CFFFFFF);
            bg.setStroke(Math.max(1, dp(focused ? 1.9f : 1.25f)), focused ? 0xCCFFFFFF : 0x56FFFFFF);
            btn.setBackground(bg);
            btn.setColorFilter(focused ? Color.WHITE : 0xC8FFFFFF, PorterDuff.Mode.SRC_IN);
        }
    }

    private void attachFocusRing(View btn, Runnable refreshStyle) {
        btn.setOnFocusChangeListener((v, hasFocus) -> {
            refreshStyle.run();
            v.animate()
                    .scaleX(hasFocus ? 1.06f : 1f)
                    .scaleY(hasFocus ? 1.06f : 1f)
                    .setDuration(140L)
                    .setInterpolator(SPRING_EASE)
                    .start();
        });
    }

    private void animateTileTap(View v) {
        v.animate().cancel();
        final float targetScale = v.isFocused() ? 1.06f : 1f;
        v.animate()
                .scaleX(0.90f)
                .scaleY(0.90f)
                .setDuration(75L)
                .setInterpolator(SPRING_EASE)
                .withEndAction(() -> v.animate()
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .setDuration(165L)
                        .setInterpolator(SPRING_EASE)
                        .start())
                .start();
    }

    private void flashActionTile(ImageButton btn, int accentRgb, Runnable restoreStyle) {
        applyActionTileStyle(btn, true, accentRgb, btn.isFocused());
        uiHandler.postDelayed(() -> {
            if (isMenuOpen()) {
                restoreStyle.run();
            }
        }, 260L);
    }

    // --- Classic List Quick Menu (Fallback Option) ---

    private void showSpecialKeysMenu() {
        List<MenuOption> options = new ArrayList<>();

        if(!PreferenceConfiguration.readPreferences(game).disableDefaultExtraKeys){
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_esc),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_ESCAPE})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_f11),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_F11})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_f4),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_F4})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_enter),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_RETURN})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_v),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_V})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_d),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_D})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_g),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_G})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_tab),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_TAB})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_shift_tab),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_TAB})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_shift_left),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_LEFT})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_f1),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F1})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_f12),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F12})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_b),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_B})));
        }

        // Import custom shortcuts
        SharedPreferences preferences = game.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE);
        String value = preferences.getString(KEY_NAME,"");

        if(!TextUtils.isEmpty(value)){
            try {
                KeyConfigHelper.ShortcutFile shortcutFile = KeyConfigHelper.parseShortcutFile(value);
                if (shortcutFile != null && shortcutFile.data != null && !shortcutFile.data.isEmpty()) {
                    List<KeyConfigHelper.Shortcut> data = shortcutFile.data;
                    for (KeyConfigHelper.Shortcut sc : data) {
                        List<String> keys = sc.keys;
                        short[] keyCodes = new short[keys.size()];

                        for (int i = 0; i < keys.size(); i++) {
                            String code = keys.get(i);
                            int keycode;

                            if (code.startsWith("0x")) {               // literal hex value
                                keycode = Integer.parseInt(code.substring(2), 16);
                            } else if (code.startsWith("VK_")) {       // symbolic constant in KeyMapper
                                Field field = KeyMapper.class.getDeclaredField(code);
                                keycode = field.getInt(null);
                            } else {                                   // unsupported
                                throw new IllegalArgumentException("Unknown key code: " + code);
                            }
                            keyCodes[i] = (short) keycode;
                        }

                        MenuOption option = new MenuOption(sc.name, () -> sendKeys(keyCodes));
                        options.add(option);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(game,getString(R.string.wrong_import_format),Toast.LENGTH_SHORT).show();
            }
        }
        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(getString(R.string.game_menu_send_keys), options.toArray(new MenuOption[options.size()]));
    }

    private void showAdvancedMenu(GameInputDevice device) {
        List<MenuOption> options = new ArrayList<>();
        if (game.allowChangeMouseMode) {
            options.add(new MenuOption(getString(R.string.game_menu_select_mouse_mode), true, () -> game.selectMouseMode(dialogScreenContext)));
        }

        options.add(new MenuOption(getString(R.string.game_menu_toggle_hud), true, game::toggleHUD));
        options.add(new MenuOption(getString(R.string.game_menu_toggle_floating_button), true, game::toggleFloatingButtonVisibility));
        options.add(new MenuOption(getString(R.string.game_menu_toggle_keyboard_model), true, game::toggleKeyboardController));
        if (!game.isOnExternalDisplay()) {
            options.add(new MenuOption(getString(R.string.game_menu_toggle_virtual_model), true, game::toggleVirtualController));
        }
        options.add(new MenuOption(getString(R.string.game_menu_toggle_virtual_keyboard_model), true, game::toggleFullKeyboard));
        options.add(new MenuOption(getString(R.string.game_menu_task_manager), true, () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_ESCAPE})));

        // **FIXED:** This is a UI navigation action, so it should not use withGameFocus.
        options.add(new MenuOption(getString(R.string.game_menu_send_keys), () -> {
            hideMenu();
            showSpecialKeysMenu();
        }));

        options.add(new MenuOption(getString(R.string.game_menu_switch_touch_sensitivity_model), true, game::switchTouchSensitivity));
        if (device != null) {
            options.addAll(device.getGameMenuOptions());
        }
        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));
        showMenuDialog(getString(R.string.game_menu_advanced), options.toArray(new MenuOption[options.size()]));
    }

    private void showServerCmd(ArrayList<String> serverCmds) {
        List<MenuOption> options = new ArrayList<>();

        AtomicInteger index = new AtomicInteger(0);
        for (String str : serverCmds) {
            final int finalI = index.getAndIncrement();
            options.add(new MenuOption("> " + str, true, () -> game.sendExecServerCmd(finalI)));
        }

        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(getString(R.string.game_menu_server_cmd), options.toArray(new MenuOption[options.size()]));
    }

    private void showClassicMenu(GameInputDevice device) {
        List<MenuOption> options = new ArrayList<>();

        options.add(new MenuOption(getString(R.string.game_menu_disconnect), game::disconnect));

        options.add(new MenuOption(getString(R.string.game_menu_quit_session), game::quit));

        options.add(new MenuOption(getString(R.string.game_menu_upload_clipboard), true,
                () -> game.sendClipboard(true)));

        options.add(new MenuOption(getString(R.string.game_menu_fetch_clipboard), true,
                () -> game.getClipboard(0)));

        options.add(new MenuOption(getString(R.string.game_menu_server_cmd), true,
                () -> {
                    ArrayList<String> serverCmds = game.getServerCmds();
                    if (serverCmds.isEmpty()) {
                        Context themedContext = new ContextThemeWrapper(dialogScreenContext, R.style.OneUI_DialogTheme);
                        AlertDialog emptyCmdDialog = new AlertDialog.Builder(themedContext, R.style.OneUI_DialogTheme)
                                .setTitle(R.string.game_dialog_title_server_cmd_empty)
                                .setMessage(R.string.game_dialog_message_server_cmd_empty)
                                .create();
                        UiHelper.styleDialogSquircle(emptyCmdDialog);
                        emptyCmdDialog.show();
                        UiHelper.styleDialogSquircle(emptyCmdDialog);
                    } else {
                        hideMenu();
                        this.showServerCmd(serverCmds);
                    }
                }));

        options.add(new MenuOption(getString(R.string.game_menu_toggle_keyboard), true,
                game::toggleKeyboard));

        options.add(new MenuOption(getString(game.isZoomModeEnabled() ? R.string.game_menu_disable_zoom_mode : R.string.game_menu_enable_zoom_mode), true,
                game::toggleZoomMode));

        if (dialogScreenContext == game) {
            options.add(new MenuOption(getString(R.string.game_menu_rotate_screen), true,
                    game::rotateScreen));
        }

        options.add(new MenuOption(getString(R.string.game_menu_advanced), true,
                () -> showAdvancedMenu(device)));

        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(getString(R.string.quick_menu_title), options.toArray(new MenuOption[options.size()]));
    }

    @Override
    public void showMenu(GameInputDevice device) {
        if (game.isCondensedQuickMenuEnabled()) {
            showCondensedQuickMenu();
        } else {
            showClassicMenu(device);
        }
    }

    @Override
    public void hideMenu() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }
        currentDialog = null;
    }

    @Override
    public boolean isMenuOpen() {
        return currentDialog != null && currentDialog.isShowing();
    }
}
