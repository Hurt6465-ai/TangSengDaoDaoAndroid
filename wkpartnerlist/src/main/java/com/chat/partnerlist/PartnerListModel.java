package com.chat.partnerlist;

import com.chat.base.base.WKBaseModel;
import com.chat.base.net.IRequestResultListener;
import com.chat.partnerlist.model.PartnerGreetingResponse;
import com.chat.partnerlist.model.PartnerHeartbeatResponse;
import com.chat.partnerlist.model.PartnerListResponse;
import com.chat.partnerlist.model.PartnerOnlineBatchResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PartnerListModel extends WKBaseModel {
    private PartnerListModel() {}

    private static class Holder {
        private static final PartnerListModel INSTANCE = new PartnerListModel();
    }

    public static PartnerListModel getInstance() {
        return Holder.INSTANCE;
    }

    public void recommendations(IRequestResultListener<PartnerListResponse> listener) {
        request(createService(PartnerListService.class).recommendations(), listener);
    }

    public void onlineBatch(List<String> uids, IRequestResultListener<PartnerOnlineBatchResponse> listener) {
        Map<String, Object> body = new HashMap<>();
        body.put("uids", uids == null ? new ArrayList<>() : new ArrayList<>(uids));
        request(createService(PartnerListService.class).onlineBatch(body), listener);
    }

    public void heartbeat(IRequestResultListener<PartnerHeartbeatResponse> listener) {
        request(createService(PartnerListService.class).heartbeat(new HashMap<>()), listener);
    }

    public void sendGreeting(String uid, String text, IRequestResultListener<PartnerGreetingResponse> listener) {
        Map<String, Object> body = new HashMap<>();
        body.put("to_uid", uid == null ? "" : uid);
        body.put("target_uid", uid == null ? "" : uid);
        body.put("text", text == null ? "" : text);
        body.put("source", "partner_list");
        request(createService(PartnerListService.class).sendGreeting(body), listener);
    }
}
