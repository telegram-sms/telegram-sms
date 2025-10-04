package com.qwe7002.telegram_sms.data_structure.telegram

import com.google.gson.annotations.SerializedName

@Suppress("unused")
object ReplyMarkupKeyboard {
    @JvmStatic
    fun getInlineKeyboardObj(
        text: String,
        callbackData: String
    ): ArrayList<InlineKeyboardButton> {
        val button = InlineKeyboardButton()
        button.text = text
        button.callbackData = callbackData
        val buttonArraylist = ArrayList<InlineKeyboardButton>()
        buttonArraylist.add(button)
        return buttonArraylist
    }

    class KeyboardMarkup {
        @SerializedName("inline_keyboard")
        lateinit var inlineKeyboard: ArrayList<ArrayList<InlineKeyboardButton>>
        var oneTimeKeyboard: Boolean = true
    }

    class InlineKeyboardButton {
        lateinit var text: String

        @SerializedName("callback_data")
        lateinit var callbackData: String
    }
}

