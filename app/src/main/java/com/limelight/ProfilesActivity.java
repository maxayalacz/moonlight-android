package com.limelight;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.ImageButton;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.limelight.profiles.ProfilesAdapter;
import com.limelight.profiles.ProfilesManager;
import com.limelight.utils.UiHelper;

public class ProfilesActivity extends AppCompatActivity implements ProfilesManager.ProfileChangeListener {
    private ProfilesAdapter adapter;
    private RecyclerView recyclerView;
    private View emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profiles);
        UiHelper.notifyNewRootView(this);

        // Setup RecyclerView
        recyclerView = findViewById(R.id.profilesRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ProfilesAdapter(this);
        recyclerView.setAdapter(adapter);

        // Setup empty state
        emptyState = findViewById(R.id.emptyState);
        UiHelper.applyLiquidGlassEffect(emptyState);
        UiHelper.applyLiquidGlassEffect(findViewById(R.id.profilesDescription));

        // Setup FloatingActionButton
        FloatingActionButton fab = findViewById(R.id.addProfileFab);
        fab.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditProfileActivity.class);
            UiHelper.startMenuActivityWithDepth(this, intent);
        });

        // Register for profile changes
        ProfilesManager.getInstance().addListener(this);

        applyThemeColors();
        updateUI();
    }

    private void applyThemeColors() {
        com.limelight.ui.AppThemePalette palette = com.limelight.ui.AppThemePalette.getCurrent(this);
        int primary = palette.getPrimaryTextColor();
        int secondary = palette.getSecondaryTextColor();

        View desc = findViewById(R.id.profilesDescription);
        if (desc instanceof android.widget.TextView) {
            UiHelper.applyLiquidGlassEffect(desc);
            ((android.widget.TextView) desc).setTextColor(primary);
        }
        if (emptyState instanceof android.view.ViewGroup) {
            UiHelper.applyLiquidGlassEffect(emptyState);
            android.view.ViewGroup vg = (android.view.ViewGroup) emptyState;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child instanceof android.widget.TextView) {
                    ((android.widget.TextView) child).setTextColor(i == 0 ? primary : secondary);
                }
            }
        }
        FloatingActionButton fab = findViewById(R.id.addProfileFab);
        if (fab != null) {
            fab.setStateListAnimator(null);
            fab.setCompatElevation(0f);
            fab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    palette.getFrostedCardTint(0x78000000)));
            fab.setImageTintList(android.content.res.ColorStateList.valueOf(primary));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiHelper.refreshBackdrop(this);
        applyThemeColors();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        UiHelper.restoreMenuForegroundIfNeeded(this);
    }

    @Override
    public void onBackPressed() {
        UiHelper.finishMenuWithDepthTransition(this, super::onBackPressed);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        UiHelper.applyReachabilitySpacing(this);
        applyThemeColors();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ProfilesManager.getInstance().removeListener(this);
    }

    @Override
    public void onProfilesChanged() {
        runOnUiThread(this::updateUI);
    }

    private void updateUI() {
        int profileCount = ProfilesManager.getInstance().getProfiles().size();
        if (profileCount == 0) {
            recyclerView.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        }
        adapter.notifyDataSetChanged();
    }
}