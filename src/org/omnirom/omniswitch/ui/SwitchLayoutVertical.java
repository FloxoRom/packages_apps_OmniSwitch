/*
 *  Copyright (C) 2015-2016 The OmniROM Project
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
import android.graphics.drawable.Drawable;
import android.graphics.PixelFormat;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class SwitchLayoutVertical extends AbstractSwitchLayout {
    private ListView mRecentList;
    private FavoriteViewVertical mFavoriteListView;
    private RecentListAdapter mRecentListAdapter;
    private ScrollView mButtonList;
    private Runnable mUpdateRamBarTask;
    private ImageView mRamDisplay;
    private View mRamDisplayContainer;
    private FrameLayout mRecentsOrAppDrawer;

    private class RecentListAdapter extends ArrayAdapter<TaskDescription> {

        public RecentListAdapter(Context context, int resource,
                List<TaskDescription> values) {
            super(context, R.layout.package_item, resource, values);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TaskDescription ad = getItem(getTaskPosition(position));
            ThumbnailTaskView item = null;
            if (convertView == null) {
                item = getRecentItemTemplate();
            } else {
                item = (ThumbnailTaskView) convertView;
            }
            item.setTask(ad, ad.isNeedsUpdate());

            if (ad.isNeedsUpdate()) {
                ad.setNeedsUpdate(false);
            }
            return item;
        }
    }

    public SwitchLayoutVertical(SwitchManager manager, Context context) {
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
                String usedMemStr = mContext.getResources()
                        .getString(R.string.service_foreground_processes,
                                sizeStr);
                sizeStr = Formatter.formatShortFileSize(mContext, availMem);
                String availMemStr = mContext.getResources()
                        .getString(R.string.service_background_processes,
                                sizeStr);
                mRamDisplay.setImageDrawable(BitmapUtils.memImage(mContext.getResources(),
                        mConfiguration.mMemDisplaySize, mConfiguration.mDensity,
                        mConfiguration.mLayoutStyle == 0, usedMemStr, availMemStr,
                        mConfiguration, mConfiguration.getCurrentButtonTint(
                        mConfiguration.getButtonBackgroundColor())));
            }
        };
    }

    @Override
    protected synchronized void createView() {
        mView = mInflater.inflate(R.layout.recents_list_vertical, null, false);

        mRecents = (LinearLayout) mView.findViewById(R.id.recents);

        mRecentList = (ListView) mView
                .findViewById(R.id.recent_list);
        mRecentList.setVerticalScrollBarEnabled(false);
        final int listMargin = Math.round(1 * mConfiguration.mDensity);
        mRecentList.setDividerHeight(listMargin);
        mRecentList.setStackFromBottom(mConfiguration.mRevertRecents);

        mNoRecentApps = (TextView) mView.findViewById(R.id.no_recent_apps);

        mRecentList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                TaskDescription task = mRecentsManager.getTasks().get(getTaskPosition(position));
                mRecentsManager.switchTask(task, mAutoClose, false);
            }
        });

        mRecentList.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                    int position, long id) {
                TaskDescription task = mRecentsManager.getTasks().get(getTaskPosition(position));
                handleLongPressRecent(task, view);
                return true;
            }
        });

        SwipeDismissListViewTouchListener touchListener = new SwipeDismissListViewTouchListener(
                mRecentList,
                new SwipeDismissListViewTouchListener.DismissCallbacks() {
                    @Override
                    public void onDismiss(ListView listView,
                            int[] reverseSortedPositions) {
                        int position = getTaskPosition(reverseSortedPositions[0]);
                        if (DEBUG) {
                            Log.d(TAG, "onDismiss: "
                                    + mRecentsManager.getTasks().size() + ":"
                                    + position);
                        }
                        try {
                            TaskDescription ad = mRecentsManager.getTasks().get(position);
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

        mRecentList.setOnTouchListener(touchListener);
        mRecentList.setOnScrollListener(touchListener.makeScrollListener());
        mRecentList.setAdapter(mRecentListAdapter);

        mFavoriteListView = (FavoriteViewVertical) mView
                .findViewById(R.id.favorite_list);
        mFavoriteListView.setVerticalScrollBarEnabled(false);
        mFavoriteListView.setStackFromBottom(mConfiguration.mBottomFavorites);
        mFavoriteListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                String intent = mFavoriteList.get(position);
                mRecentsManager.startIntentFromtString(intent, true);
            }
        });
        mFavoriteListView.setAdapter(mFavoriteListAdapter);

        mFavoriteListView
                .setOnItemLongClickListener(new OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(AdapterView<?> parent,
                            View view, int position, long id) {
                        String intent = mFavoriteList.get(position);
                        PackageManager.PackageItem packageItem = PackageManager
                                .getInstance(mContext).getPackageItem(intent);
                        handleLongPressFavorite(packageItem, view);
                        return true;
                    }
                });

        mAppDrawer = (AppDrawerView) mView.findViewById(R.id.app_drawer);
        mAppDrawer.setRecentsManager(mRecentsManager);

        mRecentsOrAppDrawer = (FrameLayout) mView.findViewById(R.id.recents_or_appdrawer);

        mPopupView = new FrameLayout(mContext);
        mPopupView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        mPopupView.setBackgroundColor(Color.BLACK);
        mPopupView.getBackground().setAlpha(0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        lp.gravity = getHorizontalGravity();
        mView.setLayoutParams(lp);
        mPopupView.addView(mView);

        mView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
        mPopupView.setOnTouchListener(mDragHandleListener);
        mPopupView.setOnKeyListener(new PopupKeyListener());

        mButtonList = (ScrollView) mView
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
        if (mNoRecentApps == null || mRecentList == null) {
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
            Log.d(TAG, "updateRecentsAppsList before notifyDataSetChanged " + System.currentTimeMillis());
        }
        mRecentList.setAlpha(0f);
        mRecentListAdapter.notifyDataSetChanged();

        if (mRecentsManager.getTasks().size() != 0) {
            mNoRecentApps.setVisibility(View.GONE);
            if (!refresh) {
                resetRecentsPosition();
            }
            mRecentList.setVisibility(View.VISIBLE);
            if (mCurrentSlideWidth != 0) {
                mRecentList.animate().alpha(1f).setDuration(200);
            } else {
                mRecentList.setAlpha(1f);
            }
        } else {
            mNoRecentApps.setVisibility(View.VISIBLE);
            mRecentList.setVisibility(View.GONE);
            mRecentList.setAlpha(1f);
        }
        mUpdateNoRecentsTasksDone = true;
    }

    @Override
    protected synchronized void initView() {
        if (DEBUG) {
            Log.d(TAG, "initView");
        }
        mFavoriteListView.setLayoutParams(getListParams());
        mFavoriteListView.setSelection(0);
        mRecentList.setLayoutParams(getRecentListParams());
        mNoRecentApps.setLayoutParams(getRecentListParams());

        final boolean resizeUpfront = mConfiguration.mLocation == 0 ?
                mConfiguration.mButtonPos == 1 :
                mConfiguration.mButtonPos == 0;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        if (resizeUpfront) {
            lp.gravity = getHorizontalGravity();
        } else {
            if (mConfiguration.mLocation == 0) {
                lp.gravity = Gravity.LEFT;
            } else {
                lp.gravity = Gravity.RIGHT;
            }
        }
        mRecents.setLayoutParams(lp);
        mRecents.setVisibility(View.VISIBLE);
    
        mShowAppDrawer = false;
        mAppDrawer.setVisibility(View.GONE);
        mAppDrawer.post(new Runnable() {
            @Override
            public void run() {
                mAppDrawer.setSelection(0);
            }
        });

        ViewGroup.LayoutParams layoutParams = mRecentsOrAppDrawer.getLayoutParams();
        layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        mRecentsOrAppDrawer.setLayoutParams(layoutParams);

        mVirtualBackKey = false;
        showOpenFavoriteButton();
        mOpenFavorite.setRotation(getExpandRotation());
        if (Utils.isLockToAppEnabled(mContext)) {
            updatePinAppButton();
        }
    }

    protected LinearLayout.LayoutParams getListParams() {
        return new LinearLayout.LayoutParams(mConfiguration.mMaxWidth + mConfiguration.mIconBorderHorizontalPx,
                LinearLayout.LayoutParams.MATCH_PARENT);
    }

    private LinearLayout.LayoutParams getRecentListParams() {
        return new LinearLayout.LayoutParams(getCurrentThumbWidth(),
                LinearLayout.LayoutParams.MATCH_PARENT);
    }

    @Override
    protected LinearLayout.LayoutParams getListItemParams() {
        return new LinearLayout.LayoutParams(mConfiguration.mMaxWidth,
                mConfiguration.getItemMaxHeight());
    }

    private LinearLayout.LayoutParams getRecentListItemParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                getCurrentThumbWidth(),
                getCurrentThumbHeight());
        return params;
    }

    private int getAppDrawerColumns() {
        int columns = getRecentsWidth() / (mConfiguration.mMaxWidth + mConfiguration.mIconBorderHorizontalPx);
        if (mConfiguration.mIconSizeDesc == SwitchConfiguration.IconSize.SMALL) {
            return Math.max(4, columns);
        }
        if (mConfiguration.mIconSizeDesc == SwitchConfiguration.IconSize.NORMAL) {
            return Math.max(3, columns);
        }
        return Math.max(2, columns);
    }

    @Override
    protected FrameLayout.LayoutParams getAppDrawerParams() {
        int appDrawerWidth = getAppDrawerWidth();
        int recentsWith = getRecentsWidth();
        appDrawerWidth = Math.max(appDrawerWidth, recentsWith);
        return new FrameLayout.LayoutParams(appDrawerWidth,
                FrameLayout.LayoutParams.MATCH_PARENT);
    }

    @Override
    protected WindowManager.LayoutParams getParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
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
        if (mRecentList != null) {
            mRecentList.setStackFromBottom(mConfiguration.mRevertRecents);
        }

        if (key != null && isPrefKeyForForceUpdate(key)) {
            if (mFavoriteListView != null) {
                mFavoriteListView.setAdapter(mFavoriteListAdapter);
            }
            if (mRecentList != null) {
                mRecentList.setAdapter(mRecentListAdapter);
            }
        }

        if (mFavoriteListView != null) {
            mFavoriteListView.updatePrefs(prefs, key);
            mFavoriteListView.setStackFromBottom(mConfiguration.mBottomFavorites);
        }
        buildButtonList();
        if (mConfiguration.mShowRambar) {
            if (mRamDisplay == null) {
                createMemoryDisplay();
            }
            addMemoryDisplay();
        }
        // must be recreated on dpi changes
        createOpenFavoriteButton();
        addOpenFavoriteButton();
        enableOpenFavoriteButton(!mShowAppDrawer);

        if (mView != null) {
            if (key != null && key.equals(SettingsActivity.PREF_BUTTON_POS)) {
                selectButtonContainer();
            }
            updateStyle();
        }
    }

    private ThumbnailTaskView getRecentItemTemplate() {
        ThumbnailTaskView item = new ThumbnailTaskView(mContext);
        item.setCanSideHeader(true);
        item.setLayoutParams(getRecentListItemParams());
        item.setBackgroundResource(mConfiguration.mBgStyle == SwitchConfiguration.BgStyle.SOLID_LIGHT ? R.drawable.ripple_dark
                : R.drawable.ripple_light);
        item.setThumbRatio(mConfiguration.mThumbRatio);
        return item;
    }

    /*@Override
    protected void flipToAppDrawerNew() {
        if (mConfiguration.mLocation == 0) {
            mView.setTranslationX(getSlideEndPoint());
        }
        mRecents.setVisibility(View.GONE);
        mAppDrawer.setVisibility(View.VISIBLE);
        mAppDrawer.setLayoutParams(getAppDrawerParams());
        mAppDrawer.requestLayout();
        mRecentsOrAppDrawer.requestLayout();
        enableOpenFavoriteButton(false);
    }*/

    /*@Override
    protected void flipToRecentsNew() {
        if (mConfiguration.mLocation == 0) {
            mView.setTranslationX(getSlideEndPoint());
        }
        mAppDrawer.setVisibility(View.GONE);
        mRecents.setVisibility(View.VISIBLE);
        mRecentsOrAppDrawer.requestLayout();
        enableOpenFavoriteButton(true);
    }*/

    @Override
    protected void flipToAppDrawerNew() {
        enableOpenFavoriteButton(false);

        if (mAppDrawerAnim != null) {
            mAppDrawerAnim.cancel();
        }
        int appDrawerWidth = getAppDrawerParams().width;
        int recentsWidth = getDefaultViewWidth();

        mAppDrawer.setLayoutParams(getAppDrawerParams());
        mAppDrawer.setTranslationX(mConfiguration.mLocation == 0 ? appDrawerWidth : -appDrawerWidth);
        mAppDrawer.setVisibility(View.VISIBLE);

        final boolean resizeUpfront = mConfiguration.mLocation == 0 ?
                mConfiguration.mButtonPos == 1 :
                mConfiguration.mButtonPos == 0;
        Log.d(TAG, "resizeUpfront = " + resizeUpfront);
        if (resizeUpfront) {
            ViewGroup.LayoutParams layoutParams = mRecentsOrAppDrawer.getLayoutParams();
            layoutParams.width = appDrawerWidth;
            mRecentsOrAppDrawer.setLayoutParams(layoutParams);
            mView.setTranslationX(getSlideEndPoint());
        }

        ValueAnimator expandAnimator = ValueAnimator.ofInt(appDrawerWidth, 0);
        expandAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int val = (Integer) valueAnimator.getAnimatedValue();
                mAppDrawer.setTranslationX(mConfiguration.mLocation == 0 ? val : -val);
                int slideWidth = appDrawerWidth - val + (isButtonVisible() ? mConfiguration.mActionSizePx : 0);
                if (slideWidth > recentsWidth) {
                    if (!resizeUpfront) {
                        if (mConfiguration.mLocation == 0) {
                            mView.setTranslationX(getCurrentOverlayWidth() - slideWidth);
                        }
                    }
                }
            }
        });
        Animator rotateAnimator = interpolator(
                mLinearInterpolator,
                ObjectAnimator.ofFloat(mAllappsButton, View.ROTATION,
                mConfiguration.mLocation != 0 ? ROTATE_90_DEGREE : ROTATE_270_DEGREE,
                mConfiguration.mLocation != 0 ? ROTATE_270_DEGREE : ROTATE_90_DEGREE));

        mAppDrawerAnim = new AnimatorSet();
        mAppDrawerAnim.playTogether(rotateAnimator, expandAnimator);
        mAppDrawerAnim.setDuration(APPDRAWER_DURATION);
        mAppDrawerAnim.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!resizeUpfront) {
                    mView.setTranslationX(getSlideEndPoint());
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
        mAppDrawerAnim.start();
    }

    @Override
    protected void flipToRecentsNew() {
        enableOpenFavoriteButton(true);

        if (mAppDrawerAnim != null) {
            mAppDrawerAnim.cancel();
        }
        int appDrawerWidth = getAppDrawerParams().width;
        final int recentsWidth = getDefaultViewWidth();

        final boolean resizeUpfront = mConfiguration.mLocation == 0 ?
                mConfiguration.mButtonPos == 1 :
                mConfiguration.mButtonPos == 0;

        ValueAnimator collapseAnimator = ValueAnimator.ofInt(0, appDrawerWidth);
        collapseAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int val = (Integer) valueAnimator.getAnimatedValue();
                mAppDrawer.setTranslationX(mConfiguration.mLocation == 0 ? val : -val);
                int slideWidth = appDrawerWidth - val + (isButtonVisible() ? mConfiguration.mActionSizePx : 0);
                if (slideWidth < recentsWidth) {
                    if (!resizeUpfront) {
                        if (mConfiguration.mLocation == 0) {
                            mView.setTranslationX(getCurrentOverlayWidth() - recentsWidth);
                        }
                    }
                } else {
                    if (!resizeUpfront) {
                        if (mConfiguration.mLocation == 0) {
                            mView.setTranslationX(getCurrentOverlayWidth() - slideWidth);
                        }
                    }
                }
            }
        });
        Animator rotateAnimator = interpolator(
                mLinearInterpolator,
                ObjectAnimator.ofFloat(mAllappsButton, View.ROTATION,
                mConfiguration.mLocation != 0 ? ROTATE_270_DEGREE : ROTATE_90_DEGREE,
                mConfiguration.mLocation != 0 ? ROTATE_90_DEGREE : ROTATE_270_DEGREE));

        mAppDrawerAnim = new AnimatorSet();
        mAppDrawerAnim.playTogether(rotateAnimator, collapseAnimator);
        mAppDrawerAnim.setDuration(APPDRAWER_DURATION);
        mAppDrawerAnim.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAppDrawer.setVisibility(View.GONE);
                if (resizeUpfront) {
                    ViewGroup.LayoutParams layoutParams = mRecentsOrAppDrawer.getLayoutParams();
                    layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                    mRecentsOrAppDrawer.setLayoutParams(layoutParams);
                }
                mView.setTranslationX(getSlideEndPoint());
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
        final int favoriteWidth = mConfiguration.mMaxWidth + mConfiguration.mIconBorderHorizontalPx;
        if (mShowFavorites) {
            ViewGroup.LayoutParams layoutParams = mFavoriteListView.getLayoutParams();
            layoutParams.width = 0;
            mFavoriteListView.setLayoutParams(layoutParams);
            mFavoriteListView.setVisibility(View.VISIBLE);

            ValueAnimator expandAnimator = ValueAnimator.ofInt(0, favoriteWidth);
            expandAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    int val = (Integer) valueAnimator.getAnimatedValue();
                    ViewGroup.LayoutParams layoutParams = mFavoriteListView.getLayoutParams();
                    layoutParams.width = val;
                    mFavoriteListView.setLayoutParams(layoutParams);
                    if (mConfiguration.mLocation == 0) {
                        mView.setTranslationX(getCurrentOverlayWidth() - getCurrentFavoritesWidth(val));
                    }
                }
            });
            Animator rotateAnimator = interpolator(
                    mLinearInterpolator,
                    ObjectAnimator.ofFloat(mOpenFavorite, View.ROTATION,
                    mConfiguration.mLocation != 0 ? ROTATE_270_DEGREE : ROTATE_90_DEGREE,
                    mConfiguration.mLocation != 0 ? ROTATE_90_DEGREE : ROTATE_270_DEGREE));
            mShowFavAnim = new AnimatorSet();
            mShowFavAnim.playTogether(rotateAnimator, expandAnimator);
            mShowFavAnim.setDuration(FAVORITE_DURATION);
            mShowFavAnim.start();
        } else {
            ValueAnimator collapseAnimator = ValueAnimator.ofInt(favoriteWidth, 0);
            collapseAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    int val = (Integer) valueAnimator.getAnimatedValue();
                    ViewGroup.LayoutParams layoutParams = mFavoriteListView.getLayoutParams();
                    layoutParams.width = val;
                    mFavoriteListView.setLayoutParams(layoutParams);
                    if (mConfiguration.mLocation == 0) {
                        mView.setTranslationX(getCurrentOverlayWidth() - getCurrentFavoritesWidth(val));
                    }
                }
            });
            Animator rotateAnimator = interpolator(
                    mLinearInterpolator,
                    ObjectAnimator.ofFloat(mOpenFavorite, View.ROTATION,
                    mConfiguration.mLocation != 0 ? ROTATE_90_DEGREE : ROTATE_270_DEGREE,
                    mConfiguration.mLocation != 0 ? ROTATE_270_DEGREE : ROTATE_90_DEGREE));
            mShowFavAnim = new AnimatorSet();
            mShowFavAnim.playTogether(rotateAnimator, collapseAnimator);
            mShowFavAnim.setDuration(FAVORITE_DURATION);
        }

        mShowFavAnim.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mOpenFavorite.setRotation(getExpandRotation());
                if (!mShowFavorites) {
                    mFavoriteListView.setVisibility(View.GONE);
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
        mShowFavAnim.start();
    }

    private void addOpenFavoriteButton() {
        mActionList.add(mOpenFavorite);
    }

    private void updateStyle() {
        if (DEBUG) {
            Log.d(TAG, "updateStyle");
        }
        mNoRecentApps.setTextColor(mConfiguration.getCurrentTextTint(mConfiguration.getViewBackgroundColor()));
        mNoRecentApps.setShadowLayer(mConfiguration.getShadowColorValue(), 0, 0, Color.BLACK);

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
        if (!mHasFavorites) {
            mShowFavorites = false;
        } else if (mConfiguration.mButtonHide) {
            mShowFavorites = true;
        }
        storeExpandedFavoritesState();
        mFavoriteListView.setVisibility(mShowFavorites ? View.VISIBLE : View.GONE);
        buildButtons();
        mButtonsVisible = isButtonVisible();
    }

    private float getExpandRotation() {
        if (mConfiguration.mLocation != 0) {
            return mShowFavorites ? ROTATE_90_DEGREE : ROTATE_270_DEGREE;
        }
        return mShowFavorites ? ROTATE_270_DEGREE : ROTATE_90_DEGREE;
    }

    @Override
    protected int getCurrentOverlayWidth() {
        return mConfiguration.getCurrentDisplayWidth();
    }

    @Override
    protected int getSlideEndValue() {
        if (mShowAppDrawer) {
            return getAppDrawerParams().width
                    + (isButtonVisible() ? mConfiguration.mActionSizePx : 0);
        }
        return getDefaultViewWidth();
    }

    private int getSlideEndValue(boolean showAppDrawer) {
        if (showAppDrawer) {
            return getAppDrawerParams().width
                    + (isButtonVisible() ? mConfiguration.mActionSizePx : 0);
        }
        return getDefaultViewWidth();
    }
 
    private int getRecentsWidth() {
        return getCurrentThumbWidth()
                + (mShowFavorites ? (mConfiguration.mMaxWidth + mConfiguration.mIconBorderHorizontalPx) : 0);
    }

    private int getAppDrawerWidth() {
        return getAppDrawerColumns()
                * (mConfiguration.mMaxWidth + mConfiguration.mIconBorderHorizontalPx);
    }

    private int getDefaultViewWidth() {
        return getCurrentThumbWidth()
                + (mShowFavorites ? (mConfiguration.mMaxWidth + mConfiguration.mIconBorderHorizontalPx) : 0)
                + (isButtonVisible() ? mConfiguration.mActionSizePx : 0);
    }

    private int getCurrentFavoritesWidth(int favoritesWidth) {
        return getCurrentThumbWidth()
            + favoritesWidth
            + (isButtonVisible() ? mConfiguration.mActionSizePx : 0);
    }

    private int getCurrentThumbWidth() {
        return (int)(mConfiguration.mThumbnailWidth * mConfiguration.mThumbRatio) +
               ( mConfiguration.mSideHeader ? mConfiguration.getOverlayHeaderWidth() : 0);
    }

    private int getCurrentThumbHeight() {
        return (int)(mConfiguration.mThumbnailHeight * mConfiguration.mThumbRatio) +
                (mConfiguration.mSideHeader ? 0 : mConfiguration.getOverlayHeaderWidth());
    }

    @Override
    protected void afterShowDone() {
    }

    private void createMemoryDisplay() {
        mRamDisplayContainer = mInflater.inflate(R.layout.memory_display, null, false);
        mRamDisplay = (ImageView) mRamDisplayContainer.findViewById(R.id.memory_image);
        mRamDisplay.setImageDrawable(BitmapUtils.memImage(mContext.getResources(),
                mConfiguration.mMemDisplaySize, mConfiguration.mDensity,
                mConfiguration.mLayoutStyle == 0, "", "", mConfiguration,
                mConfiguration.getCurrentButtonTint(mConfiguration.getButtonBackgroundColor())));
    }

    private void addMemoryDisplay() {
        mActionList.add(mRamDisplayContainer);
    }

    @Override
    protected void updateRamDisplay() {
        if (mRamDisplay != null) {
            mHandler.post(mUpdateRamBarTask);
        }
    }

    @Override
    protected View getButtonList() {
        return mButtonList;
    }

    private int getTaskPosition(int position) {
        if (mConfiguration.mRevertRecents) {
            return mRecentsManager.getTasks().size() - 1 - position;
        } else {
            return position;
        }
    }

    private void resetRecentsPosition() {
        if (mConfiguration.mRevertRecents) {
            mRecentList.setSelection(mRecentsManager.getTasks().size() - 1);
        } else {
            mRecentList.setSelection(0);
        }
    }

    private void createOpenFavoriteButton() {
        mOpenFavorite = getActionButtonTemplate(mContext.getResources()
                .getDrawable(R.drawable.ic_expand));
        mOpenFavorite.setRotation(getExpandRotation());

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
    }

    @Override
    public void notifiyRecentsListChanged() {
        if (DEBUG) {
            Log.d(TAG, "notifiyRecentsListChanged");
        }
        mRecentListAdapter.notifyDataSetChanged();
    }
}
