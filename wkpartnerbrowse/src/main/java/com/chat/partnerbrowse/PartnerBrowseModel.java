package com.chat.partnerbrowse;

import com.chat.base.base.WKBaseModel;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.IRequestResultListener;
import com.chat.partnerbrowse.model.PartnerBrowseBean;
import com.chat.partnerbrowse.model.PartnerBrowseProfileMe;
import com.chat.partnerbrowse.model.PartnerBrowseResponse;
import com.chat.partnerbrowse.model.PartnerGreetingResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PartnerBrowseModel extends WKBaseModel {
    private PartnerBrowseModel() {}

    private static class Holder {
        private static final PartnerBrowseModel INSTANCE = new PartnerBrowseModel();
    }

    public static PartnerBrowseModel getInstance() {
        return Holder.INSTANCE;
    }

    public void listPartners(String cursor, int page, int limit, String sessionId, IRequestResultListener<PartnerBrowseResponse> listener) {
        request(createService(PartnerBrowseService.class).listPartners(cursor, page, limit, "browse", sessionId), listener);
    }

    public void getPartnerProfileMe(final Callback<PartnerBrowseProfileMe> callback) {
        request(createService(PartnerBrowseService.class).getPartnerProfileMe(), new IRequestResultListener<>() {
            @Override
            public void onSuccess(PartnerBrowseProfileMe result) {
                if (callback != null) callback.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (callback != null) callback.onResult(code, msg, null);
            }
        });
    }

    public void uploadLocation(Map<String, Object> body, final Callback<Object> callback) {
        request(createService(PartnerBrowseService.class).uploadLocation(body == null ? new HashMap<>() : body), new IRequestResultListener<>() {
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

    public void sendGreeting(String uid, String text, final Callback<PartnerGreetingResponse> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("to_uid", uid == null ? "" : uid);
        body.put("target_uid", uid == null ? "" : uid);
        body.put("text", text == null ? "" : text);
        body.put("source", "partner_browse");
        request(createService(PartnerBrowseService.class).sendGreeting(body), new IRequestResultListener<>() {
            @Override
            public void onSuccess(PartnerGreetingResponse result) {
                if (callback != null) callback.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (callback != null) callback.onResult(code, msg, null);
            }
        });
    }


    public void reportExposures(List<Map<String, Object>> items, final Callback<Object> callback) {
        if (items == null || items.isEmpty()) {
            if (callback != null) callback.onResult(HttpResponseCode.success, "", null);
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("items", items);
        request(createService(PartnerBrowseService.class).reportExposures(body), new IRequestResultListener<>() {
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
    public void reportPartnerEvent(String uid, String eventType, long durationMs, int photoIndex) {
        if (uid == null || uid.length() == 0) return;
        Map<String, Object> item = new HashMap<>();
        item.put("to_uid", uid);
        item.put("seen_at", System.currentTimeMillis());
        item.put("duration_ms", Math.max(0L, durationMs));
        item.put("event_type", eventType == null || eventType.length() == 0 ? "expose" : eventType);
        item.put("source", "partner_browse");
        item.put("photo_index", Math.max(0, photoIndex));
        ArrayList<Map<String, Object>> list = new ArrayList<>();
        list.add(item);
        reportExposures(list, null);
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
