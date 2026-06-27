package com.chat.partnerbrowse;

import com.chat.base.base.WKBaseModel;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.IRequestResultListener;
import com.chat.partnerbrowse.model.PartnerBrowseBean;
import com.chat.partnerbrowse.model.PartnerBrowseResponse;

public class PartnerBrowseModel extends WKBaseModel {
    private PartnerBrowseModel() {}

    private static class Holder {
        private static final PartnerBrowseModel INSTANCE = new PartnerBrowseModel();
    }

    public static PartnerBrowseModel getInstance() {
        return Holder.INSTANCE;
    }

    public void listPartners(String cursor, int page, int limit, IRequestResultListener<PartnerBrowseResponse> listener) {
        request(createService(PartnerBrowseService.class).listPartners(cursor, page, limit, "browse"), listener);
    }

    public void getPartnerProfile(String uidOrKey, final Callback<PartnerBrowseBean> callback) {
        request(createService(PartnerBrowseService.class).getPartnerProfile(uidOrKey), new IRequestResultListener<>() {
            @Override
            public void onSuccess(PartnerBrowseBean result) {
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
