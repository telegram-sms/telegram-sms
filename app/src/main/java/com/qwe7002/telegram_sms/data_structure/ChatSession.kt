package com.qwe7002.telegram_sms.data_structure

import com.google.gson.annotations.SerializedName

/**
 * One interactive /sendsms or /sendussd conversation, keyed in the `session` MMKV
 * namespace by the Telegram message id of the bot prompt the user is responding to:
 * the ForceReply / confirm-keyboard message id (resolved from
 * `reply_to_message.message_id` for text replies, or `callback_query.message.message_id`
 * for inline-button taps). This replaces the old process-global status enums plus the
 * single shared slot in the `chat` namespace, so concurrent / out-of-order sessions no
 * longer clobber each other and state no longer leaks across a service restart.
 */
class ChatSession {
    // TYPE_SMS / TYPE_USSD — selects which step constant space `step` lives in.
    var type: Int = TYPE_SMS

    // One of the STEP_* constants in ChatService.SESSION_STEP.
    var step: Int = 0

    // SIM slot (0-based) or -1 for "no selection / system default".
    var slot: Int = -1

    var to: String = ""
    var content: String = ""

    @SerializedName("ussd_code")
    var ussdCode: String = ""

    // Original /send* command message id, used to anchor selective ForceReply prompts.
    @SerializedName("command_message_id")
    var commandMessageId: Long = 0L

    // Wall-clock creation time (ms) for TTL-based cleanup of abandoned sessions.
    @SerializedName("created_at")
    var createdAt: Long = 0L

    companion object {
        const val TYPE_SMS = 0
        const val TYPE_USSD = 1
    }
}
