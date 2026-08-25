package com.qwe7002.telegram_sms.static_class

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtherMessageThreadIdTest {

    @Test
    fun returnsDecimalValuesIncludingZeroAndSigns() {
        assertEquals(42L, Other.parseMessageThreadId("42"))
        assertEquals(42L, Other.parseMessageThreadId("+42"))
        assertEquals(-42L, Other.parseMessageThreadId("-42"))
        assertEquals(0L, Other.parseMessageThreadId("0"))
        assertEquals(7L, Other.parseMessageThreadId("0007"))
    }

    @Test
    fun ignoresSurroundingWhitespace() {
        assertEquals(123L, Other.parseMessageThreadId(" \t\r\n123 \n"))
        assertEquals(-9L, Other.parseMessageThreadId("  -9  "))
    }

    @Test
    fun acceptsBothLongBoundaries() {
        assertEquals(Long.MIN_VALUE, Other.parseMessageThreadId(Long.MIN_VALUE.toString()))
        assertEquals(Long.MAX_VALUE, Other.parseMessageThreadId(Long.MAX_VALUE.toString()))
    }

    @Test
    fun returnsNullWhenNoValueWasStored() {
        assertNull(Other.parseMessageThreadId(null))
        assertNull(Other.parseMessageThreadId(""))
        assertNull(Other.parseMessageThreadId(" \t\r\n "))
    }

    @Test
    fun returnsNullForTextThatDoesNotDenoteAnInteger() {
        assertNull(Other.parseMessageThreadId("topic 12"))
        assertNull(Other.parseMessageThreadId("12.0"))
        assertNull(Other.parseMessageThreadId("1e3"))
        assertNull(Other.parseMessageThreadId("0x10"))
        assertNull(Other.parseMessageThreadId("1 2"))
    }

    @Test
    fun returnsNullOutsideTheLongRange() {
        assertNull(Other.parseMessageThreadId("9223372036854775808"))
        assertNull(Other.parseMessageThreadId("-9223372036854775809"))
    }

    @Test
    fun preservesTheDifferenceBetweenZeroAndAbsence() {
        assertEquals(0L, Other.parseMessageThreadId("0"))
        assertNull(Other.parseMessageThreadId("not a number"))
        assertEquals(0L, Other.parseStringToLong("not a number"))
    }
}
