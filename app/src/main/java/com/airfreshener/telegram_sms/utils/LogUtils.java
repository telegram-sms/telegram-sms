package com.airfreshener.telegram_sms.utils;

import android.content.Context;
import android.util.Log;

import com.airfreshener.telegram_sms.R;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.paperdb.Paper;

public class LogUtils {
    public static void writeLog(@Nullable Context context, String log) {
        Log.i("write_log", log);
        if (context == null) return;
        int newFileMode = Context.MODE_APPEND;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(context.getString(R.string.time_format), Locale.UK);
        String write_string = "\n" + simpleDateFormat.format(new Date(System.currentTimeMillis())) + " " + log;
        int logCount = Paper.book("system_config").read("log_count", 0);
        if (logCount >= 50000) {
            resetLogFile(context);
        }
        Paper.book("system_config").write("log_count", ++logCount);
        writeLogFile(context, write_string, newFileMode);
    }

    public static String readLog(@Nullable Context context, int line) {
        if (context == null) return "";
        String result = context.getString(R.string.no_logs);
        String TAG = "read_file_last_line";
        StringBuilder builder = new StringBuilder();
        FileInputStream inputStream = null;
        FileChannel channel = null;
        try {
            inputStream = context.openFileInput("error.log");
            channel = inputStream.getChannel();
            ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            buffer.position((int) channel.size());
            int count = 0;
            for (long i = channel.size() - 1; i >= 0; i--) {
                char c = (char) buffer.get((int) i);
                builder.insert(0, c);
                if (c == '\n') {
                    if (count == (line - 1)) {
                        break;
                    }
                    ++count;
                }
            }
            if (!builder.toString().isEmpty()) {
                return builder.toString();
            } else {
                return result;
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "Unable to read the file.");
            return result;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (channel != null) {
                    channel.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void resetLogFile(Context context) {
        Paper.book("system_config").delete("log_count");
        writeLogFile(context, "", Context.MODE_PRIVATE);
    }

    private static void writeLogFile(@NotNull Context context, @NotNull String writeString, int mode) {
        FileOutputStream outputStream = null;
        try {
            outputStream = context.openFileOutput("error.log", mode);
            byte[] bytes = writeString.getBytes();
            outputStream.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
