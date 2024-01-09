package com.airfreshener.telegram_sms.utils

import android.content.Context
import android.util.Log
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.utils.PaperUtils.getSystemBook
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogUtils {

    fun writeLog(context: Context?, log: String) {
        Log.i("write_log", log)
        if (context == null) return
        val newFileMode = Context.MODE_APPEND
        val simpleDateFormat = SimpleDateFormat(context.getString(R.string.time_format), Locale.UK)
        val writeString = "${simpleDateFormat.format(Date(System.currentTimeMillis()))} $log"
        var logCount = getSystemBook().read("log_count", 0)!!
        if (logCount >= 50000) {
            resetLogFile(context)
        }
        getSystemBook().write("log_count", ++logCount)
        writeLogFile(context, writeString, newFileMode)
    }

    fun readLog(context: Context?, line: Int): String {
        if (context == null) return ""
        val result = context.getString(R.string.no_logs)
        val builder = StringBuilder()
        var inputStream: FileInputStream? = null
        var channel: FileChannel? = null
        return try {
            inputStream = context.openFileInput("error.log")
            channel = inputStream.channel
            val buffer: ByteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            buffer.position(channel.size().toInt())
            var count = 0
            for (i in channel.size() - 1 downTo 0) {
                val c = Char(buffer[i.toInt()].toUShort())
                builder.insert(0, c)
                if (c == '\n') {
                    if (count == line - 1) {
                        break
                    }
                    ++count
                }
            }
            builder.toString().ifEmpty { result }
        } catch (e: IOException) {
            e.printStackTrace()
            val tag = "read_file_last_line"
            Log.d(tag, "Unable to read the file.")
            result
        } finally {
            try {
                inputStream?.close()
                channel?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun resetLogFile(context: Context) {
        getSystemBook().delete("log_count")
        writeLogFile(context, "", Context.MODE_PRIVATE)
    }

    private fun writeLogFile(context: Context, writeString: String, mode: Int) {
        var outputStream: FileOutputStream? = null
        try {
            outputStream = context.openFileOutput("error.log", mode)
            val bytes = writeString.toByteArray()
            outputStream.write(bytes)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }
}
