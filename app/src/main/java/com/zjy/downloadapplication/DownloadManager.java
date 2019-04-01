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

import com.google.gson.Gson;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
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
    private FlowableEmitter<Integer> emitter;
    private List<Subscription> subscriptions;
    private DownloadTaskModel taskModel;
    private Gson gson;
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
        init();
    }

    private void init() {
        taskCount = 1;
        downloadApi = new Retrofit.Builder()
                .baseUrl(this.baseUrl)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build()
                .create(DownloadApi.class);
        subscriptions = new ArrayList<>();
        taskModel = new DownloadTaskModel();
        taskModel.setFilePath(filePath);
        taskModel.setUrl(url);
        taskModel.setFileName(fileName);
        gson = new Gson();
    }

    public void start() {
        if (!checkPermission()) {
            showTip();
            return;
        }
        queryLen();
    }

    //查询长度
    private void queryLen() {
        downloadApi.queryLength(url)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<ResponseBody>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        subscriptions.add(s);
                        s.request(Long.MAX_VALUE);
                        if (downloadListener != null) {
                            downloadListener.downloadStart();//算是开始
                        }
                    }

                    @Override
                    public void onNext(ResponseBody responseBody) {
                        try {
                            prepare(responseBody.contentLength());//准备
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

    //准备
    @SuppressWarnings("unchecked")
    private void prepare(final long fileLength) throws IOException {
        //创建文件
        final File file = createFile(fileName, 0);
        taskModel.setFileLength(fileLength);
        taskModel.setTaskCount(taskCount);
        //计算长度
        long itemLen = fileLength / taskCount;
        long count = 0;
        Flowable<ResponseBody>[] flowables = new Flowable[taskCount];
        for (int i = 0; i < taskCount; i++) {
            final long start = count;
            long end;
            if (i == taskCount - 1) {
                end = -1;
            } else {
                end = count + itemLen - 1;
            }
            count += itemLen;
            taskModel.addRange(i, start, end);
            flowables[i] = task(file, start, end, i);
        }
        //更新进度监听
        updateListener(fileLength);
        //开始下载
        Flowable.mergeArray(flowables)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<ResponseBody>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        subscriptions.add(s);
                        s.request(Long.MAX_VALUE);
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
                            Log.e("aaa", "onComplete: " + SPUtils.getString(context, taskModel.getFileName()));
                        }
                    }
                });
    }

    //更新进度监听
    private void updateListener(final long fileLength) {
        Flowable.create(new FlowableOnSubscribe<Integer>() {
            @Override
            public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                emitter = e;
            }
        }, BackpressureStrategy.BUFFER)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Integer>() {
                    private long downloadLength = 0;

                    @Override
                    public void onSubscribe(Subscription s) {
                        subscriptions.add(s);
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(Integer integer) {
                        downloadLength += integer;
                        downloadListener.downloadProgress(fileLength, downloadLength, ((float) (downloadLength * 100)) / fileLength);
                    }

                    @Override
                    public void onError(Throwable t) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    //任务
    private Flowable<ResponseBody> task(final File file, final long start, final long end, final int i) {
        String range = "bytes=" + start + "-" + (end == -1 ? "" : end);
        return downloadApi.download(range, url)
                .subscribeOn(Schedulers.io())
                .doOnNext(new Consumer<ResponseBody>() {
                    @Override
                    public void accept(ResponseBody responseBody) throws Exception {
                        write(file, responseBody.byteStream(), start, end, i);
                    }
                });
    }

    //写入
    @SuppressLint("CheckResult")
    private void write(File file, InputStream inputStream, long start, long end, int i) throws IOException {
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rwd");
        randomAccessFile.seek(start);
        byte[] data = new byte[1024];
        int len;
        long count = start;
        while ((len = inputStream.read(data)) != -1) {
            randomAccessFile.write(data, 0, len);
            if (downloadListener != null) {
                synchronized (DownloadBuilder.class) {
                    count += len - 1;
                    taskModel.updateRange(i, count, end);
                    SPUtils.putString(context, taskModel.getFileName(), gson.toJson(taskModel));
                    emitter.onNext(len);
                }
            }
        }
        inputStream.close();
        randomAccessFile.close();
    }

    //创建文件
    private File createFile(String fileName, int count) throws IOException {
        File file = new File((filePath.endsWith("/") ? filePath : (filePath + "/")) + fileName);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        if (file.exists()) {
            String fName;
            if (fileName.contains("(" + count + ")")) {
                fName = fileName.replace("(" + count + ")", "(" + (count + 1) + ")");
            } else if (count == 0) {
                int last = fileName.lastIndexOf(".");
                fName = fileName.substring(0, last) + "(1)" + fileName.substring(last);
            } else {
                fName = fileName;
            }
            count++;
            return createFile(fName, count);
        }
        file.createNewFile();
        return file;
    }

    private boolean checkPermission() {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
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

    private void finish() {
        if (subscriptions != null && !subscriptions.isEmpty()) {
            for (Subscription s : subscriptions) {
                if (s != null) {
                    s.cancel();
                }
            }
        }
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
        } else if (taskCount > 10) {
            this.taskCount = 10;
            return;
        }
        this.taskCount = taskCount;
    }
}
