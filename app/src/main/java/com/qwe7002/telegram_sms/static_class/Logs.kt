package com.qwe7002.telegram_sms.static_class

import android.content.Context
import android.util.Log
import com.qwe7002.telegram_sms.MMKV.MMKVConst
import com.qwe7002.telegram_sms.R
import com.tencent.mmkv.MMKV
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logs {
    private const val LOG_MAX_SIZE = 2000
    @JvmStatic
    fun writeLog(context: Context, log: String) {
        Log.i(context::class.simpleName, log)

        val kv = MMKV.mmkvWithID(MMKVConst.LOG_ID)

        val simpleDateFormat = SimpleDateFormat(context.getString(R.string.time_format), Locale.UK)
        val timeStamp = simpleDateFormat.format(Date(System.currentTimeMillis()))
        val logEntry = "\n$timeStamp $log"

        var existingLog = kv.getString("logs", "") ?: ""

        existingLog = existingLog + logEntry

        if (existingLog.length > LOG_MAX_SIZE) {
            existingLog = existingLog.substring(existingLog.length - LOG_MAX_SIZE)
            val firstNewLine = existingLog.indexOf('\n')
            if (firstNewLine != -1) {
                existingLog = existingLog.substring(firstNewLine)
            }
        }
        kv.putString("logs", existingLog)
    }

    @JvmStatic
   fun readLog(context: Context, line: Int): String {
        val result = "\n"+context.getString(R.string.no_logs)

        val kv = MMKV.mmkvWithID(MMKVConst.LOG_ID)

        val logs = kv.getString("logs", "") ?: ""
        if (logs.isEmpty()) {
            return result
        }

        val logLines = logs.split('\n').filter { it.isNotEmpty() }

        if (line >= logLines.size) {
            return logs
        }

        return logLines.takeLast(line).joinToString("\n")
    }

    fun resetLogFile() {
        MMKV.mmkvWithID(MMKVConst.LOG_ID).clearAll()
    }

}
