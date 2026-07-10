package com.chat.dating;

import android.content.Context;
import android.text.TextUtils;

import com.chat.dating.model.DatingProfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * V1 本地收藏缓存。收藏不会触发匹配。
 * 后端增加 dating_favorites 后，可在保持同一 UI 的情况下替换这里的数据源。
 */
public final class DatingFavoriteStore {
    private static final String FILE_NAME = "wkdating_favorites_v1.bin";
    private static final Object LOCK = new Object();

    private DatingFavoriteStore() {}

    public static List<DatingProfile> list(Context context) {
        synchronized (LOCK) {
            return new ArrayList<>(read(context));
        }
    }

    public static void add(Context context, DatingProfile profile) {
        if (context == null || profile == null || TextUtils.isEmpty(profile.safeUid())) return;
        synchronized (LOCK) {
            ArrayList<DatingProfile> items = read(context);
            removeByUid(items, profile.safeUid());
            items.add(0, profile);
            write(context, items);
        }
    }

    public static void remove(Context context, String uid) {
        if (context == null || TextUtils.isEmpty(uid)) return;
        synchronized (LOCK) {
            ArrayList<DatingProfile> items = read(context);
            if (removeByUid(items, uid)) write(context, items);
        }
    }

    public static boolean contains(Context context, String uid) {
        if (context == null || TextUtils.isEmpty(uid)) return false;
        synchronized (LOCK) {
            for (DatingProfile profile : read(context)) {
                if (uid.equals(profile.safeUid())) return true;
            }
            return false;
        }
    }

    private static boolean removeByUid(ArrayList<DatingProfile> items, String uid) {
        boolean changed = false;
        for (int i = items.size() - 1; i >= 0; i--) {
            DatingProfile item = items.get(i);
            if (item != null && uid.equals(item.safeUid())) {
                items.remove(i);
                changed = true;
            }
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<DatingProfile> read(Context context) {
        ArrayList<DatingProfile> result = new ArrayList<>();
        if (context == null) return result;
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) return result;
        try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(file))) {
            Object value = input.readObject();
            if (value instanceof ArrayList) result.addAll((ArrayList<DatingProfile>) value);
        } catch (Throwable ignored) {
            // 版本升级或缓存损坏时丢弃本地收藏缓存，不影响主流程。
        }
        return result;
    }

    private static void write(Context context, ArrayList<DatingProfile> items) {
        if (context == null) return;
        File file = new File(context.getFilesDir(), FILE_NAME);
        File temp = new File(context.getFilesDir(), FILE_NAME + ".tmp");
        try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(temp))) {
            output.writeObject(items);
            output.flush();
            if (file.exists()) file.delete();
            if (!temp.renameTo(file)) {
                try (ObjectOutputStream direct = new ObjectOutputStream(new FileOutputStream(file))) {
                    direct.writeObject(items);
                }
                temp.delete();
            }
        } catch (Throwable ignored) {
        }
    }
}
