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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Choreographer;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.InputEvent;
import android.view.InputMonitor;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.android.systemui.shared.system.InputChannelCompat;

import org.omnirom.omniswitch.PackageManager;
import org.omnirom.omniswitch.R;
import org.omnirom.omniswitch.RecentTasksLoader;
import org.omnirom.omniswitch.SettingsActivity;
import org.omnirom.omniswitch.SwitchConfiguration;
import org.omnirom.omniswitch.SwitchManager;
import org.omnirom.omniswitch.TaskDescription;
import org.omnirom.omniswitch.Utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class SwitchGestureView {
    private final static String TAG = "OmniSwitch:SwitchGestureView";
    private static final boolean DEBUG = false;

    private static final int FLIP_DURATION_DEFAULT = 200;

    private Context mContext;
    private WindowManager mWindowManager;
    private ImageView mDragButton;
    private FrameLayout mView;
    private float[] mDownPoint = new float[2];
    private float[] mInitDownPoint = new float[2];
    private boolean mShowing;
    private boolean mEnabled = true;
    private Drawable mDragHandleImage;
    private Drawable mDragHandleHiddenImage;
    private SwitchConfiguration mConfiguration;
    private boolean mHidden = true;
    private Handler mHandler;
    private SwitchManager mRecentsManager;
    private LinearInterpolator mLinearInterpolator = new LinearInterpolator();
    private Animator mToggleDragHandleAnim;
    private float mSlop;
    private boolean mFlingEnable = true;
    private float mLastX;
    private float mThumbRatio = 1.2f;
    private boolean mMoveStarted;
    private PackageTextView mLockToAppButton;
    private View.OnTouchListener mDragButtonListener;
    //private View.OnTouchListener mViewListener;
    private InputMonitor mInputMonitor;
    private InputChannelCompat.InputEventReceiver mInputEventReceiver;
    private int[] mDragButtonLocation = new int[2];

    private Set<String> mDragHandleShowSettings = Set.of(SettingsActivity.PREF_DRAG_HANDLE_LOCATION,
            SettingsActivity.PREF_HANDLE_HEIGHT,
            SettingsActivity.PREF_HANDLE_WIDTH,
            SettingsActivity.PREF_HANDLE_POS_START_RELATIVE,
            SettingsActivity.PREF_DRAG_HANDLE_COLOR_NEW,
            SettingsActivity.PREF_DRAG_HANDLE_DYNAMIC_COLOR);

    private GestureDetector mGestureDetector;
    private GestureDetector.OnGestureListener mGestureListener = new GestureDetector.OnGestureListener() {
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return false;
        }

        @Override
        public void onShowPress(MotionEvent e) {
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float distanceX = mInitDownPoint[0] - e2.getRawX();
            float distanceY = mInitDownPoint[1] - e2.getRawY();

            if (Math.abs(distanceY) > Math.abs(distanceX) && Math.abs(distanceY) > mSlop) {
                if (DEBUG) {
                    Log.d(TAG, "onFling cancel distanceY > mSlop");
                }
                return false;
            }
            if (Math.abs(distanceX) > Math.abs(distanceY) && Math.abs(distanceX) > mSlop * 2) {
                // this is an open only fling so velocityX must match
                if (mConfiguration.mLocation == 0) {
                    if (velocityX > 0) {
                        if (DEBUG) {
                            Log.d(TAG, "onFling cancel velocityX > 0");
                        }
                        return false;
                    }
                } else {
                    if (velocityX < 0) {
                        if (DEBUG) {
                            Log.d(TAG, "onFling cancel velocityX < 0");
                        }
                        return false;
                    }
                }
                if (DEBUG) {
                    Log.d(TAG, "onFling open " + velocityX);
                }
                mEnabled = false;
                mRecentsManager.openSlideLayout(true);
            }
            return false;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }
    };

    public SwitchGestureView(SwitchManager manager, Context context) {
        mContext = context;
        mRecentsManager = manager;
        mWindowManager = (WindowManager) mContext
                .getSystemService(Context.WINDOW_SERVICE);
        mConfiguration = SwitchConfiguration.getInstance(mContext);
        mHandler = new Handler();
        ViewConfiguration vc = ViewConfiguration.get(context);
        mSlop = vc.getScaledTouchSlop() * 0.5f;

        mGestureDetector = new GestureDetector(context, mGestureListener);
        mGestureDetector.setIsLongpressEnabled(false);

        mDragHandleImage = mContext.getDrawable(R.drawable.drag_handle_shape);
        mDragHandleHiddenImage = mContext.getDrawable(R.drawable.drag_handle_overlay_shape);

        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mView = (FrameLayout) inflater.inflate(R.layout.gesture_view, null, false);

        mDragButton = new ImageView(mContext);
        mDragButton.setScaleType(ImageView.ScaleType.FIT_XY);

        mDragButtonListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                float xRaw = event.getRawX();
                float yRaw = event.getRawY();
                float distanceX = mInitDownPoint[0] - xRaw;
                float distanceY = mInitDownPoint[1] - yRaw;

                if (DEBUG) {
                    Log.d(TAG, "mDragButton onTouch " + action + ":" + (int) xRaw + ":" + (int) yRaw + " mEnabled=" + mEnabled +
                            " mFlingEnable=" + mFlingEnable + " mMoveStarted=" + mMoveStarted);
                }
                if (mFlingEnable && !mHidden) {
                    mGestureDetector.onTouchEvent(event);
                }
                if (!mEnabled) {
                    return true;
                }
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        mFlingEnable = false;
                        mMoveStarted = false;

                        mRecentsManager.clearTasks();
                        RecentTasksLoader.getInstance(mContext).cancelLoadingTasks();
                        RecentTasksLoader.getInstance(mContext).setSwitchManager(mRecentsManager);
                        RecentTasksLoader.getInstance(mContext).preloadTasks();

                        mDownPoint[0] = xRaw;
                        mDownPoint[1] = yRaw;
                        mInitDownPoint[0] = xRaw;
                        mInitDownPoint[1] = yRaw;
                        mLastX = xRaw;
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        mEnabled = true;
                        mFlingEnable = false;
                        mMoveStarted = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (mHidden) {
                            return true;
                        }
                        if (DEBUG) {
                            Log.d(TAG, "ACTION_MOVE " + Math.abs(distanceX) + " " + Math.abs(distanceY));
                        }

                        if (!mMoveStarted) {
                            if (Math.abs(distanceY) > Math.abs(distanceX) && Math.abs(distanceY) > mSlop) {
                                if (DEBUG) {
                                    Log.d(TAG, "mDragButton cancel distanceY > mSlop");
                                }
                                cancelGesture(event);
                                return true;
                            }
                            if (Math.abs(distanceX) > Math.abs(distanceY) && Math.abs(distanceX) > mSlop) {
                                if (mLastX > xRaw) {
                                    // move left
                                    if (mConfiguration.mLocation == 0) {
                                        if (DEBUG) {
                                            Log.d(TAG, "mMoveStarted " + distanceX + " " + distanceY);
                                        }
                                        mFlingEnable = true;
                                        mMoveStarted = true;
                                        mRecentsManager.showHidden();
                                    }
                                } else {
                                    // move right
                                    if (mConfiguration.mLocation != 0) {
                                        if (DEBUG) {
                                            Log.d(TAG, "mMoveStarted " + distanceX + " " + distanceY);
                                        }
                                        mFlingEnable = true;
                                        mMoveStarted = true;
                                        mRecentsManager.showHidden();
                                    }
                                }
                            }
                            if (mMoveStarted) {
                                // Capture inputs
                                mInputMonitor.pilferPointers();
                            }
                        } else {
                            mRecentsManager.slideLayout(distanceX);
                        }
                        mLastX = xRaw;
                        break;
                    case MotionEvent.ACTION_UP:
                        mFlingEnable = false;

                        if (mMoveStarted) {
                            mRecentsManager.finishSlideLayout();
                        } else {
                            mRecentsManager.hideHidden();
                        }
                        mMoveStarted = false;
                        break;
                }
                return true;
            }
        };
        mView.addView(mDragButton, getDragHandleLayoutParamsSmall());

        updateButton(false);
    }

    private void disposeInputChannel() {
        if (mInputEventReceiver != null) {
            mInputEventReceiver.dispose();
            mInputEventReceiver = null;
        }
        if (mInputMonitor != null) {
            mInputMonitor.dispose();
            mInputMonitor = null;
        }
    }

    private void createInputChannel() {
        // Register input event receiver
        int mDisplayId = mContext.getDisplayId();
        mInputMonitor = InputManager.getInstance().monitorGestureInput(
                "omniswitch-drag-handle", mDisplayId);
        mInputEventReceiver = new InputChannelCompat.InputEventReceiver(
                mInputMonitor.getInputChannel(), Looper.getMainLooper(),
                Choreographer.getInstance(), this::onInputEvent);
    }

    private void onInputEvent(InputEvent ev) {
        if (!(ev instanceof MotionEvent)) return;
        MotionEvent event = (MotionEvent) ev;
        onMotionEvent(event);
    }

    private void onMotionEvent(MotionEvent ev) {
        if (mDragButtonLocation[0] == 0 && mDragButtonLocation[1] == 0) {
            updateDragButtonLocation();
        }
        if (mMoveStarted) {
            mDragButtonListener.onTouch(mDragButton, ev);
        } else {
            boolean isWithinInsets = isWithinDragButton((int) ev.getX(), (int) ev.getY());
            if (isWithinInsets) {
                mDragButtonListener.onTouch(mDragButton, ev);
            }
        }
    }

    private boolean isWithinDragButton(int x, int y) {
        if (y >= mDragButtonLocation[1]
                && x >= mDragButtonLocation[0]
                && y <= mDragButtonLocation[1] + mDragButton.getHeight()
                && x <= mDragButtonLocation[0] + mDragButton.getWidth()) {
            return true;
        }
        return false;
    }

    private void updateDragButtonLocation() {
        mDragButton.getLocationOnScreen(mDragButtonLocation);
        if (mConfiguration.mLocation == 1) {
            mDragButtonLocation[0] = mDragButtonLocation[0] - mDragButton.getWidth();
            mDragButtonLocation[1] = mDragButtonLocation[1] - mDragButton.getHeight();
        }
        if (DEBUG) {
            Log.d(TAG, "mDragButtonLocation = " + mDragButtonLocation[0] + ":" + mDragButtonLocation[1] + "x" + (mDragButtonLocation[0] + mDragButton.getWidth()) + ":" + (mDragButtonLocation[1] + mDragButton.getHeight()));
        }
    }

    private void resetDragButtonLocation() {
        mDragButtonLocation[0] = 0;
        mDragButtonLocation[1] = 0;
    }

    private void cancelGesture(MotionEvent ev) {
        // Send action cancel to reset all the touch events
        MotionEvent cancelEv = MotionEvent.obtain(ev);
        cancelEv.setAction(MotionEvent.ACTION_CANCEL);
        cancelEv.recycle();
    }

    private int getGravity() {
        if (mConfiguration.mLocation == 0) {
            return Gravity.RIGHT | Gravity.TOP;
        }
        if (mConfiguration.mLocation == 1) {
            return Gravity.LEFT | Gravity.TOP;
        }

        return Gravity.RIGHT | Gravity.TOP;
    }

    public WindowManager.LayoutParams getParamsSmall() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                mConfiguration.mDragHandleWidth,
                mConfiguration.mDragHandleHeight,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);

        lp.gravity = getGravity();
        lp.y = mConfiguration.getCurrentOffsetStart();
        lp.setTrustedOverlay();
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        return lp;
    }

    public WindowManager.LayoutParams getCustomParamsSmall(int height) {
        WindowManager.LayoutParams lp = getParamsSmall();
        lp.y = mConfiguration.getCurrentOffsetStart(height);
        return lp;
    }

    private FrameLayout.LayoutParams getDragHandleLayoutParamsSmall() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                mConfiguration.mDragHandleWidth,
                mConfiguration.mDragHandleHeight);
        params.gravity = Gravity.CENTER;
        if (mConfiguration.mLocation == 0) {
            params.rightMargin = -mConfiguration.mDragHandleWidth / 2;
        } else {
            params.leftMargin = -mConfiguration.mDragHandleWidth / 2;
        }
        return params;
    }

    private int getItemViewTopMargin() {
        return Math.max(0, (int) mInitDownPoint[1] - mConfiguration.mThumbnailHeight * 2);
    }

    private int getItemViewHeight() {
        return Math.round(300 * mConfiguration.mDensity);
    }

    public FrameLayout.LayoutParams getItemViewParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                mConfiguration.getCurrentDisplayWidth(),
                getItemViewHeight());
        lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        lp.topMargin = getItemViewTopMargin();
        return lp;
    }

    private void updateButton(boolean reload) {
        if (reload) {
            // to catch location/rotation changes
            updateDragHandleImage(false);
        }
        updateDragHandleImage(true);
    }

    private void colorizeDragHandleImage() {
        mDragHandleImage.setTint(mConfiguration.getDragHandleColor());
    }

    private void updateDragHandleImage(boolean shown) {
        if ((mHidden && !shown) || (!mHidden && shown)) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "updateDragHandleImage " + shown);
        }

        Drawable current = mDragHandleImage;

        mHidden = !shown;

        if (!shown) {
            current = mDragHandleHiddenImage;
        }
        toggleDragHandle(shown, current);
    }

    public void updatePrefs(SharedPreferences prefs, String key) {
        if (DEBUG) {
            Log.d(TAG, "updatePrefs");
        }

        if (key != null) {
            if (mDragHandleShowSettings.contains(key)) {
                if (mConfiguration.mDragHandleShow) {
                    colorizeDragHandleImage();
                    updateButton(true);
                    show();
                } else {
                    hide();
                }
            }
        }
    }

    public synchronized void show() {
        if (mShowing) {
            return;
        }
        // should never happen but make sure were not triggering a crash here
        if (!canDrawOverlayViews()) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "show");
        }
        mDragButton.setLayoutParams(getDragHandleLayoutParamsSmall());
        mWindowManager.addView(mView, getParamsSmall());

        createInputChannel();
        // recalc next time needed
        resetDragButtonLocation();

        mShowing = true;
        mEnabled = true;
    }

    public synchronized void hide() {
        if (!mShowing) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "hide");
        }
        mWindowManager.removeView(mView);

        disposeInputChannel();

        mShowing = false;
        mEnabled = false;
    }

    public void overlayShown() {
        if (DEBUG) {
            Log.d(TAG, "overlayShown");
        }
        updateDragHandleImage(false);
        mEnabled = false;
    }

    public void overlayHidden() {
        if (DEBUG) {
            Log.d(TAG, "overlayHidden");
        }
        updateDragHandleImage(true);

        mEnabled = true;
    }

    public boolean isShowing() {
        return mShowing;
    }

    public void updateDragHandlePosition(int height) {
        if (mShowing) {
            if (DEBUG) {
                Log.d(TAG, "updateLayout " + mConfiguration.getCurrentOffsetStart(height));
            }
            mWindowManager.updateViewLayout(mView, getCustomParamsSmall(height));
            // recalc next time needed
            resetDragButtonLocation();
        }
    }

    private Animator start(Animator a) {
        a.start();
        return a;
    }

    private Animator interpolator(TimeInterpolator ti, Animator a) {
        a.setInterpolator(ti);
        return a;
    }

    private void toggleDragHandle(final boolean show, final Drawable current) {
        if (mToggleDragHandleAnim != null) {
            mToggleDragHandleAnim.cancel();
        }

        mDragButton.setRotation(mConfiguration.mLocation == 0 ? 0f : 180f);

        if (show) {
            mDragButton.setTranslationX(mConfiguration.mLocation == 0 ? mConfiguration.mDragHandleWidth : -mConfiguration.mDragHandleWidth);
            mDragButton.setImageDrawable(current);
            mToggleDragHandleAnim = start(interpolator(mLinearInterpolator,
                    ObjectAnimator.ofFloat(mDragButton, View.TRANSLATION_X,
                            mConfiguration.mLocation == 0 ? mConfiguration.mDragHandleWidth :
                                    -mConfiguration.mDragHandleWidth,
                            0f))
                    .setDuration(FLIP_DURATION_DEFAULT));
        } else {
            mDragButton.setTranslationX(0f);
            mToggleDragHandleAnim = start(interpolator(mLinearInterpolator,
                    ObjectAnimator.ofFloat(mDragButton, View.TRANSLATION_X, 1f,
                            mConfiguration.mLocation == 0 ? mConfiguration.mDragHandleWidth :
                                    -mConfiguration.mDragHandleWidth))
                    .setDuration(FLIP_DURATION_DEFAULT));
        }

        mToggleDragHandleAnim.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!show) {
                    mDragButton.setImageDrawable(current);
                    mDragButton.setTranslationX(0f);
                }
            }

            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
    }

    private boolean canDrawOverlayViews() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(mContext);
    }
}
