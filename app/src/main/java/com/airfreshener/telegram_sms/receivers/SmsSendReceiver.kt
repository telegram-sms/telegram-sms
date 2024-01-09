package com.airfreshener.telegram_sms.receivers

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.model.RequestMessage
import com.airfreshener.telegram_sms.utils.ContextUtils.app
import com.airfreshener.telegram_sms.utils.NetworkUtils
import com.airfreshener.telegram_sms.utils.OkHttpUtils.toRequestBody
import com.airfreshener.telegram_sms.utils.ResendUtils
import com.airfreshener.telegram_sms.utils.SmsUtils
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class SmsSendReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.app()
        val prefsRepository = app.prefsRepository
        val logRepository = app.logRepository
        Log.d(TAG, "Receive action: " + intent.action)
        val extras = intent.extras!!
        val sub = extras.getInt("sub_id")
        context.applicationContext.unregisterReceiver(this)
        if (!prefsRepository.getInitialized()) {
            Log.i(TAG, "Uninitialized, SMS send receiver is deactivated.")
            return
        }
        val settings = prefsRepository.getSettings()
        val botToken = settings.botToken
        val chatId = settings.chatId
        val requestBody = RequestMessage()
        requestBody.chat_id = chatId
        var requestUri = NetworkUtils.getUrl(botToken, "sendMessage")
        val messageId = extras.getLong("message_id")
        if (messageId != -1L) {
            Log.d(TAG, "Find the message_id and switch to edit mode.")
            requestUri = NetworkUtils.getUrl(botToken, "editMessageText")
            requestBody.message_id = messageId
        }
        val resultStatus = when (resultCode) {
            Activity.RESULT_OK -> context.getString(R.string.success)
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> context.getString(R.string.send_failed)
            SmsManager.RESULT_ERROR_RADIO_OFF -> context.getString(R.string.airplane_mode)
            SmsManager.RESULT_ERROR_NO_SERVICE -> context.getString(R.string.no_network)
            else -> "Unknown"
        }
        requestBody.text = """
            ${extras.getString("message_text")}
            ${context.getString(R.string.status)}$resultStatus
            """.trimIndent()
        val body = requestBody.toRequestBody()
        val okHttpClient = NetworkUtils.getOkhttpObj(settings)
        val request: Request = Request.Builder().url(requestUri).post(body).build()
        val call = okHttpClient.newCall(request)
        val errorHead = "Send SMS status failed:"
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                logRepository.writeLog(errorHead + e.message)
                SmsUtils.sendFallbackSms(context, requestBody.text, sub)
                ResendUtils.addResendLoop(context, requestBody.text)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.code != 200) {
                    assert(response.body != null)
                    logRepository.writeLog(errorHead + response.code + " " + response.body?.string())
                    ResendUtils.addResendLoop(context, requestBody.text)
                }
            }
        })
    }

    companion object {
        private const val TAG = "SmsSendReceiver"
    }
}
