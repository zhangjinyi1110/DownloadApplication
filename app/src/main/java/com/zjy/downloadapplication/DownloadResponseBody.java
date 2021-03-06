package com.zjy.downloadapplication;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;

public class DownloadResponseBody extends ResponseBody {

    private ResponseBody responseBody;
    private DownloadListener listener;
    private BufferedSource bufferedSource;

    DownloadResponseBody(ResponseBody responseBody, DownloadListener listener) {
        this.responseBody = responseBody;
        this.listener = listener;
    }

    @Nullable
    @Override
    public MediaType contentType() {
        return responseBody.contentType();
    }

    @Override
    public long contentLength() {
        return responseBody.contentLength();
    }

    @Override
    public BufferedSource source() {
        if (bufferedSource == null)
            bufferedSource = Okio.buffer(source(responseBody.source()));
        return bufferedSource;
    }

    private Source source(Source source) {
        return new ForwardingSource(source) {
            private long size = 0;
            @Override
            public long read(@NonNull Buffer sink, long byteCount) throws IOException {
                long len = super.read(sink, byteCount);
                Log.e("aaa", "read: " + len + "/" + byteCount + "/" + contentLength());
                if (listener != null && len != -1) {
                    size += len;
                    listener.downloadProgress(contentLength(), size, ((float) (size * 100)) / contentLength());
                }
                return len;
            }
        };
    }
}
