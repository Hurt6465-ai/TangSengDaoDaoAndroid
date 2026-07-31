package com.chat.dating;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.chat.dating.model.DatingProfile;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * V1 客户端额度提示：男喜欢 40/天、女喜欢 60/天；收藏男 10/天、女 20/天；撤回免费 3/天。
 * 不喜欢不限次数，只应由服务端做频率防刷。
 *
 * 注意：真正防绕过必须由后端返回并校验剩余额度，客户端只能改善体验。
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
        if (myProfile == null || !myProfile.hasKnownSex()) return 0;
        if (DatingSwipeAction.FAVORITE.equals(action)) {
            return myProfile.isFemale() ? FEMALE_FAVORITE_LIMIT : MALE_FAVORITE_LIMIT;
        }
        return myProfile.isFemale() ? FEMALE_LIKE_LIMIT : MALE_LIKE_LIMIT;
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

    /**
     * 请求失败或服务端成功撤回时退回一次本地额度。
     * 服务端仍是最终权威，这里只修正客户端即时提示。
     */
    public static void refund(Context context, String action) {
        if (context == null || !needsQuota(action)) return;
        SharedPreferences preferences = sp(context);
        String key = key(action);
        int used = preferences.getInt(key, 0);
        if (used > 0) preferences.edit().putInt(key, used - 1).apply();
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
        if (context == null || rewindRemaining(context) <= 0) return false;
        SharedPreferences preferences = sp(context);
        String key = key("rewind");
        preferences.edit().putInt(key, preferences.getInt(key, 0) + 1).apply();
        return true;
    }

    private static SharedPreferences sp(Context context) {
        return context.getApplicationContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    private static String key(String action) {
        return action + "_" + new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }
}
