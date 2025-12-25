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
import com.qwe7002.telegram_sms.data_structure.Entry
import com.qwe7002.telegram_sms.data_structure.Request as HarRequest
import com.qwe7002.telegram_sms.static_class.CcSend
import com.qwe7002.telegram_sms.static_class.Network
import com.qwe7002.telegram_sms.static_class.SnowFlake
import com.qwe7002.telegram_sms.value.CcType
import com.qwe7002.telegram_sms.value.Const
import com.tencent.mmkv.MMKV
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.Executors

class CcSendJob : JobService() {
    
    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_VERIFICATION_CODE = "verification_code"
        
        private val gson = Gson()
        private val executor = Executors.newSingleThreadExecutor()
        
        private val FORM_URLENCODED_TYPE = "application/x-www-form-urlencoded".toMediaTypeOrNull()
        private val JSON_TYPE = "application/json".toMediaTypeOrNull()
        
        fun startJob(
            context: Context,
            type: Int,
            title: String,
            message: String,
            verificationCode: String = ""
        ) {
            if (!checkType(type)) return
            
            val jobScheduler = context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            val jobId = SnowFlake.generate().toString().takeLast(9).toIntOrNull() ?: 0
            
            val extras = PersistableBundle().apply {
                putString(EXTRA_TITLE, title)
                putString(EXTRA_MESSAGE, message)
                if (verificationCode.isNotEmpty()) {
                    putString(EXTRA_VERIFICATION_CODE, verificationCode)
                }
            }
            
            val jobInfo = JobInfo.Builder(
                jobId,
                ComponentName(context.packageName, CcSendJob::class.java.name)
            )
                .setPersisted(true)
                .setExtras(extras)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build()
            
            jobScheduler.schedule(jobInfo)
        }

        private fun checkType(type: Int): Boolean {
            Log.d(Const.TAG, "checkType: $type")
            val carbonCopyMMKV = MMKV.mmkvWithID(MMKVConst.CARBON_COPY_ID)
            val ccConfig = carbonCopyMMKV.getString("config", "{}") ?: "{}"
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
    
    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(Const.TAG, "startJob: Trying to send message.")
        
        val extras = params?.extras ?: return false
        val defaultTitle = getString(R.string.app_name)
        val message = extras.getString(EXTRA_MESSAGE, "") ?: ""
        var title = extras.getString(EXTRA_TITLE, defaultTitle) ?: defaultTitle
        var verificationCode = extras.getString(EXTRA_VERIFICATION_CODE, "") ?: ""
        
        if (verificationCode.isEmpty()) {
            verificationCode = message
        } else {
            title += getString(R.string.verification_code)
        }
        
        val finalTitle = title
        val finalVerificationCode = verificationCode
        
        executor.execute {
            processSendJob(finalTitle, message, finalVerificationCode)
            jobFinished(params, false)
        }
        
        return true
    }
    
    override fun onStopJob(params: JobParameters?): Boolean = false
    
    private fun processSendJob(title: String, message: String, verificationCode: String) {
        MMKV.initialize(applicationContext)
        val preferences = MMKV.defaultMMKV()
        val carbonCopyMMKV = MMKV.mmkvWithID(MMKVConst.CARBON_COPY_ID)
        
        val sendList = getSendList(carbonCopyMMKV)
        if (sendList.isEmpty()) return
        
        val enabledList = sendList.filter { it.enabled }
        if (enabledList.isEmpty()) return
        
        val okhttpClient = Network.getOkhttpObj(preferences.getBoolean("doh_switch", true))
        val mapper = createMapper(title, message, verificationCode, encoded = false)
        val encodeMapper = createMapper(title, message, verificationCode, encoded = true)
        
        var successCount = 0
        for (item in enabledList) {
            if (item.har.log.entries.isEmpty()) {
                Log.e(Const.TAG, "onStartJob: ${item.name} HAR is empty.")
                continue
            }
            
            for (entry in item.har.log.entries) {
                if (sendRequest(entry, okhttpClient, mapper, encodeMapper)) {
                    successCount++
                }
            }
        }
        
        Log.i(Const.TAG, "CC sending completed. Success: $successCount/${enabledList.size}")
    }
    
