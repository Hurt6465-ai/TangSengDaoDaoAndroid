package com.chat.learning;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Learning content catalog with bundled fallback and optional Cloudflare static refresh. */
final class LearningCatalogRepository {
    private static final int MAX_CATALOG_BYTES = 2 * 1024 * 1024;

    private LearningCatalogRepository() {}

    static Catalog load(Context context, String type) {
        String json = "";
        File cache = new File(context.getFilesDir(), "learning/catalogs/" + safe(type) + ".json");
        try { json = LearningRemoteContent.readFile(cache, MAX_CATALOG_BYTES); } catch (Throwable ignored) {}
        if (json.length() == 0) {
            try { json = LearningRemoteContent.readAsset(context, "learning/" + type + "/catalog.json"); }
            catch (Throwable ignored) {}
        }
        Catalog catalog;
        try { catalog = parse(context, type, json); }
        catch (Throwable ignored) { catalog = fallback(context, type); }
        refreshInBackground(context, type, cache);
        return catalog;
    }

    private static void refreshInBackground(Context context, String type, File cache) {
        if (!"words".equals(type)) return;
        String path = LearningRemoteContent.config(context).wordsCatalog;
        String resolved = LearningRemoteContent.resolveUrl(context, path);
        if (resolved.length() == 0) return;
        Context app = context.getApplicationContext();
        LearningRemoteContent.execute(() -> {
            try {
                byte[] bytes = LearningRemoteContent.download(app, resolved, MAX_CATALOG_BYTES);
                parse(app, type, new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                LearningRemoteContent.atomicWrite(cache, bytes);
            } catch (Throwable ignored) {}
        });
    }

    private static Catalog parse(Context context, String type, String json) throws Exception {
        JSONObject root = new JSONObject(json);
        Catalog catalog = new Catalog();
        catalog.type = root.optString("type", type);
        catalog.title = localized(context, root, "title", defaultTitle(context, type));
        catalog.subtitle = localized(context, root, "subtitle", "");
        catalog.items = parseItems(context, root.optJSONArray("items"));
        return catalog;
    }

    static Node find(Catalog catalog, String id) {
        if (catalog == null || id == null || id.length() == 0) return null;
        return findIn(catalog.items, id);
    }

    static List<Node> childrenOf(Catalog catalog, String parentId) {
        if (catalog == null) return new ArrayList<>();
        if (parentId == null || parentId.length() == 0) return catalog.items;
        Node parent = find(catalog, parentId);
        return parent == null || parent.children == null ? new ArrayList<>() : parent.children;
    }

    static String titleFor(Catalog catalog, String parentId) {
        if (catalog == null) return "学习目录";
        if (parentId == null || parentId.length() == 0) return catalog.title;
        Node node = find(catalog, parentId);
        return node != null ? node.title : catalog.title;
    }

    static String subtitleFor(Catalog catalog, String parentId) {
        if (catalog == null) return "";
        if (parentId == null || parentId.length() == 0) return catalog.subtitle;
        Node node = find(catalog, parentId);
        return node != null ? node.subtitle : catalog.subtitle;
    }

    private static Node findIn(List<Node> items, String id) {
        if (items == null) return null;
        for (Node item : items) {
            if (id.equals(item.id)) return item;
            Node child = findIn(item.children, id);
            if (child != null) return child;
        }
        return null;
    }

    private static List<Node> parseItems(Context context, JSONArray array) {
        ArrayList<Node> result = new ArrayList<>();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) continue;
            Node node = new Node();
            node.id = object.optString("id", "item_" + i);
            node.title = localized(context, object, "title", "");
            node.subtitle = localized(context, object, "subtitle", "");
            node.badge = localized(context, object, "badge", "");
            node.preview = object.optString("preview", "");
            node.target = object.optString("target", "");
            node.level = object.optString("level", node.id);
            node.asset = object.optString("asset", "");
            node.prompt = object.optString("prompt", "");
            node.coverUrl = object.optString("cover_url", "");
            node.coverVersion = object.optInt("cover_version", 1);
            node.dataUrl = object.optString("data_url", "");
            node.dataVersion = object.optInt("data_version", 1);
            node.dataSha256 = object.optString("data_sha256", "");
            node.children = parseItems(context, object.optJSONArray("children"));
            result.add(node);
        }
        return result;
    }


    private static String localized(Context context, JSONObject object, String key, String fallback) {
        String suffix = localeSuffix(context);
        String value = suffix.length() == 0 ? "" : object.optString(key + suffix, "").trim();
        if (value.length() == 0) value = object.optString(key, fallback).trim();
        return value.length() == 0 ? (fallback == null ? "" : fallback) : value;
    }

    private static String localeSuffix(Context context) {
        java.util.Locale locale;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            locale = context.getResources().getConfiguration().getLocales().get(0);
        } else {
            locale = context.getResources().getConfiguration().locale;
        }
        String language = locale == null ? "" : locale.getLanguage();
        if ("my".equalsIgnoreCase(language)) return "_my";
        if ("en".equalsIgnoreCase(language)) return "_en";
        return "";
    }

    private static Catalog fallback(Context context, String type) {
        Catalog catalog = new Catalog();
        catalog.type = type;
        catalog.title = defaultTitle(context, type);
        catalog.subtitle = "speaking".equals(type)
                ? context.getString(R.string.speaking_catalog_missing)
                : "";
        catalog.items = new ArrayList<>();
        return catalog;
    }

    private static String safe(String value) {
        return value == null ? "catalog" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String defaultTitle(Context context, String type) {
        if ("words".equals(type)) return "单词";
        if ("speaking".equals(type)) return context.getString(R.string.learning_home_speaking_title);
        if ("patterns".equals(type)) return "句型";
        if ("grammar".equals(type)) return "语法";
        if ("pinyin".equals(type)) return "拼音";
        if ("quiz".equals(type)) return "练习题";
        if ("books".equals(type)) return "电子书";
        if ("prompts".equals(type)) return "口语 Prompt";
        return "学习目录";
    }

    static final class Catalog {
        String type;
        String title;
        String subtitle;
        List<Node> items = new ArrayList<>();
    }

    static final class Node {
        String id;
        String title;
        String subtitle;
        String badge;
        String preview;
        String target;
        String level;
        String asset;
        String prompt;
        String coverUrl;
        int coverVersion;
        String dataUrl;
        int dataVersion;
        String dataSha256;
        List<Node> children = new ArrayList<>();

        boolean hasChildren() { return children != null && !children.isEmpty(); }
    }
}
