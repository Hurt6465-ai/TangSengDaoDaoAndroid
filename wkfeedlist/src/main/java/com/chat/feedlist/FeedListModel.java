package com.chat.feedlist;

import android.text.TextUtils;

import com.chat.base.base.WKBaseModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.net.ApiService;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.net.entity.UploadFileUrl;
import com.chat.feedlist.model.FeedListInteractionResponse;
import com.chat.feedlist.model.FeedListResponse;
import com.chat.feedlist.model.FeedListTikTokPreview;
import com.chat.feedlist.net.FeedListService;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FeedListModel extends WKBaseModel {
    private static final FeedListModel INSTANCE = new FeedListModel();
    private FeedListModel() {}
    public static FeedListModel getInstance() { return INSTANCE; }

    public void timeline(String cursor, int limit, IRequestResultListener<FeedListResponse> listener) {
        request(createService(FeedListService.class).timeline(cursor, Math.max(1, limit)), listener);
    }

    public void following(String cursor, int limit, IRequestResultListener<FeedListResponse> listener) {
        request(createService(FeedListService.class).following(cursor, Math.max(1, limit)), listener);
    }

    public void tiktokPreview(String url, IRequestResultListener<FeedListTikTokPreview> listener) {
        if (TextUtils.isEmpty(url)) {
            if (listener != null) listener.onFail(400, "请输入 TikTok 链接");
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("url", url.trim());
        request(createService(FeedListService.class).tiktokPreview(body), listener);
    }

    public void like(String feedId, boolean desired, IRequestResultListener<FeedListInteractionResponse> listener) {
        if (TextUtils.isEmpty(feedId)) {
            if (listener != null) listener.onFail(400, "动态不存在");
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("like", desired ? 1 : 0);
        request(createService(FeedListService.class).like(feedId, body), listener);
    }

    public void setFollow(String uid, boolean follow, IRequestResultListener<CommonResponse> listener) {
        if (TextUtils.isEmpty(uid)) {
            if (listener != null) listener.onFail(400, "用户不存在");
            return;
        }
        if (follow) {
            Map<String, Object> body = new HashMap<>();
            body.put("uid", uid);
            request(createService(FeedListService.class).follow(body), listener);
        } else {
            request(createService(FeedListService.class).unfollow(uid), listener);
        }
    }

    public void share(String feedId, IRequestResultListener<FeedListInteractionResponse> listener) {
        if (TextUtils.isEmpty(feedId)) {
            if (listener != null) listener.onFail(400, "动态不存在");
            return;
        }
        request(createService(FeedListService.class).share(feedId, new HashMap<>()), listener);
    }

    public void report(String feedId, String reason, IRequestResultListener<CommonResponse> listener) {
        if (TextUtils.isEmpty(feedId)) {
            if (listener != null) listener.onFail(400, "动态不存在");
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("reason", TextUtils.isEmpty(reason) ? "other" : reason);
        request(createService(FeedListService.class).report(feedId, body), listener);
    }

    public void publish(String text, List<Map<String, Object>> media, IRequestResultListener<CommonResponse> listener) {
        if (media == null || media.isEmpty()) {
            if (listener != null) listener.onFail(400, "请选择图片或 TikTok 视频");
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("text", text == null ? "" : text.trim());
        body.put("media", media);
        request(createService(FeedListService.class).publish(body), listener);
    }

    public void delete(String feedId, IRequestResultListener<CommonResponse> listener) {
        if (TextUtils.isEmpty(feedId)) {
            if (listener != null) listener.onFail(400, "动态不存在");
            return;
        }
        request(createService(FeedListService.class).delete(feedId), listener);
    }

    public void getUploadUrl(String localPath, IRequestResultListener<FeedUploadUrl> listener) {
        String uid = WKConfig.getInstance().getUid();
        if (TextUtils.isEmpty(uid)) {
            if (listener != null) listener.onFail(401, "未登录");
            return;
        }
        String ext = fileExt(localPath);
        String path = "/feed/" + uid + "/image_" + System.currentTimeMillis() + "_"
                + Math.abs(localPath == null ? 0 : localPath.hashCode()) + "." + ext;
        String url = WKApiConfig.baseUrl + "file/upload?type=common&path=" + path;
        request(createService(ApiService.class).getUploadFileUrl(url), new IRequestResultListener<UploadFileUrl>() {
            @Override public void onSuccess(UploadFileUrl result) {
                FeedUploadUrl out = new FeedUploadUrl();
                out.url = result == null ? "" : result.url;
                out.path = path;
                if (listener != null) listener.onSuccess(out);
            }
            @Override public void onFail(int code, String msg) {
                if (listener != null) listener.onFail(code, msg);
            }
        });
    }

    private static String fileExt(String localPath) {
        if (TextUtils.isEmpty(localPath)) return "webp";
        String name = new File(localPath).getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String ext = name.substring(dot + 1).toLowerCase(Locale.US);
            return "jpeg".equals(ext) ? "jpg" : ext;
        }
        return "webp";
    }

    public static final class FeedUploadUrl {
        public String url;
        public String path;
    }
}
