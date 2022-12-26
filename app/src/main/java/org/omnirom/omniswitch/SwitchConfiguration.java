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
package org.omnirom.omniswitch;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Surface;
import android.view.WindowManager;

import com.google.android.material.elevation.SurfaceColors;

import org.omnirom.omniswitch.launcher.Launcher;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SwitchConfiguration {
    private final static String TAG = "OmniSwitch:SwitchConfiguration";
    private static boolean DEBUG = false;

    public float mBackgroundOpacity = 0.7f;
    public boolean mDimBehind = true;
    public int mLocation = 0; // 0 = right 1 = left
    public boolean mAnimate = true;
    public int mIconSize = 60; // in dip
    public int mIconSizePx = 60;
    public int mQSActionSizePx = 60; // in px
    public int mActionSizePx = 48; // in px
    public int mOverlayIconSizeDp = 30;
    public int mOverlayIconSizePx = 30;
    public int mOverlayIconBorderDp = 2;
    public int mOverlayIconBorderPx = 2;
    public int mIconBorderDp = 10; // in dp
    public int mIconBorderPx;
    public float mDensity;
    public int mDensityDpi;
    public int mMaxWidth;
    public boolean mShowRambar = true;
    public int mStartYRelative;
    public int mDragHandleHeight;
    public int mDragHandleWidth;
    public int mDefaultDragHandleWidth;
    public boolean mShowLabels = true;
    public int mDragHandleColor;
    public boolean mDragHandleShow = true;
    public boolean mRestrictedMode;
    public int mLevelHeight; // in px
    public int mItemChangeWidthX; // in px - maximum value - can be lower if more items
    public int mThumbnailWidth; // in px
    public int mThumbnailHeight; // in px
    public Map<Integer, Boolean> mButtons;
    public float mLabelFontSize = 14f;
    public float mLabelFontSizeSp;
    public int mButtonPos = 1; // 0 = top 1 = bottom
    public List<String> mFavoriteList = new ArrayList<String>();
    public boolean mFilterActive = true;
    public boolean mFilterRunning;
    public long mFilterTime;
    public boolean mSideHeader = true;
    private static SwitchConfiguration mInstance;
    private WindowManager mWindowManager;
    public int mDefaultHandleHeight;
    private int mLabelFontSizePx;
    public int mMaxHeight;
    public int mMemDisplaySize;
    public int mLayoutStyle;
    public float mThumbSizeRatio = 1.0f;
    public IconSize mIconSizeDesc = IconSize.NORMAL;
    public BgStyle mBgStyle = BgStyle.SOLID_LIGHT;
    public boolean mLaunchStatsEnabled;
    public boolean mRevertRecents;
    public int mIconSizeQuickPx = 100;
    public boolean mDimActionButton;
    public List<String> mLockedAppList = new ArrayList<String>();
    public boolean mTopSortLockedApps;
    private Context mContext;
    public boolean mDynamicDragHandleColor;
    public boolean mBlockSplitscreenBreakers = true;
    public Set<String> mHiddenAppsList = new HashSet<String>();
    public Launcher mLauncher;
    public boolean mColorfulHeader;
    public boolean mBottomFavorites;
    public boolean mButtonHide;
    public int mShortcutIconSizeDp = 32;
    public int mVerticalSidebarPx;
    public int mHorizontalTopBottomPaddingPx;
    public int mHorizontalContentPaddingPx;
    public int mDragHandleBottomLimitPx;
    public int mDragHandleTopLimitPx;
    public int mThumbnnailOutlineRadiusPx;
    private int mRotation = Surface.ROTATION_0;

    // old pref slots
    private static final String PREF_DRAG_HANDLE_COLOR = "drag_handle_color";
    private static final String PREF_DRAG_HANDLE_OPACITY = "drag_handle_opacity";
    private static final String PREF_FLAT_STYLE = "flat_style";

    private List<OnSharedPreferenceChangeListener> mPrefsListeners = new ArrayList<OnSharedPreferenceChangeListener>();

    public enum IconSize {
        SMALL,
        NORMAL,
        LARGE
    }

    public enum BgStyle {
        TRANSPARENT,
        SOLID_LIGHT,
        SOLID_DARK,
        SOLID_SYSTEM
    }

    public static SwitchConfiguration getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new SwitchConfiguration(context);
        }
        return mInstance;
    }

    private SwitchConfiguration(Context context) {
        mContext = context;
        mWindowManager = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        setConfiguration(context);
        updatePrefs(PreferenceManager.getDefaultSharedPreferences(context), "");
    }

    public boolean onConfigurationChanged(Context context) {
        final float newDensity = context.getResources().getDisplayMetrics().density;
        final int newRotation = context.getDisplay().getRotation();
        if (DEBUG) {
            Log.d(TAG, "onConfigurationChanged " + mDensity + " " + newDensity + " " + mRotation + " " + newRotation);
        }
        if (newDensity != mDensity || newRotation != mRotation) {
            setConfiguration(context);
            return true;
        }
        return false;
    }

    private void setConfiguration(Context context) {
        mRotation =  context.getDisplay().getRotation();
        mDensity = context.getResources().getDisplayMetrics().density;
        mDensityDpi = context.getResources().getDisplayMetrics().densityDpi;
        if (DEBUG) {
            Log.d(TAG, "setConfiguration " + mDensity);
        }

        mDefaultHandleHeight = Math.round(100 * mDensity);
        mRestrictedMode = !hasSystemPermission(context);
        mLevelHeight = Math.round(80 * mDensity);
        mItemChangeWidthX = Math.round(40 * mDensity);
        mActionSizePx = Math.round(48 * mDensity);
        mQSActionSizePx = Math.round(60 * mDensity);
        mOverlayIconSizePx = Math.round(mOverlayIconSizeDp * mDensity);
        mOverlayIconBorderPx = Math.round(mOverlayIconBorderDp * mDensity);
        mIconSizeQuickPx = Math.round(100 * mDensity);
        mIconBorderPx = Math.round(mIconBorderDp * mDensity);
        // Render the default thumbnail background
        mThumbnailWidth = (int) context.getResources().getDimensionPixelSize(
                R.dimen.thumbnail_width);
        mThumbnailHeight = (int) context.getResources()
                .getDimensionPixelSize(R.dimen.thumbnail_height);
        mMemDisplaySize = (int) context.getResources().getDimensionPixelSize(
                R.dimen.ram_display_size);
        mLabelFontSizeSp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, mLabelFontSize, context.getResources().getDisplayMetrics());
        mVerticalSidebarPx = context.getResources().getDimensionPixelSize(R.dimen.vertical_bg_padding);
        mHorizontalTopBottomPaddingPx = context.getResources().getDimensionPixelSize(R.dimen.horizontal_bg_padding);
        mHorizontalContentPaddingPx = context.getResources().getDimensionPixelSize(R.dimen.horizontal_content_padding);
        mDragHandleBottomLimitPx = context.getResources().getDimensionPixelSize(R.dimen.drage_handle_bottom_limit);
        mDragHandleTopLimitPx = context.getResources().getDimensionPixelSize(R.dimen.drage_handle_top_limit);
        mThumbnnailOutlineRadiusPx = context.getResources().getDimensionPixelSize(R.dimen.thumbnail_outline_radius);
    }

    public void initDefaults(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.contains(SettingsActivity.PREF_BG_STYLE) &&
                prefs.contains(PREF_FLAT_STYLE)) {
            boolean flatStyle = prefs.getBoolean(PREF_FLAT_STYLE, true);
            prefs.edit().putString(SettingsActivity.PREF_BG_STYLE, flatStyle ? "0" : "1").commit();
        }
        if (!prefs.contains(SettingsActivity.PREF_DRAG_HANDLE_COLOR_NEW) &&
                prefs.contains(PREF_DRAG_HANDLE_COLOR)) {
            int dragHandleColor = prefs.getInt(PREF_DRAG_HANDLE_COLOR, getDefaultDragHandleColor());
            int opacity = prefs.getInt(PREF_DRAG_HANDLE_OPACITY, 100);
            dragHandleColor = (dragHandleColor & 0x00FFFFFF) + (opacity << 24);
            prefs.edit().putInt(SettingsActivity.PREF_DRAG_HANDLE_COLOR_NEW, dragHandleColor).commit();
        }
    }

    public void updatePrefs(SharedPreferences prefs, String key) {
        if (DEBUG) {
            Log.d(TAG, "updatePrefs");
        }
        mLocation = prefs.getInt(SettingsActivity.PREF_DRAG_HANDLE_LOCATION, 0);
        int opacity = prefs.getInt(SettingsActivity.PREF_OPACITY, 70);
        mBackgroundOpacity = (float) opacity / 100.0f;
        mAnimate = prefs.getBoolean(SettingsActivity.PREF_ANIMATE, true);
        String iconSize = prefs
                .getString(SettingsActivity.PREF_ICON_SIZE, String.valueOf(mIconSize));
        mIconSize = Integer.valueOf(iconSize);
        if (mIconSize == 60) {
            mIconSizeDesc = IconSize.NORMAL;
            mIconSize = 52;
        } else if (mIconSize == 80) {
            mIconSizeDesc = IconSize.LARGE;
            mIconSize = 70;
        } else {
            mIconSizeDesc = IconSize.SMALL;
        }
        mShowRambar = prefs.getBoolean(SettingsActivity.PREF_SHOW_RAMBAR, true);
        mShowLabels = prefs.getBoolean(SettingsActivity.PREF_SHOW_LABELS, true);

        int relHeightStart = (int) (getDefaultOffsetStart() / (getCurrentDisplayHeight() / 100));

        mStartYRelative = prefs
                .getInt(SettingsActivity.PREF_HANDLE_POS_START_RELATIVE,
                        relHeightStart);
        mDragHandleHeight = prefs.getInt(SettingsActivity.PREF_HANDLE_HEIGHT,
                mDefaultHandleHeight);

        mIconSizePx = Math.round(mIconSize * mDensity);
        mMaxWidth = Math.round((mIconSize + mIconBorderDp) * mDensity);
        mMaxHeight = Math.round((mIconSize + mIconBorderDp / 2) * mDensity);
        // add a small gap
        mLabelFontSizePx = Math.round((mLabelFontSize + mIconBorderDp) * mDensity);

        mDragHandleColor = prefs.getInt(
                SettingsActivity.PREF_DRAG_HANDLE_COLOR_NEW, getDefaultDragHandleColor());
        mDimBehind = prefs.getBoolean(SettingsActivity.PREF_DIM_BEHIND, false);

        mDefaultDragHandleWidth = Math.round(40 * mDensity);
        Utils.convertLegacyDragHandleSize(mContext, Math.round(20 * mDensity));
        mDragHandleWidth = prefs.getInt(
                SettingsActivity.PREF_HANDLE_WIDTH, mDefaultDragHandleWidth);

        mButtons = Utils.buttonStringToMap(prefs.getString(SettingsActivity.PREF_BUTTONS_NEW,
                SettingsActivity.PREF_BUTTON_DEFAULT_NEW), SettingsActivity.PREF_BUTTON_DEFAULT_NEW);
        String buttonPos = prefs.getString(SettingsActivity.PREF_BUTTON_POS, "1");
        mButtonPos = Integer.valueOf(buttonPos);

        String bgStyle = prefs.getString(SettingsActivity.PREF_BG_STYLE, "3");
        int bgStyleInt = Integer.valueOf(bgStyle);
        if (bgStyleInt == 0) {
            mBgStyle = BgStyle.SOLID_LIGHT;
        } else if (bgStyleInt == 1) {
            mBgStyle = BgStyle.TRANSPARENT;
        } else if (bgStyleInt == 3) {
            mBgStyle = BgStyle.SOLID_SYSTEM;
        } else {
            mBgStyle = BgStyle.SOLID_DARK;
        }

        Utils.convertLegacyAppLists(mContext);

        mFavoriteList.clear();
        String favoriteListString = prefs.getString(SettingsActivity.PREF_FAVORITE_APPS, "");
        Utils.parseCollection(favoriteListString, mFavoriteList);
        String filterTimeString = prefs.getString(SettingsActivity.PREF_APP_FILTER_TIME, "0");
        mFilterTime = Integer.valueOf(filterTimeString);
        if (mFilterTime != 0) {
            // value is in hours but we need millisecs
            mFilterTime = mFilterTime * 3600 * 1000;
        }
        String layoutStyle = prefs.getString(SettingsActivity.PREF_LAYOUT_STYLE, "1");
        mLayoutStyle = Integer.valueOf(layoutStyle);
        String thumbSize = prefs.getString(SettingsActivity.PREF_THUMB_SIZE, "1.0");
        mThumbSizeRatio = Float.valueOf(thumbSize);
        mFilterRunning = prefs.getBoolean(SettingsActivity.PREF_APP_FILTER_RUNNING, false);
        mLaunchStatsEnabled = prefs.getBoolean(SettingsActivity.PREF_LAUNCH_STATS, false);
        mRevertRecents = prefs.getBoolean(SettingsActivity.PREF_REVERT_RECENTS, false);
        mBottomFavorites = prefs.getBoolean(SettingsActivity.PREF_BOTTOM_FAVORITES, false);
        mDimActionButton = prefs.getBoolean(SettingsActivity.PREF_DIM_ACTION_BUTTON, false);
        mLockedAppList.clear();
        String lockedAppsListString = prefs.getString(SettingsActivity.PREF_LOCKED_APPS_LIST, "");
        Utils.parseLockedApps(lockedAppsListString, mLockedAppList);
        mTopSortLockedApps = prefs.getBoolean(SettingsActivity.PREF_LOCKED_APPS_SORT, false);
        mDynamicDragHandleColor = prefs.getBoolean(SettingsActivity.PREF_DRAG_HANDLE_DYNAMIC_COLOR, false);
        mBlockSplitscreenBreakers = prefs.getBoolean(SettingsActivity.PREF_BLOCK_APPS_ON_SPLITSCREEN, true);
        mColorfulHeader = prefs.getBoolean(SettingsActivity.PREF_COLOR_TASK_HEADER, false);
        mButtonHide = prefs.getBoolean(SettingsActivity.PREF_BUTTON_HIDE, false);
        mSideHeader = prefs.getBoolean(SettingsActivity.PREF_THUMB_HEADER_SIDE, true);

        mHiddenAppsList.clear();
        String hiddenListString = prefs.getString(SettingsActivity.PREF_HIDDEN_APPS, "");
        Utils.parseCollection(hiddenListString, mHiddenAppsList);

        for (OnSharedPreferenceChangeListener listener : mPrefsListeners) {
            if (DEBUG) {
                Log.d(TAG, "onSharedPreferenceChanged " + listener.getClass().getName());
            }
            listener.onSharedPreferenceChanged(prefs, key);
        }
    }

    public void resetDefaults(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().clear().commit();
    }

    public int getCurrentDisplayHeight() {
        return mWindowManager.getCurrentWindowMetrics().getBounds().height();
    }

    public int getCurrentDisplayWidth() {
        return mWindowManager.getCurrentWindowMetrics().getBounds().width();
    }

    public boolean isLandscape() {
        return getCurrentDisplayWidth() > getCurrentDisplayHeight();
    }

    public int getCurrentOverlayWidth() {
        if (isLandscape()) {
            // landscape
            return Math.max(mMaxWidth * 6,
                    (int) (getCurrentDisplayWidth() * 0.66f));
        }
        return getCurrentDisplayWidth();
    }

    public int getCurrentOffsetStart() {
        return (getCurrentDisplayHeight() / 100) * mStartYRelative;
    }

    public int getCurrentOffsetStart(int height) {
        return (height / 100) * mStartYRelative;
    }

    public int getCustomOffsetStart(int startYRelative) {
        return (getCurrentDisplayHeight() / 100) * startYRelative;
    }

    public int getDefaultOffsetStart() {
        return ((getCurrentDisplayHeight() / 2) - mDefaultHandleHeight / 2);
    }

    public int getCurrentOffsetEnd() {
        return getCurrentOffsetStart() + mDragHandleHeight;
    }

    public int getCustomOffsetEnd(int startYRelative, int handleHeight) {
        return getCustomOffsetStart(startYRelative) + handleHeight;
    }

    public int getDefaultOffsetEnd() {
        return getDefaultOffsetStart() + mDefaultHandleHeight;
    }

    private boolean hasSystemPermission(Context context) {
        int result = context
                .checkCallingOrSelfPermission(android.Manifest.permission.REMOVE_TASKS);
        return result == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    public int calcHorizontalDivider(boolean fullscreen) {
        int horizontalDividerWidth = 0;
        int width = fullscreen ? getCurrentDisplayWidth() : getCurrentOverlayWidth();
        int columnWidth = mMaxWidth;
        int numColumns = width / columnWidth;
        if (numColumns > 1) {
            int equalWidth = width / numColumns;
            if (equalWidth > columnWidth) {
                horizontalDividerWidth = equalWidth - columnWidth;
            }
        }
        return Math.max(horizontalDividerWidth, 10);
    }

    public int calcVerticalDivider(int height) {
        int verticalDividerHeight = 0;
        int numRows = height / getItemMaxHeight();
        if (numRows > 1) {
            int equalHeight = height / numRows;
            if (equalHeight > getItemMaxHeight()) {
                verticalDividerHeight = equalHeight - getItemMaxHeight();
            }
        }
        return Math.max(verticalDividerHeight, 10);
    }

    public int getItemMaxHeight() {
        return mShowLabels ? mMaxHeight + mLabelFontSizePx : mMaxHeight;
    }

    public int getOverlayHeaderWidth() {
        return mOverlayIconSizePx + 2 * mOverlayIconBorderPx;
    }

    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener prefsListener) {
        mPrefsListeners.add(prefsListener);
    }

    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener prefsListener) {
        mPrefsListeners.remove(prefsListener);
    }

    public int getLauncherViewWidth() {
        if (isLandscape()) {
            return (int) (getCurrentDisplayWidth() * 0.75f);
        }
        return getCurrentDisplayWidth();
    }

    public int getDragHandleColor() {
        if (mDynamicDragHandleColor) {
            return getSystemAccentColor();
        }
        return mDragHandleColor;
    }

    public int getDefaultDragHandleColor() {
        return mContext.getResources().getColor(R.color.default_drag_handle_color);
    }

    public int getSystemPrimaryColor() {
        return SurfaceColors.SURFACE_1.getColor(new ContextThemeWrapper(mContext, R.style.AppTheme));
    }

    public int getSystemPrimaryDarkColor() {
        return SurfaceColors.SURFACE_3.getColor(new ContextThemeWrapper(mContext, R.style.AppTheme));
    }

    public int getSystemPrimaryDarkerColor() {
        return SurfaceColors.SURFACE_5.getColor(new ContextThemeWrapper(mContext, R.style.AppTheme));
    }

    public int getLightPrimaryColor() {
        return SurfaceColors.SURFACE_1.getColor(new ContextThemeWrapper(mContext, R.style.AppThemeLight));
    }

    public int getLightPrimaryDarkColor() {
        return SurfaceColors.SURFACE_3.getColor(new ContextThemeWrapper(mContext, R.style.AppThemeLight));
    }

    public int getLightPrimaryDarkerColor() {
        return SurfaceColors.SURFACE_5.getColor(new ContextThemeWrapper(mContext, R.style.AppThemeLight));
    }

    public int getDarkPrimaryColor() {
        return SurfaceColors.SURFACE_1.getColor(new ContextThemeWrapper(mContext, R.style.AppThemeDark));
    }

    public int getDarkPrimaryDarkColor() {
        return SurfaceColors.SURFACE_3.getColor(new ContextThemeWrapper(mContext, R.style.AppThemeDark));
    }

    public int getDarkPrimaryDarkerColor() {
        return SurfaceColors.SURFACE_5.getColor(new ContextThemeWrapper(mContext, R.style.AppThemeDark));
    }

    public int getSystemAccentColor() {
        return mContext.getResources().getColor(R.color.colorPrimary);
    }

    public int getTaskHeaderColor() {
        return getTaskHeaderBackgroundColor();
    }

    public int getCurrentButtonTint(int color) {
        if (Utils.isBrightColor(color)) {
            return mContext.getResources().getColor(R.color.text_color_light);
        } else {
            return mContext.getResources().getColor(R.color.text_color_dark);
        }
    }

    public int getCurrentTextTint(int color) {
        return getCurrentButtonTint(color);
    }

    public int getButtonBackgroundColor() {
        if (mBgStyle == SwitchConfiguration.BgStyle.SOLID_LIGHT) {
            return getLightPrimaryDarkColor();
        } else if (mBgStyle == SwitchConfiguration.BgStyle.SOLID_DARK) {
            return getDarkPrimaryDarkColor();
        } else if (mBgStyle == SwitchConfiguration.BgStyle.SOLID_SYSTEM) {
            return getSystemPrimaryDarkColor();
        }
        return mContext.getResources().getColor(R.color.bg_transparent);
    }

    public int getBackgroundRipple() {
        if (mBgStyle == SwitchConfiguration.BgStyle.SOLID_LIGHT) {
            return R.drawable.ripple_dark;
        } else if (mBgStyle == SwitchConfiguration.BgStyle.SOLID_DARK) {
            return R.drawable.ripple_light;
        } else if (mBgStyle == SwitchConfiguration.BgStyle.SOLID_SYSTEM) {
            return R.drawable.ripple_system;
        }
        return R.drawable.ripple_light;
    }

    public int getViewBackgroundColor() {
        if (mBgStyle == SwitchConfiguration.BgStyle.SOLID_LIGHT) {
            return getLightPrimaryColor();
        } else if (mBgStyle == SwitchConfiguration.BgStyle.SOLID_DARK) {
            return getDarkPrimaryColor();
        } else if (mBgStyle == SwitchConfiguration.BgStyle.SOLID_SYSTEM) {
            return getSystemPrimaryColor();
        }
        return mContext.getResources().getColor(R.color.bg_transparent);
    }

    public int getTaskHeaderBackgroundColor() {
        if (mBgStyle == SwitchConfiguration.BgStyle.SOLID_LIGHT) {
            return getLightPrimaryDarkerColor();
        } else if (mBgStyle == SwitchConfiguration.BgStyle.SOLID_DARK) {
            return getDarkPrimaryDarkerColor();
        } else if (mBgStyle == SwitchConfiguration.BgStyle.SOLID_SYSTEM) {
            return getSystemPrimaryDarkerColor();
        }
        return mContext.getResources().getColor(R.color.bg_transparent);
    }

    public int getShadowColorValue() {
        if (mBgStyle == BgStyle.TRANSPARENT) {
            return 5;
        }
        return 0;
    }

    public int getPopupMenuStyle() {
        if (mBgStyle == BgStyle.SOLID_LIGHT) {
            return R.style.PopupMenuLight;
        } else if (mBgStyle == BgStyle.SOLID_DARK || mBgStyle == BgStyle.TRANSPARENT) {
            return R.style.PopupMenuDark;
        } else if (mBgStyle == BgStyle.SOLID_SYSTEM) {
            return R.style.PopupMenuSystem;
        }
        return R.style.PopupMenuLight;
    }

    private static SharedPreferences getPrefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static String getWeatherIconPack(Context context) {
        return getPrefs(context).getString(SettingsActivity.WEATHER_ICON_PACK_PREFERENCE_KEY, null);
    }

    public static boolean isShowAllDayEvents(Context context) {
        return getPrefs(context).getBoolean(SettingsActivity.SHOW_ALL_DAY_EVENTS_PREFERENCE_KEY, false);
    }

    public static boolean isShowToday(Context context) {
        return getPrefs(context).getBoolean(SettingsActivity.SHOW_TODAY_PREFERENCE_KEY, true);
    }

    public static int getEventDisplayPeriod(Context context) {
        return Integer.valueOf(getPrefs(context).getString(SettingsActivity.SHOW_EVENTS_PERIOD_PREFERENCE_KEY,
                context.getResources().getString(R.string.preferences_widget_days_default)));
    }

    public static boolean isShowEvents(Context context) {
        return getPrefs(context).getBoolean(SettingsActivity.SHOW_EVENTS_PREFERENCE_KEY, true);
    }

    public static boolean isShowWeather(Context context) {
        return getPrefs(context).getBoolean(SettingsActivity.SHOW_WEATHER_PREFERENCE_KEY, true);
    }

    public static boolean isTopSpaceReserved(Context context) {
        return isShowToday(context)
                || isShowWeather(context)
                || isShowEvents(context);
    }

    public static void backwardCompatibility(Context context) {
        final String SHOW_TOP_WIDGET_PREFERENCE_KEY = "pref_topWidget";
        SharedPreferences prefs = getPrefs(context);
        if (prefs.contains(SHOW_TOP_WIDGET_PREFERENCE_KEY)) {
            boolean value = prefs.getBoolean(SHOW_TOP_WIDGET_PREFERENCE_KEY, true);
            if (!value) {
                prefs.edit().putBoolean(SettingsActivity.SHOW_EVENTS_PREFERENCE_KEY, false).commit();
                prefs.edit().putBoolean(SettingsActivity.SHOW_WEATHER_PREFERENCE_KEY, false).commit();
                prefs.edit().putBoolean(SettingsActivity.SHOW_TODAY_PREFERENCE_KEY, false).commit();
            }
            prefs.edit().remove(SHOW_TOP_WIDGET_PREFERENCE_KEY).commit();
        }
    }
}
