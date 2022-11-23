/*
 *  Copyright (C) 2018 The OmniROM Project
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.ListPreference;
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
import org.omnirom.omniswitch.SettingsActivity;

public class IconShapePreference extends ListPreference {

    public IconShapePreference(Context context) {
        this(context, null);
    }

    public IconShapePreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IconShapePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayoutResource(R.layout.preference_iconshape);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        init();
        return super.onCreateView(parent);
    }

    private void init() {
        try {
            PackageManager pm = getContext().getPackageManager();
            Intent settingsActivity = new Intent(getContext(), SettingsActivity.class);
            Drawable icon = pm.getActivityIcon(settingsActivity);
            setIcon(icon);
        } catch (NameNotFoundException e) {
        }
    }
}

