package org.omnirom.omniswitch;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class ApplicationInfoHolder
{
    String mPackageName;
    Drawable mIcon;
    CharSequence mLabel;

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

    public ApplicationInfoHolder(String packaName, CharSequence label, Drawable icon) {
        mPackageName = packaName;
        mLabel = label;
        mIcon = icon;
    }


}
