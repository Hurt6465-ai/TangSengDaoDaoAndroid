package com.chat.feedlist.net;

import com.chat.base.net.entity.CommonResponse;
import com.chat.feedlist.model.FeedListInteractionResponse;
import com.chat.feedlist.model.FeedListResponse;
import com.chat.feedlist.model.FeedListTikTokPreview;

import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface FeedListService {
    @GET("feed/timeline")
    Observable<FeedListResponse> timeline(@Query("cursor") String cursor, @Query("limit") int limit);

    @GET("feed/timeline/following")
    Observable<FeedListResponse> following(@Query("cursor") String cursor, @Query("limit") int limit);

    @POST("feed/tiktok/preview")
    Observable<FeedListTikTokPreview> tiktokPreview(@Body Map<String, Object> body);

    @POST("feed/{feed_id}/like")
    Observable<FeedListInteractionResponse> like(@Path("feed_id") String feedId, @Body Map<String, Object> body);

    @POST("feed/follow")
    Observable<CommonResponse> follow(@Body Map<String, Object> body);

    @DELETE("feed/follow")
    Observable<CommonResponse> unfollow(@Query("uid") String uid);

    @POST("feed/{feed_id}/share")
    Observable<FeedListInteractionResponse> share(@Path("feed_id") String feedId, @Body Map<String, Object> body);

    @POST("feed/{feed_id}/report")
    Observable<CommonResponse> report(@Path("feed_id") String feedId, @Body Map<String, Object> body);

    @POST("feed/publish")
    Observable<CommonResponse> publish(@Body Map<String, Object> body);

    @DELETE("feed/{feed_id}")
    Observable<CommonResponse> delete(@Path("feed_id") String feedId);
}
