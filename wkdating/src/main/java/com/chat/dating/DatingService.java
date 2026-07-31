package com.chat.dating;

import com.chat.base.net.entity.CommonResponse;
import com.chat.dating.model.DatingFavoritesResponse;
import com.chat.dating.model.DatingMatchesResponse;
import com.chat.dating.model.DatingProfile;
import com.chat.dating.model.DatingRecommendResponse;
import com.chat.dating.model.DatingSwipeResult;
import com.chat.dating.model.DatingUndoResult;

import java.util.Map;

import com.alibaba.fastjson.JSONObject;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.PUT;

public interface DatingService {

    @PUT("user/current")
    Observable<CommonResponse> updateSharedUser(@Body JSONObject body);

    @GET("dating/profile/me")
    Observable<DatingProfile> getMyDatingProfile();

    @GET("dating/recommend")
    Observable<DatingRecommendResponse> recommend(@Query("cursor") String cursor,
                                                   @Query("limit") int limit,
                                                   @Query("scope") String scope,
                                                   @Query("session_id") String sessionId,
                                                   @Query("country_mode") String countryMode,
                                                   @Query("gender") String gender,
                                                   @Query("age_min") int ageMin,
                                                   @Query("age_max") int ageMax,
                                                   @Query("intent") String intent,
                                                   @Query("repeat") int repeat);

    @POST("dating/profile")
    Observable<DatingProfile> saveProfile(@Body Map<String, Object> body);

    @POST("dating/profile/copy_partner")
    Observable<DatingProfile> copyPartnerProfile(@Body Map<String, Object> body);

    @POST("dating/profile/enable")
    Observable<Object> enableProfile(@Body Map<String, Object> body);

    @POST("dating/location")
    Observable<Object> updateLocation(@Body Map<String, Object> body);

    @POST("dating/swipes")
    Observable<DatingSwipeResult> swipe(@Body Map<String, Object> body);

    @POST("dating/swipes/undo")
    Observable<DatingUndoResult> undoSwipe(@Body Map<String, Object> body);

    @POST("dating/exposures")
    Observable<Object> reportExposures(@Body Map<String, Object> body);


    @GET("dating/favorites")
    Observable<DatingFavoritesResponse> favorites(@Query("limit") int limit);

    @POST("dating/favorites/remove")
    Observable<Object> removeFavorite(@Body Map<String, Object> body);

    @GET("dating/matches")
    Observable<DatingMatchesResponse> matches(@Query("limit") int limit);

    @POST("dating/matches/{match_id}/cancel")
    Observable<Object> cancelMatch(@Path("match_id") String matchId);

    @POST("dating/block")
    Observable<Object> block(@Body Map<String, Object> body);

    @POST("dating/report")
    Observable<Object> report(@Body Map<String, Object> body);
}
