package com.airfreshener.telegram_sms

import android.app.Application
import com.airfreshener.telegram_sms.common.PrefsRepository
import com.airfreshener.telegram_sms.common.SharedPrefsRepository
import com.airfreshener.telegram_sms.utils.PaperUtils

class TelegramSmsApp : Application() {

    val prefsRepository: PrefsRepository by lazy {
        SharedPrefsRepository(
            sharedPreferences = applicationContext.getSharedPreferences("data", MODE_PRIVATE)
        )
    }

    override fun onCreate() {
        super.onCreate()
        PaperUtils.init(applicationContext)
    }
}
