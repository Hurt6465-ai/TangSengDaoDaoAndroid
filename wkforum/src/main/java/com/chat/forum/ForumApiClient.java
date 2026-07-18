package com.chat.forum;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.net.FastJsonConverterFactory;
import com.chat.base.net.entity.UploadFileUrl;
import com.chat.base.net.RetrofitUtils;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.http.Body;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Url;

/**
 * Native bbs-go client.
 *
 * TangSeng requests use the app Retrofit instance so the normal TangSeng token
 * and device headers are preserved. Forum requests deliberately use a separate
 * clean OkHttp client, therefore the TangSeng token is never sent to the forum
 * origin.
 */
public final class ForumApiClient {
    private static final String PREF_TOKEN = "forum_bbsgo_token";
    private static final String PREF_EXPIRES_AT = "forum_bbsgo_token_expires_at";
    private static final String PREF_USER_ID = "forum_bbsgo_user_id";
    private static final String PREF_ROLES = "forum_bbsgo_roles";
    private static final String PREF_PERMISSIONS = "forum_bbsgo_permissions";
    private static final String PREF_AUTH_META_VERSION = "forum_bbsgo_auth_meta_version";
    private static final String AUTH_META_VERSION = "6";
    private static final long SESSION_SAFETY_WINDOW_MS = 60_000L;

    private static final ForumApiClient INSTANCE = new ForumApiClient();
    private final Object authLock = new Object();
    private final List<ResultCallback<String>> pendingAuthCallbacks = new ArrayList<>();
    private boolean authInFlight;
    private String authUid = "";
    private volatile Retrofit forumRetrofit;
    private volatile String forumRetrofitBaseUrl = "";

    private ForumApiClient() {
    }

    public static ForumApiClient getInstance() {
        return INSTANCE;
    }

    public interface ResultCallback<T> {
        void onSuccess(@Nullable T data);

        void onError(@NonNull String message);
    }

    public void ensureSession(@NonNull Context context, @NonNull ResultCallback<String> callback) {
        final String uid = currentUid();
        if (TextUtils.isEmpty(uid)) {
            callback.onError("请先登录");
            return;
        }
        String cachedToken = getSessionToken(uid);
        boolean cachedSessionValid = !TextUtils.isEmpty(cachedToken)
                && getSessionExpiresAt(uid) - System.currentTimeMillis() > SESSION_SAFETY_WINDOW_MS;
        String authMetaVersion = WKSharedPreferencesUtil.getInstance()
                .getSP(uidKey(uid, PREF_AUTH_META_VERSION));
        if (cachedSessionValid && !TextUtils.isEmpty(getForumUserId(uid))
                && AUTH_META_VERSION.equals(authMetaVersion)) {
            callback.onSuccess(cachedToken);
            return;
        }
        if (cachedSessionValid) invalidateSession(uid);

        List<ResultCallback<String>> staleCallbacks = null;
        boolean startAuth = false;
        synchronized (authLock) {
            if (authInFlight && !TextUtils.equals(authUid, uid)) {
                staleCallbacks = new ArrayList<>(pendingAuthCallbacks);
                pendingAuthCallbacks.clear();
                authUid = uid;
                pendingAuthCallbacks.add(callback);
                startAuth = true;
            } else {
                pendingAuthCallbacks.add(callback);
                if (!authInFlight) {
                    authInFlight = true;
                    authUid = uid;
                    startAuth = true;
                }
            }
        }
        if (staleCallbacks != null) {
            for (ResultCallback<String> stale : staleCallbacks) {
                stale.onError("账号已切换，请重试");
            }
        }
        if (startAuth) requestTangSengToken(context.getApplicationContext(), uid);
    }

    public void invalidateSession() {
        invalidateSession(currentUid());
    }

    private void invalidateSession(@Nullable String uid) {
        if (TextUtils.isEmpty(uid)) return;
        WKSharedPreferencesUtil prefs = WKSharedPreferencesUtil.getInstance();
        prefs.putSP(uidKey(uid, PREF_TOKEN), "");
        prefs.putLong(uidKey(uid, PREF_EXPIRES_AT), 0L);
        prefs.putSP(uidKey(uid, PREF_USER_ID), "");
        prefs.putSP(uidKey(uid, PREF_ROLES), "");
        prefs.putSP(uidKey(uid, PREF_PERMISSIONS), "");
        prefs.putSP(uidKey(uid, PREF_AUTH_META_VERSION), "");
    }

