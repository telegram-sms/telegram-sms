package com.airfreshener.telegram_sms.model

object ReplyMarkupKeyboard {
    @JvmStatic
    fun getInlineKeyboardObj(
        text: String?,
        callbackData: String?
    ): ArrayList<InlineKeyboardButton> {
        val button = InlineKeyboardButton()
        button.text = text
        button.callback_data = callbackData
        val buttonArraylist = ArrayList<InlineKeyboardButton>()
        buttonArraylist.add(button)
        return buttonArraylist
    }

    @Suppress("PropertyName", "unused")
    class KeyboardMarkup {
        @JvmField
        var inline_keyboard: ArrayList<ArrayList<InlineKeyboardButton>>? = null
        @JvmField
        var one_time_keyboard = true
    }

    @Suppress("PropertyName")
    class InlineKeyboardButton {
        var text: String? = null
        var callback_data: String? = null
    }
}
