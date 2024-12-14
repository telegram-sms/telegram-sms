package com.qwe7002.telegram_sms

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.qwe7002.telegram_sms.config.proxy
import com.qwe7002.telegram_sms.data_structure.CCService
import com.qwe7002.telegram_sms.static_class.CCSend
import com.qwe7002.telegram_sms.static_class.Logs
import com.qwe7002.telegram_sms.static_class.Network
import com.qwe7002.telegram_sms.value.ccOptions
import com.qwe7002.telegram_sms.value.constValue
import io.paperdb.Paper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class CCSendJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d("CCSend", "startJob: Trying to send message.")
        Paper.init(applicationContext)
        val sharedPreferences = applicationContext.getSharedPreferences("data", MODE_PRIVATE)
        val extraData = params?.extras?.getString("extra_data", "")
        Thread {
            val serviceListJson =
                Paper.book("system_config").read("CC_service_list", "[]").toString()
            val gson = Gson()
            val type = object : TypeToken<ArrayList<CCService>>() {}.type
            val sendList: ArrayList<CCService> = gson.fromJson(serviceListJson, type)
            val okhttpClient =
                Network.getOkhttpObj(
                    sharedPreferences.getBoolean("doh_switch", true),
                    Paper.book("system_config").read("proxy_config", proxy())
                )
            for (item in sendList) {

                when (item.type) {
                    // 0: GET, 1: POST
                    0 -> {
                        networkProgressHandle(
                            "GET",
                            CCSend.render(item.webhook, mapOf("Message" to URLEncoder.encode(extraData!!, StandardCharsets.UTF_8.toString()))),
                            null,
                            okhttpClient
                        )
                    }
                    1 -> {
                        networkProgressHandle(
                            "POST",
                            CCSend.render(item.webhook, mapOf("Message" to URLEncoder.encode(extraData!!, StandardCharsets.UTF_8.toString()))),
                            CCSend.render(item.body, mapOf("Message" to extraData)).toRequestBody(
                                constValue.JSON),
                            okhttpClient
                        )
                    }
                }
            }
            if (sendList.isNotEmpty()) {
                Logs.writeLog(applicationContext, "The resend failure message is complete.")
            }
            jobFinished(params, false)
        }.start()

        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }

    private fun networkProgressHandle(
        function: String,

        requestUri: String,
        body: RequestBody?,
        okhttpClient: OkHttpClient,
    ) {

        val requestObj: Request = Request.Builder().url(requestUri).method(function, body).build()
        val call = okhttpClient.newCall(requestObj)
        try {
            val response = call.execute()
            if (response.code == 200) {
                Log.i("networkProgressHandle", "networkProgressHandle: Message sent successfully.")
            }
        } catch (e: IOException) {
            Logs.writeLog(applicationContext, "An error occurred while resending: " + e.message)
            e.printStackTrace()
        }
    }

    companion object {
        fun startJob(context: Context, extraData: String) {

            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            ccOptions.JOBID_counter += 1
            val jobInfoBuilder = JobInfo.Builder(
                ccOptions.JOBID_counter,
                ComponentName(context.packageName, CCSendJob::class.java.getName())
            )
                .setPersisted(true)
            val extras = PersistableBundle()
            extras.putString("extra_data", extraData)
            jobInfoBuilder.setExtras(extras)
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
