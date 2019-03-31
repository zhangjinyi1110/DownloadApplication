package com.zjy.downloadapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
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
    private PermissionListener permissionListener;
    private boolean ignoreNonePermission;
    private int taskCount;
    private DownloadApi downloadApi;
    private long fileLength;
    private long downloadLength;
    private FlowableEmitter<Integer> emitter;
    private String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};

    DownloadManager(String baseUrl, String url, String filePath, String fileName
            , Context context, DownloadListener downloadListener
            , PermissionListener permissionListener) {
        this.baseUrl = defValue(baseUrl, "http://www.baidu.com/");
        this.url = defValue(url, "");
        this.filePath = defValue(filePath, Environment.getExternalStorageDirectory().getPath() + "/" + context.getPackageName() + "/download/");
        String[] path = url.split("/");
        String name = path.length > 1 ? path[path.length - 1] : String.valueOf(System.currentTimeMillis());
        this.fileName = defValue(fileName, name);
        this.context = context;
        this.downloadListener = downloadListener;
        this.permissionListener = permissionListener;
        taskCount = 1;
    }

    public void download() {
        if (!checkPermission()) {
            showTip();
            return;
        }
        downloadApi = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build()
                .create(DownloadApi.class);
        downloadApi.queryLength(url)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<ResponseBody>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ResponseBody responseBody) {
                        try {
                            prepare(responseBody);
                        } catch (IOException e) {
                            e.printStackTrace();
                            if (downloadListener != null) {
                                downloadListener.downloadFailure(e);
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        if (downloadListener != null) {
                            downloadListener.downloadFailure(t);
                        }
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void prepare(ResponseBody responseBody) throws IOException {
        fileLength = responseBody.contentLength();
        long itemLen = fileLength / taskCount;
        long count = 0;
        Flowable<ResponseBody>[] flowables = new Flowable[taskCount];
        File pFile = new File(filePath);
        if (!pFile.exists()) {
            pFile.mkdirs();
        }
        File file = new File((filePath.endsWith("/") ? filePath : (filePath + "/")) + fileName);
        if (file.exists()) {
            file.delete();
        }
        file.createNewFile();
        for (int i = 0; i < taskCount; i++) {
            long start = count;
            long end;
            if (i == taskCount - 1) {
                end = -1;
            } else {
                end = count + itemLen - 1;
            }
            count += itemLen;
            flowables[i] = task(file, start, end);
        }
        Flowable.mergeArray(flowables)
                .subscribe(new Subscriber<ResponseBody>() {
                    @SuppressLint("CheckResult")
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(Long.MAX_VALUE);
                        downloadLength = 0;
                        if (downloadListener != null) {
                            downloadListener.downloadStart();
                        }
                        Flowable.create(new FlowableOnSubscribe<Integer>() {
                            @Override
                            public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                                emitter = e;
                            }
                        }, BackpressureStrategy.BUFFER)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new Consumer<Integer>() {
                                    @Override
                                    public void accept(Integer aLong) throws Exception {
                                        downloadLength += aLong;
                                        downloadListener.downloadProgress(fileLength, downloadLength, ((float) (downloadLength * 100)) / fileLength);
                                    }
                                });
                    }

                    @Override
                    public void onNext(ResponseBody responseBody) {
                        Log.e("aaa", "onNext: write next");
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

    private void showTip() {
        if (!ignoreNonePermission) {
            if (permissionListener != null) {
                permissionListener.nonePermission();
            } else {
                AlertDialog dialog = new AlertDialog.Builder(context)
                        .setTitle("没有操作权限")
                        .setMessage("是否跳转授权页面")
                        .setNegativeButton("否", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .setPositiveButton("是", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                Intent intent = new Intent();
                                try {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    // 将用户引导到系统设置页面
//                                        if (Build.VERSION.SDK_INT >= 9) {
                                    Log.e("HLQ_Struggle", "APPLICATION_DETAILS_SETTINGS");
                                    intent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
                                    intent.setData(Uri.fromParts("package", context.getPackageName(), null));
//                                        } else if (Build.VERSION.SDK_INT <= 8) {
//                                            intent.setAction(Intent.ACTION_VIEW);
//                                            intent.setClassName("com.android.settings", "com.android.settings.InstalledAppDetails");
//                                            intent.putExtra("com.android.settings.ApplicationPkgName", context.getPackageName());
//                                        }
                                    context.startActivity(intent);
                                } catch (Exception e) {//抛出异常就直接打开设置页面
                                    intent = new Intent(Settings.ACTION_SETTINGS);
                                    context.startActivity(intent);
                                }
                            }
                        })
                        .create();
                dialog.show();
            }
        }
    }

    private Flowable<ResponseBody> task(final File file, final long start, long end) {
        String range = "bytes=" + start + "-" + (end == -1 ? "" : end);
        Log.e("aaa", "task: " + range);
        return new Retrofit.Builder()
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .baseUrl(baseUrl)
//                .client(getClient())
                .build()
                .create(DownloadApi.class)
                .download(range, url)
                .subscribeOn(Schedulers.io())
                .doOnNext(new Consumer<ResponseBody>() {
                    @Override
                    public void accept(ResponseBody responseBody) throws Exception {
                        Log.e("aaa", "accept: write");
                        write(file, responseBody.byteStream(), filePath, fileName, start);
                    }
                })
                .observeOn(AndroidSchedulers.mainThread());
    }

    private boolean checkPermission() {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @SuppressLint("CheckResult")
    private void write(File file, InputStream inputStream, String filePath, String fileName, long start) throws IOException {
//        try {
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rwd");
        randomAccessFile.seek(start);
//        FileOutputStream outputStream = new FileOutputStream(file);
        byte[] data = new byte[1024];
        int len;
        long a = 0;
        while ((len = inputStream.read(data)) != -1) {
//            outputStream.write(data, 0, len);
            randomAccessFile.write(data, 0, len);
//            Log.e("aaa", "write: read" + len);
            a += len;
            if (downloadListener != null) {
                emitter.onNext(len);
            }
        }
        inputStream.close();
        randomAccessFile.close();
//        outputStream.flush();
//        outputStream.close();
        Log.e("aaa", "write: finish" + a + "/" + start);
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

    void setIgnoreNonePermission(boolean ignoreNonePermission) {
        this.ignoreNonePermission = ignoreNonePermission;
    }

    void setTaskCount(int taskCount) {
        if (taskCount < 1) {
            return;
        }
        this.taskCount = taskCount;
    }
}
