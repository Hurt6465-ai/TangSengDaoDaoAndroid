package com.chat.learning;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Learning content catalog with bundled fallback, remote additions and imported word books. */
final class LearningCatalogRepository {
    private static final int MAX_CATALOG_BYTES = 2 * 1024 * 1024;

    interface Callback {
        void onLoaded(Catalog catalog);
        void onError(Throwable error);
    }

    private LearningCatalogRepository() {}

    static Catalog load(Context context, String type) {
        Catalog bundled = readBundled(context, type);
        Catalog cached = readCached(context, type);
        Catalog merged = mergeCatalogs(bundled, cached);
        if ("words".equals(type)) appendImportedWords(context, merged);
        if ("speaking".equals(type)) appendImportedSpeaking(context, merged);
        return merged != null ? merged : fallback(context, type);
    }

    static void refresh(Context context, String type, Callback callback) {
        if (!"words".equals(type)) return;
        String path = LearningRemoteContent.config(context).wordsCatalog;
        String resolved = LearningRemoteContent.resolveUrl(context, path);
        if (resolved.isEmpty()) return;
        // The catalog is tiny and changes whenever a new remote word book is published.
        // A minute-based query value prevents a stale CDN/catalog cache from hiding additions.
        final String catalogUrl = appendCatalogRevision(resolved);
        Context app = context.getApplicationContext();
        File cache = cacheFile(app, type);
        LearningRemoteContent.execute(() -> {
            try {
                byte[] bytes = LearningRemoteContent.download(app, catalogUrl, MAX_CATALOG_BYTES);
                Catalog remote = parse(app, type, new String(bytes, StandardCharsets.UTF_8));
                LearningRemoteContent.atomicWrite(cache, bytes);
                Catalog merged = mergeCatalogs(readBundled(app, type), remote);
                appendImportedWords(app, merged);
                if (callback != null) callback.onLoaded(merged);
            } catch (Throwable error) {
                if (callback != null) callback.onError(error);
            }
        });
    }


    private static String appendCatalogRevision(String url) {
        if (url == null || url.isEmpty()) return "";
        long minute = System.currentTimeMillis() / 60000L;
        return url + (url.contains("?") ? "&" : "?") + "catalog_rev=" + minute;
    }

    private static Catalog readBundled(Context context, String type) {
        try {
            String json = LearningRemoteContent.readAsset(context, "learning/" + type + "/catalog.json");
            return parse(context, type, json);
        } catch (Throwable ignored) {
            return fallback(context, type);
        }
    }

