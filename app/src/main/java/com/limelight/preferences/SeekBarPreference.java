package com.limelight.preferences;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.limelight.R;
import com.limelight.utils.UiHelper;

import java.util.Locale;

// Based on a Stack Overflow example: http://stackoverflow.com/questions/1974193/slider-on-my-preferencescreen
public class SeekBarPreference extends Preference
{
    private static final String ANDROID_SCHEMA_URL = "http://schemas.android.com/apk/res/android";
    private static final String SEEKBAR_SCHEMA_URL = "http://schemas.moonlight-stream.com/apk/res/seekbar";

    private AlertDialog dialog;
    private SeekBar seekBar;
    private TextView valueText;
    private final Context context;

    private final String dialogMessage;
    private final String suffix;
    private final int defaultValue;
    private final int maxValue;
    private final int minValue;
    private final int stepSize;
    private final int keyStepSize;
    private final int divisor;
    private int currentValue;

    private final int seekbarMax;

    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;

        // Read the message from XML
        int dialogMessageId = attrs.getAttributeResourceValue(ANDROID_SCHEMA_URL, "dialogMessage", 0);
        if (dialogMessageId == 0) {
            dialogMessage = attrs.getAttributeValue(ANDROID_SCHEMA_URL, "dialogMessage");
        }
        else {
            dialogMessage = context.getString(dialogMessageId);
        }

        // Get the suffix for the number displayed in the dialog
        int suffixId = attrs.getAttributeResourceValue(ANDROID_SCHEMA_URL, "text", 0);
        if (suffixId == 0) {
            suffix = attrs.getAttributeValue(ANDROID_SCHEMA_URL, "text");
        }
        else {
            suffix = context.getString(suffixId);
        }

