package com.chat.dating;

import android.content.Context;

import com.chat.dating.model.DatingProfile;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @deprecated 收藏已经以服务端 dating_favorites 为唯一数据源。
 * 该兼容壳只负责清理旧版序列化缓存，防止历史文件继续占空间或被旧调用误读。
 */
@Deprecated
public final class DatingFavoriteStore {
    private static final String LEGACY_FILE_NAME = "wkdating_favorites_v1.bin";

    private DatingFavoriteStore() {}

    public static List<DatingProfile> list(Context context) {
        clearLegacyCache(context);
        return Collections.emptyList();
    }

    public static void add(Context context, DatingProfile profile) {
        clearLegacyCache(context);
    }

    public static void remove(Context context, String uid) {
        clearLegacyCache(context);
    }

    public static boolean contains(Context context, String uid) {
        clearLegacyCache(context);
        return false;
    }

    public static void clearLegacyCache(Context context) {
        if (context == null) return;
        File file = new File(context.getFilesDir(), LEGACY_FILE_NAME);
        File temp = new File(context.getFilesDir(), LEGACY_FILE_NAME + ".tmp");
        if (file.exists()) file.delete();
        if (temp.exists()) temp.delete();
    }
}
