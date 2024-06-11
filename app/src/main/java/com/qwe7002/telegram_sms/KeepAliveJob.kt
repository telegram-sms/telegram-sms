package com.qwe7002.telegram_sms

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.qwe7002.telegram_sms.static_class.service


class KeepAliveJob : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        val sharedPreferences = applicationContext.getSharedPreferences("data", MODE_PRIVATE)
        if (sharedPreferences.getBoolean("initialized", false)) {
            service.startService(
                applicationContext,
                sharedPreferences.getBoolean("battery_monitoring_switch", false),
                sharedPreferences.getBoolean("chat_command", false)
            )
        }
        Log.d("KeepAliveJob", "startJob: Try to pull up the service")
        this.jobFinished(params, false)
        startJob(applicationContext)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        startJob(this)
        return false
    }

    companion object {
        fun startJob(context: Context) {
            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler

            val jobInfoBuilder = JobInfo.Builder(
                10,
                ComponentName(context.packageName, KeepAliveJob::class.java.getName())
            )
                .setPersisted(true)
            jobInfoBuilder.setOverrideDeadline(JobInfo.DEFAULT_INITIAL_BACKOFF_MILLIS)
            jobInfoBuilder.setMinimumLatency(5000)

            jobScheduler.schedule(jobInfoBuilder.build())
        }
    }
}
