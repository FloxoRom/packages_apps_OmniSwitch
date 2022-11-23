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

import org.omnirom.omniswitch.SwitchConfiguration;
import org.omnirom.omniswitch.TaskDescription;
import org.omnirom.omniswitch.RecentTasksLoader;
import org.omnirom.omniswitch.R;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.os.Handler;
import android.util.AttributeSet;
import android.widget.TextView;
import android.util.Log;

public class PackageTextView extends TextView {
    private static final String TAG = "PackageTextView";
    private static boolean DEBUG = false;
    private String mIntent;
    private Drawable mOriginalImage;
    private TaskDescription mTask;
    private String mLabel;
    private Runnable mAction;
    private Handler mHandler = new Handler();

    public PackageTextView(Context context) {
        super(context);
    }

    public PackageTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PackageTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setIntent(String intent) {
        mIntent = intent;
    }

    public String getIntent() {
        return mIntent;
    }

    public void setOriginalImage(Drawable image) {
        mOriginalImage = image;
    }

    public Drawable getOriginalImage() {
        return mOriginalImage;
    }

    public TaskDescription getTask() {
        return mTask;
    }

    public void setTask(TaskDescription task) {
        mTask = task;
        mLabel = mTask.getLabel();
    }

    public String getLabel() {
        return mLabel;
    }

    public void setLabel(String label) {
        mLabel = label;
    }

    public void runAction() {
        if (mAction != null) {
            mAction.run();
        }
    }

    public void setAction(Runnable action) {
        mAction = action;
    }

    public boolean isAction() {
        return mAction != null;
    }

    @Override
    public String toString() {
        return getLabel().toString();
    }

    public void setTaskInfo(SwitchConfiguration configuration) {
        if (getTask() != null) {
            // now we need to make sure we have the correct icon and label
            if (getTask().getIcon() == null) {
                RecentTasksLoader.getInstance(mContext).loadTaskInfo(getTask());
                mLabel = getTask().getLabel();
            }
            Drawable d= getTask().getIcon();
            d.setBounds(0, 0, configuration.mIconSizePx, configuration.mIconSizePx);
            setCompoundDrawables(null, d, null, null);
            if (getTask().isLocked()) {
                setBackgroundColor(getResources().getColor(R.color.locked_task_bg_color));
            } else {
                setBackgroundColor(0);
            }
            if (configuration.mShowLabels) {
                setText(getLabel());
            } else {
                setText("");
            }
        }
    }
}
