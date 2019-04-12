package com.zjy.downloadapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RemoteViews;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "aaa";
    long start;
    long end;
    int taskCount = 1;
    private TextView textView;
    private DownloadTask manager;
    private NotificationManager notificationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.btn_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                taskCount++;
            }
        });
        findViewById(R.id.btn_remove).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                taskCount--;
            }
        });
        textView = findViewById(R.id.btn);
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                download();
            }
        });
        findViewById(R.id.finish).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (manager != null) {
                    manager.finish();
                }
            }
        });
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        }
    }

    @SuppressLint("DefaultLocale")
    private void startN(float progress) {
        if (notificationManager == null)
            notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String channelId = "";
        NotificationCompat.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = "simple";
            NotificationChannel channel = new NotificationChannel(channelId, "channel", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
            builder = new NotificationCompat.Builder(this, channelId);
        } else {
            builder = new NotificationCompat.Builder(this, channelId);
        }
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.notify_view);
        views.setTextViewText(R.id.title, "下载");
        views.setTextViewText(R.id.progress, String.format("%.2f", progress) + "%");
        views.setProgressBar(R.id.progressBar, 100, (int) progress, false);
        Notification notification = builder.setContentIntent(pendingIntent)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContent(views)
                .build();
        notificationManager.notify(0, notification);
    }

    private void download() {
        String url = ((EditText) findViewById(R.id.url)).getText().toString();
        manager = DownloadBuilder.with(MainActivity.this)
                .setUrl(url)
                .setTaskCount(taskCount)
                .setShowNotification()
                .setDownloadListener(new DownloadListener() {
                    @Override
                    public void downloadStart() {
                        start = System.currentTimeMillis();
                        Log.e(TAG, "downloadStart: " + taskCount);
                    }

                    @Override
                    public void downloadFinish() {
                        end = System.currentTimeMillis();
                        Log.e(TAG, "downloadFinish: " + (end - start));
                    }

                    @Override
                    public void downloadProgress(long size, long currLen, float progress) {
                        textView.setText(String.valueOf(progress));
//                        Log.e(TAG, "downloadProgress: " + size + "/" + currLen + "/" + progress);
//                        startN(progress);
                    }

                    @Override
                    public void downloadFailure(Throwable t) {
                        Log.e(TAG, "downloadFailure: " + t.toString());
                    }
                }).build();
        manager.start();
    }

    private void finishN() {
        if (notificationManager == null)
            notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String channelId = "";
        NotificationCompat.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = "finish";
            NotificationChannel channel = new NotificationChannel(channelId, "channel_finish", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
            builder = new NotificationCompat.Builder(this, channelId);
        } else {
            builder = new NotificationCompat.Builder(this, channelId);
        }
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        Notification notification = builder.setContentIntent(pendingIntent)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("下载")
                .setContentText("下载完成")
                .build();
        notification.flags = Notification.FLAG_AUTO_CANCEL;
        notificationManager.notify(1, notification);
        notificationManager.cancel(0);
    }
}
