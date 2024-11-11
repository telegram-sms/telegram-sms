package com.qwe7002.telegram_sms.static_class

import android.content.Context
import android.util.Log
import com.qwe7002.telegram_sms.R
import io.paperdb.Paper
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logs {
    @JvmStatic
    fun writeLog(context: Context, log: String) {
        Log.i("write_log", log)
        val newFileMode = Context.MODE_APPEND
        val simpleDateFormat = SimpleDateFormat(context.getString(R.string.time_format), Locale.UK)
        val writeString =
            "\n" + simpleDateFormat.format(Date(System.currentTimeMillis())) + " " + log;
        /*        int log_count = Paper.book("system_config").read("log_count", 0);
        if (log_count >= 50000) {
            resetLogFile(context);
        }
        Paper.book("system_config").write("log_count", ++log_count);*/
        writeLogFile(context, writeString, newFileMode)
    }

    @JvmStatic
    fun readLog(context: Context, line: Int): String {
        val result = context.getString(R.string.no_logs)
        val TAG = "read_file_last_line"
        val builder = StringBuilder()
        lateinit var fileInputStream: FileInputStream
        lateinit var channel: FileChannel
        try {
            fileInputStream = context.openFileInput("error.log")
            channel = fileInputStream.channel
            val buffer: ByteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            buffer.position(channel.size().toInt())
            var count = 0
            for (i in channel.size() - 1 downTo 0) {
                val c = Char(buffer[i.toInt()].toUShort())
                builder.insert(0, c)
                if (c == '\n') {
                    if (count == (line - 1)) {
                        break
                    }
                    ++count
                }
            }
            return builder.toString().ifEmpty {
                result
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.d(TAG, "Unable to read the file.")
            return result
        } finally {
            try {
                fileInputStream.close()
                channel.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun resetLogFile(context: Context) {
        Paper.book("system_config").delete("log_count")
        writeLogFile(context, "", Context.MODE_PRIVATE)
    }

    private fun writeLogFile(context: Context, writeString: String, mode: Int) {
        lateinit var fileOutputStream: FileOutputStream
        try {
            fileOutputStream = context.openFileOutput("error.log", mode)
            val bytes = writeString.toByteArray()
            fileOutputStream.write(bytes)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                fileOutputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
