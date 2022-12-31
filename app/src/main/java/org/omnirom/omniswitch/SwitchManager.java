/*
 *  Copyright (C) 2013-2015 The OmniROM Project
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

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.ActivityThread;
import android.app.IActivityManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.net.Uri;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.logging.InstanceId;
import com.android.internal.logging.InstanceIdSequence;
import com.android.wm.shell.splitscreen.ISplitScreen;
import com.android.wm.shell.splitscreen.ISplitScreenListener;

import org.omnirom.omniswitch.ui.ISwitchLayout;
import org.omnirom.omniswitch.ui.SwitchGestureView;
import org.omnirom.omniswitch.ui.SwitchLayout;
import org.omnirom.omniswitch.ui.SwitchLayoutVertical;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.PendingIntent.FLAG_MUTABLE;

public class SwitchManager {
    private static final String TAG = "OmniSwitch:SwitchManager";
    private static final boolean DEBUG = false;
    private List<TaskDescription> mLoadedTasks;
    private List<TaskDescription> mLoadedTasksOriginal;
    private ISwitchLayout mLayout;
    private SwitchGestureView mGestureView;
    private Context mContext;
    private SwitchConfiguration mConfiguration;
    private int mLayoutStyle;
    private final ActivityManager mAm;
    private final IActivityManager mIAm;
    private IPowerManager mPowerService;
    private LauncherApps mLauncherApps;
    private Handler mHandler = new Handler();

    private ISplitScreen mSplitScreen;
    private SplitScreenListener mSplitScreenListener;
    private InstanceIdSequence mInstanceSequence = new InstanceIdSequence(Integer.MAX_VALUE);
    private int mMainTaskId = INVALID_TASK_ID;
    private int mSideTaskId = INVALID_TASK_ID;

    public static final int STAGE_POSITION_UNDEFINED = -1;
    public static final int STAGE_POSITION_TOP_OR_LEFT = 0;
    public static final int STAGE_POSITION_BOTTOM_OR_RIGHT = 1;

    public static final int STAGE_TYPE_UNDEFINED = -1;
    public static final int STAGE_TYPE_MAIN = 0;
    public static final int STAGE_TYPE_SIDE = 1;

    public static final float DEFAULT_SPLIT_RATIO = 0.5f;

    private class SplitScreenListener extends ISplitScreenListener.Stub {
        @Override
        public void onStagePositionChanged(int stage, int position) {
            Log.d(TAG, "onStagePositionChanged " + stage + " " + position);
        }

        @Override
        public void onTaskStageChanged(int taskId, int stage, boolean visible) {
            Log.d(TAG, "onTaskStageChanged pre taskId = " + taskId + " stage = " + stage + " visible = " + visible);
            if (stage == STAGE_TYPE_SIDE && visible) {
                mMainTaskId = taskId;
            }
            if (stage == STAGE_TYPE_MAIN && visible) {
                mSideTaskId = taskId;
            }
            if (stage == STAGE_TYPE_UNDEFINED && taskId == mSideTaskId) {
                mSideTaskId = INVALID_TASK_ID;
            }
            if (stage == STAGE_TYPE_UNDEFINED && taskId == mMainTaskId) {
                mMainTaskId = INVALID_TASK_ID;
            }
            Log.d(TAG, "onTaskStageChanged post mMainTaskId = " + mMainTaskId + " mSideTaskId = " + mSideTaskId);
        }
    }

    public SwitchManager(Context context, int layoutStyle) {
        mContext = context;
        mConfiguration = SwitchConfiguration.getInstance(mContext);
        mLayoutStyle = layoutStyle;
        mAm = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mIAm = ActivityManager.getService();
        mLauncherApps = (LauncherApps) mContext.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        init();
    }

    public void bindSplitScreen(ISplitScreen splitScreen) {
        Log.d(TAG, "bindSplitScreen");
        mSplitScreen = splitScreen;
        mSplitScreenListener = new SplitScreenListener();
        try {
            mSplitScreen.registerSplitScreenListener(mSplitScreenListener);
        } catch (RemoteException e) {
            Log.e(TAG, "registerSplitScreenListener", e);
        }
    }

    public void unbindSplitScreen() {
        if (mSplitScreen != null) {
            Log.d(TAG, "unbindSplitScreen");
            try {
                mSplitScreen.unregisterSplitScreenListener(mSplitScreenListener);
            } catch (RemoteException e) {
                Log.e(TAG, "unregisterSplitScreenListener", e);
            }
            mSplitScreenListener = null;
            mSplitScreen = null;
        }
    }

    public void hide(boolean fast) {
        if (isShowing()) {
            if (DEBUG) {
                Log.d(TAG, "hide");
            }
            mLayout.hide(fast);
        }
    }

    public void hideHidden() {
        if (isShowing()) {
            if (DEBUG) {
                Log.d(TAG, "hideHidden");
            }
            mLayout.hideHidden();
        }
    }

    public void show() {
        if (!isShowing()) {
            if (DEBUG) {
                Log.d(TAG, "show");
            }
            mLayout.setHandleRecentsUpdate(true);

            clearTasks();
            RecentTasksLoader.getInstance(mContext).cancelLoadingTasks();
            RecentTasksLoader.getInstance(mContext).setSwitchManager(this);
            RecentTasksLoader.getInstance(mContext).loadTasksInBackground(0, true, true);

            // show immediately
            mLayout.show();
        }
    }

    public void showPreloaded() {
        if (!isShowing()) {
            if (DEBUG) {
                Log.d(TAG, "showPreloaded");
            }

            // show immediately
            mLayout.show();
        }
    }

    public void beforePreloadTasks() {
        if (!isShowing()) {
            if (DEBUG) {
                Log.d(TAG, "beforePreloadTasks");
            }
            clearTasks();
            mLayout.setHandleRecentsUpdate(true);
        }
    }

    public void showHidden() {
        if (!isShowing()) {
            if (DEBUG) {
                Log.d(TAG, "showHidden");
            }
            mLayout.setHandleRecentsUpdate(true);

            // show immediately
            mLayout.showHidden();
        }
    }

    public boolean isShowing() {
        return mLayout.isShowing();
    }

    private void init() {
        if (DEBUG) {
            Log.d(TAG, "init");
        }

        mLoadedTasks = new ArrayList<TaskDescription>();
        mLoadedTasksOriginal = new ArrayList<TaskDescription>();
        switchLayout();
        mGestureView = new SwitchGestureView(this, mContext);
    }

    public void killManager() {
        RecentTasksLoader.killInstance();
        mGestureView.hide();
    }

    public ISwitchLayout getLayout() {
        return mLayout;
    }

    private void switchLayout() {
        if (mLayout != null) {
            mLayout.shutdownService();
        }
        if (mLayoutStyle == 0) {
            mLayout = new SwitchLayout(this, mContext);
        } else {
            mLayout = new SwitchLayoutVertical(this, mContext);
        }

    }

    public SwitchGestureView getSwitchGestureView() {
        return mGestureView;
    }

    public void update(List<TaskDescription> taskList, List<TaskDescription> taskListOriginal) {
        mLoadedTasksOriginal = taskListOriginal;
        mLoadedTasks.clear();
        mLoadedTasks.addAll(taskList);
        mLayout.update();
    }

    public void switchTask(TaskDescription ad, boolean close, boolean customAnim) {
        if (ad.isKilled()) {
            return;
        }

        if (close) {
            hide(true);
        }
        try {
            if (isValidSideTask(ad)) {
                int sideTaskId = ad.persistentTaskId;
                launchTask(sideTaskId, STAGE_POSITION_BOTTOM_OR_RIGHT);
            } else {
                ActivityOptions options = null;
                if (customAnim) {
                    options = ActivityOptions.makeCustomAnimation(mContext, R.anim.last_app_in, R.anim.last_app_out);
                } else {
                    options = ActivityOptions.makeBasic();
                }
                ActivityManagerNative.getDefault().startActivityFromRecents(
                        ad.getPersistentTaskId(), options.toBundle());
            }
            SwitchStatistics.getInstance(mContext).traceStartIntent(ad.getIntent());
            if (DEBUG) {
                Log.d(TAG, "switch to " + ad.getLabel());
            }
        } catch (Exception e) {
        }
    }

    public void killTask(TaskDescription ad, boolean close) {
        if (mConfiguration.mRestrictedMode) {
            return;
        }

        if (close) {
            hide(false);
        }

        if (ad.isLocked()) {
            // remove from locked
            toggleLockedApp(ad, ad.isLocked(), false);
        }

        removeTask(ad.getPersistentTaskId());
        if (DEBUG) {
            Log.d(TAG, "kill " + ad.getLabel());
        }

        if (!close) {
            ad.setKilled();
            removeTaskFromList(ad);
            mLayout.refresh();
        }
    }

    /**
     * killall will always remove all tasks - also those that are
     * filtered out (not active)
     *
     * @param close
     */
    public void killAll(boolean close) {
        if (mConfiguration.mRestrictedMode) {
            return;
        }

        if (mLoadedTasksOriginal.size() == 0) {
            if (close) {
                hide(true);
            }
            return;
        }

        Iterator<TaskDescription> nextTask = mLoadedTasksOriginal.iterator();
        while (nextTask.hasNext()) {
            TaskDescription ad = nextTask.next();
            if (ad.isLocked()) {
                continue;
            }
            removeTask(ad.getPersistentTaskId());
            if (DEBUG) {
                Log.d(TAG, "kill " + ad.getLabel());
            }
            ad.setKilled();
        }
        goHome(close);
    }

    public void killOther(boolean close) {
        if (mConfiguration.mRestrictedMode) {
            return;
        }

        if (mLoadedTasksOriginal.size() <= 1) {
            if (close) {
                hide(true);
            }
            return;
        }
        Iterator<TaskDescription> nextTask = mLoadedTasksOriginal.iterator();
        // skip active task
        nextTask.next();
        while (nextTask.hasNext()) {
            TaskDescription ad = nextTask.next();
            if (ad.isLocked()) {
                continue;
            }
            removeTask(ad.getPersistentTaskId());
            if (DEBUG) {
                Log.d(TAG, "kill " + ad.getLabel());
            }
            ad.setKilled();
        }
        if (close) {
            hide(true);
        }
    }

    public void killCurrent(boolean close) {
        if (mConfiguration.mRestrictedMode) {
            return;
        }

        if (mLoadedTasksOriginal.size() == 0) {
            if (close) {
                hide(true);
            }
            return;
        }

        if (mLoadedTasksOriginal.size() >= 1) {
            TaskDescription ad = mLoadedTasksOriginal.get(0);
            if (ad.isLocked()) {
                // remove from locked
                toggleLockedApp(ad, ad.isLocked(), false);
            }
            removeTask(ad.getPersistentTaskId());
            if (DEBUG) {
                Log.d(TAG, "kill " + ad.getLabel());
            }
            ad.setKilled();
        }
        if (close) {
            hide(true);
        }
    }

    public void goHome(boolean close) {
        if (close) {
            hide(true);
        }

        Intent homeIntent = new Intent(Intent.ACTION_MAIN, null);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        mContext.startActivity(homeIntent);
    }

    public void updatePrefs(SharedPreferences prefs, String key) {
        if (key != null && key.equals(SettingsActivity.PREF_LAYOUT_STYLE)) {
            String layoutStyle = prefs.getString(SettingsActivity.PREF_LAYOUT_STYLE, "1");
            mLayoutStyle = Integer.valueOf(layoutStyle);
            switchLayout();
        }
        mLayout.updatePrefs(prefs, key);
        mGestureView.updatePrefs(prefs, key);
    }

    public void toggleLastApp(boolean close) {
        if (mLoadedTasksOriginal.size() < 2) {
            if (close) {
                hide(true);
            }
            return;
        }

        TaskDescription ad = mLoadedTasksOriginal.get(1);
        switchTask(ad, close, true);
    }

    public void startIntentFromtString(String intent, boolean close) {
        if (close) {
            hide(true);
        }
        if (mMainTaskId != INVALID_TASK_ID) {
            startIntentInStage(intent, STAGE_POSITION_BOTTOM_OR_RIGHT);
        } else {
            startIntentFromtString(mContext, intent);
        }
    }

    public static void startIntentFromtString(Context context, String intent) {
        try {
            Intent intentapp = Intent.parseUri(intent, 0);
            SwitchStatistics.getInstance(context).traceStartIntent(intentapp);
            context.startActivity(intentapp);
        } catch (URISyntaxException e) {
            Log.e(TAG, "URISyntaxException: [" + intent + "]");
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "ActivityNotFound: [" + intent + "]");
        }
    }

    public void onConfigurationChanged(int height) {
        if (mLayout.isShowing()) {
            // close on rotate
            mLayout.hide(true);
        }
        mLayout.updateLayout();
        if (mGestureView.isShowing()) {
            mGestureView.updateDragHandlePosition(height);
        }
    }

    public void startApplicationDetailsActivity(String packageName) {
        hide(true);
        startApplicationDetailsActivity(mContext, packageName);
    }

    public static void startApplicationDetailsActivity(Context context, String packageName) {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null));
        intent.setComponent(intent.resolveActivity(context.getPackageManager()));
        TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(intent).startActivities();
    }

    public void startSettingsActivity() {
        hide(true);
        startSettingsActivity(mContext);
    }

    public static void startSettingsActivity(Context context) {
        Intent intent = new Intent(Settings.ACTION_SETTINGS, null);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        context.startActivity(intent);
    }

    public void startOmniSwitchSettingsActivity() {
        hide(true);
        startOmniSwitchSettingsActivity(mContext);
    }

    public static void startOmniSwitchSettingsActivity(Context context) {
        Intent mainActivity = new Intent(context,
                SettingsActivity.class);
        mainActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        context.startActivity(mainActivity);
    }

    public void shutdownService() {
        mLayout.shutdownService();
    }

    public void slideLayout(float distanceX) {
        mLayout.slideLayout(distanceX);
    }

    public boolean finishSlideLayout() {
        return mLayout.finishSlideLayout();
    }

    public void openSlideLayout(boolean fromFling) {
        mLayout.openSlideLayout(fromFling);
    }

    public void canceSlideLayout(boolean fromFling) {
        mLayout.canceSlideLayout(fromFling);
    }

    public List<TaskDescription> getTasks() {
        return mLoadedTasks;
    }

    public void clearTasks() {
        mLoadedTasks.clear();
        mLoadedTasksOriginal.clear();
        mLayout.notifiyRecentsListChanged();
    }

    private TaskDescription getCurrentTopTask() {
        if (mLoadedTasksOriginal.size() >= 1) {
            TaskDescription ad = mLoadedTasksOriginal.get(0);
            return ad;
        } else {
            return null;
        }
    }

    public void lockToCurrentApp(boolean close) {
        TaskDescription ad = getCurrentTopTask();
        if (ad != null) {
            lockToApp(ad, close);
        }
    }

    public void lockToApp(TaskDescription ad, boolean close) {
        try {
            if (!ActivityTaskManager.getService().isInLockTaskMode()) {
                switchTask(ad, false, false);
                ActivityTaskManager.getService().startSystemLockTaskMode(ad.getPersistentTaskId());
                if (DEBUG) {
                    Log.d(TAG, "lock app " + ad.getLabel() + " " + ad.getPersistentTaskId());
                }
            }
        } catch (RemoteException e) {
        }
        if (close) {
            hide(true);
        }
    }

    public void stopLockToApp(boolean close) {
        try {
            if (ActivityTaskManager.getService().isInLockTaskMode()) {
                ActivityTaskManager.getService().stopSystemLockTaskMode();
                if (DEBUG) {
                    Log.d(TAG, "stop lock app");
                }
            }
        } catch (RemoteException e) {
        }
        if (close) {
            hide(true);
        }
    }

    public void toggleLockToApp(boolean close) {
        try {
            if (ActivityTaskManager.getService().isInLockTaskMode()) {
                stopLockToApp(false);
            } else {
                lockToCurrentApp(false);
            }
        } catch (RemoteException e) {
        }
        if (close) {
            hide(true);
        }
    }

    public void forceStop(TaskDescription ad, boolean close) {
        if (mConfiguration.mRestrictedMode) {
            return;
        }

        if (close) {
            hide(false);
        }

        mAm.forceStopPackage(ad.getPackageName());
        if (DEBUG) {
            Log.d(TAG, "forceStop " + ad.getLabel());
        }

        if (!close) {
            ad.setKilled();
            removeTaskFromList(ad);
            mLayout.refresh();
        }
    }

    public void toggleLockedApp(TaskDescription ad, boolean isLockedApp, boolean refresh) {
        List<String> lockedAppsList = mConfiguration.mLockedAppList;
        if (DEBUG) {
            Log.d(TAG, "toggleLockedApp " + lockedAppsList);
        }
        if (isLockedApp) {
            Utils.removedFromLockedApps(mContext, ad.getPackageName(), lockedAppsList);
        } else {
            Utils.addToLockedApps(mContext, ad.getPackageName(), lockedAppsList);
        }
        ad.setLocked(!isLockedApp);
        ad.setNeedsUpdate(true);
        if (refresh) {
            mLayout.refresh();
        }
    }

    private void removeTaskFromList(TaskDescription ad) {
        mLoadedTasks.remove(ad);
        mLoadedTasksOriginal.remove(ad);
        mLayout.notifiyRecentsListChanged();
    }

    private void removeTask(int taskid) {
        try {
            mIAm.removeTask(taskid);
        } catch (RemoteException e) {
            Log.e(TAG, "removeTask failed", e);
        }
    }

    public void dockTask(TaskDescription mainTask, boolean close) {
        if (close) {
            hide(false);
        }

        int taskId = mainTask.persistentTaskId;

        if (mSideTaskId != INVALID_TASK_ID) {
            // just replace main task
            launchTask(taskId, STAGE_POSITION_TOP_OR_LEFT);
        } else {
            // find valid initial side task for split
            Optional<TaskDescription> sideTask = mLoadedTasksOriginal.stream().filter(new Predicate<TaskDescription>() {
                @Override
                public boolean test(TaskDescription taskDescription) {
                    return taskDescription.persistentTaskId != taskId && taskDescription.isSupportsSplitScreen();
                }
            }).findFirst();
            if (sideTask.isPresent()) {
                TaskDescription sideTaskDescription = sideTask.get();
                int sideTaskId = sideTaskDescription.persistentTaskId;

                launchTask(taskId, STAGE_POSITION_TOP_OR_LEFT);

                // TODO - this is ugly but seems to work
                // without delay its NOT working correctly
                mHandler.postDelayed(() -> launchTask(sideTaskId, STAGE_POSITION_BOTTOM_OR_RIGHT), 500);
            } else {
                // we can not find valid side task so this will be a normal launch
                launchTask(taskId, STAGE_POSITION_TOP_OR_LEFT);
            }
        }
    }

    private InstanceId getInstanceId() {
        return mInstanceSequence.newInstanceId();
    }

    private void startIntentInStage(String intent, int stagePosition) {
        try {
            Intent intentapp = Intent.parseUri(intent, 0);
            PendingIntent taskPendingIntent = PendingIntent.getActivity(mContext, 0 /* requestCode */, intentapp,
                    FLAG_MUTABLE, null /* opts */);
            launchIntent(taskPendingIntent, new Intent(), stagePosition, getInstanceId());
        } catch (URISyntaxException e) {
            Log.e(TAG, "URISyntaxException: [" + intent + "]");
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "ActivityNotFound: [" + intent + "]");
        }
    }

    private void launchIntent(PendingIntent taskPendingIntent,
                              Intent fillInIntent, int stagePosition,
                              InstanceId shellInstanceId) {

        try {
            Log.d(TAG, "launchIntent " + taskPendingIntent);
            mSplitScreen.startIntent(taskPendingIntent,
                    fillInIntent,
                    stagePosition,
                    null,
                    shellInstanceId);
        } catch (RemoteException e) {
            Log.e(TAG, "launchIntent", e);
        }
    }

    private void launchTask(int taskId, int stageType) {
        try {
            Log.d(TAG, "startTask " + taskId + " " + stageType);
            mSplitScreen.startTask(taskId, stageType, null);
        } catch (RemoteException e) {
            Log.e(TAG, "launchTask", e);
        }
    }

    private void exitSplitScreen() {
        try {
            Log.d(TAG, "exitSplitScreen");
            mSplitScreen.exitSplitScreen(INVALID_TASK_ID);
        } catch (RemoteException e) {
            Log.e(TAG, "exitSplitScreen", e);
        }
    }

    private boolean isValidSideTask(TaskDescription ad) {
        if (mMainTaskId != INVALID_TASK_ID && ad.isSupportsSplitScreen()) {
            if (mMainTaskId != ad.persistentTaskId) {
                if (mSideTaskId != INVALID_TASK_ID && mSideTaskId == ad.persistentTaskId) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    public boolean isValidMainTask(TaskDescription ad) {
        if (Utils.isSplitScreenExternal(mContext) && ad.isSupportsSplitScreen()) {
            if (mMainTaskId != ad.persistentTaskId) {
                if (mSideTaskId != INVALID_TASK_ID && mSideTaskId == ad.persistentTaskId) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }
}
