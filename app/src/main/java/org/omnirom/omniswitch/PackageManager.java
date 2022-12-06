/*
 *  Copyright (C) 2014-2016 The OmniROM Project
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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import org.omnirom.omniswitch.ui.BitmapCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.Nullable;

public class PackageManager {
    private static final boolean DEBUG = false;
    private static final String TAG = "OmniSwitch:PackageManager";

    private Map<String, PackageItem> mInstalledPackagesMap;
    private List<PackageItem> mInstalledPackagesList;
    private Context mContext;
    private boolean mInitDone;
    private static PackageManager sInstance;
    private LauncherApps mLauncherApps;

    public static final String PACKAGES_UPDATED_TAG = "PACKAGES_UPDATED";

    public static class PackageItem implements Comparable<PackageItem> {
        private CharSequence title;
        private String packageName;
        private Intent intent;
        private ActivityInfo activity;
        private Drawable icon;

        public Intent getIntentRaw() {
            return intent;
        }

        public String getIntent() {
            return intent.toUri(0);
        }

        public CharSequence getTitle() {
            return title;
        }

        public ActivityInfo getActivityInfo() {
            return activity;
        }

        public String getPackageName() { return packageName; }

        @Override
        public int compareTo(PackageItem another) {
            int result = title.toString().compareToIgnoreCase(
                    another.title.toString());
            return result != 0 ? result : packageName
                    .compareTo(another.packageName);
        }

        @Override
        public String toString() {
            return getTitle().toString();
        }
    }

    public static PackageManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PackageManager();
        }
        sInstance.setContext(context);
        return sInstance;
    }

    private PackageManager() {
        mInstalledPackagesMap = new HashMap<String, PackageItem>();
        mInstalledPackagesList = new ArrayList<PackageItem>();
    }

    private void setContext(Context context) {
        mContext = context;
        mLauncherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
    }

    public synchronized List<PackageItem> getPackageList() {
        if (!mInitDone) {
            updatePackageList();
        }
        return mInstalledPackagesList;
    }

    public synchronized void reloadPackageList() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean old = prefs.getBoolean(PackageManager.PACKAGES_UPDATED_TAG, false);
        updatePackageList();
        prefs.edit().putBoolean(PackageManager.PACKAGES_UPDATED_TAG, !old).apply();
    }

    public synchronized void updatePackageList() {
        if (DEBUG) Log.d(TAG, "updatePackageList");
        Set<String> packageNameList = new HashSet<String>();

        mInstalledPackagesList.clear();
        mInstalledPackagesMap.clear();

        List<LauncherActivityInfo> installedAppsInfo2 = mLauncherApps.getActivityList(null, Process.myUserHandle());
        for (LauncherActivityInfo info : installedAppsInfo2) {
            ApplicationInfo appInfo = info.getApplicationInfo();
            ActivityInfo activity = info.getActivityInfo();

            final PackageItem item = new PackageItem();
            item.packageName = appInfo.packageName;
            if (DEBUG) {
                if (packageNameList.contains(item.packageName)) {
                    Log.d(TAG, "duplicate = " + item.packageName + " " + activity.name + " " + activity.packageName);
                }
            }
            packageNameList.add(item.packageName);

            item.activity = activity;

            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            intent.setComponent(info.getComponentName());
            item.intent = intent;

            item.title = info.getLabel();
            item.icon = info.getIcon(0);
            mInstalledPackagesMap.put(item.getPackageName(), item);
            mInstalledPackagesList.add(item);
        }

        updateFavorites();
        updateLockedApps(packageNameList);
        updateHiddenApps(packageNameList);

        Collections.sort(mInstalledPackagesList);
        mInitDone = true;
    }

    public synchronized void updatePackageIcons() {
        BitmapCache.getInstance(mContext).clear();
        RecentTasksLoader.getInstance(mContext).clearTaskInfoCache();
    }

    public synchronized @Nullable PackageItem getPackageItem(String pkgName) {
        if (!mInitDone) {
            updatePackageList();
        }
        return mInstalledPackagesMap.get(pkgName);
    }

    public void removePackageIconCache(String packageName) {
        BitmapCache.getInstance(mContext).removeBitmapToMemoryCache(packageName);
    }

    private synchronized void updateFavorites() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        String favoriteListString = prefs.getString(SettingsActivity.PREF_FAVORITE_APPS, "");
        List<String> favoriteList = new ArrayList<String>();
        Utils.parseCollection(favoriteListString, favoriteList);
        boolean changed = false;

        List<String> newFavoriteList = new ArrayList<String>();
        Iterator<String> nextFavorite = favoriteList.iterator();
        while (nextFavorite.hasNext()) {
            String favorite = nextFavorite.next();
            // DONT USE getPackageMap() here!
            if (!mInstalledPackagesMap.containsKey(favorite)) {
                changed = true;
                continue;
            }
            newFavoriteList.add(favorite);
        }
        if (changed) {
            prefs.edit()
                    .putString(SettingsActivity.PREF_FAVORITE_APPS, Utils.flattenCollection(newFavoriteList))
                    .apply();
        }
    }

    private synchronized void updateLockedApps(Set<String> packageNameList) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        String appListString = prefs.getString(SettingsActivity.PREF_LOCKED_APPS_LIST, "");
        List<String> appsList = new ArrayList<String>();
        Utils.parseLockedApps(appListString, appsList);
        boolean changed = false;

        List<String> newAppsList = new ArrayList<String>();
        Iterator<String> nextApp = appsList.iterator();
        while (nextApp.hasNext()) {
            String packageName = nextApp.next();
            if (!packageNameList.contains(packageName)) {
                changed = true;
                continue;
            }
            newAppsList.add(packageName);
        }
        if (changed) {
            prefs.edit()
                    .putString(SettingsActivity.PREF_LOCKED_APPS_LIST, TextUtils.join(",", newAppsList))
                    .apply();
        }
    }

    private synchronized void updateHiddenApps(Set<String> packageNameList) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        String hiddenAppsListString = prefs.getString(SettingsActivity.PREF_HIDDEN_APPS, "");
        List<String> hiddenAppsList = new ArrayList<String>();
        Utils.parseCollection(hiddenAppsListString, hiddenAppsList);
        boolean changed = false;

        List<String> newHiddenAppsList = new ArrayList<String>();
        Iterator<String> nextHiddenApp = hiddenAppsList.iterator();
        while (nextHiddenApp.hasNext()) {
            String hiddenApp = nextHiddenApp.next();
            // DONT USE getPackageMap() here!
            if (!mInstalledPackagesMap.containsKey(hiddenApp)) {
                changed = true;
                continue;
            }
            newHiddenAppsList.add(hiddenApp);
        }
        if (changed) {
            prefs.edit()
                    .putString(SettingsActivity.PREF_HIDDEN_APPS, Utils.flattenCollection(newHiddenAppsList))
                    .apply();
        }
    }

    public Drawable getPackageIcon(PackageItem item) {
        return item.icon;
    }
}

