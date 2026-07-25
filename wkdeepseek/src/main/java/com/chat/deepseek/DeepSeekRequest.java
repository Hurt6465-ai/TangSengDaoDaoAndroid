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
    public String relationshipStage = "auto";
    public String preferredStyle = "natural";
    public int flirtLevel = 0;
    public boolean contextEnabled = true;
    public int contextLimit = 0; // 0 = do not truncate locally; only shrink after DeepSeek explicitly reports overflow
    public String targetMessageId = "";
    public String targetMessageText = "";
    /**
     * Snapshot of the messages already loaded by ChatActivity. DeepSeek must not call
     * getOrSyncHistoryMessages(), because that API emits refresh callbacks and may mutate
     * the visible chat adapter while the assistant window is open.
     */
    public String contextSnapshot = "";
    public int contextSnapshotCount = 0;
    /** Internal description of whether this request carries full, incremental or checkpoint context. */
    public String contextSyncMode = "full";
    public DeepSeekContactProfile contactProfile = new DeepSeekContactProfile();
    public int action = ACTION_REPLY;

    public String safeMyNative() {
        return TextUtils.isEmpty(myNativeLanguage) ? "自动判断（优先使用应用界面语言）" : myNativeLanguage;
    }

    public String safePeerNative() {
        return TextUtils.isEmpty(peerNativeLanguage) ? "自动判断（优先依据对方资料和最近消息）" : peerNativeLanguage;
    }
}
