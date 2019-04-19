package com.zjy.downloadapplication;

public class DownloadTaskModel {

    private String url;
    private long fileLength;
    private int taskCount;
    private String[] range;
    private String fileName;
    private String filePath;
    private long currLen;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getFileLength() {
        return fileLength;
    }

    public void setFileLength(long fileLength) {
        this.fileLength = fileLength;
    }

    public int getTaskCount() {
        return taskCount;
    }

    public void setTaskCount(int taskCount) {
        this.taskCount = taskCount;
        range = new String[taskCount];
    }

    public String[] getRange() {
        return range;
    }

    public void addRange(int index, long start, long end) {
        if (range == null) {
            range = new String[taskCount];
        }
        range[index] = start + "-" + (end == -1 ? "" : end);
    }

    public void updateRange(int index, long start, long end) {
        range[index] = start + "-" + (end == -1 ? "" : end);
        setCurrLen();
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getCurrLen() {
        return currLen;
    }

    public void setCurrLen() {
        currLen = 0;
        long item = fileLength / taskCount;
        for (int i = 0; i < taskCount; i++) {
            String[] len = range[i].split("-");
            long start = Long.valueOf(len[0]);
            currLen += start - item * i;
        }
    }
}
