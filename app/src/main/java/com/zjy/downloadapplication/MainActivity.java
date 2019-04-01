package com.zjy.downloadapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.FlowableOperator;
import io.reactivex.FlowableTransformer;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "aaa";
    long start;
    long end;
    int taskCount = 1;
    private TextView textView;
    private DownloadManager manager;

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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        }
    }

    private void download() {
        String url = ((EditText) findViewById(R.id.url)).getText().toString();
        manager = DownloadBuilder.with(MainActivity.this)
                .setUrl(url)
                .setTaskCount(taskCount)
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
                        Log.e(TAG, "downloadProgress: " + size + "/" + currLen + "/" + progress);
                    }

                    @Override
                    public void downloadFailure(Throwable t) {
                        Log.e(TAG, "downloadFailure: " + t.toString());
                    }
                }).build();
        manager.start();
    }
}
