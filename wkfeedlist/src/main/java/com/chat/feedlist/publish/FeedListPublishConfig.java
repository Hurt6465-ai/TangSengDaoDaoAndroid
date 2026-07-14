package com.chat.feedlist.publish;

/** Client-side limits for the independent list feed publisher. */
public final class FeedListPublishConfig {
    public static final int IMAGE_MAX_SELECT_COUNT = 6;
    public static final int IMAGE_TARGET_KB = 200;
    public static final int IMAGE_MAX_LONG_EDGE = 1600;
    public static final int IMAGE_MAX_SOURCE_MB = 30;

    private FeedListPublishConfig() {}
}
