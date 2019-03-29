package com.zjy.downloadapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "aaa";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = ((EditText) findViewById(R.id.url)).getText().toString();
                DownloadBuilder.with(MainActivity.this)
                        .setUrl(url)
                        .setFileName("aaa.jpg")
                        .setDownloadListener(new DownloadListener() {
                            @Override
                            public void downloadStart() {
                                Log.e(TAG, "downloadStart: ");
                            }

                            @Override
                            public void downloadFinish() {
                                Log.e(TAG, "downloadFinish: ");
                            }

                            @Override
                            public void downloadProgress(int size, int currLen, float progress) {
                                Log.e(TAG, "downloadProgress: " + size + "/" + currLen + "/" + progress);
                            }

                            @Override
                            public void downloadFailure(Throwable t) {
                                Log.e(TAG, "downloadFailure: " + t.toString());
                            }
                        }).build().download();
            }
        });
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        }
    }
}
