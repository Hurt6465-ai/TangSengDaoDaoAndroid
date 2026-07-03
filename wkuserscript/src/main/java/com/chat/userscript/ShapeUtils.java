package com.chat.userscript;

import android.graphics.drawable.GradientDrawable;

public final class ShapeUtils {
    private ShapeUtils() {
    }

    public static GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }
}
