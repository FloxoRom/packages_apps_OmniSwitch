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

import android.app.StatusBarManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;
import android.util.MathUtils;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.InputEvent;
import android.view.InputMonitor;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.internal.logging.InstanceId;
import com.android.internal.statusbar.ISessionListener;
import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.shared.system.InputChannelCompat;

import org.omnirom.omniswitch.R;
import org.omnirom.omniswitch.RecentTasksLoader;
import org.omnirom.omniswitch.SettingsActivity;
import org.omnirom.omniswitch.SwitchConfiguration;
import org.omnirom.omniswitch.SwitchManager;

import java.util.HashSet;

public class SwitchGestureView {
    private final static String TAG = "OmniSwitch:SwitchGestureView";
    private static final boolean DEBUG = false;

    private Context mContext;
    private WindowManager mWindowManager;
    private ImageView mDragButton;
    private FrameLayout mView;
    private float[] mInitDownPoint = new float[2];
    private boolean mShowing;
    private boolean mEnabled = true;
    private Drawable mDragHandleImage;
    private SwitchConfiguration mConfiguration;
    private SwitchManager mRecentsManager;
    private float mDetectSlop;
    private float mDefaultSlop;
    private float mLastX;
    private boolean mMoveStarted;
    private View.OnTouchListener mDragButtonListener;
    private InputMonitor mInputMonitor;
    private InputChannelCompat.InputEventReceiver mInputEventReceiver;
    private int[] mDragButtonLocation = new int[2];
    private boolean mSessionKeyguard;
    private final int mLongPressTimeout;
    private boolean mFlingOpen;
    private VelocityTracker mVelocityTracker;
    private float mTotalTouchDelta;

    private static final HashSet<String> mDragHandleShowSettings = new HashSet<>();

    static {
        mDragHandleShowSettings.add(SettingsActivity.PREF_DRAG_HANDLE_LOCATION);
        mDragHandleShowSettings.add(SettingsActivity.PREF_HANDLE_WIDTH);
        mDragHandleShowSettings.add(SettingsActivity.PREF_HANDLE_POS_START_RELATIVE);
        mDragHandleShowSettings.add(SettingsActivity.PREF_HANDLE_POS_END_RELATIVE);
        mDragHandleShowSettings.add(SettingsActivity.PREF_DRAG_HANDLE_COLOR_NEW);
        mDragHandleShowSettings.add(SettingsActivity.PREF_DRAG_HANDLE_DYNAMIC_COLOR);
    }

    private ISessionListener mSessionListener = new ISessionListener.Stub() {
        @Override
        public void onSessionStarted(int sessionType, InstanceId instance) {
            if (DEBUG) {
                Log.d(TAG, "onSessionStarted ");
            }
            mSessionKeyguard = true;
        }

        @Override
        public void onSessionEnded(int sessionType, InstanceId instance) {
            if (DEBUG) {
                Log.d(TAG, "onSessionEnded ");
            }
            mSessionKeyguard = false;
        }
    };