    public boolean hasValidSession() {
        String uid = currentUid();
        return !TextUtils.isEmpty(uid) && !TextUtils.isEmpty(getSessionToken(uid))
                && getSessionExpiresAt(uid) - System.currentTimeMillis() > SESSION_SAFETY_WINDOW_MS;
    }

    @NonNull
    public String getCurrentForumUserId() {
        return getForumUserId(currentUid());
    }

    public boolean hasRole(@NonNull String role) {
        return containsStoredValue(PREF_ROLES, role);
    }

    public boolean hasPermission(@NonNull String permission) {
        return containsStoredValue(PREF_PERMISSIONS, "*")
                || containsStoredValue(PREF_PERMISSIONS, permission);
    }

    public boolean isForumManager() {
        return hasRole("owner") || hasRole("admin") || hasRole("administrator")
                || hasRole("moderator") || hasRole("管理员") || hasRole("站长")
                || hasPermission("dashboard.topic.recommend")
                || hasPermission("dashboard.topic.sticky")
                || hasPermission("dashboard.topic.delete")
                || hasPermission("dashboard.comment.delete")
                || hasPermission("dashboard.user.forbidden")
                || hasPermission("dashboard.user.forbiddenForever")
                || hasPermission("dashboard.category.update");
    }

    private boolean containsStoredValue(@NonNull String key, @NonNull String expected) {
        String uid = currentUid();
        if (TextUtils.isEmpty(uid) || TextUtils.isEmpty(expected)) return false;
        String raw = WKSharedPreferencesUtil.getInstance().getSP(uidKey(uid, key));
        if (TextUtils.isEmpty(raw)) return false;
        String[] values = raw.split(",", -1);
        for (String value : values) {
            if (expected.equals(value == null ? "" : value.trim())) return true;
        }
        return false;
    }

    @NonNull
    private String getForumUserId(@Nullable String uid) {
        return TextUtils.isEmpty(uid) ? "" : WKSharedPreferencesUtil.getInstance()
                .getSP(uidKey(uid, PREF_USER_ID));
    }

