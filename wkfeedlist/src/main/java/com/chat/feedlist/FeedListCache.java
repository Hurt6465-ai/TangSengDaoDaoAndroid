package com.chat.feedlist;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.chat.base.config.WKConfig;
import com.chat.feedlist.model.FeedListItem;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Account-isolated JSON cache. Image bytes remain managed by Glide's disk cache. */
public final class FeedListCache {
    public interface Callback { void onLoaded(CachedPage page); }
    public static final class CachedPage {
        public String mode;
        public ArrayList<FeedListItem> items = new ArrayList<>();
        public String cursor = "";
        public int has_more;
        public long server_time;
        public long saved_at;
    }

    private static final String PREF = "feed_list_cache_v1";
    private static final String PREFIX = "timeline:";
    private static final int MAX_ITEMS = 80;
    private static final long MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "feed-list-cache");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Gson GSON = new Gson();

    private FeedListCache() {}

    public static void load(Context context, String mode, Callback callback) {
        if (context == null || TextUtils.isEmpty(WKConfig.getInstance().getUid())) {
            if (callback != null) callback.onLoaded(null);
            return;
        }
        Context app = context.getApplicationContext();
        String requestedUid = WKConfig.getInstance().getUid();
        String key = key(requestedUid, mode);
        IO.execute(() -> {
            CachedPage page = null;
            try {
                String raw = app.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(key, "");
                if (!TextUtils.isEmpty(raw)) {
                    page = GSON.fromJson(raw, CachedPage.class);
                    long now = System.currentTimeMillis();
                    if (page == null || page.saved_at <= 0 || page.saved_at > now + 60_000L || now - page.saved_at > MAX_AGE_MS) page = null;
                    if (page != null && !TextUtils.equals(mode, page.mode)) page = null;
                    if (page != null) {
                        if (page.items == null) page.items = new ArrayList<>();
                        for (int i = page.items.size() - 1; i >= 0; i--) {
                            if (page.items.get(i) == null) page.items.remove(i);
                        }
                        if (page.items.size() > MAX_ITEMS) {
                            page.items = new ArrayList<>(page.items.subList(0, MAX_ITEMS));
                            // A cursor from a larger/corrupted cache no longer matches the
                            // final locally retained row. Force the next network load to refresh.
                            page.cursor = "";
                            page.has_more = 0;
                        }
                    }
                }
            } catch (Throwable ignored) {}
            CachedPage result = page;
            MAIN.post(() -> {
                if (callback == null) return;
                // Logout/login can happen while disk I/O is running. Never deliver the
                // previous account's cached timeline into the newly logged-in account.
                if (!TextUtils.equals(requestedUid, WKConfig.getInstance().getUid())) callback.onLoaded(null);
                else callback.onLoaded(result);
            });
        });
    }

    public static void save(Context context, String mode, List<FeedListItem> items, String cursor, boolean hasMore, long serverTime) {
        if (context == null) return;
        String uid = WKConfig.getInstance().getUid();
        if (TextUtils.isEmpty(uid)) return;
        CachedPage page = new CachedPage();
        page.mode = mode;
        int sourceSize = items == null ? 0 : items.size();
        int cachedSize = Math.min(MAX_ITEMS, sourceSize);
        if (items != null && cachedSize > 0) page.items.addAll(items.subList(0, cachedSize));
        // An opaque cursor only matches the exact final cached item. If the list was truncated,
        // disable offline pagination rather than skipping unseen rows after app restart.
        boolean exactPage = sourceSize <= MAX_ITEMS;
        page.cursor = exactPage && cursor != null ? cursor : "";
        page.has_more = exactPage && hasMore ? 1 : 0;
        page.server_time = serverTime;
        page.saved_at = System.currentTimeMillis();
        Context app = context.getApplicationContext();
        // Snapshot the mutable timeline on the caller thread. The UI may update like/comment
        // counters while the single cache executor is waiting; serializing those live objects
        // later could produce an inconsistent JSON page or a silently dropped save.
        final String snapshot;
        try { snapshot = GSON.toJson(page); }
        catch (Throwable ignored) { return; }
        IO.execute(() -> {
            try { app.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(key(uid, mode), snapshot).apply(); }
            catch (Throwable ignored) {}
        });
    }

    public static void removeFeed(Context context, String feedId) { mutate(context, item -> !TextUtils.equals(feedId, item.feed_id)); }
    public static void removeUser(Context context, String uid) { mutate(context, item -> !TextUtils.equals(uid, item.authorUid())); }

    public static void updateFollow(Context context, String targetUid, boolean followed, boolean removeFromFollowing) {
        if (context == null || TextUtils.isEmpty(targetUid)) return;
        String uid = WKConfig.getInstance().getUid();
        if (TextUtils.isEmpty(uid)) return;
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            SharedPreferences prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            String prefix = PREFIX + uid + ":";
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                if (!entry.getKey().startsWith(prefix) || !(entry.getValue() instanceof String)) continue;
                try {
                    CachedPage page = GSON.fromJson((String) entry.getValue(), CachedPage.class);
                    if (page == null || page.items == null) continue;
                    boolean changed = false;
                    boolean followingMode = "following".equals(page.mode) || entry.getKey().endsWith(":following");
                    for (int i = page.items.size() - 1; i >= 0; i--) {
                        FeedListItem item = page.items.get(i);
                        if (item == null || !TextUtils.equals(targetUid, item.authorUid())) continue;
                        if (removeFromFollowing && followingMode && !followed) {
                            page.items.remove(i);
                            page.cursor = "";
                            page.has_more = 0;
                        } else if (item.user != null) {
                            item.user.follow = followed ? 1 : 0;
                        }
                        changed = true;
                    }
                    if (changed) {
                        page.saved_at = System.currentTimeMillis();
                        editor.putString(entry.getKey(), GSON.toJson(page));
                    }
                } catch (Throwable ignored) {}
            }
            editor.apply();
        });
    }

    private interface Keep { boolean test(FeedListItem item); }
    private static void mutate(Context context, Keep keep) {
        if (context == null) return;
        String uid = WKConfig.getInstance().getUid();
        if (TextUtils.isEmpty(uid)) return;
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            SharedPreferences prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                if (!entry.getKey().startsWith(PREFIX + uid + ":") || !(entry.getValue() instanceof String)) continue;
                try {
                    CachedPage page = GSON.fromJson((String) entry.getValue(), CachedPage.class);
                    if (page == null || page.items == null) continue;
                    boolean changed = false;
                    for (int i = page.items.size() - 1; i >= 0; i--) {
                        FeedListItem item = page.items.get(i);
                        if (item == null || !keep.test(item)) {
                            page.items.remove(i);
                            changed = true;
                        }
                    }
                    if (changed) {
                        // The opaque cursor belongs to the previous final row. After local removal,
                        // force a clean refresh instead of risking skipped rows after app restart.
                        page.cursor = "";
                        page.has_more = 0;
                    }
                    long now = System.currentTimeMillis();
                    if (page.server_time > 0 && page.saved_at > 0 && now >= page.saved_at) {
                        page.server_time += now - page.saved_at;
                    }
                    page.saved_at = now;
                    editor.putString(entry.getKey(), GSON.toJson(page));
                } catch (Throwable ignored) {}
            }
            editor.apply();
        });
    }

    public static void clearCurrentAccount(Context context) {
        if (context == null) return;
        String uid = WKConfig.getInstance().getUid();
        if (TextUtils.isEmpty(uid)) return;
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            SharedPreferences prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            String prefix = PREFIX + uid + ":";
            for (String key : prefs.getAll().keySet()) if (key.startsWith(prefix)) editor.remove(key);
            editor.apply();
        });
    }

    private static String key(String uid, String mode) { return PREFIX + uid + ":" + mode; }
}
