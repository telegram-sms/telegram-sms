package com.airfreshener.telegram_sms.common.data

import kotlinx.coroutines.flow.StateFlow

interface LogRepository {

    val logs: StateFlow<List<String>>

    fun writeLog(log: String)
    fun resetLogFile()
}