    public SwitchGestureView(SwitchManager manager, Context context) {
        mContext = context;
        mRecentsManager = manager;
        mWindowManager = (WindowManager) mContext
                .getSystemService(Context.WINDOW_SERVICE);
        mConfiguration = SwitchConfiguration.getInstance(mContext);
        ViewConfiguration vc = ViewConfiguration.get(context);
        mDefaultSlop = vc.getScaledTouchSlop();
        mDetectSlop = mDefaultSlop * 0.5f;
        mLongPressTimeout = ViewConfiguration.getLongPressTimeout();

        mDragHandleImage = mContext.getDrawable(R.drawable.drag_handle_shape);

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

                if (DEBUG) {
                    Log.d(TAG, "mDragButton onTouch " + action + ":" + (int) xRaw + ":" + (int) yRaw + " mEnabled=" + mEnabled +
                            " mMoveStarted=" + mMoveStarted);
                }

                if (!mEnabled) {
                    return true;
                }

                if (mVelocityTracker == null) {
                    mVelocityTracker = VelocityTracker.obtain();
                }
                mVelocityTracker.addMovement(event);

                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        mInputEventReceiver.setBatchingEnabled(false);
                        mMoveStarted = false;
                        mFlingOpen = false;
                        mTotalTouchDelta = 0;

                        mRecentsManager.clearTasks();
                        RecentTasksLoader.getInstance(mContext).cancelLoadingTasks();
                        RecentTasksLoader.getInstance(mContext).setSwitchManager(mRecentsManager);
                        RecentTasksLoader.getInstance(mContext).preloadTasks();

                        mInitDownPoint[0] = xRaw;
                        mInitDownPoint[1] = yRaw;
                        mLastX = xRaw;
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        mEnabled = true;
                        mMoveStarted = false;
                        mFlingOpen = false;
                        resetInitDownPoint();
                        mVelocityTracker.recycle();
                        mVelocityTracker = null;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (!isDownPointValid()) {
                            if (DEBUG) {
                                Log.d(TAG, "ACTION_MOVE ignored cause we dont have ACTION_DOWN");
                            }
                            return true;
                        }

                        float distanceX = mInitDownPoint[0] - xRaw;
                        float distanceY = mInitDownPoint[1] - yRaw;
                        float lastDistanceX  = mInitDownPoint[0] - mLastX;
                        float delta = Math.abs(distanceX) - Math.abs(lastDistanceX);
                        if (Math.abs(delta) > 0) {
                            if (Math.signum(delta) == Math.signum(mTotalTouchDelta)) {
                                mTotalTouchDelta += delta;
                            } else {
                                mTotalTouchDelta = delta;
                            }
                        }
                        if (DEBUG) {
                            Log.d(TAG, "ACTION_MOVE " + Math.abs(distanceX) + " " + Math.abs(distanceY) + " " + delta + " " + mTotalTouchDelta);
                        }

                        if (!mMoveStarted) {
                            if ((event.getEventTime() - event.getDownTime()) > mLongPressTimeout) {
                                if (DEBUG) {
                                    Log.d(TAG, "mDragButton cancel - long press");
                                }
                                cancelGesture(event);
                                return true;
                            }
                            if (Math.abs(distanceY) > Math.abs(distanceX) && Math.abs(distanceY) > mDefaultSlop) {
                                if (DEBUG) {
                                    Log.d(TAG, "mDragButton cancel - distanceY > mSlop");
                                }
                                cancelGesture(event);
                                return true;
                            }
                            if (Math.abs(distanceX) > Math.abs(distanceY) && Math.abs(distanceX) > mDetectSlop) {
                                if (DEBUG) {
                                    Log.d(TAG, "mMoveStarted " + distanceX + " " + distanceY);
                                }
                                mInputMonitor.pilferPointers();
                                mInputEventReceiver.setBatchingEnabled(true);

                                mMoveStarted = true;
                                mFlingOpen = true;
                                mRecentsManager.showHidden();
                            }
                        }
                        if (mMoveStarted) {
                            mVelocityTracker.computeCurrentVelocity(1000);
                            float xVelocity = mVelocityTracker.getXVelocity();
                            boolean isSlow = Math.abs(xVelocity) < 500;

                            if (DEBUG) {
                                Log.d(TAG, "xVelocity " + xVelocity + " isSlow = " + isSlow);
                            }
                            if (mConfiguration.mLocation == 0) {
                                mFlingOpen = xVelocity < 0 && !isSlow;
                            } else {
                                mFlingOpen = xVelocity > 0 && !isSlow;
                            }
                            if (DEBUG) {
                                Log.d(TAG, "mFlingOpen = " + mFlingOpen);
                            }
                            mRecentsManager.slideLayout(distanceX);
                        }
                        mLastX = xRaw;
                        break;
                    case MotionEvent.ACTION_UP:
                        if (!isDownPointValid()) {
                            if (DEBUG) {
                                Log.d(TAG, "ACTION_UP ignored cause we dont have ACTION_DOWN");
                            }
                        } else {
                            if (mMoveStarted) {
                                if (mFlingOpen) {
                                    mRecentsManager.openSlideLayout(true);
                                } else {
                                    if (mRecentsManager.finishSlideLayout()) {
                                        mRecentsManager.openSlideLayout(true);
                                    } else {
                                        mRecentsManager.canceSlideLayout(true);
                                    }
                                }
                            } else {
                                mRecentsManager.hideHidden();
                            }
                        }
                        mMoveStarted = false;
                        mFlingOpen = false;
                        resetInitDownPoint();
                        mVelocityTracker.recycle();
                        mVelocityTracker = null;
                        break;
                }
                return true;
            }
        };
        resetInitDownPoint();
        mView.addView(mDragButton, getDragHandleLayoutParamsSmall());
        updateDragHandleImage();
        mEnabled = true;
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
        if (mSessionKeyguard) return;
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
            if (isWithinInsets && mEnabled) {
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

    private void resetInitDownPoint() {
        mInitDownPoint[0] = -1f;
        mInitDownPoint[1] = -1f;
    }

    private boolean isDownPointValid() {
        return mInitDownPoint[0] != -1 && mInitDownPoint[1] != -1f;
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
        int currentHeight = mConfiguration.getCurrentOffsetEnd() - mConfiguration.getCurrentOffsetStart();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                mConfiguration.mDragHandleWidth,
                currentHeight,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);

        lp.gravity = getGravity();
        lp.y = mConfiguration.getCurrentOffsetStart();
        lp.setTrustedOverlay();
        lp.windowAnimations = 0;
        lp.privateFlags |=
                (WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS
                        | WindowManager.LayoutParams.PRIVATE_FLAG_EXCLUDE_FROM_SCREEN_MAGNIFICATION);
        lp.setTitle(TAG + mContext.getDisplayId());
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        return lp;
    }

    public WindowManager.LayoutParams getCustomParamsSmall(int height) {
        WindowManager.LayoutParams lp = getParamsSmall();
        lp.y = mConfiguration.getCurrentOffsetStart(height);
        return lp;
    }

    private FrameLayout.LayoutParams getDragHandleLayoutParamsSmall() {
        int currentHeight = mConfiguration.getCurrentOffsetEnd() - mConfiguration.getCurrentOffsetStart();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                mConfiguration.mDragHandleWidth,
                currentHeight);
        params.gravity = Gravity.CENTER;
        if (mConfiguration.mLocation == 0) {
            params.rightMargin = -mConfiguration.mDragHandleWidth / 2;
        } else {
            params.leftMargin = -mConfiguration.mDragHandleWidth / 2;
        }
        return params;
    }

    private void updateDragHandleImage() {
        if (DEBUG) {
            Log.d(TAG, "updateDragHandleImage");
        }

        mDragButton.setRotation(mConfiguration.mLocation == 0 ? 0f : 180f);
        mDragHandleImage.setTint(mConfiguration.getDragHandleColor());
        mDragButton.setImageDrawable(mDragHandleImage);
        mDragButton.setLayoutParams(getDragHandleLayoutParamsSmall());
    }

    public void updatePrefs(SharedPreferences prefs, String key) {
        if (DEBUG) {
            Log.d(TAG, "updatePrefs");
        }

        if (key != null) {
            if (mDragHandleShowSettings.contains(key)) {
                updateDragHandleImage();
                if (mConfiguration.mDragHandleShow) {
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
        initStatusBarSession();
        registerStatusBarSessionListener();

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
        unregisterStatusBarSessionListener();

        mShowing = false;
        mEnabled = false;
    }

    public void overlayShown() {
        if (DEBUG) {
            Log.d(TAG, "overlayShown");
        }
        mEnabled = false;
    }

    public void overlayHidden() {
        if (DEBUG) {
            Log.d(TAG, "overlayHidden");
        }
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
            // image layout params have changed
            updateDragHandleImage();
            // recalc next time needed
            resetDragButtonLocation();
        }
    }

    private boolean canDrawOverlayViews() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(mContext);
    }

    private void registerStatusBarSessionListener() {
        if (DEBUG) {
            Log.d(TAG, "registerSessionListener");
        }
        IStatusBarService service = IStatusBarService.Stub.asInterface(ServiceManager.getService("statusbar"));
        if (service != null) {
            try {
                service.registerSessionListener(StatusBarManager.SESSION_KEYGUARD, mSessionListener);
            } catch (RemoteException e) {
            }
        }
    }

    private void unregisterStatusBarSessionListener() {
        if (DEBUG) {
            Log.d(TAG, "unregisterSessionListener");
        }
        IStatusBarService service = IStatusBarService.Stub.asInterface(ServiceManager.getService("statusbar"));
        if (service != null) {
            try {
                service.unregisterSessionListener(StatusBarManager.SESSION_KEYGUARD, mSessionListener);
            } catch (RemoteException e) {
            }
        }
    }

    private void initStatusBarSession() {
        IStatusBarService service = IStatusBarService.Stub.asInterface(ServiceManager.getService("statusbar"));
        if (service != null) {
            try {
                mSessionKeyguard = service.getSessionStatus(StatusBarManager.SESSION_KEYGUARD);
                if (DEBUG) {
                    Log.d(TAG, "initStatusBarSession mSessionKeyguard = " + mSessionKeyguard);
                }
            } catch (RemoteException e) {
            }
        }
    }
}
