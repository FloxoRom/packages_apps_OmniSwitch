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

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import android.util.LruCache;
import android.window.TaskSnapshot;

import org.omnirom.omniswitch.ui.BitmapUtils;
import org.omnirom.omniswitch.ui.IconPackHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static android.graphics.Bitmap.Config.ARGB_8888;

public class RecentTasksLoader {
    private static final String TAG = "RecentTasksLoader";
    private static final boolean DEBUG = true;
    private static final int TASK_INIT_LOAD = 8;

    private Context mContext;
    private AsyncTask<Void, List<TaskDescription>, Void> mTaskLoader;
    private AsyncTask<Void, Void, Void> mTaskInfoLoader;
    private AsyncTask<Void, TaskDescription, Void> mThumbnailLoader;
    private Handler mHandler;
    private List<TaskDescription> mLoadedTasks;
    private List<TaskDescription> mLoadedTasksOriginal;
    private boolean mPreloaded;
    private SwitchManager mSwitchManager;
    private ActivityManager mActivityManager;
    private Bitmap mDefaultThumbnail;
    private PreloadTaskRunnable mPreloadTasksRunnable;
    private boolean mHasThumbPermissions;
    private SwitchConfiguration mConfiguration;
    private PackageManager mPackageManager;
    private Drawable mDefaultAppIcon;
    private Set<String> mLockedAppsList;
    private LauncherApps mLauncherApps;
    private LruCache<String, TaskApplicationInfo> mTaskInfoCache;

    class TaskApplicationInfo {
        String mPackageName;
        Drawable mIcon;
        String mLabel;

        @Override
        public int hashCode() {
            return mPackageName.hashCode();
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            return obj instanceof TaskApplicationInfo && ((TaskApplicationInfo) obj).mPackageName.equals(mPackageName);
        }

        @NonNull
        @Override
        public String toString() {
            return mPackageName + " " + mLabel;
        }

        public TaskApplicationInfo(String packaName, String label, Drawable icon) {
            mPackageName = packaName;
            mLabel = label;
            mIcon = icon;
        }


    }
    private enum State {
        LOADING, IDLE
    }

    private State mState = State.IDLE;

    private static RecentTasksLoader sInstance;

