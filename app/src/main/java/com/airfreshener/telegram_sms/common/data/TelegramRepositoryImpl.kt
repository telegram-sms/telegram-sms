package com.airfreshener.telegram_sms.common.data

import android.util.Log
import com.airfreshener.telegram_sms.model.ReplyMarkupKeyboard
import com.airfreshener.telegram_sms.model.RequestMessage
import com.airfreshener.telegram_sms.utils.NetworkUtils
import com.airfreshener.telegram_sms.utils.OkHttpUtils.toRequestBody
import com.airfreshener.telegram_sms.utils.OtherUtils
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class TelegramRepositoryImpl(
    private val prefsRepository: PrefsRepository,
    private val logRepository: LogRepository,
) : TelegramRepository {

    override fun sendMessage(
        message: String,
        keyboardMarkup: ReplyMarkupKeyboard.KeyboardMarkup?,
        onSuccess: ((messageId: Long?) -> Unit)?,
        onFailure: (() -> Unit)?
    ) = doRequest(
        message = message,
        keyboardMarkup = keyboardMarkup,
        functionName = TelegramApiMethods.SEND_MESSAGE,
        onSuccess = onSuccess,
        onFailure = onFailure,
    )

    override fun editMessage(
        message: String,
        messageId: Long,
        onSuccess: ((messageId: Long?) -> Unit)?,
        onFailure: (() -> Unit)?
    ) = doRequest(
        message = message,
        messageId = messageId,
        functionName = TelegramApiMethods.EDIT_MESSAGE_TEXT,
        onSuccess = onSuccess,
        onFailure = onFailure,
    )


    private fun doRequest(
        message: String,
        messageId: Long? = null,
        keyboardMarkup: ReplyMarkupKeyboard.KeyboardMarkup? = null,
        functionName: TelegramApiMethods,
        onSuccess: ((messageId: Long?) -> Unit)?,
        onFailure: (() -> Unit)?
    ) {
        val settings = prefsRepository.getSettings()
        val sendSmsRequestBody = RequestMessage().apply {
            chat_id = settings.chatId
            text = message
            message_id = messageId ?: -1L
            reply_markup = keyboardMarkup
        }
        val okhttpClient = NetworkUtils.getOkhttpObj(settings)
        val requestUri = getUrl(settings.botToken, functionName.value)
        val requestBody = sendSmsRequestBody.toRequestBody()
        val requestObj: Request = Request.Builder().url(requestUri).post(requestBody).build()
        val call = okhttpClient.newCall(requestObj)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val errorMessage = e.message
                Log.d(TAG, "Failed to send message:: $errorMessage")
                e.printStackTrace()
                logRepository.writeLog("Failed to send message:$errorMessage")
                onFailure?.invoke()
            }

            override fun onResponse(call: Call, response: Response) {
                val log = if (functionName == TelegramApiMethods.EDIT_MESSAGE_TEXT) {
                    "${functionName.value}($messageId)"
                } else {
                    functionName.value
                }
                Log.d(TAG, "$log onResponse: " + response.code)
                val code = response.code
                val body = response.body
                val bodyStr = body?.string()
                if (code != 200 || body == null) {
                    Log.d(TAG, "Failed to send message: $bodyStr")
                    logRepository.writeLog("Failed to send message: $bodyStr")
                    onFailure?.invoke()
                } else {
                    val responseMessageId = OtherUtils.getMessageId(bodyStr)
                    Log.d(TAG, "MessageId: $responseMessageId")
                    onSuccess?.invoke(responseMessageId)
                }
                try {
                    body?.close()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        })
    }

    fun getUrl(token: String, func: String): String = "${TELEGRAM_API_URL}$token/$func"

    companion object {
        private val TAG = TelegramRepositoryImpl::class.java.simpleName
        private const val TELEGRAM_API_URL = "https://api.telegram.org/bot"
        private enum class TelegramApiMethods(val value: String) {
            SEND_MESSAGE("sendMessage"),
            EDIT_MESSAGE_TEXT("editMessageText")
        }
    }
}
