package com.qwe7002.telegram_sms.static_class

import android.content.Context
import com.qwe7002.telegram_sms.R
import com.qwe7002.telegram_sms.ReSendJob
import io.paperdb.Paper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Resend {
    @JvmStatic
    fun addResendLoop(context: Context, msg: String) {
        var message = msg
        Paper.init(context)
        val resendList = Paper.book("resend").read("list", ArrayList<String>())
        val simpleDateFormat = SimpleDateFormat(context.getString(R.string.time_format), Locale.UK)
        message += "\n"+context.getString(R.string.time) + simpleDateFormat.format(Date(System.currentTimeMillis()))
        resendList?.add(message)
        if (resendList != null) {
            Paper.book("resend").write("list", resendList)
        }
        ReSendJob.startJob(context)
    }
}
