package com.qwe7002.telegram_sms.static_class

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.qwe7002.telegram_sms.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_sms.value.Const
import com.tencent.mmkv.MMKV
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * Unified Telegram API utility class for sending messages.
 * Abstracts common network request patterns used across Receivers and Services.
 */
@Suppress("unused")
object TelegramApi {
    private const val TAG = "TelegramApi"
    private val gson = Gson()

    /**
     * Data class for media attachments (photo, audio, video)
     */
    data class MediaData(
        val fileName: String,
        val contentType: String,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as MediaData
            return fileName == other.fileName && contentType == other.contentType && data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = fileName.hashCode()
            result = 31 * result + contentType.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    /**
     * Send a text message to Telegram asynchronously.
     *
     * @param context Android context
     * @param requestBody The RequestMessage object containing chat_id, text, etc.
     * @param method Telegram API method (default: "sendMessage", can be "editMessageText")
     * @param errorTag Tag for error logging
     * @param fallbackSubId Subscription ID for SMS fallback (-1 to disable)
     * @param enableResend Whether to add to resend loop on failure (default: true)
     * @param onSuccess Optional callback when request succeeds, receives response body string
     */
    @JvmStatic
    @JvmOverloads
    fun sendMessage(
        context: Context,
        requestBody: RequestMessage,
        method: String = "sendMessage",
        errorTag: String = TAG,
        fallbackSubId: Int = -1,
        enableResend: Boolean = true,
        onSuccess: ((String) -> Unit)? = null
    ) {
        val preferences = MMKV.defaultMMKV()
        val botToken = preferences.getString("bot_token", "") ?: ""
        val dohSwitch = preferences.getBoolean("doh_switch", true)

        // Auto-fill chatId and messageThreadId from preferences if not initialized
        try {
            requestBody.chatId
        } catch (_: UninitializedPropertyAccessException) {
            requestBody.chatId = preferences.getString("chat_id", "") ?: ""
        }
        try {
            requestBody.messageThreadId
        } catch (_: UninitializedPropertyAccessException) {
            requestBody.messageThreadId = preferences.getString("message_thread_id", "") ?: ""
        }

        val requestUri = Network.getUrl(botToken, method)
        val requestBodyJson = gson.toJson(requestBody)
        val body: RequestBody = requestBodyJson.toRequestBody(Const.JSON)
        val okhttpClient = Network.getOkhttpObj(dohSwitch)

        val request = Request.Builder()
            .url(requestUri)
            .method("POST", body)
            .build()

        val call = okhttpClient.newCall(request)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                Log.e(errorTag, "Request failed: ${e.message}")
                handleFailure(context, requestBody.text, fallbackSubId, enableResend)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.body.string()
                if (response.code != 200) {
                    Log.e(errorTag, "Request error: ${response.code} $result")
                    handleFailure(context, requestBody.text, fallbackSubId, enableResend)
                } else {
                    onSuccess?.invoke(result)
                }
            }
        })
    }

    /**
     * Send a text message to Telegram synchronously.
     * Use this only when already running on a background thread.
     *
     * @param context Android context
     * @param requestBody The RequestMessage object
     * @param method Telegram API method
     * @param errorTag Tag for error logging
     * @param fallbackSubId Subscription ID for SMS fallback
     * @param enableResend Whether to add to resend loop on failure
     * @return Response body string on success, null on failure
     */
    @JvmStatic
    @JvmOverloads
    fun sendMessageSync(
        context: Context,
        requestBody: RequestMessage,
        method: String = "sendMessage",
        errorTag: String = TAG,
        fallbackSubId: Int = -1,
        enableResend: Boolean = true
    ): String? {
        val preferences = MMKV.defaultMMKV()
        val botToken = preferences.getString("bot_token", "") ?: ""
        val dohSwitch = preferences.getBoolean("doh_switch", true)

        // Auto-fill chatId and messageThreadId from preferences if not initialized
        try {
            requestBody.chatId
        } catch (_: UninitializedPropertyAccessException) {
            requestBody.chatId = preferences.getString("chat_id", "") ?: ""
        }
        try {
            requestBody.messageThreadId
        } catch (_: UninitializedPropertyAccessException) {
            requestBody.messageThreadId = preferences.getString("message_thread_id", "") ?: ""
        }

        val requestUri = Network.getUrl(botToken, method)
        val requestBodyJson = gson.toJson(requestBody)
        val body: RequestBody = requestBodyJson.toRequestBody(Const.JSON)
        val okhttpClient = Network.getOkhttpObj(dohSwitch)

        val request = Request.Builder()
            .url(requestUri)
            .method("POST", body)
            .build()

        return try {
            val response = okhttpClient.newCall(request).execute()
            val result = response.body.string()
            if (response.code == 200) {
                result
            } else {
                Log.e(errorTag, "Request error: ${response.code} $result")
                handleFailure(context, requestBody.text, fallbackSubId, enableResend)
                null
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(errorTag, "Request failed: ${e.message}")
            handleFailure(context, requestBody.text, fallbackSubId, enableResend)
            null
        }
    }

    /**
     * Send a media file (photo/audio/video) to Telegram asynchronously.
     *
     * @param context Android context
     * @param mediaType Type of media: "photo", "audio", or "video"
     * @param media MediaData containing file name, content type, and data bytes
     * @param caption Optional caption text
     * @param errorTag Tag for error logging
     * @param fallbackSubId Subscription ID for SMS fallback
     * @param enableResend Whether to add caption to resend loop on failure
     * @param onSuccess Optional callback when request succeeds
     */
    @JvmStatic
    @JvmOverloads
    fun sendMedia(
        context: Context,
        mediaType: String,
        media: MediaData,
        caption: String = "",
        errorTag: String = TAG,
        fallbackSubId: Int = -1,
        enableResend: Boolean = true,
        onSuccess: ((String) -> Unit)? = null
    ) {
        val preferences = MMKV.defaultMMKV()
        val botToken = preferences.getString("bot_token", "") ?: ""
        val chatId = preferences.getString("chat_id", "") ?: ""
        val messageThreadId = preferences.getString("message_thread_id", "") ?: ""
        val dohSwitch = preferences.getBoolean("doh_switch", true)

        val method = when (mediaType.lowercase()) {
            "photo" -> "sendPhoto"
            "audio" -> "sendAudio"
            "video" -> "sendVideo"
            else -> "sendDocument"
        }

        val requestUri = Network.getUrl(botToken, method)
        val okhttpClient = Network.getOkhttpObj(dohSwitch)

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart(
                mediaType.lowercase(),
                media.fileName,
                media.data.toRequestBody(media.contentType.toMediaType())
            )

        if (caption.isNotEmpty()) {
            multipartBuilder.addFormDataPart("caption", caption)
        }

        if (messageThreadId.isNotEmpty()) {
            multipartBuilder.addFormDataPart("message_thread_id", messageThreadId)
        }

        val requestBody = multipartBuilder.build()
        val request = Request.Builder()
            .url(requestUri)
            .post(requestBody)
            .build()

        val call = okhttpClient.newCall(request)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                Log.e(errorTag, "Media upload failed: ${e.message}")
                if (caption.isNotEmpty()) {
                    handleFailure(context, caption, fallbackSubId, enableResend)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.body.string()
                if (response.code != 200) {
                    Log.e(errorTag, "Media upload error: ${response.code} $result")
                    if (caption.isNotEmpty()) {
                        handleFailure(context, caption, fallbackSubId, enableResend)
                    }
                } else {
                    Log.i(errorTag, "Media uploaded successfully")
                    onSuccess?.invoke(result)
                }
            }
        })
    }

    /**
     * Handle request failure by attempting SMS fallback and adding to resend loop.
     */
    private fun handleFailure(
        context: Context,
        messageText: String,
        fallbackSubId: Int,
        enableResend: Boolean
    ) {
        // Attempt SMS fallback if permission granted and subId is valid
        if (fallbackSubId >= 0 && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            SMS.fallbackSMS(messageText, fallbackSubId)
        }

        // Add to resend loop if enabled
        if (enableResend && messageText.isNotEmpty()) {
            Resend.addResendLoop(context, messageText)
        }
    }

    /**
     * Create a RequestMessage with common fields pre-filled from preferences.
     *
     * @param text Message text content
     * @param parseMode Optional parse mode ("html", "markdown", etc.)
     * @param messageId Optional message ID for editing existing messages
     * @param disableNotification Whether to disable notification
     * @return Configured RequestMessage object
     */
    @JvmStatic
    @JvmOverloads
    fun createRequestMessage(
        text: String,
        parseMode: String? = null,
        messageId: Long? = null,
        disableNotification: Boolean = false
    ): RequestMessage {
        val preferences = MMKV.defaultMMKV()
        val chatId = preferences.getString("chat_id", "") ?: ""
        val messageThreadId = preferences.getString("message_thread_id", "") ?: ""

        return RequestMessage().apply {
            this.chatId = chatId
            this.messageThreadId = messageThreadId
            this.text = text
            parseMode?.let { this.parseMode = it }
            messageId?.let { this.messageId = it }
            this.disableNotification = disableNotification
        }
    }
}
