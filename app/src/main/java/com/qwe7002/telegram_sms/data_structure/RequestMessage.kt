package com.qwe7002.telegram_sms.data_structure

import com.google.gson.annotations.SerializedName
import com.qwe7002.telegram_sms.data_structure.ReplyMarkupKeyboard.KeyboardMarkup

class RequestMessage {
    //Turn off page preview to avoid being tracked
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
    @SerializedName(value = "message_thread_id")
    lateinit var messageThreadId: String
    @SerializedName(value = "reply_markup")
    lateinit var replyMarkup: KeyboardMarkup
}
