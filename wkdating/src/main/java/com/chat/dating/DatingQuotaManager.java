package com.chat.dating;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.chat.dating.model.DatingProfile;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 前端轻量额度提示。真正额度必须由后端再次校验，避免改包绕过。
 * V1：不喜欢不限额，只做后端频率防刷；喜欢/收藏限额；撤回每天免费 3 次。
 * 交友资料只保留男女两个性别；未知/异常值按男性额度处理，避免出现第三套规则。
 */
public final class DatingQuotaManager {
    private static final String SP_NAME = "wkdating_daily_quota";
    private static final int MALE_LIKE_LIMIT = 40;
    private static final int FEMALE_LIKE_LIMIT = 60;
    private static final int MALE_FAVORITE_LIMIT = 10;
    private static final int FEMALE_FAVORITE_LIMIT = 20;
    private static final int FREE_REWIND_LIMIT = 3;

    private DatingQuotaManager() {}

    public static boolean needsQuota(String action) {
        return DatingSwipeAction.LIKE.equals(action) || DatingSwipeAction.FAVORITE.equals(action);
    }

    public static int dailyLimit(DatingProfile myProfile, String action) {
        boolean female = isFemale(myProfile);
        if (DatingSwipeAction.FAVORITE.equals(action)) {
            return female ? FEMALE_FAVORITE_LIMIT : MALE_FAVORITE_LIMIT;
        }
        return female ? FEMALE_LIKE_LIMIT : MALE_LIKE_LIMIT;
    }

    public static int used(Context context, String action) {
        if (context == null || TextUtils.isEmpty(action)) return 0;
        return sp(context).getInt(key(action), 0);
    }

    public static int remaining(Context context, DatingProfile myProfile, String action) {
        if (!needsQuota(action)) return Integer.MAX_VALUE;
        return Math.max(0, dailyLimit(myProfile, action) - used(context, action));
    }

    public static boolean hasQuota(Context context, DatingProfile myProfile, String action) {
        return !needsQuota(action) || remaining(context, myProfile, action) > 0;
    }

    public static boolean consume(Context context, DatingProfile myProfile, String action) {
        if (!needsQuota(action)) return true;
        if (!hasQuota(context, myProfile, action)) return false;
        SharedPreferences preferences = sp(context);
        String key = key(action);
        preferences.edit().putInt(key, preferences.getInt(key, 0) + 1).apply();
        return true;
    }


    public static int rewindDailyLimit() {
        return FREE_REWIND_LIMIT;
    }

    public static int rewindUsed(Context context) {
        if (context == null) return 0;
        return sp(context).getInt(key("rewind"), 0);
    }

    public static int rewindRemaining(Context context) {
        return Math.max(0, FREE_REWIND_LIMIT - rewindUsed(context));
    }

    public static boolean consumeRewind(Context context) {
        if (context == null) return false;
        if (rewindRemaining(context) <= 0) return false;
        SharedPreferences preferences = sp(context);
        String key = key("rewind");
        preferences.edit().putInt(key, preferences.getInt(key, 0) + 1).apply();
        return true;
    }

    private static boolean isFemale(DatingProfile profile) {
        if (profile == null) return false;
        int gender = profile.safeGender();
        return gender == 2;
    }

    private static SharedPreferences sp(Context context) {
        return context.getApplicationContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    private static String key(String action) {
        return action + "_" + new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }
}
