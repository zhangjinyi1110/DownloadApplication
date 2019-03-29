package com.zjy.downloadapplication;

import io.reactivex.Flowable;
import okhttp3.ResponseBody;
import retrofit2.http.GET;
import retrofit2.http.Streaming;
import retrofit2.http.Url;

public interface DownloadApi {

    @Streaming
    @GET
    Flowable<ResponseBody> download(@Url String url);

}
