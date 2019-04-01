package com.zjy.downloadapplication;

import android.content.Context;
import android.content.SharedPreferences;

public class SPUtils {

    private static final String FILE_NAME = "share_download_file";

    public static void putString(Context context, String key, String value) {
        SharedPreferences.Editor editor = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(key, value);
        editor.apply();
    }

    public static String getString(Context context, String key) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getString(key, "");
    }

}
