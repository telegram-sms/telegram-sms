package com.qwe7002.telegram_sms.static_class

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneTest {

    @Test
    fun formatSimDisplayName_showsCarrierAndPhone_whenAllPresent() {
        val result = Phone.formatSimDisplayName(
            operatorName = "CMCC",
            displayName = "CMCC",
            phoneNumber = "+8615905698105",
            hidePhoneNumber = false
        )
        assertEquals("CMCC (+8615905698105)", result)
    }

    // Regression: SIM2: Tello () was rendered when the SIM had no phone number stored.
    @Test
    fun formatSimDisplayName_omitsParens_whenPhoneNumberIsEmpty() {
        val result = Phone.formatSimDisplayName(
            operatorName = "Tello",
            displayName = "Tello",
            phoneNumber = "",
            hidePhoneNumber = false
        )
        assertEquals("Tello ", result)
    }

    @Test
    fun formatSimDisplayName_omitsParens_whenPhoneNumberIsNull() {
        val result = Phone.formatSimDisplayName(
            operatorName = "Tello",
            displayName = "Tello",
            phoneNumber = null,
            hidePhoneNumber = false
        )
        assertEquals("Tello ", result)
    }

    @Test
    fun formatSimDisplayName_omitsParens_whenPhoneNumberIsBlank() {
        val result = Phone.formatSimDisplayName(
            operatorName = "Tello",
            displayName = "Tello",
            phoneNumber = "   ",
            hidePhoneNumber = false
        )
        assertEquals("Tello ", result)
    }

    @Test
    fun formatSimDisplayName_hidesPhoneNumber_whenHideFlagIsSet() {
        val result = Phone.formatSimDisplayName(
            operatorName = "CMCC",
            displayName = "CMCC",
            phoneNumber = "+8615905698105",
            hidePhoneNumber = true
        )
        assertEquals("CMCC ", result)
    }

    @Test
    fun formatSimDisplayName_prefersDisplayName_whenOperatorDiffers() {
        val result = Phone.formatSimDisplayName(
            operatorName = "Verizon",
            displayName = "Work Line",
            phoneNumber = "+15551234567",
            hidePhoneNumber = false
        )
        assertEquals("Work Line (+15551234567)", result)
    }

    @Test
    fun formatSimDisplayName_handlesNullOperator_withDisplayName() {
        val result = Phone.formatSimDisplayName(
            operatorName = null,
            displayName = "CMCC",
            phoneNumber = "+8615905698105",
            hidePhoneNumber = false
        )
        assertEquals("CMCC (+8615905698105)", result)
    }

    @Test
    fun formatSimDisplayName_fallsBackToOperator_whenDisplayNameMissing() {
        val result = Phone.formatSimDisplayName(
            operatorName = "Verizon",
            displayName = null,
            phoneNumber = "+15551234567",
            hidePhoneNumber = false
        )
        assertEquals("Verizon (+15551234567)", result)
    }

    @Test
    fun formatSimDisplayName_fallsBackToOperator_whenDisplayNameBlank() {
        val result = Phone.formatSimDisplayName(
            operatorName = "Verizon",
            displayName = "   ",
            phoneNumber = "+15551234567",
            hidePhoneNumber = false
        )
        assertEquals("Verizon (+15551234567)", result)
    }

    @Test
    fun formatSimDisplayName_handlesBothNamesNull() {
        val result = Phone.formatSimDisplayName(
            operatorName = null,
            displayName = null,
            phoneNumber = "+15551234567",
            hidePhoneNumber = false
        )
        assertEquals(" (+15551234567)", result)
    }

    @Test
    fun formatSimDisplayName_handlesBothNamesNull_andNoPhone() {
        val result = Phone.formatSimDisplayName(
            operatorName = null,
            displayName = null,
            phoneNumber = null,
            hidePhoneNumber = false
        )
        assertEquals(" ", result)
    }
}
