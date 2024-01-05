package com.airfreshener.telegram_sms.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.services.ResendService
import io.paperdb.Paper
import java.text.SimpleDateFormat
import java.util.*

object ResendUtils {
    @JvmStatic
    fun addResendLoop(context: Context, message: String?) {
        var message = message
        Paper.init(context)
        val resendList: ArrayList<String?> = Paper.book().read("resend_list", ArrayList())!!
        val simpleDateFormat = SimpleDateFormat(context.getString(R.string.time_format), Locale.UK)
        message += """
               
               ${context.getString(R.string.time)}${simpleDateFormat.format(Date(System.currentTimeMillis()))}
               """.trimIndent()
        resendList.add(message)
        Paper.book().write("resend_list", resendList)
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
