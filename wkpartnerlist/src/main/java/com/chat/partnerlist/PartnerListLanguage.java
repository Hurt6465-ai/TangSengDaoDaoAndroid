package com.chat.partnerlist;

import android.content.Context;
import android.text.TextUtils;

import com.chat.partnerlist.R;

import java.util.List;
import java.util.Locale;

public final class PartnerListLanguage {
    private PartnerListLanguage() {}

    public static String relation(Context context, List<String> nativeLangs, List<String> learningLangs) {
        String nativeName = nativeLangs == null || nativeLangs.isEmpty() ? "" : display(context, nativeLangs.get(0));
        String learningName = learningLangs == null || learningLangs.isEmpty() ? "" : display(context, learningLangs.get(0));
        if (TextUtils.isEmpty(nativeName) && TextUtils.isEmpty(learningName)) return "";
        if (TextUtils.isEmpty(nativeName)) return context.getString(R.string.partnerlist_learning_language, learningName);
        if (TextUtils.isEmpty(learningName)) return nativeName;
        return nativeName + "  →  " + learningName;
    }

    public static String display(Context context, String code) {
        if (context == null || TextUtils.isEmpty(code)) return code == null ? "" : code;
        String value = code.trim().toLowerCase(Locale.US).replace('_', '-');
        switch (value) {
            case "zh": case "zh-cn": case "zh-tw": return context.getString(R.string.partnerlist_lang_zh);
            case "en": case "en-us": case "en-gb": return context.getString(R.string.partnerlist_lang_en);
            case "my": return context.getString(R.string.partnerlist_lang_my);
            case "ja": case "jp": return context.getString(R.string.partnerlist_lang_ja);
            case "ko": case "kr": return context.getString(R.string.partnerlist_lang_ko);
            case "th": return context.getString(R.string.partnerlist_lang_th);
            case "vi": return context.getString(R.string.partnerlist_lang_vi);
            case "id": return context.getString(R.string.partnerlist_lang_id);
            case "ms": return context.getString(R.string.partnerlist_lang_ms);
            case "fil": case "tl": return context.getString(R.string.partnerlist_lang_fil);
            case "km": return context.getString(R.string.partnerlist_lang_km);
            case "lo": return context.getString(R.string.partnerlist_lang_lo);
            case "hi": return context.getString(R.string.partnerlist_lang_hi);
            case "fr": return context.getString(R.string.partnerlist_lang_fr);
            case "de": return context.getString(R.string.partnerlist_lang_de);
            case "es": return context.getString(R.string.partnerlist_lang_es);
            case "ru": return context.getString(R.string.partnerlist_lang_ru);
            default: return code.trim();
        }
    }
}
