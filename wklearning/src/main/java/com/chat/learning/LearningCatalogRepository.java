package com.chat.learning;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 学习内容目录仓库。
 * 目录结构来自 assets/learning/{type}/catalog.json，首页不再承载二级/三级目录数据。
 */
final class LearningCatalogRepository {
    private LearningCatalogRepository() {}

    static Catalog load(Context context, String type) {
        try {
            String json = readAsset(context, "learning/" + type + "/catalog.json");
            JSONObject root = new JSONObject(json);
            Catalog catalog = new Catalog();
            catalog.type = root.optString("type", type);
            catalog.title = root.optString("title", defaultTitle(type));
            catalog.subtitle = root.optString("subtitle", "");
            catalog.items = parseItems(root.optJSONArray("items"));
            return catalog;
        } catch (Throwable ignored) {
            return fallback(type);
        }
    }

    static Node find(Catalog catalog, String id) {
        if (catalog == null || id == null || id.length() == 0) return null;
        return findIn(catalog.items, id);
    }

    static List<Node> childrenOf(Catalog catalog, String parentId) {
        if (catalog == null) return new ArrayList<>();
        if (parentId == null || parentId.length() == 0) return catalog.items;
        Node parent = find(catalog, parentId);
        if (parent == null || parent.children == null) return new ArrayList<>();
        return parent.children;
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

    private static List<Node> parseItems(JSONArray array) {
        ArrayList<Node> result = new ArrayList<>();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) continue;
            Node node = new Node();
            node.id = object.optString("id", "item_" + i);
            node.title = object.optString("title", "");
            node.subtitle = object.optString("subtitle", "");
            node.badge = object.optString("badge", "");
            node.preview = object.optString("preview", "");
            node.target = object.optString("target", "");
            node.level = object.optString("level", node.id);
            node.asset = object.optString("asset", "");
            node.prompt = object.optString("prompt", "");
            node.children = parseItems(object.optJSONArray("children"));
            result.add(node);
        }
        return result;
    }

    private static String readAsset(Context context, String path) throws Exception {
        InputStream input = context.getAssets().open(path);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString("UTF-8");
        } finally {
            try { input.close(); } catch (Throwable ignored) {}
        }
    }

    private static Catalog fallback(String type) {
        Catalog catalog = new Catalog();
        catalog.type = type;
        catalog.title = defaultTitle(type);
        catalog.subtitle = "本地目录文件缺失，先使用内置占位目录";
        catalog.items = new ArrayList<>();
        Node node = new Node();
        node.id = type + "_demo";
        node.title = catalog.title + "示例";
        node.subtitle = "后续接入 assets/learning/" + type + "/catalog.json";
        node.target = "study";
        catalog.items.add(node);
        return catalog;
    }

    private static String defaultTitle(String type) {
        if ("words".equals(type)) return "单词";
        if ("speaking".equals(type)) return "口语";
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
        List<Node> children = new ArrayList<>();

        boolean hasChildren() {
            return children != null && !children.isEmpty();
        }
    }
}
