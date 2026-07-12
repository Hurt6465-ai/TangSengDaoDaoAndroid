package com.chat.dating;

import android.content.Context;
import android.text.TextUtils;

import java.util.Locale;

/**
 * 交友意向的统一业务值。
 *
 * UI 文案可以翻译，但网络请求和本地筛选只使用稳定 code，避免把中文展示文案当作业务值。
 * 后端会兼容这些 code，并继续把历史中文值标准化保存。
 */
public final class DatingIntent {
    public static final String LONG_TERM = "long_term";
    public static final String LONG_TERM_OPEN_SHORT = "long_term_open_short";
    public static final String SHORT_TERM_OPEN_LONG = "short_term_open_long";
    public static final String SHORT_TERM = "short_term";
    public static final String FRIENDS = "friends";
    public static final String OPEN = "open";

    public static final String GOAL_ALL = "all";
    public static final String GOAL_SERIOUS = "serious";
    public static final String GOAL_MARRIAGE = "marriage";

    private static final String[] PROFILE_CODES = {
            LONG_TERM,
            LONG_TERM_OPEN_SHORT,
            SHORT_TERM_OPEN_LONG,
            SHORT_TERM,
            FRIENDS,
            OPEN
    };

    private static final String[] PROFILE_LABELS = {
            "寻找长期伴侣",
            "长期伴侣，但不拒绝短期交往",
            "短期伴侣，但不拒绝长期交往",
            "享受短期交往的乐趣",
            "结交新朋友",
            "顺其自然"
    };


    private static final int[] PROFILE_LABEL_RES = {
            R.string.dating_intent_long_term,
            R.string.dating_intent_long_term_open_short,
            R.string.dating_intent_short_term_open_long,
            R.string.dating_intent_short_term,
            R.string.dating_intent_friends,
            R.string.dating_intent_open
    };

    private DatingIntent() {
    }

    public static String[] profileLabels() {
        return PROFILE_LABELS.clone();
    }

    public static String[] profileLabels(Context context) {
        if (context == null) return profileLabels();
        String[] out = new String[PROFILE_LABEL_RES.length];
        for (int i = 0; i < PROFILE_LABEL_RES.length; i++) out[i] = context.getString(PROFILE_LABEL_RES[i]);
        return out;
    }

    public static String codeForLabel(String label) {
        if (TextUtils.isEmpty(label)) return LONG_TERM;
        for (int i = 0; i < PROFILE_LABELS.length; i++) {
            if (TextUtils.equals(PROFILE_LABELS[i], label)) return PROFILE_CODES[i];
        }
        return normalizeProfileCode(label);
    }

    public static String displayLabel(Context context, String value) {
        String code = normalizeProfileCode(value);
        for (int i = 0; i < PROFILE_CODES.length; i++) {
            if (TextUtils.equals(PROFILE_CODES[i], code)) {
                return context == null ? PROFILE_LABELS[i] : context.getString(PROFILE_LABEL_RES[i]);
            }
        }
        return context == null ? PROFILE_LABELS[0] : context.getString(PROFILE_LABEL_RES[0]);
    }

    public static String codeForDisplayLabel(Context context, String label) {
        if (TextUtils.isEmpty(label)) return LONG_TERM;
        String[] labels = profileLabels(context);
        for (int i = 0; i < labels.length; i++) {
            if (TextUtils.equals(labels[i], label)) return PROFILE_CODES[i];
        }
        return codeForLabel(label);
    }

    public static String labelForCode(String value) {
        String code = normalizeProfileCode(value);
        for (int i = 0; i < PROFILE_CODES.length; i++) {
            if (TextUtils.equals(PROFILE_CODES[i], code)) return PROFILE_LABELS[i];
        }
        return PROFILE_LABELS[0];
    }

    public static String normalizeProfileCode(String value) {
        if (TextUtils.isEmpty(value)) return LONG_TERM;
        String raw = value.trim();
        String lower = raw.toLowerCase(Locale.US);
        switch (lower) {
            case LONG_TERM:
            case "love":
            case "dating":
            case "marriage":
                return LONG_TERM;
            case LONG_TERM_OPEN_SHORT:
                return LONG_TERM_OPEN_SHORT;
            case SHORT_TERM_OPEN_LONG:
                return SHORT_TERM_OPEN_LONG;
            case SHORT_TERM:
                return SHORT_TERM;
            case FRIENDS:
            case "friend":
                return FRIENDS;
            case OPEN:
            case "chat":
                return OPEN;
            default:
                break;
        }
        for (int i = 0; i < PROFILE_LABELS.length; i++) {
            if (TextUtils.equals(PROFILE_LABELS[i], raw)) return PROFILE_CODES[i];
        }
        if (raw.contains("新朋友")) return FRIENDS;
        if (raw.contains("长期") && raw.contains("短期")) {
            return raw.startsWith("短期") ? SHORT_TERM_OPEN_LONG : LONG_TERM_OPEN_SHORT;
        }
        if (raw.contains("短期")) return SHORT_TERM;
        if (raw.contains("顺其自然")) return OPEN;
        if (raw.contains("长期") || raw.contains("结婚") || raw.contains("恋爱")) return LONG_TERM;
        return LONG_TERM;
    }

    public static String normalizeGoal(String value) {
        if (TextUtils.isEmpty(value)) return GOAL_SERIOUS;
        String lower = value.trim().toLowerCase(Locale.US);
        switch (lower) {
            case GOAL_ALL:
                return GOAL_ALL;
            case GOAL_MARRIAGE:
                return GOAL_MARRIAGE;
            case GOAL_SERIOUS:
            case "love":
            case "dating":
                return GOAL_SERIOUS;
            default:
                return GOAL_SERIOUS;
        }
    }

    public static boolean matchesGoal(String goal, String targetIntent) {
        String normalizedGoal = normalizeGoal(goal);
        if (GOAL_ALL.equals(normalizedGoal)) return true;
        String targetCode = normalizeProfileCode(targetIntent);
        if (GOAL_MARRIAGE.equals(normalizedGoal)) return LONG_TERM.equals(targetCode);
        return LONG_TERM.equals(targetCode) || LONG_TERM_OPEN_SHORT.equals(targetCode);
    }
}
