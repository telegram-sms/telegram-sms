package com.airfreshener.telegram_sms

import android.app.Application
import com.airfreshener.telegram_sms.common.data.LogRepository
import com.airfreshener.telegram_sms.common.data.LogRepositoryImpl
import com.airfreshener.telegram_sms.common.data.PrefsRepository
import com.airfreshener.telegram_sms.common.data.SharedPrefsRepository
import com.airfreshener.telegram_sms.utils.PaperUtils

class TelegramSmsApp : Application() {

    // TODO move dependencies to some DI

    val prefsRepository: PrefsRepository by lazy {
        SharedPrefsRepository(
            sharedPreferences = applicationContext.getSharedPreferences("data", MODE_PRIVATE)
        )
    }
    val logRepository: LogRepository by lazy {
        LogRepositoryImpl(appContext = applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        PaperUtils.init(applicationContext)
    }
}