    private static Catalog readCached(Context context, String type) {
        try {
            String json = LearningRemoteContent.readFile(cacheFile(context, type), MAX_CATALOG_BYTES);
            return json.isEmpty() ? null : parse(context, type, json);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static File cacheFile(Context context, String type) {
        return new File(context.getFilesDir(), "learning/catalogs/" + safe(type) + ".json");
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
        if (catalog == null || id == null || id.isEmpty()) return null;
        return findIn(catalog.items, id);
    }

    static List<Node> childrenOf(Catalog catalog, String parentId) {
        if (catalog == null) return new ArrayList<>();
        if (parentId == null || parentId.isEmpty()) return catalog.items;
        Node parent = find(catalog, parentId);
        return parent == null || parent.children == null ? new ArrayList<>() : parent.children;
    }

    static String titleFor(Catalog catalog, String parentId) {
        if (catalog == null) return "学习目录";
        if (parentId == null || parentId.isEmpty()) return catalog.title;
        Node node = find(catalog, parentId);
        return node != null ? node.title : catalog.title;
    }

    static String subtitleFor(Catalog catalog, String parentId) {
        if (catalog == null) return "";
        if (parentId == null || parentId.isEmpty()) return catalog.subtitle;
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
            node.itemCount = Math.max(0, object.optInt("item_count", countFromBadge(node.badge)));
            node.children = parseItems(context, object.optJSONArray("children"));
            result.add(node);
        }
        return result;
    }

    private static Catalog mergeCatalogs(Catalog base, Catalog overlay) {
        if (base == null) return overlay;
        if (overlay == null) return base;
        Catalog merged = new Catalog();
        merged.type = nonEmpty(overlay.type, base.type);
        merged.title = nonEmpty(overlay.title, base.title);
        merged.subtitle = nonEmpty(overlay.subtitle, base.subtitle);
        merged.items = mergeNodes(base.items, overlay.items);
        return merged;
    }

    private static List<Node> mergeNodes(List<Node> base, List<Node> overlay) {
        LinkedHashMap<String, Node> result = new LinkedHashMap<>();
        if (base != null) {
            for (Node node : base) result.put(node.id, copyNode(node));
        }
        if (overlay != null) {
            for (Node node : overlay) {
                Node old = result.get(node.id);
                result.put(node.id, old == null ? copyNode(node) : mergeNode(old, node));
            }
        }
        return new ArrayList<>(result.values());
    }

    private static Node mergeNode(Node base, Node overlay) {
        boolean staleOverlay = overlay.dataVersion > 0 && base.dataVersion > 0
                && overlay.dataVersion < base.dataVersion;
        boolean incompleteSameVersion = overlay.dataVersion == base.dataVersion
                && overlay.itemCount > 0 && base.itemCount > overlay.itemCount;
        Node preferred = staleOverlay || incompleteSameVersion ? base : overlay;
        Node fallback = preferred == overlay ? base : overlay;

        Node node = new Node();
        node.id = nonEmpty(preferred.id, fallback.id);
        node.title = nonEmpty(preferred.title, fallback.title);
        node.subtitle = nonEmpty(preferred.subtitle, fallback.subtitle);
        node.badge = nonEmpty(preferred.badge, fallback.badge);
        node.preview = nonEmpty(preferred.preview, fallback.preview);
        node.target = nonEmpty(preferred.target, fallback.target);
        node.level = nonEmpty(preferred.level, fallback.level);
        node.asset = nonEmpty(preferred.asset, fallback.asset);
        node.prompt = nonEmpty(preferred.prompt, fallback.prompt);
        node.coverUrl = nonEmpty(preferred.coverUrl, fallback.coverUrl);
        node.coverVersion = Math.max(base.coverVersion, overlay.coverVersion);
        node.dataUrl = nonEmpty(preferred.dataUrl, fallback.dataUrl);
        node.dataVersion = Math.max(base.dataVersion, overlay.dataVersion);
        node.dataSha256 = nonEmpty(preferred.dataSha256, fallback.dataSha256);
        node.itemCount = Math.max(base.itemCount, overlay.itemCount);
        node.imported = overlay.imported || base.imported;
        node.children = mergeNodes(base.children, overlay.children);
        return node;
    }

    private static Node copyNode(Node source) {
        Node node = new Node();
        node.id = source.id;
        node.title = source.title;
        node.subtitle = source.subtitle;
        node.badge = source.badge;
        node.preview = source.preview;
        node.target = source.target;
        node.level = source.level;
        node.asset = source.asset;
        node.prompt = source.prompt;
        node.coverUrl = source.coverUrl;
        node.coverVersion = source.coverVersion;
        node.dataUrl = source.dataUrl;
        node.dataVersion = source.dataVersion;
        node.dataSha256 = source.dataSha256;
        node.itemCount = source.itemCount;
        node.imported = source.imported;
        node.children = source.children == null ? new ArrayList<>() : mergeNodes(source.children, null);
        return node;
    }

    private static void appendImportedWords(Context context, Catalog catalog) {
        if (catalog == null) return;
        List<Node> imported = LearningImportedWordStore.nodes(context);
        catalog.items = mergeNodes(catalog.items, imported);
    }

    private static void appendImportedSpeaking(Context context, Catalog catalog) {
        if (catalog == null) return;
        List<Node> imported = SpeakingImportedPackStore.nodes(context);
        catalog.items = mergeNodes(catalog.items, imported);
    }

    private static String localized(Context context, JSONObject object, String key, String fallback) {
        String suffix = localeSuffix(context);
        String value = suffix.isEmpty() ? "" : object.optString(key + suffix, "").trim();
        if (value.isEmpty()) value = object.optString(key, fallback).trim();
        return value.isEmpty() ? (fallback == null ? "" : fallback) : value;
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

    private static String nonEmpty(String first, String second) {
        return first != null && !first.trim().isEmpty() ? first.trim()
                : second == null ? "" : second;
    }

    private static int countFromBadge(String badge) {
        if (badge == null) return 0;
        String digits = badge.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try { return Integer.parseInt(digits); }
        catch (Throwable ignored) { return 0; }
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
        int itemCount;
        boolean imported;
        List<Node> children = new ArrayList<>();

        boolean hasChildren() { return children != null && !children.isEmpty(); }
    }
}
