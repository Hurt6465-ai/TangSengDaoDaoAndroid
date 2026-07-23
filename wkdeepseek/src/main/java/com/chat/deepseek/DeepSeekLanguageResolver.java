package com.chat.deepseek;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

final class DeepSeekLanguageResolver {
    private static final String PREF = "wk_deepseek_language_cache";
    private static final long CACHE_TTL_MS = 24L * 60L * 60L * 1000L;

    private DeepSeekLanguageResolver() {}

    static void resolve(Context context, DeepSeekRequest request, Runnable done) {
        resolveUser(context, request.selfUid,
                needsResolve(request.myNativeLanguage),
                TextUtils.isEmpty(request.myLearningLanguages),
                (nativeLanguage, learningLanguage) -> {
                    if (!TextUtils.isEmpty(nativeLanguage) && needsResolve(request.myNativeLanguage)) {
                        request.myNativeLanguage = nativeLanguage;
                    }
                    if (!TextUtils.isEmpty(learningLanguage) && TextUtils.isEmpty(request.myLearningLanguages)) {
                        request.myLearningLanguages = learningLanguage;
                    }
                    resolveUser(context, request.channelId,
                            needsResolve(request.peerNativeLanguage),
                            TextUtils.isEmpty(request.peerLearningLanguages),
                            (peerNative, peerLearning) -> {
                                if (!TextUtils.isEmpty(peerNative) && needsResolve(request.peerNativeLanguage)) {
                                    request.peerNativeLanguage = peerNative;
                                }
                                if (!TextUtils.isEmpty(peerLearning) && TextUtils.isEmpty(request.peerLearningLanguages)) {
                                    request.peerLearningLanguages = peerLearning;
                                }
                                if (done != null) done.run();
                            });
                });
    }

    private static boolean needsResolve(String value) {
        if (TextUtils.isEmpty(value)) return true;
        String clean = value.trim();
        return "自动".equals(clean) || clean.startsWith("自动判断");
    }

    private interface ResultCallback {
        void onResult(String nativeLanguage, String learningLanguage);
    }

    private static void resolveUser(Context context, String uid, boolean needNative, boolean needLearning,
                                    ResultCallback callback) {
        if ((!needNative && !needLearning) || TextUtils.isEmpty(uid)) {
            callback.onResult("", "");
            return;
        }

        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String prefix = uid.trim() + "_";
        long savedAt = sp.getLong(prefix + "time", 0L);
        String cachedNative = sp.getString(prefix + "native", "");
        String cachedLearning = sp.getString(prefix + "learning", "");
        boolean fresh = savedAt > 0 && System.currentTimeMillis() - savedAt < CACHE_TTL_MS;
        if (fresh && (!needNative || !TextUtils.isEmpty(cachedNative))
                && (!needLearning || !TextUtils.isEmpty(cachedLearning))) {
            callback.onResult(cachedNative, cachedLearning);
            return;
        }

        DeepSeekProfileModel.getInstance().getProfile(uid, entity -> {
            if (entity == null) {
                callback.onResult(cachedNative, cachedLearning);
                return;
            }
            String nativeLanguage = entity.nativeLanguageText();
            String learningLanguage = entity.learningLanguageText();
            sp.edit()
                    .putString(prefix + "native", nativeLanguage)
                    .putString(prefix + "learning", learningLanguage)
                    .putLong(prefix + "time", System.currentTimeMillis())
                    .apply();
            callback.onResult(nativeLanguage, learningLanguage);
        });
    }
}
