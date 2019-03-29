package com.zjy.downloadapplication;

public interface DownloadListener {

    void downloadStart();
    void downloadFinish();
    void downloadProgress(long size, long currLen, float progress);
    void downloadFailure(Throwable t);

}
