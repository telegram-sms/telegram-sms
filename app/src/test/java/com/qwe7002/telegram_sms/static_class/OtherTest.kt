package com.qwe7002.telegram_sms.static_class

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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
        val originalLocale = java.util.Locale.getDefault()
        val originalTz = TimeZone.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("fa", "IR"))
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val output = Other.formatTimestamp(1705314645000L)
            output.forEach { c ->
                if (c.isDigit()) {
                    assertTrue("non-ASCII digit '$c' in $output", c in '0'..'9')
                }
            }
        } finally {
            java.util.Locale.setDefault(originalLocale)
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

    @Test
    fun isPhoneNumber_emptyStringIsTrue() {
        // Empty string passes the loop trivially — preserved as the existing contract.
        assertTrue(Other.isPhoneNumber(""))
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
}
