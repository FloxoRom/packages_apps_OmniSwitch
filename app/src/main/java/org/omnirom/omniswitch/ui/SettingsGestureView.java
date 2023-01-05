/*
 *  Copyright (C) 2013-2016 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.omnirom.omniswitch.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;

import org.omnirom.omniswitch.R;
import org.omnirom.omniswitch.SettingsActivity;
import org.omnirom.omniswitch.SwitchConfiguration;
import org.omnirom.omniswitch.SwitchService;
import org.omnirom.omniswitch.colorpicker.ColorSelectDialog;

import androidx.appcompat.app.AlertDialog;


public class SettingsGestureView implements DialogInterface.OnDismissListener {
    private static final String TAG = "OmniSwitch:SettingsGestureView";
    private WindowManager mWindowManager;
    private ImageView mDragButton;
    private ImageView mDragButtonStart;
    private ImageView mDragButtonEnd;
    private Button mOkButton;
    private Button mCancelButton;
    private Button mLocationButton;
    private Button mResetButton;
    private LinearLayout mView;
    private ViewGroup mDragHandleViewLeft;
    private ViewGroup mDragHandleViewRight;
    private Context mContext;
    private int mLocation = 0; // 0 = right 1 = left
    private boolean mShowing;
    private float mDensity;
    private int mStartY;
    private int mStartYRelative;
    private int mHandleHeight;
    private int mEndY;
    private int mColor;
    private LayerDrawable mDragHandle;
    private Drawable mDragHandleStart;
    private Drawable mDragHandleEnd;
    private SharedPreferences mPrefs;
    private float mDownY;
    private int mSlop;
    private int mDragHandleMinHeight;
    private int mDragHandleLimiterHeight;
    private int mDragHandleLimiterWidth;
    private SwitchConfiguration mConfiguration;
    private SeekBar mDragHandleWidthBar;
    private int mDragHandleWidth;
    private ImageView mDragHandleColorView;
    private View mDragHandleColorContainer;
    private Dialog mDialog;
    private Switch mDragHandleDynamicColor;
    private int mMoveStartY;
    private int mMoveEndY;

    public SettingsGestureView(Context context) {
        mContext = context;
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mDensity = mContext.getResources().getDisplayMetrics().density;
        ViewConfiguration vc = ViewConfiguration.get(mContext);
        mSlop = vc.getScaledTouchSlop();
        mConfiguration = SwitchConfiguration.getInstance(mContext);

        mDragHandleMinHeight = Math.round(80 * mDensity);
        mDragHandleWidth = mConfiguration.mDefaultDragHandleWidth;

        mDragHandle = (LayerDrawable) mContext.getDrawable(
                R.drawable.drag_handle_shape_settings);
        mDragHandleStart = mContext.getDrawable(
                R.drawable.drag_handle_marker_alt);
        mDragHandleEnd = mContext.getDrawable(
                R.drawable.drag_handle_marker_alt);
        mDragHandleLimiterHeight = mDragHandleEnd.getIntrinsicHeight();
        mDragHandleLimiterWidth = mDragHandleEnd.getIntrinsicWidth();

        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mView = (LinearLayout) inflater.inflate(R.layout.settings_gesture_view, null, false);

        mDragHandleViewLeft = mView.findViewById(R.id.drag_handle_view_left);
        mDragHandleViewRight = mView.findViewById(R.id.drag_handle_view_right);

        mOkButton = (Button) mView.findViewById(R.id.ok_button);
        mCancelButton = (Button) mView.findViewById(R.id.cancel_button);
        mLocationButton = (Button) mView.findViewById(R.id.location_button);
        mResetButton = (Button) mView.findViewById(R.id.reset_button);

        mDragButton = new ImageView(mContext);
        mDragButton.setOnTouchListener((v, event) -> {
            int action = event.getAction();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    mDownY = event.getRawY();
                    mMoveStartY = mStartY;
                    mMoveEndY = mEndY;
                    break;
                case MotionEvent.ACTION_UP:
                    break;
                case MotionEvent.ACTION_CANCEL:
                    mDownY = 0;
                    break;
                case MotionEvent.ACTION_MOVE:
                    int deltaY = (int) (event.getRawY() - mDownY);
                    if (Math.abs(deltaY) > mSlop) {
                        if ((mMoveEndY + deltaY < getLowerHandleLimit())
                                && (mMoveStartY + deltaY > getUpperHandleLimit())) {
                            mStartY = mMoveStartY + deltaY;
                            mEndY = mMoveEndY + deltaY;
                            updateDragHandleLayoutParams();
                        }
                    }
                    break;
            }
            return true;
        });

        mDragButtonStart = new ImageView(mContext);
        mDragButtonStart.setOnTouchListener((v, event) -> {
            int action = event.getAction();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    mDownY = event.getRawY();
                    break;
                case MotionEvent.ACTION_UP:
                    break;
                case MotionEvent.ACTION_CANCEL:
                    mDownY = 0;
                    break;
                case MotionEvent.ACTION_MOVE:
                    int deltaY = (int) (event.getRawY() - mDownY);
                    if (Math.abs(deltaY) > mSlop) {
                        if ((event.getRawY() < mEndY - mDragHandleMinHeight)
                                && (event.getRawY() - mDragHandleLimiterHeight > getUpperHandleLimit())) {
                            mStartY = (int) event.getRawY();
                            updateDragHandleLayoutParams();
                        }
                    }
                    break;
            }
            return true;
        });
        mDragButtonEnd = new ImageView(mContext);
        mDragButtonEnd.setOnTouchListener((v, event) -> {
            int action = event.getAction();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    mDownY = event.getRawY();
                    break;
                case MotionEvent.ACTION_UP:
                    break;
                case MotionEvent.ACTION_CANCEL:
                    mDownY = 0;
                    break;
                case MotionEvent.ACTION_MOVE:
                    int deltaY = (int) (event.getRawY() - mDownY);
                    if (Math.abs(deltaY) > mSlop) {
                        if ((event.getRawY() > mStartY + mDragHandleMinHeight)
                                && (event.getRawY() + mDragHandleLimiterHeight < getLowerHandleLimit())) {
                            mEndY = (int) event.getRawY();
                            updateDragHandleLayoutParams();
                        }
                    }
                    break;
            }
            return true;
        });

        mDragHandleWidthBar = (SeekBar) mView.findViewById(R.id.drag_handle_width);
        double min = mConfiguration.mDefaultDragHandleWidth * 0.5f;
        double max = mConfiguration.mDefaultDragHandleWidth * 2.0f;
        double value = mConfiguration.mDragHandleWidth;
        double progressValue = scaleValue(value, min, max, 1f, 100f);
        mDragHandleWidthBar.setProgress((int) progressValue);
        mDragHandleWidthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                double progressValue = seekBar.getProgress();
                // 20 = mConfiguration.mDefaultDragHandleWidth
                // max = 2,0 * mConfiguration.mDefaultDragHandleWidth
                // min = mConfiguration.mDefaultDragHandleWidth / 2
                // 1-100 -> 0.5-2.0 -> 10-40
                double scaleFactor = scaleValue(progressValue, 1f, 100f, 0.5f, 2.0f);
                mDragHandleWidth = (int) (mConfiguration.mDefaultDragHandleWidth * scaleFactor);
                updateDragHandleLayoutParams();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
            }
        });

        mOkButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mDialog != null) {
                    mDialog.dismiss();
                }
                Editor edit = mPrefs.edit();
                edit.putInt(SettingsActivity.PREF_DRAG_HANDLE_LOCATION, mLocation);
                int relHeight = mStartY / (mConfiguration.getCurrentDisplayHeight() / 100);
                edit.putInt(SettingsActivity.PREF_HANDLE_POS_START_RELATIVE, relHeight);
                int relHeightEnd = mEndY / (mConfiguration.getCurrentDisplayHeight() / 100);
                edit.putInt(SettingsActivity.PREF_HANDLE_POS_END_RELATIVE, relHeightEnd);
                edit.putInt(SettingsActivity.PREF_HANDLE_WIDTH, mDragHandleWidth);
                edit.putInt(SettingsActivity.PREF_DRAG_HANDLE_COLOR_NEW, mColor);
                edit.putBoolean(SettingsActivity.PREF_DRAG_HANDLE_DYNAMIC_COLOR, mDragHandleDynamicColor.isChecked());
                edit.commit();
                hide();
            }
        });

        mCancelButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mDialog != null) {
                    mDialog.dismiss();
                }
                hide();
            }
        });

        mLocationButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mLocation == 1) {
                    mLocation = 0;
                    mLocationButton.setText(mContext.getResources().getString(R.string.location_left));
                } else {
                    mLocation = 1;
                    mLocationButton.setText(mContext.getResources().getString(R.string.location_right));
                }
                updateLayout();
            }
        });

        mResetButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                resetPosition();
                resetColor();
            }
        });

        mView.setFocusableInTouchMode(true);
        mView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    hide();
                    return true;
                }
                return false;
            }
        });

        mDragHandleColorView = (ImageView) mView.findViewById(R.id.drag_handle_color);
        mDragHandleColorContainer = mView.findViewById(R.id.drag_handle_color_view);
        mDragHandleColorContainer.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mDialog != null && mDialog.isShowing()) return;
                mDialog = getColorDialog();
                mDialog.setOnDismissListener(SettingsGestureView.this);
                mDialog.show();
            }
        });

        mDragHandleDynamicColor = mView.findViewById(R.id.drag_handle_dynamic_color);
        mDragHandleDynamicColor.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mDragHandleColorContainer.setEnabled(!mDragHandleDynamicColor.isChecked());
                updateLayout();
            }
        });
        mDragHandleDynamicColor.setChecked(mPrefs.getBoolean(SettingsActivity.PREF_DRAG_HANDLE_DYNAMIC_COLOR, false));
        mDragHandleColorContainer.setEnabled(!mDragHandleDynamicColor.isChecked());
    }

    public WindowManager.LayoutParams getGesturePanelLayoutParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        lp.dimAmount = 0.6f;
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        return lp;
    }

    private void updateLayout() {
        mDragHandleViewLeft.removeAllViews();
        mDragHandleViewRight.removeAllViews();

        updateDragHandleImage();

        getDragHandleContainer().addView(mDragButton);
        getDragHandleContainer().addView(mDragButtonStart);
        getDragHandleContainer().addView(mDragButtonEnd);

        updateDragHandleLayoutParams();
    }

    private ViewGroup getDragHandleContainer() {
        if (mLocation == 1) {
            return mDragHandleViewLeft;
        } else {
            return mDragHandleViewRight;
        }
    }

    private void updateDragHandleLayoutParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                mDragHandleWidth,
                mEndY - mStartY);
        params.topMargin = mStartY;
        params.gravity = mLocation == 1 ? Gravity.LEFT : Gravity.RIGHT;
        if (mLocation == 1) {
            params.leftMargin = -mDragHandleWidth / 2;
        } else {
            params.rightMargin = -mDragHandleWidth / 2;
        }
        mDragButton.setLayoutParams(params);

        params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = mStartY - mDragHandleLimiterHeight / 2 + 2;
        params.gravity = mLocation == 1 ? Gravity.LEFT : Gravity.RIGHT;
        int dragButtonLimiterMargin = Math.max(0, (mDragHandleWidth / 2 - mDragHandleLimiterHeight) / 2);
        if (mLocation == 1) {
            params.leftMargin = dragButtonLimiterMargin;
        } else {
            params.rightMargin = dragButtonLimiterMargin;
        }
        mDragButtonStart.setLayoutParams(params);

        params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = mEndY - mDragHandleLimiterHeight / 2 - 2;
        params.gravity = mLocation == 1 ? Gravity.LEFT : Gravity.RIGHT;
        if (mLocation == 1) {
            params.leftMargin = dragButtonLimiterMargin;
        } else {
            params.rightMargin = dragButtonLimiterMargin;
        }
        mDragButtonEnd.setLayoutParams(params);

        mStartYRelative = mStartY / (mConfiguration.getCurrentDisplayHeight() / 100);
        mHandleHeight = mEndY - mStartY;
    }

    private void updateDragHandleImage() {
        Drawable d = mDragHandle;
        Drawable d1 = mDragHandleStart;
        Drawable d2 = mDragHandleEnd;

        mDragButton.setScaleType(ImageView.ScaleType.FIT_XY);
        mDragHandle.findDrawableByLayerId(R.id.drag_handle_shape).setTint(getDragHandleColor());

        mDragButton.setImageDrawable(mDragHandle);
        mDragButton.setRotation(mLocation == 1 ? 180 : 0);

        mDragButtonStart.setScaleType(ImageView.ScaleType.FIT_XY);
        mDragButtonStart.setImageDrawable(d1);
        mDragButtonStart.setRotation(mLocation == 1 ? 180 : 0);

        mDragButtonEnd.setScaleType(ImageView.ScaleType.FIT_XY);
        mDragButtonEnd.setImageDrawable(d2);
        mDragButtonEnd.setRotation(mLocation == 1 ? 180 : 0);
    }

    private void updateFromPrefs() {
        mStartY = mConfiguration.getCurrentOffsetStart();
        mEndY = mConfiguration.getCurrentOffsetEnd();
        mDragHandleWidth = mConfiguration.mDragHandleWidth;

        mLocation = mConfiguration.mLocation;
        if (mLocation == 1) {
            mLocationButton.setText(mContext.getResources().getString(R.string.location_right));
        } else {
            mLocationButton.setText(mContext.getResources().getString(R.string.location_left));
        }
        mColor = mConfiguration.mDragHandleColor;
        updateColorRect();
    }

    public void show() {
        if (mShowing) {
            return;
        }
        updateFromPrefs();
        updateLayout();

        mWindowManager.addView(mView, getGesturePanelLayoutParams());
        mShowing = true;

        Intent intent = new Intent(
                SwitchService.RecentsReceiver.ACTION_HANDLE_HIDE);
        mContext.sendBroadcast(intent);
    }

    public void hide() {
        if (!mShowing) {
            return;
        }

        mWindowManager.removeView(mView);
        mShowing = false;

        Intent intent = new Intent(
                SwitchService.RecentsReceiver.ACTION_HANDLE_SHOW);
        mContext.sendBroadcast(intent);
    }

    private void resetPosition() {
        mStartY = mConfiguration.getDefaultOffsetStart();
        mEndY = mConfiguration.getDefaultOffsetEnd();
        mDragHandleWidth = mConfiguration.mDefaultDragHandleWidth;
        mDragHandleWidthBar.setProgress(40);
        updateLayout();
    }

    private void resetColor() {
        mColor = mConfiguration.getDefaultDragHandleColor();
        updateColorRect();
        updateDragHandleImage();
    }

    public boolean isShowing() {
        return mShowing;
    }

    private int getLowerHandleLimit() {
        return mConfiguration.getCurrentDisplayHeight() - mConfiguration.mDragHandleBottomLimitPx;
    }

    private int getUpperHandleLimit() {
        return mConfiguration.mDragHandleTopLimitPx;
    }

    private double scaleValue(double value, double oldMin, double oldMax, double newMin, double newMax) {
        return ((value - oldMin) / (oldMax - oldMin)) * (newMax - newMin) + newMin;
    }

    private static ShapeDrawable createRectShape(int width, int height, int color) {
        ShapeDrawable shape = new ShapeDrawable(new RectShape());
        shape.setIntrinsicHeight(height);
        shape.setIntrinsicWidth(width);
        shape.getPaint().setColor(color);
        return shape;
    }

    private void updateColorRect() {
        final int width = (int) mContext.getResources().getDimension(R.dimen.color_button_width);
        final int height = (int) mContext.getResources().getDimension(R.dimen.color_button_height);
        mDragHandleColorView.setImageDrawable(createRectShape(width, height, mColor));
    }

    private Dialog getColorDialog() {
        final ColorSelectDialog d = new ColorSelectDialog(mContext, mColor, true, ColorSelectDialog.CUSTOM_ACCENT_COLORS);
        d.setButton(AlertDialog.BUTTON_POSITIVE,
                mContext.getResources().getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mColor = d.getColor();
                        updateColorRect();
                        updateDragHandleImage();
                    }
                });
        d.setButton(AlertDialog.BUTTON_NEUTRAL,
                mContext.getResources().getString(R.string.reset),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mColor = mConfiguration.getDefaultDragHandleColor();
                        updateColorRect();
                        updateDragHandleImage();
                        d.dismiss();
                    }
                });
        d.setButton(AlertDialog.BUTTON_NEGATIVE,
                mContext.getResources().getString(R.string.cancel),
                (DialogInterface.OnClickListener) null);
        // must be shown above TYPE_APPLICATION_OVERLAY
        d.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        return d;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        mDialog = null;
    }

    private int getDragHandleColor() {
        if (mDragHandleDynamicColor.isChecked()) {
            return mConfiguration.getSystemAccentColorWithAlpha(0.25f);
        }
        return mColor;
    }
}
