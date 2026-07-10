package com.chat.dating;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.chat.dating.model.DatingProfile;

public class DatingFilter {
    private static final String SP = "wkdating_filter";
    private static final String KEY_COUNTRY_MODE = "country_mode";
    private static final String KEY_GENDER = "gender";
    private static final String KEY_AGE_MIN = "age_min";
    private static final String KEY_AGE_MAX = "age_max";
    private static final String KEY_GOAL = "goal";

    public static final String COUNTRY_SMART = "smart";
    public static final String COUNTRY_SAME = "same_country";
    public static final String COUNTRY_FOREIGN = "foreign_open";

    public String countryMode = COUNTRY_SMART;
    public String gender = "all";
    public int ageMin = 18;
    public int ageMax = 35;
    public String goal = "love";

    public static DatingFilter load(Context context) {
        DatingFilter f = new DatingFilter();
        if (context == null) return f;
        SharedPreferences sp = context.getSharedPreferences(SP, Context.MODE_PRIVATE);
        f.countryMode = sp.getString(KEY_COUNTRY_MODE, COUNTRY_SMART);
        f.gender = sp.getString(KEY_GENDER, "all");
        f.ageMin = sp.getInt(KEY_AGE_MIN, 18);
        f.ageMax = sp.getInt(KEY_AGE_MAX, 35);
        f.goal = sp.getString(KEY_GOAL, "love");
        return f;
    }

    public void save(Context context) {
        if (context == null) return;
        context.getSharedPreferences(SP, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_COUNTRY_MODE, countryMode)
                .putString(KEY_GENDER, gender)
                .putInt(KEY_AGE_MIN, ageMin)
                .putInt(KEY_AGE_MAX, ageMax)
                .putString(KEY_GOAL, goal)
                .apply();
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        if (COUNTRY_SAME.equals(countryMode)) sb.append("本国恋");
        else if (COUNTRY_FOREIGN.equals(countryMode)) sb.append("可异国恋");
        else sb.append("智能推荐");
        sb.append(" · ").append(ageMin).append("-").append(ageMax);
        if ("female".equals(gender)) sb.append(" · 女生");
        else if ("male".equals(gender)) sb.append(" · 男生");
        else sb.append(" · 不限");
        return sb.toString();
    }

    public boolean accepts(DatingProfile my, DatingProfile target) {
        if (target == null) return false;
        if (target.age > 0 && (target.age < ageMin || target.age > ageMax)) return false;
        if (!"all".equals(gender)) {
            if ("female".equals(gender) && !target.isFemale()) return false;
            if ("male".equals(gender) && !target.isMale()) return false;
        }
        if (TextUtils.isEmpty(goal) || "all".equals(goal)) return acceptsCountry(my, target);
        String goalText = target.safeRelationshipGoal().toLowerCase();
        if ("love".equals(goal) && goalText.contains("friend") && !goalText.contains("love")) return false;
        return acceptsCountry(my, target);
    }

    public boolean acceptsCountry(DatingProfile my, DatingProfile target) {
        if (my == null || target == null) return true;
        String myCountry = my.safeCountryCode();
        String targetCountry = target.safeCountryCode();
        if (TextUtils.isEmpty(myCountry) || TextUtils.isEmpty(targetCountry)) {
            // 只接受本国时，国籍未知就不能冒险推荐；显式本国/异国筛选也要求双方国家明确。
            if (my.rejectsCrossBorder() || target.rejectsCrossBorder()) return false;
            return COUNTRY_SMART.equals(countryMode);
        }
        boolean sameCountry = myCountry.equalsIgnoreCase(targetCountry);
        if (my.rejectsCrossBorder() && !sameCountry) return false;
        if (target.rejectsCrossBorder() && !sameCountry) return false;
        if (COUNTRY_SAME.equals(countryMode) && !sameCountry) return false;
        if (COUNTRY_FOREIGN.equals(countryMode) && sameCountry) return false;
        return true;
    }
}
