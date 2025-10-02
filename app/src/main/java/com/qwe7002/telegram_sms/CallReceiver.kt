@file:Suppress("DEPRECATION")

package com.qwe7002.telegram_sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import com.google.gson.Gson
import com.qwe7002.telegram_sms.config.proxy
import com.qwe7002.telegram_sms.data_structure.RequestMessage
import com.qwe7002.telegram_sms.static_class.Logs
import com.qwe7002.telegram_sms.static_class.Network
import com.qwe7002.telegram_sms.static_class.Other
import com.qwe7002.telegram_sms.static_class.Resend
import com.qwe7002.telegram_sms.static_class.SMS
import com.qwe7002.telegram_sms.static_class.Template
import com.qwe7002.telegram_sms.value.CcType
import com.qwe7002.telegram_sms.value.constValue
import io.paperdb.Paper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

@Suppress("DEPRECATION")
class CallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        Paper.init(context)
        Log.d("call_receiver", "Receive action: " + intent.action)
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

        // Removed init block that used constructor's incomingNumber

        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(nowState: Int, nowIncomingNumber: String) {
            // Use nowIncomingNumber from the callback parameter directly.
            // It can be an empty string if the number is unknown.
            val actualIncomingNumber = if (nowIncomingNumber.isNullOrEmpty()) {
                Log.w("PhoneStatusListener", "Incoming number from callback is null or empty. Using 'Unknown'.")
                // Consider using a string resource: context.getString(R.string.unknown_caller_id)
                "Unknown"
            } else {
                nowIncomingNumber
            }

            if (lastReceiveStatus == TelephonyManager.CALL_STATE_IDLE && nowState == TelephonyManager.CALL_STATE_RINGING) {
                val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
                if (!sharedPreferences.getBoolean("call_notify", true)) {
                    Log.i("call_status_listener", "Call notifications are disabled by user setting.")
                    return
                }
                val botToken = sharedPreferences.getString("bot_token", "")
                val chatId = sharedPreferences.getString("chat_id", "")
                val messageThreadId = sharedPreferences.getString("message_thread_id", "")
                val requestUri = Network.getUrl(botToken.toString(), "sendMessage")
                val requestBody = RequestMessage()
                requestBody.chatId = chatId.toString()
                requestBody.messageThreadId = messageThreadId.toString()
                val dualSim = Other.getDualSimCardDisplay(
                    context,
                    slot
                )
                // Use actualIncomingNumber from the callback
                requestBody.text = Template.render(context, "TPL_receiving_call", mapOf("SIM" to dualSim, "From" to actualIncomingNumber))
                CcSendJob.startJob(context, CcType.CALL, context.getString(R.string.receiving_call_title), requestBody.text)
                val requestBodyRaw = Gson().toJson(requestBody)
                val body: RequestBody = requestBodyRaw.toRequestBody(constValue.JSON)
                val okhttpObj = Network.getOkhttpObj(
                    sharedPreferences.getBoolean("doh_switch", true),
                    Paper.book("system_config").read("proxy_config", proxy())
                )
                val request: Request =
                    Request.Builder().url(requestUri).method("POST", body).build()
                val call = okhttpObj.newCall(request)
                val errorHead = "Send receiving call error:"
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        e.printStackTrace()
                        Logs.writeLog(context, "$errorHead ${e.message}")
                        SMS.fallbackSMS(context, requestBody.text, Other.getSubId(context, slot))
                        Resend.addResendLoop(context, requestBody.text)
                    }

                    @Throws(IOException::class)
                    override fun onResponse(call: Call, response: Response) {
                        val responseBodyStr = response.body?.string()
                        if (!response.isSuccessful) {
                            val errorMessage = "$errorHead ${response.code} $responseBodyStr"
                            Logs.writeLog(context, errorMessage)
                            Resend.addResendLoop(context, requestBody.text)
                        } else {
                            // Use actualIncomingNumber from the callback
                            if (!Other.isPhoneNumber(actualIncomingNumber) && actualIncomingNumber != "Unknown") {
                                Logs.writeLog(
                                    context,
                                    "[$actualIncomingNumber] Not a regular phone number."
                                )
                                return
                            }
                            Other.addMessageList(Other.getMessageId(responseBodyStr ?: ""), actualIncomingNumber, slot)
                        }
                    }
                })
            }
            if (lastReceiveStatus == TelephonyManager.CALL_STATE_RINGING && nowState == TelephonyManager.CALL_STATE_IDLE) {
                val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
                if (!sharedPreferences.getBoolean("initialized", false)) {
                    Log.i("call_status_listener", "Uninitialized, Phone receiver is deactivated.")
                    return
                }

                val botToken = sharedPreferences.getString("bot_token", "")
                val chatId = sharedPreferences.getString("chat_id", "")
                val messageThreadId = sharedPreferences.getString("message_thread_id", "")
                val requestUri = Network.getUrl(botToken.toString(), "sendMessage")
                val requestBody = RequestMessage()
                requestBody.chatId = chatId.toString()
                requestBody.messageThreadId = messageThreadId.toString()
                val dualSim = Other.getDualSimCardDisplay(
                    context,
                    slot
                )
                // Use actualIncomingNumber from the callback
                requestBody.text = Template.render(context, "TPL_missed_call", mapOf("SIM" to dualSim, "From" to actualIncomingNumber))
                CcSendJob.startJob(context, CcType.CALL, context.getString(R.string.missed_call_title), requestBody.text)
                val requestBodyRaw = Gson().toJson(requestBody)
                val body: RequestBody = requestBodyRaw.toRequestBody(constValue.JSON)
                val okhttpObj = Network.getOkhttpObj(
                    sharedPreferences.getBoolean("doh_switch", true),
                    Paper.book("system_config").read("proxy_config", proxy())
                )
                val request: Request =
                    Request.Builder().url(requestUri).method("POST", body).build()
                val call = okhttpObj.newCall(request)
                val errorHead = "Send missed call error:"
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        e.printStackTrace()
                        Logs.writeLog(context, "$errorHead ${e.message}")
                        SMS.fallbackSMS(context, requestBody.text, Other.getSubId(context, slot))
                        Resend.addResendLoop(context, requestBody.text)
                    }

                    @Throws(IOException::class)
                    override fun onResponse(call: Call, response: Response) {
                        val responseBodyStr = response.body?.string()
                        if (!response.isSuccessful) {
                            val errorMessage = "$errorHead ${response.code} $responseBodyStr"
                            Logs.writeLog(context, errorMessage)
                            Resend.addResendLoop(context, requestBody.text)
                        } else {
                            // Use actualIncomingNumber from the callback
                            if (!Other.isPhoneNumber(actualIncomingNumber) && actualIncomingNumber != "Unknown") {
                                Logs.writeLog(
                                    context,
                                    "[$actualIncomingNumber] Not a regular phone number."
                                )
                                return
                            }
                            Other.addMessageList(Other.getMessageId(responseBodyStr ?: ""), actualIncomingNumber, slot)
                        }
                    }
                })
            }
            lastReceiveStatus = nowState
        }

        companion object {
            private var lastReceiveStatus = TelephonyManager.CALL_STATE_IDLE
            // Removed: private lateinit var incomingNumber: String
        }
    }

    companion object {
        private var slot = 0
        // Removed: private lateinit var incomingNumber: String
    }
}
