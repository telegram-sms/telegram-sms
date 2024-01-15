package com.airfreshener.telegram_sms

import android.app.Application
import com.airfreshener.telegram_sms.common.data.LogRepository
import com.airfreshener.telegram_sms.common.data.LogRepositoryImpl
import com.airfreshener.telegram_sms.common.data.PrefsRepository
import com.airfreshener.telegram_sms.common.data.SharedPrefsRepository
import com.airfreshener.telegram_sms.common.data.TelegramRepository
import com.airfreshener.telegram_sms.common.data.UssdRepository
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

    val telegramRepository: TelegramRepository by lazy {
        TelegramRepository(
            appContext = applicationContext,
            prefsRepository = prefsRepository,
            logRepository = logRepository,
        )
    }
    val ussdRepository: UssdRepository by lazy { UssdRepository(
        appContext = applicationContext,
        logRepository = logRepository,
        telegramRepository = telegramRepository,
    ) }

    override fun onCreate() {
        super.onCreate()
        PaperUtils.init(applicationContext)
    }
}
