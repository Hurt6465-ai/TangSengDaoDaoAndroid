package com.chat.partner.profile;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface PartnerProfileService {
    @GET("users/{uid}")
    Observable<PartnerProfileEntity> getUserProfile(@Path("uid") String uid);

    @PUT("user/current")
    Observable<CommonResponse> updateCurrentProfile(@Body JSONObject body);
}
