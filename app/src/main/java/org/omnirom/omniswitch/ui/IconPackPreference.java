/*
 *  Copyright (C) 2017 The OmniROM Project
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

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.omnirom.omniswitch.R;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;


public class IconPackPreference  extends  Preference {
    public IconPackPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public IconPackPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void init() {
        String currentPack = getPersistedString("");
        if (!currentPack.isEmpty()) {
            try {
                ApplicationInfo info = getContext().getPackageManager().getApplicationInfo(currentPack, 0);
                setIcon(info.loadIcon(getContext().getPackageManager()));
                setSummary(info.loadLabel(getContext().getPackageManager()));
            } catch (PackageManager.NameNotFoundException e) {
            }
        } else {
            setNone();
        }
    }

    private void setNone() {
        setIcon(getContext().getResources().getDrawable(R.drawable.ic_launcher));
        setSummary("None");
    }

    public void showDialog() {
        final Map<String, IconPackInfo> packages = loadAvailableIconPacks();
        final IconAdapter adapter = new IconAdapter(getContext(), packages, getPersistedString(""));
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int position) {
                String item = adapter.getItem(position);
                persistString(item);
                if (!item.isEmpty()) {
                    IconPackInfo packInfo = packages.get(item);
                    setIcon(packInfo.icon);
                    setSummary(packInfo.label);
                } else {
                    setNone();
                }
            }
        });
        builder.show();
    }

    private Map<String, IconPackInfo> loadAvailableIconPacks() {
        Map<String, IconPackInfo> iconPacks = new HashMap<>();
        List<ResolveInfo> list;
        PackageManager pm = getContext().getPackageManager();
        list = pm.queryIntentActivities(new Intent("com.novalauncher.THEME"), 0);
        list.addAll(pm.queryIntentActivities(new Intent("org.adw.launcher.icons.ACTION_PICK_ICON"), 0));
        list.addAll(pm.queryIntentActivities(new Intent("com.dlto.atom.launcher.THEME"), 0));
        list.addAll(pm.queryIntentActivities(new Intent("android.intent.action.MAIN").addCategory("com.anddoes.launcher.THEME"), 0));
        for (ResolveInfo info : list) {
            iconPacks.put(info.activityInfo.packageName, new IconPackInfo(info, pm));
        }
        return iconPacks;
    }

    private static class IconPackInfo {
        String packageName;
        CharSequence label;
        Drawable icon;

        IconPackInfo(ResolveInfo r, PackageManager packageManager) {
            packageName = r.activityInfo.packageName;
            icon = r.loadIcon(packageManager);
            label = r.loadLabel(packageManager);
        }

        public IconPackInfo(String label, Drawable icon, String packageName) {
            this.label = label;
            this.icon = icon;
            this.packageName = packageName;
        }
    }

    private static class IconAdapter extends BaseAdapter {
        ArrayList<IconPackInfo> mSupportedPackages;
        LayoutInflater mLayoutInflater;
        String mCurrentIconPack;

        IconAdapter(Context context, Map<String, IconPackInfo> supportedPackages, String currentPack) {
            mLayoutInflater = LayoutInflater.from(context);
            mSupportedPackages = new ArrayList<>(supportedPackages.values());
            Collections.sort(mSupportedPackages, new Comparator<IconPackInfo>() {
                @Override
                public int compare(IconPackInfo lhs, IconPackInfo rhs) {
                    return lhs.label.toString().compareToIgnoreCase(rhs.label.toString());
                }
            });

            Resources res = context.getResources();
            String defaultLabel = "None";
            Drawable icon = res.getDrawable(R.drawable.ic_launcher);
            mSupportedPackages.add(0, new IconPackInfo(defaultLabel, icon, ""));
            mCurrentIconPack = currentPack;
        }

        @Override
        public int getCount() {
            return mSupportedPackages.size();
        }

        @Override
        public String getItem(int position) {
            return mSupportedPackages.get(position).packageName;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.iconpack_view, null);
            }
            IconPackInfo info = mSupportedPackages.get(position);
            TextView txtView = (TextView) convertView.findViewById(R.id.title);
            txtView.setText(info.label);
            ImageView imgView = (ImageView) convertView.findViewById(R.id.icon);
            imgView.setImageDrawable(info.icon);
            RadioButton radioButton = (RadioButton) convertView.findViewById(R.id.radio);
            radioButton.setChecked(info.packageName.equals(mCurrentIconPack));
            return convertView;
        }
    }

}
