package com.qwe7002.telegram_sms.data_structure.telegram

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ReplyMarkupKeyboard].
 *
 * The `callback_data` strings built here are a protocol between this file and
 * `ChatService.handleCallbackQuery`, which dispatches on `startsWith("<prefix>:")`
 * after `callbackData.split(":")`. Nothing checks that the two sides agree at
 * compile time, so the exact prefixes, the separator count and the 1-based page
 * labels are pinned below.
 */
class ReplyMarkupKeyboardTest {

    private val gson = Gson()

    private fun texts(row: List<ReplyMarkupKeyboard.InlineKeyboardButton>) = row.map { it.text }
    private fun data(row: List<ReplyMarkupKeyboard.InlineKeyboardButton>) = row.map { it.callbackData }

    // --- createPaginationKeyboard --------------------------------------------

    @Test
    fun pagination_firstPage_hasNoPreviousButton() {
        val row = ReplyMarkupKeyboard.createPaginationKeyboard(0, 3, "p").single()
        assertEquals(listOf("1/3", "▶️"), texts(row))
        assertEquals(listOf("p:current", "p:1"), data(row))
    }

    @Test
    fun pagination_middlePage_hasBothArrows() {
        val row = ReplyMarkupKeyboard.createPaginationKeyboard(1, 3, "p").single()
        assertEquals(listOf("◀️", "2/3", "▶️"), texts(row))
        assertEquals(listOf("p:0", "p:current", "p:2"), data(row))
    }

    @Test
    fun pagination_lastPage_hasNoNextButton() {
        val row = ReplyMarkupKeyboard.createPaginationKeyboard(2, 3, "p").single()
        assertEquals(listOf("◀️", "3/3"), texts(row))
        assertEquals(listOf("p:1", "p:current"), data(row))
    }

    @Test
    fun pagination_singlePage_hasCounterOnly() {
        val row = ReplyMarkupKeyboard.createPaginationKeyboard(0, 1, "p").single()
        assertEquals(listOf("1/1"), texts(row))
        assertEquals(listOf("p:current"), data(row))
    }

    @Test
    fun pagination_pageLabelIsOneBased_butCallbackDataStaysZeroBased() {
        // The counter is display text; the callback data stays 0-based, because that is
        // what ChatService feeds straight back into SMS.getSmsList(page = ...).
        val row = ReplyMarkupKeyboard.createPaginationKeyboard(4, 9, "p").single()
        assertEquals("5/9", texts(row)[1])
        assertEquals(listOf("p:3", "p:current", "p:5"), data(row))
    }

    /**
     * Degenerate inputs are clamped rather than rendered literally. An empty result set
     * arrives as totalPages = 0, and a stale callback can name a page past the end or
     * below zero; those used to show "1/0", "4/3" and "0/3", and offer callback data for
     * pages that do not exist.
     */
    @Test
    fun pagination_emptyResultSetShowsASinglePage() {
        val row = ReplyMarkupKeyboard.createPaginationKeyboard(0, 0, "p").single()
        assertEquals(listOf("1/1"), texts(row))
        assertEquals(listOf("p:current"), data(row))
    }

    @Test
    fun pagination_pagePastTheEndIsHeldAtTheLastPage() {
        val row = ReplyMarkupKeyboard.createPaginationKeyboard(3, 3, "p").single()
        assertEquals(listOf("◀️", "3/3"), texts(row))
        assertEquals(listOf("p:1", "p:current"), data(row))
    }

    @Test
    fun pagination_negativePageIsHeldAtTheFirstPage() {
        val row = ReplyMarkupKeyboard.createPaginationKeyboard(-1, 3, "p").single()
        assertEquals(listOf("1/3", "▶️"), texts(row))
        assertEquals(listOf("p:current", "p:1"), data(row))
    }

    @Test
    fun pagination_neverOffersACallbackForAPageThatDoesNotExist() {
        // Every emitted page index must be addressable, or a tap sends the caller to a
        // page its query cannot produce.
        for (total in 0..4) {
            for (page in -2..6) {
                val row = ReplyMarkupKeyboard.createPaginationKeyboard(page, total, "p").single()
                for (button in row) {
                    val target = button.callbackData.removePrefix("p:")
                    if (target == "current") continue
                    val index = target.toInt()
                    assertTrue("offered page $index for total=$total", index >= 0)
                    assertTrue("offered page $index for total=$total", index < maxOf(total, 1))
                }
            }
        }
    }

