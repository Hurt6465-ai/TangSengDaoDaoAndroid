package com.chat.forum;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chat.base.config.WKApiConfig;

import java.util.List;
import java.util.Locale;

/** Routes forum links to native screens while leaving true external URLs to the browser. */
final class ForumLinkRouter {
    private static final String SCHEME_TALKAMI = "talkami";
    private static final String HOST_FORUM = "forum";

    private ForumLinkRouter() {
    }

    static void setLinkedText(@NonNull TextView view, @Nullable CharSequence value) {
        SpannableStringBuilder text = new SpannableStringBuilder(value == null ? "" : value);
        URLSpan[] links = text.getSpans(0, text.length(), URLSpan.class);
        for (URLSpan link : links) {
            int start = text.getSpanStart(link);
            int end = text.getSpanEnd(link);
            int flags = text.getSpanFlags(link);
            String url = link.getURL();
            text.removeSpan(link);
            text.setSpan(new ForumClickableSpan(url), start, end, flags);
        }
        view.setText(text);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setHighlightColor(Color.TRANSPARENT);
        view.setLinksClickable(true);
    }

    static boolean open(@NonNull Context context, @Nullable String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (TextUtils.isEmpty(url)) return false;

        InternalTarget target = parseInternalTarget(url);
        if (target != null) {
            if (target.articleId > 0L) {
                context.startActivity(ForumArticleActivity.createIntent(context, target.articleId));
                return true;
            }
            if (!TextUtils.isEmpty(target.topicId)) {
                context.startActivity(ForumTopicActivity.createIntent(context, target.topicId));
                return true;
            }
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @NonNull
    static String articleWebUrl(long articleId) {
        return buildForumUrl("article", String.valueOf(articleId));
    }

    @NonNull
    static String topicWebUrl(@Nullable String topicId) {
        return buildForumUrl("topic", topicId == null ? "" : topicId.trim());
    }

    @NonNull
    static String markdownReference(@Nullable String label, @Nullable String url) {
        String safeLabel = TextUtils.isEmpty(label) ? "查看相关内容" : label.trim();
        safeLabel = safeLabel.replace("\\", "\\\\")
                .replace("[", "\\[")
                .replace("]", "\\]");
        String safeUrl = url == null ? "" : url.trim().replace(" ", "%20");
        return "[" + safeLabel + "](" + safeUrl + ")";
    }

    @NonNull
    static String normalizeReference(@Nullable String raw, boolean articleDefault) {
        String value = raw == null ? "" : raw.trim();
        if (TextUtils.isEmpty(value)) return "";
        if (value.matches("[0-9]+")) {
            return articleDefault ? articleWebUrl(parseLong(value)) : topicWebUrl(value);
        }
        InternalTarget target = parseInternalTarget(value);
        if (target == null) return "";
        if (target.articleId > 0L) return articleWebUrl(target.articleId);
        if (!TextUtils.isEmpty(target.topicId)) return topicWebUrl(target.topicId);
        return "";
    }

    static boolean copyToClipboard(@NonNull Context context, @NonNull String label,
                                   @Nullable String text) {
        if (TextUtils.isEmpty(text)) return false;
        ClipboardManager clipboard = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return false;
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        return true;
    }

    @NonNull
    private static String buildForumUrl(@NonNull String type, @NonNull String id) {
        String base = WKApiConfig.getForumBaseUrl();
        if (TextUtils.isEmpty(base)) {
            return "talkami://forum/" + type + "/" + Uri.encode(id);
        }
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/" + type + "/" + Uri.encode(id);
    }

    @Nullable
    private static InternalTarget parseInternalTarget(@NonNull String raw) {
        String value = raw.trim();
        if (value.startsWith("/article/") || value.startsWith("/topic/")) {
            return targetFromPath(value);
        }

        Uri uri;
        try {
            uri = Uri.parse(value);
        } catch (Throwable ignored) {
            return null;
        }

        String scheme = safeLower(uri.getScheme());
        if (SCHEME_TALKAMI.equals(scheme) && HOST_FORUM.equals(safeLower(uri.getHost()))) {
            return targetFromSegments(uri.getPathSegments());
        }

        if (!"http".equals(scheme) && !"https".equals(scheme)) return null;
        if (!isForumHost(uri.getHost())) return null;
        return targetFromSegments(uri.getPathSegments());
    }

    @Nullable
    private static InternalTarget targetFromPath(@NonNull String path) {
        try {
            return targetFromSegments(Uri.parse(path).getPathSegments());
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static InternalTarget targetFromSegments(@Nullable List<String> segments) {
        if (segments == null || segments.size() < 2) return null;
        for (int i = 0; i + 1 < segments.size(); i++) {
            String type = safeLower(segments.get(i));
            String id = segments.get(i + 1) == null ? "" : segments.get(i + 1).trim();
            if (TextUtils.isEmpty(id)) continue;
            if ("article".equals(type)) {
                long articleId = parseLong(id);
                if (articleId > 0L) return InternalTarget.article(articleId);
            }
            if ("topic".equals(type)) return InternalTarget.topic(id);
        }
        return null;
    }

    private static boolean isForumHost(@Nullable String host) {
        if (TextUtils.isEmpty(host)) return false;
        String current = safeLower(host);
        String base = WKApiConfig.getForumBaseUrl();
        if (TextUtils.isEmpty(base)) return false;
        try {
            String expected = safeLower(Uri.parse(base).getHost());
            return !TextUtils.isEmpty(expected)
                    && (TextUtils.equals(current, expected)
                    || TextUtils.equals(stripWww(current), stripWww(expected)));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static long parseLong(@Nullable String value) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    @NonNull
    private static String safeLower(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    @NonNull
    private static String stripWww(@NonNull String host) {
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private static final class ForumClickableSpan extends ClickableSpan {
        private final String url;

        ForumClickableSpan(@Nullable String url) {
            this.url = url == null ? "" : url;
        }

        @Override
        public void onClick(@NonNull View widget) {
            open(widget.getContext(), url);
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            super.updateDrawState(ds);
            ds.setColor(0xFF1877F2);
            ds.setUnderlineText(false);
        }
    }

    private static final class InternalTarget {
        final long articleId;
        final String topicId;

        private InternalTarget(long articleId, @NonNull String topicId) {
            this.articleId = articleId;
            this.topicId = topicId;
        }

        static InternalTarget article(long id) {
            return new InternalTarget(id, "");
        }

        static InternalTarget topic(@NonNull String id) {
            return new InternalTarget(0L, id);
        }
    }
}
