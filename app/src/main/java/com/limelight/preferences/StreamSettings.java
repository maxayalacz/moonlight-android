package com.limelight.preferences;

import static com.limelight.utils.ServerHelper.getActiveDisplay;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.MediaCodecInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.os.Vibrator;

import android.content.res.ColorStateList;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.DialogFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;

import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Range;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.Gson;
import com.limelight.DebugInfoActivity;
import com.limelight.BuildConfig;
import com.limelight.GameMenu;
import com.limelight.LimeLog;
import com.limelight.PcView;
import com.limelight.R;
import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.profiles.ProfilesManager;
import com.limelight.utils.Dialog;
import com.limelight.utils.FileUriUtils;
import com.limelight.utils.PerformanceDataTracker;
import com.limelight.utils.UiHelper;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

public class StreamSettings extends AppCompatActivity {
    private PreferenceConfiguration previousPrefs;
    private int previousDisplayPixelCount;

    private SettingsFragment prefsFragment;
    private View settingsHeaderBar;
    private View settingsSearchBar;
    private android.widget.ImageButton settingsBackButton;
    private android.widget.TextView settingsHeaderTitle;
    private android.widget.TextView settingsProfileSubtitle;
    private android.widget.TextView settingsToggleAllButton;
    private android.widget.ImageView settingsSearchIcon;
    private EditText settingsSearchInput;
    private android.widget.ImageButton settingsSearchClear;

    // HACK for Android 9
    static DisplayCutout displayCutoutP;

