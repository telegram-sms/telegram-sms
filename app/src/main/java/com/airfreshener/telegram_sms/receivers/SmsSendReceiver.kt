package com.airfreshener.telegram_sms.receivers

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.utils.ContextUtils.app
import com.airfreshener.telegram_sms.utils.ResendUtils
import com.airfreshener.telegram_sms.utils.SmsUtils

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
        telegramRepository.sendMessage(
            message = message,
            messageId = extras.getLong("message_id"),
            onFailure = {
                SmsUtils.sendFallbackSms(context, message, sub)
                ResendUtils.addResendLoop(context, message)
            }
        )
    }

    companion object {
        private const val TAG = "SmsSendReceiver"
    }
}
