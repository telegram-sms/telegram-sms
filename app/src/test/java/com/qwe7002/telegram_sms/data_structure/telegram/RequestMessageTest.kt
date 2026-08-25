package com.qwe7002.telegram_sms.data_structure.telegram

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Wire-format contract for [RequestMessage], the body every Bot API call is built from
 * (`TelegramApi.sendMessageSync`, `SMS.send`, `USSD.sendUssd`).
 *
 * Two things make this worth pinning:
 *  - the JSON keys and their JSON *types* are what Telegram reads; a renamed or camelCase
 *    key, or a number sent as a quoted string, is silently ignored by the API rather than
 *    rejected, so a regression here is invisible at runtime;
 *  - `TelegramApi` fills in an unset `chatId` by *catching
 *    [UninitializedPropertyAccessException]*, and an unset `messageThreadId` by checking
 *    for null. Both only work while those fields keep their current declarations and Gson
 *    keeps omitting them when unset.
 */
class RequestMessageTest {

    private val gson = Gson()

    private fun serialize(message: RequestMessage): JsonObject =
        JsonParser.parseString(gson.toJson(message)).asJsonObject

    private fun minimalMessage() = RequestMessage().apply {
        chatId = "-1001234567890"
        text = "hello"
    }

    // --- key names ------------------------------------------------------------

    @Test
    fun serializesTelegramSnakeCaseKeys() {
        val json = serialize(RequestMessage().apply {
            chatId = "-100123"
            text = "hi"
            messageThreadId = 42L
            parseMode = "HTML"
            messageId = 7L
            replyToMessageId = 5L
            allowSendingWithoutReply = true
            disableNotification = true
        })

        assertEquals("-100123", json["chat_id"].asString)
        assertEquals("hi", json["text"].asString)
        assertEquals(42L, json["message_thread_id"].asLong)
        assertEquals("HTML", json["parse_mode"].asString)
        assertEquals(7L, json["message_id"].asLong)
        assertEquals(5L, json["reply_to_message_id"].asLong)
        assertTrue(json["allow_sending_without_reply"].asBoolean)
        assertTrue(json["disable_notification"].asBoolean)

        // No camelCase leakage — a missing @SerializedName would show up here.
        for (camel in listOf(
            "chatId", "messageThreadId", "parseMode", "messageId",
            "replyToMessageId", "allowSendingWithoutReply", "disableNotification",
            "disableWebPagePreview", "replyMarkup"
        )) {
            assertFalse("unexpected camelCase key '$camel'", json.has(camel))
        }
    }

    /**
     * The Bot API declares `message_thread_id` as an Integer. It used to be a `String`
     * here, so it went out quoted — and as `""` when the chat had no topic, which is not
     * an Integer at all. Callers now convert the stored preference through
     * `Other.parseMessageThreadId`, and an absent topic omits the field entirely.
     */
    @Test
    fun messageThreadIdIsSerializedAsAnInteger() {
        val withTopic = serialize(RequestMessage().apply {
            chatId = "-100123"; text = "hi"; messageThreadId = 42L
        })
        assertTrue(withTopic["message_thread_id"].asJsonPrimitive.isNumber)
        assertFalse(withTopic["message_thread_id"].asJsonPrimitive.isString)
        assertEquals(42L, withTopic["message_thread_id"].asLong)
    }

    @Test
    fun messageThreadIdIsOmittedWhenThereIsNoTopic() {
        // Absent, not "" and not 0: either would be a value Telegram has to interpret.
        assertFalse(serialize(minimalMessage()).has("message_thread_id"))
    }

    // --- fields that are always present ---------------------------------------

    @Test
    fun alwaysDisablesWebPagePreview() {
        // Privacy: link previews would make Telegram fetch URLs contained in forwarded
        // SMS. The field is a `val` with no setter, so it must be true on every payload.
        assertTrue(serialize(minimalMessage())["disable_web_page_preview"].asBoolean)
    }