    void reloadSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode mode = getActiveDisplay(StreamSettings.this, previousPrefs).getMode();
            previousDisplayPixelCount = mode.getPhysicalWidth() * mode.getPhysicalHeight();
        }
        prefsFragment = new SettingsFragment(PreferenceConfiguration.readPreferences(
                this,
                PreferenceManager.getDefaultSharedPreferences(this)
        ));
        getSupportFragmentManager().beginTransaction().replace(
                R.id.stream_settings, prefsFragment
        ).commitAllowingStateLoss();
        if (settingsSearchInput != null && settingsSearchInput.getText() != null
                && settingsSearchInput.getText().length() > 0) {
            final String q = settingsSearchInput.getText().toString();
            settingsSearchInput.post(() -> {
                if (prefsFragment != null) {
                    prefsFragment.applySearchQuery(q);
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
//        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);

        previousPrefs = PreferenceConfiguration.readPreferences(this);

        UiHelper.setLocale(this);

        setContentView(R.layout.activity_stream_settings);
        setupSettingsHeader();
        UiHelper.applyReachabilitySpacing(this);

        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentStarted(@NonNull androidx.fragment.app.FragmentManager fm,
                                                  @NonNull androidx.fragment.app.Fragment f) {
                        if (f instanceof DialogFragment) {
                            UiHelper.styleDialogSquircle(((DialogFragment) f).getDialog());
                        }
                    }
                }, true);

        UiHelper.notifyNewRootView(this);
        applySettingsChrome();
    }

    private void setupSettingsHeader() {
        settingsHeaderBar = findViewById(R.id.settingsHeaderBar);
        settingsSearchBar = findViewById(R.id.settingsSearchBar);
        settingsBackButton = findViewById(R.id.settingsBackButton);
        settingsHeaderTitle = findViewById(R.id.settingsHeaderTitle);
        settingsProfileSubtitle = findViewById(R.id.settingsProfileSubtitle);
        settingsToggleAllButton = findViewById(R.id.settingsToggleAllButton);
        settingsSearchIcon = findViewById(R.id.settingsSearchIcon);
        settingsSearchInput = findViewById(R.id.settingsSearchInput);
        settingsSearchClear = findViewById(R.id.settingsSearchClear);

        if (settingsBackButton != null) {
            settingsBackButton.setOnClickListener(v -> onBackPressed());
        }
        if (settingsToggleAllButton != null) {
            settingsToggleAllButton.setOnClickListener(v -> {
                if (prefsFragment != null) {
                    prefsFragment.toggleAllCategories();
                    updateToggleAllButtonLabel();
                }
            });
        }
        if (settingsSearchClear != null) {
            settingsSearchClear.setOnClickListener(v -> {
                if (settingsSearchInput != null) {
                    settingsSearchInput.setText("");
                }
            });
        }
        if (settingsSearchInput != null) {
            settingsSearchInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    String q = s != null ? s.toString() : "";
                    if (settingsSearchClear != null) {
                        settingsSearchClear.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    if (prefsFragment != null) {
                        prefsFragment.applySearchQuery(q);
                        updateToggleAllButtonLabel();
                    }
                }
            });
        }
    }

    public void updateToggleAllButtonLabel() {
        if (settingsToggleAllButton == null || prefsFragment == null) {
            return;
        }
        boolean allExpanded = prefsFragment.areAllCategoriesExpanded();
        settingsToggleAllButton.setText(allExpanded
                ? R.string.settings_collapse_all
                : R.string.settings_expand_all);
    }

    public void applySettingsChrome() {
        com.limelight.ui.AppThemePalette palette = com.limelight.ui.AppThemePalette.getCurrent(this);
        int primaryText = palette.getPrimaryTextColor();
        int secondaryText = palette.getSecondaryTextColor();
        int neonAccent = palette.getNeonAccent();

        if (settingsHeaderBar != null) {
            UiHelper.applyFrostedCardBackground(
                    settingsHeaderBar,
                    com.limelight.ui.FlowingAuroraView.CornerMode.ALL,
                    24f,
                    0x5C121826);
        }
        if (settingsSearchBar != null) {
            UiHelper.applyFrostedCardBackground(
                    settingsSearchBar,
                    com.limelight.ui.FlowingAuroraView.CornerMode.ALL,
                    22f,
                    0x4E121826);
        }
        if (settingsBackButton != null) {
            settingsBackButton.setColorFilter(primaryText, android.graphics.PorterDuff.Mode.SRC_IN);
        }
        if (settingsHeaderTitle != null) {
            settingsHeaderTitle.setTextColor(primaryText);
        }
        if (settingsProfileSubtitle != null) {
            String activeProfile = ProfilesManager.getInstance().getActiveName();
            if (activeProfile != null && !activeProfile.isEmpty()) {
                settingsProfileSubtitle.setText(activeProfile);
                settingsProfileSubtitle.setTextColor(neonAccent);
                settingsProfileSubtitle.setVisibility(View.VISIBLE);
            } else {
                settingsProfileSubtitle.setVisibility(View.GONE);
            }
        }
        if (settingsToggleAllButton != null) {
            android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
            pill.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            pill.setCornerRadius(UiHelper.dpToPx(this, 999f));
            pill.setColor(com.limelight.ui.AppThemePalette.withAlpha(palette.accent, palette.isLight ? 0.14f : 0.22f));
            pill.setStroke(Math.max(1, Math.round(UiHelper.dpToPx(this, 1f))),
                    com.limelight.ui.AppThemePalette.withAlpha(neonAccent, 0.55f));
            settingsToggleAllButton.setBackground(pill);
            settingsToggleAllButton.setTextColor(neonAccent);
        }
        if (settingsSearchIcon != null) {
            settingsSearchIcon.setColorFilter(secondaryText, android.graphics.PorterDuff.Mode.SRC_IN);
        }
        if (settingsSearchClear != null) {
            settingsSearchClear.setColorFilter(secondaryText, android.graphics.PorterDuff.Mode.SRC_IN);
        }
        if (settingsSearchInput != null) {
            settingsSearchInput.setTextColor(primaryText);
            settingsSearchInput.setHintTextColor(secondaryText);
        }
        updateToggleAllButtonLabel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiHelper.refreshBackdrop(this);
        applySettingsChrome();
        if (prefsFragment != null) {
            prefsFragment.refreshThemePreferenceEntries();
            androidx.recyclerview.widget.RecyclerView rv = prefsFragment.getListView();
            if (rv != null && rv.getAdapter() != null) {
                rv.getAdapter().notifyDataSetChanged();
            }
        }
        UiHelper.restoreMenuForegroundIfNeeded(this);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // We have to use this hack on Android 9 because we don't have Display.getCutout()
        // which was added in Android 10.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            // Insets can be null when the activity is recreated on screen rotation
            // https://stackoverflow.com/questions/61241255/windowinsets-getdisplaycutout-is-null-everywhere-except-within-onattachedtowindo
            WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            if (insets != null) {
                displayCutoutP = insets.getDisplayCutout();
            }
        }

        reloadSettings();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        UiHelper.applyReachabilitySpacing(this);
        applySettingsChrome();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode mode = getActiveDisplay(StreamSettings.this, previousPrefs).getMode();

            // If the display's physical pixel count has changed, we consider that it's a new display
            // and we should reload our settings (which include display-dependent values).
            //
            // NB: We aren't using displayId here because that stays the same (DEFAULT_DISPLAY) when
            // switching between screens on a foldable device.
            if (mode.getPhysicalWidth() * mode.getPhysicalHeight() != previousDisplayPixelCount) {
                reloadSettings();
            }
        }
    }

    @Override
    // NOTE: This will NOT be called on Android 13+ with android:enableOnBackInvokedCallback="true"
    public void onBackPressed() {
        UiHelper.finishMenuWithDepthTransition(this, () -> {
            finish();

            // Language changes are handled via configuration changes in Android 13+,
            // so manual activity relaunching is no longer required.
            PreferenceConfiguration newPrefs = PreferenceConfiguration.readPreferences(this);
            if (!newPrefs.language.equals(previousPrefs.language)) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    // Restart the PC view to apply UI changes
                    Intent intent = new Intent(this, PcView.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent, null);
                } else {
                    if (newPrefs.language == PreferenceConfiguration.DEFAULT_LANGUAGE) {
                        Toast.makeText(this, "Language has been reset to default, please restart the app!", Toast.LENGTH_LONG).show();
                        System.exit(0);
                    }
                }
            }
        });
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private int nativeResolutionStartIndex = Integer.MAX_VALUE;
        private boolean nativeFramerateShown = false;

        private PreferenceConfiguration prevPrefConfig;

        public SettingsFragment(PreferenceConfiguration prefCfg) {
            prevPrefConfig = prefCfg;
        }

        protected SharedPreferences getPrefs() {
            if (getContext() != null) {
                return ProfilesManager.getInstance().getOverlayingSharedPreferences(getContext());
            }
            return getPreferenceManager().getSharedPreferences();
        }

        private void setValue(String preferenceKey, String value) {
            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            pref.setValue(value);
        }

        private void appendPreferenceEntry(ListPreference pref, String newEntryName, String newEntryValue) {
            CharSequence[] newEntries = Arrays.copyOf(pref.getEntries(), pref.getEntries().length + 1);
            CharSequence[] newValues = Arrays.copyOf(pref.getEntryValues(), pref.getEntryValues().length + 1);

            // Add the new option
            newEntries[newEntries.length - 1] = newEntryName;
            newValues[newValues.length - 1] = newEntryValue;

            pref.setEntries(newEntries);
            pref.setEntryValues(newValues);
        }

        private void addNativeResolutionEntry(int nativeWidth, int nativeHeight, boolean insetsRemoved, boolean portrait, boolean is_custom) {
            ListPreference pref = (ListPreference) findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING);

            String newName;

            if (insetsRemoved) {
                newName = getResources().getString(R.string.resolution_prefix_native_fullscreen);
            }
            else {
                newName = is_custom ? getResources().getString(R.string.resolution_prefix_custom) : getResources().getString(R.string.resolution_prefix_native);
            }

            if (PreferenceConfiguration.isSquarishScreen(nativeWidth, nativeHeight)) {
                if (portrait) {
                    newName += " " + getResources().getString(R.string.resolution_prefix_native_portrait);
                }
                else {
                    newName += " " + getResources().getString(R.string.resolution_prefix_native_landscape);
                }
            }

            newName += " ("+nativeWidth+"x"+nativeHeight+")";

            String newValue = nativeWidth+"x"+nativeHeight;

            // Check if the native resolution is already present
            for (CharSequence value : pref.getEntryValues()) {
                if (newValue.equals(value.toString())) {
                    // It is present in the default list, so don't add it again
                    return;
                }
            }

            if (pref.getEntryValues().length < nativeResolutionStartIndex) {
                nativeResolutionStartIndex = pref.getEntryValues().length;
            }
            appendPreferenceEntry(pref, newName, newValue);
        }

        private void addNativeResolutionEntries(int nativeWidth, int nativeHeight, boolean insetsRemoved, boolean is_custom) {
            if (PreferenceConfiguration.isSquarishScreen(nativeWidth, nativeHeight)) {
                addNativeResolutionEntry(nativeHeight, nativeWidth, insetsRemoved, true, is_custom);
            }
            addNativeResolutionEntry(nativeWidth, nativeHeight, insetsRemoved, false, is_custom);
        }

        private void addNativeFrameRateEntry(float framerate, boolean is_custom) {
            if (!is_custom) {
                framerate = Math.round(framerate);
                if (framerate == 0) {
                    return;
                }
            }

            ListPreference pref = (ListPreference) findPreference(PreferenceConfiguration.FPS_PREF_STRING);
            String fpsValue = is_custom ? Float.toString(framerate) : Integer.toString(Math.round(framerate));
            String fpsName = (is_custom ? getResources().getString(R.string.resolution_prefix_custom) : getResources().getString(R.string.resolution_prefix_native)) +
                    " (" + fpsValue + " " + getResources().getString(R.string.fps_suffix_fps) + ")";

            // Check if the native frame rate is already present
            for (CharSequence value : pref.getEntryValues()) {
                if (fpsValue.equals(value.toString())) {
                    // It is present in the default list, so don't add it again
                    nativeFramerateShown = false;
                    return;
                }
            }

            appendPreferenceEntry(pref, fpsName, fpsValue);
            nativeFramerateShown = true;
        }

        private void removeValue(String preferenceKey, String value, Runnable onMatched) {
            int matchingCount = 0;

            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            // Count the number of matching entries we'll be removing
            for (CharSequence seq : pref.getEntryValues()) {
                if (seq.toString().equalsIgnoreCase(value)) {
                    matchingCount++;
                }
            }

            // Create the new arrays
            CharSequence[] entries = new CharSequence[pref.getEntries().length-matchingCount];
            CharSequence[] entryValues = new CharSequence[pref.getEntryValues().length-matchingCount];
            int outIndex = 0;
            for (int i = 0; i < pref.getEntryValues().length; i++) {
                if (pref.getEntryValues()[i].toString().equalsIgnoreCase(value)) {
                    // Skip matching values
                    continue;
                }

                entries[outIndex] = pref.getEntries()[i];
                entryValues[outIndex] = pref.getEntryValues()[i];
                outIndex++;
            }

            if (pref.getValue().equalsIgnoreCase(value)) {
                onMatched.run();
            }

            // Update the preference with the new list
            pref.setEntries(entries);
            pref.setEntryValues(entryValues);
        }

        private void resetBitrateToDefault(SharedPreferences prefs, String res, String fps) {
            if (res == null) {
                res = prefs.getString(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.DEFAULT_RESOLUTION);
            }
            if (fps == null) {
                fps = prefs.getString(PreferenceConfiguration.FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS);
            }

            prefs.edit()
                    .putInt(PreferenceConfiguration.BITRATE_PREF_STRING,
                            PreferenceConfiguration.getDefaultBitrate(res, fps))
                    .apply();
        }

        private final java.util.Set<String> expandedCategories = new java.util.HashSet<>();
        private final java.util.Map<Preference, Boolean> basePrefVisibility = new java.util.IdentityHashMap<>();
        private final java.util.Map<String, Integer> categoryOptionCounts = new java.util.HashMap<>();
        private String searchQuery = "";

        private void captureBaseVisibilityAndCollapseAll() {
            PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) {
                return;
            }
            basePrefVisibility.clear();
            categoryOptionCounts.clear();
            for (int i = 0; i < screen.getPreferenceCount(); i++) {
                Preference p = screen.getPreference(i);
                if (p instanceof PreferenceCategory) {
                    PreferenceCategory cat = (PreferenceCategory) p;
                    int visibleCount = 0;
                    for (int j = 0; j < cat.getPreferenceCount(); j++) {
                        Preference child = cat.getPreference(j);
                        boolean naturallyVisible = child.isVisible();
                        basePrefVisibility.put(child, naturallyVisible);
                        if (naturallyVisible) {
                            visibleCount++;
                        }
                    }
                    if (cat.getKey() != null) {
                        categoryOptionCounts.put(cat.getKey(), visibleCount);
                    }
                }
            }
            applyAccordionVisibility();
        }

        private void applyAccordionVisibility() {
            PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) {
                return;
            }
            String q = searchQuery != null ? searchQuery.trim().toLowerCase(java.util.Locale.ROOT) : "";
            boolean searching = !q.isEmpty();

            for (int i = 0; i < screen.getPreferenceCount(); i++) {
                Preference p = screen.getPreference(i);
                if (!(p instanceof PreferenceCategory)) {
                    continue;
                }
                PreferenceCategory cat = (PreferenceCategory) p;
                String catKey = cat.getKey() != null ? cat.getKey() : String.valueOf(i);
                String catTitle = cat.getTitle() != null ? cat.getTitle().toString().toLowerCase(java.util.Locale.ROOT) : "";
                boolean catTitleMatches = searching && catTitle.contains(q);
                boolean expanded = expandedCategories.contains(catKey);

                int naturalCount = 0;
                int matchedCount = 0;
                for (int j = 0; j < cat.getPreferenceCount(); j++) {
                    Preference child = cat.getPreference(j);
                    Boolean baseVis = basePrefVisibility.get(child);
                    boolean naturallyVisible = (baseVis != null) ? baseVis : child.isVisible();
                    if (!naturallyVisible) {
                        child.setVisible(false);
                        continue;
                    }
                    naturalCount++;
                    if (searching) {
                        String t = child.getTitle() != null ? child.getTitle().toString().toLowerCase(java.util.Locale.ROOT) : "";
                        String s = child.getSummary() != null ? child.getSummary().toString().toLowerCase(java.util.Locale.ROOT) : "";
                        boolean match = catTitleMatches || t.contains(q) || s.contains(q);
                        child.setVisible(match);
                        if (match) {
                            matchedCount++;
                        }
                    } else {
                        child.setVisible(expanded);
                    }
                }

                if (searching) {
                    cat.setVisible(matchedCount > 0);
                    categoryOptionCounts.put(catKey, matchedCount);
                } else {
                    cat.setVisible(naturalCount > 0);
                    categoryOptionCounts.put(catKey, naturalCount);
                }
            }

            androidx.recyclerview.widget.RecyclerView rv = getListView();
            if (rv != null && rv.getAdapter() != null) {
                rv.getAdapter().notifyDataSetChanged();
            }
            if (getActivity() instanceof StreamSettings) {
                ((StreamSettings) getActivity()).updateToggleAllButtonLabel();
            }
        }

        public void applySearchQuery(String query) {
            this.searchQuery = query != null ? query : "";
            applyAccordionVisibility();
        }

        public boolean areAllCategoriesExpanded() {
            PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) {
                return false;
            }
            int visibleCats = 0;
            int expandedCount = 0;
            for (int i = 0; i < screen.getPreferenceCount(); i++) {
                Preference p = screen.getPreference(i);
                if (p instanceof PreferenceCategory && p.isVisible()) {
                    visibleCats++;
                    if (p.getKey() != null && expandedCategories.contains(p.getKey())) {
                        expandedCount++;
                    }
                }
            }
            return visibleCats > 0 && expandedCount >= visibleCats;
        }

        public void toggleAllCategories() {
            PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) {
                return;
            }
            boolean collapse = areAllCategoriesExpanded();
            expandedCategories.clear();
            if (!collapse) {
                for (int i = 0; i < screen.getPreferenceCount(); i++) {
                    Preference p = screen.getPreference(i);
                    if (p instanceof PreferenceCategory && p.getKey() != null && p.isVisible()) {
                        expandedCategories.add(p.getKey());
                    }
                }
            }
            applyAccordionVisibility();
        }

        public void refreshThemePreferenceEntries() {
            if (getContext() == null) {
                return;
            }
            ListPreference schemePref = findPreference(com.limelight.ui.AppThemePalette.PREF_COLOR_SCHEME);
            if (schemePref == null) {
                return;
            }
            java.util.List<CharSequence> entries = new java.util.ArrayList<>();
            java.util.List<CharSequence> values = new java.util.ArrayList<>();
            for (com.limelight.ui.AppThemePalette preset : com.limelight.ui.AppThemePalette.getPresets()) {
                entries.add(preset.label);
                values.add(preset.key);
            }
            for (com.limelight.ui.AppThemePalette.ThemeBundle saved : com.limelight.ui.AppThemePalette.loadSavedThemes(requireContext())) {
                entries.add(saved.name);
                values.add(com.limelight.ui.AppThemePalette.SAVED_THEME_PREFIX + saved.id);
            }
            entries.add(getString(R.string.color_scheme_custom));
            values.add(com.limelight.ui.AppThemePalette.CUSTOM_THEME_KEY);

            schemePref.setEntries(entries.toArray(new CharSequence[0]));
            schemePref.setEntryValues(values.toArray(new CharSequence[0]));
            String activeKey = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString(com.limelight.ui.AppThemePalette.PREF_COLOR_SCHEME,
                            com.limelight.ui.AppThemePalette.DEFAULT_THEME_KEY);
            schemePref.setValue(activeKey);
        }

        private static int getCategoryIconRes(String catKey) {
            if (catKey == null) {
                return R.drawable.ic_settings;
            }
            switch (catKey) {
                case "category_ui_settings":
                    return R.drawable.ic_palette;
                case "category_portal_animation":
                    return R.drawable.ic_focus_mode;
                case "category_video_settings":
                    return R.drawable.ic_computer;
                case "category_audio_settings":
                    return R.drawable.ic_audio;
                case "category_perf_monitor_settings":
                    return R.drawable.ic_qm_perf_stats;
                case "category_gamepad_settings":
                    return R.drawable.ic_qm_osc;
                case "category_input_settings":
                    return R.drawable.ic_qm_mouse_multitouch;
                case "category_onscreen_controls":
                    return R.drawable.ic_qm_osc;
                case "category_onscreen_keyboard":
                    return R.drawable.ic_qm_keyboard;
                case "category_virtual_trackpad_settings":
                    return R.drawable.ic_qm_mouse_trackpad_natural;
                case "category_special_key_layout":
                    return R.drawable.ic_keyboard_setting;
                case "category_host_settings":
                    return R.drawable.ic_computer;
                case "category_general_settings":
                    return R.drawable.ic_language;
                case "category_advanced_settings":
                    return R.drawable.ic_lime_layer;
                case "category_settings_misc":
                default:
                    return R.drawable.ic_settings;
            }
        }

        @NonNull
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            UiHelper.applyStatusBarPadding(view);
            return view;
        }

        @Override
        public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            setDivider(null);
            final androidx.recyclerview.widget.RecyclerView rv = getListView();
            if (rv != null) {
                final int sidePad = (int) UiHelper.dpToPx(requireContext(), 4f);
                final int bottomPad = (int) UiHelper.dpToPx(requireContext(), 28f);
                final int cardGap = (int) UiHelper.dpToPx(requireContext(), 10f);
                final int dividerInset = (int) UiHelper.dpToPx(requireContext(), 18f);
                final android.graphics.Paint dividerPaint = new android.graphics.Paint();
                dividerPaint.setStrokeWidth(Math.max(1f, UiHelper.dpToPx(requireContext(), 0.75f)));

                rv.setClipToPadding(false);
                rv.setPadding(sidePad, (int) UiHelper.dpToPx(requireContext(), 2f), sidePad, bottomPad);
                rv.addItemDecoration(new androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
                    @Override
                    public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View item,
                                               @NonNull androidx.recyclerview.widget.RecyclerView parent,
                                               @NonNull androidx.recyclerview.widget.RecyclerView.State state) {
                        androidx.recyclerview.widget.RecyclerView.Adapter<?> rawAdapter = parent.getAdapter();
                        if (!(rawAdapter instanceof androidx.preference.PreferenceGroupAdapter)) {
                            return;
                        }
                        int pos = parent.getChildAdapterPosition(item);
                        if (pos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                            return;
                        }
                        androidx.preference.PreferenceGroupAdapter adapter =
                                (androidx.preference.PreferenceGroupAdapter) rawAdapter;
                        Preference pref = adapter.getItem(pos);
                        if (pref instanceof PreferenceCategory && pos > 0) {
                            outRect.top = cardGap;
                        }
                    }

                    @Override
                    public void onDrawOver(@NonNull android.graphics.Canvas c,
                                           @NonNull androidx.recyclerview.widget.RecyclerView parent,
                                           @NonNull androidx.recyclerview.widget.RecyclerView.State state) {
                        androidx.recyclerview.widget.RecyclerView.Adapter<?> rawAdapter = parent.getAdapter();
                        if (!(rawAdapter instanceof androidx.preference.PreferenceGroupAdapter)) {
                            return;
                        }
                        com.limelight.ui.AppThemePalette divPalette = com.limelight.ui.AppThemePalette.getCurrent(parent.getContext());
                        dividerPaint.setColor(divPalette.getDividerColor());
                        androidx.preference.PreferenceGroupAdapter adapter =
                                (androidx.preference.PreferenceGroupAdapter) rawAdapter;
                        for (int i = 0; i < parent.getChildCount(); i++) {
                            View child = parent.getChildAt(i);
                            int pos = parent.getChildAdapterPosition(child);
                            if (pos == androidx.recyclerview.widget.RecyclerView.NO_POSITION
                                    || pos + 1 >= adapter.getItemCount()) {
                                continue;
                            }
                            Preference pref = adapter.getItem(pos);
                            Preference nextPref = adapter.getItem(pos + 1);
                            if (pref == null) {
                                continue;
                            }
                            if (nextPref != null && !(nextPref instanceof PreferenceCategory)) {
                                float y = child.getBottom() + child.getTranslationY();
                                c.drawLine(child.getLeft() + dividerInset, y,
                                        child.getRight() - dividerInset, y, dividerPaint);
                            }
                        }
                    }
                });
            }
        }

        @NonNull
        @Override
        @SuppressWarnings("RestrictedApi")
        protected androidx.recyclerview.widget.RecyclerView.Adapter onCreateAdapter(
                @NonNull PreferenceScreen preferenceScreen) {
            return new androidx.preference.PreferenceGroupAdapter(preferenceScreen) {
                @Override
                public void onBindViewHolder(@NonNull androidx.preference.PreferenceViewHolder holder, int position) {
                    super.onBindViewHolder(holder, position);
                    Preference pref = getItem(position);
                    Preference nextPref = (position + 1 < getItemCount()) ? getItem(position + 1) : null;
                    View itemView = holder.itemView;
                    if (pref == null) {
                        return;
                    }
                    com.limelight.ui.AppThemePalette palette = com.limelight.ui.AppThemePalette.getCurrent(itemView.getContext());
                    if (pref instanceof PreferenceCategory) {
                        PreferenceCategory cat = (PreferenceCategory) pref;
                        String catKey = cat.getKey();
                        boolean hasChildren = nextPref != null && !(nextPref instanceof PreferenceCategory);
                        boolean searching = searchQuery != null && !searchQuery.trim().isEmpty();
                        boolean isExpanded = searching || (catKey != null && expandedCategories.contains(catKey));

                        com.limelight.ui.FlowingAuroraView.CornerMode mode = hasChildren
                                ? com.limelight.ui.FlowingAuroraView.CornerMode.TOP
                                : com.limelight.ui.FlowingAuroraView.CornerMode.ALL;
                        if (!UiHelper.applyFrostedCardBackground(itemView, mode, 24f, isExpanded ? 0x64121826 : 0x54121826)) {
                            itemView.setBackgroundResource(hasChildren
                                    ? R.drawable.bg_oneui_glass_card_top
                                    : R.drawable.bg_oneui_squircle_card);
                        }

                        int neonAccent = palette.getNeonAccent();
                        View iconBg = holder.findViewById(R.id.categoryIconBg);
                        if (iconBg != null) {
                            android.graphics.drawable.GradientDrawable badge = new android.graphics.drawable.GradientDrawable();
                            badge.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                            badge.setCornerRadius(UiHelper.dpToPx(itemView.getContext(), 12f));
                            badge.setColor(com.limelight.ui.AppThemePalette.withAlpha(
                                    palette.accent, isExpanded ? (palette.isLight ? 0.22f : 0.30f) : (palette.isLight ? 0.12f : 0.18f)));
                            badge.setStroke(Math.max(1, Math.round(UiHelper.dpToPx(itemView.getContext(), 1f))),
                                    com.limelight.ui.AppThemePalette.withAlpha(neonAccent, isExpanded ? 0.65f : 0.32f));
                            iconBg.setBackground(badge);
                        }
                        View iconView = holder.findViewById(R.id.categoryIcon);
                        if (iconView instanceof android.widget.ImageView) {
                            android.widget.ImageView iv = (android.widget.ImageView) iconView;
                            iv.setImageResource(getCategoryIconRes(catKey));
                            iv.setColorFilter(isExpanded ? neonAccent : palette.getPrimaryTextColor(),
                                    android.graphics.PorterDuff.Mode.SRC_IN);
                        }

                        View title = holder.findViewById(android.R.id.title);
                        if (title instanceof android.widget.TextView) {
                            android.widget.TextView tv = (android.widget.TextView) title;
                            tv.setTextColor(palette.getPrimaryTextColor());
                            tv.setShadowLayer(0f, 0f, 0f, 0);
                        }

                        View countView = holder.findViewById(R.id.categoryCountBadge);
                        if (countView instanceof android.widget.TextView) {
                            int count = (catKey != null && categoryOptionCounts.containsKey(catKey))
                                    ? categoryOptionCounts.get(catKey)
                                    : cat.getPreferenceCount();
                            ((android.widget.TextView) countView).setText(
                                    itemView.getContext().getString(R.string.settings_option_count_fmt, count));
                            ((android.widget.TextView) countView).setTextColor(palette.getSecondaryTextColor());
                        }

                        View chevron = holder.findViewById(R.id.categoryChevron);
                        if (chevron instanceof android.widget.ImageView) {
                            android.widget.ImageView chev = (android.widget.ImageView) chevron;
                            chev.setColorFilter(isExpanded ? neonAccent : palette.getSecondaryTextColor(),
                                    android.graphics.PorterDuff.Mode.SRC_IN);
                            chev.setRotation(isExpanded ? 180f : 0f);
                        }

                        itemView.setClickable(true);
                        itemView.setFocusable(true);
                        itemView.setOnClickListener(v -> {
                            if (catKey == null || searching) {
                                return;
                            }
                            if (expandedCategories.contains(catKey)) {
                                expandedCategories.remove(catKey);
                            } else {
                                expandedCategories.add(catKey);
                            }
                            applyAccordionVisibility();
                        });
                        itemView.setAlpha(1.0f);
                    } else {
                        boolean isLastInCard = nextPref == null || nextPref instanceof PreferenceCategory;
                        com.limelight.ui.FlowingAuroraView.CornerMode mode = isLastInCard
                                ? com.limelight.ui.FlowingAuroraView.CornerMode.BOTTOM
                                : com.limelight.ui.FlowingAuroraView.CornerMode.MIDDLE;
                        if (!UiHelper.applyFrostedCardBackground(itemView, mode, 24f, 0x4E121826)) {
                            itemView.setBackgroundResource(isLastInCard
                                    ? R.drawable.bg_oneui_glass_card_bottom
                                    : R.drawable.bg_oneui_glass_card_middle);
                        }
                        View title = holder.findViewById(android.R.id.title);
                        if (title instanceof android.widget.TextView) {
                            ((android.widget.TextView) title).setTextColor(palette.getPrimaryTextColor());
                        }
                        View summary = holder.findViewById(android.R.id.summary);
                        if (summary instanceof android.widget.TextView) {
                            ((android.widget.TextView) summary).setTextColor(palette.getSecondaryTextColor());
                        }
                        itemView.setAlpha(pref.isEnabled() ? 1.0f : 0.45f);
                        applySwitchPalette(itemView, palette);
                        applyValuePill(holder, pref, palette);
                    }
                }

                private void applyValuePill(@NonNull androidx.preference.PreferenceViewHolder holder,
                                            @NonNull Preference pref,
                                            @NonNull com.limelight.ui.AppThemePalette palette) {
                    View titleView = holder.findViewById(android.R.id.title);
                    View summaryView = holder.findViewById(android.R.id.summary);
                    if (titleView == null || !(titleView.getParent() instanceof ViewGroup)) {
                        return;
                    }
                    ViewGroup textColumn = (ViewGroup) titleView.getParent();
                    android.widget.TextView pill = textColumn.findViewWithTag("oneui_pref_value_pill");

                    String pillText = null;
                    if (pref instanceof ListPreference) {
                        CharSequence entry = ((ListPreference) pref).getEntry();
                        if (entry != null && entry.length() > 0) {
                            pillText = entry.toString();
                        }
                    } else if (pref instanceof SeekBarPreference) {
                        pillText = ((SeekBarPreference) pref).getFormattedValue();
                    }

                    if (pillText == null) {
                        if (pill != null) {
                            pill.setVisibility(View.GONE);
                        }
                        return;
                    }

                    Context ctx = textColumn.getContext();
                    if (pill == null) {
                        pill = new android.widget.TextView(ctx);
                        pill.setTag("oneui_pref_value_pill");
                        pill.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11.5f);
                        pill.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                        pill.setSingleLine(true);
                        int padH = Math.round(UiHelper.dpToPx(ctx, 10f));
                        int padV = Math.round(UiHelper.dpToPx(ctx, 4f));
                        pill.setPadding(padH, padV, padH, padV);
                        textColumn.addView(pill);
                    }

                    boolean summaryVisible = summaryView != null && summaryView.getVisibility() == View.VISIBLE
                            && (summaryView instanceof android.widget.TextView)
                            && !TextUtils.isEmpty(((android.widget.TextView) summaryView).getText());

                    if (textColumn instanceof android.widget.RelativeLayout) {
                        android.widget.RelativeLayout.LayoutParams rlp = new android.widget.RelativeLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        rlp.addRule(android.widget.RelativeLayout.BELOW,
                                summaryVisible ? android.R.id.summary : android.R.id.title);
                        rlp.addRule(android.widget.RelativeLayout.ALIGN_START, android.R.id.title);
                        rlp.topMargin = Math.round(UiHelper.dpToPx(ctx, 6f));
                        pill.setLayoutParams(rlp);
                    } else if (textColumn instanceof android.widget.LinearLayout) {
                        android.widget.LinearLayout.LayoutParams llp = new android.widget.LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        llp.topMargin = Math.round(UiHelper.dpToPx(ctx, 6f));
                        pill.setLayoutParams(llp);
                    } else {
                        ViewGroup.MarginLayoutParams mlp = new ViewGroup.MarginLayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        mlp.topMargin = Math.round(UiHelper.dpToPx(ctx, 6f));
                        pill.setLayoutParams(mlp);
                    }

                    int neonAccent = palette.getNeonAccent();
                    android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                    bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    bg.setCornerRadius(UiHelper.dpToPx(ctx, 999f));
                    bg.setColor(com.limelight.ui.AppThemePalette.withAlpha(palette.accent, palette.isLight ? 0.14f : 0.22f));
                    bg.setStroke(Math.max(1, Math.round(UiHelper.dpToPx(ctx, 1f))),
                            com.limelight.ui.AppThemePalette.withAlpha(neonAccent, 0.45f));
                    pill.setBackground(bg);
                    pill.setTextColor(neonAccent);
                    pill.setText(pillText);
                    pill.setVisibility(View.VISIBLE);
                }

                private android.graphics.drawable.Drawable buildPillTrackDrawable(
                        Context ctx, com.limelight.ui.AppThemePalette palette, int neonAccent) {
                    int w = Math.round(UiHelper.dpToPx(ctx, 52f));
                    int h = Math.round(UiHelper.dpToPx(ctx, 30f));
                    float radius = h * 0.5f;
                    int strokeW = Math.max(2, Math.round(UiHelper.dpToPx(ctx, 1.8f)));

                    android.graphics.drawable.GradientDrawable checked = new android.graphics.drawable.GradientDrawable();
                    checked.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    checked.setSize(w, h);
                    checked.setCornerRadius(radius);
                    checked.setColor(com.limelight.ui.AppThemePalette.withAlpha(neonAccent, palette.isLight ? 0.22f : 0.28f));
                    checked.setStroke(strokeW, neonAccent);

                    android.graphics.drawable.GradientDrawable unchecked = new android.graphics.drawable.GradientDrawable();
                    unchecked.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    unchecked.setSize(w, h);
                    unchecked.setCornerRadius(radius);
                    unchecked.setColor(palette.isLight ? 0x1A0F172A : 0x2E0B101B);
                    unchecked.setStroke(strokeW, com.limelight.ui.AppThemePalette.withAlpha(
                            palette.cardOutline, palette.isLight ? 0.72f : 0.75f));

                    android.graphics.drawable.StateListDrawable stateList = new android.graphics.drawable.StateListDrawable();
                    stateList.addState(new int[]{android.R.attr.state_checked}, checked);
                    stateList.addState(new int[]{}, unchecked);
                    return stateList;
                }

                private android.graphics.drawable.Drawable buildInsetThumbBall(Context ctx, int ballColor) {
                    int frameSize = Math.round(UiHelper.dpToPx(ctx, 30f));
                    int pad = Math.round(UiHelper.dpToPx(ctx, 5.5f));
                    int ballSize = Math.max(8, frameSize - pad * 2);

                    android.graphics.drawable.GradientDrawable frame = new android.graphics.drawable.GradientDrawable();
                    frame.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    frame.setSize(frameSize, frameSize);
                    frame.setColor(android.graphics.Color.TRANSPARENT);

                    android.graphics.drawable.GradientDrawable ball = new android.graphics.drawable.GradientDrawable();
                    ball.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                    ball.setSize(ballSize, ballSize);
                    ball.setColor(ballColor);

                    android.graphics.drawable.LayerDrawable layer = new android.graphics.drawable.LayerDrawable(
                            new android.graphics.drawable.Drawable[]{frame, ball});
                    layer.setLayerInset(1, pad, pad, pad, pad);
                    return layer;
                }

                private android.graphics.drawable.Drawable buildPillThumbDrawable(
                        Context ctx, com.limelight.ui.AppThemePalette palette, int neonAccent) {
                    int uncheckedThumb = palette.isLight
                            ? com.limelight.ui.AppThemePalette.withAlpha(palette.getSecondaryTextColor(), 0.85f)
                            : 0xFFD0D5DD;
                    android.graphics.drawable.StateListDrawable stateList = new android.graphics.drawable.StateListDrawable();
                    stateList.addState(new int[]{android.R.attr.state_checked}, buildInsetThumbBall(ctx, neonAccent));
                    stateList.addState(new int[]{}, buildInsetThumbBall(ctx, uncheckedThumb));
                    return stateList;
                }

                private void applySwitchPalette(View root, com.limelight.ui.AppThemePalette palette) {
                    int neonAccent = palette.getNeonAccent();
                    if (root instanceof SwitchCompat) {
                        SwitchCompat sw = (SwitchCompat) root;
                        sw.setThumbTintList(null);
                        sw.setTrackTintList(null);
                        sw.setTrackDrawable(buildPillTrackDrawable(sw.getContext(), palette, neonAccent));
                        sw.setThumbDrawable(buildPillThumbDrawable(sw.getContext(), palette, neonAccent));
                        sw.setSwitchMinWidth(Math.round(UiHelper.dpToPx(sw.getContext(), 52f)));
                        sw.setThumbTextPadding(0);
                        sw.setSplitTrack(false);
                        sw.setShowText(false);
                    } else if (root instanceof android.widget.Switch) {
                        android.widget.Switch sw = (android.widget.Switch) root;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            sw.setThumbTintList(null);
                            sw.setTrackTintList(null);
                        }
                        sw.setTrackDrawable(buildPillTrackDrawable(sw.getContext(), palette, neonAccent));
                        sw.setThumbDrawable(buildPillThumbDrawable(sw.getContext(), palette, neonAccent));
                        sw.setSwitchMinWidth(Math.round(UiHelper.dpToPx(sw.getContext(), 52f)));
                        sw.setThumbTextPadding(0);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            sw.setSplitTrack(false);
                        }
                        sw.setShowText(false);
                    } else if (root instanceof ViewGroup) {
                        ViewGroup vg = (ViewGroup) root;
                        for (int i = 0; i < vg.getChildCount(); i++) {
                            applySwitchPalette(vg.getChildAt(i), palette);
                        }
                    }
                }
            };
        }

        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState, boolean unused) {
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            if (getPreferenceManager().getPreferenceDataStore() == null && getContext() != null) {
                final SharedPreferences overlayPrefs = ProfilesManager.getInstance().getOverlayingSharedPreferences(getContext());
                getPreferenceManager().setPreferenceDataStore(new androidx.preference.PreferenceDataStore() {
                    @Override public void putString(String key, String value) { overlayPrefs.edit().putString(key, value).apply(); }
                    @Override public void putStringSet(String key, java.util.Set<String> values) { overlayPrefs.edit().putStringSet(key, values).apply(); }
                    @Override public void putInt(String key, int value) { overlayPrefs.edit().putInt(key, value).apply(); }
                    @Override public void putLong(String key, long value) { overlayPrefs.edit().putLong(key, value).apply(); }
                    @Override public void putFloat(String key, float value) { overlayPrefs.edit().putFloat(key, value).apply(); }
                    @Override public void putBoolean(String key, boolean value) { overlayPrefs.edit().putBoolean(key, value).apply(); }
                    @Override public String getString(String key, String defValue) { return overlayPrefs.getString(key, defValue); }
                    @Override public java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) { return overlayPrefs.getStringSet(key, defValues); }
                    @Override public int getInt(String key, int defValue) { return overlayPrefs.getInt(key, defValue); }
                    @Override public long getLong(String key, long defValue) { return overlayPrefs.getLong(key, defValue); }
                    @Override public float getFloat(String key, float defValue) { return overlayPrefs.getFloat(key, defValue); }
                    @Override public boolean getBoolean(String key, boolean defValue) { return overlayPrefs.getBoolean(key, defValue); }
                });
            }
            initializePreferences();
        }

        public void initializePreferences() {
            addPreferencesFromResource(R.xml.preferences);
            PreferenceScreen screen = getPreferenceScreen();

            AppCompatActivity activity = (AppCompatActivity) requireActivity();
            PackageManager pm = activity.getPackageManager();

            // hide on-screen controls category on non touch screen devices
            if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                PreferenceCategory category = findPreference("category_onscreen_controls");
                if (category != null) {
                    screen.removePreference(category);
                }
                category = findPreference("category_special_key_layout");
                if (category != null) {
                    screen.removePreference(category);
                }
            }

            // Hide remote desktop mouse mode on pre-Oreo (which doesn't have pointer capture)
            // and NVIDIA SHIELD devices (which support raw mouse input in pointer capture mode)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    getActivity().getPackageManager().hasSystemFeature("com.nvidia.feature.shield")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_input_settings");
                category.removePreference(findPreference("checkbox_absolute_mouse_mode"));
            }

            // Hide gamepad motion sensor option when running on OSes before Android 12.
            // Support for motion, LED, battery, and other extensions were introduced in S.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_gamepad_motion_sensors"));
            }

            // Hide gamepad motion sensor fallback option if the device has no gyro or accelerometer
            if (!pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER) &&
                    !activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_force_device_motion"));
                category.removePreference(findPreference("checkbox_gamepad_motion_fallback"));
            }

            // Hide USB driver options on devices without USB host support
            if (!pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_usb_bind_all"));
                category.removePreference(findPreference("checkbox_usb_driver"));
            }

            // Remove PiP mode on devices pre-Oreo, where the feature is not available (some low RAM devices),
            // and on Fire OS where it violates the Amazon App Store guidelines for some reason.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    !pm.hasSystemFeature("android.software.picture_in_picture") ||
                    pm.hasSystemFeature("com.amazon.software.fireos")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_ui_settings");
                category.removePreference(findPreference("checkbox_enable_pip"));
            }

            // Fire TV apps are not allowed to use WebViews or browsers, so hide the Help category
            /*if (getActivity().getPackageManager().hasSystemFeature("amazon.hardware.fire_tv")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_help");
                screen.removePreference(category);
            }*/
            PreferenceCategory category_gamepad_settings =
                    (PreferenceCategory) findPreference("category_gamepad_settings");
            // Remove the vibration options if the device can't vibrate
            if (!((Vibrator)getActivity().getSystemService(Context.VIBRATOR_SERVICE)).hasVibrator()) {
                category_gamepad_settings.removePreference(findPreference("checkbox_vibrate_fallback"));
                category_gamepad_settings.removePreference(findPreference("seekbar_vibrate_fallback_strength"));
                // The entire OSC category may have already been removed by the touchscreen check above
                PreferenceCategory category = findPreference("category_onscreen_controls");
                if (category != null) {
                    category.removePreference(findPreference("checkbox_vibrate_osc"));
                }
                category = findPreference("category_special_key_layout");
                if (category != null) {
                    category.removePreference(findPreference("checkbox_vibrate_keyboard"));
                }
                category = findPreference("category_gamepad_settings");
                if (category != null) {
                    category.removePreference(findPreference("checkbox_enable_device_rumble"));
                }
            }
            else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    !((Vibrator)getActivity().getSystemService(Context.VIBRATOR_SERVICE)).hasAmplitudeControl()) {
                // Remove the vibration strength selector of the device doesn't have amplitude control
                category_gamepad_settings.removePreference(findPreference("seekbar_vibrate_fallback_strength"));
            }

            // Check custom resolution
            String customResStr = prevPrefConfig.customResolution;
            if(customResStr != null && !customResStr.isEmpty()){
                String[] resolutionSegments = customResStr.split("x");
                if(resolutionSegments.length == 2){
                    try {
                        addNativeResolutionEntries(Integer.parseInt(resolutionSegments[0]), Integer.parseInt(resolutionSegments[1]), false, true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            
            // Check custom refresh rate
            String customRefreshRateStr = prevPrefConfig.customRefreshRate;
            if (customRefreshRateStr != null && !customRefreshRateStr.isEmpty()) {
                try {
                    float customRefreshRateValue = Float.parseFloat(customRefreshRateStr);
                    if (customRefreshRateValue > 0) {
                        addNativeFrameRateEntry(customRefreshRateValue, true);
                    }
                } catch (NumberFormatException e) {
                    getPrefs().edit().remove(PreferenceConfiguration.CUSTOM_REFRESH_RATE_PREF_STRING).apply();
                }
            }

            Display display = activity.getWindowManager().getDefaultDisplay();
            float maxSupportedFps = display.getRefreshRate();

            // Hide non-supported resolution/FPS combinations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int maxSupportedResW = 0;

                // Add a native resolution with any insets included for users that don't want content
                // behind the notch of their display
                boolean hasInsets = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    DisplayCutout cutout;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Use the much nicer Display.getCutout() API on Android 10+
                        cutout = display.getCutout();
                    }
                    else {
                        // Android 9 only
                        cutout = displayCutoutP;
                    }

                    if (cutout != null) {
                        int widthInsets = cutout.getSafeInsetLeft() + cutout.getSafeInsetRight();
                        int heightInsets = cutout.getSafeInsetBottom() + cutout.getSafeInsetTop();

                        if (widthInsets != 0 || heightInsets != 0) {
                            DisplayMetrics metrics = new DisplayMetrics();
                            display.getRealMetrics(metrics);

                            int width = Math.max(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets);
                            int height = Math.min(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets);

                            addNativeResolutionEntries(width, height, false, false);
                            hasInsets = true;
                        }
                    }
                }

                // Always allow resolutions that are smaller or equal to the active
                // display resolution because decoders can report total non-sense to us.
                // For example, a p201 device reports:
                // AVC Decoder: OMX.amlogic.avc.decoder.awesome
                // HEVC Decoder: OMX.amlogic.hevc.decoder.awesome
                // AVC supported width range: 64 - 384
                // HEVC supported width range: 64 - 544
                for (Display.Mode candidate : display.getSupportedModes()) {
                    // Some devices report their dimensions in the portrait orientation
                    // where height > width. Normalize these to the conventional width > height
                    // arrangement before we process them.

                    int width = Math.max(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());
                    int height = Math.min(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());

                    // Some TVs report strange values here, so let's avoid native resolutions on a TV
                    // unless they report greater than 4K resolutions.
                    if (!activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                            (width > 3840 || height > 2160)) {
                        addNativeResolutionEntries(width, height, hasInsets, false);
                    }

                    if ((width >= 3840 || height >= 2160) && maxSupportedResW < 3840) {
                        maxSupportedResW = 3840;
                    }
                    else if ((width >= 2560 || height >= 1440) && maxSupportedResW < 2560) {
                        maxSupportedResW = 2560;
                    }
                    else if ((width >= 1920 || height >= 1080) && maxSupportedResW < 1920) {
                        maxSupportedResW = 1920;
                    }

                    if (candidate.getRefreshRate() > maxSupportedFps) {
                        maxSupportedFps = candidate.getRefreshRate();
                    }
                }

                // This must be called to do runtime initialization before calling functions that evaluate
                // decoder lists.
                MediaCodecHelper.initialize(getContext(), GlPreferences.readPreferences(requireContext()).glRenderer);

                MediaCodecInfo avcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/avc", -1);
                MediaCodecInfo hevcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/hevc", -1);

                if (avcDecoder != null) {
                    Range<Integer> avcWidthRange = avcDecoder.getCapabilitiesForType("video/avc").getVideoCapabilities().getSupportedWidths();

                    LimeLog.info("AVC supported width range: "+avcWidthRange.getLower()+" - "+avcWidthRange.getUpper());

                    // If 720p is not reported as supported, ignore all results from this API
                    if (avcWidthRange.contains(1280)) {
                        if (avcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                            maxSupportedResW = 3840;
                        }
                        else if (avcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                            maxSupportedResW = 1920;
                        }
                        else if (maxSupportedResW < 1280) {
                            maxSupportedResW = 1280;
                        }
                    }
                }

                if (hevcDecoder != null) {
                    Range<Integer> hevcWidthRange = hevcDecoder.getCapabilitiesForType("video/hevc").getVideoCapabilities().getSupportedWidths();

                    LimeLog.info("HEVC supported width range: "+hevcWidthRange.getLower()+" - "+hevcWidthRange.getUpper());

                    // If 720p is not reported as supported, ignore all results from this API
                    if (hevcWidthRange.contains(1280)) {
                        if (hevcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                            maxSupportedResW = 3840;
                        }
                        else if (hevcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                            maxSupportedResW = 1920;
                        }
                        else if (maxSupportedResW < 1280) {
                            maxSupportedResW = 1280;
                        }
                    }
                }

                LimeLog.info("Maximum resolution slot: "+maxSupportedResW);

                if (maxSupportedResW != 0) {
                    if (maxSupportedResW < 3840) {
                        // 4K is unsupported
                        removeEntryFromListAndSetValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_4K, PreferenceConfiguration.RES_1440P);
                    }
                    if (maxSupportedResW < 2560) {
                        // 1440p is unsupported
                        removeEntryFromListAndSetValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1440P, PreferenceConfiguration.RES_1080P);
                    }
                    if (maxSupportedResW < 1920) {
                        // 1080p is unsupported
                        removeEntryFromListAndSetValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1080P, PreferenceConfiguration.RES_720P);
                    }
                    // Never remove 720p
                }
            }
            else {
                // We can get the true metrics via the getRealMetrics() function (unlike the lies
                // that getWidth() and getHeight() tell to us).
                DisplayMetrics metrics = new DisplayMetrics();
                display.getRealMetrics(metrics);
                int width = Math.max(metrics.widthPixels, metrics.heightPixels);
                int height = Math.min(metrics.widthPixels, metrics.heightPixels);
                addNativeResolutionEntries(width, height, false, false);
            }

            if (!PreferenceConfiguration.readPreferences(this.getActivity()).unlockFps) {
                // We give some extra room in case the FPS is rounded down
                if (maxSupportedFps < 118) {
                    removeEntryFromListAndSetValue(PreferenceConfiguration.FPS_PREF_STRING, "120", "90");
                }
                if (maxSupportedFps < 88) {
                    // 1080p is unsupported
                    removeEntryFromListAndSetValue(PreferenceConfiguration.FPS_PREF_STRING, "90", "60");
                }
                // Never remove 30 FPS or 60 FPS
            }
            addNativeFrameRateEntry(maxSupportedFps, false);

            // Android L introduces the drop duplicate behavior of releaseOutputBuffer()
            // that the unlock FPS option relies on to not massively increase latency.
            findPreference(PreferenceConfiguration.UNLOCK_FPS_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    // HACK: We need to let the preference change succeed before reinitializing to ensure
                    // it's reflected in the new layout.
                    reloadSettings();

                    // Allow the original preference change to take place
                    return true;
                }
            });

            // Remove HDR preference for devices below Nougat
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                LimeLog.info("Excluding HDR toggle based on OS");
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_video_settings");
                category.removePreference(findPreference("checkbox_enable_hdr"));
            }
            else {
                Display.HdrCapabilities hdrCaps = display.getHdrCapabilities();
                Log.d("HDR CAP", display + "");
                // We must now ensure our display is compatible with HDR10
                boolean foundHdr10 = false;
                if (hdrCaps != null) {
                    // getHdrCapabilities() returns null on Lenovo Lenovo Mirage Solo (vega), Android 8.0
                    for (int hdrType : hdrCaps.getSupportedHdrTypes()) {
                        if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                            foundHdr10 = true;
                            break;
                        }
                    }
                }

                if (!foundHdr10) {
                    LimeLog.info("Excluding HDR toggle based on display capabilities");
                    PreferenceCategory category =
                            (PreferenceCategory) findPreference("category_video_settings");
                    category.removePreference(findPreference("checkbox_enable_hdr"));
                }
                else if (PreferenceConfiguration.isShieldAtvFirmwareWithBrokenHdr()) {
                    LimeLog.info("Disabling HDR toggle on old broken SHIELD TV firmware");
                    PreferenceCategory category =
                            (PreferenceCategory) findPreference("category_video_settings");
                    TwoStatePreference hdrPref = (TwoStatePreference) category.findPreference("checkbox_enable_hdr");
                    if (hdrPref != null) {
                        hdrPref.setEnabled(false);
                        hdrPref.setChecked(false);
                        hdrPref.setSummary("Update the firmware on your NVIDIA SHIELD Android TV to enable HDR");
                    }
                }
            }

            // Add a listener to the FPS and resolution preference
            // so the bitrate can be auto-adjusted
            findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences prefs = getPrefs();
                    String valueStr = (String) newValue;

                    // Detect if this value is the native resolution option
                    CharSequence[] values = ((ListPreference)preference).getEntryValues();
                    boolean isNativeRes = true;
                    for (int i = 0; i < values.length; i++) {
                        // Look for a match prior to the start of the native resolution entries
                        if (valueStr.equals(values[i].toString()) && i < nativeResolutionStartIndex) {
                            isNativeRes = false;
                            break;
                        }
                    }

                    // If this is native resolution, show the warning dialog
                    if (isNativeRes) {
                        Dialog.displayDialog(getActivity(),
                                getResources().getString(R.string.title_native_res_dialog),
                                getResources().getString(R.string.text_native_res_dialog),
                                false);
                    }

                    // Write the new bitrate value
                    resetBitrateToDefault(prefs, valueStr, null);

                    // Allow the original preference change to take place
                    return true;
                }
            });
            findPreference(PreferenceConfiguration.FPS_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences prefs = getPrefs();
                    String valueStr = (String) newValue;

                    // If this is native frame rate, show the warning dialog
                    CharSequence[] values = ((ListPreference)preference).getEntryValues();
                    if (nativeFramerateShown && values[values.length - 1].toString().equals(newValue.toString())) {
                        Dialog.displayDialog(getActivity(),
                                getResources().getString(R.string.title_native_fps_dialog),
                                getResources().getString(R.string.text_native_res_dialog),
                                false);
                    }

                    // Write the new bitrate value
                    resetBitrateToDefault(prefs, null, valueStr);

                    // Allow the original preference change to take place
                    return true;
                }
            });

            findPreference("checkbox_enable_perf_logging").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Boolean loggingEnabled = (Boolean) newValue;

                    if(!loggingEnabled) {
                        new PerformanceDataTracker().clearLogs(preference.getContext());
                    }

                    // Allow the original preference change to take place
                    return true;
                }
            });

            for (String key : new String[]{
                    PreferenceConfiguration.BLUR_AMOUNT_PREF_STRING,
                    PreferenceConfiguration.BG_QUALITY_PREF_STRING,
                    PreferenceConfiguration.BG_RESOLUTION_PERCENT_PREF_STRING,
                    PreferenceConfiguration.BLUR_RESOLUTION_PERCENT_PREF_STRING,
                    PreferenceConfiguration.UI_FRAMERATE_PREF_STRING,
                    PreferenceConfiguration.PORTAL_PARTICLE_BRIGHTNESS_PREF_STRING
            }) {
                Preference livePref = findPreference(key);
                if (livePref != null) {
                    livePref.setOnPreferenceChangeListener((preference, newValue) -> {
                        final View root = getView();
                        if (root != null) {
                            root.post(() -> UiHelper.refreshBackdrop(getActivity()));
                        }
                        return true;
                    });
                }
            }

            Preference _pref;
            _pref = findPreference("import_keyboard_file");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/json");
                        startActivityForResult(intent, READ_REQUEST_CODE);
                        return false;
                    }
                });
            }

            _pref = findPreference("import_special_button_file");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/json");
                        startActivityForResult(intent, READ_REQUEST_SPECIAL_CODE);
                        return false;
                    }
                });
            }

            _pref = findPreference("share_performance_logs");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Context context = preference.getContext();
                        PerformanceDataTracker tracker = new PerformanceDataTracker();
                        String logs = tracker.getLog(context);

                        if (logs == null || logs.trim().isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.toast_no_logs), Toast.LENGTH_SHORT).show();
                            return false;
                        }

                        String prefixMessage = context.getString(R.string.email_prefix_message);
                        String emailRecipient = context.getString(R.string.email_recipient);
                        String emailSubject = context.getString(R.string.email_subject);
                        String chooserTitle = context.getString(R.string.email_chooser_title);
                        String noEmailClientsMsg = context.getString(R.string.toast_no_email_clients);

                        try {
                            File cacheDir = context.getCacheDir();
                            File logFile = new File(cacheDir, "artemistics_logs.txt");
                            try (FileOutputStream fos = new FileOutputStream(logFile)) {
                                fos.write(logs.getBytes(StandardCharsets.UTF_8));
                            }

                            Uri logFileUri = FileProvider.getUriForFile(context,
                                    context.getPackageName() + ".fileprovider",
                                    logFile);

                            Intent emailIntent = new Intent(Intent.ACTION_SEND);
                            emailIntent.setType("text/plain");
                            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{emailRecipient});
                            emailIntent.putExtra(Intent.EXTRA_SUBJECT, emailSubject);
                            emailIntent.putExtra(Intent.EXTRA_TEXT, prefixMessage);
                            emailIntent.putExtra(Intent.EXTRA_STREAM, logFileUri);

                            emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                            context.startActivity(Intent.createChooser(emailIntent, chooserTitle));
                        } catch (IOException e) {
                            Log.d("PerformanceDataTracker", "Error creating log file");
                        } catch (android.content.ActivityNotFoundException ex) {
                            Toast.makeText(context, noEmailClientsMsg, Toast.LENGTH_SHORT).show();
                        }
                        return false;
                    }
                });
            }

            _pref = findPreference("export_keyboard_file");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        File file = new File(requireActivity().getExternalCacheDir(),"export_settings");
                        if(!file.exists()){
                            file.mkdir();
                        }
                        File file1= getJsonContent(requireActivity(),file);
                        if(file1==null){
                            Toast.makeText(requireActivity(),getString(R.string.pref_error_occurred),Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        Uri uri;
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        String authority= BuildConfig.APPLICATION_ID+".fileprovider";
                        uri = FileProvider.getUriForFile(requireActivity(),authority,file1);
                        intent.putExtra(Intent.EXTRA_STREAM, uri);
                        intent.setType("application/json");
                        startActivity(Intent.createChooser(intent,getString(R.string.pref_save_keyboard_profile)));
                        return false;
                    }
                });
            }

            refreshThemePreferenceEntries();
            _pref = findPreference(com.limelight.ui.AppThemePalette.PREF_COLOR_SCHEME);
            if (_pref != null) {
                _pref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String key = newValue != null ? newValue.toString() : com.limelight.ui.AppThemePalette.DEFAULT_THEME_KEY;
                    com.limelight.ui.AppThemePalette.onThemeKeySelected(requireActivity(), key);
                    requireView().post(() -> {
                        UiHelper.refreshBackdrop(requireActivity());
                        if (getActivity() instanceof StreamSettings) {
                            ((StreamSettings) getActivity()).applySettingsChrome();
                        }
                        androidx.recyclerview.widget.RecyclerView rv = getListView();
                        if (rv != null && rv.getAdapter() != null) {
                            rv.getAdapter().notifyDataSetChanged();
                        }
                    });
                    return true;
                });
            }

            _pref = findPreference("pref_custom_palette");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(requireActivity(), com.limelight.ui.ThemeStudioActivity.class);
                    UiHelper.startMenuActivityWithDepth(requireActivity(), intent);
                    return true;
                });
            }

            _pref = findPreference(PreferenceConfiguration.UI_PERFORMANCE_MODE_PREF_STRING);
            if (_pref != null) {
                _pref.setOnPreferenceChangeListener((preference, newValue) -> {
                    requireView().post(() -> {
                        UiHelper.refreshBackdrop(requireActivity());
                        androidx.recyclerview.widget.RecyclerView rv = getListView();
                        if (rv != null && rv.getAdapter() != null) {
                            rv.getAdapter().notifyDataSetChanged();
                        }
                    });
                    return true;
                });
            }

            _pref = findPreference(PreferenceConfiguration.UI_BATTERY_SAVER_PREF_STRING);
            if (_pref != null) {
                _pref.setOnPreferenceChangeListener((preference, newValue) -> {
                    requireView().post(() -> {
                        UiHelper.refreshBackdrop(requireActivity());
                        androidx.recyclerview.widget.RecyclerView rv = getListView();
                        if (rv != null && rv.getAdapter() != null) {
                            rv.getAdapter().notifyDataSetChanged();
                        }
                    });
                    return true;
                });
            }

            _pref = findPreference("option_edit_osc_layout");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(requireActivity(), com.limelight.OscLayoutEditorActivity.class);
                    UiHelper.startMenuActivityWithDepth(requireActivity(), intent);
                    return true;
                });
            }

            _pref = findPreference("pref_debug_info");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(@NonNull Preference preference) {
                        Intent intent = new Intent(requireActivity(), DebugInfoActivity.class);
                        UiHelper.startMenuActivityWithDepth(requireActivity(), intent);
                        return false;
                    }
                });
            }

            EditTextPreference bitrateEditPref = findPreference(PreferenceConfiguration.CUSTOM_BITRATE_PREF_STRING);
            if (bitrateEditPref != null) {
                bitrateEditPref.setOnBindEditTextListener((EditText editText) -> {
                    editText.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
                });

                bitrateEditPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String value = (String) newValue;
                    if (TextUtils.isEmpty(value)) {
                        Toast.makeText(getActivity(), getString(R.string.pref_enter_value_0_9999), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    float bitrateValue = Float.parseFloat(value) * 1000;
                    int bitrate = (int) bitrateValue;
                    SharedPreferences prefs = getPrefs();
                    prefs.edit().putInt(PreferenceConfiguration.BITRATE_PREF_STRING, bitrate).apply();
                    Toast.makeText(getActivity(), getString(R.string.pref_set_success), Toast.LENGTH_SHORT).show();
                    return true;
                });
            }

            EditTextPreference resolutionEditPref = findPreference(PreferenceConfiguration.CUSTOM_RESOLUTION_PREF_STRING);
            if (resolutionEditPref != null) {
                resolutionEditPref.setOnBindEditTextListener((EditText editText) -> {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT);
                    editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(11)});
                });

                resolutionEditPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String value = (String) newValue;
                    if (TextUtils.isEmpty(value)) {
                        Toast.makeText(getActivity(), getString(R.string.pref_enter_value_0_9999), Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    // Verify format: [width]x[height]
                    String[] resolutionSegments = value.split("x");
                    if (resolutionSegments.length != 2) {
                        Toast.makeText(getActivity(), getString(R.string.pref_error_occurred), Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    try {
                        int width = Integer.parseInt(resolutionSegments[0]);
                        int height = Integer.parseInt(resolutionSegments[1]);
                        
                        if (width <= 0 || height <= 0) {
                            Toast.makeText(getActivity(), getString(R.string.pref_error_occurred), Toast.LENGTH_SHORT).show();
                            return false;
                        }

                        // Save the value and reload settings
                        editAndReload(PreferenceConfiguration.CUSTOM_RESOLUTION_PREF_STRING, value);

                        return true;
                    } catch (NumberFormatException e) {
                        Toast.makeText(getActivity(), getString(R.string.pref_error_occurred), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                });
            }

            EditTextPreference customRefreshRatePref = findPreference(PreferenceConfiguration.CUSTOM_REFRESH_RATE_PREF_STRING);
            if (customRefreshRatePref != null) {
                customRefreshRatePref.setOnBindEditTextListener((EditText editText) -> {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
                });

                customRefreshRatePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String value = (String) newValue;
                    if (TextUtils.isEmpty(value)) {
                        Toast.makeText(getActivity(), getString(R.string.pref_enter_value_0_9999), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    
                    try {
                        float refreshRate = Float.parseFloat(value);
                        if (refreshRate <= 0) {
                            Toast.makeText(getActivity(), getString(R.string.pref_enter_value_0_9999), Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        
                        // Format to max 3 decimal places
                        String formattedValue = String.format("%.3f", refreshRate);
                        // Remove trailing zeros
                        formattedValue = formattedValue.replaceAll("0+$", "").replaceAll("\\.$", "");

                        editAndReload(PreferenceConfiguration.CUSTOM_REFRESH_RATE_PREF_STRING, formattedValue);

                        return true;
                    } catch (NumberFormatException e) {
                        Toast.makeText(getActivity(), getString(R.string.pref_error_occurred), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                });
            }

            // Start with all 15 categories cleanly collapsed in accordion mode
            captureBaseVisibilityAndCollapseAll();
        }

        private void removeEntryFromListAndSetValue(String resolutionPrefString, String entryToRemove, String nextDefault) {
            removeValue(resolutionPrefString, entryToRemove, new Runnable() {
                @Override
                public void run() {
                    SharedPreferences prefs = getPrefs();
                    setValue(resolutionPrefString, nextDefault);
                    resetBitrateToDefault(prefs, null, null);
                }
            });
        }

        private void editAndReload(String prefKey, String newVal) {
            SharedPreferences prefs = getPrefs();
            prefs.edit().putString(prefKey, newVal).apply();

            reloadSettings();
        }

        protected void reloadSettings() {
            // HACK: We need to let the preference change succeed before reinitializing to ensure
            // it's reflected in the new layout.
            final Handler h = new Handler();
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Ensure the activity is still open when this timeout expires
                    StreamSettings settingsActivity = (StreamSettings) SettingsFragment.this.getActivity();
                    if (settingsActivity != null) {
                        settingsActivity.reloadSettings();
                    }
                }
            }, 500);
        }

        int READ_REQUEST_CODE = 1001;
        int READ_REQUEST_SPECIAL_CODE = 1002;

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK && data.getData() != null) {
                try {
                    Uri uri = data.getData();
                    String json = FileUriUtils.openUriForRead(getActivity(), uri);
                    if (TextUtils.isEmpty(json)) {
                        Toast.makeText(getActivity(), getString(R.string.pref_empty_file), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String name = getPrefs().getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
                    SharedPreferences.Editor prefEditor = requireActivity().getSharedPreferences(name, Activity.MODE_PRIVATE).edit();
                    JSONObject object = new JSONObject(json);
                    Iterator it = object.keys();
                    prefEditor.clear();
                    while (it.hasNext()) {
                        String key = (String) it.next();// 获得key
                        String value = object.getString(key);// 获得value
                        prefEditor.putString(key, value);
                    }
                    prefEditor.apply();
                    Toast.makeText(getActivity(), getString(R.string.pref_import_success), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getActivity(), getString(R.string.pref_error_occurred) + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                return;
            }

            if (requestCode == READ_REQUEST_SPECIAL_CODE && resultCode == Activity.RESULT_OK && data.getData() != null) {
                try {
                    Uri uri = data.getData();
                    String json = FileUriUtils.openUriForRead(getActivity(), uri);
                    if (TextUtils.isEmpty(json)) {
                        Toast.makeText(getActivity(), getString(R.string.pref_empty_file), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SharedPreferences.Editor prefEditor = getActivity().getSharedPreferences(GameMenu.PREF_NAME, Activity.MODE_PRIVATE).edit();
                    prefEditor.putString(GameMenu.KEY_NAME, json);
                    prefEditor.apply();
                    Toast.makeText(getActivity(), getString(R.string.pref_import_success), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getActivity(), getString(R.string.pref_error_occurred) + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }

        @Override
        public void onDisplayPreferenceDialog(@NonNull Preference preference) {
            if (preference instanceof ConfirmDeleteOscPreference) {
                DialogFragment dialogFragment = ConfirmDeleteOscPreference.DialogFragmentCompat.newInstance(preference.getKey());
                dialogFragment.setTargetFragment(this, 0);
                dialogFragment.show(getFragmentManager(), null);
            } else if (preference instanceof ConfirmDeleteKeyboardPreference) {
                DialogFragment dialogFragment = ConfirmDeleteKeyboardPreference.DialogFragmentCompat.newInstance(preference.getKey());
                dialogFragment.setTargetFragment(this, 0);
                dialogFragment.show(getFragmentManager(), null);
            } else super.onDisplayPreferenceDialog(preference);
        }

        private File getJsonContent(Context context,File file){
            String name = getPrefs().getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
            SharedPreferences pref = context.getSharedPreferences(name, Activity.MODE_PRIVATE);
            Map<String,?> map = pref.getAll();
            File file1= new File(file,name+".json");
            String jsonStr=new Gson().toJson(map);
            if(!FileUriUtils.writerFileString(file1,jsonStr)){
                return null;
            }
            return file1;
        }

        //获取所有设置项配置文件
        private File getAllJsonData(File file){
            SharedPreferences pref = getPrefs();
            Map<String,?> map = pref.getAll();
            //获取适配电脑的数据库信息
//            List<ComputerDetails> map= new ComputerDatabaseManager(context).getAllComputers();
            File file1= new File(file,"allJSON.json");
            String jsonStr=new Gson().toJson(map);
            if(!FileUriUtils.writerFileString(file1,jsonStr)){
                return null;
            }
            return file1;
        }
    }
}

