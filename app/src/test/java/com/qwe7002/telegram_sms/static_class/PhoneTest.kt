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

    // Regression for issue #82: dual-SIM China Mobile, hide_phone_number=true.
    // This is the exact configuration the bug reporter was on; the result must
    // remain a visible non-blank marker so the {{SIM}} placeholder does not
    // collapse to nothing in the rendered Telegram message.
    @Test
    fun formatSimDisplayName_issue82_chinaMobile_hidesPhoneNumber() {
        val result = Phone.formatSimDisplayName(
            operatorName = "中国移动",
            displayName = "中国移动",
            phoneNumber = "+8613800138000",
            hidePhoneNumber = true
        )
        assertEquals("中国移动 ", result)
    }

    // Regression for issue #82: dual-SIM China Mobile, hide_phone_number=false.
    @Test
    fun formatSimDisplayName_issue82_chinaMobile_showsPhoneNumber() {
        val result = Phone.formatSimDisplayName(
            operatorName = "中国移动",
            displayName = "中国移动",
            phoneNumber = "+8613800138000",
            hidePhoneNumber = false
        )
        assertEquals("中国移动 (+8613800138000)", result)
    }

    // Regression for issue #82: device exposes only operatorName (some OEMs
    // report a blank displayName for the second SIM slot). The function must
    // still surface a non-blank marker rather than degrade to a bare " ".
    @Test
    fun formatSimDisplayName_issue82_displayNameMissing_fallsBackToOperator() {
        val result = Phone.formatSimDisplayName(
            operatorName = "中国移动",
            displayName = "",
            phoneNumber = null,
            hidePhoneNumber = true
        )
        assertEquals("中国移动 ", result)
    }
}
