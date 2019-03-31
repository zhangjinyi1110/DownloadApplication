package com.zjy.downloadapplication;

import android.content.Context;

public class DownloadBuilder {

    private String baseUrl;
    private String url;
    private String filePath;
    private String fileName;
    private Context context;
    private DownloadListener downloadListener;
    private PermissionListener permissionListener;
    private boolean ignoreNonePermission;
    private int taskCount;

    public DownloadBuilder(Context context) {
        this.context = context;
    }

    public static DownloadBuilder with(Context context) {
        return new DownloadBuilder(context);
    }

    public DownloadBuilder setUrl(String url) {
        this.url = url;
        return this;
    }

    public DownloadBuilder setDownloadListener(DownloadListener downloadListener) {
        this.downloadListener = downloadListener;
        return this;
    }

    public DownloadBuilder setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public DownloadBuilder setFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    public DownloadBuilder setFilePath(String filePath) {
        this.filePath = filePath;
        return this;
    }

    public DownloadBuilder setPermissionListener(PermissionListener permissionListener) {
        this.permissionListener = permissionListener;
        return this;
    }

    public DownloadBuilder setIgnoreNonePermission(boolean ignoreNonePermission) {
        this.ignoreNonePermission = ignoreNonePermission;
        return this;
    }

    public DownloadBuilder setTaskCount(int taskCount) {
        this.taskCount = taskCount;
        return this;
    }

    public DownloadManager build() {
        DownloadManager manager = new DownloadManager(baseUrl, url, filePath, fileName, context, downloadListener, permissionListener);
        manager.setIgnoreNonePermission(ignoreNonePermission);
        manager.setTaskCount(taskCount);
        return manager;
    }
}
