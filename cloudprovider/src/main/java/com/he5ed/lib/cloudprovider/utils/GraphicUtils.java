/*
 * Copyright 2015 HE5ED.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.he5ed.lib.cloudprovider.utils;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * @hide
 */
public class GraphicUtils {

    /**
     * Tint the drawable resource with color
     *
     * @param res resources location
     * @param resId drawable resource id
     * @param tint color to apply to the image
     * @return the drawable after tint as a new copy, original resource will not be changed
     */
    public static Drawable setTint(Resources res, int resId, int tint) {
        Bitmap bitmap = BitmapFactory.decodeResource(res, resId);
        // make a copy of the drawable object
        Drawable bitmapDrawable = new BitmapDrawable(res, bitmap);
        // setup color filter for tinting
        ColorFilter cf = new PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN);
        bitmapDrawable.setColorFilter(cf);

        return bitmapDrawable;
    }

    /**
     * Tint the drawable with color
     *
     * @param drawable to be tint
     * @param tint color to apply to the image
     * @return the drawable after tint as a new copy, original resource will not be changed
     */
    public static Drawable setTint(Drawable drawable, int tint) {
        // clone the drawable
        Drawable clone = drawable.mutate();
        // setup color filter for tinting
        ColorFilter cf = new PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN);
        if (clone != null) clone.setColorFilter(cf);

        return clone;
    }

}
