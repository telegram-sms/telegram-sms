package com.qwe7002.telegram_sms

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.google.gson.Gson
import com.qwe7002.telegram_sms.config.proxy
import com.qwe7002.telegram_sms.data_structure.sendMessageBody
import com.qwe7002.telegram_sms.static_class.log
import com.qwe7002.telegram_sms.static_class.network
import com.qwe7002.telegram_sms.value.constValue
import io.paperdb.Paper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ReSendJob : JobService() {
    private lateinit var requestUri: String
    private val tableName: String = "resend_list"
    override fun onStartJob(params: JobParameters?): Boolean {
        Paper.init(applicationContext)
        val sharedPreferences = applicationContext.getSharedPreferences("data", MODE_PRIVATE)
        requestUri = network.getUrl(sharedPreferences.getString("bot_token", ""), "SendMessage")
        Thread {
            val sendList: java.util.ArrayList<String>? =
                Paper.book().read(tableName, java.util.ArrayList())
            val okhttpClient =
                network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true),
                    Paper.book("system_config").read("proxy_config", proxy()))
            for (item in sendList!!) {
                networkProgressHandle(
                    item,
                    sharedPreferences.getString("chat_id", "").toString(),
                    okhttpClient,
                    sharedPreferences.getString("message_thread_id", "").toString()
                )
            }
            if (sendList.isNotEmpty()){
                log.writeLog(applicationContext, "The resend failure message is complete.")
            }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }

    private fun networkProgressHandle(
        message: String,
        chatId: String,
        okhttpClient: OkHttpClient,
        messageThreadId: String
    ) {
        val requestBody = sendMessageBody()
        requestBody.chat_id = chatId
        requestBody.text = message
        requestBody.message_thread_id = messageThreadId
        if (message.contains("<code>") && message.contains("</code>")) {
            requestBody.parse_mode = "html"
        }
        val requestBodyJson = Gson().toJson(requestBody)
        val body: RequestBody = requestBodyJson.toRequestBody(constValue.JSON)
        val requestObj: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(requestObj)
        try {
            val response = call.execute()
            if (response.code == 200) {
                val resendListLocal = Paper.book().read(tableName, ArrayList<String>())!!
                resendListLocal.remove(message)
                Paper.book().write(tableName, resendListLocal)
            }
        } catch (e: IOException) {
            log.writeLog(applicationContext, "An error occurred while resending: " + e.message)
            e.printStackTrace()
        }
    }
    companion object {
        fun startJob(context: Context) {
            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler

            val jobInfoBuilder = JobInfo.Builder(
                20,
                ComponentName(context.packageName, KeepAliveJob::class.java.getName())
            )
                .setPersisted(true)
            jobInfoBuilder.setPeriodic(TimeUnit.MINUTES.toMillis(15))
            jobInfoBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            jobScheduler.schedule(jobInfoBuilder.build())

        }

        fun stopJob(context: Context) {
            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler

            jobScheduler.cancel(10)
        }
    }
}
