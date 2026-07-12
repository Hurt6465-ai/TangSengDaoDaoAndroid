package com.chat.uikit.chat.manager;

import com.chat.base.base.WKBaseModel;
import com.chat.base.net.IRequestResultListener;

import java.util.Map;

final class PartnerPendingMessageModel extends WKBaseModel {
    private static final PartnerPendingMessageModel INSTANCE = new PartnerPendingMessageModel();

    private PartnerPendingMessageModel() {}

    static PartnerPendingMessageModel getInstance() {
        return INSTANCE;
    }

    void send(Map<String, Object> body, IRequestResultListener<PartnerPendingMessageResponse> listener) {
        request(createService(PartnerPendingMessageService.class).send(body), listener);
    }
}
