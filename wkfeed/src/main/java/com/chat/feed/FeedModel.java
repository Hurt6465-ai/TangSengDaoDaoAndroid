package com.chat.feed;

import android.text.TextUtils;

import com.chat.base.base.WKBaseModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.net.ApiService;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.net.entity.UploadFileUrl;
import com.chat.feed.config.FeedConfig;
import com.chat.feed.mock.FeedMockData;
import com.chat.feed.model.CommentListResponse;
import com.chat.feed.model.FeedListResponse;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FeedModel extends WKBaseModel {
    public static final int PAGE_SIZE = FeedConfig.PAGE_SIZE;
    public static final String MODE_DISCOVER = "discover";
    public static final String MODE_NEARBY = "nearby";
    public static final String MODE_PROFILE = "profile";
    public static final String MODE_FOLLOWING = "following";

    private FeedModel() {}

    private static class Holder {
        private static final FeedModel INSTANCE = new FeedModel();
    }

    public static FeedModel getInstance() {
        return Holder.INSTANCE;
    }

    public void recommend(String mode, String cursor, String uid, IRequestResultListener<FeedListResponse> listener) {
        if (FeedConfig.DEBUG_MOCK) {
            if (listener != null) listener.onSuccess(FeedMockData.feeds(mode, cursor, uid, PAGE_SIZE));
            return;
        }
        request(createService(FeedService.class).recommend(mode, cursor, PAGE_SIZE, uid), new IRequestResultListener<FeedListResponse>() {
            @Override
            public void onSuccess(FeedListResponse result) {
                if (listener != null) listener.onSuccess(result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (FeedConfig.FALLBACK_MOCK_ON_ERROR) {
                    if (listener != null) listener.onSuccess(FeedMockData.feeds(mode, cursor, uid, PAGE_SIZE));
                } else if (listener != null) {
                    listener.onFail(code, msg);
                }
            }
        });
    }

    public void following(String cursor, IRequestResultListener<FeedListResponse> listener) {
        if (FeedConfig.DEBUG_MOCK) {
            if (listener != null) listener.onSuccess(FeedMockData.feeds(MODE_DISCOVER, cursor, "", PAGE_SIZE));
            return;
        }
        request(createService(FeedService.class).following(cursor, PAGE_SIZE), new IRequestResultListener<FeedListResponse>() {
            @Override
            public void onSuccess(FeedListResponse result) {
                if (listener != null) listener.onSuccess(result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (FeedConfig.FALLBACK_MOCK_ON_ERROR) {
                    if (listener != null) listener.onSuccess(FeedMockData.feeds(MODE_DISCOVER, cursor, "", PAGE_SIZE));
                } else if (listener != null) {
                    listener.onFail(code, msg);
                }
            }
        });
    }

    public void userFeeds(String uid, String cursor, IRequestResultListener<FeedListResponse> listener) {
        if (FeedConfig.DEBUG_MOCK) {
            if (listener != null) listener.onSuccess(FeedMockData.feeds(MODE_PROFILE, cursor, uid, PAGE_SIZE));
            return;
        }
        request(createService(FeedService.class).userFeeds(uid, cursor, PAGE_SIZE), new IRequestResultListener<FeedListResponse>() {
            @Override
            public void onSuccess(FeedListResponse result) {
                if (listener != null) listener.onSuccess(result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (FeedConfig.FALLBACK_MOCK_ON_ERROR) {
                    if (listener != null) listener.onSuccess(FeedMockData.feeds(MODE_PROFILE, cursor, uid, PAGE_SIZE));
                } else if (listener != null) {
                    listener.onFail(code, msg);
                }
            }
        });
    }

    public void comments(String feedId, String cursor, IRequestResultListener<CommentListResponse> listener) {
        if (FeedConfig.DEBUG_MOCK) {
            if (listener != null) listener.onSuccess(FeedMockData.comments(feedId, cursor, 30));
            return;
        }
        request(createService(FeedService.class).comments(feedId, cursor, 30), new IRequestResultListener<CommentListResponse>() {
            @Override
            public void onSuccess(CommentListResponse result) {
                if (listener != null) listener.onSuccess(result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (FeedConfig.FALLBACK_MOCK_ON_ERROR) {
                    if (listener != null) listener.onSuccess(FeedMockData.comments(feedId, cursor, 30));
                } else if (listener != null) {
                    listener.onFail(code, msg);
                }
            }
        });
    }

    public void like(String feedId, boolean like, IRequestResultListener<CommonResponse> listener) {
        if (FeedConfig.DEBUG_MOCK) {
            if (listener != null) listener.onSuccess(null);
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("like", like ? 1 : 0);
        request(createService(FeedService.class).like(feedId, body), listener);
    }

    public void follow(String uid, IRequestResultListener<CommonResponse> listener) {
        if (TextUtils.isEmpty(uid)) {
            if (listener != null) listener.onFail(400, "用户不存在");
            return;
        }
        if (FeedConfig.DEBUG_MOCK) {
            if (listener != null) listener.onSuccess(null);
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("uid", uid);
        request(createService(FeedService.class).follow(body), listener);
    }

    public void unfollow(String uid, IRequestResultListener<CommonResponse> listener) {
        if (TextUtils.isEmpty(uid)) {
            if (listener != null) listener.onFail(400, "用户不存在");
            return;
        }
        if (FeedConfig.DEBUG_MOCK) {
            if (listener != null) listener.onSuccess(null);
            return;
        }
        request(createService(FeedService.class).unfollow(uid), listener);
    }

    public void setFollow(String uid, boolean follow, IRequestResultListener<CommonResponse> listener) {
        if (follow) follow(uid, listener);
        else unfollow(uid, listener);
    }

    public void sendComment(String feedId, String content, IRequestResultListener<CommonResponse> listener) {
        if (FeedConfig.DEBUG_MOCK) {
            if (listener != null) listener.onSuccess(null);
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        request(createService(FeedService.class).sendComment(feedId, body), listener);
    }

    public void publish(String text, List<Map<String, Object>> mediaList, IRequestResultListener<CommonResponse> listener) {
        if (mediaList == null || mediaList.isEmpty()) {
            if (listener != null) listener.onFail(400, "请选择图片或视频");
            return;
        }
        // 发布、点赞、评论属于写操作，不能因为 FALLBACK_MOCK_ON_ERROR=true 就假装成功。
        // 否则前端提示“发布成功”，但后端没有任何数据，别人和个人主页都刷不到。
        if (FeedConfig.DEBUG_MOCK) {
            if (listener != null) listener.onSuccess(null);
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("text", text == null ? "" : text.trim());
        body.put("media", mediaList);
        request(createService(FeedService.class).publish(body), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (listener != null) listener.onSuccess(result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (listener != null) listener.onFail(code, msg);
            }
        });
    }

    public void delete(String feedId, IRequestResultListener<CommonResponse> listener) {
        if (TextUtils.isEmpty(feedId)) {
            if (listener != null) listener.onFail(400, "作品不存在");
            return;
        }
        if (FeedConfig.DEBUG_MOCK) {
            if (listener != null) listener.onSuccess(null);
            return;
        }
        request(createService(FeedService.class).delete(feedId), listener);
    }

    public void getFeedUploadFileUrl(String localPath, String mediaType, IRequestResultListener<FeedUploadUrl> listener) {
        String ext = fileExt(localPath);
        String uid = WKConfig.getInstance().getUid();
        if (TextUtils.isEmpty(uid)) uid = "anonymous";
        String typeDir = TextUtils.isEmpty(mediaType) ? "file" : mediaType.toLowerCase(Locale.US);
        String path = "/feed/" + uid + "/" + typeDir + "_" + System.currentTimeMillis() + "_" + Math.abs(localPath == null ? 0 : localPath.hashCode()) + "." + ext;
        String url = WKApiConfig.baseUrl + "file/upload?type=common&path=" + path;
        request(createService(ApiService.class).getUploadFileUrl(url), new IRequestResultListener<UploadFileUrl>() {
            @Override
            public void onSuccess(UploadFileUrl result) {
                FeedUploadUrl out = new FeedUploadUrl();
                out.url = result == null ? "" : result.url;
                out.path = path;
                out.publicUrl = result == null ? "" : result.public_url;
                if (listener != null) listener.onSuccess(out);
            }

            @Override
            public void onFail(int code, String msg) {
                if (listener != null) listener.onFail(code, msg);
            }
        });
    }

    private String fileExt(String localPath) {
        if (TextUtils.isEmpty(localPath)) return "bin";
        String name = new File(localPath).getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String ext = name.substring(dot + 1).toLowerCase(Locale.US);
            if ("jpeg".equals(ext)) return "jpg";
            return ext;
        }
        return "bin";
    }

    public void share(String feedId, IRequestResultListener<CommonResponse> listener) {
        if (TextUtils.isEmpty(feedId)) {
            if (listener != null) listener.onFail(400, "作品不存在");
            return;
        }
        if (FeedConfig.DEBUG_MOCK) {
            if (listener != null) listener.onSuccess(null);
            return;
        }
        request(createService(FeedService.class).share(feedId, new HashMap<>()), listener);
    }

    public void report(String feedId, String reason, IRequestResultListener<CommonResponse> listener) {
        if (TextUtils.isEmpty(feedId)) {
            if (listener != null) listener.onFail(400, "作品不存在");
            return;
        }
        if (FeedConfig.DEBUG_MOCK) {
            if (listener != null) listener.onSuccess(null);
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("reason", TextUtils.isEmpty(reason) ? "normal" : reason);
        request(createService(FeedService.class).report(feedId, body), listener);
    }

    public void event(String feedId, String type, Map<String, Object> extra, IRequestResultListener<CommonResponse> listener) {
        if (TextUtils.isEmpty(feedId) || TextUtils.isEmpty(type)) return;
        if (FeedConfig.DEBUG_MOCK) {
            if (listener != null) listener.onSuccess(null);
            return;
        }
        Map<String, Object> body = extra == null ? new HashMap<>() : new HashMap<>(extra);
        body.put("type", type);
        request(createService(FeedService.class).event(feedId, body), listener);
    }

    public static class FeedUploadUrl {
        public String url;
        public String path;
        public String publicUrl;
    }
}
