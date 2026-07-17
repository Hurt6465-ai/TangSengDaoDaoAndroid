package com.chat.partnerlist;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PartnerListFlagResolver {
    private static final Map<String, Integer> RESOURCE_CACHE = new ConcurrentHashMap<>();

    private PartnerListFlagResolver() {}

    public static void bind(ImageView imageView, String countryCode, String countryName) {
        if (imageView == null) return;
        // 与会话列表 / 全屏语伴 AvatarView 一致：国旗资源本身就是透明圆形，
        // 不再额外套白底、描边或 padding，避免出现双层白圈。
        imageView.setBackground(null);
        imageView.setPadding(0, 0, 0, 0);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Context context = imageView.getContext();
        String code = normalize(!TextUtils.isEmpty(countryCode) ? countryCode : countryName);
        if (TextUtils.isEmpty(code)) {
            imageView.setVisibility(View.GONE);
            imageView.setImageDrawable(null);
            return;
        }
        int id = RESOURCE_CACHE.computeIfAbsent(code, key -> {
            String name = "ic_flag_" + key.toLowerCase(Locale.US);
            int found = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
            if (found == 0) found = context.getResources().getIdentifier("ic_flag_other", "drawable", context.getPackageName());
            return found;
        });
        if (id == 0) {
            imageView.setVisibility(View.GONE);
            imageView.setImageDrawable(null);
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
        if (code.contains("UNITED STATES") || code.contains("USA") || code.contains("美国")) return "US";
        if (code.contains("FRANCE") || code.contains("法国")) return "FR";
        if (code.contains("GERMANY") || code.contains("德国")) return "DE";
        if (code.contains("UNITED KINGDOM") || code.contains("BRITAIN") || code.contains("英国")) return "GB";
        return "";
    }
}
