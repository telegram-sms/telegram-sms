package com.airfreshener.telegram_sms.model

import com.airfreshener.telegram_sms.model.ReplyMarkupKeyboard.KeyboardMarkup

@Suppress("unused", "PropertyName")
class RequestMessage {

    val disable_web_page_preview = true //Turn off page preview to avoid being tracked
    var message_id: Long = 0
    var parse_mode: String? = null
    var chat_id: String? = null
    var text: String? = null
    var reply_markup: KeyboardMarkup? = null
}
