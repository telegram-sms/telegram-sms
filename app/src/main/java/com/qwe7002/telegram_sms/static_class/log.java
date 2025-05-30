package com.qwe7002.telegram_sms.static_class;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class log {
    private static final String TAG = "log";
    private static final String LOG_DIRECTORY = "log";
    private static final String LOG_FILE_PREFIX = "error_log_";
    private static final String LOG_FILE_SUFFIX = ".log";

    public static void writeLog(Context context, String log_message) {
        Log.i(TAG, log_message);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK);
        String write_log = simpleDateFormat.format(new Date()) + " " + log_message + "\n";
        File log_file = new File(context.getExternalFilesDir(LOG_DIRECTORY), getLogFileName());
        try {
            FileWriter fileWriter = new FileWriter(log_file, true);
            fileWriter.write(write_log);
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getLogFileName() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.UK);
        return LOG_FILE_PREFIX + simpleDateFormat.format(new Date()) + LOG_FILE_SUFFIX;
    }
}
