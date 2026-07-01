package com.chat.room.model;

import com.chat.room.entity.CreateRoomTopicRequest;
import com.chat.room.entity.RoomTopicEntity;
import com.chat.room.entity.RoomTopicListResponse;

import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

interface RoomTopicService {
    @GET("chatrooms/list")
    Observable<RoomTopicListResponse> listRooms();

    @GET("chatrooms/list")
    Observable<RoomTopicListResponse> listRooms(@Query("limit") int limit, @Query("cursor") String cursor);

    @POST("chatrooms/create")
    Observable<RoomTopicEntity> createRoom(@Body CreateRoomTopicRequest request);

    @POST("chatrooms/enter")
    Observable<RoomTopicEntity> enterRoom(@Body Map<String, Object> request);

    @POST("chatrooms/pin")
    Observable<RoomTopicEntity> pinRoom(@Body Map<String, Object> request);

    @POST("chatrooms/delete")
    Observable<Object> deleteRoom(@Body Map<String, Object> request);
}
