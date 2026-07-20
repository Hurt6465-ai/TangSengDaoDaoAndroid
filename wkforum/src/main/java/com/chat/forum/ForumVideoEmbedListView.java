package com.chat.forum;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONObject;

/**
 * Lightweight official YouTube/TikTok embeds.
 *
 * Posts may contain many supported links, but this view creates only preview cards initially.
 * A WebView is allocated only after the user taps a card, and only one player is kept alive
 * across the process. This is the same performance principle used by mature forum clients:
 * many previews are cheap; the expensive player is lazy and exclusive.
 */
final class ForumVideoEmbedListView extends LinearLayout {
    private static final int MAX_EMBEDS = 30;
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[^\\s<>\\\"']+", Pattern.CASE_INSENSITIVE);
    private static WeakReference<ForumVideoEmbedListView> activeOwner =
            new WeakReference<>(null);
    private static final LruCache<String, String> THUMBNAIL_CACHE = new LruCache<>(64);
    private static final OkHttpClient SHORT_LINK_CLIENT = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build();

    private final List<EmbedCard> cards = new ArrayList<>();
    private final List<Call> resolverCalls = new ArrayList<>();
    private String boundKey = "";
    private EmbedCard activeCard;

    ForumVideoEmbedListView(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        setVisibility(GONE);
    }

    @Nullable
    static String normalizeSupportedUrl(@Nullable String raw) {
        Embed embed = parse(cleanCandidate(raw));
        return embed == null ? null : embed.shareUrl;
    }

    @NonNull
    static String stripStandaloneEmbedUrls(@Nullable String content) {
        if (TextUtils.isEmpty(content)) return "";
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            if (normalizeSupportedUrl(line.trim()) != null) continue;
            if (result.length() > 0) result.append('\n');
            result.append(line);
        }
        return result.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    void bind(@Nullable String content) {
        List<Embed> embeds = extract(content);
        String key = buildKey(embeds);
        if (TextUtils.equals(boundKey, key)) {
            setVisibility(embeds.isEmpty() ? GONE : VISIBLE);
            return;
        }
        recycle();
        boundKey = key;
        if (embeds.isEmpty()) {
            setVisibility(GONE);
            return;
        }
        for (int i = 0; i < embeds.size(); i++) {
            EmbedCard card = createPreviewCard(embeds.get(i), i);
            cards.add(card);
            addView(card.root);
        }
        setVisibility(VISIBLE);
    }

    void recycle() {
        deactivatePlayer();
        for (Call call : new ArrayList<>(resolverCalls)) {
            if (call != null) call.cancel();
        }
        resolverCalls.clear();
        for (EmbedCard card : new ArrayList<>(cards)) {
            try {
                Glide.with(card.thumbnail).clear(card.thumbnail);
            } catch (Throwable ignored) {
            }
        }
        cards.clear();
        removeAllViews();
        boundKey = "";
        setVisibility(GONE);
    }

    @Override
    protected void onDetachedFromWindow() {
        deactivatePlayer();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        WebView player = activeCard == null ? null : activeCard.player;
        if (player == null) return;
        try {
            if (visibility == VISIBLE) player.onResume();
            else player.onPause();
        } catch (Throwable ignored) {
        }
    }

