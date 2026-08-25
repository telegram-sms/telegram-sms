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
import com.google.gson.reflect.TypeToken
import com.qwe7002.telegram_sms.MMKV.CARBON_COPY_ID
import com.qwe7002.telegram_sms.data_structure.config.CcConfig
import com.qwe7002.telegram_sms.data_structure.CcSendService
import com.qwe7002.telegram_sms.data_structure.Entry
import com.qwe7002.telegram_sms.static_class.CcRequest
import com.qwe7002.telegram_sms.static_class.HarImport
import com.qwe7002.telegram_sms.static_class.JobIds
import com.qwe7002.telegram_sms.static_class.Network
import com.qwe7002.telegram_sms.value.CcType
import com.qwe7002.telegram_sms.value.TAG
import com.tencent.mmkv.MMKV
import okhttp3.OkHttpClient
import okhttp3.Request
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
        val stored: List<CcSendService> = gson.fromJson(serviceListJson, type) ?: emptyList()
        // Services saved before HAR validation existed can carry a capture Gson filled with
        // nulls; dropping them here keeps one bad entry from aborting every other delivery.
        return stored.filter { service ->
            val reason = HarImport.validate(service.har)
            if (reason != null) {
                Log.e(logTag, "ccSendJob: skipping ${service.name}, unusable HAR: $reason")
            }
            reason == null
        }
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
        // Request construction lives in CcRequest so it can be unit tested off-device;
        // null means the entry could not produce a sendable request and was already logged.
        val request = CcRequest.build(entry, mapper, encodeMapper) ?: return false
        return executeRequest(client, request)
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
