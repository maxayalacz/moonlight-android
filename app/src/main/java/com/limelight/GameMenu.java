package com.limelight;

import android.app.Activity;
import android.app.Dialog;
import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.binding.input.GameInputDevice;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.profiles.ProfilesManager;
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

    private static final int NEON_ORANGE = 0xFFFF7A00;
    private static final int NEON_RED = 0xFFFF3B5C;
    private static final String PREF_DRAWER_ON_RIGHT = "quick_menu_drawer_on_right";
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

    // --- Revamped Condensed Icon-Only Quick Menu + Live-Blur Side Settings Drawer ---

    private interface BooleanConsumer {
        void accept(boolean value);
    }

    private interface IntConsumer {
        void accept(int value);
    }

    /**
     * Single-pass 30 FPS PixelCopy live stream blur shared across both the centered Quick Menu
     * card and the left-edge Side Settings Drawer so each card refracts the exact pixels beneath it.
     */
    private static final class StreamBlurController {
        private static final int CAPTURE_LONG_EDGE = 220;
        private static final long CAPTURE_INTERVAL_MS = 33L;

        private final Activity activity;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final List<FrostedStreamCardLayout> cards = new ArrayList<>();
        private final int[] surfaceLoc = new int[2];
        private final Bitmap[] buffers = new Bitmap[2];

        private int writeIndex = 0;
        private volatile Bitmap readyBitmap = null;
        private volatile int surfaceScreenLeft = 0;
        private volatile int surfaceScreenTop = 0;
        private volatile int surfaceWidth = 0;
        private volatile int surfaceHeight = 0;

        private boolean running = false;
        private boolean inFlight = false;
        private int blurRadius = 5;

        private final Runnable captureTick = new Runnable() {
            @Override
            public void run() {
                if (!running) {
                    return;
                }
                requestFrameCapture();
                handler.postDelayed(this, CAPTURE_INTERVAL_MS);
            }
        };

        StreamBlurController(Activity activity) {
            this.activity = activity;
            int blurPct = ProfilesManager.getInstance()
                    .getOverlayingSharedPreferences(activity)
                    .getInt(PreferenceConfiguration.BLUR_AMOUNT_PREF_STRING,
                            PreferenceConfiguration.DEFAULT_BLUR_AMOUNT);
            this.blurRadius = Math.max(0, Math.min(12, Math.round(5f * (blurPct / 100f))));
        }

        void registerCard(FrostedStreamCardLayout card) {
            if (!cards.contains(card)) {
                cards.add(card);
            }
        }

        void start() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || running || blurRadius <= 0) {
                return;
            }
            running = true;
            handler.removeCallbacks(captureTick);
            handler.post(captureTick);
        }

        void stop() {
            running = false;
            inFlight = false;
            handler.removeCallbacks(captureTick);
            readyBitmap = null;
        }

        Bitmap getReadyBitmap() {
            return readyBitmap;
        }

        int getSurfaceScreenLeft() {
            return surfaceScreenLeft;
        }

        int getSurfaceScreenTop() {
            return surfaceScreenTop;
        }

        int getSurfaceWidth() {
            return surfaceWidth;
        }

        int getSurfaceHeight() {
            return surfaceHeight;
        }

        private void requestFrameCapture() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || inFlight
                    || activity == null || activity.isFinishing()) {
                return;
            }
            SurfaceView surfaceView = UiHelper.findStreamSurfaceView(activity);
            if (surfaceView == null || surfaceView.getWidth() <= 0 || surfaceView.getHeight() <= 0
                    || !surfaceView.getHolder().getSurface().isValid()) {
                return;
            }

            final int sw = surfaceView.getWidth();
            final int sh = surfaceView.getHeight();
            surfaceView.getLocationOnScreen(surfaceLoc);
            final int sl = surfaceLoc[0];
            final int st = surfaceLoc[1];

            int bw;
            int bh;
            if (sw >= sh) {
                bw = CAPTURE_LONG_EDGE;
                bh = Math.max(16, Math.round(CAPTURE_LONG_EDGE * (sh / (float) sw)));
            } else {
                bh = CAPTURE_LONG_EDGE;
                bw = Math.max(16, Math.round(CAPTURE_LONG_EDGE * (sw / (float) sh)));
            }

            if (buffers[0] == null || buffers[0].getWidth() != bw || buffers[0].getHeight() != bh) {
                buffers[0] = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                buffers[1] = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                readyBitmap = null;
            }

            final int idx = writeIndex;
            final Bitmap target = buffers[idx];
            inFlight = true;
            try {
                PixelCopy.request(surfaceView, target, copyResult -> {
                    inFlight = false;
                    if (!running || copyResult != PixelCopy.SUCCESS) {
                        return;
                    }
                    if (blurRadius > 0) {
                        UiHelper.fastBoxBlur(target, blurRadius);
                    }
                    surfaceScreenLeft = sl;
                    surfaceScreenTop = st;
                    surfaceWidth = sw;
                    surfaceHeight = sh;
                    readyBitmap = target;
                    writeIndex = 1 - idx;

                    for (int i = 0; i < cards.size(); i++) {
                        FrostedStreamCardLayout card = cards.get(i);
                        if (card.getVisibility() == View.VISIBLE) {
                            card.invalidate();
                        }
                    }
                }, handler);
            } catch (IllegalArgumentException | IllegalStateException e) {
                inFlight = false;
            }
        }
    }

    /**
     * Rounded One UI frosted glass card that clips a live screen-aligned blurred stream crop
     * strictly inside its squircle outline, layered with a clean uniform translucent dark-glass
     * tint and optional global outline rim stroke.
     */
    private static final class FrostedStreamCardLayout extends FrameLayout {
        private final StreamBlurController blurController;
        private final float cornerRadiusPx;
        private final int tintColorWithBlur;
        private final int tintColorFallback;
        private final int accentDeepTint;

        private final Path clipPath = new Path();
        private final RectF cardBounds = new RectF();
        private final Rect srcRect = new Rect();
        private final RectF dstRect = new RectF();
        private final int[] viewLoc = new int[2];

        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        FrostedStreamCardLayout(Context context, StreamBlurController blurController,
                                float cornerRadiusPx, AppThemePalette palette) {
            super(context);
            this.blurController = blurController;
            this.cornerRadiusPx = cornerRadiusPx;
            int darkBase = palette.isLight ? 0xFF141B29 : palette.background;
            this.tintColorWithBlur = AppThemePalette.withAlphaInt(darkBase, 0xAA);
            this.tintColorFallback = AppThemePalette.withAlphaInt(darkBase, 0xDE);
            this.accentDeepTint = AppThemePalette.withAlpha(palette.isLight ? palette.accent : palette.surface, palette.isLight ? 0.14f : 0.24f);

            setWillNotDraw(false);
            setClickable(true);

            rimPaint.setStyle(Paint.Style.STROKE);
            rimPaint.setColor(0x3DFFFFFF);
            refreshRimOutline();

            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
                }
            });
            setClipToOutline(true);
            blurController.registerCard(this);
        }

        void refreshRimOutline() {
            rimPaint.setStrokeWidth(UiHelper.getCardOutlineWidthPx(getContext(), 1.15f));
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            cardBounds.set(0f, 0f, w, h);
            clipPath.reset();
            clipPath.addRoundRect(cardBounds, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }

            boolean drewBlur = false;
            Bitmap bmp = blurController.getReadyBitmap();
            int sw = blurController.getSurfaceWidth();
            int sh = blurController.getSurfaceHeight();

            canvas.save();
            canvas.clipPath(clipPath);

            if (bmp != null && !bmp.isRecycled() && sw > 0 && sh > 0) {
                getLocationOnScreen(viewLoc);
                int originL = blurController.getSurfaceScreenLeft();
                int originT = blurController.getSurfaceScreenTop();
                int bw = bmp.getWidth();
                int bh = bmp.getHeight();
                float scaleX = bw / (float) sw;
                float scaleY = bh / (float) sh;

                int srcL = Math.max(0, Math.min(bw - 1, Math.round((viewLoc[0] - originL) * scaleX)));
                int srcT = Math.max(0, Math.min(bh - 1, Math.round((viewLoc[1] - originT) * scaleY)));
                int srcR = Math.max(srcL + 1, Math.min(bw, Math.round((viewLoc[0] + w - originL) * scaleX)));
                int srcB = Math.max(srcT + 1, Math.min(bh, Math.round((viewLoc[1] + h - originT) * scaleY)));

                srcRect.set(srcL, srcT, srcR, srcB);
                dstRect.set(0f, 0f, w, h);
                canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint);
                drewBlur = true;
            }

            fillPaint.setColor(drewBlur ? tintColorWithBlur : tintColorFallback);
            canvas.drawRect(cardBounds, fillPaint);

            fillPaint.setColor(accentDeepTint);
            canvas.drawRect(cardBounds, fillPaint);
            canvas.restore();

            float strokeW = rimPaint.getStrokeWidth();
            if (strokeW > 0.1f) {
                float inset = strokeW * 0.5f;
                dstRect.set(inset, inset, w - inset, h - inset);
                canvas.drawRoundRect(dstRect, Math.max(0f, cornerRadiusPx - inset),
                        Math.max(0f, cornerRadiusPx - inset), rimPaint);
            }
        }
    }

    private void showCondensedQuickMenu() {
        hideMenu();

        final Context themedContext = new ContextThemeWrapper(dialogScreenContext, R.style.OneUI_DialogTheme);
        final Dialog dialog = new Dialog(themedContext, R.style.OneUI_DialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        final AppThemePalette palette = AppThemePalette.getCurrent(game);
        final StreamBlurController blurController = new StreamBlurController(game);
        final boolean[] drawerOpen = { false };
        final Runnable[] closeDrawerRef = new Runnable[1];

        dialog.setOnCancelListener(d -> hideMenu());
        dialog.setOnDismissListener(d -> {
            blurController.stop();
            if (currentDialog == d) {
                currentDialog = null;
            }
        });
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && (keyCode == KeyEvent.KEYCODE_BACK
                    || keyCode == KeyEvent.KEYCODE_BUTTON_B
                    || keyCode == KeyEvent.KEYCODE_ESCAPE)) {
                if (drawerOpen[0] && closeDrawerRef[0] != null) {
                    closeDrawerRef[0].run();
                    return true;
                }
                hideMenu();
                return true;
            }
            return false;
        });

        // Fullscreen transparent overlay container catching outside taps
        final FrameLayout overlayRoot = new FrameLayout(themedContext);
        overlayRoot.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlayRoot.setClipChildren(false);
        overlayRoot.setClipToPadding(false);

        final View scrimView = new View(themedContext);
        scrimView.setBackgroundColor(0x28000000);
        scrimView.setAlpha(0f);
        scrimView.setOnClickListener(v -> hideMenu());
        overlayRoot.addView(scrimView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // --- Centered Quick Menu Card (4x2 Squircle Grid + Centered Settings Pill Below) ---
        final int tileSize = dp(58f);
        final int tileGap = dp(12f);
        final int cardPad = dp(18f);
        final float cardRadius = dp(28f);

        final FrostedStreamCardLayout quickMenuCard = new FrostedStreamCardLayout(
                themedContext, blurController, cardRadius, palette);

        LinearLayout rootColumn = new LinearLayout(themedContext);
        rootColumn.setOrientation(LinearLayout.VERTICAL);
        rootColumn.setGravity(Gravity.CENTER_HORIZONTAL);
        rootColumn.setPadding(cardPad, cardPad, cardPad, dp(14f));
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

        // 1. Disconnect (Neon Orange icon & outline)
        ImageButton btnDisconnect = createSquircleIconButton(
                themedContext, R.drawable.ic_qm_disconnect, getString(R.string.game_menu_disconnect), tileSize);
        applyNeonTileStyle(btnDisconnect, NEON_ORANGE, false);
        attachFocusRing(btnDisconnect, () -> applyNeonTileStyle(btnDisconnect, NEON_ORANGE, btnDisconnect.isFocused()));
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

        // 9. Centered Settings Pill below the 4x2 grid
        final int pillWidth = dp(96f);
        final int pillHeight = dp(36f);
        final FrameLayout btnSettingsPill = new FrameLayout(themedContext);
        btnSettingsPill.setFocusable(true);
        btnSettingsPill.setClickable(true);
        setTooltipAndDesc(btnSettingsPill, getString(R.string.quick_settings_title));

        final ImageView settingsIconView = new ImageView(themedContext);
        settingsIconView.setImageResource(R.drawable.ic_settings);
        settingsIconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int gearSize = dp(20f);
        FrameLayout.LayoutParams gearLp = new FrameLayout.LayoutParams(gearSize, gearSize);
        gearLp.gravity = Gravity.CENTER;
        btnSettingsPill.addView(settingsIconView, gearLp);

        final Runnable refreshSettingsPill = () -> applySettingsPillStyle(
                btnSettingsPill, settingsIconView, drawerOpen[0], palette.highlight, btnSettingsPill.isFocused());
        refreshSettingsPill.run();
        attachFocusRing(btnSettingsPill, refreshSettingsPill);

        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(pillWidth, pillHeight);
        pillLp.topMargin = dp(12f);
        pillLp.gravity = Gravity.CENTER_HORIZONTAL;
        rootColumn.addView(btnSettingsPill, pillLp);

        quickMenuCard.addView(rootColumn, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams quickCardLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        quickCardLp.gravity = Gravity.CENTER;
        overlayRoot.addView(quickMenuCard, quickCardLp);

        // --- Switchable Left/Right Scrollable Live-Blur Side Settings Drawer ---
        DisplayMetrics dm = dialogScreenContext.getResources().getDisplayMetrics();
        final int drawerMargin = dp(12f);
        final int drawerWidth = Math.max(dp(256f), Math.min(dp(304f), Math.round(dm.widthPixels * 0.42f)));

        final SharedPreferences overlayPrefs = ProfilesManager.getInstance()
                .getOverlayingSharedPreferences(game);
        final boolean[] drawerOnRight = { overlayPrefs.getBoolean(PREF_DRAWER_ON_RIGHT, false) };

        final FrostedStreamCardLayout sideDrawerCard = new FrostedStreamCardLayout(
                themedContext, blurController, cardRadius, palette);
        sideDrawerCard.setVisibility(View.GONE);
        sideDrawerCard.setAlpha(0f);
        sideDrawerCard.setTranslationX((drawerOnRight[0] ? 1f : -1f) * (drawerWidth + dp(24f)));

        final FrameLayout.LayoutParams drawerLp = new FrameLayout.LayoutParams(
                drawerWidth, ViewGroup.LayoutParams.MATCH_PARENT);
        drawerLp.gravity = (drawerOnRight[0] ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL;
        drawerLp.setMargins(drawerMargin, drawerMargin, drawerMargin, drawerMargin);

        final Runnable updateQuickMenuShift = () -> {
            float targetShiftX = 0f;
            if (drawerOpen[0]) {
                int screenW = overlayRoot.getWidth() > 0 ? overlayRoot.getWidth() : dm.widthPixels;
                int quickW = quickMenuCard.getWidth() > 0 ? quickMenuCard.getWidth() : dp(304f);
                float defaultLeft = (screenW - quickW) * 0.5f;
                float minClearLeft = drawerMargin + drawerWidth + dp(12f);
                if (defaultLeft < minClearLeft) {
                    float maxShift = Math.max(0f, screenW - dp(10f) - (defaultLeft + quickW));
                    float shiftMag = Math.min(minClearLeft - defaultLeft, maxShift);
                    targetShiftX = drawerOnRight[0] ? -shiftMag : shiftMag;
                }
            }
            quickMenuCard.animate().cancel();
            quickMenuCard.animate()
                    .translationX(targetShiftX)
                    .setDuration(240L)
                    .setInterpolator(SPRING_EASE)
                    .setUpdateListener(a -> quickMenuCard.invalidate())
                    .start();
        };

        Runnable setDrawerVisibility = new Runnable() {
            @Override
            public void run() {
                boolean open = drawerOpen[0];
                float hiddenOffset = (drawerOnRight[0] ? 1f : -1f) * (drawerWidth + dp(24f));
                refreshSettingsPill.run();
                settingsIconView.animate().cancel();
                settingsIconView.animate()
                        .rotation(open ? 60f : 0f)
                        .setDuration(240L)
                        .setInterpolator(SPRING_EASE)
                        .start();

                if (open) {
                    sideDrawerCard.setVisibility(View.VISIBLE);
                    sideDrawerCard.animate().cancel();
                    sideDrawerCard.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(240L)
                            .setInterpolator(SPRING_EASE)
                            .setUpdateListener(a -> sideDrawerCard.invalidate())
                            .start();
                } else {
                    sideDrawerCard.animate().cancel();
                    sideDrawerCard.animate()
                            .translationX(hiddenOffset)
                            .alpha(0f)
                            .setDuration(190L)
                            .setInterpolator(SPRING_EASE)
                            .setUpdateListener(a -> sideDrawerCard.invalidate())
                            .withEndAction(() -> {
                                if (!drawerOpen[0]) {
                                    sideDrawerCard.setVisibility(View.GONE);
                                }
                            })
                            .start();
                }

                updateQuickMenuShift.run();
            }
        };

        Runnable swapDrawerSide = () -> {
            drawerOnRight[0] = !drawerOnRight[0];
            overlayPrefs.edit().putBoolean(PREF_DRAWER_ON_RIGHT, drawerOnRight[0]).apply();

            sideDrawerCard.animate().cancel();
            drawerLp.gravity = (drawerOnRight[0] ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL;
            sideDrawerCard.setLayoutParams(drawerLp);

            float enterOffset = (drawerOnRight[0] ? 1f : -1f) * dp(44f);
            sideDrawerCard.setTranslationX(enterOffset);
            sideDrawerCard.setAlpha(0.35f);
            sideDrawerCard.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(240L)
                    .setInterpolator(SPRING_EASE)
                    .setUpdateListener(a -> sideDrawerCard.invalidate())
                    .start();

            updateQuickMenuShift.run();
        };

        Runnable refreshCardOutlines = () -> {
            quickMenuCard.refreshRimOutline();
            sideDrawerCard.refreshRimOutline();
        };

        closeDrawerRef[0] = () -> {
            if (drawerOpen[0]) {
                drawerOpen[0] = false;
                setDrawerVisibility.run();
            }
        };

        View drawerContent = buildSideSettingsDrawerContent(
                themedContext, palette, closeDrawerRef[0], swapDrawerSide,
                refreshMouseTile, refreshCardOutlines);
        sideDrawerCard.addView(drawerContent, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        overlayRoot.addView(sideDrawerCard, drawerLp);

        btnSettingsPill.setOnClickListener(v -> {
            animateTileTap(v);
            drawerOpen[0] = !drawerOpen[0];
            if (drawerOpen[0]) {
                // Ensure starting offscreen translation matches the current side
                sideDrawerCard.setTranslationX((drawerOnRight[0] ? 1f : -1f) * (drawerWidth + dp(24f)));
            }
            setDrawerVisibility.run();
        });

        // Re-evaluate non-overlapping shift if device rotates while drawer is open
        overlayRoot.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if ((r - l) != (or - ol) && drawerOpen[0]) {
                overlayRoot.post(updateQuickMenuShift);
            }
        });

        dialog.setContentView(overlayRoot);
        currentDialog = dialog;

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.FILL);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0f;
            lp.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            window.setAttributes(lp);
            View decor = window.getDecorView();
            decor.setPadding(0, 0, 0, 0);
            decor.setBackground(new ColorDrawable(Color.TRANSPARENT));
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }

        // Smooth entrance animation on card & scrim
        quickMenuCard.setAlpha(0f);
        quickMenuCard.setScaleX(0.93f);
        quickMenuCard.setScaleY(0.93f);

        dialog.show();
        blurController.start();

        scrimView.animate()
                .alpha(1f)
                .setDuration(180L)
                .setInterpolator(SPRING_EASE)
                .start();
        quickMenuCard.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(210L)
                .setInterpolator(SPRING_EASE)
                .setUpdateListener(a -> quickMenuCard.invalidate())
                .start();
    }

    private static final class AccordionSection {
        final LinearLayout content;
        final TextView chevron;
        boolean expanded = false;

        AccordionSection(LinearLayout content, TextView chevron) {
            this.content = content;
            this.chevron = chevron;
        }
    }

    private View buildSideSettingsDrawerContent(Context ctx, AppThemePalette palette,
                                                Runnable onCloseDrawer, Runnable onSwapSide,
                                                Runnable onMouseModeMayChange,
                                                Runnable onCardOutlineChanged) {
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);

        // Fixed Header Bar with Swap-Side (⇄) and Close (✕) buttons
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16f), dp(14f), dp(12f), dp(12f));

        TextView titleView = new TextView(ctx);
        titleView.setText(R.string.quick_settings_title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(titleView, titleLp);

        TextView swapBtn = createDrawerHeaderIconButton(ctx, "\u21C4", getString(R.string.quick_settings_swap_side));
        swapBtn.setOnClickListener(v -> {
            animateTileTap(v);
            onSwapSide.run();
        });
        LinearLayout.LayoutParams swapLp = new LinearLayout.LayoutParams(dp(28f), dp(28f));
        swapLp.setMarginEnd(dp(6f));
        header.addView(swapBtn, swapLp);

        TextView closeBtn = createDrawerHeaderIconButton(ctx, "\u2715", getString(R.string.cancel));
        closeBtn.setOnClickListener(v -> {
            animateTileTap(v);
            onCloseDrawer.run();
        });
        header.addView(closeBtn, new LinearLayout.LayoutParams(dp(28f), dp(28f)));

        container.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(ctx);
        divider.setBackgroundColor(0x22FFFFFF);
        container.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1f))));

        // Scrollable Accordion Settings Body (all categories start collapsed)
        ScrollView scrollView = new ScrollView(ctx);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);

        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12f), dp(10f), dp(12f), dp(18f));

        final List<AccordionSection> sections = new ArrayList<>();

        // 1. On-Screen Controller Category (hidden on external display)
        if (!game.isOnExternalDisplay()) {
            LinearLayout oscContent = addAccordionCategory(
                    body, ctx, getString(R.string.quick_settings_section_osc), palette, sections);

            addDrawerSliderRow(oscContent, ctx, getString(R.string.dialog_title_osc_opacity),
                    0, 100, 1, game.getOscOpacity(), "%", palette,
                    game::setOscOpacityLive);

            addDrawerSliderRow(oscContent, ctx, getString(R.string.title_osc_outline_thickness),
                    0, 250, 5, game.getOscOutlineThickness(), "%", palette,
                    game::setOscOutlineThicknessLive);

            addDrawerToggleRow(oscContent, ctx, getString(R.string.quick_settings_osc_motion),
                    game.isOscMotionSensorsEnabled(), palette,
                    game::setOscMotionSensorsLive);

            addDrawerToggleRow(oscContent, ctx, getString(R.string.title_checkbox_vibrate_osc),
                    game.isOscVibrateEnabled(), palette,
                    game::setOscVibrateLive);

            final View[] freeStickOpacityRowRef = new View[1];
            addDrawerToggleRow(oscContent, ctx, getString(R.string.title_free_analog_stick),
                    game.isFreeAnalogStickEnabled(), palette, enabled -> {
                        game.setFreeAnalogStickLive(enabled);
                        if (freeStickOpacityRowRef[0] != null) {
                            setRowEnabledState(freeStickOpacityRowRef[0], enabled);
                        }
                    });

            freeStickOpacityRowRef[0] = addDrawerSliderRow(oscContent, ctx,
                    getString(R.string.title_free_analog_stick_opacity),
                    0, 100, 5, game.getFreeAnalogStickOpacity(), "%", palette,
                    game::setFreeAnalogStickOpacityLive);
            setRowEnabledState(freeStickOpacityRowRef[0], game.isFreeAnalogStickEnabled());

            addDrawerActionButton(oscContent, ctx, getString(R.string.quick_settings_edit_osc_inline), palette, () -> {
                hideMenu();
                game.startInlineOscLayoutEdit();
            });
        }

        // 2. On-Screen Full Keyboard Category
        LinearLayout kbdContent = addAccordionCategory(
                body, ctx, getString(R.string.category_onscreen_keyboard), palette, sections);

        addDrawerSliderRow(kbdContent, ctx, getString(R.string.title_keyboard_opacity),
                0, 100, 1, game.getFullKeyboardOpacity(), "%", palette,
                game::setFullKeyboardOpacityLive);

        addDrawerToggleRow(kbdContent, ctx, getString(R.string.title_checkbox_enable_sticky_modifier_key_virtual_keyboard),
                game.isFullKeyboardStickyModifierEnabled(), palette,
                game::setFullKeyboardStickyModifierLive);

        final View[] kbdHeightRowRef = new View[1];
        final View[] kbdWidthRowRef = new View[1];
        addDrawerToggleRow(kbdContent, ctx, getString(R.string.title_onscreen_keyboard_autofit),
                game.isFullKeyboardCustomSizeEnabled(), palette, enabled -> {
                    game.setFullKeyboardCustomSizeLive(enabled);
                    if (kbdHeightRowRef[0] != null) {
                        setRowEnabledState(kbdHeightRowRef[0], enabled);
                    }
                    if (kbdWidthRowRef[0] != null) {
                        setRowEnabledState(kbdWidthRowRef[0], enabled);
                    }
                });

        kbdHeightRowRef[0] = addDrawerSliderRow(kbdContent, ctx, getString(R.string.title_onscreen_keyboard_height),
                150, 400, 10, game.getFullKeyboardHeightDp(), "dp", palette,
                game::setFullKeyboardHeightDpLive);
        kbdWidthRowRef[0] = addDrawerSliderRow(kbdContent, ctx, getString(R.string.title_onscreen_keyboard_width),
                150, 1000, 10, game.getFullKeyboardWidthDp(), "dp", palette,
                game::setFullKeyboardWidthDpLive);
        setRowEnabledState(kbdHeightRowRef[0], game.isFullKeyboardCustomSizeEnabled());
        setRowEnabledState(kbdWidthRowRef[0], game.isFullKeyboardCustomSizeEnabled());

        addDrawerKeyboardAlignRow(kbdContent, ctx, palette);

        // 3. Touch & Trackpad Category
        LinearLayout touchContent = addAccordionCategory(
                body, ctx, getString(R.string.quick_settings_section_touch), palette, sections);

        addDrawerSliderRow(touchContent, ctx, getString(R.string.title_touchpad_sensitivity_x),
                10, 300, 10, game.getTrackpadSensitivityX(), "%", palette,
                game::setTrackpadSensitivityXLive);

        addDrawerSliderRow(touchContent, ctx, getString(R.string.title_touchpad_sensitivity_y),
                10, 300, 10, game.getTrackpadSensitivityY(), "%", palette,
                game::setTrackpadSensitivityYLive);

        addDrawerToggleRow(touchContent, ctx, getString(R.string.title_trackpad_swap_axis),
                game.isTrackpadSwapAxisEnabled(), palette,
                game::setTrackpadSwapAxisLive);

        addDrawerToggleRow(touchContent, ctx, getString(R.string.title_trackpad_drag_drop_vibration),
                game.isTrackpadDragDropVibrationEnabled(), palette,
                game::setTrackpadDragDropVibrationLive);

        final View[] touchSensXRowRef = new View[1];
        final View[] touchSensYRowRef = new View[1];
        addDrawerToggleRow(touchContent, ctx, getString(R.string.title_touch_sensitivity),
                game.isTouchSensitivityEnabled(), palette, enabled -> {
                    game.setTouchSensitivityEnabledLive(enabled);
                    if (touchSensXRowRef[0] != null) {
                        setRowEnabledState(touchSensXRowRef[0], enabled);
                    }
                    if (touchSensYRowRef[0] != null) {
                        setRowEnabledState(touchSensYRowRef[0], enabled);
                    }
                });

        touchSensXRowRef[0] = addDrawerSliderRow(touchContent, ctx, getString(R.string.title_touch_sensitivity_x),
                10, 300, 10, game.getTouchSensitivityX(), "%", palette,
                game::setTouchSensitivityXLive);
        touchSensYRowRef[0] = addDrawerSliderRow(touchContent, ctx, getString(R.string.title_touch_sensitivity_y),
                10, 300, 10, game.getTouchSensitivityY(), "%", palette,
                game::setTouchSensitivityYLive);
        setRowEnabledState(touchSensXRowRef[0], game.isTouchSensitivityEnabled());
        setRowEnabledState(touchSensYRowRef[0], game.isTouchSensitivityEnabled());

        addDrawerToggleRow(touchContent, ctx, getString(R.string.quick_settings_local_cursor),
                game.isLocalMouseCursorVisible(), palette, enabled -> {
                    if (game.isLocalMouseCursorVisible() != enabled) {
                        game.toggleLocalMouseCursor();
                    }
                });

        addDrawerToggleRow(touchContent, ctx, getString(R.string.quick_settings_special_keys),
                game.isSpecialKeysVisible(), palette, enabled -> {
                    if (game.isSpecialKeysVisible() != enabled) {
                        game.toggleKeyboardController();
                    }
                });

        // 4. Display & View Category
        LinearLayout displayContent = addAccordionCategory(
                body, ctx, getString(R.string.quick_settings_section_display), palette, sections);

        addDrawerScaleModeRow(displayContent, ctx, palette);

        addDrawerToggleRow(displayContent, ctx, getString(R.string.quick_settings_pan_zoom),
                game.isZoomModeEnabled(), palette, enabled -> {
                    if (game.isZoomModeEnabled() != enabled) {
                        game.toggleZoomMode();
                        if (onMouseModeMayChange != null) {
                            onMouseModeMayChange.run();
                        }
                    }
                });

        addDrawerToggleRow(displayContent, ctx, getString(R.string.title_top_center_display),
                game.isAlignDisplayTopCenter(), palette,
                game::setAlignDisplayTopCenterLive);

        if (dialogScreenContext == game) {
            addDrawerActionButton(displayContent, ctx, getString(R.string.quick_settings_rotate_screen),
                    palette, game::rotateScreen);
        }

        // 5. Overlay & UI Category
        LinearLayout overlayContent = addAccordionCategory(
                body, ctx, getString(R.string.quick_settings_section_overlay), palette, sections);

        addDrawerSliderRow(overlayContent, ctx, getString(R.string.title_card_outline_thickness),
                0, 250, 5, game.getCardOutlineThickness(), "%", palette, val -> {
                    game.setCardOutlineThicknessLive(val);
                    if (onCardOutlineChanged != null) {
                        onCardOutlineChanged.run();
                    }
                });

        addDrawerToggleRow(overlayContent, ctx, getString(R.string.quick_settings_perf_lite),
                game.isPerfOverlayLite(), palette,
                game::setPerfOverlayLiteLive);

        addDrawerToggleRow(overlayContent, ctx, getString(R.string.quick_settings_perf_bottom),
                game.isPerfOverlayBottom(), palette,
                game::setPerfOverlayBottomLive);

        addDrawerToggleRow(overlayContent, ctx, getString(R.string.title_floating_button),
                game.isFloatingMenuButtonVisible(), palette,
                game::setFloatingMenuButtonLive);

        addDrawerToggleRow(overlayContent, ctx, getString(R.string.title_post_session_metrics),
                game.isPostSessionMetricsEnabled(), palette,
                game::setPostSessionMetricsLive);

        addDrawerToggleRow(overlayContent, ctx, getString(R.string.title_checkbox_disable_warnings),
                game.isDisableWarningsEnabled(), palette,
                game::setDisableWarningsLive);

        scrollView.addView(body, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return container;
    }

    private TextView createDrawerHeaderIconButton(Context ctx, String symbol, String contentDesc) {
        TextView btn = new TextView(ctx);
        btn.setText(symbol);
        btn.setTextColor(0xE0FFFFFF);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        btn.setGravity(Gravity.CENTER);
        btn.setFocusable(true);
        btn.setClickable(true);
        setTooltipAndDesc(btn, contentDesc);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x20FFFFFF);
        bg.setStroke(Math.max(1, dp(1f)), 0x3DFFFFFF);
        btn.setBackground(bg);
        return btn;
    }

    private LinearLayout addAccordionCategory(LinearLayout parent, Context ctx, String title,
                                              AppThemePalette palette,
                                              List<AccordionSection> allSections) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setCornerRadius(dp(16f));
        cardBg.setColor(0x14FFFFFF);
        cardBg.setStroke(Math.max(1, dp(1f)), 0x24FFFFFF);
        card.setBackground(cardBg);

        LinearLayout headerRow = new LinearLayout(ctx);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(12f), dp(11f), dp(12f), dp(11f));
        headerRow.setFocusable(true);
        headerRow.setClickable(true);

        TextView titleView = new TextView(ctx);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        headerRow.addView(titleView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = new TextView(ctx);
        chevron.setText("\u25BE");
        chevron.setTextColor(palette.getNeonAccent());
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        chevron.setGravity(Gravity.CENTER);
        headerRow.addView(chevron, new LinearLayout.LayoutParams(dp(20f), dp(20f)));

        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(10f), dp(2f), dp(10f), dp(10f));
        content.setVisibility(View.GONE);

        final AccordionSection section = new AccordionSection(content, chevron);
        allSections.add(section);

        headerRow.setOnClickListener(v -> {
            boolean willExpand = !section.expanded;
            // Accordion behavior: collapse any other open category first
            for (int i = 0; i < allSections.size(); i++) {
                AccordionSection other = allSections.get(i);
                if (other != section && other.expanded) {
                    other.expanded = false;
                    other.content.setVisibility(View.GONE);
                    other.chevron.animate().cancel();
                    other.chevron.animate()
                            .rotation(0f)
                            .setDuration(180L)
                            .setInterpolator(SPRING_EASE)
                            .start();
                }
            }

            section.expanded = willExpand;
            chevron.animate().cancel();
            chevron.animate()
                    .rotation(willExpand ? 180f : 0f)
                    .setDuration(200L)
                    .setInterpolator(SPRING_EASE)
                    .start();
            if (willExpand) {
                content.setVisibility(View.VISIBLE);
                content.setAlpha(0f);
                content.animate().cancel();
                content.animate()
                        .alpha(1f)
                        .setDuration(170L)
                        .setInterpolator(SPRING_EASE)
                        .start();
            } else {
                content.animate().cancel();
                content.setVisibility(View.GONE);
            }
        });

        card.addView(headerRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (allSections.size() > 1) {
            cardLp.topMargin = dp(8f);
        }
        parent.addView(card, cardLp);
        return content;
    }

    private void addDrawerKeyboardAlignRow(LinearLayout parent, Context ctx, AppThemePalette palette) {
        LinearLayout block = new LinearLayout(ctx);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(4f), dp(5f), dp(2f), dp(7f));

        TextView label = new TextView(ctx);
        label.setText(R.string.title_onscreen_keyboard_align_mode);
        label.setTextColor(0xDEFFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        block.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout capsule = new LinearLayout(ctx);
        capsule.setOrientation(LinearLayout.HORIZONTAL);
        capsule.setPadding(dp(3f), dp(3f), dp(3f), dp(3f));
        GradientDrawable capsuleBg = new GradientDrawable();
        capsuleBg.setShape(GradientDrawable.RECTANGLE);
        capsuleBg.setCornerRadius(dp(13f));
        capsuleBg.setColor(0x18FFFFFF);
        capsuleBg.setStroke(Math.max(1, dp(1f)), 0x33FFFFFF);
        capsule.setBackground(capsuleBg);

        final String[] modes = { "left", "center", "right" };
        final int[] labels = { R.string.align_left, R.string.align_center, R.string.align_right };
        final TextView[] tabs = new TextView[modes.length];
        final String[] selected = { game.getFullKeyboardAlignMode() };

        Runnable refreshTabs = () -> {
            for (int i = 0; i < modes.length; i++) {
                boolean active = modes[i].equals(selected[0]);
                TextView tab = tabs[i];
                if (active) {
                    GradientDrawable tabBg = new GradientDrawable();
                    tabBg.setShape(GradientDrawable.RECTANGLE);
                    tabBg.setCornerRadius(dp(10f));
                    tabBg.setColor(AppThemePalette.withAlpha(palette.highlight, 0.34f));
                    tabBg.setStroke(Math.max(1, dp(1.1f)), 0xC8FFFFFF);
                    tab.setBackground(tabBg);
                    tab.setTextColor(Color.WHITE);
                    tab.setTypeface(Typeface.DEFAULT_BOLD);
                } else {
                    tab.setBackground(null);
                    tab.setTextColor(0xA8FFFFFF);
                    tab.setTypeface(Typeface.DEFAULT);
                }
            }
        };

        for (int i = 0; i < modes.length; i++) {
            final String mode = modes[i];
            TextView tab = new TextView(ctx);
            tab.setText(labels[i]);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(4f), dp(6f), dp(4f), dp(6f));
            tab.setFocusable(true);
            tab.setClickable(true);
            tab.setOnClickListener(v -> {
                selected[0] = mode;
                refreshTabs.run();
                game.setFullKeyboardAlignModeLive(mode);
            });
            tabs[i] = tab;
            capsule.addView(tab, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        refreshTabs.run();

        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        capLp.topMargin = dp(6f);
        block.addView(capsule, capLp);

        parent.addView(block, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private View addDrawerToggleRow(LinearLayout parent, Context ctx, String label,
                                    boolean initialState, AppThemePalette palette,
                                    BooleanConsumer onToggle) {
        final boolean[] checked = { initialState };

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4f), dp(7f), dp(2f), dp(7f));
        row.setFocusable(true);
        row.setClickable(true);

        TextView text = new TextView(ctx);
        text.setText(label);
        text.setTextColor(0xEAFFFFFF);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        text.setMaxLines(2);
        text.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginEnd(dp(10f));
        row.addView(text, textLp);

        // Sleek One UI pill toggle switch
        final int trackW = dp(40f);
        final int trackH = dp(22f);
        final int thumbSize = dp(16f);
        final int thumbMargin = dp(3f);
        final float travelPx = trackW - thumbSize - thumbMargin * 2f;

        FrameLayout switchTrack = new FrameLayout(ctx);
        View thumb = new View(ctx);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setShape(GradientDrawable.OVAL);
        thumbBg.setColor(Color.WHITE);
        thumb.setBackground(thumbBg);
        FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(thumbSize, thumbSize);
        thumbLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        thumbLp.setMarginStart(thumbMargin);
        switchTrack.addView(thumb, thumbLp);

        Runnable applyVisual = () -> {
            GradientDrawable trackBg = new GradientDrawable();
            trackBg.setShape(GradientDrawable.RECTANGLE);
            trackBg.setCornerRadius(trackH * 0.5f);
            if (checked[0]) {
                trackBg.setColor(AppThemePalette.withAlpha(palette.highlight, 0.85f));
                trackBg.setStroke(Math.max(1, dp(1f)), 0x99FFFFFF);
            } else {
                trackBg.setColor(0x28FFFFFF);
                trackBg.setStroke(Math.max(1, dp(1f)), 0x44FFFFFF);
            }
            switchTrack.setBackground(trackBg);
            thumb.setAlpha(checked[0] ? 1f : 0.78f);
        };
        applyVisual.run();
        thumb.setTranslationX(checked[0] ? travelPx : 0f);

        row.addView(switchTrack, new LinearLayout.LayoutParams(trackW, trackH));

        row.setOnClickListener(v -> {
            checked[0] = !checked[0];
            applyVisual.run();
            thumb.animate().cancel();
            thumb.animate()
                    .translationX(checked[0] ? travelPx : 0f)
                    .setDuration(170L)
                    .setInterpolator(SPRING_EASE)
                    .start();
            onToggle.accept(checked[0]);
        });

        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private View addDrawerSliderRow(LinearLayout parent, Context ctx, String label,
                                    int minVal, int maxVal, int step, int initialVal,
                                    String suffix, AppThemePalette palette,
                                    IntConsumer onChanged) {
        final int clampedInit = Math.max(minVal, Math.min(maxVal, initialVal));
        final int safeStep = Math.max(1, step);
        final int stepsCount = Math.max(1, (maxVal - minVal) / safeStep);

        LinearLayout block = new LinearLayout(ctx);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(4f), dp(5f), dp(2f), dp(6f));

        LinearLayout topLine = new LinearLayout(ctx);
        topLine.setOrientation(LinearLayout.HORIZONTAL);
        topLine.setGravity(Gravity.CENTER_VERTICAL);

        TextView labelView = new TextView(ctx);
        labelView.setText(label);
        labelView.setTextColor(0xDEFFFFFF);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        labelView.setMaxLines(1);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        topLine.addView(labelView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(ctx);
        valueView.setText(clampedInit + suffix);
        valueView.setTextColor(palette.getNeonAccent());
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valLp.setMarginStart(dp(8f));
        topLine.addView(valueView, valLp);

        block.addView(topLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        SeekBar seekBar = new SeekBar(ctx);
        seekBar.setMax(stepsCount);
        seekBar.setProgress(Math.max(0, Math.min(stepsCount, Math.round((clampedInit - minVal) / (float) safeStep))));
        seekBar.setProgressTintList(ColorStateList.valueOf(palette.highlight));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(0x3DFFFFFF));
        seekBar.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        seekBar.setPadding(dp(8f), dp(4f), dp(8f), dp(2f));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int val = Math.min(maxVal, minVal + progress * safeStep);
                valueView.setText(val + suffix);
                if (fromUser) {
                    onChanged.accept(val);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });

        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbLp.topMargin = dp(2f);
        block.addView(seekBar, sbLp);

        parent.addView(block, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return block;
    }

    private void addDrawerScaleModeRow(LinearLayout parent, Context ctx, AppThemePalette palette) {
        LinearLayout block = new LinearLayout(ctx);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(4f), dp(5f), dp(2f), dp(7f));

        TextView label = new TextView(ctx);
        label.setText(R.string.title_video_scale_mode);
        label.setTextColor(0xDEFFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        block.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout capsule = new LinearLayout(ctx);
        capsule.setOrientation(LinearLayout.HORIZONTAL);
        capsule.setPadding(dp(3f), dp(3f), dp(3f), dp(3f));
        GradientDrawable capsuleBg = new GradientDrawable();
        capsuleBg.setShape(GradientDrawable.RECTANGLE);
        capsuleBg.setCornerRadius(dp(13f));
        capsuleBg.setColor(0x18FFFFFF);
        capsuleBg.setStroke(Math.max(1, dp(1f)), 0x33FFFFFF);
        capsule.setBackground(capsuleBg);

        final PreferenceConfiguration.ScaleMode[] modes = {
                PreferenceConfiguration.ScaleMode.FIT,
                PreferenceConfiguration.ScaleMode.FILL,
                PreferenceConfiguration.ScaleMode.STRETCH
        };
        final int[] labels = {
                R.string.video_scale_mode_fit,
                R.string.video_scale_mode_fill,
                R.string.video_scale_mode_stretch
        };
        final TextView[] tabs = new TextView[modes.length];
        final PreferenceConfiguration.ScaleMode[] selected = { game.getVideoScaleMode() };

        Runnable refreshTabs = () -> {
            for (int i = 0; i < modes.length; i++) {
                boolean active = modes[i] == selected[0];
                TextView tab = tabs[i];
                if (active) {
                    GradientDrawable tabBg = new GradientDrawable();
                    tabBg.setShape(GradientDrawable.RECTANGLE);
                    tabBg.setCornerRadius(dp(10f));
                    tabBg.setColor(AppThemePalette.withAlpha(palette.highlight, 0.34f));
                    tabBg.setStroke(Math.max(1, dp(1.1f)), 0xC8FFFFFF);
                    tab.setBackground(tabBg);
                    tab.setTextColor(Color.WHITE);
                    tab.setTypeface(Typeface.DEFAULT_BOLD);
                } else {
                    tab.setBackground(null);
                    tab.setTextColor(0xA8FFFFFF);
                    tab.setTypeface(Typeface.DEFAULT);
                }
            }
        };

        for (int i = 0; i < modes.length; i++) {
            final PreferenceConfiguration.ScaleMode mode = modes[i];
            TextView tab = new TextView(ctx);
            tab.setText(labels[i]);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(4f), dp(6f), dp(4f), dp(6f));
            tab.setFocusable(true);
            tab.setClickable(true);
            tab.setOnClickListener(v -> {
                selected[0] = mode;
                refreshTabs.run();
                game.setVideoScaleModeLive(mode);
            });
            tabs[i] = tab;
            capsule.addView(tab, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        refreshTabs.run();

        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        capLp.topMargin = dp(6f);
        block.addView(capsule, capLp);

        parent.addView(block, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addDrawerActionButton(LinearLayout parent, Context ctx, String label,
                                       AppThemePalette palette, Runnable onClick) {
        TextView btn = new TextView(ctx);
        btn.setText(label);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(12f), dp(9f), dp(12f), dp(9f));
        btn.setFocusable(true);
        btn.setClickable(true);

        Runnable applyStyle = () -> {
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(14f));
            boolean focused = btn.isFocused();
            bg.setColor(AppThemePalette.withAlpha(palette.highlight, focused ? 0.32f : 0.18f));
            bg.setStroke(Math.max(1, dp(focused ? 1.6f : 1.1f)),
                    focused ? Color.WHITE : 0x66FFFFFF);
            btn.setBackground(bg);
        };
        applyStyle.run();
        attachFocusRing(btn, applyStyle);
        btn.setOnClickListener(v -> {
            animateTileTap(v);
            onClick.run();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6f);
        lp.bottomMargin = dp(2f);
        parent.addView(btn, lp);
    }

    private void setRowEnabledState(View row, boolean enabled) {
        row.setAlpha(enabled ? 1f : 0.38f);
        setViewTreeEnabled(row, enabled);
    }

    private void setViewTreeEnabled(View v, boolean enabled) {
        v.setEnabled(enabled);
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                setViewTreeEnabled(vg.getChildAt(i), enabled);
            }
        }
    }

    private void applySettingsPillStyle(View pill, ImageView icon, boolean isOpen, int accentRgb, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(999f));
        if (isOpen) {
            bg.setColor(AppThemePalette.withAlpha(accentRgb, focused ? 0.34f : 0.24f));
            bg.setStroke(Math.max(1, dp(focused ? 2.0f : 1.5f)),
                    focused ? Color.WHITE : AppThemePalette.withAlpha(Color.WHITE, 0.88f));
            pill.setBackground(bg);
            icon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        } else {
            bg.setColor(focused ? 0x2EFFFFFF : 0x1AFFFFFF);
            bg.setStroke(Math.max(1, dp(focused ? 1.8f : 1.15f)),
                    focused ? 0xCCFFFFFF : 0x4AFFFFFF);
            pill.setBackground(bg);
            icon.setColorFilter(focused ? Color.WHITE : 0xC8FFFFFF, PorterDuff.Mode.SRC_IN);
        }
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
