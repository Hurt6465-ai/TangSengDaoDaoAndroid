package com.chat.partner.profile;

import android.text.TextUtils;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.net.entity.UploadFileUrl;

public class PartnerProfileModel extends WKBaseModel {
    private PartnerProfileModel() {}

    private static class Holder {
        private static final PartnerProfileModel INSTANCE = new PartnerProfileModel();
    }

    public static PartnerProfileModel getInstance() {
        return Holder.INSTANCE;
    }

    public void getUserProfile(String uid, final Callback<PartnerProfileEntity> callback) {
        request(createService(PartnerProfileService.class).getUserProfile(uid), new IRequestResultListener<>() {
            @Override
            public void onSuccess(PartnerProfileEntity result) {
                if (callback != null) callback.onResult(200, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (callback != null) callback.onResult(code, msg, null);
            }
        });
    }

    public void updateCurrentProfile(JSONObject body, final Callback<CommonResponse> callback) {
        request(createService(PartnerProfileService.class).updateCurrentProfile(body), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                int status = result == null ? 200 : result.status;
                String msg = result == null ? "" : result.msg;
                if (callback != null) callback.onResult(status, msg, result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (callback != null) callback.onResult(code, msg, null);
            }
        });
    }

    public void getProfileUploadUrl(String path, final Callback<String> callback) {
        if (TextUtils.isEmpty(path)) {
            if (callback != null) callback.onResult(400, "path empty", "");
            return;
        }
        String safePath = path.startsWith("/") ? path : "/" + path;
        String url = WKApiConfig.baseUrl + "file/upload?type=common&path=" + safePath;
        request(createService(PartnerProfileService.class).getUploadUrl(url), new IRequestResultListener<UploadFileUrl>() {
            @Override
            public void onSuccess(UploadFileUrl result) {
                if (callback != null) callback.onResult(200, "", result == null ? "" : result.url);
            }

            @Override
            public void onFail(int code, String msg) {
                if (callback != null) callback.onResult(code, msg, "");
            }
        });
    }

    public interface Callback<T> {
        void onResult(int code, String msg, T data);
    }
}
