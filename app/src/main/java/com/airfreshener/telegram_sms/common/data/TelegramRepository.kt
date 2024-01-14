package com.airfreshener.telegram_sms.common.data

import com.airfreshener.telegram_sms.model.ReplyMarkupKeyboard

interface TelegramRepository {

    fun sendMessage(
        message: String,
        keyboardMarkup: ReplyMarkupKeyboard.KeyboardMarkup? = null,
        onSuccess: ((messageId: Long?) -> Unit)? = null,
        onFailure: (() -> Unit)? = null,
    )
    fun editMessage(
        message: String,
        messageId: Long,
        onSuccess: ((messageId: Long?) -> Unit)? = null,
        onFailure: (() -> Unit)? = null,
    )
}
