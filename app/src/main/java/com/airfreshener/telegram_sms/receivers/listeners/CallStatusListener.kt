package com.airfreshener.telegram_sms.receivers.listeners

import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.model.ProxyConfigV2
import com.airfreshener.telegram_sms.model.RequestMessage
import com.airfreshener.telegram_sms.utils.LogUtils
import com.airfreshener.telegram_sms.utils.NetworkUtils.get_okhttp_obj
import com.airfreshener.telegram_sms.utils.NetworkUtils.get_url
import com.airfreshener.telegram_sms.utils.OkHttpUtils.toRequestBody
import com.airfreshener.telegram_sms.utils.OtherUtils
import com.airfreshener.telegram_sms.utils.ResendUtils
import com.airfreshener.telegram_sms.utils.SmsUtils
import io.paperdb.Paper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.util.*

class CallStatusListener : PhoneStateListener {

    private var last_receive_status = TelephonyManager.CALL_STATE_IDLE
    private val incoming_number: String
    private val context: Context?
    private val slot: Int

    constructor(context: Context?, slot: Int, incoming_number: String?)   {
        this.context = context
        this.slot = slot
        this.incoming_number = incoming_number ?: "-"
    }

    override fun onCallStateChanged(now_state: Int, now_incoming_number: String?) {
        if (last_receive_status == TelephonyManager.CALL_STATE_RINGING
            && now_state == TelephonyManager.CALL_STATE_IDLE
        ) {
            val sharedPreferences = context!!.getSharedPreferences("data", Context.MODE_PRIVATE)
            if (!sharedPreferences.getBoolean("initialized", false)) {
                Log.i("call_status_listener", "Uninitialized, Phone receiver is deactivated.")
                return
            }
            val bot_token = sharedPreferences.getString("bot_token", "")
            val chat_id = sharedPreferences.getString("chat_id", "")
            val request_uri = get_url(bot_token!!, "sendMessage")
            val request_body = RequestMessage()
            request_body.chat_id = chat_id
            val dual_sim = OtherUtils.get_dual_sim_card_display(
                context,
                slot,
                sharedPreferences.getBoolean("display_dual_sim_display_name", false)
            )
            request_body.text = """
            [$dual_sim${context.getString(R.string.missed_call_head)}]
            ${context.getString(R.string.Incoming_number)}$incoming_number
            """.trimIndent()
            val body: RequestBody = request_body.toRequestBody()
            val okhttp_client = get_okhttp_obj(
                sharedPreferences.getBoolean("doh_switch", true),
                Paper.book("system_config").read("proxy_config", ProxyConfigV2())!!
            )
            val request: Request = Request.Builder().url(request_uri).method("POST", body).build()
            val call = okhttp_client.newCall(request)
            val error_head = "Send missed call error: "
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                    LogUtils.write_log(context, error_head + e.message)
                    SmsUtils.send_fallback_sms(
                        context,
                        request_body.text,
                        OtherUtils.get_sub_id(context, slot)
                    )
                    ResendUtils.add_resend_loop(context, request_body.text)
                }

                @Throws(IOException::class)
                override fun onResponse(call: Call, response: Response) {
                    assert(response.body != null)
                    if (response.code != 200) {
                        val error_message =
                            error_head + response.code + " " + response.body?.string().orEmpty()
                        LogUtils.write_log(context, error_message)
                        ResendUtils.add_resend_loop(context, request_body.text)
                    } else {
                        val result = response.body?.string() ?: return
                        if (!OtherUtils.is_phone_number(incoming_number)) {
                            LogUtils.write_log(
                                context,
                                "[$incoming_number] Not a regular phone number."
                            )
                            return
                        }
                        OtherUtils.add_message_list(
                            OtherUtils.get_message_id(result),
                            incoming_number,
                            slot
                        )
                    }
                }
            })
        }
        last_receive_status = now_state
    }
}