    @Test
    fun smsList_clampsTheNavigationRowTheSameWay() {
        val keyboard = ReplyMarkupKeyboard.createSmsListKeyboard(emptyList(), 0, 0, "all")
        assertEquals(listOf("1/1"), texts(keyboard.single()))
        assertEquals(listOf("sms_page:all:current"), data(keyboard.single()))
    }

    @Test
    fun pagination_alwaysEmitsExactlyOneRow() {
        // The nav row is never empty (the counter is unconditional), so the keyboard is
        // always a single row - callers index it as such.
        for (page in -1..4) {
            assertEquals(1, ReplyMarkupKeyboard.createPaginationKeyboard(page, 3, "p").size)
        }
    }

    @Test
    fun pagination_honoursCustomArrowLabels() {
        val row = ReplyMarkupKeyboard.createPaginationKeyboard(1, 3, "p", "prev", "next").single()
        assertEquals(listOf("prev", "2/3", "next"), texts(row))
    }

    // --- createSmsListKeyboard ------------------------------------------------

    @Test
    fun smsList_putsOneRowPerMessageThenTheNavigationRow() {
        val keyboard = ReplyMarkupKeyboard.createSmsListKeyboard(listOf(11L, 12L), 0, 2, "inbox")

        assertEquals(3, keyboard.size)
        assertEquals(listOf("📖 #11"), texts(keyboard[0]))
        assertEquals(listOf("sms_read:11"), data(keyboard[0]))
        assertEquals(listOf("📖 #12"), texts(keyboard[1]))
        assertEquals(listOf("sms_read:12"), data(keyboard[1]))
        assertEquals(listOf("1/2", "▶️"), texts(keyboard[2]))
        assertEquals(listOf("sms_page:inbox:current", "sms_page:inbox:1"), data(keyboard[2]))
    }

    @Test
    fun smsList_emptyPage_stillRendersNavigation() {
        val keyboard = ReplyMarkupKeyboard.createSmsListKeyboard(emptyList(), 1, 3, "sent")
        assertEquals(1, keyboard.size)
        assertEquals(
            listOf("sms_page:sent:0", "sms_page:sent:current", "sms_page:sent:2"),
            data(keyboard.single())
        )
    }

    @Test
    fun smsList_pageCallbackSplitsIntoThreePartsForChatService() {
        // ChatService requires parts.size >= 3 for the "sms_page:" branch and reads the
        // page index out of parts[2], so the type must stay at index 1 and must not
        // introduce extra separators.
        val nav = ReplyMarkupKeyboard.createSmsListKeyboard(listOf(1L), 1, 5, "all").last()
        for (button in nav) {
            val parts = button.callbackData.split(":")
            assertEquals(3, parts.size)
            assertEquals("sms_page", parts[0])
            assertEquals("all", parts[1])
        }
    }

    // --- detail / delete keyboards -------------------------------------------

    @Test
    fun smsDetail_offersDeleteConfirmationAndBackToFirstPage() {
        val keyboard = ReplyMarkupKeyboard.createSmsDetailKeyboard(42L)
        assertEquals(listOf("sms_del_confirm:42"), data(keyboard[0]))
        assertEquals(listOf("sms_page:all:0"), data(keyboard[1]))
    }

    @Test
    fun deleteConfirm_confirmDeletesAndCancelReturnsToTheMessage() {
        val row = ReplyMarkupKeyboard.createDeleteConfirmKeyboard(42L).single()
        assertEquals(listOf("✅ Confirm", "❌ Cancel"), texts(row))
        assertEquals(listOf("sms_del:42", "sms_read:42"), data(row))
    }

    @Test
    fun deleteConfirmPrefixIsNotSwallowedByTheDeletePrefix() {
        // ChatService dispatches on startsWith(); "sms_del_confirm:" must not match
        // "sms_del:" or tapping the "delete?" prompt would delete straight away.
        val confirmPrompt = ReplyMarkupKeyboard.createSmsDetailKeyboard(42L)[0].single().callbackData
        assertTrue(confirmPrompt.startsWith("sms_del_confirm:"))
        assertFalse(confirmPrompt.startsWith("sms_del:"))
    }

