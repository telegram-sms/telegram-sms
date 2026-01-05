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

    @JvmStatic
    fun createButton(text: String, callbackData: String): InlineKeyboardButton {
        return InlineKeyboardButton().apply {
            this.text = text
            this.callbackData = callbackData
        }
    }

    @JvmStatic
    fun createButtonRow(vararg buttons: InlineKeyboardButton): ArrayList<InlineKeyboardButton> {
        return ArrayList(buttons.toList())
    }

    @JvmStatic
    fun createPaginationKeyboard(
        currentPage: Int,
        totalPages: Int,
        callbackPrefix: String,
        prevText: String = "◀️",
        nextText: String = "▶️"
    ): ArrayList<ArrayList<InlineKeyboardButton>> {
        val keyboard = ArrayList<ArrayList<InlineKeyboardButton>>()
        val navRow = ArrayList<InlineKeyboardButton>()

        if (currentPage > 0) {
            navRow.add(createButton(prevText, "${callbackPrefix}:${currentPage - 1}"))
        }
        navRow.add(createButton("${currentPage + 1}/$totalPages", "${callbackPrefix}:current"))
        if (currentPage < totalPages - 1) {
            navRow.add(createButton(nextText, "${callbackPrefix}:${currentPage + 1}"))
        }

        if (navRow.isNotEmpty()) {
            keyboard.add(navRow)
        }
        return keyboard
    }

    @JvmStatic
    fun createSmsListKeyboard(
        smsIds: List<Long>,
        currentPage: Int,
        totalPages: Int,
        type: String
    ): ArrayList<ArrayList<InlineKeyboardButton>> {
        val keyboard = ArrayList<ArrayList<InlineKeyboardButton>>()

        // Add SMS item buttons (each SMS as a row)
        for (id in smsIds) {
            keyboard.add(getInlineKeyboardObj("📖 #$id", "sms_read:$id"))
        }

        // Add pagination row
        val navRow = ArrayList<InlineKeyboardButton>()
        if (currentPage > 0) {
            navRow.add(createButton("◀️", "sms_page:$type:${currentPage - 1}"))
        }
        navRow.add(createButton("${currentPage + 1}/$totalPages", "sms_page:$type:current"))
        if (currentPage < totalPages - 1) {
            navRow.add(createButton("▶️", "sms_page:$type:${currentPage + 1}"))
        }
        if (navRow.isNotEmpty()) {
            keyboard.add(navRow)
        }

        return keyboard
    }

    @JvmStatic
    fun createSmsDetailKeyboard(smsId: Long): ArrayList<ArrayList<InlineKeyboardButton>> {
        val keyboard = ArrayList<ArrayList<InlineKeyboardButton>>()
        keyboard.add(getInlineKeyboardObj("🗑️ Delete", "sms_del_confirm:$smsId"))
        keyboard.add(getInlineKeyboardObj("◀️ Back", "sms_page:all:0"))
        return keyboard
    }

    @JvmStatic
    fun createDeleteConfirmKeyboard(smsId: Long): ArrayList<ArrayList<InlineKeyboardButton>> {
        val keyboard = ArrayList<ArrayList<InlineKeyboardButton>>()
        val confirmRow = ArrayList<InlineKeyboardButton>()
        confirmRow.add(createButton("✅ Confirm", "sms_del:$smsId"))
        confirmRow.add(createButton("❌ Cancel", "sms_read:$smsId"))
        keyboard.add(confirmRow)
        return keyboard
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

    /**
     * Reply keyboard markup for displaying command buttons
     */
    class ReplyKeyboardMarkup {
        @SerializedName("keyboard")
        lateinit var keyboard: ArrayList<ArrayList<ReplyKeyboardButton>>

        @SerializedName("resize_keyboard")
        var resizeKeyboard: Boolean = true

        @SerializedName("one_time_keyboard")
        var oneTimeKeyboard: Boolean = false

        @SerializedName("is_persistent")
        var isPersistent: Boolean = true
    }

    class ReplyKeyboardButton {
        lateinit var text: String
    }

    /**
     * Create a ReplyKeyboardButton with the given text
     */
    @JvmStatic
    fun createReplyButton(text: String): ReplyKeyboardButton {
        return ReplyKeyboardButton().apply {
            this.text = text
        }
    }

    /**
     * Create a row of reply keyboard buttons
     */
    @JvmStatic
    fun createReplyButtonRow(vararg buttons: ReplyKeyboardButton): ArrayList<ReplyKeyboardButton> {
        return ArrayList(buttons.toList())
    }

    /**
     * Remove the current custom keyboard and display the default letter-keyboard
     */
    class ReplyKeyboardRemove {
        @SerializedName("remove_keyboard")
        var removeKeyboard: Boolean = true

        @SerializedName("selective")
        var selective: Boolean = false
    }
}
