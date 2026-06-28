package com.chat.partner.profile;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.net.ApiService;
import com.chat.base.net.entity.UploadFileUrl;

import java.io.File;
import java.util.Locale;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;

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

    public void getProfileUploadFileUrl(String uid, String localPath, boolean cover, final Callback<ProfileUploadUrl> callback) {
        String safeUid = uid == null ? "" : uid.trim();
        if (safeUid.length() == 0) {
            if (callback != null) callback.onResult(400, "uid is empty", null);
            return;
        }
        String suffix = "webp";
        File file = new File(localPath == null ? "" : localPath);
        String fileName = file.getName();
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            String ext = fileName.substring(dot + 1).toLowerCase(Locale.US);
            if (ext.length() > 0) suffix = ext;
        }
        String prefix = cover ? "cover_" : "photo_";
        String path = "/profile/" + safeUid + "/" + prefix + System.currentTimeMillis() + "." + suffix;
        String requestUrl = WKApiConfig.baseUrl + "file/upload?type=common&path=" + path;
        request(createService(ApiService.class).getUploadFileUrl(requestUrl), new IRequestResultListener<>() {
            @Override
            public void onSuccess(UploadFileUrl result) {
                ProfileUploadUrl uploadUrl = new ProfileUploadUrl();
                uploadUrl.url = result == null ? "" : result.url;
                uploadUrl.path = "file/preview/common" + path;
                if (callback != null) callback.onResult(200, "", uploadUrl);
            }

            @Override
            public void onFail(int code, String msg) {
                if (callback != null) callback.onResult(code, msg, null);
            }
        });
    }

    public static class ProfileUploadUrl {
        public String url;
        public String path;
    }

    public interface Callback<T> {
        void onResult(int code, String msg, T data);
    }
}
