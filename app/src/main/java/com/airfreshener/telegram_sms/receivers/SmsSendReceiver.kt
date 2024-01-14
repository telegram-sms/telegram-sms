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
        val telegramRepository = app.telegramRepository
        Log.d(TAG, "Receive action: " + intent.action)
        context.applicationContext.unregisterReceiver(this)
        val extras = intent.extras
        if (extras == null) {
            Log.i(TAG, "Received extras is null")
            return
        }
        val sub = extras.getInt("sub_id")
        if (!prefsRepository.getInitialized()) {
            Log.i(TAG, "Uninitialized, SMS send receiver is deactivated.")
            return
        }
        val resultStatus = when (resultCode) {
            Activity.RESULT_OK -> context.getString(R.string.success)
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> context.getString(R.string.send_failed)
            SmsManager.RESULT_ERROR_RADIO_OFF -> context.getString(R.string.airplane_mode)
            SmsManager.RESULT_ERROR_NO_SERVICE -> context.getString(R.string.no_network)
            else -> "Unknown"
        }
        val message = extras.getString("message_text") + "\n" +
                context.getString(R.string.status) + resultStatus
        val messageId = extras.getLong("message_id")
        if (messageId == -1L) {
            telegramRepository.sendMessage(
                message = message,
                onFailure = {
                    SmsUtils.sendFallbackSms(context, message, sub)
                    ResendUtils.addResendLoop(context, message)
                }
            )
        } else {
            telegramRepository.editMessage(
                message = message,
                messageId = messageId,
                onFailure = {
                    SmsUtils.sendFallbackSms(context, message, sub)
                    ResendUtils.addResendLoop(context, message)
                }
            )
        }
    }

    companion object {
        private const val TAG = "SmsSendReceiver"
    }
}
