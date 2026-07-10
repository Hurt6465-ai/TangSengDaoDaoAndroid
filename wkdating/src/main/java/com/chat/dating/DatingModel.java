package com.chat.dating;

import android.text.TextUtils;

import com.chat.base.base.WKBaseModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.net.ApiService;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.UploadFileUrl;
import com.chat.dating.model.DatingMatchesResponse;
import com.chat.dating.model.DatingProfile;
import com.chat.dating.model.DatingRecommendResponse;
import com.chat.dating.model.DatingSwipeResult;
import com.chat.dating.model.DatingUploadUrl;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
        request(createService(DatingService.class).getMyDatingProfile(), listener(callback));
    }

    public void recommend(String cursor, int limit, String scope, String sessionId, DatingFilter filter,
                          final Callback<DatingRecommendResponse> callback) {
        DatingFilter f = filter == null ? new DatingFilter() : filter;
        request(createService(DatingService.class).recommend(
                cursor == null ? "" : cursor,
                limit,
                scope == null ? "global" : scope,
                sessionId == null ? "" : sessionId,
                f.countryMode,
                f.gender,
                f.ageMin,
                f.ageMax,
                f.goal
        ), listener(callback));
    }

    public void saveProfile(Map<String, Object> body, final Callback<DatingProfile> callback) {
        request(createService(DatingService.class).saveProfile(body == null ? new HashMap<>() : body), listener(callback));
    }

    public void copyPartnerProfile(final Callback<DatingProfile> callback) {
        request(createService(DatingService.class).copyPartnerProfile(new HashMap<>()), listener(callback));
    }

    public void enableProfile(boolean enabled, final Callback<Object> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("enabled", enabled ? 1 : 0);
        request(createService(DatingService.class).enableProfile(body), listener(callback));
    }

    public void updateLocation(double lat, double lng, String city, String countryCode, final Callback<Object> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("lat", lat);
        body.put("lng", lng);
        body.put("city", city == null ? "" : city);
        body.put("country_code", countryCode == null ? "" : countryCode);
        body.put("source", "android");
        request(createService(DatingService.class).updateLocation(body), listener(callback));
    }

    public void swipe(String targetUid, String action, int photoIndex, String sessionId,
                      final Callback<DatingSwipeResult> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("to_uid", targetUid == null ? "" : targetUid);
        body.put("target_uid", targetUid == null ? "" : targetUid);
        body.put("action", action == null ? DatingSwipeAction.PASS : action);
        body.put("photo_index", Math.max(0, photoIndex));
        body.put("source", "wkdating");
        body.put("session_id", sessionId == null ? "" : sessionId);
        request(createService(DatingService.class).swipe(body), listener(callback));
    }

    public void reportExposures(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return;
        Map<String, Object> body = new HashMap<>();
        body.put("items", items);
        request(createService(DatingService.class).reportExposures(body), new IRequestResultListener<>() {
            @Override public void onSuccess(Object result) {}
            @Override public void onFail(int code, String msg) {}
        });
    }

    public void matches(int limit, final Callback<DatingMatchesResponse> callback) {
        request(createService(DatingService.class).matches(Math.max(1, Math.min(100, limit))), listener(callback));
    }

    public void cancelMatch(String matchId, final Callback<Object> callback) {
        if (TextUtils.isEmpty(matchId)) {
            if (callback != null) callback.onResult(400, "匹配不存在", null);
            return;
        }
        request(createService(DatingService.class).cancelMatch(matchId), listener(callback));
    }

    public void block(String targetUid, final Callback<Object> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("to_uid", targetUid == null ? "" : targetUid);
        body.put("target_uid", targetUid == null ? "" : targetUid);
        request(createService(DatingService.class).block(body), listener(callback));
    }

    public void report(String targetUid, String reason, String description, final Callback<Object> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("to_uid", targetUid == null ? "" : targetUid);
        body.put("target_uid", targetUid == null ? "" : targetUid);
        body.put("reason", reason == null ? "" : reason);
        body.put("description", description == null ? "" : description);
        request(createService(DatingService.class).report(body), listener(callback));
    }

    /** 获取交友照片上传地址，复用唐僧现有 common file/upload。 */
    public void getUploadFileUrl(String localPath, final Callback<DatingUploadUrl> callback) {
        String ext = fileExt(localPath);
        String uid = WKConfig.getInstance().getUid();
        if (TextUtils.isEmpty(uid)) uid = "anonymous";
        String path = "/dating/" + uid + "/photo_" + System.currentTimeMillis() + "_"
                + Math.abs(localPath == null ? 0 : localPath.hashCode()) + "." + ext;
        String url = WKApiConfig.baseUrl + "file/upload?type=common&path=" + path;
        request(createService(ApiService.class).getUploadFileUrl(url), new IRequestResultListener<>() {
            @Override
            public void onSuccess(UploadFileUrl result) {
                DatingUploadUrl out = new DatingUploadUrl();
                out.url = result == null ? "" : result.url;
                out.path = path;
                out.publicUrl = result == null ? "" : result.public_url;
                if (callback != null) callback.onResult(HttpResponseCode.success, "", out);
            }

            @Override
            public void onFail(int code, String msg) {
                if (callback != null) callback.onResult(code, msg, null);
            }
        });
    }

    private String fileExt(String localPath) {
        if (TextUtils.isEmpty(localPath)) return "webp";
        String name = new File(localPath).getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String ext = name.substring(dot + 1).toLowerCase(Locale.US);
            if ("jpeg".equals(ext)) return "jpg";
            return ext;
        }
        return "webp";
    }

    private <T> IRequestResultListener<T> listener(final Callback<T> callback) {
        return new IRequestResultListener<>() {
            @Override
            public void onSuccess(T result) {
                if (callback != null) callback.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (callback != null) callback.onResult(code, msg, null);
            }
        };
    }

    public interface Callback<T> {
        void onResult(int code, String msg, T data);
    }
}
