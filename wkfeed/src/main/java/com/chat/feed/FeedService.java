package com.chat.feed;

import com.chat.base.net.entity.CommonResponse;
import com.chat.feed.model.CommentListResponse;
import com.chat.feed.model.FeedListResponse;

import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface FeedService {
    @GET("feed/recommend")
    Observable<FeedListResponse> recommend(@Query("mode") String mode,
                                           @Query("cursor") String cursor,
                                           @Query("limit") int limit,
                                           @Query("uid") String uid);

    @GET("feed/user/{uid}")
    Observable<FeedListResponse> userFeeds(@Path("uid") String uid,
                                           @Query("cursor") String cursor,
                                           @Query("limit") int limit);

    @GET("feed/{feed_id}/comments")
    Observable<CommentListResponse> comments(@Path("feed_id") String feedId,
                                             @Query("cursor") String cursor,
                                             @Query("limit") int limit);

    @POST("feed/{feed_id}/like")
    Observable<CommonResponse> like(@Path("feed_id") String feedId, @Body Map<String, Object> body);

    @POST("feed/{feed_id}/comments")
    Observable<CommonResponse> sendComment(@Path("feed_id") String feedId, @Body Map<String, Object> body);

    @POST("feed/publish")
    Observable<CommonResponse> publish(@Body Map<String, Object> body);
}
