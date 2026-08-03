package com.chat.learning;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Method;

/**
 * 学习模块统一 TTS 桥。
 *
 * 这里不写死任何在线 TTS 地址。背词页只调用本桥，真实发音由你们已有的
 * wkspeech / 微软发音脚本 / 字节离线 TTS 插件实现。
 *
 * 插件侧后续只要暴露以下任一静态方法即可被自动命中：
 * - speak(Context, String, String, String, String) // text, pinyin, lang, mode
 * - speak(Context, String, String)
 * - speak(Context, String)
 * - speak(String, String)
 * - speak(String)
 */
final class LearningTtsBridge {
    private static final String TAG = "LearningTtsBridge";
    static final String LANG_ZH_CN = "zh-CN";
    static final String MODE_WORD = "word";
    static final String MODE_SPELLING = "spelling";
    static final String MODE_EXAMPLE = "example";

    private LearningTtsBridge() {}

    static boolean speak(Context context, String text, String lang, String mode) {
        return speak(context, text, null, lang, mode);
    }

    static boolean speak(Context context, String text, String pinyin, String lang, String mode) {
        if (context == null || text == null || text.trim().length() == 0) return false;
        Context app = context.getApplicationContext();
        String content = text.trim();
        String pronunciation = pinyin == null ? "" : pinyin.trim();

        int speechManagerResult = trySpeechManagerExact(
                app, content, pronunciation, lang, mode
        );
        if (speechManagerResult == 1) return true;
        if (speechManagerResult == -1) {
            Toast.makeText(context, "语音组件调用失败，请重新打开应用后重试", Toast.LENGTH_SHORT).show();
            return false;
        }

        // Compatibility path for builds that genuinely do not contain wkspeech. Do not use the
        // generic SpeechManager overloads here: losing pinyin/mode can silently change the selected
        // offline voice into an automatic/system-TTS route.
        if (tryLegacyStatic(app, content, pronunciation, lang, mode)) return true;
        if (tryIntent(app, content, pronunciation, lang, mode)) return true;
        Toast.makeText(context, context.getString(R.string.word_tts_plugin_missing), Toast.LENGTH_SHORT).show();
        return false;
    }

    static void stop(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        try {
            Class<?> managerClass = Class.forName("com.chat.speech.SpeechManager");
            Method getMethod = managerClass.getMethod("get", Context.class);
            Object manager = getMethod.invoke(null, app);
            if (manager == null) return;
            Method stopMethod = managerClass.getMethod("stop");
            stopMethod.invoke(manager);
        } catch (Throwable ignored) {
        }
    }

    /**
     * @return 1 when invoked, 0 when SpeechManager is absent, -1 when it exists but invocation failed.
     */
    private static int trySpeechManagerExact(
            Context context,
            String text,
            String pinyin,
            String lang,
            String mode
    ) {
        try {
            Class<?> cls = Class.forName("com.chat.speech.SpeechManager");
            Method method = cls.getMethod(
                    "speak",
                    Context.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class
            );
            method.invoke(null, context, text, pinyin, lang, mode);
            return 1;
        } catch (ClassNotFoundException absent) {
            return 0;
        } catch (Throwable error) {
            Log.e(TAG, "Exact SpeechManager invocation failed", error);
            return -1;
        }
    }

    private static boolean tryLegacyStatic(
            Context context,
            String text,
            String pinyin,
            String lang,
            String mode
    ) {
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
                for (String methodName : methodNames) {
                    if (invoke(cls, methodName,
                            new Class[]{Context.class, String.class, String.class, String.class, String.class},
                            new Object[]{context, text, pinyin, lang, mode})) return true;
                    if (invoke(cls, methodName,
                            new Class[]{Context.class, String.class, String.class, String.class},
                            new Object[]{context, text, lang, mode})) return true;
                }
            } catch (Throwable ignored) {
            }
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

    private static boolean tryIntent(Context context, String text, String pinyin, String lang, String mode) {
        try {
            Intent intent = new Intent("com.chat.speech.action.SPEAK");
            intent.setPackage(context.getPackageName());
            intent.putExtra("text", text);
            intent.putExtra("pinyin", pinyin);
            intent.putExtra("lang", lang);
            intent.putExtra("mode", mode);
            java.util.List<ResolveInfo> receivers = context.getPackageManager().queryBroadcastReceivers(intent, 0);
            if (receivers == null || receivers.isEmpty()) return false;
            context.sendBroadcast(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
