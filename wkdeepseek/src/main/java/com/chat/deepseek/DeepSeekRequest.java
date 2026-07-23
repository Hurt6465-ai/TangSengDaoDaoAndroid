package com.chat.deepseek;

import android.text.TextUtils;

public final class DeepSeekRequest {
    public static final int ACTION_REPLY = 1;
    public static final int ACTION_TRANSLATE = 2;
    public static final int ACTION_POLISH = 3;

    public String channelId = "";
    public byte channelType;
    public String selfUid = "";
    public String myNativeLanguage = "自动";
    public String peerNativeLanguage = "自动";
    public String myLearningLanguages = "";
    public String peerLearningLanguages = "";
    public String draft = "";
    public String background = "";
    public String purpose = "自然继续聊天";
    public int action = ACTION_REPLY;

    public String safeMyNative() {
        return TextUtils.isEmpty(myNativeLanguage) ? "自动判断（优先使用应用界面语言）" : myNativeLanguage;
    }

    public String safePeerNative() {
        return TextUtils.isEmpty(peerNativeLanguage) ? "自动判断（优先依据对方资料和最近消息）" : peerNativeLanguage;
    }
}
