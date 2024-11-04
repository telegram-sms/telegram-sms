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
import com.qwe7002.telegram_sms.static_class.log
import com.qwe7002.telegram_sms.static_class.network
import com.qwe7002.telegram_sms.static_class.other
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

@Suppress("DEPRECATION")
class CallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        Paper.init(context)
        Log.d("call_receiver", "Receive action: " + intent.action)
        when (Objects.requireNonNull(intent.action)) {
            "android.intent.action.PHONE_STATE" -> {
                if (intent.getStringExtra("incoming_number") != null) {
                    incomingNumber = intent.getStringExtra("incoming_number")
                }
                val telephony = context
                    .getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val customPhoneListener = callStatusListener(context, slot, incomingNumber)
                telephony.listen(customPhoneListener, PhoneStateListener.LISTEN_CALL_STATE)
            }

            "android.intent.action.SUBSCRIPTION_PHONE_STATE" -> slot =
                intent.getIntExtra("slot", -1)
        }
    }

    internal class callStatusListener(
        private val context: Context,
        private val slot: Int,
        incomingNumber: String?
    ) : PhoneStateListener() {
        init {
            Companion.incomingNumber = incomingNumber
        }

        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(nowState: Int, nowIncomingNumber: String) {
            if (lastReceiveStatus == TelephonyManager.CALL_STATE_RINGING
                && nowState == TelephonyManager.CALL_STATE_IDLE
            ) {
                val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
                if (!sharedPreferences.getBoolean("initialized", false)) {
                    Log.i("call_status_listener", "Uninitialized, Phone receiver is deactivated.")
                    return
                }
                val botToken = sharedPreferences.getString("bot_token", "")
                val chatId = sharedPreferences.getString("chat_id", "")
                val messageThreadId = sharedPreferences.getString("message_thread_id", "")
                val requestUri = network.getUrl(botToken, "sendMessage")
                val requestBody = RequestMessage()
                requestBody.chatId = chatId.toString()
                requestBody.messageThreadId = messageThreadId.toString()
                val dual_sim = other.getDualSimCardDisplay(
                    context,
                    slot,
                    sharedPreferences.getBoolean("display_dual_sim_display_name", false)
                )
                requestBody.text = """
                    [$dual_sim${context.getString(R.string.missed_call_head)}]
                    ${context.getString(R.string.Incoming_number)}$incomingNumber
                    """.trimIndent()
                val requestBodyRaw = Gson().toJson(requestBody)
                val body: RequestBody = requestBodyRaw.toRequestBody(constValue.JSON)
                val okhttpObj = network.getOkhttpObj(
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
                        log.writeLog(context, errorHead + e.message)
                        SMS.fallbackSMS(context, requestBody.text, other.getSubId(context, slot))
                        Resend.addResendLoop(context, requestBody.text)
                    }

                    @Throws(IOException::class)
                    override fun onResponse(call: Call, response: Response) {
                        if (response.code != 200) {
                            val errorMessage =
                                errorHead + response.code + " " + Objects.requireNonNull(response.body)
                                    .string()
                            log.writeLog(context, errorMessage)
                            Resend.addResendLoop(context, requestBody.text)
                        } else {
                            val result = Objects.requireNonNull(response.body).string()
                            if (!other.isPhoneNumber(incomingNumber!!)) {
                                log.writeLog(
                                    context,
                                    "[$incomingNumber] Not a regular phone number."
                                )
                                return
                            }
                            other.addMessageList(other.getMessageId(result), incomingNumber, slot)
                        }
                    }
                })
            }
            lastReceiveStatus = nowState
        }

        companion object {
            private var lastReceiveStatus = TelephonyManager.CALL_STATE_IDLE
            private var incomingNumber: String? = null
        }
    }

    companion object {
        private var slot = 0
        private var incomingNumber: String? = null
    }
}


