package com.chat.forum;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.chat.base.config.WKConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Local board navigation state used by the Tieba-style board drawer. */
final class ForumBoardStore {
    private static final String PREF = "forum_board_navigation";
    private static final String KEY_FOLLOWED = "followed_ids";
    private static final String KEY_RECENT = "recent_ids";
    private static final int MAX_RECENT = 8;

    private ForumBoardStore() {
    }

    static boolean isFollowed(@NonNull Context context, long categoryId) {
        if (categoryId <= 0) return false;
        return readIds(context, KEY_FOLLOWED).contains(categoryId);
    }

    static boolean toggleFollowed(@NonNull Context context, long categoryId) {
        if (categoryId <= 0) return false;
        LinkedHashSet<Long> ids = new LinkedHashSet<>(readIds(context, KEY_FOLLOWED));
        boolean followed;
        if (ids.contains(categoryId)) {
            ids.remove(categoryId);
            followed = false;
        } else {
            ids.add(categoryId);
            followed = true;
        }
        writeIds(context, KEY_FOLLOWED, new ArrayList<>(ids));
        return followed;
    }

    @NonNull
    static List<Long> followedIds(@NonNull Context context) {
        return readIds(context, KEY_FOLLOWED);
    }

    static void addRecent(@NonNull Context context, long categoryId) {
        if (categoryId <= 0) return;
        List<Long> current = readIds(context, KEY_RECENT);
        LinkedHashSet<Long> ordered = new LinkedHashSet<>();
        ordered.add(categoryId);
        ordered.addAll(current);
        List<Long> result = new ArrayList<>();
        for (Long id : ordered) {
            if (id == null || id <= 0) continue;
            result.add(id);
            if (result.size() >= MAX_RECENT) break;
        }
        writeIds(context, KEY_RECENT, result);
    }

    @NonNull
    static List<Long> recentIds(@NonNull Context context) {
        return readIds(context, KEY_RECENT);
    }

    @NonNull
    private static List<Long> readIds(@NonNull Context context, @NonNull String key) {
        SharedPreferences preferences = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String value = preferences.getString(accountKey(key), "");
        List<Long> result = new ArrayList<>();
        if (TextUtils.isEmpty(value)) return result;
        Set<Long> seen = new LinkedHashSet<>();
        for (String part : value.split(",")) {
            if (TextUtils.isEmpty(part)) continue;
            try {
                long id = Long.parseLong(part.trim());
                if (id > 0 && seen.add(id)) result.add(id);
            } catch (NumberFormatException ignored) {
                // Ignore obsolete or malformed entries instead of breaking the drawer.
            }
        }
        return result;
    }

    @NonNull
    private static String accountKey(@NonNull String key) {
        String uid = WKConfig.getInstance().getUid();
        if (TextUtils.isEmpty(uid)) uid = "guest";
        return uid + "_" + key;
    }

    private static void writeIds(@NonNull Context context, @NonNull String key,
                                 @NonNull List<Long> ids) {
        StringBuilder value = new StringBuilder();
        Set<Long> seen = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0 || !seen.add(id)) continue;
            if (value.length() > 0) value.append(',');
            value.append(id);
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(accountKey(key), value.toString())
                .apply();
    }
}