    @Test
    fun emitsMessageIdAndDisableNotificationDefaults() {
        // Primitive defaults are serialized rather than omitted, so `message_id` and
        // `disable_notification` ride along on every call. Neither is a sendMessage
        // parameter at its default value — `message_id` only means something to
        // editMessageText, and `SMS.send` writes -1 there for a brand-new message — so
        // this documents that the payload is not method-specific rather than claiming
        // the fields are required.
        val json = serialize(minimalMessage())
        assertEquals(0L, json["message_id"].asLong)
        assertFalse(json["disable_notification"].asBoolean)
    }

    // --- fields that must be omitted when unset -------------------------------

    @Test
    fun omitsNullableFieldsWhenUnset() {
        // Gson does not serialize nulls by default. reply_to_message_id must stay absent:
        // sending it as null would make Telegram reject the whole request.
        val json = serialize(minimalMessage())
        assertFalse(json.has("reply_to_message_id"))
        assertFalse(json.has("allow_sending_without_reply"))
        assertFalse(json.has("reply_markup"))
    }

    @Test
    fun omitsUninitializedLateinitFields() {
        // parse_mode is left unset by most callers; it must not appear as null.
        val json = serialize(minimalMessage())
        assertFalse(json.has("parse_mode"))
    }

    @Test
    fun omitsEveryUnsetOptionalFieldOnAFreshInstance() {
        val json = serialize(RequestMessage())
        for (key in listOf("chat_id", "text", "message_thread_id", "parse_mode")) {
            assertFalse("expected '$key' to be omitted when unset", json.has(key))
        }
    }

    // --- the UninitializedPropertyAccessException signal -----------------------

    @Test
    fun readingUnsetChatIdThrowsUninitializedPropertyAccess() {
        // This is not incidental: TelegramApi.sendMessage/sendMessageSync auto-fill chatId
        // from MMKV by catching exactly this exception. Turning chatId into a nullable
        // `var` with a default would make that fallback dead code, and every caller that
        // does not set it would post an empty chat_id instead.
        val message = RequestMessage()
        try {
            message.chatId
            fail("expected UninitializedPropertyAccessException for chatId")
        } catch (_: UninitializedPropertyAccessException) {
        }
    }

    @Test
    fun unsetMessageThreadIdIsNullSoTelegramApiCanFillItIn() {
        // The sibling half of the fallback above, expressed as a null rather than an
        // exception: TelegramApi only reads the preference when this is still null.
        assertNull(RequestMessage().messageThreadId)
    }

    @Test
    fun assignedChatIdNoLongerThrows() {
        val message = RequestMessage()
        message.chatId = ""
        assertEquals("", message.chatId) // empty string is "set", and stays on the wire
        assertEquals("", serialize(message)["chat_id"].asString)
    }

    // --- reply markup ---------------------------------------------------------

    @Test
    fun serializesNestedReplyMarkup() {
        // replyMarkup is typed Any? so any of the ReplyMarkupKeyboard shapes can be
        // attached; Gson must serialize the concrete runtime type, not an empty object.
        val message = minimalMessage()
        message.replyMarkup = ReplyMarkupKeyboard.ForceReply().apply {
            inputFieldPlaceholder = "phone number"
        }

        val markup = serialize(message)["reply_markup"].asJsonObject
        assertTrue(markup["force_reply"].asBoolean)
        assertTrue(markup["selective"].asBoolean)
        assertEquals("phone number", markup["input_field_placeholder"].asString)
    }

    @Test
    fun deserializesFromTelegramStyleJson() {
        // Round trip through the same key names, so a rename breaks in both directions.
        val parsed = gson.fromJson(
            """{"chat_id":"-100123","text":"hi","message_thread_id":9,"message_id":3}""",
            RequestMessage::class.java
        )
        assertEquals("-100123", parsed.chatId)
        assertEquals("hi", parsed.text)
        assertEquals(9L, parsed.messageThreadId)
        assertEquals(3L, parsed.messageId)
        assertNull(parsed.replyToMessageId)
    }
}
