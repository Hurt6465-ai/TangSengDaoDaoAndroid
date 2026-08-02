package com.chat.learning;

import android.content.Context;
import android.widget.Toast;

import com.chat.speech.SpeechManager;

/**
 * 学习模块统一使用项目内置的 {@link SpeechManager}。
 *
 * 五参数入口会原样传递汉字、拼音、语言和朗读模式：
 * - word：整词朗读
 * - spelling：按拼音拼读后再读完整词句
 * - example：例句/听力自然朗读
 */
final class LearningTtsBridge {
    static final String LANG_ZH_CN = "zh-CN";
    static final String MODE_WORD = "word";
    static final String MODE_SPELLING = "spelling";
    static final String MODE_EXAMPLE = "example";

    private LearningTtsBridge() {}

    static boolean speak(Context context, String text, String lang, String mode) {
        return speak(context, text, null, lang, mode);
    }

    static boolean speak(Context context, String text, String pinyin, String lang, String mode) {
        if (context == null || text == null || text.trim().isEmpty()) return false;
        try {
            SpeechManager.speak(
                    context.getApplicationContext(),
                    text.trim(),
                    pinyin == null ? null : pinyin.trim(),
                    lang,
                    mode
            );
            return true;
        } catch (RuntimeException error) {
            Toast.makeText(context, R.string.word_tts_plugin_missing, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    static void stop(Context context) {
        if (context == null) return;
        try {
            SpeechManager.get(context.getApplicationContext()).stop();
        } catch (RuntimeException ignored) {
        }
    }
}
