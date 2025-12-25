package com.qwe7002.telegram_sms

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.qwe7002.telegram_sms.static_class.Service
import com.qwe7002.telegram_sms.value.Const
import com.tencent.mmkv.MMKV
import java.util.concurrent.TimeUnit

class KeepAliveJob : JobService() {

    companion object {
        private const val JOB_ID = 10
        private val MIN_LATENCY_MS = TimeUnit.SECONDS.toMillis(5)

        fun startJob(context: Context) {
            val jobScheduler = context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler

            val jobInfo = JobInfo.Builder(
                JOB_ID,
                ComponentName(context.packageName, KeepAliveJob::class.java.name)
            )
                .setPersisted(true)
                .setMinimumLatency(MIN_LATENCY_MS)
                .setOverrideDeadline(JobInfo.DEFAULT_INITIAL_BACKOFF_MILLIS)
                .build()

            jobScheduler.schedule(jobInfo)
        }

        fun stopJob(context: Context) {
            val jobScheduler = context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(JOB_ID)
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        val preferences = MMKV.defaultMMKV()

        if (preferences.getBoolean("initialized", false)) {
            Service.startService(
                applicationContext,
                preferences.getBoolean("battery_monitoring_switch", false),
                preferences.getBoolean("chat_command", false)
            )
        }

        Log.d(Const.TAG, "startJob: Try to pull up the service")
        jobFinished(params, false)
        startJob(applicationContext)

        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}
