package com.qwe7002.telegram_sms.data_structure.telegram

import com.google.gson.annotations.SerializedName

class RequestMessage {
    //Turn off page preview to avoid being tracked
    @Suppress("unused")
    @SerializedName(value = "disable_web_page_preview")
    val disableWebPagePreview: Boolean = true
    @SerializedName(value = "message_id")
    var messageId: Long = 0
    @SerializedName(value = "parse_mode")
    lateinit var parseMode: String
    @SerializedName(value = "chat_id")
    lateinit var chatId: String
    @SerializedName(value = "text")
    lateinit var text: String
    // Integer per the Bot API, and omitted entirely when the chat has no topic. Callers
    // convert the stored preference with Other.parseMessageThreadId; null means "not set",
    // which lets TelegramApi fill it in from preferences.
    @SerializedName(value = "message_thread_id")
    var messageThreadId: Long? = null
    @SerializedName(value = "reply_markup")
    var replyMarkup: Any? = null

    @SerializedName(value = "disable_notification")
    var disableNotification: Boolean = false

    @SerializedName(value = "reply_to_message_id")
    var replyToMessageId: Long? = null

    @SerializedName(value = "allow_sending_without_reply")
    var allowSendingWithoutReply: Boolean? = null
}
