package com.chat.deepseek;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.GET;
import retrofit2.http.Path;

interface DeepSeekProfileService {
    @GET("users/{uid}")
    Observable<DeepSeekProfileEntity> getUserProfile(@Path("uid") String uid);
}
