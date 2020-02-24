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

import java.util.List;

import org.omnirom.omniswitch.PackageManager;
import org.omnirom.omniswitch.R;
import org.omnirom.omniswitch.SettingsActivity;
import org.omnirom.omniswitch.SwitchConfiguration;
import org.omnirom.omniswitch.SwitchManager;
import org.omnirom.omniswitch.TaskDescription;
import org.omnirom.omniswitch.Utils;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SwitchLayout extends AbstractSwitchLayout {
    private HorizontalListView mRecentListHorizontal;
    private FavoriteViewHorizontal mFavoriteListHorizontal;
    private RecentListAdapter mRecentListAdapter;
    private LinearColorBar mRamUsageBar;
    private TextView mBackgroundProcessText;
    private TextView mForegroundProcessText;
    private LinearLayout mRamUsageBarContainer;
    private HorizontalScrollView mButtonList;
    protected Runnable mUpdateRamBarTask;
    private FrameLayout mRecentsOrAppDrawer;
    private int mCurrentHeight;

    private class RecentListAdapter extends ArrayAdapter<TaskDescription> {

        public RecentListAdapter(Context context, int resource,
                List<TaskDescription> values) {
            super(context, R.layout.package_item, resource, values);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TaskDescription ad = getItem(position);

            PackageTextView item = null;
            if (convertView == null) {
                item = getPackageItemTemplate();
            } else {
                item = (PackageTextView) convertView;
            }
            item.setTask(ad);
            item.setTaskInfo(mConfiguration);
            if (ad.isLocked()) {
                item.setTextColor(Color.WHITE);
            } else {
                item.setTextColor(mConfiguration.getCurrentTextTint(mConfiguration.getViewBackgroundColor()));
            }
            return item;
        }
    }

    public SwitchLayout(SwitchManager manager, Context context) {
        super(manager, context);
        mRecentListAdapter = new RecentListAdapter(mContext,
                android.R.layout.simple_list_item_single_choice,
                mRecentsManager.getTasks());
        // default on first start
        mShowFavorites = mPrefs.getBoolean(SettingsActivity.PREF_SHOW_FAVORITE,
                false);

        mUpdateRamBarTask = new Runnable() {
            @Override
            public void run() {
                final ActivityManager am = (ActivityManager) mContext
                        .getSystemService(Context.ACTIVITY_SERVICE);
                MemoryInfo memInfo = new MemoryInfo();
                am.getMemoryInfo(memInfo);

                long availMem = memInfo.availMem;
                long totalMem = memInfo.totalMem;

                String sizeStr = Formatter.formatShortFileSize(mContext,
                        totalMem - availMem);
                mForegroundProcessText.setText(mContext.getResources()
                        .getString(R.string.service_foreground_processes,
                                sizeStr));
                sizeStr = Formatter.formatShortFileSize(mContext, availMem);
                mBackgroundProcessText.setText(mContext.getResources()
                        .getString(R.string.service_background_processes,
                                sizeStr));

                float fTotalMem = totalMem;
                float fAvailMem = availMem;
                mRamUsageBar.setRatios((fTotalMem - fAvailMem) / fTotalMem, 0,
                        0);
            }
        };
    }

    @Override
    protected synchronized void createView() {
        mView = mInflater
                .inflate(R.layout.recents_list_horizontal, null, false);

        mRecents = (LinearLayout) mView.findViewById(R.id.recents);

        mRecentListHorizontal = (HorizontalListView) mView
                .findViewById(R.id.recent_list_horizontal);

        mNoRecentApps = (TextView) mView.findViewById(R.id.no_recent_apps);

        mRecentListHorizontal.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                TaskDescription task = mRecentsManager.getTasks().get(position);
                mRecentsManager.switchTask(task, mAutoClose, false);
            }
        });

        mRecentListHorizontal
                .setOnItemLongClickListener(new OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(AdapterView<?> parent,
                            View view, int position, long id) {
                        TaskDescription task = mRecentsManager.getTasks().get(
                                position);
                        handleLongPressRecent(task, view);
                        return true;
                    }
                });

        SwipeDismissHorizontalListViewTouchListener touchListener = new SwipeDismissHorizontalListViewTouchListener(
                mRecentListHorizontal,
                new SwipeDismissHorizontalListViewTouchListener.DismissCallbacks() {
                    public void onDismiss(HorizontalListView listView,
                            int[] reverseSortedPositions) {
                        Log.d(TAG, "onDismiss: "
                                + mRecentsManager.getTasks().size() + ":"
                                + reverseSortedPositions[0]);
                        try {
                            TaskDescription ad = mRecentsManager.getTasks()
                                    .get(reverseSortedPositions[0]);
                            mRecentsManager.killTask(ad, false);
                        } catch (IndexOutOfBoundsException e) {
                            // ignored
                        }
                    }

                    @Override
                    public boolean canDismiss(int position) {
                        if (position < mRecentsManager.getTasks().size()) {
                            TaskDescription ad = mRecentsManager.getTasks().get(position);
                            /*if (ad.isLocked()) {
                                return false;
                            }*/
                            return true;
                        }
                        return false;
                    }
                });

        mRecentListHorizontal.setSwipeListener(touchListener);
        mRecentListHorizontal.setAdapter(mRecentListAdapter);

        mOpenFavorite = (ImageView) mView.findViewById(R.id.openFavorites);

        mOpenFavorite.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleFavorites();
            }
        });

        mOpenFavorite.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(
                        mContext,
                        mContext.getResources().getString(
                                R.string.open_favorite_help),
                        Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        mFavoriteListHorizontal = (FavoriteViewHorizontal) mView
                .findViewById(R.id.favorite_list_horizontal);
        mFavoriteListHorizontal.setRecentsManager(mRecentsManager);
        mFavoriteListHorizontal.init();

        mRamUsageBarContainer = (LinearLayout) mView
                .findViewById(R.id.ram_usage_bar_container);
        mRamUsageBar = (LinearColorBar) mView.findViewById(R.id.ram_usage_bar);
        mForegroundProcessText = (TextView) mView
                .findViewById(R.id.foregroundText);
        mBackgroundProcessText = (TextView) mView
                .findViewById(R.id.backgroundText);
        mForegroundProcessText.setTextColor(Color.WHITE);
        mBackgroundProcessText.setTextColor(Color.WHITE);

        mAppDrawer = (AppDrawerView) mView.findViewById(R.id.app_drawer);
        mAppDrawer.setRecentsManager(mRecentsManager);

        mRecentsOrAppDrawer = (FrameLayout) mView.findViewById(R.id.recents_or_appdrawer);

        mPopupView = new FrameLayout(mContext);
        mPopupView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        mPopupView.setBackgroundColor(Color.BLACK);
        mPopupView.getBackground().setAlpha(0);

        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams (
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        mView.setLayoutParams(lp);
        setViewTopMargin();
    
        mPopupView.addView(mView);

        mView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
        mPopupView.setOnTouchListener(mDragHandleListener);
        mPopupView.setOnKeyListener(new PopupKeyListener());

        mButtonList = (HorizontalScrollView) mView
                .findViewById(R.id.button_list_top);
        mButtonListItems = (LinearLayout) mView
                .findViewById(R.id.button_list_items_top);

        mButtonListContainerTop = (LinearLayout) mView
                .findViewById(R.id.button_list_container_top);
        mButtonListContainerBottom = (LinearLayout) mView
                .findViewById(R.id.button_list_container_bottom);
        selectButtonContainer();
        updateStyle();
    }

    @Override
    protected synchronized void updateRecentsAppsList(boolean force,  boolean refresh) {
        if (DEBUG) {
            Log.d(TAG, "updateRecentsAppsList " + System.currentTimeMillis());
        }
        if (!force && mUpdateNoRecentsTasksDone) {
            if (DEBUG) {
                Log.d(TAG, "!force && mUpdateNoRecentsTasksDone");
            }
            return;
        }
        if (mNoRecentApps == null || mRecentListHorizontal == null) {
            if (DEBUG) {
                Log.d(TAG,
                        "mNoRecentApps == null || mRecentListHorizontal == null");
            }
            return;
        }

        if (!mTaskLoadDone) {
            if (DEBUG) {
                Log.d(TAG, "!mTaskLoadDone");
            }
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "updateRecentsAppsList2");
        }
        mRecentListAdapter.notifyDataSetChanged();

        if (mRecentsManager.getTasks().size() != 0) {
            mNoRecentApps.setVisibility(View.GONE);
            mRecentListHorizontal.setVisibility(View.VISIBLE);
        } else {
            mNoRecentApps.setVisibility(View.VISIBLE);
            mRecentListHorizontal.setVisibility(View.GONE);
        }
        mUpdateNoRecentsTasksDone = true;
    }

    @Override
    protected synchronized void initView() {
        updateListLayout();

        mNoRecentApps.setLayoutParams(getListParams());
        mRecents.setVisibility(View.VISIBLE);
        mShowAppDrawer = false;
        mAppDrawer.setVisibility(View.GONE);
        mAppDrawer.setTranslationY(0);
        mAppDrawer.post(new Runnable() {
            @Override
            public void run() {
                mAppDrawer.setSelection(0);
            }
        });

        ViewGroup.LayoutParams layoutParams = mRecentsOrAppDrawer.getLayoutParams();
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        mRecentsOrAppDrawer.setLayoutParams(layoutParams);
        mCurrentHeight = mRecentsOrAppDrawer.getHeight();

        mVirtualBackKey = false;
        showOpenFavoriteButton();
        mOpenFavorite.setRotation(getExpandRotation());
        if (Utils.isLockToAppEnabled(mContext)) {
            updatePinAppButton();
        }
    }

    private void updateListLayout() {
        int dividerWith = mConfiguration.calcHorizontalDivider(false);

        mFavoriteListHorizontal.setLayoutParams(getListParams());
        mFavoriteListHorizontal.scrollTo(0);
        mFavoriteListHorizontal.setDividerWidth(dividerWith);
        mFavoriteListHorizontal.setPadding(dividerWith / 2, 0,
                dividerWith / 2, 0);

        mRecentListHorizontal.setLayoutParams(getListParams());
        mRecentListHorizontal.scrollTo(0);
        mRecentListHorizontal.setDividerWidth(dividerWith);
        mRecentListHorizontal.setPadding(dividerWith / 2, 0,
                dividerWith / 2, 0);
    }

    private LinearLayout.LayoutParams getListParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                mConfiguration.getItemMaxHeight());
    }

    @Override
    protected LinearLayout.LayoutParams getListItemParams() {
        return new LinearLayout.LayoutParams(mConfiguration.mMaxWidth + mConfiguration.mIconBorderHorizontalPx,
                mConfiguration.getItemMaxHeight());
    }

    private int getAppDrawerLines() {
        if (mConfiguration.mIconSizeDesc == SwitchConfiguration.IconSize.SMALL) {
            if (mConfiguration.isLandscape()) {
                return 4;
            } else {
                return 5;
            }
        }
        if (mConfiguration.mIconSizeDesc == SwitchConfiguration.IconSize.NORMAL) {
            if (mConfiguration.isLandscape()) {
                return 3;
            } else {
                return 4;
            }
        }
        if (mConfiguration.isLandscape()) {
            return 2;
        }
        return 3;
    }

    @Override
    protected FrameLayout.LayoutParams getAppDrawerParams() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, getAppDrawerLines()
                        * mConfiguration.getItemMaxHeight());
    }

    @Override
    protected WindowManager.LayoutParams getParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                getCurrentOverlayWidth(),
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                0, PixelFormat.TRANSLUCENT);

        if (mConfiguration.mDimBehind) {
            mPopupView.getBackground().setAlpha(
                        (int) (255 * mConfiguration.mBackgroundOpacity));
        } else {
            mPopupView.getBackground().setAlpha(0);
        }

        params.gravity = getHorizontalGravity();
        return params;
    }

    @Override
    public void updatePrefs(SharedPreferences prefs, String key) {
        super.updatePrefs(prefs, key);
        if (DEBUG) {
            Log.d(TAG, "updatePrefs");
        }
        if (mFavoriteListHorizontal != null) {
            mFavoriteListHorizontal.updatePrefs(prefs, key);
        }
        if (key != null && isPrefKeyForForceUpdate(key)) {
            if (mRecentListHorizontal != null) {
                mRecentListHorizontal.setAdapter(mRecentListAdapter);
            }
        }
        buildButtonList();
        if (mView != null) {
            if (key != null) {
                if (key.equals(SettingsActivity.PREF_BUTTON_POS)) {
                    selectButtonContainer();
                } else if (key.equals(SettingsActivity.PREF_HANDLE_POS_START_RELATIVE)) {
                    setViewTopMargin();
                }
            }
            updateStyle();
        }
    }

    private void setViewTopMargin() {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) mView.getLayoutParams();
        lp.topMargin = mConfiguration.getCurrentOffsetStart()
                + mConfiguration.mDragHandleHeight / 2
                - mConfiguration.getItemMaxHeight() / 2
                - (mButtonsVisible ? mConfiguration.mActionSizePx : 0);
        mView.setLayoutParams(lp);
    }

    @Override
    public void updateLayout() {
        try {
            updateListLayout();
        } catch (Exception e) {
            // ignored
        }
        super.updateLayout();
    }

    @Override
    protected void flipToAppDrawerNew() {
        if (mAppDrawerAnim != null) {
            mAppDrawerAnim.cancel();
        }
        mAppDrawer.setLayoutParams(getAppDrawerParams());
        mCurrentHeight = mRecentsOrAppDrawer.getHeight();
        int recentsHeight = mCurrentHeight;
        int appDrawerHeight = getAppDrawerParams().height;

        mAppDrawer.setTranslationY(-appDrawerHeight);
        mAppDrawer.setVisibility(View.VISIBLE);

        ValueAnimator expandAnimator = ValueAnimator.ofInt(appDrawerHeight, 0);
        expandAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int val = (Integer) valueAnimator.getAnimatedValue();
                mAppDrawer.setTranslationY(-val);
                if (mConfiguration.mButtonPos == 1) {
                    int slideHeight = appDrawerHeight - val;
                    if (slideHeight > recentsHeight) {
                        ViewGroup.LayoutParams layoutParams = mRecentsOrAppDrawer.getLayoutParams();
                        layoutParams.height = slideHeight;
                        mRecentsOrAppDrawer.setLayoutParams(layoutParams);
                    } else {
                        ViewGroup.LayoutParams layoutParams = mRecentsOrAppDrawer.getLayoutParams();
                        layoutParams.height = recentsHeight;
                        mRecentsOrAppDrawer.setLayoutParams(layoutParams);
                    }
                }
            }
        });

        Animator rotateAnimator = interpolator(
                mLinearInterpolator,
                ObjectAnimator.ofFloat(mAllappsButton, View.ROTATION,
                ROTATE_180_DEGREE, ROTATE_0_DEGREE));

        mAppDrawerAnim = new AnimatorSet();
        mAppDrawerAnim.playTogether(rotateAnimator, expandAnimator);
        mAppDrawerAnim.setDuration(APPDRAWER_DURATION);
        mAppDrawerAnim.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
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
        mAppDrawerAnim.start();
    }

    @Override
    protected void flipToRecentsNew() {
        if (mShowFavAnim != null) {
            mShowFavAnim.cancel();
        }
        int appDrawerHeight = getAppDrawerParams().height;
        int recentsHeight = mCurrentHeight;

        ValueAnimator collapseAnimator = ValueAnimator.ofInt(0, appDrawerHeight);
        collapseAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int val = (Integer) valueAnimator.getAnimatedValue();
                mAppDrawer.setTranslationY(-val);
                if (mConfiguration.mButtonPos == 1) {
                    int slideHeight = appDrawerHeight - val;
                    if (slideHeight > recentsHeight) {
                        ViewGroup.LayoutParams layoutParams = mRecentsOrAppDrawer.getLayoutParams();
                        layoutParams.height = slideHeight;
                        mRecentsOrAppDrawer.setLayoutParams(layoutParams);
                    } else {
                        ViewGroup.LayoutParams layoutParams = mRecentsOrAppDrawer.getLayoutParams();
                        layoutParams.height = recentsHeight;
                        mRecentsOrAppDrawer.setLayoutParams(layoutParams);
                    }
                }
            }
        });

        Animator rotateAnimator = interpolator(
                mLinearInterpolator,
                ObjectAnimator.ofFloat(mAllappsButton, View.ROTATION,
                ROTATE_0_DEGREE, ROTATE_180_DEGREE));

        mAppDrawerAnim = new AnimatorSet();
        mAppDrawerAnim.playTogether(rotateAnimator, collapseAnimator);
        mAppDrawerAnim.setDuration(APPDRAWER_DURATION);
        mAppDrawerAnim.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAppDrawer.setVisibility(View.GONE);
                mAppDrawer.setTranslationY(0);

                ViewGroup.LayoutParams layoutParams = mRecentsOrAppDrawer.getLayoutParams();
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                mRecentsOrAppDrawer.setLayoutParams(layoutParams);
                mCurrentHeight = mRecentsOrAppDrawer.getLayoutParams().height;
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
        mAppDrawerAnim.start();
    }

    @Override
    protected void toggleFavorites() {
        mShowFavorites = !mShowFavorites;
        storeExpandedFavoritesState();

        if (mShowFavAnim != null) {
            mShowFavAnim.cancel();
        }

        if (mShowFavorites) {
            ViewGroup.LayoutParams layoutParams = mFavoriteListHorizontal.getLayoutParams();
            layoutParams.height = 0;
            mFavoriteListHorizontal.setLayoutParams(layoutParams);
            mFavoriteListHorizontal.setVisibility(View.VISIBLE);

            ValueAnimator expandAnimator = ValueAnimator.ofInt(0, mConfiguration.getItemMaxHeight());
            expandAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    int val = (Integer) valueAnimator.getAnimatedValue();
                    ViewGroup.LayoutParams layoutParams = mFavoriteListHorizontal.getLayoutParams();
                    layoutParams.height = val;
                    mFavoriteListHorizontal.setLayoutParams(layoutParams);
                }
            });

            Animator rotateAnimator = interpolator(
                    mLinearInterpolator,
                    ObjectAnimator.ofFloat(mOpenFavorite, View.ROTATION, ROTATE_0_DEGREE, ROTATE_180_DEGREE));
            mShowFavAnim = new AnimatorSet();
            mShowFavAnim.playTogether(expandAnimator, rotateAnimator);
            mShowFavAnim.setDuration(FAVORITE_DURATION);
            mShowFavAnim.start();
        } else {
            ValueAnimator collapseAnimator = ValueAnimator.ofInt(mConfiguration.getItemMaxHeight(), 0);
            collapseAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    int val = (Integer) valueAnimator.getAnimatedValue();
                    ViewGroup.LayoutParams layoutParams = mFavoriteListHorizontal.getLayoutParams();
                    layoutParams.height = val;
                    mFavoriteListHorizontal.setLayoutParams(layoutParams);
                }
            });

            Animator rotateAnimator = interpolator(
                    mLinearInterpolator,
                    ObjectAnimator.ofFloat(mOpenFavorite, View.ROTATION, ROTATE_180_DEGREE, ROTATE_0_DEGREE));
            mShowFavAnim = new AnimatorSet();
            mShowFavAnim.playTogether(collapseAnimator, rotateAnimator);
            mShowFavAnim.setDuration(FAVORITE_DURATION);
            mShowFavAnim.start();
        }

        mShowFavAnim.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mOpenFavorite.setRotation(getExpandRotation());
                if (!mShowFavorites) {
                    mFavoriteListHorizontal.setVisibility(View.GONE);
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

    private void updateStyle() {
        mNoRecentApps.setTextColor(mConfiguration.getCurrentTextTint(mConfiguration.getViewBackgroundColor()));
        mNoRecentApps.setShadowLayer(mConfiguration.getShadowColorValue(), 0, 0, Color.BLACK);

        if (mConfiguration.mBgStyle == SwitchConfiguration.BgStyle.SOLID_LIGHT) {
            mForegroundProcessText.setShadowLayer(0, 0, 0, Color.BLACK);
            mBackgroundProcessText.setShadowLayer(0, 0, 0, Color.BLACK);
        } else {
            mForegroundProcessText.setShadowLayer(5, 0, 0, Color.BLACK);
            mBackgroundProcessText.setShadowLayer(5, 0, 0, Color.BLACK);
        }
        mButtonListContainer.setBackgroundColor(mConfiguration.getButtonBackgroundColor());
        if (mConfiguration.mBgStyle == SwitchConfiguration.BgStyle.TRANSPARENT) {
            if (mConfiguration.mDimActionButton) {
                mButtonListContainer.getBackground().setAlpha(200);
            } else {
                if (!mConfiguration.mDimBehind) {
                    mButtonListContainer.getBackground().setAlpha(
                            (int) (255 * mConfiguration.mBackgroundOpacity));
                } else {
                    mButtonListContainer.getBackground().setAlpha(0);
                }
            }
        }
        mRecents.setBackgroundColor(mConfiguration.getViewBackgroundColor());
        mAppDrawer.setBackgroundColor(mConfiguration.getViewBackgroundColor());
        if (mConfiguration.mBgStyle == SwitchConfiguration.BgStyle.TRANSPARENT) {
            if (!mConfiguration.mDimBehind) {
                mRecents.getBackground().setAlpha(
                        (int) (255 * mConfiguration.mBackgroundOpacity));
            } else {
                mRecents.getBackground().setAlpha(0);
            }
        }
        mRamUsageBarContainer
                .setVisibility(mConfiguration.mShowRambar ? View.VISIBLE
                        : View.GONE);
        if (!mHasFavorites) {
            mShowFavorites = false;
        }
        mFavoriteListHorizontal.setVisibility(mShowFavorites ? View.VISIBLE
                : View.GONE);

        ((ImageView) mOpenFavorite).setImageDrawable(BitmapUtils.colorize(mContext.getResources(),
                mConfiguration.getCurrentButtonTint(
                mConfiguration.getButtonBackgroundColor()),
                mContext.getResources().getDrawable(R.drawable.ic_expand)));
        mOpenFavorite.setBackgroundResource(mConfiguration.getBackgroundRipple());

        buildButtons();
        mButtonsVisible = isButtonVisible();
    }

    @Override
    protected int getCurrentOverlayWidth() {
        return mConfiguration.getCurrentOverlayWidth();
    }

    @Override
    protected int getSlideEndValue() {
        return mConfiguration.getCurrentOverlayWidth();
    }

    @Override
    protected void updateRamDisplay() {
        if (mUpdateRamBarTask != null) {
            mHandler.post(mUpdateRamBarTask);
        }
    }

    @Override
    protected void afterShowDone() {
    }

    private float getExpandRotation() {
        return mShowFavorites ? ROTATE_180_DEGREE : ROTATE_0_DEGREE;
    }

    @Override
    protected View getButtonList() {
        return mButtonList;
    }

    @Override
    public void notifiyRecentsListChanged() {
        if (DEBUG) {
            Log.d(TAG, "notifiyRecentsListChanged");
        }
        mRecentListAdapter.notifyDataSetChanged();
    }
}
