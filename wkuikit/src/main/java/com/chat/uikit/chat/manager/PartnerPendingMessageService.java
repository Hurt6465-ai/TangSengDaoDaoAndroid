package com.chat.uikit.chat.manager;

import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.POST;

interface PartnerPendingMessageService {
    @POST("message/send")
    Observable<PartnerPendingMessageResponse> send(@Body Map<String, Object> body);
}
