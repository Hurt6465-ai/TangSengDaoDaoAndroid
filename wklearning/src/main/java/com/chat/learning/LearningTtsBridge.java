package com.chat.learning;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import java.lang.reflect.Method;

/**
 * 学习模块统一 TTS 桥。
 *
 * 这里不写死任何在线 TTS 地址。背词页只调用本桥，真实发音由你们已有的
 * wkspeech / 微软发音脚本 / 字节离线 TTS 插件实现。
 *
 * 插件侧后续只要暴露以下任一静态方法即可被自动命中：
 * - speak(Context, String, String)
 * - speak(Context, String)
 * - speak(String, String)
 * - speak(String)
 */
final class LearningTtsBridge {
    static final String LANG_ZH_CN = "zh-CN";
    static final String MODE_WORD = "word";
    static final String MODE_SPELLING = "spelling";
    static final String MODE_EXAMPLE = "example";

    private LearningTtsBridge() {}

    static boolean speak(Context context, String text, String lang, String mode) {
        if (context == null || text == null || text.trim().length() == 0) return false;
        Context app = context.getApplicationContext();
        String content = text.trim();
        if (tryStatic(app, content, lang)) return true;
        if (tryIntent(app, content, lang, mode)) return true;
        Toast.makeText(context, context.getString(R.string.word_tts_plugin_missing), Toast.LENGTH_SHORT).show();
        return false;
    }

    private static boolean tryStatic(Context context, String text, String lang) {
        String[] classNames = new String[]{
                "com.chat.speech.TsddTtsManager",
                "com.chat.speech.WKSpeechManager",
                "com.chat.speech.WKSpeechBridge",
                "com.chat.speech.TangSengSpeechBridge",
                "com.chat.userscript.TangSengSpeechBridge",
                "com.chat.userscript.WKSpeechBridge"
        };
        String[] methodNames = new String[]{"speak", "speakText", "play", "playText", "tts"};
        for (String clsName : classNames) {
            try {
                Class<?> cls = Class.forName(clsName);
                for (String m : methodNames) {
                    if (invoke(cls, m, new Class[]{Context.class, String.class, String.class}, new Object[]{context, text, lang})) return true;
                    if (invoke(cls, m, new Class[]{Context.class, String.class}, new Object[]{context, text})) return true;
                    if (invoke(cls, m, new Class[]{String.class, String.class}, new Object[]{text, lang})) return true;
                    if (invoke(cls, m, new Class[]{String.class}, new Object[]{text})) return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static boolean invoke(Class<?> cls, String methodName, Class<?>[] types, Object[] args) {
        try {
            Method method = cls.getMethod(methodName, types);
            method.setAccessible(true);
            method.invoke(null, args);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean tryIntent(Context context, String text, String lang, String mode) {
        try {
            Intent intent = new Intent("com.chat.speech.action.SPEAK");
            intent.setPackage(context.getPackageName());
            intent.putExtra("text", text);
            intent.putExtra("lang", lang);
            intent.putExtra("mode", mode);
            context.sendBroadcast(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
