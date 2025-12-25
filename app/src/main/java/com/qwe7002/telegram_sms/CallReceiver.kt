@file:Suppress("DEPRECATION")

package com.qwe7002.telegram_sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import com.qwe7002.telegram_sms.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_sms.static_class.Other
import com.qwe7002.telegram_sms.static_class.Phone
import com.qwe7002.telegram_sms.static_class.TelegramApi
import com.qwe7002.telegram_sms.static_class.Template
import com.qwe7002.telegram_sms.value.CcType
import com.qwe7002.telegram_sms.value.Const
import com.tencent.mmkv.MMKV

@Suppress("DEPRECATION")
class CallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        MMKV.initialize(context)
        Log.d(Const.TAG, "Receive action: " + intent.action)
        // Removed local lateinit var incomingNumber

        when (intent.action) {
            "android.intent.action.PHONE_STATE" -> {
                // Removed logic that tried to initialize a local incomingNumber
                // PhoneStatusListener will get the number from its own callback

                val telephony = context
                    .getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                // PhoneStatusListener constructor no longer takes incomingNumber
                val customPhoneListener = PhoneStatusListener(context, slot)
                telephony.listen(customPhoneListener, PhoneStateListener.LISTEN_CALL_STATE)
            }

            "android.intent.action.SUBSCRIPTION_PHONE_STATE" -> slot =
                intent.getIntExtra("slot", -1)
        }
    }

    internal class PhoneStatusListener(
        private val context: Context,
        private val slot: Int
        // Removed constructor parameter: incomingNumber: String?
    ) : PhoneStateListener() {

        @RequiresPermission(allOf = [Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS, Manifest.permission.READ_PHONE_STATE])
        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(nowState: Int, nowIncomingNumber: String) {
            // Use nowIncomingNumber from the callback parameter directly.
            // It can be an empty string if the number is unknown.
            val actualIncomingNumber = nowIncomingNumber.ifEmpty {
                Log.w(
                    context::class.simpleName,
                    "Incoming number from callback is null or empty. Using 'Unknown'."
                )
                // Consider using a string resource: context.getString(R.string.unknown_caller_id)
                "Unknown"
            }

            if (lastReceiveStatus == TelephonyManager.CALL_STATE_IDLE && nowState == TelephonyManager.CALL_STATE_RINGING) {
                val preferences = MMKV.defaultMMKV()
                if (!preferences.getBoolean("call_notify", true)) {
                    Log.i(
                        context::class.simpleName,
                        "Call notifications are disabled by user setting."
                    )
                    return
                }
                val requestBody = RequestMessage()
                val dualSim = Phone.getSimDisplayName(context, slot)
                // Use actualIncomingNumber from the callback
                requestBody.text = Template.render(
                    context,
                    "TPL_receiving_call",
                    mapOf("SIM" to dualSim, "From" to actualIncomingNumber)
                )
                CcSendJob.startJob(
                    context,
                    CcType.CALL,
                    context.getString(R.string.receiving_call_title),
                    requestBody.text
                )

                TelegramApi.sendMessage(
                    context = context,
                    requestBody = requestBody,
                    fallbackSubId = Other.getSubId(context, slot)
                ) { responseBodyStr ->
                    // Use actualIncomingNumber from the callback
                    if (Other.isPhoneNumber(actualIncomingNumber) || actualIncomingNumber == "Unknown") {
                        Other.addMessageList(
                            Other.getMessageId(responseBodyStr),
                            actualIncomingNumber,
                            slot
                        )
                    } else {
                        Log.w(
                            "CallReceiver",
                            "[$actualIncomingNumber] Not a regular phone number."
                        )
                    }
                }
            }
            if (lastReceiveStatus == TelephonyManager.CALL_STATE_RINGING && nowState == TelephonyManager.CALL_STATE_IDLE) {
                val preferences = MMKV.defaultMMKV()
                if (!preferences.getBoolean("initialized", false)) {
                    Log.i(Const.TAG, "Uninitialized, Phone receiver is deactivated.")
                    return
                }

                val requestBody = RequestMessage()
                val dualSim = Phone.getSimDisplayName(context, slot)
                // Use actualIncomingNumber from the callback
                requestBody.text = Template.render(
                    context,
                    "TPL_missed_call",
                    mapOf("SIM" to dualSim, "From" to actualIncomingNumber)
                )
                CcSendJob.startJob(
                    context,
                    CcType.CALL,
                    context.getString(R.string.missed_call_title),
                    requestBody.text
                )

                TelegramApi.sendMessage(
                    context = context,
                    requestBody = requestBody,
                    fallbackSubId = Other.getSubId(context, slot)
                ) { responseBodyStr ->
                    // Use actualIncomingNumber from the callback
                    if (Other.isPhoneNumber(actualIncomingNumber) || actualIncomingNumber == "Unknown") {
                        Other.addMessageList(
                            Other.getMessageId(responseBodyStr),
                            actualIncomingNumber,
                            slot
                        )
                    } else {
                        Log.w(
                            "CallReceiver",
                            "[$actualIncomingNumber] Not a regular phone number."
                        )
                    }
                }
            }
            lastReceiveStatus = nowState
        }

        companion object {
            private var lastReceiveStatus = TelephonyManager.CALL_STATE_IDLE
        }
    }

    companion object {
        private var slot = 0
    }
}
