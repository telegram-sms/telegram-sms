package com.qwe7002.telegram_sms.static_class

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateTest {

    @Test
    fun substitute_replacesSinglePlaceholder() {
        val result = Template.substitute(
            "[{{SIM}}Receive SMS]\nFrom: {{From}}\nContent: {{Content}}",
            mapOf(
                "SIM" to "CMCC ",
                "From" to "10086",
                "Content" to "hello"
            )
        )
        assertEquals("[CMCC Receive SMS]\nFrom: 10086\nContent: hello", result)
    }

    @Test
    fun substitute_leavesUnknownPlaceholdersUntouched() {
        val result = Template.substitute(
            "[{{SIM}}Receive SMS] {{Unknown}}",
            mapOf("SIM" to "CMCC ")
        )
        assertEquals("[CMCC Receive SMS] {{Unknown}}", result)
    }

    @Test
    fun substitute_replacesAllOccurrencesOfSameKey() {
        val result = Template.substitute(
            "{{SIM}}/{{SIM}}",
            mapOf("SIM" to "CMCC ")
        )
        assertEquals("CMCC /CMCC ", result)
    }

    // Regression for issue #82.
    // When Phone.getSimDisplayName returns "" (slot not found in extras, or
    // SecurityException caught in the receiver), the {{SIM}} placeholder
    // collapses to nothing and the rendered message looks like "[Receive SMS]"
    // with no SIM marker at all. This test pins that behavior so that any
    // future change is forced to confirm the upstream contract (callers must
    // pass a non-empty value, or templates must use a different sentinel).
    @Test
    fun substitute_collapsesPlaceholder_whenValueIsEmpty_issue82() {
        val result = Template.substitute(
            "[{{SIM}}接收短信]\n发件人: {{From}}\n内容: {{Content}}",
            mapOf(
                "SIM" to "",
                "From" to "10086910",
                "Content" to "verification code"
            )
        )
        assertEquals(
            "[接收短信]\n发件人: 10086910\n内容: verification code",
            result
        )
    }

    // Companion to the issue #82 regression: when upstream returns the
    // "name + trailing space" convention (e.g. "CMCC "), the placeholder
    // expands inline without an extra space — the trailing space inside the
    // value is what separates SIM from the rest of the header.
    @Test
    fun substitute_simPlaceholder_inlinesWithTrailingSpaceConvention_issue82() {
        val result = Template.substitute(
            "[{{SIM}}接收短信]",
            mapOf("SIM" to "中国移动 ")
        )
        assertEquals("[中国移动 接收短信]", result)
    }

    @Test
    fun substitute_handlesEmptyValuesMap() {
        val result = Template.substitute("plain text", emptyMap())
        assertEquals("plain text", result)
    }
}
