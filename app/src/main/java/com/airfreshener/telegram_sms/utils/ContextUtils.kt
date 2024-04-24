package com.airfreshener.telegram_sms.utils

import android.content.Context
import com.airfreshener.telegram_sms.TelegramSmsApp

object ContextUtils {
    fun Context.app(): TelegramSmsApp = applicationContext as TelegramSmsApp
    fun Context.dpToPx(dp: Double) = (dp * resources.displayMetrics.density)
    fun Context.dpToPx(dp: Int) = (dp * resources.displayMetrics.density)
}
