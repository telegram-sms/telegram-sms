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
import com.qwe7002.telegram_sms.data_structure.CcSendService
import com.qwe7002.telegram_sms.static_class.CcSend
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

class CcSendJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d("CCSend", "startJob: Trying to send message.")
        Paper.init(applicationContext)
        val sharedPreferences = applicationContext.getSharedPreferences("data", MODE_PRIVATE)
        val message: String = params?.extras?.getString("message", "") ?: ""
        var title: String = params?.extras?.getString("title", getString(R.string.app_name))
            ?: getString(R.string.app_name)
        var verificationCode: String = params?.extras?.getString("verification_code", "") ?: ""
        if (verificationCode.isEmpty()) {
            verificationCode = message
        } else {
            title += getString(R.string.verification_code)
        }
        Thread {
            val serviceListJson =
                Paper.book("system_config").read("CC_service_list", "[]").toString()
            val gson = Gson()
            var type = object : TypeToken<ArrayList<CcSendService>>() {}.type
            val sendList: ArrayList<CcSendService> = gson.fromJson(serviceListJson, type)
            val okhttpClient =
                Network.getOkhttpObj(
                    sharedPreferences.getBoolean("doh_switch", true),
                    Paper.book("system_config").read("proxy_config", proxy())
                )
            for (item in sendList) {
                if(item.enabled.not()) continue
                var header: Map<String, String> = mapOf()
                if (item.header.isNotEmpty()) {
                    type = object : TypeToken<Map<String, String>>() {}.type
                    header = gson.fromJson(item.header, type)
                }
                when (item.method) {
                    // 0: GET, 1: POST
                    0 -> {
                        networkProgressHandle(
                            "GET",
                            CcSend.render(
                                item.webhook,
                                mapOf(
                                    "Title" to URLEncoder.encode(
                                        title,
                                        StandardCharsets.UTF_8.toString()
                                    ),
                                    "Message" to URLEncoder.encode(
                                        message,
                                        StandardCharsets.UTF_8.toString()
                                    ),
                                    "Code" to URLEncoder.encode(
                                        verificationCode,
                                        StandardCharsets.UTF_8.toString()
                                    )
                                )
                            ),
                            null,
                            header,
                            okhttpClient
                        )
                    }

                    1 -> {
                        networkProgressHandle(
                            "POST",
                            CcSend.render(
                                item.webhook,
                                mapOf(
                                    "Title" to URLEncoder.encode(
                                        title,
                                        StandardCharsets.UTF_8.toString()
                                    ),
                                    "Message" to URLEncoder.encode(
                                        message,
                                        StandardCharsets.UTF_8.toString()
                                    ),
                                    "Code" to URLEncoder.encode(
                                        verificationCode,
                                        StandardCharsets.UTF_8.toString()
                                    )
                                )
                            ),
                            CcSend.render(
                                item.body,
                                mapOf(
                                    "Title" to title,
                                    "Message" to message,
                                    "Code" to verificationCode
                                )
                            ).toRequestBody(
                                constValue.JSON
                            ),
                            header,
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
        header: Map<String, String>,
        okhttpClient: OkHttpClient,
    ) {

        val requestObj = Request.Builder().url(requestUri).method(function, body)
        for (item in header) {
            requestObj.addHeader(item.key, item.value)
        }
        val call = okhttpClient.newCall(requestObj.build())
        try {
            val response = call.execute()
            if (response.code == 200) {
                Log.i("networkProgressHandle", "networkProgressHandle: Message sent successfully.")
            }else{
                Logs.writeLog(applicationContext, "Send message failed: " + response.code + " " + response.body.string())
            }
        } catch (e: IOException) {
            Logs.writeLog(applicationContext, "An error occurred while resending: " + e.message)
            e.printStackTrace()
        }
    }

    companion object {
        fun startJob(context: Context, title: String, message: String, verificationCode: String) {
            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            ccOptions.JOBID_counter += 1
            val jobInfoBuilder = JobInfo.Builder(
                ccOptions.JOBID_counter,
                ComponentName(context.packageName, CcSendJob::class.java.getName())
            )
                .setPersisted(true)
            val extras = PersistableBundle()
            extras.putString("title", title)
            extras.putString("message", message)
            extras.putString("verification_code", verificationCode)
            jobInfoBuilder.setExtras(extras)
            jobInfoBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            jobScheduler.schedule(jobInfoBuilder.build())

        }

        fun startJob(context: Context, title: String, message: String) {
            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            ccOptions.JOBID_counter += 1
            val jobInfoBuilder = JobInfo.Builder(
                ccOptions.JOBID_counter,
                ComponentName(context.packageName, CcSendJob::class.java.getName())
            )
                .setPersisted(true)
            val extras = PersistableBundle()
            extras.putString("title", title)
            extras.putString("message", message)
            jobInfoBuilder.setExtras(extras)
            jobInfoBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            jobScheduler.schedule(jobInfoBuilder.build())

        }
    }
}
