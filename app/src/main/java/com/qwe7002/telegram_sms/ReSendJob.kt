package com.qwe7002.telegram_sms

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.qwe7002.telegram_sms.MMKV.MMKVConst
import com.qwe7002.telegram_sms.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_sms.static_class.TelegramApi
import com.tencent.mmkv.MMKV
import java.util.concurrent.TimeUnit

class ReSendJob : JobService() {
    private lateinit var resendMMKV: MMKV

    private fun networkProgressHandle(message: String) {
        resendMMKV = MMKV.mmkvWithID(MMKVConst.RESEND_ID)
        val requestBody = RequestMessage()
        requestBody.text = message
        if (message.contains("<code>") && message.contains("</code>")) {
            requestBody.parseMode = "html"
        }

        val result = TelegramApi.sendMessageSync(
            context = this,
            requestBody = requestBody,
            errorTag = "ReSendJob",
            fallbackSubId = -1,  // No SMS fallback for resend
            enableResend = false  // Don't add back to resend loop
        )

        if (result != null) {
            // Successfully sent, remove from resend list
            val resendListLocal = resendMMKV.decodeStringSet("resend_list", setOf())?.toMutableList() ?: mutableListOf()
            resendListLocal.remove(message)
            resendMMKV.encode("resend_list", resendListLocal.toSet())
        } else {
            Log.e("ReSendJob", "Failed to resend message, will retry later")
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d("ReSend", "startJob: Try resending the message.")
        MMKV.initialize(applicationContext)
        resendMMKV = MMKV.mmkvWithID(MMKVConst.RESEND_ID)

        Thread {
            val sendList = resendMMKV.decodeStringSet("resend_list", setOf())?.toMutableList() ?: mutableListOf()
            for (item in sendList) {
                networkProgressHandle(item)
            }
            if (sendList.isNotEmpty()) {
                Log.i(
                    "ReSendJob",
                    "startJob: Resend completed. ${sendList.size} messages have been resent."
                )
            }
            jobFinished(params, false)
        }.start()

        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }

    companion object {
        fun startJob(context: Context) {

            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler

            val jobInfoBuilder = JobInfo.Builder(
                20,
                ComponentName(context.packageName, ReSendJob::class.java.getName())
            )
                .setPersisted(true)
            jobInfoBuilder.setPeriodic(TimeUnit.MINUTES.toMillis(15))
            jobInfoBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            jobScheduler.schedule(jobInfoBuilder.build())

        }

        fun stopJob(context: Context) {
            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler

            jobScheduler.cancel(20)
        }
    }
}
