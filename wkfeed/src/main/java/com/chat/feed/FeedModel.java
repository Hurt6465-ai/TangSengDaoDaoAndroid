package com.chat.feed;

import com.chat.base.base.WKBaseModel;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.feed.config.FeedConfig;
import com.chat.feed.mock.FeedMockData;
import com.chat.feed.model.CommentListResponse;
import com.chat.feed.model.FeedListResponse;

import java.util.HashMap;
import java.util.Map;

public class FeedModel extends WKBaseModel {
    public static final int PAGE_SIZE = FeedConfig.PAGE_SIZE;
    public static final String MODE_DISCOVER = "discover";
    public static final String MODE_NEARBY = "nearby";
    public static final String MODE_PROFILE = "profile";

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
        if (FeedConfig.DEBUG_MOCK || FeedConfig.FALLBACK_MOCK_ON_ERROR) {
            if (listener != null) listener.onSuccess(null);
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("like", like ? 1 : 0);
        request(createService(FeedService.class).like(feedId, body), listener);
    }

    public void sendComment(String feedId, String content, IRequestResultListener<CommonResponse> listener) {
        if (FeedConfig.DEBUG_MOCK || FeedConfig.FALLBACK_MOCK_ON_ERROR) {
            if (listener != null) listener.onSuccess(null);
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        request(createService(FeedService.class).sendComment(feedId, body), listener);
    }
}
