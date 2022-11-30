/*
 * Copyright (C) 2010 Daniel Nilsson
 * Copyright (C) 2012 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.omnirom.omniswitch.colorpicker;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.omnirom.omniswitch.R;

import java.util.Locale;

import androidx.appcompat.app.AlertDialog;

public class ColorSelectDialog extends AlertDialog implements
        ColorPickerView.OnColorChangedListener, TextWatcher, OnFocusChangeListener {

    private static final String TAG = "ColorSelectDialog";
    private final static String STATE_KEY_COLOR = "color";

    public static Integer[] CUSTOM_ACCENT_COLORS = new Integer[]{
            Color.parseColor("#a1c729"),
            Color.parseColor("#ff8000"),
            Color.parseColor("#a020f0"),
            Color.parseColor("#ff005a"),
            Color.parseColor("#e5141b"),
            Color.parseColor("#f0b50b"),
            Color.parseColor("#009ed8"),
            Color.parseColor("#00897b"),
    };

    private ColorPickerView mColorPicker;

    private EditText mHexColorInput;
    private ColorPanelView mNewColor;
    private LinearLayout mColorPanelView;
    private boolean mWithAlpha;
    private Context mContext;
    private ViewGroup mColorPresetView;
    private Integer[] mPresetColors;

    public ColorSelectDialog(Context context, int initialColor, boolean withAlpha, Integer[] presetColors) {
        super(context);
        mContext = context;
        mWithAlpha = withAlpha;
        mPresetColors = presetColors;
        init(initialColor);
    }

    private void init(int color) {
        // To fight color banding.
        getWindow().setFormat(PixelFormat.RGBA_8888);
        setUp(color);
    }

    /**
     * This function sets up the dialog with the proper values.  If the speedOff parameters
     * has a -1 value disable both spinners
     *
     * @param color - the color to set
     */
    private void setUp(int color) {
        View layout = getLayoutInflater().inflate(R.layout.color_select_dialog, null);

        mColorPicker = (ColorPickerView) layout.findViewById(R.id.color_picker_view);
        mHexColorInput = (EditText) layout.findViewById(R.id.hex_color_input);
        mNewColor = (ColorPanelView) layout.findViewById(R.id.color_panel);
        mColorPanelView = (LinearLayout) layout.findViewById(R.id.color_panel_view);
        mColorPresetView = layout.findViewById(R.id.color_preset_view);
        if (mPresetColors != null && mPresetColors.length != 0) {
            mColorPresetView.setVisibility(View.VISIBLE);
            for (Integer presetColor : mPresetColors) {
                Log.d(TAG, "presetColor = " + presetColor);
                View colorPresetItem = getLayoutInflater().inflate(R.layout.color_preset_item, null);
                colorPresetItem.findViewById(R.id.color_preset_item_view).setBackground(new ColorDrawable(presetColor));
                int colorPresetItemSize = getContext().getResources().getDimensionPixelSize(R.dimen.color_preset_item_size);
                if (getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(colorPresetItemSize, colorPresetItemSize);
                    colorPresetItem.setLayoutParams(lp);
                } else {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, colorPresetItemSize);
                    lp.weight = 0.1f;
                    colorPresetItem.setLayoutParams(lp);
                }
                mColorPresetView.addView(colorPresetItem);
                colorPresetItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int presetColor = ((ColorDrawable) v.findViewById(R.id.color_preset_item_view).getBackground()).getColor();
                        mColorPicker.setColor(presetColor, true);
                    }
                });
            }
        }

        mColorPicker.setOnColorChangedListener(this);
        mHexColorInput.setOnFocusChangeListener(this);
        setAlphaSliderVisible(mWithAlpha);
        mColorPicker.setColor(color, true);

        setView(layout);

        mColorPicker.setVisibility(View.VISIBLE);
        mColorPanelView.setVisibility(View.VISIBLE);
    }

    @Override
    public Bundle onSaveInstanceState() {
        Bundle state = super.onSaveInstanceState();
        state.putInt(STATE_KEY_COLOR, getColor());
        return state;
    }

    @Override
    public void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        mColorPicker.setColor(state.getInt(STATE_KEY_COLOR), true);
    }

    @Override
    public void onColorChanged(int color) {
        final boolean hasAlpha = mWithAlpha;
        final String format = hasAlpha ? "%08x" : "%06x";
        final int mask = hasAlpha ? 0xFFFFFFFF : 0x00FFFFFF;

        mNewColor.setColor(color);
        mHexColorInput.setText(String.format(Locale.US, format, color & mask));
    }

    public void setAlphaSliderVisible(boolean visible) {
        mHexColorInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(visible ? 8 : 6)});
        mColorPicker.setAlphaSliderVisible(visible);
    }

    public int getColor() {
        return mColorPicker.getColor();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        String hexColor = mHexColorInput.getText().toString();
        if (!hexColor.isEmpty()) {
            try {
                int color = Color.parseColor('#' + hexColor);
                if (!mWithAlpha) {
                    color |= 0xFF000000; // set opaque
                }
                mColorPicker.setColor(color);
                mNewColor.setColor(color);
            } catch (IllegalArgumentException ex) {
                // Number format is incorrect, ignore
            }
        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (!hasFocus) {
            mHexColorInput.removeTextChangedListener(this);
            InputMethodManager inputMethodManager = (InputMethodManager) getContext()
                    .getSystemService(Activity.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } else {
            mHexColorInput.addTextChangedListener(this);
        }
    }
}
