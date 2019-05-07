package com.zjy.downloadapplication;

import android.annotation.SuppressLint;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public class DownloadManager {

    private List<DownloadTask> downloadTasks;
    @SuppressLint("StaticFieldLeak")
    private static DownloadManager manager;
    @SuppressLint("StaticFieldLeak")
    private static Context context;

    public static void init(Context context) {
        DownloadManager.context = context;
    }

    private DownloadManager() {
        downloadTasks = new ArrayList<>();
    }

    public static DownloadManager getInstance() {
        if (manager == null) {
            synchronized (DownloadManager.class) {
                if (manager == null) {
                    manager = new DownloadManager();
                }
            }
        }
        return manager;
    }

    public DownloadManager addTask(DownloadTask task) {
        if (task != null && !isHave(task))
            downloadTasks.add(task);
        return manager;
    }

    public DownloadManager addAllTask(List<DownloadTask> tasks) {
        if (tasks != null) {
            for (DownloadTask task : tasks) {
                addTask(task);
            }
        }
        return manager;
    }

    public void startAllTask() {
        for (DownloadTask task : downloadTasks) {
            startTask(task);
        }
    }

    public void startTask(DownloadTask task) {
        for (DownloadTask t : downloadTasks) {
            if (t.equals(task) && !t.isDownloading()) {
                task.start();
                return;
            }
        }
    }

    public void addAndStart(DownloadTask task) {
        if (task != null) {
            if (!isHave(task)) {
                downloadTasks.add(task);
            }
            task.start();
        }
    }

    public void addAndStartAll(List<DownloadTask> tasks) {
        if (tasks != null && !tasks.isEmpty()) {
            for (DownloadTask t : tasks) {
                addAndStart(t);
            }
        }
    }

    public void stopAllTask() {
        for (DownloadTask task : downloadTasks) {
            stopTask(task);
        }
    }

    public void stopTask(DownloadTask task) {
        for (DownloadTask t : downloadTasks) {
            if (t.equals(task) && t.isDownloading()) {
                task.finish();
                return;
            }
        }
    }

    private boolean isHave(DownloadTask task) {
        for (DownloadTask t : downloadTasks) {
            if (t.getTaskModel().getUrl().contains(task.getTaskModel().getUrl())) {
                return true;
            }
        }
        return false;
    }

}
