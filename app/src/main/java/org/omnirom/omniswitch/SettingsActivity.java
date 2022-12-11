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

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.widget.Switch;
import android.widget.Toast;

import org.omnirom.omniswitch.launcher.topwidget.OmniJawsClient;
import org.omnirom.omniswitch.ui.BitmapUtils;
import org.omnirom.omniswitch.ui.CheckboxListDialog;
import org.omnirom.omniswitch.ui.FavoriteDialog;
import org.omnirom.omniswitch.ui.HiddenAppsDialog;
import org.omnirom.omniswitch.ui.IconPackHelper;
import org.omnirom.omniswitch.ui.IconPackPreference;
import org.omnirom.omniswitch.ui.SettingsGestureView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "OmniSwitch:SettingsActivity";

    public static final String PREF_OPACITY = "opacity";
    public static final String PREF_ANIMATE = "animate";
    public static final String PREF_ICON_SIZE = "icon_size";
    public static final String PREF_DRAG_HANDLE_LOCATION = "drag_handle_location_new";
    private static final String PREF_ADJUST_HANDLE = "adjust_handle";
    public static final String PREF_DRAG_HANDLE_COLOR_NEW = "drag_handle_color_new";
    public static final String PREF_SHOW_RAMBAR = "show_rambar";
    public static final String PREF_SHOW_LABELS = "show_labels";
    private static final String PREF_FAVORITE_APPS_CONFIG = "favorite_apps_config";
    public static final String PREF_FAVORITE_APPS = "favorite_apps_new";
    public static final String PREF_FAVORITE_APPS_OLD = "favorite_apps";
    public static final String PREF_HANDLE_POS_START_RELATIVE = "handle_pos_start_relative";
    public static final String PREF_HANDLE_HEIGHT = "handle_height";
    public static final String PREF_BUTTON_CONFIG = "button_config";
    public static final String PREF_BUTTONS_NEW = "buttons_new";
    public static final String PREF_BUTTON_DEFAULT_NEW = "0:1,1:1,2:1,3:0,4:1,5:1,6:0,7:1,8:1,9:0,10:0,11:0,12:0,13:0,14:0";
    public static final String PREF_ENABLE = "enable";
    public static final String PREF_DIM_BEHIND = "dim_behind";
    public static final String PREF_ICONPACK = "iconpack";
    public static final String PREF_SHOW_FAVORITE = "show_favorite";
    public static final String PREF_BUTTON_POS = "button_pos";
    public static final String PREF_BG_STYLE = "bg_style";
    public static final String PREF_LAYOUT_STYLE = "layout_style";
    public static final String PREF_APP_FILTER_TIME = "app_filter_time";
    public static final String PREF_THUMB_SIZE = "thumb_size";
    public static final String PREF_APP_FILTER_RUNNING = "app_filter_running";
    public static final String PREF_HANDLE_WIDTH_OLD = "handle_width";
    public static final String PREF_HANDLE_WIDTH = "handle_width_new";
    public static final String PREF_LAUNCHER_MODE = "launcher_mode";
    public static final String PREF_LAUNCH_STATS = "launch_stats";
    public static final String PREF_LAUNCH_STATS_DELETE = "launch_stats_delete";
    public static final String PREF_FAVORITE_APPS_CONFIG_STAT = "favorite_apps_config_stat";
    public static final String PREF_REVERT_RECENTS = "revert_recents";
    public static final String PREF_DIM_ACTION_BUTTON = "dim_action_buttons";
    public static final String PREF_LOCKED_APPS_LIST = "locked_apps_list";
    public static final String PREF_LOCKED_APPS_SORT = "locked_apps_sort";
    public static final String PREF_DRAG_HANDLE_DYNAMIC_COLOR = "drag_handle_dynamic_color";
    public static final String PREF_BLOCK_APPS_ON_SPLITSCREEN = "block_apps_on_splitscreen";
    private static final String PREF_HIDDEN_APPS_CONFIG = "hidden_apps_config";
    public static final String PREF_HIDDEN_APPS = "hidden_apps_new";
    public static final String PREF_HIDDEN_APPS_OLD = "hidden_apps";
    public static final String PREF_COLOR_TASK_HEADER = "color_task_header";
    public static final String PREF_BOTTOM_FAVORITES = "bottom_favorites";
    public static final String PREF_BUTTON_HIDE = "button_hide";
    public static final String PREF_THUMB_HEADER_SIDE = "thumb_header_position_side";

    public static final String WEATHER_ICON_PACK_PREFERENCE_KEY = "pref_weatherIconPack";
    public static final String SHOW_ALL_DAY_EVENTS_PREFERENCE_KEY = "pref_allDayEvents";
    public static final String SHOW_EVENTS_PERIOD_PREFERENCE_KEY = "pref_showEventsPeriod";
    public static final String SHOW_TODAY_PREFERENCE_KEY = "pref_showToday";
    public static final String SHOW_EVENTS_PREFERENCE_KEY = "pref_showEvents";
    private static final String DEFAULT_WEATHER_ICON_PACKAGE = "org.omnirom.omnijaws";
    private static final String DEFAULT_WEATHER_ICON_PREFIX = "outline";
    private static final String CHRONUS_ICON_PACK_INTENT = "com.dvtonder.chronus.ICON_PACK";
    public static final String SHOW_WEATHER_PREFERENCE_KEY = "pref_showWeather";

    public static int BUTTON_KILL_ALL = 0;
    public static int BUTTON_KILL_OTHER = 1;
    public static int BUTTON_TOGGLE_APP = 2;
    public static int BUTTON_HOME = 3;
    public static int BUTTON_SETTINGS = 4;
    public static int BUTTON_ALLAPPS = 5;
    public static int BUTTON_BACK = 6;
    public static int BUTTON_LOCK_APP = 7;
    public static int BUTTON_CLOSE = 8;
    public static int BUTTON_MENU = 9;
    public static int BUTTON_PHONE = 10;
    public static int BUTTON_CAMERA = 11;
    public static int BUTTON_ASSIST = 12;
    public static int BUTTON_GOOGLE_ASSISTANT = 13;
    public static int BUTTON_FLASHLIGHT = 14;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);
        setSupportActionBar(findViewById(R.id.toolbar));

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle(R.string.settings);
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getSupportFragmentManager().beginTransaction().replace(R.id.content, new SettingsFragment())
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements
            Preference.OnPreferenceChangeListener, IEditFavoriteActivity {
        private ListPreference mIconSize;
        private Preference mFavoriteAppsConfig;
        private Preference mAdjustHandle;
        private SharedPreferences mPrefs;
        private SettingsGestureView mGestureView;
        private Preference mButtonConfig;
        private String[] mButtonEntries;
        private Drawable[] mButtonImages;
        private String mButtons;
        private Switch mToggleServiceSwitch;
        private SharedPreferences.OnSharedPreferenceChangeListener mPrefsListener;
        private ListPreference mButtonPos;
        private ListPreference mBgStyle;
        private ListPreference mLayoutStyle;
        private ListPreference mAppFilterTime;
        private ListPreference mThumbSize;
        private SwitchPreference mEnable;
        private SwitchPreference mLauncherMode;
        private Preference mLaunchStatsDelete;
        private SwitchPreference mLaunchStats;
        private Preference mFavoriteAppsConfigStat;
        private SwitchPreference mRevertRecents;
        private SwitchPreference mBottomFavorites;
        private Preference mHiddenAppsConfig;
        private SwitchPreference mColorTaskHeader;
        private Handler mHandler;
        private SwitchPreference mButtonHide;
        private IconPackPreference mIconPack;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            mHandler = new Handler();
            mPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());

            addPreferencesFromResource(R.xml.recents_settings);

            mEnable = (SwitchPreference) findPreference(PREF_ENABLE);
            mEnable.setChecked(SwitchService.isRunning() && mPrefs.getBoolean(SettingsActivity.PREF_ENABLE, false));
            mEnable.setOnPreferenceChangeListener(this);
            mIconSize = (ListPreference) findPreference(PREF_ICON_SIZE);
            mIconSize.setOnPreferenceChangeListener(this);
            int idx = mIconSize.findIndexOfValue(mPrefs.getString(PREF_ICON_SIZE,
                    mIconSize.getEntryValues()[1].toString()));
            mIconSize.setValueIndex(idx);
            mIconSize.setSummary(mIconSize.getEntries()[idx]);
            mAdjustHandle = (Preference) findPreference(PREF_ADJUST_HANDLE);
            mButtonConfig = (Preference) findPreference(PREF_BUTTON_CONFIG);
            mButtons = mPrefs.getString(PREF_BUTTONS_NEW, PREF_BUTTON_DEFAULT_NEW);
            mFavoriteAppsConfig = (Preference) findPreference(PREF_FAVORITE_APPS_CONFIG);

            mButtonPos = (ListPreference) findPreference(PREF_BUTTON_POS);
            mButtonPos.setOnPreferenceChangeListener(this);
            idx = mButtonPos.findIndexOfValue(mPrefs.getString(PREF_BUTTON_POS,
                    mButtonPos.getEntryValues()[0].toString()));
            mButtonPos.setValueIndex(idx);
            mButtonPos.setSummary(mButtonPos.getEntries()[idx]);

            initButtons();

            mBgStyle = (ListPreference) findPreference(PREF_BG_STYLE);
            mBgStyle.setOnPreferenceChangeListener(this);
            idx = mBgStyle.findIndexOfValue(mPrefs.getString(PREF_BG_STYLE,
                    mBgStyle.getEntryValues()[0].toString()));
            mBgStyle.setValueIndex(idx);
            mBgStyle.setSummary(mBgStyle.getEntries()[idx]);

            mLayoutStyle = (ListPreference) findPreference(PREF_LAYOUT_STYLE);
            mLayoutStyle.setOnPreferenceChangeListener(this);
            idx = mLayoutStyle.findIndexOfValue(mPrefs.getString(PREF_LAYOUT_STYLE,
                    mLayoutStyle.getEntryValues()[0].toString()));
            mLayoutStyle.setValueIndex(idx);
            mLayoutStyle.setSummary(mLayoutStyle.getEntries()[idx]);

            mAppFilterTime = (ListPreference) findPreference(PREF_APP_FILTER_TIME);
            mAppFilterTime.setOnPreferenceChangeListener(this);
            idx = mAppFilterTime.findIndexOfValue(mPrefs.getString(PREF_APP_FILTER_TIME,
                    mAppFilterTime.getEntryValues()[0].toString()));
            mAppFilterTime.setValueIndex(idx);
            mAppFilterTime.setSummary(mAppFilterTime.getEntries()[idx]);

            mThumbSize = (ListPreference) findPreference(PREF_THUMB_SIZE);
            mThumbSize.setOnPreferenceChangeListener(this);
            idx = mThumbSize.findIndexOfValue(mPrefs.getString(PREF_THUMB_SIZE,
                    mThumbSize.getEntryValues()[2].toString()));
            mThumbSize.setValueIndex(idx);
            mThumbSize.setSummary(mThumbSize.getEntries()[idx]);
            mLauncherMode = (SwitchPreference) findPreference(PREF_LAUNCHER_MODE);
            mLaunchStats = (SwitchPreference) findPreference(PREF_LAUNCH_STATS);
            mLaunchStatsDelete = (Preference) findPreference(PREF_LAUNCH_STATS_DELETE);
            mFavoriteAppsConfigStat = (Preference) findPreference(PREF_FAVORITE_APPS_CONFIG_STAT);
            mRevertRecents = (SwitchPreference) findPreference(PREF_REVERT_RECENTS);
            mBottomFavorites = (SwitchPreference) findPreference(PREF_BOTTOM_FAVORITES);
            mColorTaskHeader = (SwitchPreference) findPreference(PREF_COLOR_TASK_HEADER);

            mButtonHide = (SwitchPreference) findPreference(PREF_BUTTON_HIDE);
            mButtonPos.setEnabled(!mButtonHide.isChecked());
            mButtonConfig.setEnabled(!mButtonHide.isChecked());

            boolean vertical = mLayoutStyle.getValue().equals("1");
            mRevertRecents.setEnabled(vertical);
            mThumbSize.setEnabled(vertical);
            mColorTaskHeader.setEnabled(vertical);
            mBottomFavorites.setEnabled(vertical);

            mHiddenAppsConfig = findPreference(PREF_HIDDEN_APPS_CONFIG);
            mIconPack = findPreference(PREF_ICONPACK);
            mIconPack.init();

            OmniJawsClient weatherClient = new OmniJawsClient(getContext());
            final ListPreference iconPack = (ListPreference) findPreference(WEATHER_ICON_PACK_PREFERENCE_KEY);
            if (!weatherClient.isOmniJawsServiceInstalled()) {
                PreferenceCategory launcherScreen = (PreferenceCategory) findPreference("category_launcher");
                launcherScreen.removePreference(iconPack);
            } else {
                String settingHeaderPackage = SwitchConfiguration.getWeatherIconPack(getContext());
                if (settingHeaderPackage == null) {
                    settingHeaderPackage = DEFAULT_WEATHER_ICON_PACKAGE + "." + DEFAULT_WEATHER_ICON_PREFIX;
                }

                List<String> entries = new ArrayList<String>();
                List<String> values = new ArrayList<String>();
                getAvailableWeatherIconPacks(entries, values);
                iconPack.setEntries(entries.toArray(new String[entries.size()]));
                iconPack.setEntryValues(values.toArray(new String[values.size()]));

                int valueIndex = iconPack.findIndexOfValue(settingHeaderPackage);
                if (valueIndex == -1) {
                    // no longer found
                    settingHeaderPackage = DEFAULT_WEATHER_ICON_PACKAGE + "." + DEFAULT_WEATHER_ICON_PREFIX;
                    valueIndex = iconPack.findIndexOfValue(settingHeaderPackage);
                }
                iconPack.setValueIndex(valueIndex >= 0 ? valueIndex : 0);
                iconPack.setSummary(iconPack.getEntry());
                iconPack.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        int valueIndex = iconPack.findIndexOfValue((String) newValue);
                        iconPack.setSummary(iconPack.getEntries()[valueIndex]);
                        return true;
                    }
                });
            }

            final ListPreference eventsPeriod = (ListPreference) findPreference(SHOW_EVENTS_PERIOD_PREFERENCE_KEY);
            eventsPeriod.setSummary(eventsPeriod.getEntry());
            eventsPeriod.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    int index = eventsPeriod.findIndexOfValue((String) newValue);
                    eventsPeriod.setSummary(eventsPeriod.getEntries()[index]);
                    return true;
                }
            });

            mPrefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                public void onSharedPreferenceChanged(SharedPreferences prefs,
                                                      String key) {
                    updatePrefs(prefs, key);
                }
            };

            updatePrefs(mPrefs, null);
        }

        @Override
        public void onPause() {
            if (mGestureView != null) {
                mGestureView.hide();
                mGestureView = null;
            }
            mPrefs.unregisterOnSharedPreferenceChangeListener(mPrefsListener);
            super.onPause();
        }

        @Override
        public void onResume() {
            mPrefs.registerOnSharedPreferenceChangeListener(mPrefsListener);
            mEnable.setChecked(SwitchService.isRunning() && mPrefs.getBoolean(SettingsActivity.PREF_ENABLE, false));
            super.onResume();
        }

        private class ButtonsApplyRunnable implements CheckboxListDialog.ApplyRunnable {
            public void apply(Map<Integer, Boolean> buttons) {
                mButtons = Utils.buttonMapToString(buttons);
                mPrefs.edit().putString(PREF_BUTTONS_NEW, mButtons).commit();
            }
        }

        @Override
        public boolean onPreferenceTreeClick(@NonNull Preference preference) {
            if (preference == mAdjustHandle) {
                if (mGestureView != null) {
                    mGestureView.hide();
                    mGestureView = null;
                }
                mGestureView = new SettingsGestureView(getContext());
                mGestureView.show();
                return true;
            } else if (preference == mButtonConfig) {
                Map<Integer, Boolean> buttons = Utils.buttonStringToMap(mButtons, PREF_BUTTON_DEFAULT_NEW);
                CheckboxListDialog dialog = new CheckboxListDialog(getContext(),
                        mButtonEntries, mButtonImages, buttons, new ButtonsApplyRunnable(),
                        getResources().getString(R.string.buttons_title));
                dialog.show();
                return true;
            } else if (preference == mFavoriteAppsConfig) {
                String favoriteListString = mPrefs.getString(PREF_FAVORITE_APPS, "");
                List<String> favoriteList = new ArrayList<String>();
                Utils.parseCollection(favoriteListString, favoriteList);
                doShowFavoritesList(favoriteList);
                return true;
            } else if (preference == mHiddenAppsConfig) {
                String hiddenAppsListString = mPrefs.getString(PREF_HIDDEN_APPS, "");
                Set<String> hiddenAppsList = new HashSet<String>();
                Utils.parseCollection(hiddenAppsListString, hiddenAppsList);
                doShowHiddenAppsList(hiddenAppsList);
                return true;
            } else if (preference == mFavoriteAppsConfigStat) {
                final List<String> favoriteList = Utils.getFavoriteListFromStats(getContext(), 10);
                if (favoriteList.size() < 5) {
                    new AlertDialog.Builder(getContext())
                            .setTitle(R.string.launch_stats_low_title)
                            .setMessage(R.string.launch_stats_low_notice)
                            .setCancelable(true)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(android.R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            doShowFavoritesList(favoriteList);
                                        }
                                    }).show();
                } else {
                    doShowFavoritesList(favoriteList);
                }
                return true;
            } else if (preference == mLauncherMode) {
                Utils.enableLauncherMode(getContext(), mLauncherMode.isChecked());
                return true;
            } else if (preference == mLaunchStats) {
                if (!mLaunchStats.isChecked()) {
                    SwitchStatistics.getInstance(getContext()).clear();
                }
                return true;
            } else if (preference == mLaunchStatsDelete) {
                SwitchStatistics.getInstance(getContext()).clear();
                Toast.makeText(getContext(), R.string.launch_stats_delete_notice, Toast.LENGTH_LONG).show();
                return true;
            } else if (preference == mButtonHide) {
                mButtonPos.setEnabled(!mButtonHide.isChecked());
                mButtonConfig.setEnabled(!mButtonHide.isChecked());
                return true;
            } else if (preference == mIconPack) {
                mIconPack.showDialog();
                return true;
            }
            return super.onPreferenceTreeClick(preference);
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (preference == mIconSize) {
                String value = (String) newValue;
                int idx = mIconSize.findIndexOfValue(value);
                mIconSize.setSummary(mIconSize.getEntries()[idx]);
                mIconSize.setValueIndex(idx);
                return true;
            } else if (preference == mButtonPos) {
                String value = (String) newValue;
                int idx = mButtonPos.findIndexOfValue(value);
                mButtonPos.setSummary(mButtonPos.getEntries()[idx]);
                mButtonPos.setValueIndex(idx);
                return true;
            } else if (preference == mBgStyle) {
                String value = (String) newValue;
                int idx = mBgStyle.findIndexOfValue(value);
                mBgStyle.setSummary(mBgStyle.getEntries()[idx]);
                mBgStyle.setValueIndex(idx);
                return true;
            } else if (preference == mLayoutStyle) {
                String value = (String) newValue;
                int idx = mLayoutStyle.findIndexOfValue(value);
                mLayoutStyle.setSummary(mLayoutStyle.getEntries()[idx]);
                mLayoutStyle.setValueIndex(idx);
                boolean vertical = mLayoutStyle.getValue().equals("1");
                mRevertRecents.setEnabled(vertical);
                mThumbSize.setEnabled(vertical);
                mColorTaskHeader.setEnabled(vertical);
                mBottomFavorites.setEnabled(vertical);
                return true;
            } else if (preference == mAppFilterTime) {
                String value = (String) newValue;
                int idx = mAppFilterTime.findIndexOfValue(value);
                mAppFilterTime.setSummary(mAppFilterTime.getEntries()[idx]);
                mAppFilterTime.setValueIndex(idx);
                return true;
            } else if (preference == mThumbSize) {
                String value = (String) newValue;
                int idx = mThumbSize.findIndexOfValue(value);
                mThumbSize.setSummary(mThumbSize.getEntries()[idx]);
                mThumbSize.setValueIndex(idx);
                return true;
            } else if (preference == mEnable) {
                boolean value = ((Boolean) newValue).booleanValue();
                startOmniSwitch(value);
                if (!value && mLauncherMode.isChecked()) {
                    Toast.makeText(getContext(), R.string.launcher_mode_enable_check, Toast.LENGTH_LONG).show();
                }
                return true;
            }
            return false;
        }

        @Override
        public void applyFavoritesChanges(List<String> favoriteList) {
            mPrefs.edit()
                    .putString(PREF_FAVORITE_APPS,
                            Utils.flattenCollection(favoriteList))
                    .commit();
        }

        @Override
        public void applyHiddenAppsChanges(Collection<String> hiddenAppsList) {
            mPrefs.edit()
                    .putString(PREF_HIDDEN_APPS,
                            Utils.flattenCollection(hiddenAppsList))
                    .commit();
        }

        private void initButtons() {
            mButtonEntries = getResources().getStringArray(R.array.button_entries);
            mButtonImages = new Drawable[mButtonEntries.length];
            int color = getContext().getResources().getColor(R.color.text_color_default);
            mButtonImages[0] = BitmapUtils.colorize(getResources(), color, getResources().getDrawable(R.drawable.kill_all));
            mButtonImages[1] = BitmapUtils.colorize(getResources(), color, getResources().getDrawable(R.drawable.kill_other));
            mButtonImages[2] = BitmapUtils.colorize(getResources(), color, getResources().getDrawable(R.drawable.ic_lastapp));
            mButtonImages[3] = BitmapUtils.colorize(getResources(), color, getResources().getDrawable(R.drawable.ic_sysbar_home));
            mButtonImages[4] = BitmapUtils.colorize(getResources(), color, getResources().getDrawable(R.drawable.ic_settings));
            mButtonImages[5] = BitmapUtils.colorize(getResources(), color, getResources().getDrawable(R.drawable.ic_apps));
            mButtonImages[6] = BitmapUtils.colorize(getResources(), color, getResources().getDrawable(R.drawable.ic_sysbar_back));
            mButtonImages[7] = BitmapUtils.colorize(getResources(), color, getResources().getDrawable(R.drawable.ic_pin));
            mButtonImages[8] = BitmapUtils.colorize(getResources(), color, getResources().getDrawable(R.drawable.ic_close));
            mButtonImages[9] = BitmapUtils.colorize(getResources(), color, getResources().getDrawable(R.drawable.ic_menu));
            mButtonImages[10] = BitmapUtils.colorize(getResources(), color, getResources().getDrawable(R.drawable.ic_phone));
            mButtonImages[11] = BitmapUtils.colorize(getResources(), color, getResources().getDrawable(R.drawable.ic_camera));
            mButtonImages[12] = BitmapUtils.colorize(getResources(), color, getResources().getDrawable(R.drawable.ic_assist));
            mButtonImages[13] = BitmapUtils.colorize(getResources(), color, getResources().getDrawable(R.drawable.ic_voice_assist));
            mButtonImages[14] = BitmapUtils.colorize(getResources(), color, getResources().getDrawable(R.drawable.ic_qs_flashlight));
        }

        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            // dont restart activity on orientation changes
            if (mGestureView != null && mGestureView.isShowing()) {
                mGestureView.handleRotation();
            }
        }

        public void updatePrefs(SharedPreferences prefs, String key) {
            if (!SwitchService.isRunning()) {
                IconPackHelper.getInstance(getContext()).updatePrefs(mPrefs, null);
            }
        }

        private void startOmniSwitch(boolean value) {
            Intent svc = new Intent(getContext(), SwitchService.class);
            if (value) {
                if (SwitchService.isRunning()) {
                    getContext().stopService(svc);
                }
                getContext().startService(svc);
            } else {
                if (SwitchService.isRunning()) {
                    getContext().stopService(svc);
                }
            }
        }

        private void doShowFavoritesList(List<String> favoriteList) {
            FavoriteDialog dialog = new FavoriteDialog(getContext(), this, favoriteList);
            dialog.show();
        }

        private void doShowHiddenAppsList(Collection<String> hiddenApsList) {
            HiddenAppsDialog dialog = new HiddenAppsDialog(getContext(), this, hiddenApsList);
            dialog.show();
        }

        private void getAvailableWeatherIconPacks(List<String> entries, List<String> values) {
            Intent i = new Intent();
            android.content.pm.PackageManager packageManager = getContext().getPackageManager();
            i.setAction("org.omnirom.WeatherIconPack");
            for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
                String packageName = r.activityInfo.packageName;
                String label = r.activityInfo.loadLabel(getContext().getPackageManager()).toString();
                if (label == null) {
                    label = r.activityInfo.packageName;
                }
                if (entries.contains(label)) {
                    continue;
                }
                if (packageName.equals(DEFAULT_WEATHER_ICON_PACKAGE)) {
                    values.add(0, r.activityInfo.name);
                } else {
                    values.add(r.activityInfo.name);
                }

                if (packageName.equals(DEFAULT_WEATHER_ICON_PACKAGE)) {
                    entries.add(0, label);
                } else {
                    entries.add(label);
                }
            }
            i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(CHRONUS_ICON_PACK_INTENT);
            for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
                String packageName = r.activityInfo.packageName;
                String label = r.activityInfo.loadLabel(getContext().getPackageManager()).toString();
                if (label == null) {
                    label = r.activityInfo.packageName;
                }
                if (entries.contains(label)) {
                    continue;
                }
                values.add(packageName + ".weather");
                entries.add(label);
            }
        }
    }
}
