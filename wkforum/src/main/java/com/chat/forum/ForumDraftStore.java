package com.chat.forum;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chat.base.config.WKConfig;

/** Small account-scoped text draft store for the native forum composer. */
final class ForumDraftStore {
    private static final String PREF_NAME = "forum_topic_drafts";
    private static final long MAX_DRAFT_AGE_MS = 30L * 24L * 60L * 60L * 1000L;

    private ForumDraftStore() {
    }

    static final class Draft {
        String title = "";
        String content = "";
        String tags = "";
        String bounty = "";
        boolean voteEnabled;
        String voteTitle = "";
        String voteOptions = "";
        int voteType = 1;
        int voteNum = 1;
        int voteDurationIndex;
        int topicType;
        long categoryId;
        long savedAt;

        boolean hasContent() {
            return !TextUtils.isEmpty(safe(title).trim())
                    || !TextUtils.isEmpty(safe(content).trim())
                    || !TextUtils.isEmpty(safe(tags).trim())
                    || !TextUtils.isEmpty(safe(bounty).trim())
                    || voteEnabled
                    || !TextUtils.isEmpty(safe(voteTitle).trim())
                    || !TextUtils.isEmpty(safe(voteOptions).trim());
        }
    }

    static void save(@NonNull Context context, @NonNull Draft draft) {
        if (!draft.hasContent()) {
            clear(context);
            return;
        }
        String prefix = keyPrefix();
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putString(prefix + "title", safe(draft.title))
                .putString(prefix + "content", safe(draft.content))
                .putString(prefix + "tags", safe(draft.tags))
                .putString(prefix + "bounty", safe(draft.bounty))
                .putBoolean(prefix + "vote_enabled", draft.voteEnabled)
                .putString(prefix + "vote_title", safe(draft.voteTitle))
                .putString(prefix + "vote_options", safe(draft.voteOptions))
                .putInt(prefix + "vote_type", draft.voteType)
                .putInt(prefix + "vote_num", draft.voteNum)
                .putInt(prefix + "vote_duration", draft.voteDurationIndex)
                .putInt(prefix + "type", draft.topicType)
                .putLong(prefix + "category", draft.categoryId)
                .putLong(prefix + "saved_at", System.currentTimeMillis())
                .apply();
    }

    @Nullable
    static Draft load(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String prefix = keyPrefix();
        long savedAt = prefs.getLong(prefix + "saved_at", 0L);
        if (savedAt <= 0L || System.currentTimeMillis() - savedAt > MAX_DRAFT_AGE_MS) {
            clear(context);
            return null;
        }
        Draft draft = new Draft();
        draft.title = prefs.getString(prefix + "title", "");
        draft.content = prefs.getString(prefix + "content", "");
        draft.tags = prefs.getString(prefix + "tags", "");
        draft.bounty = prefs.getString(prefix + "bounty", "");
        draft.voteEnabled = prefs.getBoolean(prefix + "vote_enabled", false);
        draft.voteTitle = prefs.getString(prefix + "vote_title", "");
        draft.voteOptions = prefs.getString(prefix + "vote_options", "");
        draft.voteType = prefs.getInt(prefix + "vote_type", 1);
        draft.voteNum = prefs.getInt(prefix + "vote_num", 1);
        draft.voteDurationIndex = prefs.getInt(prefix + "vote_duration", 0);
        draft.topicType = prefs.getInt(prefix + "type", 0);
        draft.categoryId = prefs.getLong(prefix + "category", 0L);
        draft.savedAt = savedAt;
        return draft.hasContent() ? draft : null;
    }

    static void clear(@NonNull Context context) {
        String prefix = keyPrefix();
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .remove(prefix + "title")
                .remove(prefix + "content")
                .remove(prefix + "tags")
                .remove(prefix + "bounty")
                .remove(prefix + "vote_enabled")
                .remove(prefix + "vote_title")
                .remove(prefix + "vote_options")
                .remove(prefix + "vote_type")
                .remove(prefix + "vote_num")
                .remove(prefix + "vote_duration")
                .remove(prefix + "type")
                .remove(prefix + "category")
                .remove(prefix + "saved_at")
                .apply();
    }

    @NonNull
    private static String keyPrefix() {
        String uid = WKConfig.getInstance().getUid();
        if (TextUtils.isEmpty(uid)) uid = "guest";
        return uid + "_";
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null ? "" : value;
    }
}
