package com.chat.speech;

import com.chat.speech.model.SpeechSegment;

import java.util.ArrayList;
import java.util.List;

public class SpeechSegmenter {
    private SpeechSegmenter() {}

    public static List<SpeechSegment> splitByLanguage(String text) {
        List<SpeechSegment> list = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return list;

        StringBuilder buffer = new StringBuilder();
        String currentLang = null;
        for (int offset = 0; offset < text.length(); ) {
            int cp = text.codePointAt(offset);
            String lang = detectLang(cp);
            String appendLang = lang;
            if (SpeechSegment.LANG_OTHER.equals(lang) && currentLang != null) {
                appendLang = currentLang;
            }
            if (currentLang == null) {
                currentLang = appendLang;
            }
            if (!currentLang.equals(appendLang) && buffer.length() > 0) {
                addSegment(list, buffer.toString(), currentLang);
                buffer.setLength(0);
                currentLang = appendLang;
            }
            buffer.appendCodePoint(cp);
            offset += Character.charCount(cp);
        }
        if (buffer.length() > 0) addSegment(list, buffer.toString(), currentLang == null ? SpeechSegment.LANG_ZH : currentLang);
        return list;
    }

    private static void addSegment(List<SpeechSegment> list, String text, String lang) {
        String t = text == null ? "" : text.trim();
        if (!t.isEmpty()) list.add(new SpeechSegment(t, lang));
    }

    private static String detectLang(int cp) {
        if ((cp >= 0x1000 && cp <= 0x109F) || (cp >= 0xAA60 && cp <= 0xAA7F) || (cp >= 0xA9E0 && cp <= 0xA9FF)) {
            return SpeechSegment.LANG_MY;
        }
        if ((cp >= 0x4E00 && cp <= 0x9FFF) || (cp >= 0x3400 && cp <= 0x4DBF) || (cp >= 0xF900 && cp <= 0xFAFF)) {
            return SpeechSegment.LANG_ZH;
        }
        return SpeechSegment.LANG_OTHER;
    }
}
