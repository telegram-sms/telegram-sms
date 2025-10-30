package com.qwe7002.telegram_sms

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.PersistableBundle
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.qwe7002.telegram_sms.MMKV.MMKVConst
import com.qwe7002.telegram_sms.data_structure.config.CcConfig
import com.qwe7002.telegram_sms.data_structure.CcSendService
import com.qwe7002.telegram_sms.static_class.CcSend
import com.qwe7002.telegram_sms.static_class.Logs
import com.qwe7002.telegram_sms.static_class.Network
import com.qwe7002.telegram_sms.static_class.SnowFlake
import com.qwe7002.telegram_sms.value.CcType
import com.tencent.mmkv.MMKV
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class CcSendJob : JobService() {
    @Suppress("NAME_SHADOWING")
    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d("CCSend", "startJob: Trying to send message.")
        MMKV.initialize(applicationContext)
        val preferences = MMKV.defaultMMKV()
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
            val carbonCopyMMKV = MMKV.mmkvWithID(MMKVConst.CARBON_COPY_ID)
            val serviceListJson = carbonCopyMMKV.getString("service", "[]")
            val gson = Gson()
            val type = object : TypeToken<ArrayList<CcSendService>>() {}.type
            val sendList: ArrayList<CcSendService> = gson.fromJson(serviceListJson, type)
            val okhttpClient =
                Network.getOkhttpObj(
                    preferences.getBoolean("doh_switch", true)
                )
            for (item in sendList) {
                if (item.enabled.not()) continue
                if (item.har.log.entries.isEmpty()) {
                    Log.e("HAR", "onStartJob: " + item.name + " HAR is empty.")
                    continue
                }
                val mapper = mapOf(
                    "Title" to title,
                    "Message" to message,
                    "Code" to verificationCode
                )
                val encodeMapper = mapOf(
                    "Title" to Uri.encode(title),
                    "Message" to Uri.encode(message),
                    "Code" to Uri.encode(verificationCode)
                )
                for (entry in item.har.log.entries) {
                    val request = entry.request
                    val header: Map<String, String> =
                        request.headers.associate { it.name to it.value }
                    val uriHttp = CcSend.render(request.url, encodeMapper).toHttpUrlOrNull()!!
                    val httpUrlBuilder: HttpUrl.Builder = uriHttp.newBuilder()
                    val query: Map<String, String> =
                        request.queryString.associate { it.name to it.value }
                    for (queryItem in query) {
                        val value = CcSend.render(
                            queryItem.value,
                            encodeMapper
                        )
                        httpUrlBuilder.addQueryParameter(queryItem.key, value)
                    }
                    val body: RequestBody? = if (request.postData != null) {
                        when (val mimeType = request.postData.mimeType.toMediaTypeOrNull()) {
                            null -> {
                                Logs.writeLog(
                                    applicationContext,
                                    "The MIME type of the request is not supported."
                                )
                                continue
                            }

                            "application/x-www-form-urlencoded".toMediaTypeOrNull() -> {
                                val params: Map<String, String> =
                                    request.postData.params?.associate {
                                        it.name to CcSend.render(
                                            it.value, mapper
                                        )
                                    } ?: mapOf()
                                FormBody.Builder().apply {
                                    for (param in params) {
                                        add(param.key, param.value)
                                    }
                                }.build()
                            }

                            "application/json".toMediaTypeOrNull() -> {
                                val value = CcSend.render(
                                    request.postData.text ?: "",
                                    mapper
                                )
                                if (value.isNotEmpty()) {
                                    val jsonElement = JsonParser.parseString(value)
                                    Gson().toJson(jsonElement).toRequestBody(mimeType)
                                } else {
                                    "{}".toRequestBody(mimeType)
                                }
                            }

                            else -> {
                                Logs.writeLog(
                                    applicationContext,
                                    "The MIME type of the request is not supported."
                                )
                                continue
                            }
                        }
                    } else {
                        when (request.method) {
                            "GET" -> null
                            "POST" -> {
                                FormBody.Builder().build()
                            }

                            "PUT" -> {
                                FormBody.Builder().build()
                            }

                            else -> {
                                Logs.writeLog(
                                    applicationContext,
                                    "The request method is not supported."
                                )
                                continue
                            }
                        }
                    }

                    val sendUrl = CcSend.render(
                        httpUrlBuilder.build().toString(), encodeMapper
                    )
                    val requestObj = Request.Builder().url(sendUrl)
                        .method(request.method, body)
                    if (request.cookies.isNotEmpty()) {
                        val cookieHeader =
                            request.cookies.joinToString("; ") { "${it.name}=${it.value}" }
                        requestObj.addHeader("Cookie", cookieHeader)
                    }
                    for (item in header) {
                        requestObj.addHeader(item.key, item.value)
                    }
                    val call = okhttpClient.newCall(requestObj.build())
                    try {
                        val response = call.execute()
                        if (response.isSuccessful) {
                            Log.i(
                                "networkProgressHandle",
                                "networkProgressHandle: Message sent successfully."
                            )
                        } else {
                            Logs.writeLog(
                                applicationContext,
                                "Send message failed: " + response.code + " " + response.body.string()
                            )
                        }
                    } catch (e: IOException) {
                        Logs.writeLog(
                            applicationContext,
                            "An error occurred while resending: " + e.message
                        )
                        e.printStackTrace()
                    }
                }
            }
            if (sendList.isNotEmpty()) {
                Logs.writeLog(
                    applicationContext,
                    "The CC sending was completed, and a total of ${sendList.size} messages were sent."
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
        fun startJob(
            context: Context,
            type: Int,
            title: String,
            message: String,
            verificationCode: String
        ) {
            if (!checkType(type)) return
            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            val jobInfoBuilder = JobInfo.Builder(
                SnowFlake.generate().toString().padStart(4, '0').toInt().toInt(),
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

        fun startJob(context: Context, type: Int, title: String, message: String) {
            if (!checkType(type)) return
            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            val jobInfoBuilder = JobInfo.Builder(
                SnowFlake.generate().toString().padStart(4, '0').toInt(),
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

        private fun checkType(type: Int): Boolean {
            Log.d("checkType", "checkType: $type")
            //val ccConfig = Paper.book("carbon_copy").read("cc_config", "{}").toString()
            val carbonCopyMMKV = MMKV.mmkvWithID(MMKVConst.CARBON_COPY_ID)
            val ccConfig = carbonCopyMMKV.getString("config", "{}")
            val gson = Gson()
            val configType = object : TypeToken<CcConfig>() {}.type
            val config: CcConfig = gson.fromJson(ccConfig, configType)
            return when (type) {
                -1 -> true // For Test message
                CcType.SMS -> config.receiveSMS
                CcType.CALL -> config.missedCall
                CcType.NOTIFICATION -> config.receiveNotification
                CcType.BATTERY -> config.battery
                else -> false
            }
        }
    }
}
