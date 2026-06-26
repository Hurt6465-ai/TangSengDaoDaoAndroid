package com.chat.partner.profile;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.net.entity.UploadFileUrl;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Url;

public interface PartnerProfileService {
    @GET("users/{uid}")
    Observable<PartnerProfileEntity> getUserProfile(@Path("uid") String uid);

    @PUT("user/current")
    Observable<CommonResponse> updateCurrentProfile(@Body JSONObject body);

    @GET
    Observable<UploadFileUrl> getUploadUrl(@Url String url);
}
