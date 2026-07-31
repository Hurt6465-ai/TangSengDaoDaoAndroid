package com.chat.dating;

import android.content.Context;
import android.text.TextUtils;

import com.chat.dating.model.DatingProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 将后端稳定 code 统一转换为当前语言文案，页面禁止直接显示 open_foreign 等代码。 */
public final class DatingValueFormatter {
    private static final String[] ORIENTATION_CODES = {
            "orientation_straight", "orientation_gay", "orientation_bisexual",
            "orientation_pansexual", "orientation_private"
    };
    private static final String[][] ORIENTATION_ALIASES = {
            {"heterosexual", "straight", "异性恋"},
            {"homosexual", "gay", "lesbian", "同性恋"},
            {"bisexual", "双性恋"},
            {"pansexual", "泛性恋"},
            {"private", "secret", "保密"}
    };

    private static final String[] DRINKING_CODES = {
            "drinking_never", "drinking_occasionally", "drinking_social",
            "drinking_often", "drinking_private"
    };
    private static final String[][] DRINKING_ALIASES = {
            {"never", "none", "从不"},
            {"occasionally", "sometimes", "偶尔"},
            {"social", "socially", "社交时"},
            {"often", "frequent", "经常"},
            {"private", "secret", "保密"}
    };

    private static final String[] SMOKING_CODES = {
            "smoking_never", "smoking_occasionally", "smoking_often",
            "smoking_quitting", "smoking_private"
    };
    private static final String[][] SMOKING_ALIASES = {
            {"never", "none", "从不"},
            {"occasionally", "sometimes", "偶尔"},
            {"often", "frequent", "经常"},
            {"quitting", "quit", "正在戒烟"},
            {"private", "secret", "保密"}
    };


    private static final String[] DEALBREAKER_CODES = {
            "dealbreaker_dishonesty", "dealbreaker_silent_treatment",
            "dealbreaker_controlling", "dealbreaker_boundaries",
            "dealbreaker_disappearing", "dealbreaker_lying",
            "dealbreaker_heavy_drinking", "dealbreaker_gambling",
            "dealbreaker_poor_hygiene", "dealbreaker_only_flirting",
            "dealbreaker_pressuring_meet", "dealbreaker_asking_money"
    };
    private static final String[][] DEALBREAKER_ALIASES = {
            {"dishonesty", "不真诚", "မရိုးသားမှု"},
            {"silent_treatment", "silent treatment", "冷暴力", "စကားမပြောဘဲ အပြစ်ပေးခြင်း"},
            {"controlling", "controlling behavior", "控制欲强", "ထိန်းချုပ်လိုမှု"},
            {"boundaries", "disrespecting boundaries", "不尊重边界", "နယ်နိမိတ်မလေးစားမှု"},
            {"disappearing", "frequently disappearing", "经常失联", "မကြာခဏပျောက်သွားမှု"},
            {"lying", "撒谎", "လိမ်ညာမှု"},
            {"heavy_drinking", "heavy drinking", "酗酒", "အရက်အလွန်အကျွံ"},
            {"gambling", "赌博", "လောင်းကစား"},
            {"poor_hygiene", "poor hygiene", "不爱卫生", "သန့်ရှင်းမှုမရှိ"},
            {"only_flirting", "only flirting", "只聊暧昧", "အပျော်အပါးသာပြောခြင်း"},
            {"pressuring_meet", "pressuring to meet", "催见面", "တွေ့ရန်ဖိအားပေးခြင်း"},
            {"asking_money", "asking for money", "索要钱财", "ငွေတောင်းခြင်း"}
    };

    private DatingValueFormatter() {
    }

    public static String crossBorder(Context context, String raw) {
        if (context == null || TextUtils.isEmpty(raw)) return raw == null ? "" : raw;
        String value = normalize(raw);
        if (containsAny(value, "same_country", "same-country", "local_only", "nearby_only", "no_foreign", "refuse_foreign")) {
            return context.getString(R.string.dating_cross_same_country);
        }
        if (containsAny(value, "prefer_foreign", "foreign_preferred", "prefer-cross", "foreign_only")) {
            return context.getString(R.string.dating_cross_prefer_foreign);
        }
        if (containsAny(value, "open_foreign", "open", "cross_border", "accept_foreign")) {
            return context.getString(R.string.dating_cross_open);
        }
        return DatingSharedProfileFormatter.display(context, raw);
    }

    public static String orientation(Context context, String raw) {
        return optionLabel(context, raw, R.array.dating_sexual_orientation_options,
                ORIENTATION_CODES, ORIENTATION_ALIASES);
    }

    public static String orientationCode(Context context, String raw) {
        return optionCode(context, raw, R.array.dating_sexual_orientation_options,
                ORIENTATION_CODES, ORIENTATION_ALIASES);
    }

    public static String drinking(Context context, String raw) {
        return optionLabel(context, raw, R.array.dating_drinking_options,
                DRINKING_CODES, DRINKING_ALIASES);
    }

    public static String drinkingCode(Context context, String raw) {
        return optionCode(context, raw, R.array.dating_drinking_options,
                DRINKING_CODES, DRINKING_ALIASES);
    }

    public static String smoking(Context context, String raw) {
        return optionLabel(context, raw, R.array.dating_smoking_options,
                SMOKING_CODES, SMOKING_ALIASES);
    }

