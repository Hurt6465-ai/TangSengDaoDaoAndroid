package com.chat.deepseek;

import com.chat.base.base.WKBaseModel;
import com.chat.base.net.IRequestResultListener;

final class DeepSeekProfileModel extends WKBaseModel {
    interface Callback {
        void onResult(DeepSeekProfileEntity entity);
    }

    private DeepSeekProfileModel() {}

    private static final class Holder {
        private static final DeepSeekProfileModel INSTANCE = new DeepSeekProfileModel();
    }

    static DeepSeekProfileModel getInstance() {
        return Holder.INSTANCE;
    }

    void getProfile(String uid, Callback callback) {
        if (uid == null || uid.trim().isEmpty()) {
            if (callback != null) callback.onResult(null);
            return;
        }
        request(createService(DeepSeekProfileService.class).getUserProfile(uid.trim()),
                new IRequestResultListener<DeepSeekProfileEntity>() {
                    @Override
                    public void onSuccess(DeepSeekProfileEntity result) {
                        if (callback != null) callback.onResult(result);
                    }

                    @Override
                    public void onFail(int code, String msg) {
                        if (callback != null) callback.onResult(null);
                    }
                });
    }
}
