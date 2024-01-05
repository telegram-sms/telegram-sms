package com.airfreshener.telegram_sms.receivers.listeners

import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.model.RequestMessage
import com.airfreshener.telegram_sms.utils.LogUtils
import com.airfreshener.telegram_sms.utils.NetworkUtils.getOkhttpObj
import com.airfreshener.telegram_sms.utils.NetworkUtils.getUrl
import com.airfreshener.telegram_sms.utils.OkHttpUtils.toRequestBody
import com.airfreshener.telegram_sms.utils.OtherUtils
import com.airfreshener.telegram_sms.utils.PaperUtils
import com.airfreshener.telegram_sms.utils.ResendUtils
import com.airfreshener.telegram_sms.utils.SmsUtils
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException

class CallStatusListener(
    private val context: Context?,
    private val slot: Int,
    incomingNumber: String?
) : PhoneStateListener() {

    private var lastReceiveStatus = TelephonyManager.CALL_STATE_IDLE
    private val incomingNumber: String

    init {
        this.incomingNumber = incomingNumber ?: "-"
    }

    override fun onCallStateChanged(now_state: Int, now_incoming_number: String?) {
        if (lastReceiveStatus == TelephonyManager.CALL_STATE_RINGING
            && now_state == TelephonyManager.CALL_STATE_IDLE
        ) {
            val sharedPreferences = context!!.getSharedPreferences("data", Context.MODE_PRIVATE)
            if (!sharedPreferences.getBoolean("initialized", false)) {
                Log.i("call_status_listener", "Uninitialized, Phone receiver is deactivated.")
                return
            }
            val botToken = sharedPreferences.getString("bot_token", "")
            val chatId = sharedPreferences.getString("chat_id", "")
            val requestUri = getUrl(botToken!!, "sendMessage")
            val requestBody = RequestMessage()
            requestBody.chat_id = chatId
            val dual_sim = OtherUtils.getDualSimCardDisplay(
                context,
                slot,
                sharedPreferences.getBoolean("display_dual_sim_display_name", false)
            )
            requestBody.text = """
            [$dual_sim${context.getString(R.string.missed_call_head)}]
            ${context.getString(R.string.Incoming_number)}$incomingNumber
            """.trimIndent()
            val body: RequestBody = requestBody.toRequestBody()
            val okHttpClient = getOkhttpObj(
                sharedPreferences.getBoolean("doh_switch", true),
                PaperUtils.getProxyConfig()
            )
            val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
            val call = okHttpClient.newCall(request)
            val errorHead = "Send missed call error: "
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                    LogUtils.writeLog(context, errorHead + e.message)
                    SmsUtils.sendFallbackSms(
                        context,
                        requestBody.text,
                        OtherUtils.getSubId(context, slot)
                    )
                    ResendUtils.addResendLoop(context, requestBody.text)
                }

                override fun onResponse(call: Call, response: Response) {
                    assert(response.body != null)
                    if (response.code != 200) {
                        val errorMessage =
                            errorHead + response.code + " " + response.body?.string().orEmpty()
                        LogUtils.writeLog(context, errorMessage)
                        ResendUtils.addResendLoop(context, requestBody.text)
                    } else {
                        val result = response.body?.string() ?: return
                        if (!OtherUtils.isPhoneNumber(incomingNumber)) {
                            LogUtils.writeLog(
                                context,
                                "[$incomingNumber] Not a regular phone number."
                            )
                            return
                        }
                        OtherUtils.addMessageList(
                            OtherUtils.getMessageId(result),
                            incomingNumber,
                            slot
                        )
                    }
                }
            })
        }
        lastReceiveStatus = now_state
    }
}
