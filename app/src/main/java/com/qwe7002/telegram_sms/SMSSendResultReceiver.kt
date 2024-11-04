package com.qwe7002.telegram_sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import com.google.gson.Gson
import com.qwe7002.telegram_sms.config.proxy
import com.qwe7002.telegram_sms.data_structure.RequestMessage
import com.qwe7002.telegram_sms.static_class.log
import com.qwe7002.telegram_sms.static_class.network
import com.qwe7002.telegram_sms.static_class.Resend
import com.qwe7002.telegram_sms.static_class.SMS
import com.qwe7002.telegram_sms.value.constValue
import io.paperdb.Paper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Objects

class SMSSendResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Paper.init(context)
        val TAG = "sms_send_receiver"
        Log.d(TAG, "Receive action: " + intent.action)
        val extras = intent.extras!!
        val sub = extras.getInt("sub_id")
        context.applicationContext.unregisterReceiver(this)
        val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(TAG, "Uninitialized, SMS send receiver is deactivated.")
            return
        }
        val botToken = sharedPreferences.getString("bot_token", "")
        val chatId = sharedPreferences.getString("chat_id", "")
        val messageThreadId = sharedPreferences.getString("message_thread_id", "")
        val requestBody = RequestMessage()
        requestBody.chatId = chatId.toString()

        requestBody.messageThreadId = messageThreadId.toString()
        var requestUri = network.getUrl(botToken, "sendMessage")
        val messageId = extras.getLong("message_id")
        if (messageId != -1L) {
            Log.d(TAG, "Find the message_id and switch to edit mode.")
            Log.d(TAG, "onReceive: $messageId")
            requestUri = network.getUrl(botToken, "editMessageText")
            requestBody.messageId = messageId
        }
        var resultStatus = "Unknown"
        when (resultCode) {
            Activity.RESULT_OK -> resultStatus = context.getString(R.string.success)
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> resultStatus =
                context.getString(R.string.send_failed)

            SmsManager.RESULT_ERROR_RADIO_OFF -> resultStatus =
                context.getString(R.string.airplane_mode)

            SmsManager.RESULT_ERROR_NO_SERVICE -> resultStatus =
                context.getString(R.string.no_network)
        }
        requestBody.text = extras.getString("message_text")+"\n"+"""${context.getString(R.string.status)}$resultStatus""".trimIndent()
        val requestBodyRaw = Gson().toJson(requestBody)
        val body: RequestBody = requestBodyRaw.toRequestBody(constValue.JSON)
        val okhttpClient = network.getOkhttpObj(
            sharedPreferences.getBoolean("doh_switch", true),
            Paper.book("system_config").read("proxy_config", proxy())
        )
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(request)
        val errorHead = "Send SMS status failed:"
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                log.writeLog(context, errorHead + e.message)
                SMS.fallbackSMS(context, requestBody.text, sub)
                Resend.addResendLoop(context, requestBody.text)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (response.code != 200) {
                    log.writeLog(
                        context,
                        errorHead + response.code + " " + Objects.requireNonNull(response.body)
                            .string()
                    )
                    Resend.addResendLoop(context, requestBody.text)
                }
                Log.d(TAG, "onResponse: "+response.body.string())
            }
        })
    }
}
