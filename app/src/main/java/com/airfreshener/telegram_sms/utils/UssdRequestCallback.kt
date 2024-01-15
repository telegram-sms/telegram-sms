package com.airfreshener.telegram_sms.utils

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.UssdResponseCallback
import androidx.annotation.RequiresApi
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.utils.ContextUtils.app

@RequiresApi(api = Build.VERSION_CODES.O)
class UssdRequestCallback(
    private val context: Context,
    private val messageId: Long,
) : UssdResponseCallback() {
    private val messageHeader: String = context.getString(R.string.send_ussd_head)
    private val telegramRepository by lazy { context.app().telegramRepository }

    override fun onReceiveUssdResponse(
        telephonyManager: TelephonyManager,
        request: String,
        response: CharSequence
    ) {
        super.onReceiveUssdResponse(telephonyManager, request, response)
        val message = messageHeader + "\n" +
            context.getString(R.string.request) + request + "\n" +
            context.getString(R.string.content) + response
        networkProgressHandle(message)
    }

    override fun onReceiveUssdResponseFailed(
        telephonyManager: TelephonyManager,
        request: String,
        failureCode: Int
    ) {
        super.onReceiveUssdResponseFailed(telephonyManager, request, failureCode)
        val message = messageHeader + "\n" +
            context.getString(R.string.request) + request + "\n" +
            context.getString(R.string.error_message) + getErrorCodeString(failureCode)
        networkProgressHandle(message)
    }

    private fun networkProgressHandle(message: String) {
        telegramRepository.sendMessage(
            message = message,
            messageId = messageId,
            onFailure = {
                SmsUtils.sendFallbackSms(context, message, -1)
                ResendUtils.addResendLoop(context, message)
            }
        )
    }

    private fun getErrorCodeString(errorCode: Int): String {
        val result: String = when (errorCode) {
            -1 -> "Connection problem or invalid MMI code."
            -2 -> "No service."
            else -> "An unknown error occurred ($errorCode)"
        }
        return result
    }
}
