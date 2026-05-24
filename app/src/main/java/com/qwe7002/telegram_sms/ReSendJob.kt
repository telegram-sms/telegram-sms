package com.qwe7002.telegram_sms

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.qwe7002.telegram_sms.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_sms.static_class.JobIds
import com.qwe7002.telegram_sms.static_class.Resend
import com.qwe7002.telegram_sms.static_class.TelegramApi
import com.qwe7002.telegram_sms.value.TAG
import com.tencent.mmkv.MMKV
import java.util.concurrent.TimeUnit

class ReSendJob : JobService() {
    private val logTag = "${TAG}.ReSendJob"

    @Volatile
    private var workerThread: Thread? = null

    @Volatile
    private var stopped = false

    private fun networkProgressHandle(message: String) {
        val requestBody = RequestMessage()
        requestBody.text = message
        if (message.contains("<code>") && message.contains("</code>")) {
            requestBody.parseMode = "html"
        }

        val result = TelegramApi.sendMessageSync(
            context = this,
            requestBody = requestBody,
            fallbackSubId = -1,  // No SMS fallback for resend
            enableResend = false  // Don't add back to resend loop
        )

        if (result != null) {
            // Successfully sent: atomically drop just this message so a concurrent add isn't lost.
            Resend.removeFromResendList(message)
        } else {
            Log.e(logTag, "Failed to resend message, will retry later")
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(logTag, "ReSendJob: Try resending the message.")
        MMKV.initialize(applicationContext)

        stopped = false
        workerThread = Thread {
            val self = Thread.currentThread()
            val sendList = Resend.getResendList()
            for (item in sendList) {
                // Stop on OS preemption: check both the interrupt flag (OkHttp may clear it after a
                // cancelled call) and that we're still the active worker.
                if (stopped || self.isInterrupted || workerThread !== self) {
                    Log.w(logTag, "ReSendJob: stopped, aborting remaining resends.")
                    break
                }
                networkProgressHandle(item)
            }
            // Only finish (and clear our slot) if we completed normally. If onStopJob ran, it already
            // requested a reschedule via return-true; calling jobFinished(false) here would cancel it.
            if (!stopped && workerThread === self) {
                if (sendList.isNotEmpty()) {
                    Log.i(
                        logTag,
                        "ReSendJob: Resend completed. ${sendList.size} messages have been processed."
                    )
                }
                workerThread = null
                jobFinished(params, false)
            }
        }.also { it.start() }

        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // The OS is reclaiming the job (e.g. Doze / network loss). Interrupt the worker so we stop
        // running network requests without a WakeLock; return true so the job is rescheduled.
        stopped = true
        workerThread?.interrupt()
        workerThread = null
        return true
    }

    companion object {
        fun startJob(context: Context) {

            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler

            val jobInfoBuilder = JobInfo.Builder(
                JobIds.RESEND,
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

            jobScheduler.cancel(JobIds.RESEND)
        }
    }
}