        // Get default, min, and max seekbar values
        defaultValue = attrs.getAttributeIntValue(ANDROID_SCHEMA_URL, "defaultValue", PreferenceConfiguration.getDefaultBitrate(context));
        maxValue = attrs.getAttributeIntValue(ANDROID_SCHEMA_URL, "max", 100);
        minValue = attrs.getAttributeIntValue(SEEKBAR_SCHEMA_URL, "min", 1);
        stepSize = attrs.getAttributeIntValue(SEEKBAR_SCHEMA_URL, "step", 1);
        divisor = attrs.getAttributeIntValue(SEEKBAR_SCHEMA_URL, "divisor", 1);
        keyStepSize = attrs.getAttributeIntValue(SEEKBAR_SCHEMA_URL, "keyStep", 0);
        seekbarMax = maxValue - minValue;
    }

    protected AlertDialog getDialog() {
        if (dialog != null) {
            return dialog;
        }

        LinearLayout.LayoutParams params;
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(6, 6, 6, 6);

        TextView splashText = new TextView(context);
        splashText.setPadding(30, 10, 30, 10);
        if (dialogMessage != null) {
            splashText.setText(dialogMessage);
        }
        layout.addView(splashText);

        valueText = new TextView(context);
        valueText.setGravity(Gravity.CENTER_HORIZONTAL);
        valueText.setTextSize(32);
        // Default text for value; hides bug where OnSeekBarChangeListener isn't called when opacity is 0%
        valueText.setText("0%");
        params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layout.addView(valueText, params);

        seekBar = new SeekBar(context);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int value, boolean b) {
                value += minValue;
                if (value < minValue) {
                    seekBar.setProgress(0);
                    return;
                }

                int roundedValue = Math.round((float)value / stepSize) * stepSize;
                if (roundedValue != value) {
                    seekBar.setProgress(roundedValue - minValue);
                    return;
                }

                String t;
                if (divisor != 1) {
                    float floatValue = roundedValue / (float)divisor;
                    t = String.format((Locale)null, "%.1f", floatValue);
                }
                else {
                    t = String.valueOf(value);
                }
                valueText.setText(suffix == null ? t : t.concat(suffix.length() > 1 ? " "+suffix : suffix));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        layout.addView(seekBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        applyNeonSliderStyle();

        if (shouldPersist()) {
            currentValue = getPersistedInt(defaultValue);
        }

        seekBar.setMax(seekbarMax);
        if (keyStepSize != 0) {
            seekBar.setKeyProgressIncrement(keyStepSize);
        }
        seekBar.setProgress(currentValue - minValue);

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);
        dialogBuilder.setTitle(getTitle());
        dialogBuilder.setView(layout);

        dialogBuilder.setPositiveButton("OK", (dialog, which) -> {
            if (shouldPersist()) {
                currentValue = seekBar.getProgress() + minValue;
                persistInt(currentValue);
                callChangeListener(currentValue);
            }

            dialog.dismiss();
        });
        dialogBuilder.setNegativeButton(context.getString(R.string.cancel), (dialog, which) -> dialog.dismiss());

        dialog = dialogBuilder.create();
        UiHelper.styleDialogSquircle(dialog);
        return dialog;
    }

    private void applyNeonSliderStyle() {
        if (seekBar == null || valueText == null) {
            return;
        }
        com.limelight.ui.AppThemePalette palette = com.limelight.ui.AppThemePalette.getCurrent(context);
        int neonAccent = palette.getNeonAccent();

        valueText.setTextColor(neonAccent);
        valueText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        valueText.setShadowLayer(10f, 0f, 0f, com.limelight.ui.AppThemePalette.withAlpha(neonAccent, 0.55f));

        int trackH = Math.max(4, Math.round(UiHelper.dpToPx(context, 4f)));
        float trackR = trackH * 0.5f;

        android.graphics.drawable.GradientDrawable bgTrack = new android.graphics.drawable.GradientDrawable();
        bgTrack.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bgTrack.setCornerRadius(trackR);
        bgTrack.setColor(0x2EFFFFFF);
        bgTrack.setSize(-1, trackH);

        android.graphics.drawable.GradientDrawable activeTrack = new android.graphics.drawable.GradientDrawable();
        activeTrack.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        activeTrack.setCornerRadius(trackR);
        activeTrack.setColor(neonAccent);
        activeTrack.setSize(-1, trackH);

        android.graphics.drawable.ClipDrawable progressClip = new android.graphics.drawable.ClipDrawable(
                activeTrack, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL);

        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{bgTrack, progressClip});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            layers.setLayerHeight(0, trackH);
            layers.setLayerHeight(1, trackH);
            layers.setLayerGravity(0, Gravity.CENTER_VERTICAL);
            layers.setLayerGravity(1, Gravity.CENTER_VERTICAL);
        }

        int thumbSize = Math.round(UiHelper.dpToPx(context, 18f));
        android.graphics.drawable.GradientDrawable thumb = new android.graphics.drawable.GradientDrawable();
        thumb.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        thumb.setSize(thumbSize, thumbSize);
        thumb.setColor(neonAccent);
        thumb.setStroke(Math.max(2, Math.round(UiHelper.dpToPx(context, 2f))), 0xFFFFFFFF);

        seekBar.setProgressDrawable(layers);
        seekBar.setThumb(thumb);
        seekBar.setThumbOffset(0);
        seekBar.setSplitTrack(false);
        int padH = Math.round(UiHelper.dpToPx(context, 18f));
        int padV = Math.round(UiHelper.dpToPx(context, 14f));
        seekBar.setPadding(padH, padV, padH, padV);
    }

    protected void updateSeekbar() {
        seekBar.setMax(seekbarMax);
        if (keyStepSize != 0) {
            seekBar.setKeyProgressIncrement(keyStepSize);
        }
        seekBar.setProgress(currentValue - minValue);
    }

    @Override
    protected void onSetInitialValue(boolean restore, Object defaultValue)
    {
        super.onSetInitialValue(restore, defaultValue);
        if (restore) {
            currentValue = shouldPersist() ? getPersistedInt(this.defaultValue) : 0;
        }
        else {
            currentValue = (Integer) defaultValue;
        }
    }

    public void setProgress(int progress) {
        this.currentValue = progress;
        if (seekBar != null) {
            seekBar.setProgress(progress - minValue);
        }
    }
    public int getProgress() {
        return currentValue + minValue;
    }

    public void showDialog() {
        AlertDialog dialog = getDialog();
        applyNeonSliderStyle();
        updateSeekbar();
        UiHelper.styleDialogSquircle(dialog);
        dialog.show();
        UiHelper.styleDialogSquircle(dialog);
    }

    @Override
    protected void onClick() {
        super.onClick();
        showDialog();
    }
}
