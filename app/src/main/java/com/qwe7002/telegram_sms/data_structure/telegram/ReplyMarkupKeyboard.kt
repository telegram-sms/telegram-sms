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

    /**
     * Clamps a (page, total) pair to something that can be shown to a user.
     *
     * Callers derive both from a query result, so an empty result set arrives as
     * `totalPages = 0` and a stale callback can arrive with a page past the end or below
     * zero. Left alone those render as "1/0", "4/3" or "0/3" — and the callback data would
     * invite a tap onto a page that does not exist. One page always exists, even when it
     * is empty, so the total floors at 1 and the page is held inside it.
     */
    private fun clampPage(currentPage: Int, totalPages: Int): Pair<Int, Int> {
        val total = if (totalPages < 1) 1 else totalPages
        val page = currentPage.coerceIn(0, total - 1)
        return page to total
    }

    @JvmStatic
    fun createPaginationKeyboard(
        currentPage: Int,
        totalPages: Int,
        callbackPrefix: String,
        prevText: String = "◀️",
        nextText: String = "▶️"
    ): ArrayList<ArrayList<InlineKeyboardButton>> {
        val (page, total) = clampPage(currentPage, totalPages)
        val keyboard = ArrayList<ArrayList<InlineKeyboardButton>>()
        val navRow = ArrayList<InlineKeyboardButton>()

        if (page > 0) {
            navRow.add(createButton(prevText, "${callbackPrefix}:${page - 1}"))
        }
        navRow.add(createButton("${page + 1}/$total", "${callbackPrefix}:current"))
        if (page < total - 1) {
            navRow.add(createButton(nextText, "${callbackPrefix}:${page + 1}"))
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
        val (page, total) = clampPage(currentPage, totalPages)
        val keyboard = ArrayList<ArrayList<InlineKeyboardButton>>()

        // Add SMS item buttons (each SMS as a row)
        for (id in smsIds) {
            keyboard.add(getInlineKeyboardObj("📖 #$id", "sms_read:$id"))
        }

        // Add pagination row
        val navRow = ArrayList<InlineKeyboardButton>()
        if (page > 0) {
            navRow.add(createButton("◀️", "sms_page:$type:${page - 1}"))
        }
        navRow.add(createButton("${page + 1}/$total", "sms_page:$type:current"))
        if (page < total - 1) {
            navRow.add(createButton("▶️", "sms_page:$type:${page + 1}"))
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

    /**
     * Telegram clients will display a reply interface to the user (as if the user
     * had selected the bot's message and tapped 'Reply'). Used for step-by-step
     * interactive prompts such as the SMS phone-number / content input flow.
     *
     * `selective` defaults to true: combined with a `reply_to_message_id` pointing at
     * the triggering user's message, only that user is forced to reply — which keeps
     * the interactive state machine from being driven by unrelated chat members.
     */
    class ForceReply {
        @SerializedName("force_reply")
        val forceReply: Boolean = true

        @SerializedName("input_field_placeholder")
        var inputFieldPlaceholder: String? = null

        @SerializedName("selective")
        var selective: Boolean = true
    }
}