    public static String smokingCode(Context context, String raw) {
        return optionCode(context, raw, R.array.dating_smoking_options,
                SMOKING_CODES, SMOKING_ALIASES);
    }

    public static String dealbreaker(Context context, String raw) {
        return optionLabel(context, raw, R.array.dating_dealbreaker_options,
                DEALBREAKER_CODES, DEALBREAKER_ALIASES);
    }

    public static String dealbreakerCode(Context context, String raw) {
        return optionCode(context, raw, R.array.dating_dealbreaker_options,
                DEALBREAKER_CODES, DEALBREAKER_ALIASES);
    }

    public static List<String> dealbreakerCodes(Context context, List<String> raw) {
        ArrayList<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String item : raw) {
            String code = dealbreakerCode(context, item);
            if (!TextUtils.isEmpty(code) && !out.contains(code)) out.add(code);
        }
        return out;
    }

    public static List<String> dealbreakerLabels(Context context, List<String> raw) {
        ArrayList<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String item : raw) {
            String label = dealbreaker(context, item);
            if (!TextUtils.isEmpty(label) && !out.contains(label)) out.add(label);
        }
        return out;
    }

    public static String display(Context context, String raw) {
        if (TextUtils.isEmpty(raw)) return "";
        String shared = DatingSharedProfileFormatter.display(context, raw);
        if (!TextUtils.equals(shared, raw)) return shared;
        String cross = crossBorder(context, raw);
        if (!TextUtils.equals(cross, raw)) return cross;
        String orientation = orientation(context, raw);
        if (!TextUtils.equals(orientation, raw)) return orientation;
        String drinking = drinking(context, raw);
        if (!TextUtils.equals(drinking, raw)) return drinking;
        String smoking = smoking(context, raw);
        if (!TextUtils.equals(smoking, raw)) return smoking;
        String dealbreaker = dealbreaker(context, raw);
        if (!TextUtils.equals(dealbreaker, raw)) return dealbreaker;
        return raw.trim();
    }

    public static List<String> displayList(Context context, List<String> raw) {
        ArrayList<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String item : raw) {
            String value = display(context, item);
            if (!TextUtils.isEmpty(value) && !out.contains(value)) out.add(value);
        }
        return out;
    }

    public static String basicLine(Context context, DatingProfile profile) {
        if (context == null || profile == null) return "";
        ArrayList<String> parts = new ArrayList<>();
        String relation = DatingSharedProfileFormatter.display(context, profile.relationship_status);
        String orientation = orientation(context, profile.sexual_orientation);
        String drinking = drinking(context, profile.drinking);
        String smoking = smoking(context, profile.smoking);
        String job = DatingSharedProfileFormatter.display(context,
                TextUtils.isEmpty(profile.job_status) ? profile.job : profile.job_status);
        String education = DatingSharedProfileFormatter.display(context, profile.education);
        add(parts, relation);
        add(parts, orientation);
        add(parts, job);
        add(parts, education);
        if (profile.height_cm > 0) add(parts, profile.height_cm + "cm");
        if (profile.weight_kg > 0) add(parts, profile.weight_kg + "kg");
        if (!TextUtils.isEmpty(drinking)) add(parts, context.getString(R.string.dating_drinking_value, drinking));
        if (!TextUtils.isEmpty(smoking)) add(parts, context.getString(R.string.dating_smoking_value, smoking));
        return TextUtils.join(context.getString(R.string.dating_meta_separator), parts);
    }

    private static String optionLabel(Context context, String raw, int arrayRes,
                                      String[] codes, String[][] aliases) {
        if (context == null || TextUtils.isEmpty(raw)) return raw == null ? "" : raw;
        String code = optionCode(context, raw, arrayRes, codes, aliases);
        int index = indexOf(codes, code);
        String[] labels = context.getResources().getStringArray(arrayRes);
        return index >= 0 && index < labels.length ? labels[index] : raw.trim();
    }

    private static String optionCode(Context context, String raw, int arrayRes,
                                     String[] codes, String[][] aliases) {
        if (TextUtils.isEmpty(raw)) return codes.length == 0 ? "" : codes[0];
        String clean = raw.trim();
        String normalized = normalize(clean);
        for (int i = 0; i < codes.length; i++) {
            if (TextUtils.equals(normalize(codes[i]), normalized)) return codes[i];
            if (i < aliases.length && containsAny(normalized, aliases[i])) return codes[i];
        }
        if (context != null) {
            String[] labels = context.getResources().getStringArray(arrayRes);
            for (int i = 0; i < labels.length && i < codes.length; i++) {
                if (labels[i].equalsIgnoreCase(clean)) return codes[i];
            }
        }
        return clean;
    }

    private static int indexOf(String[] values, String value) {
        if (values == null || TextUtils.isEmpty(value)) return -1;
        for (int i = 0; i < values.length; i++) {
            if (TextUtils.equals(values[i], value)) return i;
        }
        return -1;
    }

    private static boolean containsAny(String value, String... aliases) {
        if (TextUtils.isEmpty(value) || aliases == null) return false;
        for (String alias : aliases) {
            if (!TextUtils.isEmpty(alias) && value.contains(normalize(alias))) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US).replace('-', '_');
    }

    private static void add(List<String> out, String value) {
        if (!TextUtils.isEmpty(value) && !out.contains(value)) out.add(value);
    }
}