    @Test
    fun everyCallbackDataFitsTheSixtyFourByteLimit() {
        // Bot API rejects callback_data longer than 64 bytes; ids come from the SMS
        // provider _id column and can be arbitrarily large.
        val id = Long.MAX_VALUE
        val generated =
            ReplyMarkupKeyboard.createSmsListKeyboard(listOf(id), 0, Int.MAX_VALUE, "inbox") +
                    ReplyMarkupKeyboard.createSmsDetailKeyboard(id) +
                    ReplyMarkupKeyboard.createDeleteConfirmKeyboard(id) +
                    ReplyMarkupKeyboard.createPaginationKeyboard(0, Int.MAX_VALUE, "sms_page:inbox")

        for (row in generated) {
            for (button in row) {
                val size = button.callbackData.toByteArray(Charsets.UTF_8).size
                assertTrue("callback_data too long ($size B): ${button.callbackData}", size <= 64)
            }
        }
    }

    // --- small builders -------------------------------------------------------

    @Test
    fun getInlineKeyboardObj_wrapsASingleButtonInARow() {
        val row = ReplyMarkupKeyboard.getInlineKeyboardObj("label", "cb")
        assertEquals(1, row.size)
        assertEquals("label", row[0].text)
        assertEquals("cb", row[0].callbackData)
    }

    @Test
    fun createButtonRow_preservesOrder() {
        val row = ReplyMarkupKeyboard.createButtonRow(
            ReplyMarkupKeyboard.createButton("a", "1"),
            ReplyMarkupKeyboard.createButton("b", "2")
        )
        assertEquals(listOf("a", "b"), texts(row))
        assertEquals(listOf("1", "2"), data(row))
    }

    @Test
    fun createReplyButtonRow_preservesOrder() {
        val row = ReplyMarkupKeyboard.createReplyButtonRow(
            ReplyMarkupKeyboard.createReplyButton("/start"),
            ReplyMarkupKeyboard.createReplyButton("/help")
        )
        assertEquals(listOf("/start", "/help"), row.map { it.text })
    }

    // --- serialized markup shapes --------------------------------------------

    @Test
    fun inlineKeyboardMarkupUsesTelegramKeys() {
        val markup = ReplyMarkupKeyboard.KeyboardMarkup().apply {
            inlineKeyboard = arrayListOf(ReplyMarkupKeyboard.getInlineKeyboardObj("a", "1"))
        }
        val json = JsonParser.parseString(gson.toJson(markup)).asJsonObject

        val button = json["inline_keyboard"].asJsonArray[0].asJsonArray[0].asJsonObject
        assertEquals("a", button["text"].asString)
        assertEquals("1", button["callback_data"].asString)
        assertFalse(button.has("callbackData"))

        // InlineKeyboardMarkup has exactly one field in the Bot API. Anything else here is
        // an invalid field the server is under no obligation to ignore, so the payload must
        // stay minimal - in either spelling.
        assertEquals(setOf("inline_keyboard"), json.keySet())
        assertFalse(json.has("oneTimeKeyboard"))
        assertFalse(json.has("one_time_keyboard"))
    }

    @Test
    fun replyKeyboardMarkupUsesTelegramKeysAndDefaults() {
        val markup = ReplyMarkupKeyboard.ReplyKeyboardMarkup().apply {
            keyboard = arrayListOf(
                ReplyMarkupKeyboard.createReplyButtonRow(
                    ReplyMarkupKeyboard.createReplyButton("/start")
                )
            )
        }
        val json = JsonParser.parseString(gson.toJson(markup)).asJsonObject

        assertEquals(
            "/start",
            json["keyboard"].asJsonArray[0].asJsonArray[0].asJsonObject["text"].asString
        )
        assertTrue(json["resize_keyboard"].asBoolean)
        assertFalse(json["one_time_keyboard"].asBoolean) // the command bar must stay up
        assertTrue(json["is_persistent"].asBoolean)
    }

    @Test
    fun forceReplyIsSelectiveByDefaultAndOmitsAnUnsetPlaceholder() {
        // `selective` plus reply_to_message_id is what keeps an interactive /sendsms
        // flow bound to the user who started it instead of any chat member.
        val json = JsonParser.parseString(gson.toJson(ReplyMarkupKeyboard.ForceReply())).asJsonObject
        assertTrue(json["force_reply"].asBoolean)
        assertTrue(json["selective"].asBoolean)
        assertFalse(json.has("input_field_placeholder"))
    }

    @Test
    fun replyKeyboardRemoveIsNotSelective() {
        val json =
            JsonParser.parseString(gson.toJson(ReplyMarkupKeyboard.ReplyKeyboardRemove())).asJsonObject
        assertTrue(json["remove_keyboard"].asBoolean)
        assertFalse(json["selective"].asBoolean) // clear it for everyone, not just one user
    }
}
