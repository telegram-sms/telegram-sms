package com.airfreshener.telegram_sms

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.UssdResponseCallback
import androidx.annotation.RequiresApi
import com.airfreshener.telegram_sms.model.RequestMessage
import com.airfreshener.telegram_sms.model.Settings
import com.airfreshener.telegram_sms.utils.LogUtils
import com.airfreshener.telegram_sms.utils.NetworkUtils
import com.airfreshener.telegram_sms.utils.OkHttpUtils.toRequestBody
import com.airfreshener.telegram_sms.utils.ResendUtils
import com.airfreshener.telegram_sms.utils.SmsUtils
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

@RequiresApi(api = Build.VERSION_CODES.O)
class UssdRequestCallback(
    private val context: Context,
    private val settings: Settings,
    messageId: Long
) : UssdResponseCallback() {
    private var requestUri: String
    private val messageHeader: String = context.getString(R.string.send_ussd_head)
    private val requestBody: RequestMessage = RequestMessage().apply {
        chat_id = settings.chatId
    }

    init {
        requestUri = NetworkUtils.getUrl(settings.botToken, "SendMessage")
        if (messageId != -1L) {
            requestUri = NetworkUtils.getUrl(settings.botToken, "editMessageText")
            requestBody.message_id = messageId
        }
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
        networkProgressHandle(message)
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
        networkProgressHandle(message)
    }

    private fun networkProgressHandle(message: String) {
        requestBody.text = message
        val body = requestBody.toRequestBody()
        val okHttpClient = NetworkUtils.getOkhttpObj(settings)
        val requestObj: Request = Request.Builder().url(requestUri).post(body).build()
        val call = okHttpClient.newCall(requestObj)
        val errorHead = "Send USSD failed:"
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                LogUtils.writeLog(context, errorHead + e.message)
                SmsUtils.sendFallbackSms(context, requestBody.text, -1)
                ResendUtils.addResendLoop(context, requestBody.text)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.code != 200) {
                    val responseBody = response.body ?: return
                    LogUtils.writeLog(
                        context,
                        errorHead + response.code + " " + responseBody.string()
                    )
                    SmsUtils.sendFallbackSms(context, requestBody.text, -1)
                    ResendUtils.addResendLoop(context, requestBody.text)
                }
            }
        })
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
