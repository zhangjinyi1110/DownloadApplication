package com.zjy.downloadapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;

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

import static android.content.Context.NOTIFICATION_SERVICE;

public class DownloadTask {

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
    private boolean downloading = false;
    private boolean showNotification = false;
    private OnUpdateNotification updateNotification;
    private NotificationManager notificationManager;
    private String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};

    DownloadTask(String baseUrl, String url, String filePath, String fileName
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
        gson = new Gson();
        String model = SPUtils.getString(context, url);
        if (TextUtils.isEmpty(model)) {
            taskModel = new DownloadTaskModel();
            taskModel.setFilePath(filePath);
            taskModel.setUrl(url);
//            taskModel.setFileName(fileName);
        } else {
            taskModel = gson.fromJson(model, DownloadTaskModel.class);
        }
    }

    @SuppressWarnings("unchecked")
    public void start() {
        if (!checkPermission()) {
            showTip();
            return;
        }
        downloading = true;
        if (taskModel.getTaskCount() == 0)
            queryLen();
        else {
            File file = openFile(taskModel.getFileName());
            if (file == null) {
                queryLen();
            } else {
                Flowable<ResponseBody>[] flowables = new Flowable[taskModel.getTaskCount()];
                long count = 0;
                long item = taskModel.getFileLength() / taskModel.getTaskCount();
                for (int i = 0; i < taskModel.getTaskCount(); i++) {
                    String[] len = taskModel.getRange()[i].split("-");
                    long start = Long.valueOf(len[0]);
                    long end = len.length <= 1 || TextUtils.isEmpty(len[1]) ? -1 : Long.valueOf(len[1]);
                    flowables[i] = task(file, start, end, i);
                    count += start - item * i;
                }
                if (downloadListener != null) {
                    downloadListener.downloadStart();//算是开始
                }
                if (showNotification) {

                }
                startDownload(count, taskModel.getFileLength(), flowables);
            }
        }
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
                        if (showNotification) {

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
        startDownload(0, fileLength, flowables);
    }

    private void startDownload(long i, long fileLength, Flowable<ResponseBody>[] flowables) {
        //更新进度监听
        updateListener(i, fileLength);
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
                            Log.e("aaa", "onComplete: " + SPUtils.getString(context, taskModel.getUrl()));
                            SPUtils.putString(context, taskModel.getUrl(), "");
                            downloadListener.downloadFinish();
                            if (showNotification) {
                                if (updateNotification == null) {
                                    closeNotification();
                                }
                            }
                        }
                    }
                });
    }

    //更新进度监听
    private void updateListener(final long currLen, final long fileLength) {
        Flowable.create(new FlowableOnSubscribe<Integer>() {
            @Override
            public void subscribe(FlowableEmitter<Integer> e) {
                emitter = e;
            }
        }, BackpressureStrategy.BUFFER)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Integer>() {
                    private long downloadLength = currLen;

                    @Override
                    public void onSubscribe(Subscription s) {
                        subscriptions.add(s);
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(Integer integer) {
                        downloadLength += integer;
                        float progress = ((float) (downloadLength * 100)) / fileLength;
                        downloadListener.downloadProgress(fileLength, downloadLength, progress);
                        if (showNotification) {
                            if (updateNotification != null)
                                updateNotification(updateNotification.onUpdate(fileLength, downloadLength, progress));
                            else
                                updateNotification(getDefNotify(progress));
                        }
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
    private void write(File file, InputStream inputStream, long start, long end, int i) throws Exception {
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rwd");
        randomAccessFile.seek(start);
        byte[] data = new byte[1024];
        int len;
        long count = start;
        while (downloading && ((len = inputStream.read(data)) != -1)) {
            randomAccessFile.write(data, 0, len);
            if (downloadListener != null) {
                synchronized (DownloadBuilder.class) {
                    count += len - 1;
                    taskModel.updateRange(i, count, end);
                    SPUtils.putString(context, taskModel.getUrl(), gson.toJson(taskModel));
                    emitter.onNext(len);
                }
            }
        }
        inputStream.close();
        randomAccessFile.close();
    }

    private void updateNotification(Notification notification) {
        if (notificationManager == null)
            notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(0, notification);
    }

    private void closeNotification() {
        if (notificationManager == null)
            notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancel(0);
        String channelId = "download_finish";
        NotificationCompat.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "download_channel_finish", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
            builder = new NotificationCompat.Builder(context, channelId);
        } else {
            builder = new NotificationCompat.Builder(context, channelId);
        }
        Notification notification = builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(fileName)
                .setContentText("下载完成")
                .build();
        notification.flags = Notification.FLAG_AUTO_CANCEL;
        notificationManager.notify(1, notification);
    }

    @SuppressLint("DefaultLocale")
    private Notification getDefNotify(float progress) {
        String channelId = "download";
        NotificationCompat.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "download_channel", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
            builder = new NotificationCompat.Builder(context, channelId);
        } else {
            builder = new NotificationCompat.Builder(context, channelId);
        }
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.notify_view);
        views.setTextViewText(R.id.title, fileName);
        views.setTextViewText(R.id.progress, String.format("%.2f", progress) + "%");
        views.setProgressBar(R.id.progressBar, 100, (int) progress, false);
        return builder.setSmallIcon(R.drawable.ic_file_download_black_24dp).setContent(views).build();
    }

    //打开文件
    private File openFile(String fileName) {
        File file = new File((filePath.endsWith("/") ? filePath : (filePath + "/")) + fileName);
        if (!file.getParentFile().exists()) {
            return null;
        }
        if (!file.exists()) {
            return null;
        }
        return file;
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
        taskModel.setFileName(fileName);
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

    public void finish() {
        if (subscriptions != null && !subscriptions.isEmpty()) {
            downloading = false;
            for (Subscription s : subscriptions) {
                if (s != null) {
                    s.cancel();
                }
            }
            subscriptions.clear();
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

    void setShowNotification(boolean showNotification) {
        this.showNotification = showNotification;
    }

    public interface OnUpdateNotification {
        Notification onUpdate(long size, long currLen, float progress);
    }

    void setUpdateNotification(OnUpdateNotification updateNotification) {
        this.updateNotification = updateNotification;
    }
}
