package com.qwe7002.telegram_sms.static_class

import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class SmsInfoTest {

    private lateinit var originalTz: TimeZone
    private lateinit var originalLocale: Locale

    @Before
    fun pinLocale() {
        originalTz = TimeZone.getDefault()
        originalLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        TimeZone.setDefault(originalTz)
        Locale.setDefault(originalLocale)
    }

    @Test
    fun getFormattedDate_rendersYyyyMmDdHhMm() {
        val sms = SmsInfo(
            id = 1L,
            address = "+15551234567",
            body = "hello",
            date = 1705314645000L, // 2024-01-15 10:30:45 UTC
            type = 1
        )
        // SimpleDateFormat pattern is "yyyy-MM-dd HH:mm" — no seconds in the output.
        assertEquals("2024-01-15 10:30", sms.getFormattedDate())
    }

    @Test
    fun getFormattedDate_handlesEpoch() {
        val sms = SmsInfo(id = 1L, address = "", body = "", date = 0L, type = 1)
        assertEquals("1970-01-01 00:00", sms.getFormattedDate())
    }

    @Test
    fun smsInfo_dataClass_equalityAndCopy() {
        val a = SmsInfo(1L, "addr", "body", 100L, 1)
        val b = SmsInfo(1L, "addr", "body", 100L, 1)
        assertEquals(a, b)
        assertEquals(a.copy(body = "body"), b)
    }
}
