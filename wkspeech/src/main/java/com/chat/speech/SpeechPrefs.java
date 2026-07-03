package com.chat.speech;

import android.content.Context;
import android.content.SharedPreferences;

import com.chat.speech.model.SpeechSegment;

public class SpeechPrefs {
    private static final String PREF = "tsdd_speech_prefs";
    private static final String KEY_MS_ENABLED = "ms_enabled";
    private static final String KEY_ZH_VOICE = "zh_voice";
    private static final String KEY_MY_VOICE = "my_voice";
    private static final String KEY_AUDIO_FORMAT = "audio_format";
    private static final String KEY_IMPORTED_VOICE_COUNT = "imported_voice_count";
    private static final String KEY_IMPORTED_SOURCE_NAME = "imported_source_name";

    public static final String DEFAULT_AUDIO_FORMAT = "audio-24khz-48kbitrate-mono-mp3";
    public static final String DEFAULT_ZH_VOICE = "zh-CN-XiaoxiaoNeural";
    public static final String DEFAULT_ZH_MALE_VOICE = "zh-CN-YunxiNeural";
    public static final String DEFAULT_ZH_MULTI_VOICE = "zh-CN-XiaochenMultilingualNeural";
    public static final String DEFAULT_MY_VOICE = "my-MM-NilarNeural";
    public static final String DEFAULT_MY_MALE_VOICE = "my-MM-ThihaNeural";

    private final SharedPreferences sp;

    public SpeechPrefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public boolean isMsEnabled() {
        return sp.getBoolean(KEY_MS_ENABLED, false);
    }

    public void setMsEnabled(boolean enabled) {
        sp.edit().putBoolean(KEY_MS_ENABLED, enabled).apply();
    }

    public String getZhVoice() {
        return sp.getString(KEY_ZH_VOICE, DEFAULT_ZH_VOICE);
    }

    public void setZhVoice(String voice) {
        sp.edit().putString(KEY_ZH_VOICE, voice).apply();
    }

    public String getMyVoice() {
        return sp.getString(KEY_MY_VOICE, DEFAULT_MY_VOICE);
    }

    public void setMyVoice(String voice) {
        sp.edit().putString(KEY_MY_VOICE, voice).apply();
    }

    public String getAudioFormat() {
        return sp.getString(KEY_AUDIO_FORMAT, DEFAULT_AUDIO_FORMAT);
    }

    public void setAudioFormat(String format) {
        if (format == null || format.trim().isEmpty()) return;
        sp.edit().putString(KEY_AUDIO_FORMAT, format.trim()).apply();
    }

    public String voiceForLang(String lang) {
        if (SpeechSegment.LANG_MY.equals(lang)) return getMyVoice();
        return getZhVoice();
    }

    public void setImportedSource(String name, int voiceCount) {
        sp.edit()
                .putString(KEY_IMPORTED_SOURCE_NAME, name == null ? "MultiTTS 导入源" : name)
                .putInt(KEY_IMPORTED_VOICE_COUNT, Math.max(0, voiceCount))
                .apply();
    }

    public String getImportedSourceName() {
        return sp.getString(KEY_IMPORTED_SOURCE_NAME, "未导入");
    }

    public int getImportedVoiceCount() {
        return sp.getInt(KEY_IMPORTED_VOICE_COUNT, 0);
    }
}
