package com.airfreshener.telegram_sms.common.data

interface LogRepository {

    fun writeLog(log: String)
    fun readLog(line: Int): String
    fun resetLogFile()
}
