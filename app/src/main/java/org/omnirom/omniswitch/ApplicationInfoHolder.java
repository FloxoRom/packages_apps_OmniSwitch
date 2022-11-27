package org.omnirom.omniswitch;

import android.content.Intent;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class ApplicationInfoHolder
{
    String mPackageName;
    Drawable mIcon;
    CharSequence mLabel;
    Intent mIntent;

    @Override
    public int hashCode() {
        return mPackageName.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return obj instanceof ApplicationInfoHolder && ((ApplicationInfoHolder) obj).mPackageName.equals(mPackageName);
    }

    @NonNull
    @Override
    public String toString() {
        return mPackageName + " " + mLabel;
    }

    public ApplicationInfoHolder(String packageName, CharSequence label, Drawable icon, Intent intent) {
        mPackageName = packageName;
        mLabel = label;
        mIcon = icon;
        mIntent = intent;
    }
}
