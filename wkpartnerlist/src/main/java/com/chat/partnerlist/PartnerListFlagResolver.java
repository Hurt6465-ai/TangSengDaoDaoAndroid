package com.chat.partnerlist;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import java.util.Locale;

public final class PartnerListFlagResolver {
    private PartnerListFlagResolver() {}

    public static void bind(ImageView imageView, String countryCode) {
        if (imageView == null) return;
        Context context = imageView.getContext();
        String code = normalize(countryCode);
        if (TextUtils.isEmpty(code)) {
            imageView.setVisibility(View.GONE);
            imageView.setImageDrawable(null);
            return;
        }
        String name = "ic_flag_" + code.toLowerCase(Locale.US);
        int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        if (id == 0) id = context.getResources().getIdentifier("ic_flag_other", "drawable", context.getPackageName());
        if (id == 0) {
            imageView.setVisibility(View.GONE);
            return;
        }
        imageView.setImageResource(id);
        imageView.setVisibility(View.VISIBLE);
    }

    private static String normalize(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String code = value.trim().toUpperCase(Locale.US);
        if (code.length() == 2) return code;
        if (code.contains("MYANMAR") || code.contains("BURMA") || code.contains("缅")) return "MM";
        if (code.contains("CHINA") || code.contains("中国")) return "CN";
        if (code.contains("KOREA") || code.contains("韩国")) return "KR";
        if (code.contains("JAPAN") || code.contains("日本")) return "JP";
        if (code.contains("THAILAND") || code.contains("泰国")) return "TH";
        if (code.contains("VIETNAM") || code.contains("越南")) return "VN";
        if (code.contains("UNITED STATES") || code.contains("美国")) return "US";
        return "";
    }
}
