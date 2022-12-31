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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

public final class TaskDescription {
    final int taskId; // application task id for curating apps
    final int persistentTaskId; // persistent id
    final Intent intent; // launch intent for application
    private Drawable mIcon; // application package icon
    private boolean mIsActive;
    private boolean mKilled;
    private ThumbChangeListener mListener;
    private boolean mThumbLoading;
    private ThumbnailData mThumb;
    private CharSequence mLabel;
    private boolean mLocked;
    private boolean mNeedsUpdate;
    private boolean mSupportsSplitScreen;
    private int mActivityPrimaryColor;
    private boolean mUseLightOnPrimaryColor;
    private boolean mMultiWindowMode;
    private String mPackageName;

    public static interface ThumbChangeListener {
        public void thumbChanged(int pesistentTaskId);
        public int getPersistentTaskId();
    }

    public TaskDescription(int _taskId, int _persistentTaskId, String packageName,
            Intent _intent, boolean supportsSplitScreen) {
        intent = _intent;
        taskId = _taskId;
        mPackageName = packageName;
        persistentTaskId = _persistentTaskId;
        mSupportsSplitScreen = supportsSplitScreen;
    }

    public Drawable getIcon() {
        return mIcon;
    }

    public void setIcon(Drawable icon) {
        mIcon = icon;
    }

    public int getTaskId() {
        return taskId;
    }

    public Intent getIntent() {
        return intent;
    }

    public int getPersistentTaskId() {
        return persistentTaskId;
    }

    public void setLabel(CharSequence label) {
        mLabel = label;
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public boolean isKilled() {
        return mKilled;
    }

    public void setKilled() {
        this.mKilled = true;
    }

    @Override
    public String toString() {
        return intent.toString();
    }

    public void setThumb(ThumbnailData thumb, boolean callListener) {
        mThumb = thumb;
        if (callListener) {
            callListener();
        }
    }

    public void setThumbChangeListener(ThumbChangeListener client) {
        mListener = client;
    }

    private void callListener() {
        if (mListener != null) {
            // only call back if the listener is still the one attached to us
            if (mListener.getPersistentTaskId() == persistentTaskId) {
                mListener.thumbChanged(persistentTaskId);
            }
        }
    }

    public boolean isThumbLoading() {
        return mThumbLoading;
    }

    public void setThumbLoading(boolean thumbLoading) {
        this.mThumbLoading = thumbLoading;
    }

    public ThumbnailData getThumb() {
        return mThumb;
    }

    public void setLocked(boolean value) {
        mLocked = value;
    }

    public boolean isLocked() {
        return mLocked;
    }

    public boolean isNeedsUpdate() {
        return mNeedsUpdate;
    }

    public void setNeedsUpdate(boolean value) {
        mNeedsUpdate = value;
    }

    public boolean isSupportsSplitScreen() {
        return mSupportsSplitScreen;
    }

    public void setTaskPrimaryColor(int activityColor) {
        mActivityPrimaryColor = activityColor;
        mUseLightOnPrimaryColor = Utils.computeContrastBetweenColors(mActivityPrimaryColor, Color.WHITE) > 3f;
    }

    public int getTaskPrimaryColor() {
        return mActivityPrimaryColor;
    }

    public boolean useLightOnPrimaryColor() {
        return mUseLightOnPrimaryColor;
    }
}
