package com.airfreshener.telegram_sms.utils

import android.content.Context
import com.airfreshener.telegram_sms.TelegramSmsApp

object ContextUtils {
    fun Context.app(): TelegramSmsApp = applicationContext as TelegramSmsApp
}
