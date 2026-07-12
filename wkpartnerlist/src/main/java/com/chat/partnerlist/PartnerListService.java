package com.chat.partnerlist;

import com.chat.partnerlist.model.PartnerGreetingResponse;
import com.chat.partnerlist.model.PartnerHeartbeatResponse;
import com.chat.partnerlist.model.PartnerListResponse;
import com.chat.partnerlist.model.PartnerOnlineBatchResponse;

import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface PartnerListService {
    @GET("partner-list/recommendations")
    Observable<PartnerListResponse> recommendations();

    @POST("partner-list/online/batch")
    Observable<PartnerOnlineBatchResponse> onlineBatch(@Body Map<String, Object> body);

    @POST("partner-list/activity/heartbeat")
    Observable<PartnerHeartbeatResponse> heartbeat(@Body Map<String, Object> body);

    @POST("partners/greetings")
    Observable<PartnerGreetingResponse> sendGreeting(@Body Map<String, Object> body);
}
