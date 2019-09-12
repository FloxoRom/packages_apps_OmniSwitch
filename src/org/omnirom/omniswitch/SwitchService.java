/*
 *  Copyright (C) 2013 The OmniROM Project
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

import org.omnirom.omniswitch.launcher.Launcher;
import org.omnirom.omniswitch.ui.BitmapCache;
import org.omnirom.omniswitch.ui.BitmapUtils;
import org.omnirom.omniswitch.ui.IconPackHelper;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Binder;
import android.os.IBinder;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import java.util.Set;
import java.util.HashSet;

public class SwitchService extends Service {
    private final static String TAG = "OmniSwitch:SwitchService";
    private static boolean DEBUG = false;

    private static final int START_SERVICE_ERROR_ID = 0;
    private static final int START_PERMISSION_SETTINGS_ID = 1;
    public static final String DPI_CHANGE = "dpi_change";

    private RecentsReceiver mReceiver;
    private static SwitchManager mManager;
    private SharedPreferences mPrefs;
    private SharedPreferences.OnSharedPreferenceChangeListener mPrefsListener;
    private static SwitchConfiguration mConfiguration;
    private static int mUserId = -1;
    private Set<String> mPrefKeyFilter = new HashSet<String>();
    private static boolean mIsRunning;
    private static boolean mCommitSuicide;
    private static boolean mPreloadDone;
    private OverlayMonitor mOverlayMonitor;

    public static boolean isRunning() {
        return mIsRunning;
    }

    @Override
    public void onCreate() {
        try {
            super.onCreate();
            mConfiguration = SwitchConfiguration.getInstance(this);
            mConfiguration.initDefaults(this);

            if (!canDrawOverlayViews()) {
                createOverlayNotification();
                commitSuicide();
                return;
            }
            if(mConfiguration.mRestrictedMode){
                createErrorNotification();
            }
            mUserId = UserHandle.myUserId();
            Log.d(TAG, "started SwitchService " + mUserId);

            mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
            mPrefKeyFilter.add(SettingsActivity.PREF_SHOW_FAVORITE);
            mPrefKeyFilter.add(Launcher.WECLOME_SCREEN_DISMISSED);
            mPrefKeyFilter.add(Launcher.STATE_ESSENTIALS_EXPANDED);
            mPrefKeyFilter.add(Launcher.STATE_PANEL_SHOWN);
            if (DEBUG) {
                Log.d(TAG, "mPrefKeyFilter " + mPrefKeyFilter);
            }

            BitmapUtils.clearCachedColors();

            String layoutStyle = mPrefs.getString(SettingsActivity.PREF_LAYOUT_STYLE, "1");
            mManager = new SwitchManager(this, Integer.valueOf(layoutStyle));

            mReceiver = new RecentsReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(RecentsReceiver.ACTION_HANDLE_HIDE);
            filter.addAction(RecentsReceiver.ACTION_HANDLE_SHOW);
            filter.addAction(Intent.ACTION_USER_SWITCHED);
            filter.addAction(Intent.ACTION_SHUTDOWN);

            registerReceiver(mReceiver, filter);
            PackageManager.getInstance(this).updatePackageList();

            updatePrefs(mPrefs, null);

            mPrefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                public void onSharedPreferenceChanged(SharedPreferences prefs,
                        String key) {
                    try {
                        updatePrefs(prefs, key);
                    } catch(Exception e) {
                        Log.e(TAG, "updatePrefs", e);
                    }
                }
            };

            mPrefs.registerOnSharedPreferenceChangeListener(mPrefsListener);

            mOverlayMonitor = new OverlayMonitor(this);

            if (mConfiguration.mLaunchStatsEnabled) {
                SwitchStatistics.getInstance(this).loadStatistics();
            }
            mIsRunning = true;
        } catch(Exception e) {
            Log.e(TAG, "onCreate", e);
            commitSuicide();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "stopped SwitchService " + mUserId);

        try {
            unregisterReceiver(mReceiver);
            mPrefs.unregisterOnSharedPreferenceChangeListener(mPrefsListener);
        } catch(IllegalArgumentException e) {
            // ignored on purpose
        }
        mOverlayMonitor.unregisterReceiver(this);

        if (mManager != null) {
            mManager.killManager();
            mManager.shutdownService();
        }

        if (mConfiguration.mLaunchStatsEnabled) {
            SwitchStatistics.getInstance(this).saveStatistics();
        }
        mIsRunning = false;
        BitmapCache.getInstance(this).clear();

        mCommitSuicide = false;
    }

    public class LocalBinder extends Binder {
        public SwitchService getService() {
            return SwitchService.this;
        }
    }
    private final LocalBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public static class RecentsReceiver extends BroadcastReceiver {
        public static final String ACTION_HANDLE_HIDE = "org.omnirom.omniswitch.ACTION_HANDLE_HIDE";
        public static final String ACTION_HANDLE_SHOW = "org.omnirom.omniswitch.ACTION_HANDLE_SHOW";
        public static final String ACTION_TOGGLE_OVERLAY = "org.omnirom.omniswitch.ACTION_TOGGLE_OVERLAY";
        public static final String ACTION_TOGGLE_OVERLAY2 = "org.omnirom.omniswitch.ACTION_TOGGLE_OVERLAY2";
        public static final String ACTION_PRELOAD_TASKS = "org.omnirom.omniswitch.ACTION_PRELOAD_TASKS";
        public static final String ACTION_HIDE_OVERLAY = "org.omnirom.omniswitch.ACTION_HIDE_OVERLAY";

        @Override
        public void onReceive(final Context context, Intent intent) {
            try {
                String action = intent.getAction();
                if(DEBUG){
                    Log.d(TAG, "onReceive " + action);
                }
                if (ACTION_HANDLE_SHOW.equals(action)){
                    if (mConfiguration.mDragHandleShow){
                        mManager.getSwitchGestureView().show();
                    }
                } else if (ACTION_HANDLE_HIDE.equals(action)){
                    mManager.getSwitchGestureView().hide();
                } else if (ACTION_TOGGLE_OVERLAY.equals(action)) {
                    if (DEBUG){
                        Log.d(TAG, "ACTION_TOGGLE_OVERLAY " + System.currentTimeMillis());
                    }
                    if (mManager.isShowing()) {
                        mManager.hide(false);
                    } else {
                        mManager.show();
                    }
                } else if (ACTION_TOGGLE_OVERLAY2.equals(action)) {
                    if (DEBUG){
                        Log.d(TAG, "ACTION_TOGGLE_OVERLAY2 " + System.currentTimeMillis());
                    }
                    if (mManager.isShowing()) {
                        mManager.hide(false);
                    } else {
                        if (mPreloadDone) {
                            mManager.showPreloaded();
                        } else {
                            // just in case
                            Log.e(TAG, "ACTION_TOGGLE_OVERLAY2 called without preload - fallback to ACTION_TOGGLE_OVERLAY");
                            mManager.show();
                        }
                    }
                    mPreloadDone = false;
                } else if (ACTION_HIDE_OVERLAY.equals(action)) {
                    if (DEBUG){
                        Log.d(TAG, "ACTION_HIDE_OVERLAY");
                    }
                    if (mManager.isShowing()) {
                        mManager.hide(false);
                    }
                } else if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                    int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                    Log.d(TAG, "user switch " + mUserId + "->" + userId);
                    if (userId != mUserId){
                        mManager.getSwitchGestureView().hide();
                    } else {
                        if (mConfiguration.mDragHandleShow){
                            mManager.getSwitchGestureView().show();
                        }
                    }
                } else if (Intent.ACTION_SHUTDOWN.equals(action)) {
                    Log.d(TAG, "ACTION_SHUTDOWN");
                    mManager.shutdownService();
                } else if (ACTION_PRELOAD_TASKS.equals(action)) {
                    if(DEBUG){
                        Log.d(TAG, "ACTION_PRELOAD_TASKS " + System.currentTimeMillis());
                    }
                    if (!mManager.isShowing()) {
                        mManager.beforePreloadTasks();
                        RecentTasksLoader.getInstance(context).cancelLoadingTasks();
                        RecentTasksLoader.getInstance(context).setSwitchManager(mManager);
                        RecentTasksLoader.getInstance(context).preloadTasks();
                        mPreloadDone = true;
                    }
                }
            } catch(Exception e) {
                Log.e(TAG,"onReceive", e);
            }
        }
    }

    private class OverlayMonitor extends BroadcastReceiver {
        private final String ACTION_OVERLAY_CHANGED = "android.intent.action.OVERLAY_CHANGED";

        OverlayMonitor(Context context) {
            context.registerReceiver(this, Utils.getPackageFilter("android", ACTION_OVERLAY_CHANGED));
        }

        public void unregisterReceiver(Context context) {
            try {
                context.unregisterReceiver(this);
            } catch (Exception e) {
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(DEBUG){
                Log.d(TAG, "onReceive " + action);
            }
            PackageManager.getInstance(context).updatePackageIcons();
            updatePrefs(mPrefs, SettingsActivity.PREF_ICON_SHAPE);
        }
    }

    public void updatePrefs(SharedPreferences prefs, String key) {
        if (isFilteredPrefsChange(key)) {
            return;
        }
        if(DEBUG){
            Log.d(TAG, "updatePrefs " + key);
        }
        BitmapUtils.clearCachedColors();
        IconPackHelper.getInstance(this).updatePrefs(prefs, key);
        mConfiguration.updatePrefs(prefs, key);
        mManager.updatePrefs(prefs, key);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if(DEBUG){
            Log.d(TAG, "onConfigurationChanged");
        }
        try {
            if (mIsRunning) {
                boolean updateDone = false;
                if (mConfiguration.onConfigurationChanged(this)) {
                    updatePrefs(mPrefs, DPI_CHANGE);
                    updateDone = true;
                }
                if (!updateDone && (mConfiguration.mBgStyle == SwitchConfiguration.BgStyle.SOLID_SYSTEM ||
                        mConfiguration.mDynamicDragHandleColor)) {
                    updatePrefs(mPrefs, SettingsActivity.PREF_BG_STYLE);
                }
                int newScreenHeight = Math.round(newConfig.screenHeightDp * mConfiguration.mDensity);
                if(DEBUG){
                    Log.d(TAG, "newScreenHeight = " + newScreenHeight);
                }
                mManager.updateLayout(newScreenHeight);
            }
        } catch(Exception e) {
            Log.e(TAG, "onConfigurationChanged", e);
        }
    }

    private void createErrorNotification() {
        final NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        final Notification notifyDetails = new Notification.Builder(this)
                .setContentTitle("OmniSwitch restricted mode")
                .setContentText("Failed to gain system permissions")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setShowWhen(false)
                .build();
        notificationManager.cancel(START_SERVICE_ERROR_ID);
        notificationManager.notify(START_SERVICE_ERROR_ID, notifyDetails);
    }

    private void disableAutoStart() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean(SettingsActivity.PREF_ENABLE, false).commit();
    }

    private void commitSuicide() {
        disableAutoStart();
        mCommitSuicide = true;
        Intent stopIntent = new Intent(this, SwitchService.class);
        stopService(stopIntent);
    }

    /**
     * fugly but save since the service is actually a singleton
     */
    public static SwitchManager getRecentsManager() {
        return mManager;
    }

    private boolean isFilteredPrefsChange(String key) {
        return mPrefKeyFilter.contains(key);
    }

    private void createOverlayNotification() {
        final NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        PendingIntent settingsIntent = PendingIntent.getActivity(this, START_PERMISSION_SETTINGS_ID,
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT);

        final Notification notifyDetails = new Notification.Builder(this)
                .setContentTitle(getResources().getString(R.string.dialog_overlay_perms_title))
                .setContentText(getResources().getString(R.string.dialog_overlay_perms_msg))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(settingsIntent)
                .setShowWhen(false)
                .build();

        notificationManager.cancel(START_SERVICE_ERROR_ID);
        notificationManager.notify(START_SERVICE_ERROR_ID, notifyDetails);
    }

    private boolean canDrawOverlayViews() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }
}