    @NonNull
    private EmbedCard createPreviewCard(@NonNull Embed embed, int index) {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(VERTICAL);
        GradientDrawable background = roundRect(0xFF111318, 13);
        root.setBackground(background);
        root.setClipToOutline(true);

        FrameLayout media = new FrameLayout(getContext());
        media.setBackgroundColor(0xFF111318);

        ImageView thumbnail = new ImageView(getContext());
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnail.setBackgroundColor(embed.platform == Platform.YOUTUBE
                ? 0xFF15171B : 0xFF101116);
        media.addView(thumbnail, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View shade = new View(getContext());
        shade.setBackgroundColor(0x45000000);
        media.addView(shade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView brand = text(embed.platform.label, 12.5f, Color.WHITE, true);
        brand.setGravity(Gravity.CENTER);
        brand.setPadding(dp(9), 0, dp(9), 0);
        brand.setBackground(roundRect(0xA8000000, 12));
        FrameLayout.LayoutParams brandParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(26), Gravity.START | Gravity.TOP);
        brandParams.leftMargin = dp(10);
        brandParams.topMargin = dp(10);
        media.addView(brand, brandParams);

        TextView open = text("↗", 19, Color.WHITE, true);
        open.setGravity(Gravity.CENTER);
        open.setContentDescription(getResources().getString(R.string.forum_video_open_in, embed.platform.label));
        open.setBackground(roundRect(0x8F000000, 18));
        open.setOnClickListener(v -> ForumLinkRouter.open(getContext(), embed.shareUrl));
        FrameLayout.LayoutParams openParams = new FrameLayout.LayoutParams(
                dp(36), dp(36), Gravity.END | Gravity.TOP);
        openParams.rightMargin = dp(8);
        openParams.topMargin = dp(8);
        media.addView(open, openParams);

        TextView play = text("▶", 28, Color.WHITE, true);
        play.setGravity(Gravity.CENTER);
        play.setIncludeFontPadding(false);
        play.setPadding(dp(3), 0, 0, 0);
        play.setBackground(roundRect(0xD9000000, 30));
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(
                dp(60), dp(60), Gravity.CENTER);
        media.addView(play, playParams);

        int mediaHeight = dp(200);
        root.addView(media, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, mediaHeight));

        LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rootParams.topMargin = index == 0 ? dp(10) : dp(9);
        root.setLayoutParams(rootParams);

        EmbedCard card = new EmbedCard(embed, root, media, thumbnail);
        media.setOnClickListener(v -> activatePlayer(card));
        play.setOnClickListener(v -> activatePlayer(card));
        loadPreview(card);
        return card;
    }

    private void loadPreview(@NonNull EmbedCard card) {
        if (card.embed.platform == Platform.YOUTUBE) {
            Glide.with(card.thumbnail)
                    .load("https://i.ytimg.com/vi/" + card.embed.id + "/mqdefault.jpg")
                    .centerCrop()
                    .into(card.thumbnail);
            return;
        }
        if (TextUtils.isEmpty(card.embed.id)) {
            resolveTikTokShortLink(card, false);
        } else {
            loadTikTokCover(card);
        }
    }

    private void loadTikTokCover(@NonNull EmbedCard card) {
        if (card.coverLoading || TextUtils.isEmpty(card.embed.shareUrl)) return;
        final String expectedKey = card.embed.key();
        String cached;
        synchronized (THUMBNAIL_CACHE) {
            cached = THUMBNAIL_CACHE.get(expectedKey);
        }
        if (!TextUtils.isEmpty(cached)) {
            Glide.with(card.thumbnail).load(cached).centerCrop().into(card.thumbnail);
            return;
        }
        card.coverLoading = true;
        String endpoint = "https://www.tiktok.com/oembed?url=" + Uri.encode(card.embed.shareUrl);
        Request request = new Request.Builder()
                .url(endpoint)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Mobile Safari/537.36")
                .get()
                .build();
        Call call = SHORT_LINK_CLIENT.newCall(request);
        resolverCalls.add(call);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call failedCall, @NonNull IOException error) {
                post(() -> finishTikTokCover(card, failedCall, expectedKey, null));
            }

