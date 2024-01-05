package com.airfreshener.telegram_sms.model

import com.airfreshener.telegram_sms.model.ReplyMarkupKeyboard.KeyboardMarkup

@Suppress("unused", "PropertyName")
class RequestMessage {
    //Turn off page preview to avoid being tracked
    val disable_web_page_preview = true
    @JvmField
    var message_id: Long = 0
    @JvmField
    var parse_mode: String? = null
    @JvmField
    var chat_id: String? = null
    @JvmField
    var text: String? = null
    @JvmField
    var reply_markup: KeyboardMarkup? = null
}
