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
import com.qwe7002.telegram_sms.MMKV.CARBON_COPY_ID
import com.qwe7002.telegram_sms.data_structure.config.CcConfig
import com.qwe7002.telegram_sms.data_structure.CcSendService
import com.qwe7002.telegram_sms.data_structure.Entry
import com.qwe7002.telegram_sms.data_structure.Request as HarRequest
import com.qwe7002.telegram_sms.static_class.CcSend
import com.qwe7002.telegram_sms.static_class.JobIds
import com.qwe7002.telegram_sms.static_class.Network
import com.qwe7002.telegram_sms.value.CcType
import com.qwe7002.telegram_sms.value.TAG
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
        private val logTag = "${TAG}.CcSendJob"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_VERIFICATION_CODE = "verification_code"
        
        private val gson = Gson()
        private val executor = Executors.newSingleThreadExecutor()
        
        private const val FORM_URLENCODED_SUBTYPE = "application/x-www-form-urlencoded"
        private const val JSON_SUBTYPE = "application/json"
        private val SUPPORTED_METHODS = setOf("GET", "POST", "PUT")

        // Headers that must not be replayed verbatim from a captured HAR: Cookie is rebuilt from
        // request.cookies, and the rest are connection/content metadata that OkHttp or the
        // RequestBody recomputes — replaying a stale captured value duplicates or corrupts the request.
        private val SKIP_HEADERS = setOf(
            "cookie", "content-type", "content-length", "content-encoding", "host",
            "connection", "transfer-encoding", "keep-alive", "accept-encoding"
        )
        
        fun startJob(
            context: Context,
            type: Int,
            title: String,
            message: String,
            verificationCode: String = ""
        ) {
            if (!checkType(type)) return
            
            val jobScheduler = context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            val jobId = JobIds.nextCarbonCopyId()
            
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

            val carbonCopyMMKV = MMKV.mmkvWithID(CARBON_COPY_ID)
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
        Log.d(logTag, "ccSendJob: Trying to send message.")
        
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
            try {
                processSendJob(finalTitle, message, finalVerificationCode)
            } catch (e: Exception) {
                Log.e(logTag, "ccSendJob: unexpected error while sending.", e)
            } finally {
                jobFinished(params, false)
            }
        }
        
        return true
    }
    
    override fun onStopJob(params: JobParameters?): Boolean = false
    
    private fun processSendJob(title: String, message: String, verificationCode: String) {
        val preferences = MMKV.defaultMMKV()
        val carbonCopyMMKV = MMKV.mmkvWithID(CARBON_COPY_ID)
        
        val sendList = getSendList(carbonCopyMMKV)
        if (sendList.isEmpty()) return
        
        val enabledList = sendList.filter { it.enabled }
        if (enabledList.isEmpty()) return
        
        val okhttpClient = Network.getOkhttpObj(preferences.getBoolean("doh_switch", true))
        val mapper = createMapper(title, message, verificationCode, encoded = false)
        val encodeMapper = createMapper(title, message, verificationCode, encoded = true)
        
        var successCount = 0
        var attemptCount = 0
        for (item in enabledList) {
            if (item.har.log.entries.isEmpty()) {
                Log.e(logTag, "ccSendJob: ${item.name} HAR is empty.")
                continue
            }

            for (entry in item.har.log.entries) {
                attemptCount++
                if (sendRequest(entry, okhttpClient, mapper, encodeMapper)) {
                    successCount++
                }
            }
        }

        Log.i(logTag, "CC sending completed. Success: $successCount/$attemptCount")
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
            Log.e(logTag, "Invalid URL: ${request.url}")
            return false
        }
        
        // addQueryParameter percent-encodes values itself, so feed it the raw mapper
        // (encodeMapper is only for {{placeholders}} substituted directly into the URL string).
        // Browser-exported HAR carries the query in BOTH request.url and request.queryString, so only
        // append params the parsed URL doesn't already have — otherwise we get ?k=v&k=v duplicates.
        val existingParams = httpUrl.queryParameterNames
        val httpUrlBuilder = httpUrl.newBuilder().apply {
            request.queryString.forEach { query ->
                if (query.name !in existingParams) {
                    addQueryParameter(query.name, CcSend.render(query.value, mapper))
                }
            }
        }

        val body = buildRequestBody(request, mapper) ?: run {
            if (request.postData != null || request.method !in SUPPORTED_METHODS) {
                return false
            }
            getDefaultBody(request.method)
        }

        val requestBuilder = Request.Builder()
            .url(httpUrlBuilder.build())
            .method(request.method, body)
        
        // Add cookies (render placeholders so {{Code}} etc. work in cookie values too)
        if (request.cookies.isNotEmpty()) {
            val cookieHeader = request.cookies.joinToString("; ") {
                "${it.name}=${CcSend.render(it.value, mapper)}"
            }
            addHeaderSafely(requestBuilder, "Cookie", cookieHeader)
        }

        // Add headers, skipping Cookie (rebuilt above) and transport/content headers OkHttp owns,
        // so a stale captured Cookie/Content-Length/Host isn't duplicated onto the request.
        request.headers.forEach { header ->
            if (header.name.lowercase() in SKIP_HEADERS) return@forEach
            addHeaderSafely(requestBuilder, header.name, CcSend.render(header.value, mapper))
        }

        return executeRequest(client, requestBuilder.build())
    }

    // OkHttp rejects non-ASCII header names/values with IllegalArgumentException; skip the
    // offending header instead of letting it abort the whole delivery.
    private fun addHeaderSafely(builder: Request.Builder, name: String, value: String) {
        try {
            builder.addHeader(name, value)
        } catch (e: IllegalArgumentException) {
            Log.w(logTag, "Skipping invalid header '$name': ${e.message}")
        }
    }
    
    private fun buildRequestBody(
        request: HarRequest,
        mapper: Map<String, String>
    ): RequestBody? {
        val postData = request.postData ?: return null
        val mimeType = postData.mimeType.toMediaTypeOrNull() ?: run {
            Log.w(logTag, "MIME type is null or invalid: ${postData.mimeType}")
            return null
        }
        // Match on type/subtype only — a captured HAR often carries a charset
        // (e.g. "application/json; charset=utf-8") which must not break the match.
        return when ("${mimeType.type}/${mimeType.subtype}") {
            FORM_URLENCODED_SUBTYPE -> {
                FormBody.Builder().apply {
                    postData.params?.forEach { param ->
                        add(param.name, CcSend.render(param.value, mapper))
                    }
                }.build()
            }

            JSON_SUBTYPE -> {
                val value = CcSend.renderForJson(postData.text ?: "", mapper)
                if (value.isNotEmpty()) {
                    try {
                        val jsonElement = JsonParser.parseString(value)
                        gson.toJson(jsonElement).toRequestBody(mimeType)
                    } catch (e: Exception) {
                        Log.e(logTag, "Failed to parse JSON: ${e.message}")
                        "{}".toRequestBody(mimeType)
                    }
                } else {
                    "{}".toRequestBody(mimeType)
                }
            }

            else -> {
                Log.w(logTag, "Unsupported MIME type: ${postData.mimeType}")
                null
            }
        }
    }
    
    private fun getDefaultBody(method: String): RequestBody? {
        return when (method) {
            "GET" -> null
            "POST", "PUT" -> FormBody.Builder().build()
            else -> {
                Log.w(logTag, "Unsupported request method: $method")
                null
            }
        }
    }
    
    private fun executeRequest(client: OkHttpClient, request: Request): Boolean {
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(logTag, "Message sent successfully.")
                    true
                } else {
                    Log.e(logTag, "Send message failed: ${response.code} ${response.body.string()}")
                    false
                }
            }
        } catch (e: IOException) {
            Log.e(logTag, "An error occurred while sending: ${e.message}", e)
            false
        }
    }
}
