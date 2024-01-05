package com.airfreshener.telegram_sms.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.PermissionChecker
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.model.RequestMessage
import com.airfreshener.telegram_sms.receivers.SmsSendReceiver
import com.airfreshener.telegram_sms.utils.NetworkUtils.getOkhttpObj
import com.airfreshener.telegram_sms.utils.NetworkUtils.getUrl
import com.airfreshener.telegram_sms.utils.OkHttpUtils.toRequestBody
import com.airfreshener.telegram_sms.utils.OtherUtils.getDualSimCardDisplay
import com.airfreshener.telegram_sms.utils.OtherUtils.getMessageId
import com.airfreshener.telegram_sms.utils.OtherUtils.isPhoneNumber
import com.airfreshener.telegram_sms.utils.PaperUtils.getProxyConfig
import okhttp3.Request
import java.io.IOException

object SmsUtils {
    @JvmStatic
    fun sendSms(context: Context, sendTo: String, content: String, slot: Int, subId: Int) {
        sendSms(context, sendTo, content, slot, subId, -1)
    }

    @JvmStatic
    @SuppressLint("UnspecifiedImmutableFlag")
    fun sendSms(
        context: Context,
        sendTo: String,
        content: String,
        slot: Int,
        subId: Int,
        messageId: Long
    ) {
        var messageId = messageId
        val tag = "send_sms"
        val appContext = context.applicationContext
        if (PermissionChecker.checkSelfPermission(
                appContext,
                Manifest.permission.SEND_SMS
            ) != PermissionChecker.PERMISSION_GRANTED
        ) {
            Log.d(tag, "No permission.")
            return
        }
        if (!isPhoneNumber(sendTo)) {
            LogUtils.writeLog(appContext, "[$sendTo] is an illegal phone number")
            return
        }
        val sharedPreferences = appContext.getSharedPreferences("data", Context.MODE_PRIVATE)
        val botToken = sharedPreferences.getString("bot_token", "")
        val chatId = sharedPreferences.getString("chat_id", "")
        var requestUri = getUrl(botToken!!, "sendMessage")
        if (messageId != -1L) {
            Log.d(tag, "Find the message_id and switch to edit mode.")
            requestUri = getUrl(botToken, "editMessageText")
        }
        val requestBody = RequestMessage()
        requestBody.chat_id = chatId
        val smsManager: SmsManager = if (subId == -1) {
            SmsManager.getDefault()
        } else {
            SmsManager.getSmsManagerForSubscriptionId(subId)
        }
        val dualSim = getDualSimCardDisplay(
            appContext,
            slot,
            sharedPreferences.getBoolean("display_dual_sim_display_name", false)
        )
        val sendContent = """
            [$dualSim${appContext.getString(R.string.send_sms_head)}]
            ${appContext.getString(R.string.to)}$sendTo
            ${appContext.getString(R.string.content)}$content
            """.trimIndent()
        requestBody.text = """
            $sendContent
            ${appContext.getString(R.string.status)}${appContext.getString(R.string.sending)}
            """.trimIndent()
        requestBody.message_id = messageId
        val body = requestBody.toRequestBody()
        val okHttpClient = getOkhttpObj(
            sharedPreferences.getBoolean("doh_switch", true),
            getProxyConfig()
        )
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okHttpClient.newCall(request)
        try {
            call.execute().use { response ->
                val responseBody = response.body
                if (response.code != 200 || responseBody == null) {
                    throw IOException(response.code.toString())
                }
                if (messageId == -1L) {
                    messageId = getMessageId(responseBody.string())
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            LogUtils.writeLog(appContext, "failed to send message:" + e.message)
        }
        val divideContents = smsManager.divideMessage(content)
        val sendReceiverList = ArrayList<PendingIntent>()
        val filter = IntentFilter("send_sms")
        val receiver: BroadcastReceiver = SmsSendReceiver()
        appContext.registerReceiver(receiver, filter)
        val sentIntent = Intent("send_sms")
        sentIntent.putExtra("message_id", messageId)
        sentIntent.putExtra("message_text", sendContent)
        sentIntent.putExtra("sub_id", smsManager.subscriptionId)
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_CANCEL_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(appContext, 0, sentIntent, flag)
        sendReceiverList.add(pendingIntent)
        smsManager.sendMultipartTextMessage(sendTo, null, divideContents, sendReceiverList, null)
    }

    @JvmStatic
    fun sendFallbackSms(context: Context, content: String?, subId: Int) {
        val tag = "send_fallback_sms"
        if (PermissionChecker.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) != PermissionChecker.PERMISSION_GRANTED
        ) {
            Log.d(tag, "No permission.")
            return
        }
        val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
        val trustedPhoneNumber = sharedPreferences.getString("trusted_phone_number", null)
        if (trustedPhoneNumber == null) {
            Log.i(tag, "The trusted number is empty.")
            return
        }
        if (!sharedPreferences.getBoolean("fallback_sms", false)) {
            Log.i(tag, "SMS fallback is not turned on.")
            return
        }
        val smsManager: SmsManager = if (subId == -1) {
            SmsManager.getDefault()
        } else {
            SmsManager.getSmsManagerForSubscriptionId(subId)
        }
        val divideContents = smsManager.divideMessage(content)
        smsManager.sendMultipartTextMessage(trustedPhoneNumber, null, divideContents, null, null)
    }
}
