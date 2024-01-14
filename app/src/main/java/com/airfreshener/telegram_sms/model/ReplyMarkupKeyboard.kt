package com.airfreshener.telegram_sms.model

object ReplyMarkupKeyboard {
    fun getInlineKeyboardObj(
        vararg data: Pair<String?, String?>
    ): List<List<InlineKeyboardButton>> = data.map { (text, callbackData) ->
        InlineKeyboardButton().apply {
            this.text = text
            this.callback_data = callbackData
        }.let { listOf(it) }
    }

    @Suppress("PropertyName", "unused")
    class KeyboardMarkup {
        @JvmField
        var inline_keyboard: List<List<InlineKeyboardButton>>? = null
        @JvmField
        var one_time_keyboard = true
    }

    @Suppress("PropertyName")
    class InlineKeyboardButton {
        var text: String? = null
        var callback_data: String? = null
    }
}