    private fun getSendList(mmkv: MMKV): List<CcSendService> {
        val serviceListJson = mmkv.getString("service", "[]") ?: "[]"
        val type = object : TypeToken<ArrayList<CcSendService>>() {}.type
        return gson.fromJson(serviceListJson, type) ?: emptyList()
    }
    
    private fun createMapper(
        title: String,
        message: String,
        verificationCode: String,
        encoded: Boolean
    ): Map<String, String> {
        return if (encoded) {
            mapOf(
                "Title" to Uri.encode(title),
                "Message" to Uri.encode(message),
                "Code" to Uri.encode(verificationCode)
            )
        } else {
            mapOf(
                "Title" to title,
                "Message" to message,
                "Code" to verificationCode
            )
        }
    }
    
    private fun sendRequest(
        entry: Entry,
        client: OkHttpClient,
        mapper: Map<String, String>,
        encodeMapper: Map<String, String>
    ): Boolean {
        val request = entry.request
        
        val httpUrl = CcSend.render(request.url, encodeMapper).toHttpUrlOrNull() ?: run {
            Log.e(Const.TAG, "Invalid URL: ${request.url}")
            return false
        }
        
        val httpUrlBuilder = httpUrl.newBuilder().apply {
            request.queryString.forEach { query ->
                val value = CcSend.render(query.value, encodeMapper)
                addQueryParameter(query.name, value)
            }
        }
        
        val body = buildRequestBody(request, mapper) ?: run {
            if (request.postData != null || request.method !in listOf("GET", "POST", "PUT")) {
                return false
            }
            getDefaultBody(request.method)
        }
        
        val sendUrl = CcSend.render(httpUrlBuilder.build().toString(), encodeMapper)
        
        val requestBuilder = Request.Builder()
            .url(sendUrl)
            .method(request.method, body)
        
        // Add cookies
        if (request.cookies.isNotEmpty()) {
            val cookieHeader = request.cookies.joinToString("; ") { "${it.name}=${it.value}" }
            requestBuilder.addHeader("Cookie", cookieHeader)
        }
        
        // Add headers
        request.headers.forEach { header ->
            requestBuilder.addHeader(header.name, header.value)
        }
        
        return executeRequest(client, requestBuilder.build())
    }
    
    private fun buildRequestBody(
        request: HarRequest,
        mapper: Map<String, String>
    ): RequestBody? {
        val postData = request.postData ?: return null
        return when (val mimeType = postData.mimeType.toMediaTypeOrNull()) {
            null -> {
                Log.w(Const.TAG, "MIME type is null or invalid: ${postData.mimeType}")
                null
            }
            
            FORM_URLENCODED_TYPE -> {
                FormBody.Builder().apply {
                    postData.params?.forEach { param ->
                        add(param.name, CcSend.render(param.value, mapper))
                    }
                }.build()
            }
            
            JSON_TYPE -> {
                val value = CcSend.renderForJson(postData.text ?: "", mapper)
                if (value.isNotEmpty()) {
                    try {
                        val jsonElement = JsonParser.parseString(value)
                        gson.toJson(jsonElement).toRequestBody(mimeType)
                    } catch (e: Exception) {
                        Log.e(Const.TAG, "Failed to parse JSON: ${e.message}")
                        "{}".toRequestBody(mimeType)
                    }
                } else {
                    "{}".toRequestBody(mimeType)
                }
            }
            
            else -> {
                Log.w(Const.TAG, "Unsupported MIME type: ${postData.mimeType}")
                null
            }
        }
    }
    
    private fun getDefaultBody(method: String): RequestBody? {
        return when (method) {
            "GET" -> null
            "POST", "PUT" -> FormBody.Builder().build()
            else -> {
                Log.w(Const.TAG, "Unsupported request method: $method")
                null
            }
        }
    }
    
    private fun executeRequest(client: OkHttpClient, request: Request): Boolean {
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(Const.TAG, "Message sent successfully.")
                    true
                } else {
                    Log.e(Const.TAG, "Send message failed: ${response.code} ${response.body.string()}")
                    false
                }
            }
        } catch (e: IOException) {
            Log.e(Const.TAG, "An error occurred while sending: ${e.message}", e)
            false
        }
    }
}