    public static RecentTasksLoader getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new RecentTasksLoader(context);
        }
        return sInstance;
    }

    public static void killInstance() {
        sInstance = null;
    }

    private RecentTasksLoader(Context context) {
        mContext = context;
        mHandler = new Handler();
        mLoadedTasks = new CopyOnWriteArrayList<TaskDescription>();
        mLoadedTasksOriginal = new CopyOnWriteArrayList<TaskDescription>();
        mLockedAppsList = new HashSet<String>();
        mActivityManager = (ActivityManager)
                mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mPackageManager = mContext.getPackageManager();
        mDefaultThumbnail = Bitmap.createBitmap(1, 1, ARGB_8888);
        mDefaultThumbnail.setHasAlpha(true);
        mDefaultThumbnail.eraseColor(0x00ffffff);
        mHasThumbPermissions = hasSystemPermission(context);
        mConfiguration = SwitchConfiguration.getInstance(mContext);
        mDefaultAppIcon = BitmapUtils.getDefaultActivityIcon(mContext);
        mLauncherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        mTaskInfoCache = new LruCache<>(128);
    }

    private boolean isCurrentHomeActivity(ComponentName component,
                                          ActivityInfo homeInfo) {
        if (homeInfo == null) {
            homeInfo = new Intent(Intent.ACTION_MAIN).addCategory(
                    Intent.CATEGORY_HOME).resolveActivityInfo(mPackageManager, 0);
        }
        return homeInfo != null
                && homeInfo.packageName.equals(component.getPackageName())
                && homeInfo.name.equals(component.getClassName());
    }

    TaskDescription createTaskDescription(ActivityManager.RecentTaskInfo recentInfo) {
        int taskId = recentInfo.id;
        int persistentTaskId = recentInfo.persistentId;
        Intent baseIntent = recentInfo.baseIntent;
        String packageName = baseIntent.getComponent().getPackageName();
        boolean supportsSplitScreenMultiWindow = recentInfo.supportsSplitScreenMultiWindow;
        boolean multiWindowMode = android.app.WindowConfiguration.inMultiWindowMode(
                recentInfo.configuration.windowConfiguration.getWindowingMode());

        if (DEBUG)
            Log.v(TAG, "creating activity desc for id=" + persistentTaskId);
        TaskDescription ad = new TaskDescription(taskId,
                persistentTaskId, packageName, baseIntent,
                supportsSplitScreenMultiWindow, multiWindowMode);
        return ad;
    }

    private class PreloadTaskRunnable implements Runnable {
        @Override
        public void run() {
            if (DEBUG) {
                Log.d(TAG, "preload start " + System.currentTimeMillis());
            }
            loadTasksInBackground(0, true, true);
        }
    }

    public void preloadTasks() {
        mPreloadTasksRunnable = new PreloadTaskRunnable();
        mHandler.post(mPreloadTasksRunnable);
    }

    public void setSwitchManager(final SwitchManager manager) {
        mSwitchManager = manager;
    }

    public void cancelLoadingTasks() {
        if (DEBUG) {
            Log.d(TAG, "cancelLoadingTasks state = " + mState);
        }
        if (mTaskLoader != null) {
            mTaskLoader.cancel(true);
            mTaskLoader = null;
        }
        if (mTaskInfoLoader != null) {
            mTaskInfoLoader.cancel(true);
            mTaskInfoLoader = null;
        }
        if (mThumbnailLoader != null) {
            mThumbnailLoader.cancel(true);
            mThumbnailLoader = null;
        }
        if (mPreloadTasksRunnable != null) {
            mHandler.removeCallbacks(mPreloadTasksRunnable);
            mPreloadTasksRunnable = null;
        }
        mLoadedTasks.clear();
        mLoadedTasksOriginal.clear();
        mPreloaded = false;
        mState = State.IDLE;
    }

    public void loadTasksInBackground(int maxNumTasks, boolean withIcons, boolean withThumbs) {
        if (mPreloaded && mState != State.IDLE) {
            if (DEBUG) {
                Log.d(TAG, "recents preloaded: waiting for done");
            }
            return;
        }
        if (mPreloaded && mSwitchManager != null) {
            if (DEBUG) {
                Log.d(TAG, "recents preloaded " + mLoadedTasks);
            }
            mSwitchManager.update(mLoadedTasks, mLoadedTasksOriginal);
            loadMissingTaskInfo();
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "recents load");
        }
        mPreloaded = true;
        mState = State.LOADING;
        mLoadedTasks.clear();
        mLoadedTasksOriginal.clear();

        final long currentTime = SystemClock.elapsedRealtime();
        mLockedAppsList.clear();
        mLockedAppsList.addAll(mConfiguration.mLockedAppList);

        mTaskLoader = new AsyncTask<Void, List<TaskDescription>, Void>() {
            @Override
            protected void onProgressUpdate(
                    List<TaskDescription>... values) {
                if (!isCancelled()) {
                    if (mSwitchManager != null) {
                        if (DEBUG) {
                            Log.d(TAG, "recents loaded");
                        }
                        mSwitchManager.update(mLoadedTasks, mLoadedTasksOriginal);
                        loadMissingTaskInfo();
                    } else {
                        if (DEBUG) {
                            Log.d(TAG, "recents preloaded");
                        }
                    }
                }
            }

            @Override
            protected Void doInBackground(Void... params) {
                long start = System.currentTimeMillis();
                if (DEBUG) {
                    Log.d(TAG, "loadTasksInBackground " + mSwitchManager + " start " + start);
                }

                final int origPri = Process.getThreadPriority(Process.myTid());
                Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);

                final List<ActivityManager.RecentTaskInfo> recentTasks = mActivityManager
                        .getRecentTasks(maxNumTasks == 0 ? ActivityManager.getMaxRecentTasksStatic() : maxNumTasks,
                                ActivityManager.RECENT_IGNORE_UNAVAILABLE |
                                        ActivityManager.RECENT_WITH_EXCLUDED);

                int numTasks = recentTasks.size();
                final ActivityInfo homeInfo = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME).resolveActivityInfo(mPackageManager, 0);
                boolean isFirstValidTask = true;
                int preloadTaskNum = 0;
                final boolean withIconPack = IconPackHelper.getInstance(mContext).isIconPackLoaded();

                for (int i = 0; i < numTasks; ++i) {
                    if (isCancelled()) {
                        break;
                    }
                    final ActivityManager.RecentTaskInfo recentInfo = recentTasks
                            .get(i);
                    final ActivityManager.TaskDescription taskDescription = recentInfo.taskDescription;

                    if (DEBUG) {
                        Log.d(TAG, "" + i + " recent item = " + recentInfo.baseIntent + " " + recentInfo.taskDescription.getLabel());
                    }
                    TaskDescription item = createTaskDescription(recentInfo);

                    if (item == null) {
                        if (DEBUG) {
                            Log.d(TAG, "" + i + " recent item skipped 1");
                        }
                        continue;
                    }

                    if (mLockedAppsList.contains(item.getPackageName())) {
                        item.setLocked(true);
                    }

                    Intent intent = new Intent(recentInfo.baseIntent);
                    if (recentInfo.origActivity != null) {
                        intent.setComponent(recentInfo.origActivity);
                    }

                    if (taskDescription != null) {
                        item.setTaskPrimaryColor(taskDescription.getPrimaryColor());
                        item.setTaskBackgroundColor(taskDescription.getBackgroundColor());
                    }

                    // Don't load the current home activity.
                    if (isCurrentHomeActivity(intent.getComponent(), homeInfo)) {
                        if (DEBUG) {
                            Log.d(TAG, "" + i + " recent item skipped 2");
                        }
                        continue;
                    }

                    final String componentString = intent.getComponent().flattenToShortString();
                    if (componentString.contains(".recents.RecentsActivity") ||
                            componentString.contains("com.android.settings/.FallbackHome")) {
                        if (DEBUG) {
                            Log.d(TAG, "" + i + " recent item skipped 3");
                        }
                        continue;
                    }

                    boolean activeTask = true;
                    // never time filter locked tasks
                    if (mConfiguration.mFilterActive && !item.isLocked()) {
                        long lastActiveTime = recentInfo.lastActiveTime;
                        if (DEBUG) {
                            Log.d(TAG, intent.getComponent().getPackageName() + ": " + lastActiveTime + ":" + currentTime);
                        }
                        if (activeTask) {
                            // filter older then time
                            if (mConfiguration.mFilterTime != 0 && lastActiveTime < currentTime - mConfiguration.mFilterTime) {
                                if (DEBUG) {
                                    Log.d(TAG, intent.getComponent().getPackageName() + ": filter app not active since " + mConfiguration.mFilterTime);
                                }
                                activeTask = false;
                            }
                        }
                        if (activeTask) {
                            if (mConfiguration.mFilterRunning && recentInfo.id < 0) {
                                if (DEBUG) {
                                    Log.d(TAG, intent.getComponent().getPackageName() + ": filter app not running");
                                }
                                activeTask = false;
                            }
                        }
                    }

                    if (!activeTask) {
                        if (DEBUG) {
                            Log.d(TAG, "skip inactive task =" + recentInfo.baseIntent);
                        }
                        continue;
                    }

                    // Check the first non-recents task, include this task even if it is marked as excluded
                    boolean isExcluded = (recentInfo.baseIntent.getFlags() & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                            == Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;

                    if (isExcluded && !isFirstValidTask) {
                        if (DEBUG) {
                            Log.d(TAG, "" + i + " recent item skipped 4");
                        }
                        continue;
                    }
                    isFirstValidTask = false;

                    mLoadedTasksOriginal.add(item);

                    if (item.isLocked() && mConfiguration.mTopSortLockedApps) {
                        mLoadedTasks.add(0, item);
                    } else {
                        mLoadedTasks.add(item);
                    }
                    if (preloadTaskNum < TASK_INIT_LOAD) {
                        if (withIcons) {
                            loadTaskInfo(item);
                        }
                        if (withThumbs) {
                            ThumbnailData b = getThumbnail(item.persistentTaskId);
                            if (b != null) {
                                item.setThumb(b, false);
                            }
                        }
                        preloadTaskNum++;
                    }
                }
                if (!isCancelled()) {
                    publishProgress(mLoadedTasks);
                }
                if (DEBUG) {
                    Log.d(TAG, "loadTasksInBackground end " + (System.currentTimeMillis() - start));
                }
                mState = State.IDLE;
                Process.setThreadPriority(origPri);
                return null;
            }
        };
        mTaskLoader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private ThumbnailData getThumbnail(int taskId) {
        try {
            TaskSnapshot snapshot = ActivityTaskManager.getService().getTaskSnapshot(taskId, true);
            if (snapshot != null) {
                if (DEBUG) {
                    Log.d(TAG, "getThumbnail " + taskId);
                }
                ThumbnailData data = new ThumbnailData(snapshot);
                return data;
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to retrieve snapshot", e);
        }
        return null;
    }

    /*void loadTaskIcon(TaskDescription td, boolean withIconPack, String label) {
        Drawable icon = getTaskIcon(td);
        if (icon == null) {
            icon = mDefaultAppIcon;
        }
        td.setIcon(icon);
    }*/

    private IconPackHelper getIconPackHelper() {
        return IconPackHelper.getInstance(mContext);
    }

    private Drawable getIconPackIcon(TaskApplicationInfo ti) {
        Resources resources;
        try {
            resources = mPackageManager.getResourcesForApplication(ti.mPackageName);
        } catch (PackageManager.NameNotFoundException e) {
            resources = null;
        }
        if (resources != null) {
            int iconId = getIconPackHelper().getResourceIdForActivityIcon(ti.mPackageName, ti.mLabel);
            if (iconId != 0) {
                return IconPackHelper.getInstance(mContext).getIconPackResources().getDrawable(iconId);
            }
            try {
                Drawable icon = BitmapUtils.compose(resources,
                        ti.mIcon, mContext, getIconPackHelper().getIconBackFor(ti.mLabel),
                        getIconPackHelper().getIconMask(), getIconPackHelper().getIconUpon(),
                        getIconPackHelper().getIconScale(), mConfiguration.mIconSize, mConfiguration.mDensity);

                return icon;
            } catch (Exception e) {
            }
        }
        return ti.mIcon;
    }

    public void loadThumbnail(final TaskDescription td) {
        if (!mHasThumbPermissions) {
            return;
        }
        if (td.isThumbLoading()) {
            return;
        }
        mThumbnailLoader = new AsyncTask<Void, TaskDescription, Void>() {
            @Override
            protected void onProgressUpdate(TaskDescription... values) {
            }

            @Override
            protected Void doInBackground(Void... params) {
                final int origPri = Process.getThreadPriority(Process.myTid());
                Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);

                if (DEBUG) {
                    Log.d(TAG, "late load thumb " + td + " " + td.persistentTaskId);
                }
                td.setThumbLoading(true);
                ThumbnailData b = getThumbnail(td.persistentTaskId);
                if (b != null) {
                    td.setThumbLoading(false);
                    td.setThumb(b, true);
                }

                Process.setThreadPriority(origPri);
                return null;
            }
        };
        mThumbnailLoader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void loadTaskInfo(final TaskDescription td) {
        synchronized (td) {
            TaskApplicationInfo ti = mTaskInfoCache.get(td.getPackageName());
            if (ti == null) {
                ApplicationInfo applicationInfo = getApplicationInfo(td.getPackageName(),
                        Process.myUserHandle(), 0);
                if (applicationInfo == null) {
                    Log.e(TAG, "Failed to get icon for task " + td.persistentTaskId + " " + td.getPackageName());
                    td.setIcon(mDefaultAppIcon);
                    td.setLabel("foo");
                    return;
                }
                String label = applicationInfo.loadLabel(mPackageManager).toString();
                Drawable icon = applicationInfo.loadIcon(mPackageManager);

                ti = new TaskApplicationInfo(td.getPackageName(), label, icon);
                mTaskInfoCache.put(ti.mPackageName, ti);
            }
            final boolean withIconPack = IconPackHelper.getInstance(mContext).isIconPackLoaded();
            if (withIconPack) {
                td.setIcon(getIconPackIcon(ti));
                td.setLabel(ti.mLabel);
            } else {
                td.setIcon(ti.mIcon);
                td.setLabel(ti.mLabel);
            }
        }
    }

    private void loadMissingTaskInfo() {
        mTaskInfoLoader = new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onProgressUpdate(Void... values) {
            }

            @Override
            protected Void doInBackground(Void... params) {
                long start = System.currentTimeMillis();
                final int origPri = Process.getThreadPriority(Process.myTid());
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

                for (TaskDescription td : mLoadedTasks) {
                    if (isCancelled()) {
                        break;
                    }
                    synchronized (td) {
                        if (DEBUG) {
                            Log.d(TAG, "late load task info " + td + " " + td.persistentTaskId);
                        }
                        loadTaskInfo(td);
                    }
                }
                if (DEBUG) {
                    Log.d(TAG, "loadMissingTaskInfo end " + (System.currentTimeMillis() - start));
                }
                Process.setThreadPriority(origPri);
                return null;
            }
        };
        mTaskInfoLoader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private boolean hasSystemPermission(Context context) {
        int result = context
                .checkCallingOrSelfPermission(android.Manifest.permission.READ_FRAME_BUFFER);
        return result == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    public Bitmap getDefaultThumb() {
        return mDefaultThumbnail;
    }

    private String getSettingsActivity() {
        return mContext.getPackageName() + "/.SettingsActivity";
    }

    private CharSequence getTaskTitle(TaskDescription td) {
        ApplicationInfo applicationInfo = getApplicationInfo(td.getPackageName(), Process.myUserHandle(), 0);
        if (applicationInfo == null) {
            Log.e(TAG, "Failed to get title for task " + td.taskId);
            return "";
        }
        return applicationInfo.loadLabel(mPackageManager);
    }

    private Drawable getTaskIcon(TaskDescription td) {
        ApplicationInfo applicationInfo = getApplicationInfo(td.getPackageName(), Process.myUserHandle(), 0);
        if (applicationInfo == null) {
            Log.e(TAG, "Failed to get icon for task " + td.taskId);
            return null;
        }
        return applicationInfo.loadIcon(mPackageManager);
    }

    private ApplicationInfo getApplicationInfo(String packageName, UserHandle user, int flags) {
        try {
            ApplicationInfo info = mLauncherApps.getApplicationInfo(packageName, flags, user);
            return (info.flags & ApplicationInfo.FLAG_INSTALLED) == 0 || !info.enabled
                    ? null : info;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    public void clearTaskInfoCache() {
        if (DEBUG) Log.d(TAG, "clearTaskInfoCache");
        mTaskInfoCache.evictAll();
    }
}
