package com.qwe7002.telegram_sms

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.UssdResponseCallback
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.qwe7002.telegram_sms.config.proxy
import com.qwe7002.telegram_sms.data_structure.RequestMessage
import com.qwe7002.telegram_sms.static_class.log
import com.qwe7002.telegram_sms.static_class.Network
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

@RequiresApi(api = Build.VERSION_CODES.O)
class USSDCallBack(
    private val context: Context,
    sharedPreferences: SharedPreferences,
    messageId: Long
) : UssdResponseCallback() {
    private val dohSwitch: Boolean
    private var requestUri: String
    private val messageHeader: String
    private val requestBody: RequestMessage

    init {
        Paper.init(context)
        val chatId = sharedPreferences.getString("chat_id", "")
        this.dohSwitch = sharedPreferences.getBoolean("doh_switch", true)
        this.requestBody = RequestMessage()
        requestBody.chatId = chatId.toString()
        val botToken = sharedPreferences.getString("bot_token", "")
        this.requestUri = Network.getUrl(botToken.toString(), "SendMessage")
        if (messageId != -1L) {
            this.requestUri = Network.getUrl(botToken.toString(), "editMessageText")
            requestBody.messageId = messageId
        }
        this.messageHeader = context.getString(R.string.send_ussd_head)
    }

    override fun onReceiveUssdResponse(
        telephonyManager: TelephonyManager,
        request: String,
        response: CharSequence
    ) {
        super.onReceiveUssdResponse(telephonyManager, request, response)
        val message = """
            $messageHeader
            ${context.getString(R.string.request)}$request
            ${context.getString(R.string.content)}$response
            """.trimIndent()
        network_progress_handle(message)
    }

    override fun onReceiveUssdResponseFailed(
        telephonyManager: TelephonyManager,
        request: String,
        failureCode: Int
    ) {
        super.onReceiveUssdResponseFailed(telephonyManager, request, failureCode)
        val message = """
            $messageHeader
            ${context.getString(R.string.request)}$request
            ${context.getString(R.string.error_message)}${getErrorCodeString(failureCode)}
            """.trimIndent()
        network_progress_handle(message)
    }

    private fun network_progress_handle(message: String) {
        requestBody.text = message
        val requestBodyJson = Gson().toJson(requestBody)
        val body: RequestBody = requestBodyJson.toRequestBody(constValue.JSON)
        val okhttpClient = Network.getOkhttpObj(
            dohSwitch,
            Paper.book("system_config").read("proxy_config", proxy())
        )
        val requestObj: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(requestObj)
        val errorHead = "Send USSD failed:"
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                log.writeLog(context, errorHead + e.message)
                SMS.fallbackSMS(context, requestBody.text, -1)
                Resend.addResendLoop(context, requestBody.text)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (response.code != 200) {
                    log.writeLog(
                        context,
                        errorHead + response.code + " " + Objects.requireNonNull(response.body)
                            .string()
                    )
                    SMS.fallbackSMS(context, requestBody.text, -1)
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
