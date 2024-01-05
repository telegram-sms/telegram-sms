package com.airfreshener.telegram_sms.static_class;

import android.content.Context;
import android.util.Log;

import com.airfreshener.telegram_sms.R;

import org.jetbrains.annotations.NotNull;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.paperdb.Paper;

public class log_func {
    public static void write_log(@NotNull Context context, String log) {
        Log.i("write_log", log);
        int new_file_mode = Context.MODE_APPEND;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(context.getString(R.string.time_format), Locale.UK);
        String write_string = "\n" + simpleDateFormat.format(new Date(System.currentTimeMillis())) + " " + log;
        int log_count = Paper.book("system_config").read("log_count", 0);
        if (log_count >= 50000) {
            reset_log_file(context);
        }
        Paper.book("system_config").write("log_count", ++log_count);
        write_log_file(context, write_string, new_file_mode);
    }

    public static String read_log(@NotNull Context context, int line) {
        String result = context.getString(R.string.no_logs);
        String TAG = "read_file_last_line";
        StringBuilder builder = new StringBuilder();
        FileInputStream file_stream = null;
        FileChannel channel = null;
        try {
            file_stream = context.openFileInput("error.log");
            channel = file_stream.getChannel();
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
                if (file_stream != null) {
                    file_stream.close();
                }
                if (channel != null) {
                    channel.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void reset_log_file(Context context) {
        Paper.book("system_config").delete("log_count");
        write_log_file(context, "", Context.MODE_PRIVATE);
    }

    private static void write_log_file(@NotNull Context context, @NotNull String write_string, int mode) {
        FileOutputStream file_stream = null;
        try {
            file_stream = context.openFileOutput("error.log", mode);
            byte[] bytes = write_string.getBytes();
            file_stream.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (file_stream != null) {
                try {
                    file_stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
