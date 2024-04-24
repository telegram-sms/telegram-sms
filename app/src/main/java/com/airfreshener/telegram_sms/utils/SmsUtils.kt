package com.airfreshener.telegram_sms.utils

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.PermissionChecker
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.receivers.SmsSendReceiver
import com.airfreshener.telegram_sms.utils.ContextUtils.app
import java.util.ArrayList

object SmsUtils {
    fun sendSms(context: Context, sendTo: String, content: String, slot: Int, subId: Int) {
        sendSms(context, sendTo, content, slot, subId, -1)
    }

    fun sendSms(
        context: Context,
        sendTo: String,
        content: String,
        slot: Int,
        subId: Int,
        messageId: Long
    ) {
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
        val app = context.app()
        val logRepository = app.logRepository
        val telegramRepository = app.telegramRepository
        if (!OtherUtils.isPhoneNumber(sendTo)) {
            logRepository.writeLog("[$sendTo] is an illegal phone number")
            return
        }
        val settings = app.prefsRepository.getSettings()
        val smsManager: SmsManager = if (subId == -1) {
            SmsManager.getDefault()
        } else {
            SmsManager.getSmsManagerForSubscriptionId(subId)
        }
        val dualSim = OtherUtils.getDualSimCardDisplay(appContext, slot, settings.isDisplayDualSim)
        val sendContent = "[" + dualSim + appContext.getString(R.string.send_sms_head) + "]" + "\n" +
                appContext.getString(R.string.to) + sendTo + "\n" +
                appContext.getString(R.string.content) + content
        val status = appContext.getString(R.string.status) + appContext.getString(R.string.sending)
        telegramRepository.sendMessage(
            message = sendContent + "\n" + status,
            messageId = messageId,
            onSuccess = { newMessageId ->
                val divideContents = smsManager.divideMessage(content)
                appContext.registerReceiver(SmsSendReceiver(), IntentFilter("send_sms"), Context.RECEIVER_EXPORTED)
                val sentIntent = Intent("send_sms")
                sentIntent.putExtra("message_id", newMessageId)
                sentIntent.putExtra("message_text", sendContent)
                sentIntent.putExtra("sub_id", smsManager.subscriptionId)
                val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_CANCEL_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(appContext, 0, sentIntent, flag)
                val sendReceiverList = ArrayList<PendingIntent>().apply { add(pendingIntent) }
                smsManager.sendMultipartTextMessage(sendTo, null, divideContents, sendReceiverList, null)
            }
        )
    }

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
        val settings = context.app().prefsRepository.getSettings()
        val trustedPhoneNumber = settings.trustedPhoneNumber
        if (trustedPhoneNumber.isEmpty()) {
            Log.i(tag, "The trusted number is empty.")
            return
        }
        if (!settings.isFallbackSms) {
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
