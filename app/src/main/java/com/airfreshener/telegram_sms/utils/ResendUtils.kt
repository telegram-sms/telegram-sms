package com.airfreshener.telegram_sms.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.services.ResendService
import com.airfreshener.telegram_sms.utils.PaperUtils.DEFAULT_BOOK
import com.airfreshener.telegram_sms.utils.PaperUtils.tryRead
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.*

object ResendUtils {
    fun addResendLoop(context: Context, message: String?) {
        val resendList: ArrayList<String?> = DEFAULT_BOOK.tryRead("resend_list", ArrayList())
        val simpleDateFormat = SimpleDateFormat(context.getString(R.string.time_format), Locale.UK)
        val sb = StringBuilder(message ?: "")
            .append(context.getString(R.string.time))
            .append(simpleDateFormat.format(Date(System.currentTimeMillis())))
        resendList.add(sb.toString())
        DEFAULT_BOOK.write("resend_list", resendList)
        startResend(context)
    }

    fun startResend(context: Context) {
        val intent = Intent(context, ResendService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
