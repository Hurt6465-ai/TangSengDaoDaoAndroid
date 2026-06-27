package com.chat.partnerbrowse;

import com.chat.partnerbrowse.model.PartnerBrowseBean;
import com.chat.partnerbrowse.model.PartnerBrowseResponse;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface PartnerBrowseService {
    @GET("partners")
    Observable<PartnerBrowseResponse> listPartners(@Query("cursor") String cursor,
                                                   @Query("page") int page,
                                                   @Query("limit") int limit,
                                                   @Query("mode") String mode);

    @GET("users/{uid}")
    Observable<PartnerBrowseBean> getPartnerProfile(@Path("uid") String uid);
}
