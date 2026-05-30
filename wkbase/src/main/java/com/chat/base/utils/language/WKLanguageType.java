package com.chat.base.utils.language;

import com.chat.base.WKBaseApplication;

/**
 * 多语言类型
 */
public class WKLanguageType {
    public static final int LANGUAGE_FOLLOW_SYSTEM = 0; // 跟随系统
    public static final int LANGUAGE_EN = 1; // 英文
    public static final int LANGUAGE_CHINESE_SIMPLIFIED = 2; // 简体
    public static final int LANGUAGE_CHINESE_TRADITIONAL = 3; // 繁体
    public static final int LANGUAGE_MYANMAR = 4; // 缅甸语

    public static boolean isCN() {
        return WKBaseApplication.getInstance().getContext().getResources().getConfiguration().locale.getCountry().equals("CN");
    }
}
