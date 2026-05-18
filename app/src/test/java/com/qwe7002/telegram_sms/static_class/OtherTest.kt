package com.qwe7002.telegram_sms.static_class

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class OtherTest {

    @Test
    fun formatTimestamp_usesAsciiDigits_andUtcCalendarFormat() {
        val originalTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals(
                "2024-01-15 10:30:45",
                Other.formatTimestamp(1705314645000L)
            )
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun formatTimestamp_returnsAsciiDigits_evenInFarsiLocale() {
        val originalLocale = Locale.getDefault()
        val originalTz = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("fa-IR"))
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val output = Other.formatTimestamp(1705314645000L)
            output.forEach { c ->
                if (c.isDigit()) {
                    assertTrue("non-ASCII digit '$c' in $output", c in '0'..'9')
                }
            }
        } finally {
            Locale.setDefault(originalLocale)
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun formatTimestamp_epoch() {
        val originalTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals("1970-01-01 00:00:00", Other.formatTimestamp(0L))
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun getNineKeyMapConvert_mapsLettersToDigits() {
        assertEquals("228", Other.getNineKeyMapConvert("ABT"))
        assertEquals("9999", Other.getNineKeyMapConvert("WXYZ"))
        assertEquals("234567", Other.getNineKeyMapConvert("ADGJMP"))
    }

    @Test
    fun getNineKeyMapConvert_keepsDigitsAndSymbols() {
        assertEquals("+1-800-2255-2255", Other.getNineKeyMapConvert("+1-800-CALL-CALL"))
        assertEquals("12345", Other.getNineKeyMapConvert("12345"))
    }

    @Test
    fun getNineKeyMapConvert_isCaseInsensitive() {
        assertEquals(Other.getNineKeyMapConvert("abc"), Other.getNineKeyMapConvert("ABC"))
    }

    @Test
    fun getNineKeyMapConvert_coversEveryKeypadLetter() {
        // Sanity check the full mapping: all 26 letters should produce expected digits.
        assertEquals(
            "22233344455566677778889999",
            Other.getNineKeyMapConvert("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        )
    }

    // Regression: prior code used Locale.getDefault() for uppercasing. On a Turkish
    // device, 'i' → 'İ' (U+0130), which isn't in NINE_KEY_MAP and would pass through
    // unmapped. Locale.ROOT fixes it.
    @Test
    fun getNineKeyMapConvert_isLocaleStable_underTurkishLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("444", Other.getNineKeyMapConvert("iii"))
            assertEquals(Other.getNineKeyMapConvert("HELLO"), Other.getNineKeyMapConvert("hello"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun getNineKeyMapConvert_emptyInput() {
        assertEquals("", Other.getNineKeyMapConvert(""))
    }

    @Test
    fun parseStringToLong_validNumber() {
        assertEquals(1234567890L, Other.parseStringToLong("1234567890"))
    }

    @Test
    fun parseStringToLong_negativeNumber() {
        assertEquals(-42L, Other.parseStringToLong("-42"))
    }

    @Test
    fun parseStringToLong_returnsZero_forNull() {
        assertEquals(0L, Other.parseStringToLong(null))
    }

    @Test
    fun parseStringToLong_returnsZero_forBlank() {
        assertEquals(0L, Other.parseStringToLong(""))
        assertEquals(0L, Other.parseStringToLong("   "))
    }

    @Test
    fun parseStringToLong_returnsZero_forNonNumeric() {
        assertEquals(0L, Other.parseStringToLong("abc"))
        assertEquals(0L, Other.parseStringToLong("12.34"))
    }

    @Test
    fun parseStringToLong_returnsZero_forOverflow() {
        assertEquals(0L, Other.parseStringToLong("99999999999999999999"))
    }

    @Test
    fun parseStringToLong_handlesMaxLong() {
        assertEquals(Long.MAX_VALUE, Other.parseStringToLong(Long.MAX_VALUE.toString()))
    }

    @Test
    fun getSendPhoneNumber_stripsNonDigits_butKeepsPlus() {
        assertEquals("+15551234567", Other.getSendPhoneNumber("+1 (555) 123-4567"))
    }

    @Test
    fun getSendPhoneNumber_convertsLettersViaNineKey() {
        assertEquals("18002255", Other.getSendPhoneNumber("1-800-CALL"))
    }

    @Test
    fun getSendPhoneNumber_emptyInput() {
        assertEquals("", Other.getSendPhoneNumber(""))
    }

    @Test
    fun isPhoneNumber_acceptsDigits() {
        assertTrue(Other.isPhoneNumber("15905698105"))
    }

    @Test
    fun isPhoneNumber_acceptsLeadingPlus() {
        assertTrue(Other.isPhoneNumber("+15905698105"))
    }

    @Test
    fun isPhoneNumber_rejectsLetters() {
        assertFalse(Other.isPhoneNumber("123abc"))
    }

    @Test
    fun isPhoneNumber_rejectsSpaces() {
        assertFalse(Other.isPhoneNumber("123 456"))
    }

    // Was: returned true on the prior code because the loop body was skipped.
    // Fix: empty string is not a valid number.
    @Test
    fun isPhoneNumber_rejectsEmptyString() {
        assertFalse(Other.isPhoneNumber(""))
    }

    @Test
    fun isPhoneNumber_rejectsPlusOnly() {
        assertFalse(Other.isPhoneNumber("+"))
    }

    @Test
    fun isPhoneNumber_rejectsPlusNotAtStart() {
        assertFalse(Other.isPhoneNumber("1+5905698105"))
        assertFalse(Other.isPhoneNumber("15905+98105"))
    }

    @Test
    fun isPhoneNumber_acceptsSingleDigit() {
        assertTrue(Other.isPhoneNumber("0"))
        assertTrue(Other.isPhoneNumber("9"))
    }

    @Test
    fun isPhoneNumber_rejectsMultiplePluses() {
        assertFalse(Other.isPhoneNumber("++1234"))
    }

    @Test
    fun getMessageId_extractsFromTelegramResponse() {
        val json = """{"ok":true,"result":{"message_id":12345,"chat":{"id":1}}}"""
        assertEquals(12345L, Other.getMessageId(json))
    }

    @Test
    fun getMessageId_handlesLargeIds() {
        val json = """{"result":{"message_id":9223372036854775000}}"""
        assertEquals(9223372036854775000L, Other.getMessageId(json))
    }

    // Was: threw on the prior code, crashing the OkHttp Response callback.
    // Fix: return 0L so callers (MMKV puts, addMessageList) stay alive.
    @Test
    fun getMessageId_returnsZero_forTelegramError() {
        val errorResponse = """{"ok":false,"error_code":400,"description":"Bad Request"}"""
        assertEquals(0L, Other.getMessageId(errorResponse))
    }

    @Test
    fun getMessageId_returnsZero_forMalformedJson() {
        assertEquals(0L, Other.getMessageId("not-json"))
        assertEquals(0L, Other.getMessageId(""))
    }

    @Test
    fun getMessageId_returnsZero_whenMessageIdMissing() {
        val json = """{"ok":true,"result":{"chat":{"id":1}}}"""
        assertEquals(0L, Other.getMessageId(json))
    }

    @Test
    fun getMessageId_returnsZero_forHtmlBody() {
        // Proxies or captive portals can return HTML instead of Telegram JSON.
        assertEquals(0L, Other.getMessageId("<html><body>502 Bad Gateway</body></html>"))
    }
}
