/*
 *  Copyright (C) 2019 The OmniROM Project
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
package org.omnirom.omniswitch.launcher;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.text.TextUtils;
import android.view.Gravity;

import org.omnirom.omniswitch.R;
import org.omnirom.omniswitch.SwitchConfiguration;
import org.omnirom.omniswitch.ui.FavoriteView;
import org.omnirom.omniswitch.ui.PackageTextView;

public class LauncherFavoriteView extends FavoriteView {

    public LauncherFavoriteView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private int getTextColor() {
        TypedArray array = mContext.obtainStyledAttributes(new int[]{R.attr.workspaceTextColor});
        int color = array.getColor(0, 0);
        array.recycle();
        return color;
    }

    private int getShadowColor() {
        TypedArray array = mContext.obtainStyledAttributes(new int[]{R.attr.workspaceShadowColor});
        int color = array.getColor(0, 0);
        array.recycle();
        return color;
    }

    private boolean needsShadow() {
        TypedArray ta = mContext.obtainStyledAttributes(new int[] {R.attr.isWorkspaceDarkText});
        boolean isDark = ta.getBoolean(0, false);
        ta.recycle();
        return !isDark;
    }

    @Override
    protected PackageTextView getPackageItemTemplate() {
        PackageTextView item = new PackageTextView(mContext);
        item.setTextColor(getTextColor());
        if (needsShadow()) {
            item.setShadowLayer(5f, 0, 0, getShadowColor());
        }
        item.setTextSize(mConfiguration.mLabelFontSize);
        item.setEllipsize(TextUtils.TruncateAt.END);
        item.setGravity(Gravity.CENTER);
        item.setLayoutParams(getListItemParams());
        item.setPadding(0, mConfiguration.mIconBorderPx, 0, 0);
        item.setMaxLines(1);
        item.setTypeface(mLabelFont);
        item.setBackgroundResource(R.drawable.ripple_dark);
        return item;
    }
}
