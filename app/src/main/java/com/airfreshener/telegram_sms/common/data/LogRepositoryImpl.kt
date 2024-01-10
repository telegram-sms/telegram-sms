package com.airfreshener.telegram_sms.common.data

import android.content.Context
import android.util.Log
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.utils.PaperUtils
import com.airfreshener.telegram_sms.utils.PaperUtils.tryRead
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class LogRepositoryImpl(
    private val appContext: Context,
) : LogRepository {

    private val initialLog = listOf(appContext.getString(R.string.no_logs))
    private val list: ArrayList<String> = ArrayList()
    private val _logs: MutableStateFlow<List<String>> = MutableStateFlow(initialList())
    override val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private fun initialList(): List<String> {
        var inputStream: FileInputStream? = null
        var inputreader: InputStreamReader? = null
        var buffreader: BufferedReader? = null
        return try {
            inputStream = appContext.openFileInput("error.log")
            inputreader = InputStreamReader(inputStream)
            buffreader = BufferedReader(inputreader)
            list.addAll(buffreader.readLines().takeLast(100))
            if (list.isEmpty()) initialLog else list
        } catch (e: IOException) {
            e.printStackTrace()
            Log.d(TAG, "Unable to read the file.")
            emptyList()
        } finally {
            try {
                buffreader?.close()
                inputreader?.close()
                inputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    override fun writeLog(log: String) {
        Log.i("write_log", log)
        val simpleDateFormat = SimpleDateFormat(appContext.getString(R.string.time_format), Locale.UK)
        val writeString = "${simpleDateFormat.format(Date(System.currentTimeMillis()))} $log\n"
        var logCount = PaperUtils.getSystemBook().tryRead("log_count", 0)
        if (logCount >= 50000) {
            resetLogFile()
        }
        PaperUtils.getSystemBook().write("log_count", ++logCount)
        writeLogFile(writeString, Context.MODE_APPEND)
        _logs.value = list.apply {
            if (size == 100) removeFirst()
            add(writeString)
        }
    }

    override fun resetLogFile() {
        PaperUtils.getSystemBook().delete("log_count")
        writeLogFile("", Context.MODE_PRIVATE)
    }

    private fun writeLogFile(writeString: String, mode: Int) {
        var outputStream: FileOutputStream? = null
        try {
            outputStream = appContext.openFileOutput("error.log", mode)
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

    companion object {
        private const val TAG = "LogRepository"
    }
}
