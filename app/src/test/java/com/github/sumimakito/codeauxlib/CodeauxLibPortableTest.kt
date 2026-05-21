package com.github.sumimakito.codeauxlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CodeauxLibPortableTest {

    private val codeaux = CodeauxLibPortable()

    @Test
    fun find_extractsEnglishVerificationCode_withColon() {
        assertEquals("123456", codeaux.find("Your verification code is: 123456"))
    }

    @Test
    fun find_extractsEnglishOtp_withCodeKeyword() {
        assertEquals("987654", codeaux.find("Your code is 987654. Do not share."))
    }

    @Test
    fun find_extractsEnglishOtp_withSecurityKeyword() {
        assertEquals("482917", codeaux.find("Use 482917 as your security code."))
    }

    @Test
    fun find_extractsChineseVerificationCode() {
        assertEquals("654321", codeaux.find("您的验证码是 654321，请勿泄露。"))
    }

    @Test
    fun find_extractsChineseVerificationCode_withColon() {
        assertEquals("112233", codeaux.find("验证码：112233"))
    }

    @Test
    fun find_extractsTraditionalChineseVerificationCode() {
        assertEquals("445566", codeaux.find("您的驗證碼為 445566，請勿洩露。"))
    }

    @Test
    fun find_extractsJapaneseVerificationCode() {
        assertEquals("778899", codeaux.find("認証コードは 778899 です。"))
    }

    @Test
    fun find_stripsUrls_beforeMatching() {
        // The URL contains digit-like sequences. The pre-process omit list should
        // strip the URL so the actual code "246810" is what gets returned.
        assertEquals(
            "246810",
            codeaux.find("Visit https://example.com/path/9999 — your code is 246810")
        )
    }

    @Test
    fun find_returnsNull_whenNoCodePresent() {
        assertNull(codeaux.find("Hello, how are you today?"))
    }

    @Test
    fun find_returnsNull_forEmptyInput() {
        assertNull(codeaux.find(""))
    }

    @Test
    fun find_ignoresFileSizes_likeKb() {
        // The 'filtered' list strips "<N> KB / MB" so a transfer-status SMS doesn't
        // get misidentified as a 4-8 digit OTP.
        assertNull(codeaux.find("Downloaded 12345 KB successfully"))
    }

    @Test
    fun find_ignoresDurations_likeMinutes() {
        assertNull(codeaux.find("Call lasted 12345 minutes"))
    }

    @Test
    fun find_handlesSeparators_inCodes() {
        // Codes commonly appear with dashes/spaces; the library strips those in the
        // returned value.
        assertEquals("123456", codeaux.find("Your code is: 123-456"))
    }

    @Test
    fun find_extractsChineseCode_withActionVerb() {
        assertEquals("998877", codeaux.find("请使用 998877 完成注册"))
    }

}