            @Override
            public void onResponse(@NonNull Call completedCall, @NonNull Response response) {
                String thumbnailUrl = null;
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        thumbnailUrl = json.optString("thumbnail_url", "").trim();
                    }
                } catch (Throwable ignored) {
                    thumbnailUrl = null;
                } finally {
                    response.close();
                }
                final String result = thumbnailUrl;
                post(() -> finishTikTokCover(card, completedCall, expectedKey, result));
            }
        });
    }

    private void finishTikTokCover(@NonNull EmbedCard card, @NonNull Call call,
                                   @NonNull String expectedKey, @Nullable String thumbnailUrl) {
        resolverCalls.remove(call);
        card.coverLoading = false;
        if (!cards.contains(card) || !isAttachedToWindow()
                || !TextUtils.equals(expectedKey, card.embed.key())
                || TextUtils.isEmpty(thumbnailUrl)) return;
        synchronized (THUMBNAIL_CACHE) {
            THUMBNAIL_CACHE.put(expectedKey, thumbnailUrl);
        }
        Glide.with(card.thumbnail).load(thumbnailUrl).centerCrop().into(card.thumbnail);
    }

    private void activatePlayer(@NonNull EmbedCard card) {
        if (card.resolving) {
            card.playAfterResolve = true;
            Toast.makeText(getContext(), R.string.forum_video_preparing, Toast.LENGTH_SHORT).show();
            return;
        }
        if (card.embed.platform == Platform.TIKTOK && TextUtils.isEmpty(card.embed.id)) {
            resolveTikTokShortLink(card, true);
            return;
        }
        if (card == activeCard && card.player != null) return;
        stopOtherActivePlayer();
        deactivatePlayer();

        activeOwner = new WeakReference<>(this);
        activeCard = card;
        card.previewChildren = snapshotChildren(card.media);
        card.media.removeAllViews();

        WebView webView = new WebView(getContext());
        configureWebView(webView, card);
        card.player = webView;
        card.media.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webView.loadDataWithBaseURL("https://forum.talkami.local/",
                buildHtml(card.embed, true), "text/html", "UTF-8", null);
    }

    private void resolveTikTokShortLink(@NonNull EmbedCard card, boolean playAfterResolve) {
        if (card.resolving) {
            card.playAfterResolve = card.playAfterResolve || playAfterResolve;
            return;
        }
        card.resolving = true;
        card.playAfterResolve = playAfterResolve;
        Request request = new Request.Builder()
                .url(card.embed.shareUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Mobile Safari/537.36")
                .get()
                .build();
        Call call = SHORT_LINK_CLIENT.newCall(request);
        resolverCalls.add(call);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call failedCall, @NonNull IOException error) {
                post(() -> finishShortLinkResolve(card, failedCall, null));
            }

            @Override
            public void onResponse(@NonNull Call completedCall, @NonNull Response response) {
                String finalUrl = response.request().url().toString();
                response.close();
                Embed resolved = parse(finalUrl);
                post(() -> finishShortLinkResolve(card, completedCall, resolved));
            }
        });
    }

    private void finishShortLinkResolve(@NonNull EmbedCard card, @NonNull Call call,
                                        @Nullable Embed resolved) {
        resolverCalls.remove(call);
        boolean shouldPlay = card.playAfterResolve;
        card.playAfterResolve = false;
        card.resolving = false;
        if (!cards.contains(card) || !isAttachedToWindow()) return;
        if (resolved != null && !TextUtils.isEmpty(resolved.id)) {
            card.embed = resolved;
            loadTikTokCover(card);
            if (shouldPlay) activatePlayer(card);
            return;
        }
        if (shouldPlay) {
            Toast.makeText(getContext(), R.string.forum_video_short_link_failed,
                    Toast.LENGTH_SHORT).show();
            ForumLinkRouter.open(getContext(), card.embed.shareUrl);
        }
    }

    private void stopOtherActivePlayer() {
        ForumVideoEmbedListView owner = activeOwner.get();
        if (owner != null && owner != this) owner.deactivatePlayer();
    }

    private void deactivatePlayer() {
        EmbedCard card = activeCard;
        activeCard = null;
        if (card == null) return;

        WebView player = card.player;
        card.player = null;
        if (player != null) {
            try {
                player.stopLoading();
                player.loadUrl("about:blank");
                player.setWebChromeClient(null);
                player.setWebViewClient(null);
                player.removeAllViews();
                player.destroy();
            } catch (Throwable ignored) {
            }
        }
        if (card.chromeClient != null) {
            try {
                card.chromeClient.release();
            } catch (Throwable ignored) {
            }
            card.chromeClient = null;
        }
        card.media.removeAllViews();
        if (card.previewChildren != null) {
            for (View child : card.previewChildren) {
                ViewGroup parent = child.getParent() instanceof ViewGroup
                        ? (ViewGroup) child.getParent() : null;
                if (parent != null) parent.removeView(child);
                card.media.addView(child);
            }
        }
        card.previewChildren = null;

        ForumVideoEmbedListView owner = activeOwner.get();
        if (owner == this) activeOwner = new WeakReference<>(null);
    }

    @NonNull
    private static List<View> snapshotChildren(@NonNull ViewGroup parent) {
        List<View> children = new ArrayList<>();
        for (int i = 0; i < parent.getChildCount(); i++) children.add(parent.getChildAt(i));
        return children;
    }

    private void configureWebView(@NonNull WebView webView, @NonNull EmbedCard card) {
        webView.setBackgroundColor(Color.BLACK);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || !request.isForMainFrame()) return false;
                Uri url = request.getUrl();
                if (url == null || "about".equalsIgnoreCase(url.getScheme())) return false;
                String value = url.toString();
                if (value.startsWith("https://forum.talkami.local/")
                        || isOfficialPlayerUrl(value)) return false;
                ForumLinkRouter.open(getContext(), value);
                return true;
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (TextUtils.isEmpty(url) || url.startsWith("about:")
                        || url.startsWith("https://forum.talkami.local/")
                        || isOfficialPlayerUrl(url)) return false;
                ForumLinkRouter.open(getContext(), url);
                return true;
            }
        });
        FullscreenChromeClient chromeClient = new FullscreenChromeClient(getContext());
        card.chromeClient = chromeClient;
        webView.setWebChromeClient(chromeClient);
    }

    private static boolean isOfficialPlayerUrl(@Nullable String raw) {
        if (TextUtils.isEmpty(raw)) return false;
        try {
            Uri uri = Uri.parse(raw);
            String host = stripWww(lower(uri.getHost()));
            String path = uri.getPath() == null ? "" : uri.getPath();
            if ((host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com"))
                    && path.startsWith("/embed/")) return true;
            return host.endsWith("tiktok.com") && path.startsWith("/player/v1/");
        } catch (Throwable ignored) {
            return false;
        }
    }

    @NonNull
    private static List<Embed> extract(@Nullable String content) {
        Map<String, Embed> unique = new LinkedHashMap<>();
        if (TextUtils.isEmpty(content)) return new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(content);
        while (matcher.find() && unique.size() < MAX_EMBEDS) {
            Embed embed = parse(cleanCandidate(matcher.group()));
            if (embed != null) unique.put(embed.key(), embed);
        }
        return new ArrayList<>(unique.values());
    }

    @Nullable
    private static Embed parse(@Nullable String raw) {
        if (TextUtils.isEmpty(raw)) return null;
        Uri uri;
        try {
            uri = Uri.parse(raw.trim());
        } catch (Throwable ignored) {
            return null;
        }
        String scheme = lower(uri.getScheme());
        if (!"http".equals(scheme) && !"https".equals(scheme)) return null;
        String host = stripWww(lower(uri.getHost()));
        List<String> segments = uri.getPathSegments();

        String youtubeId = "";
        if ("youtu.be".equals(host) && !segments.isEmpty()) {
            youtubeId = segments.get(0);
        } else if (host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com")) {
            if ("watch".equals(first(segments))) {
                youtubeId = uri.getQueryParameter("v");
            } else if (("shorts".equals(first(segments)) || "embed".equals(first(segments))
                    || "live".equals(first(segments))) && segments.size() > 1) {
                youtubeId = segments.get(1);
            }
        }
        if (validYoutubeId(youtubeId)) {
            String id = youtubeId.trim();
            return new Embed(Platform.YOUTUBE, id,
                    "https://www.youtube.com/watch?v=" + id,
                    "https://www.youtube-nocookie.com/embed/" + id);
        }

        if (host.endsWith("tiktok.com")) {
            String id = "";
            for (int i = 0; i + 1 < segments.size(); i++) {
                if ("video".equalsIgnoreCase(segments.get(i))
                        || ("v1".equalsIgnoreCase(segments.get(i))
                        && i > 0 && "player".equalsIgnoreCase(segments.get(i - 1)))) {
                    id = segments.get(i + 1);
                    break;
                }
            }
            if (id.matches("[0-9]{8,24}")) {
                String share = raw.trim();
                return new Embed(Platform.TIKTOK, id, share,
                        "https://www.tiktok.com/player/v1/" + id);
            }
            if ("vm.tiktok.com".equals(host) || "vt.tiktok.com".equals(host)
                    || "www.tiktok.com".equals(host) && "t".equals(first(segments))) {
                return new Embed(Platform.TIKTOK, "", raw.trim(), "");
            }
        }
        return null;
    }

    @NonNull
    private static String buildHtml(@NonNull Embed embed, boolean autoplay) {
        String title = embed.platform == Platform.YOUTUBE ? "YouTube video" : "TikTok video";
        String playerUrl;
        if (embed.platform == Platform.YOUTUBE) {
            playerUrl = embed.playerBaseUrl + "?autoplay=" + (autoplay ? "1" : "0")
                    + "&playsinline=1&rel=0&controls=1";
        } else {
            playerUrl = embed.playerBaseUrl + "?autoplay=" + (autoplay ? "1" : "0")
                    + "&controls=1&progress_bar=1&play_button=1&volume_control=1"
                    + "&fullscreen_button=1&timestamp=1&description=0&music_info=0&rel=0";
        }
        String allow = "autoplay; encrypted-media; picture-in-picture; fullscreen";
        return "<!doctype html><html><head>"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1\">"
                + "<style>html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden;}"
                + "iframe{display:block;border:0;width:100%;height:100%;background:#000;}</style>"
                + "</head><body><iframe src=\"" + escapeHtml(playerUrl) + "\""
                + " title=\"" + title + "\" allow=\"" + allow + "\""
                + " allowfullscreen referrerpolicy=\"strict-origin-when-cross-origin\"></iframe>"
                + "</body></html>";
    }

    @NonNull
    private static String buildKey(@NonNull List<Embed> embeds) {
        StringBuilder out = new StringBuilder();
        for (Embed embed : embeds) out.append(embed.key()).append(';');
        return out.toString();
    }

    @NonNull
    private static String cleanCandidate(@Nullable String raw) {
        String value = raw == null ? "" : raw.trim();
        while (!value.isEmpty()) {
            char last = value.charAt(value.length() - 1);
            if (last == ')' || last == ']' || last == '}' || last == ',' || last == '.'
                    || last == ';' || last == '!' || last == '?' || last == '，'
                    || last == '。') {
                value = value.substring(0, value.length() - 1);
            } else {
                break;
            }
        }
        return value;
    }

    private static boolean validYoutubeId(@Nullable String id) {
        return id != null && id.trim().matches("[A-Za-z0-9_-]{6,20}");
    }

    @NonNull
    private static String first(@Nullable List<String> values) {
        return values == null || values.isEmpty() ? "" : lower(values.get(0));
    }

    @NonNull
    private static String lower(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    @NonNull
    private static String stripWww(@NonNull String value) {
        return value.startsWith("www.") ? value.substring(4) : value;
    }

    @NonNull
    private static String escapeHtml(@NonNull String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    @NonNull
    private GradientDrawable roundRect(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    @NonNull
    private TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private enum Platform {
        YOUTUBE("YouTube"), TIKTOK("TikTok");
        final String label;
        Platform(String label) { this.label = label; }
    }

    private static final class Embed {
        final Platform platform;
        final String id;
        final String shareUrl;
        final String playerBaseUrl;

        Embed(Platform platform, String id, String shareUrl, String playerBaseUrl) {
            this.platform = platform;
            this.id = id;
            this.shareUrl = shareUrl;
            this.playerBaseUrl = playerBaseUrl;
        }

        String key() {
            return platform.name() + ':' + (TextUtils.isEmpty(id) ? shareUrl : id);
        }
    }

    private static final class EmbedCard {
        Embed embed;
        final LinearLayout root;
        final FrameLayout media;
        final ImageView thumbnail;
        List<View> previewChildren;
        WebView player;
        FullscreenChromeClient chromeClient;
        boolean resolving;
        boolean playAfterResolve;
        boolean coverLoading;

        EmbedCard(Embed embed, LinearLayout root, FrameLayout media, ImageView thumbnail) {
            this.embed = embed;
            this.root = root;
            this.media = media;
            this.thumbnail = thumbnail;
        }
    }

    private static final class FullscreenChromeClient extends WebChromeClient {
        private final Context context;
        private Dialog dialog;
        private CustomViewCallback callback;

        FullscreenChromeClient(@NonNull Context context) {
            this.context = context;
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (!(context instanceof Activity) || view == null) {
                if (callback != null) callback.onCustomViewHidden();
                return;
            }
            onHideCustomView();
            this.callback = callback;
            dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            dialog.setContentView(view, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            Window window = dialog.getWindow();
            if (window != null) {
                window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN);
                window.setBackgroundDrawableResource(android.R.color.black);
            }
            dialog.setOnDismissListener(ignored -> finishCustomView());
            dialog.show();
        }

        @Override
        public void onHideCustomView() {
            if (dialog != null) {
                Dialog old = dialog;
                dialog = null;
                old.setOnDismissListener(null);
                old.dismiss();
            }
            finishCustomView();
        }

        void release() {
            onHideCustomView();
        }

        private void finishCustomView() {
            CustomViewCallback old = callback;
            callback = null;
            if (old != null) old.onCustomViewHidden();
        }
    }
}
