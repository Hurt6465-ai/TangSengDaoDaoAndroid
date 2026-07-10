package com.chat.dating;

import com.chat.base.base.WKBaseModel;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.IRequestResultListener;
import com.chat.dating.model.DatingProfile;
import com.chat.dating.model.DatingRecommendResponse;
import com.chat.dating.model.DatingSwipeResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatingModel extends WKBaseModel {
    private DatingModel() {}

    private static class Holder {
        private static final DatingModel INSTANCE = new DatingModel();
    }

    public static DatingModel getInstance() {
        return Holder.INSTANCE;
    }

    public void getMyDatingProfile(final Callback<DatingProfile> callback) {
        request(createService(DatingService.class).getMyDatingProfile(), new IRequestResultListener<>() {
            @Override
            public void onSuccess(DatingProfile result) {
                if (callback != null) callback.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (callback != null) callback.onResult(code, msg, null);
            }
        });
    }

    public void recommend(String cursor, int limit, String scope, String sessionId, DatingFilter filter, final Callback<DatingRecommendResponse> callback) {
        DatingFilter f = filter == null ? new DatingFilter() : filter;
        request(createService(DatingService.class).recommend(cursor == null ? "" : cursor,
                limit,
                scope == null ? "global" : scope,
                sessionId == null ? "" : sessionId,
                f.countryMode,
                f.gender,
                f.ageMin,
                f.ageMax,
                f.goal), new IRequestResultListener<>() {
            @Override
            public void onSuccess(DatingRecommendResponse result) {
                if (callback != null) callback.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (callback != null) callback.onResult(code, msg, null);
            }
        });
    }

    public void saveProfile(Map<String, Object> body, final Callback<DatingProfile> callback) {
        request(createService(DatingService.class).saveProfile(body == null ? new HashMap<>() : body), new IRequestResultListener<>() {
            @Override
            public void onSuccess(DatingProfile result) {
                if (callback != null) callback.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (callback != null) callback.onResult(code, msg, null);
            }
        });
    }

    public void copyPartnerProfile(final Callback<DatingProfile> callback) {
        request(createService(DatingService.class).copyPartnerProfile(new HashMap<>()), new IRequestResultListener<>() {
            @Override
            public void onSuccess(DatingProfile result) {
                if (callback != null) callback.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (callback != null) callback.onResult(code, msg, null);
            }
        });
    }

    public void enableProfile(boolean enabled, final Callback<Object> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("enabled", enabled ? 1 : 0);
        request(createService(DatingService.class).enableProfile(body), new IRequestResultListener<>() {
            @Override
            public void onSuccess(Object result) {
                if (callback != null) callback.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (callback != null) callback.onResult(code, msg, null);
            }
        });
    }

    public void updateLocation(double lat, double lng, String city, String countryCode, final Callback<Object> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("lat", lat);
        body.put("lng", lng);
        body.put("city", city == null ? "" : city);
        body.put("country_code", countryCode == null ? "" : countryCode);
        request(createService(DatingService.class).updateLocation(body), new IRequestResultListener<>() {
            @Override
            public void onSuccess(Object result) {
                if (callback != null) callback.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (callback != null) callback.onResult(code, msg, null);
            }
        });
    }

    public void swipe(String targetUid, String action, int photoIndex, final Callback<DatingSwipeResult> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("to_uid", targetUid == null ? "" : targetUid);
        body.put("target_uid", targetUid == null ? "" : targetUid);
        body.put("action", action == null ? DatingSwipeAction.PASS : action);
        body.put("photo_index", Math.max(0, photoIndex));
        body.put("source", "wkdating");
        request(createService(DatingService.class).swipe(body), new IRequestResultListener<>() {
            @Override
            public void onSuccess(DatingSwipeResult result) {
                if (callback != null) callback.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (callback != null) callback.onResult(code, msg, null);
            }
        });
    }

    public void reportExposures(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return;
        Map<String, Object> body = new HashMap<>();
        body.put("items", items);
        request(createService(DatingService.class).reportExposures(body), new IRequestResultListener<>() {
            @Override
            public void onSuccess(Object result) {}

            @Override
            public void onFail(int code, String msg) {}
        });
    }


    public void block(String targetUid, final Callback<Object> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("to_uid", targetUid == null ? "" : targetUid);
        body.put("target_uid", targetUid == null ? "" : targetUid);
        request(createService(DatingService.class).block(body), new IRequestResultListener<>() {
            @Override
            public void onSuccess(Object result) {
                if (callback != null) callback.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (callback != null) callback.onResult(code, msg, null);
            }
        });
    }

    public void report(String targetUid, String reason, final Callback<Object> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("to_uid", targetUid == null ? "" : targetUid);
        body.put("target_uid", targetUid == null ? "" : targetUid);
        body.put("reason", reason == null ? "" : reason);
        request(createService(DatingService.class).report(body), new IRequestResultListener<>() {
            @Override
            public void onSuccess(Object result) {
                if (callback != null) callback.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (callback != null) callback.onResult(code, msg, null);
            }
        });
    }

    public interface Callback<T> {
        void onResult(int code, String msg, T data);
    }
}
