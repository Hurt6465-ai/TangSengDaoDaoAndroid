package com.chat.partnerbrowse;

import com.chat.partnerbrowse.model.PartnerBrowseBean;
import com.chat.partnerbrowse.model.PartnerBrowseProfileMe;
import com.chat.partnerbrowse.model.PartnerBrowseResponse;
import com.chat.partnerbrowse.model.PartnerGreetingResponse;

import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface PartnerBrowseService {
    @GET("partners")
    Observable<PartnerBrowseResponse> listPartners(@Query("cursor") String cursor,
                                                   @Query("page") int page,
                                                   @Query("limit") int limit,
                                                   @Query("mode") String mode);

    @GET("partners/profile/me")
    Observable<PartnerBrowseProfileMe> getPartnerProfileMe();

    @POST("partners/location")
    Observable<Object> uploadLocation(@Body Map<String, Object> body);

    @POST("partners/greetings")
    Observable<PartnerGreetingResponse> sendGreeting(@Body Map<String, Object> body);

    @GET("users/{uid}")
    Observable<PartnerBrowseBean> getPartnerProfile(@Path("uid") String uid);
}
