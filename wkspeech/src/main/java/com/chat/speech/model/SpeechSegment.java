package com.chat.speech.model;

public class SpeechSegment {
    public static final String LANG_ZH = "zh-CN";
    public static final String LANG_MY = "my-MM";
    public static final String LANG_OTHER = "other";

    public final String text;
    public final String lang;

    public SpeechSegment(String text, String lang) {
        this.text = text;
        this.lang = lang;
    }
}
