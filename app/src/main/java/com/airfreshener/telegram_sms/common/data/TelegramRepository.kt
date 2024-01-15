package com.airfreshener.telegram_sms.common.data

import android.content.Context
import android.util.Log
import com.airfreshener.telegram_sms.model.ReplyMarkupKeyboard
import com.airfreshener.telegram_sms.model.RequestMessage
import com.airfreshener.telegram_sms.utils.NetworkUtils
import com.airfreshener.telegram_sms.utils.OkHttpUtils.toRequestBody
import com.airfreshener.telegram_sms.utils.OtherUtils
import com.airfreshener.telegram_sms.utils.ResendUtils
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException

class TelegramRepository(
    private val appContext: Context,
    private val prefsRepository: PrefsRepository,
    private val logRepository: LogRepository,
) {

    fun sendMessage(
        message: String,
        messageId: Long? = null,
        parseMode: String? = null,
        resendOnFail: Boolean = false,
        keyboardMarkup: ReplyMarkupKeyboard.KeyboardMarkup? = null,
        onSuccess: ((messageId: Long) -> Unit)? = null,
        onFailure: (() -> Unit)? = null,
    ) = doRequest(
        message = message,
        messageId = messageId,
        parseMode = parseMode,
        resendOnFail = resendOnFail,
        keyboardMarkup = keyboardMarkup,
        functionName = if (messageId == null || messageId == -1L) {
            TelegramApiMethods.SEND_MESSAGE
        } else {
            TelegramApiMethods.EDIT_MESSAGE_TEXT
        },
        onSuccess = onSuccess,
        onFailure = onFailure,
    )

    fun sendMessageSync(
        message: String,
        messageId: Long? = null,
        parseMode: String? = null,
        keyboardMarkup: ReplyMarkupKeyboard.KeyboardMarkup? = null,
    ) = doRequestSync(
        message = message,
        messageId = messageId,
        parseMode = parseMode,
        keyboardMarkup = keyboardMarkup,
        functionName = if (messageId == null || messageId == -1L) {
            TelegramApiMethods.SEND_MESSAGE
        } else {
            TelegramApiMethods.EDIT_MESSAGE_TEXT
        },
    )

    private fun doRequestSync(
        message: String,
        messageId: Long?,
        parseMode: String?,
        keyboardMarkup: ReplyMarkupKeyboard.KeyboardMarkup?,
        functionName: TelegramApiMethods,
    ): Long? {
        var outMessageId: Long? = null
        val settings = prefsRepository.getSettings()
        val requestBody = RequestMessage().apply {
            chat_id = settings.chatId
            text = message
            parse_mode = parseMode
            message_id = messageId ?: -1L
            reply_markup = keyboardMarkup
        }.toRequestBody()
        val requestUri = getUrl(settings.botToken, functionName.value)
        val requestObj: Request = Request.Builder().url(requestUri).post(requestBody).build()
        val call = NetworkUtils.getOkhttpObj(settings).newCall(requestObj)
        var response: Response? = null
        var body: ResponseBody? = null
        try {
            response = call.execute()
            body = response.body
            val bodyStr = body?.string()
            val responseMessageId = OtherUtils.getMessageId(bodyStr)
            if (response.code != 200 || body == null || responseMessageId == null) {
                Log.d(TAG, "Failed to send message: $bodyStr")
                logRepository.writeLog("Failed to send message: $bodyStr")
                return null
            }
            outMessageId = responseMessageId
        } catch (e: Exception) {
            val errorMessage = e.message
            Log.d(TAG, "Failed to send message:: $errorMessage")
            e.printStackTrace()
            logRepository.writeLog("Failed to send message:$errorMessage")

        } finally {
            runCatching { body?.close() }
            runCatching { response?.close() }
        }
        return outMessageId
    }

    private fun doRequest(
        message: String,
        messageId: Long?,
        parseMode: String?,
        resendOnFail: Boolean,
        keyboardMarkup: ReplyMarkupKeyboard.KeyboardMarkup?,
        functionName: TelegramApiMethods,
        onSuccess: ((messageId: Long) -> Unit)?,
        onFailure: (() -> Unit)?,
    ) {
        val settings = prefsRepository.getSettings()
        val requestBody = RequestMessage().apply {
            chat_id = settings.chatId
            text = message
            parse_mode = parseMode
            message_id = messageId ?: -1L
            reply_markup = keyboardMarkup
        }.toRequestBody()
        val requestUri = getUrl(settings.botToken, functionName.value)
        val requestObj: Request = Request.Builder().url(requestUri).post(requestBody).build()
        val call = NetworkUtils.getOkhttpObj(settings).newCall(requestObj)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val errorMessage = e.message
                Log.d(TAG, "Failed to send message:: $errorMessage")
                e.printStackTrace()
                logRepository.writeLog("Failed to send message:$errorMessage")
                if (resendOnFail) ResendUtils.addResendLoop(appContext, message)
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
                val responseMessageId = OtherUtils.getMessageId(bodyStr)
                if (code != 200 || body == null || responseMessageId == null) {
                    Log.d(TAG, "Failed to send message: $bodyStr")
                    logRepository.writeLog("Failed to send message: $bodyStr")
                    if (resendOnFail) ResendUtils.addResendLoop(appContext, message)
                    onFailure?.invoke()
                } else {
                    Log.d(TAG, "MessageId: $responseMessageId")
                    onSuccess?.invoke(responseMessageId)
                }
                try {
                    body?.close()
                    response.close()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        })
    }

    private fun getUrl(token: String, func: String): String = "${TELEGRAM_API_URL}$token/$func"

    companion object {
        private val TAG = TelegramRepository::class.java.simpleName
        private const val TELEGRAM_API_URL = "https://api.telegram.org/bot"
        private enum class TelegramApiMethods(val value: String) {
            SEND_MESSAGE("sendMessage"),
            EDIT_MESSAGE_TEXT("editMessageText")
        }
    }
}
