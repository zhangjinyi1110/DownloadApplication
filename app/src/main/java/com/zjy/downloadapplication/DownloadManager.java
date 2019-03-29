package com.zjy.downloadapplication;

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

public class DownloadManager {

    private String baseUrl;
    private String url;
    private String filePath;
    private String fileName;
    private Context context;
    private DownloadListener downloadListener;

    DownloadManager(String baseUrl, String url, String filePath, String fileName, Context context, DownloadListener downloadListener) {
        this.baseUrl = defValue(baseUrl, "http://www.baidu.com/");
        this.url = defValue(url, "");
        this.filePath = defValue(filePath, Environment.getExternalStorageDirectory().getPath() + "/" + context.getPackageName() + "/download/");
        this.fileName = defValue(fileName, "");
        this.context = context;
        this.downloadListener = downloadListener;
    }

    public void download() {
        new Retrofit.Builder()
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .baseUrl(baseUrl)
                .client(getClient())
                .build()
                .create(DownloadApi.class)
                .download(url)
                .subscribeOn(Schedulers.io())
                .doOnNext(new Consumer<ResponseBody>() {
                    @Override
                    public void accept(ResponseBody responseBody) throws Exception {
                        Log.e("aaa", "accept: write");
                        write(responseBody.byteStream(), filePath, fileName);
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<ResponseBody>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(Long.MAX_VALUE);
                        if (downloadListener != null) {
                            downloadListener.downloadStart();
                        }
                    }

                    @Override
                    public void onNext(ResponseBody responseBody) {

                    }

                    @Override
                    public void onError(Throwable t) {
                        if (downloadListener != null) {
                            downloadListener.downloadFailure(t);
                        }
                    }

                    @Override
                    public void onComplete() {
                        if (downloadListener != null) {
                            downloadListener.downloadFinish();
                        }
                    }
                });
    }

    private void write(InputStream inputStream, String filePath, String fileName) throws IOException {
//        try {
            File pFile = new File(filePath);
            if (!pFile.exists()) {
                pFile.mkdirs();
            }
            File file = new File((filePath.endsWith("/") ? filePath : (filePath + "/")) + fileName);
            if (!file.exists()) {
                file.delete();
            }
            file.createNewFile();
            FileOutputStream outputStream = new FileOutputStream(file);
            byte[] data = new byte[1024];
            int len;
            while ((len = inputStream.read(data)) != -1) {
                Log.e("aaa", "write: " + len);
                outputStream.write(data, 0, len);
            }
            inputStream.close();
            outputStream.flush();
            outputStream.close();
        Log.e("aaa", "write: finish");
//        } catch (Exception e) {
//            Log.e("aaa", "write: " + e.toString());
//            if (downloadListener != null) {
//                downloadListener.downloadFailure(e);
//            }
//        }
    }

    private OkHttpClient getClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.addInterceptor(new DownloadInterceptor(downloadListener));
        return builder.build();
    }

    private String defValue(String text, String def) {
        return TextUtils.isEmpty(text) ? def : text;
    }

}
