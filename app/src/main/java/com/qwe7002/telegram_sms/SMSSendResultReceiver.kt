package com.qwe7002.telegram_sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import com.qwe7002.telegram_sms.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_sms.static_class.TelegramApi
import com.qwe7002.telegram_sms.value.Const
import com.tencent.mmkv.MMKV

class SMSSendResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(Const.TAG, "Receive action: " + intent.action)
        val extras = intent.extras!!
        val sub = extras.getInt("sub_id")
        val sharedPreferences = MMKV.defaultMMKV()
        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(this::class.simpleName, "Uninitialized, SMS send receiver is deactivated.")
            return
        }
        context.applicationContext.unregisterReceiver(this)

        val requestBody = RequestMessage()
        var method = "sendMessage"
        val messageId = extras.getLong("message_id")
        if (messageId != -1L) {
            Log.d(Const.TAG, "Find the message_id and switch to edit mode.")
            Log.d(Const.TAG, "onReceive: $messageId")
            method = "editMessageText"
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
        requestBody.text =
            extras.getString("message_text") + "\n" + """${context.getString(R.string.status)}$resultStatus""".trimIndent()

        TelegramApi.sendMessage(
            context = context,
            requestBody = requestBody,
            method = method,
            errorTag = "SMSSendResultReceiver",
            fallbackSubId = sub
        ) { result ->
            Log.d("SMSSendResultReceiver", "onResponse: $result")
        }
    }
}
