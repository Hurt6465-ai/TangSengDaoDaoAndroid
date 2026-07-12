package com.chat.partnerlist;

import android.content.Context;
import android.text.TextUtils;

import java.util.List;
import java.util.Locale;

public final class PartnerListLanguage {
    private PartnerListLanguage() {}

    public static String relation(Context context, List<String> nativeLangs, List<String> learningLangs) {
        String nativeName = nativeLangs == null || nativeLangs.isEmpty() ? "" : display(context, nativeLangs.get(0));
        String learningName = learningLangs == null || learningLangs.isEmpty() ? "" : display(context, learningLangs.get(0));
        if (TextUtils.isEmpty(nativeName) && TextUtils.isEmpty(learningName)) return "";
        if (TextUtils.isEmpty(nativeName)) return learningName;
        if (TextUtils.isEmpty(learningName)) return nativeName;
        return nativeName + "  ⇋  " + learningName;
    }

    public static String display(Context context, String code) {
        if (context == null || TextUtils.isEmpty(code)) return code == null ? "" : code;
        String original = code.trim();
        String value = original.toLowerCase(Locale.US).replace('_', '-');
        switch (value) {
            case "zh": case "zh-cn": case "zh-tw": case "cn": return "ZH";
            case "en": case "en-us": case "en-gb": return "EN";
            case "my": case "mm": return "MY";
            case "ja": case "jp": return "JA";
            case "ko": case "kr": return "KO";
            case "th": return "TH";
            case "vi": case "vn": return "VI";
            case "id": return "ID";
            case "ms": return "MS";
            case "fil": case "tl": return "FIL";
            case "km": case "kh": return "KM";
            case "lo": case "la": return "LO";
            case "hi": return "HI";
            case "fr": return "FR";
            case "de": return "DE";
            case "es": return "ES";
            case "ru": return "RU";
            default:
                String letters = original.replaceAll("[^A-Za-z]", "");
                return TextUtils.isEmpty(letters) ? original : letters.substring(0, Math.min(3, letters.length())).toUpperCase(Locale.US);
        }
    }
}