    @NonNull
    public String resolveUrl(@Nullable String value) {
        String url = value == null ? "" : value.trim();
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("https://") || url.startsWith("http://")) return url;
        try {
            return new URL(new URL(WKApiConfig.getForumBaseUrl()), url).toString();
        } catch (Throwable ignored) {
            return url;
        }
    }

    public void getCategories(@NonNull ResultCallback<List<Category>> callback) {
        forumService().categories(authHeader()).enqueue(new EnvelopeCallback<>(callback));
    }

    public void getTopics(long categoryId, @Nullable String cursor,
                          @NonNull ResultCallback<Page<Topic>> callback) {
        getTopics(categoryId, cursor, "latestPublish", callback);
    }

    public void getTopics(long categoryId, @Nullable String cursor, @Nullable String sort,
                          @NonNull ResultCallback<Page<Topic>> callback) {
        forumService().topics(authHeader(), categoryId,
                        TextUtils.isEmpty(cursor) ? "" : cursor,
                        TextUtils.isEmpty(sort) ? "" : sort)
                .enqueue(new EnvelopeCallback<>(callback));
    }

    public void getUserTopics(@NonNull String userId, @Nullable String cursor,
                              @NonNull ResultCallback<Page<Topic>> callback) {
        forumService().userTopics(requireAuthHeader(), userId,
                        TextUtils.isEmpty(cursor) ? "" : cursor)
                .enqueue(new EnvelopeCallback<>(callback));
    }

    public void getFavorites(@Nullable String cursor,
                             @NonNull ResultCallback<Page<Favorite>> callback) {
        forumService().favorites(requireAuthHeader(),
                        TextUtils.isEmpty(cursor) ? "" : cursor)
                .enqueue(new EnvelopeCallback<>(callback));
    }

    public void getRecentMessages(@NonNull ResultCallback<RecentMessages> callback) {
        forumService().recentMessages(requireAuthHeader())
                .enqueue(new EnvelopeCallback<>(callback));
    }

    public void getUserFollowed(@NonNull String userId,
                                @NonNull ResultCallback<Boolean> callback) {
        forumService().isFollowed(requireAuthHeader(), userId)
                .enqueue(new EnvelopeCallback<>(callback));
    }

    public void setUserFollowed(@NonNull String userId, boolean followed,
                                @NonNull ResultCallback<Void> callback) {
        Call<ApiEnvelope<Void>> call = followed
                ? forumService().follow(requireAuthHeader(), userId)
                : forumService().unfollow(requireAuthHeader(), userId);
        call.enqueue(new EnvelopeCallback<>(callback));
    }

    public void getMessages(@Nullable String cursor,
                            @NonNull ResultCallback<Page<Message>> callback) {
        forumService().messages(requireAuthHeader(),
                        TextUtils.isEmpty(cursor) ? "" : cursor)
                .enqueue(new EnvelopeCallback<>(callback));
    }

    @NonNull
    public String topicIdFromUrl(@Nullable String value) {
        String url = value == null ? "" : value.trim();
        if (TextUtils.isEmpty(url)) return "";
        try {
            List<String> segments = Uri.parse(url).getPathSegments();
            for (int i = 0; i + 1 < segments.size(); i++) {
                if ("topic".equals(segments.get(i))) return segments.get(i + 1);
            }
        } catch (Throwable ignored) {
        }
        int index = url.indexOf("/topic/");
        if (index < 0) return "";
        String result = url.substring(index + 7);
        int end = result.indexOf('?');
        if (end >= 0) result = result.substring(0, end);
        end = result.indexOf('#');
        if (end >= 0) result = result.substring(0, end);
        end = result.indexOf('/');
        if (end >= 0) result = result.substring(0, end);
        return result.trim();
    }

    public void getTopic(@NonNull String topicId, @NonNull ResultCallback<Topic> callback) {
        forumService().topic(authHeader(), topicId).enqueue(new EnvelopeCallback<>(callback));
    }

    public void getComments(@NonNull String topicId, @Nullable String cursor,
                            @NonNull ResultCallback<Page<Comment>> callback) {
        getComments(topicId, cursor, "desc", callback);
    }

    public void getComments(@NonNull String topicId, @Nullable String cursor,
                            @Nullable String sort,
                            @NonNull ResultCallback<Page<Comment>> callback) {
        forumService().comments(authHeader(), "topic", topicId,
                        TextUtils.isEmpty(cursor) ? "" : cursor,
                        TextUtils.isEmpty(sort) ? "desc" : sort)
                .enqueue(new EnvelopeCallback<>(callback));
    }

    public void getReplies(long commentId, @Nullable String cursor,
                           @NonNull ResultCallback<Page<Comment>> callback) {
        forumService().replies(authHeader(), commentId,
                        TextUtils.isEmpty(cursor) ? "" : cursor)
                .enqueue(new EnvelopeCallback<>(callback));
    }

    public void createComment(@NonNull String entityType, @NonNull String entityId,
                              @NonNull String content, long quoteId,
                              @NonNull List<ImageInfo> images,
                              @NonNull ResultCallback<Comment> callback) {
        CreateCommentRequest request = new CreateCommentRequest();
        request.entityType = entityType;
        request.entityId = entityId;
        request.content = content;
        request.quoteId = quoteId;
        request.imageList = imageListJson(images);
        forumService().createComment(requireAuthHeader(), request)
                .enqueue(new EnvelopeCallback<>(callback));
    }

    public void createTopic(long categoryId, @NonNull String title,
                            @NonNull String content, @NonNull List<String> tags,
                            @NonNull List<ImageInfo> images,
                            @NonNull ResultCallback<Topic> callback) {
        CreateTopicRequest request = new CreateTopicRequest();
        request.type = 0;
        request.categoryId = categoryId;
        request.title = title;
        request.content = content;
        request.contentType = "markdown";
        request.tags = tags;
        request.imageList = images;
        forumService().createTopic(requireAuthHeader(), request)
                .enqueue(new EnvelopeCallback<>(callback));
    }

    public void setTopicLiked(@NonNull String topicId, boolean liked,
                              @NonNull ResultCallback<Void> callback) {
        EntityActionRequest request = new EntityActionRequest("topic", topicId);
        Call<ApiEnvelope<Void>> call = liked
                ? forumService().like(requireAuthHeader(), request)
                : forumService().unlike(requireAuthHeader(), request);
        call.enqueue(new EnvelopeCallback<>(callback));
    }

    public void setCommentLiked(long commentId, boolean liked,
                                @NonNull ResultCallback<Void> callback) {
        EntityActionRequest request = new EntityActionRequest("comment", String.valueOf(commentId));
        Call<ApiEnvelope<Void>> call = liked
                ? forumService().like(requireAuthHeader(), request)
                : forumService().unlike(requireAuthHeader(), request);
        call.enqueue(new EnvelopeCallback<>(callback));
    }

    public void setTopicFavorited(@NonNull String topicId, boolean favorited,
                                  @NonNull ResultCallback<Void> callback) {
        EntityActionRequest request = new EntityActionRequest("topic", topicId);
        Call<ApiEnvelope<Void>> call = favorited
                ? forumService().favorite(requireAuthHeader(), request)
                : forumService().unfavorite(requireAuthHeader(), request);
        call.enqueue(new EnvelopeCallback<>(callback));
    }

    public void reportTopic(@NonNull String topicId, @NonNull String reason,
                            @NonNull ResultCallback<Void> callback) {
        ReportRequest request = new ReportRequest();
        request.dataId = topicId;
        request.dataType = "topic";
        request.reason = reason;
        forumService().report(requireAuthHeader(), request)
                .enqueue(new EnvelopeCallback<>(callback));
    }

    public void reportComment(long commentId, @NonNull String reason,
                              @NonNull ResultCallback<Void> callback) {
        ReportRequest request = new ReportRequest();
        request.dataId = String.valueOf(commentId);
        request.dataType = "comment";
        request.reason = reason;
        forumService().report(requireAuthHeader(), request)
                .enqueue(new EnvelopeCallback<>(callback));
    }

    public void deleteTopic(@NonNull String topicId, @NonNull ResultCallback<Void> callback) {
        forumService().deleteTopic(requireAuthHeader(), topicId)
                .enqueue(new EnvelopeCallback<>(callback));
    }

    public void setTopicRecommended(@NonNull String topicId, boolean recommended,
                                    @NonNull ResultCallback<Void> callback) {
        forumService().recommendTopic(requireAuthHeader(), topicId, recommended)
                .enqueue(new EnvelopeCallback<>(callback));
    }

    public void setTopicSticky(@NonNull String topicId, boolean sticky,
                               @NonNull ResultCallback<Void> callback) {
        forumService().stickyTopic(requireAuthHeader(), topicId, sticky)
                .enqueue(new EnvelopeCallback<>(callback));
    }

    public void deleteComment(long commentId, @NonNull ResultCallback<Void> callback) {
        forumService().deleteComment(requireAuthHeader(), commentId)
                .enqueue(new EnvelopeCallback<>(callback));
    }

    public void forbidUser(@NonNull String userId, int days, @NonNull String reason,
                           @NonNull ResultCallback<Void> callback) {
        UserForbiddenRequest request = new UserForbiddenRequest();
        request.userId = userId;
        request.days = days;
        request.reason = reason;
        forumService().forbidUser(requireAuthHeader(), request)
                .enqueue(new EnvelopeCallback<>(callback));
    }

    public void uploadImage(@NonNull File file, @NonNull ResultCallback<UploadResult> callback) {
        MediaType mediaType = MediaType.Companion.parse("image/webp");
        RequestBody body = RequestBody.Companion.create(file, mediaType);
        MultipartBody.Part part = MultipartBody.Part.createFormData("image", file.getName(), body);
        forumService().upload(requireAuthHeader(), part)
                .enqueue(new EnvelopeCallback<>(callback));
    }

    public void getVoiceUploadTarget(@NonNull File file,
                                     @NonNull ResultCallback<VoiceUploadTarget> callback) {
        if (!file.exists() || file.length() <= 0) {
            callback.onError("录音文件不存在");
            return;
        }
        String uid = currentUid();
        if (TextUtils.isEmpty(uid)) {
            callback.onError("请先登录");
            return;
        }
        String name = file.getName();
        String ext = "amr";
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) ext = name.substring(dot + 1);
        String path = "/forum/" + uid + "/voice_" + System.currentTimeMillis() + "." + ext;
        String requestUrl = WKApiConfig.baseUrl + "file/upload?type=common&path="
                + Uri.encode(path, "/");
        TangSengUploadService service;
        try {
            service = RetrofitUtils.getInstance().createService(TangSengUploadService.class);
        } catch (Throwable error) {
            callback.onError(readableError(error, "上传服务尚未初始化"));
            return;
        }
        service.getUploadFileUrl(requestUrl).enqueue(new Callback<UploadFileUrl>() {
            @Override
            public void onResponse(@NonNull Call<UploadFileUrl> call,
                                   @NonNull Response<UploadFileUrl> response) {
                UploadFileUrl data = response.body();
                if (!response.isSuccessful() || data == null || TextUtils.isEmpty(data.url)) {
                    callback.onError(extractHttpError(response, "无法获取语音上传地址"));
                    return;
                }
                VoiceUploadTarget target = new VoiceUploadTarget();
                target.uploadUrl = data.url;
                target.path = path;
                target.publicUrl = data.public_url;
                callback.onSuccess(target);
            }

            @Override
            public void onFailure(@NonNull Call<UploadFileUrl> call,
                                  @NonNull Throwable throwable) {
                callback.onError(readableError(throwable, "无法连接上传服务"));
            }
        });
    }

    private void requestTangSengToken(Context context, String uid) {
        TangSengService service;
        try {
            service = RetrofitUtils.getInstance().createService(TangSengService.class);
        } catch (Throwable error) {
            finishAuth(uid, null, readableError(error, "唐僧接口尚未初始化"));
            return;
        }
        service.issueForumToken().enqueue(new Callback<TangSengTokenResponse>() {
            @Override
            public void onResponse(@NonNull Call<TangSengTokenResponse> call,
                                   @NonNull Response<TangSengTokenResponse> response) {
                TangSengTokenResponse body = response.body();
                if (!response.isSuccessful() || body == null || TextUtils.isEmpty(body.token)) {
                    finishAuth(uid, null, extractHttpError(response, "无法获取论坛登录凭证"));
                    return;
                }
                exchangeToken(uid, body.token);
            }

            @Override
            public void onFailure(@NonNull Call<TangSengTokenResponse> call,
                                  @NonNull Throwable throwable) {
                finishAuth(uid, null, readableError(throwable, "无法连接唐僧服务器"));
            }
        });
    }

    private void exchangeToken(String uid, String shortToken) {
        forumService().exchange(new ExchangeRequest(shortToken))
                .enqueue(new Callback<ApiEnvelope<ExchangeData>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiEnvelope<ExchangeData>> call,
                                           @NonNull Response<ApiEnvelope<ExchangeData>> response) {
                        ApiEnvelope<ExchangeData> envelope = response.body();
                        String error = envelopeError(response, envelope, "论坛登录失败", null);
                        if (error != null) {
                            finishAuth(uid, null, error);
                            return;
                        }
                        ExchangeData data = envelope == null ? null : envelope.data;
                        if (data == null || TextUtils.isEmpty(data.token) || data.expiresAt <= 0) {
                            finishAuth(uid, null, "论坛登录返回数据不完整");
                            return;
                        }
                        long expiresAtMs = data.expiresAt > 10_000_000_000L
                                ? data.expiresAt : data.expiresAt * 1000L;
                        if (!TextUtils.equals(uid, currentUid())) {
                            finishAuth(uid, null, "账号已切换，请重试");
                            return;
                        }
                        WKSharedPreferencesUtil prefs = WKSharedPreferencesUtil.getInstance();
                        prefs.putSP(uidKey(uid, PREF_TOKEN), data.token);
                        prefs.putLong(uidKey(uid, PREF_EXPIRES_AT), expiresAtMs);
                        prefs.putSP(uidKey(uid, PREF_USER_ID), data.user == null ? "" : safe(data.user.id));
                        prefs.putSP(uidKey(uid, PREF_ROLES), data.user == null
                                ? "" : joinValues(data.user.roles));
                        prefs.putSP(uidKey(uid, PREF_PERMISSIONS), data.user == null
                                ? "" : joinValues(data.user.permissions));
                        prefs.putSP(uidKey(uid, PREF_AUTH_META_VERSION), AUTH_META_VERSION);
                        finishAuth(uid, data.token, null);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiEnvelope<ExchangeData>> call,
                                          @NonNull Throwable throwable) {
                        finishAuth(uid, null, readableError(throwable, "无法连接论坛服务器"));
                    }
                });
    }

    private void finishAuth(@NonNull String uid, @Nullable String token,
                            @Nullable String errorMessage) {
        List<ResultCallback<String>> callbacks;
        synchronized (authLock) {
            if (!authInFlight || !TextUtils.equals(authUid, uid)) return;
            callbacks = new ArrayList<>(pendingAuthCallbacks);
            pendingAuthCallbacks.clear();
            authInFlight = false;
            authUid = "";
        }
        for (ResultCallback<String> callback : callbacks) {
            if (!TextUtils.isEmpty(token)) {
                callback.onSuccess(token);
            } else {
                callback.onError(TextUtils.isEmpty(errorMessage)
                        ? "论坛登录失败" : errorMessage);
            }
        }
    }

    @Nullable
    private String authHeader() {
        String token = getSessionToken();
        return TextUtils.isEmpty(token) ? null : token;
    }

    @NonNull
    private String requireAuthHeader() {
        String token = getSessionToken();
        return TextUtils.isEmpty(token) ? "" : token;
    }

    private String getSessionToken() {
        return getSessionToken(currentUid());
    }

    private String getSessionToken(@Nullable String uid) {
        return TextUtils.isEmpty(uid) ? "" : WKSharedPreferencesUtil.getInstance()
                .getSP(uidKey(uid, PREF_TOKEN));
    }

    private long getSessionExpiresAt(@Nullable String uid) {
        return TextUtils.isEmpty(uid) ? 0L : WKSharedPreferencesUtil.getInstance()
                .getLong(uidKey(uid, PREF_EXPIRES_AT));
    }

    @NonNull
    private static String currentUid() {
        String uid = WKConfig.getInstance().getUid();
        return uid == null ? "" : uid;
    }

    @NonNull
    private static String uidKey(@NonNull String uid, @NonNull String key) {
        return uid + "_" + key;
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null ? "" : value;
    }

    @NonNull
    private static String joinValues(@Nullable List<String> values) {
        if (values == null || values.isEmpty()) return "";
        List<String> clean = new ArrayList<>();
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) clean.add(value.trim());
        }
        return TextUtils.join(",", clean);
    }

    private ForumService forumService() {
        String baseUrl = WKApiConfig.getForumBaseUrl();
        Retrofit current = forumRetrofit;
        if (current == null || !TextUtils.equals(baseUrl, forumRetrofitBaseUrl)) {
            synchronized (this) {
                current = forumRetrofit;
                if (current == null || !TextUtils.equals(baseUrl, forumRetrofitBaseUrl)) {
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(35, TimeUnit.SECONDS)
                            .writeTimeout(35, TimeUnit.SECONDS)
                            .callTimeout(60, TimeUnit.SECONDS)
                            .build();
                    current = new Retrofit.Builder()
                            .baseUrl(baseUrl)
                            .client(client)
                            .addConverterFactory(FastJsonConverterFactory.Companion.create())
                            .build();
                    forumRetrofit = current;
                    forumRetrofitBaseUrl = baseUrl;
                }
            }
        }
        return current.create(ForumService.class);
    }

    @Nullable
    private static <T> String envelopeError(Response<ApiEnvelope<T>> response,
                                            @Nullable ApiEnvelope<T> envelope,
                                            String fallback,
                                            @Nullable String requestToken) {
        if (!response.isSuccessful()) {
            if (response.code() == 401) INSTANCE.invalidateSessionIfMatches(requestToken);
            return "论坛服务器返回 " + response.code();
        }
        if (envelope == null) return fallback;
        boolean failed = Boolean.FALSE.equals(envelope.success)
                || (envelope.success == null && envelope.errorCode != null
                && envelope.errorCode != 0);
        if (failed) {
            if (envelope.errorCode != null && envelope.errorCode == 1) {
                INSTANCE.invalidateSessionIfMatches(requestToken);
            }
            return TextUtils.isEmpty(envelope.message) ? fallback : envelope.message;
        }
        return null;
    }

    private void invalidateSessionIfMatches(@Nullable String requestToken) {
        if (TextUtils.isEmpty(requestToken)) return;
        String uid = currentUid();
        if (!TextUtils.isEmpty(uid) && TextUtils.equals(requestToken, getSessionToken(uid))) {
            invalidateSession(uid);
        }
    }

    private static String extractHttpError(Response<?> response, String fallback) {
        if (response.isSuccessful()) return fallback;
        return fallback + "（" + response.code() + "）";
    }

    private static String readableError(Throwable throwable, String fallback) {
        String message = throwable == null ? "" : throwable.getMessage();
        return TextUtils.isEmpty(message) ? fallback : fallback + "：" + message;
    }

    private static String imageListJson(List<ImageInfo> images) {
        if (images == null || images.isEmpty()) return "[]";
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (ImageInfo image : images) {
            if (image == null || TextUtils.isEmpty(image.url)) continue;
            if (!first) builder.append(',');
            first = false;
            builder.append("{\"url\":\"")
                    .append(escapeJson(image.url))
                    .append("\"}");
        }
        return builder.append(']').toString();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class EnvelopeCallback<T> implements Callback<ApiEnvelope<T>> {
        private final ResultCallback<T> callback;
        private final String requestToken;

        private EnvelopeCallback(ResultCallback<T> callback) {
            this.callback = callback;
            this.requestToken = INSTANCE.getSessionToken();
        }

        @Override
        public void onResponse(@NonNull Call<ApiEnvelope<T>> call,
                               @NonNull Response<ApiEnvelope<T>> response) {
            ApiEnvelope<T> envelope = response.body();
            String error = envelopeError(response, envelope, "论坛返回数据异常", requestToken);
            if (error != null) {
                callback.onError(error);
                return;
            }
            callback.onSuccess(envelope == null ? null : envelope.data);
        }

        @Override
        public void onFailure(@NonNull Call<ApiEnvelope<T>> call,
                              @NonNull Throwable throwable) {
            callback.onError(readableError(throwable, "论坛网络请求失败"));
        }
    }

    private interface TangSengService {
        @POST("forum/token")
        Call<TangSengTokenResponse> issueForumToken();
    }

    private interface TangSengUploadService {
        @GET
        Call<UploadFileUrl> getUploadFileUrl(@Url String url);
    }

    private interface ForumService {
        @POST("api/talkami/exchange")
        Call<ApiEnvelope<ExchangeData>> exchange(@Body ExchangeRequest request);

        @GET("api/topic/category_navs")
        Call<ApiEnvelope<List<Category>>> categories(@Header("X-User-Token") String token);

        @GET("api/topic/topics")
        Call<ApiEnvelope<Page<Topic>>> topics(@Header("X-User-Token") String token,
                                             @Query("categoryId") long categoryId,
                                             @Query("cursor") String cursor,
                                             @Query("sort") String sort);

        @GET("api/topic/user_topics")
        Call<ApiEnvelope<Page<Topic>>> userTopics(@Header("X-User-Token") String token,
                                                  @Query("userId") String userId,
                                                  @Query("cursor") String cursor);

        @GET("api/user/favorites")
        Call<ApiEnvelope<Page<Favorite>>> favorites(@Header("X-User-Token") String token,
                                                     @Query("cursor") String cursor);

        @GET("api/user/msg_recent")
        Call<ApiEnvelope<RecentMessages>> recentMessages(@Header("X-User-Token") String token);

        @GET("api/user/messages")
        Call<ApiEnvelope<Page<Message>>> messages(@Header("X-User-Token") String token,
                                                   @Query("cursor") String cursor);

        @GET("api/fans/is_followed")
        Call<ApiEnvelope<Boolean>> isFollowed(@Header("X-User-Token") String token,
                                              @Query("userId") String userId);

        @FormUrlEncoded
        @POST("api/fans/follow")
        Call<ApiEnvelope<Void>> follow(@Header("X-User-Token") String token,
                                       @Field("userId") String userId);

        @FormUrlEncoded
        @POST("api/fans/unfollow")
        Call<ApiEnvelope<Void>> unfollow(@Header("X-User-Token") String token,
                                         @Field("userId") String userId);

        @GET("api/topic/{id}")
        Call<ApiEnvelope<Topic>> topic(@Header("X-User-Token") String token,
                                      @Path("id") String topicId);

        @GET("api/comment/comments")
        Call<ApiEnvelope<Page<Comment>>> comments(@Header("X-User-Token") String token,
                                                  @Query("entityType") String entityType,
                                                  @Query("entityId") String entityId,
                                                  @Query("cursor") String cursor,
                                                  @Query("sort") String sort);

        @GET("api/comment/replies")
        Call<ApiEnvelope<Page<Comment>>> replies(@Header("X-User-Token") String token,
                                                 @Query("commentId") long commentId,
                                                 @Query("cursor") String cursor);

        @POST("api/comment/create")
        Call<ApiEnvelope<Comment>> createComment(@Header("X-User-Token") String token,
                                                 @Body CreateCommentRequest request);

        @POST("api/topic/create")
        Call<ApiEnvelope<Topic>> createTopic(@Header("X-User-Token") String token,
                                             @Body CreateTopicRequest request);

        @POST("api/like/like")
        Call<ApiEnvelope<Void>> like(@Header("X-User-Token") String token,
                                     @Body EntityActionRequest request);

        @POST("api/like/unlike")
        Call<ApiEnvelope<Void>> unlike(@Header("X-User-Token") String token,
                                       @Body EntityActionRequest request);

        @POST("api/favorite/add")
        Call<ApiEnvelope<Void>> favorite(@Header("X-User-Token") String token,
                                         @Body EntityActionRequest request);

        @POST("api/favorite/delete")
        Call<ApiEnvelope<Void>> unfavorite(@Header("X-User-Token") String token,
                                           @Body EntityActionRequest request);

        @POST("api/user-report/submit")
        Call<ApiEnvelope<Void>> report(@Header("X-User-Token") String token,
                                       @Body ReportRequest request);

        @POST("api/topic/delete/{id}")
        Call<ApiEnvelope<Void>> deleteTopic(@Header("X-User-Token") String token,
                                            @Path("id") String topicId);

        @FormUrlEncoded
        @POST("api/topic/recommend/{id}")
        Call<ApiEnvelope<Void>> recommendTopic(@Header("X-User-Token") String token,
                                               @Path("id") String topicId,
                                               @Field("recommend") boolean recommend);

        @FormUrlEncoded
        @POST("api/topic/sticky/{id}")
        Call<ApiEnvelope<Void>> stickyTopic(@Header("X-User-Token") String token,
                                            @Path("id") String topicId,
                                            @Field("sticky") boolean sticky);

        @POST("api/comment/delete/{id}")
        Call<ApiEnvelope<Void>> deleteComment(@Header("X-User-Token") String token,
                                              @Path("id") long commentId);

        @POST("api/user/forbidden")
        Call<ApiEnvelope<Void>> forbidUser(@Header("X-User-Token") String token,
                                           @Body UserForbiddenRequest request);

        @Multipart
        @POST("api/upload")
        Call<ApiEnvelope<UploadResult>> upload(@Header("X-User-Token") String token,
                                               @Part MultipartBody.Part image);
    }

    private static final class TangSengTokenResponse {
        public String token;
    }

    private static final class ExchangeRequest {
        public final String token;

        private ExchangeRequest(String token) {
            this.token = token;
        }
    }

    private static final class ExchangeData {
        public String token;
        public long expiresAt;
        public User user;
    }

    private static final class EntityActionRequest {
        public final String entityType;
        public final String entityId;

        private EntityActionRequest(String entityType, String entityId) {
            this.entityType = entityType;
            this.entityId = entityId;
        }
    }

    private static final class ReportRequest {
        public String dataId;
        public String dataType;
        public String reason;
    }

    private static final class UserForbiddenRequest {
        public String userId;
        public int days;
        public String reason;
    }

    private static final class CreateCommentRequest {
        public String entityType;
        public String entityId;
        public String content;
        public String imageList;
        public long quoteId;
    }

    private static final class CreateTopicRequest {
        public int type;
        public long categoryId;
        public String title;
        public String content;
        public String contentType;
        public List<String> tags;
        public List<ImageInfo> imageList;
    }

    private static final class ApiEnvelope<T> {
        public Boolean success;
        public T data;
        public String message;
        public Integer errorCode;
    }

    public static final class Page<T> {
        public List<T> results;
        public String cursor;
        public boolean hasMore;
    }

    public static final class Category {
        public long id;
        public long parentId;
        public String name;
        public String description;
        public String logo;
        public boolean adminOnlyPost;
        public boolean canPost;
        public List<Category> children;
    }

    public static final class Topic {
        public String id;
        public int type;
        public String title;
        public String summary;
        public String content;
        public long createTime;
        public long lastCommentTime;
        public long viewCount;
        public long commentCount;
        public long likeCount;
        public boolean sticky;
        public boolean recommend;
        public boolean liked;
        public boolean favorited;
        public User user;
        public Category category;
        public List<ImageInfo> imageList;
    }

    public static final class Favorite {
        public long id;
        public String entityType;
        public long entityId;
        public boolean deleted;
        public String title;
        public String content;
        public User user;
        public String url;
        public long createTime;
    }

    public static final class Message {
        public long id;
        public User from;
        public String title;
        public String content;
        public String quoteContent;
        public int type;
        public String detailUrl;
        public String extraData;
        public int status;
        public long createTime;
    }

    public static final class RecentMessages {
        public long count;
        public List<Message> messages;
    }

    public static final class Comment {
        public long id;
        public User user;
        public String entityType;
        public long entityId;
        public String contentType;
        public String content;
        public List<ImageInfo> imageList;
        public long likeCount;
        public long commentCount;
        public boolean liked;
        public long quoteId;
        public Comment quote;
        public Page<Comment> replies;
        public String ipLocation;
        public int status;
        public long createTime;
    }

    public static final class User {
        /** Talkami uid; BBS-GO id remains in id for forum APIs. */
        public String uid;
        public String id;
        public String nickname;
        public String avatar;
        public String smallAvatar;
        public String countryCode;
        public String country;
        public List<String> roles;
        public List<String> permissions;
    }

    public static final class ImageInfo {
        public String url;
        public String preview;

        public ImageInfo() {
        }

        public ImageInfo(String url) {
            this.url = url;
        }
    }

    public static final class VoiceUploadTarget {
        public String uploadUrl;
        public String path;
        public String publicUrl;
    }

    public static final class UploadResult {
        public String url;
        public String contentType;
        public long size;
        public int width;
        public int height;
    }
}
