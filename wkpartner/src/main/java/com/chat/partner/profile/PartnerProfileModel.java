package com.chat.partner.profile;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
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
                if (callback != null) callback.onResult(result.status, result.msg, result);
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
