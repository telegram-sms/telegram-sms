package com.qwe7002.telegram_sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.UssdResponseCallback
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.qwe7002.telegram_sms.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_sms.static_class.Network
import com.qwe7002.telegram_sms.static_class.Resend
import com.qwe7002.telegram_sms.static_class.SMS
import com.qwe7002.telegram_sms.static_class.Template
import com.qwe7002.telegram_sms.value.Const
import com.tencent.mmkv.MMKV
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Objects

@RequiresApi(api = Build.VERSION_CODES.O)
class USSDCallBack(
    private val context: Context,
    messageId: Long
) : UssdResponseCallback() {
    private val dohSwitch: Boolean
    private var requestUri: String
    private val requestBody: RequestMessage

    init {
        val preferences = MMKV.defaultMMKV()
        val chatId = preferences.getString("chat_id", "")
        this.dohSwitch = preferences.getBoolean("doh_switch", true)
        this.requestBody = RequestMessage()
        requestBody.chatId = chatId.toString()
        requestBody.messageThreadId =
            preferences.getString("message_thread_id", "").toString()
        val botToken = preferences.getString("bot_token", "")
        this.requestUri = Network.getUrl(botToken.toString(), "SendMessage")
        if (messageId != -1L) {
            this.requestUri = Network.getUrl(botToken.toString(), "editMessageText")
            requestBody.messageId = messageId
        }
    }

    override fun onReceiveUssdResponse(
        telephonyManager: TelephonyManager,
        request: String,
        response: CharSequence
    ) {
        super.onReceiveUssdResponse(telephonyManager, request, response)
        val message = Template.render(
            context,
            "TPL_send_USSD",
            mapOf("Request" to request, "Response" to response.toString())
        )
        networkProgressHandle(message)
    }

    override fun onReceiveUssdResponseFailed(
        telephonyManager: TelephonyManager,
        request: String,
        failureCode: Int
    ) {
        super.onReceiveUssdResponseFailed(telephonyManager, request, failureCode)
        val message = Template.render(
            context,
            "TPL_send_USSD",
            mapOf("Request" to request, "Response" to getErrorCodeString(failureCode))
        )
        networkProgressHandle(message)
    }

    private fun networkProgressHandle(message: String) {
        requestBody.text = message
        val requestBodyJson = Gson().toJson(requestBody)
        val body: RequestBody = requestBodyJson.toRequestBody(Const.JSON)
        val okhttpClient = Network.getOkhttpObj(
            dohSwitch
        )
        val requestObj: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(requestObj)
        val errorHead = "Send USSD failed:"
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                Log.e(Const.TAG, errorHead + e.message)
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.SEND_SMS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    SMS.fallbackSMS( requestBody.text, -1)
                }

                Resend.addResendLoop(context, requestBody.text)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (response.code != 200) {
                    Log.e(
                        Const.TAG,
                        errorHead + response.code + " " + Objects.requireNonNull(response.body)
                            .string()
                    )
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.SEND_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        SMS.fallbackSMS( requestBody.text, -1)
                    }
                    Resend.addResendLoop(context, requestBody.text)
                }
            }
        })
    }

    private fun getErrorCodeString(errorCode: Int): String {
        val result = when (errorCode) {
            -1 -> "Connection problem or invalid MMI code."
            -2 -> "No service."
            else -> "An unknown error occurred ($errorCode)"
        }
        return result
    }
}
