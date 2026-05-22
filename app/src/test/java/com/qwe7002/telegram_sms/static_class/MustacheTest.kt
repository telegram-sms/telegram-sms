package com.qwe7002.telegram_sms.static_class

import org.junit.Assert.assertEquals
import org.junit.Test

class MustacheTest {

    @Test
    fun substitutesKnownVariables() {
        assertEquals(
            "Hello Alice!",
            Mustache.render("Hello {{Name}}!", mapOf("Name" to "Alice"))
        )
    }

    @Test
    fun ignoresWhitespaceInsideBraces() {
        assertEquals("Alice", Mustache.render("{{ Name }}", mapOf("Name" to "Alice")))
    }

    @Test
    fun unknownVariableIsLeftVerbatim() {
        // Legacy behavior: placeholders without a matching key stay in the output.
        assertEquals("{{Missing}}", Mustache.render("{{Missing}}", emptyMap()))
    }

    @Test
    fun valueContainingPlaceholderIsNotReExpanded() {
        // Single-pass: a value that looks like a tag must not be substituted again.
        val out = Mustache.render(
            "{{A}}-{{B}}",
            mapOf("A" to "{{B}}", "B" to "x")
        )
        assertEquals("{{B}}-x", out)
    }

    @Test
    fun sectionRendersWhenTruthy() {
        assertEquals(
            "[code: 1234]",
            Mustache.render("[{{#Code}}code: {{Code}}{{/Code}}]", mapOf("Code" to "1234"))
        )
    }

    @Test
    fun sectionSkippedWhenEmptyMissingOrFalse() {
        val tpl = "A{{#Code}}X{{/Code}}B"
        assertEquals("AB", Mustache.render(tpl, mapOf("Code" to "")))
        assertEquals("AB", Mustache.render(tpl, emptyMap()))
        assertEquals("AB", Mustache.render(tpl, mapOf("Code" to "false")))
        assertEquals("AB", Mustache.render(tpl, mapOf("Code" to "FALSE")))
    }

    @Test
    fun invertedSectionRendersWhenFalsy() {
        val tpl = "{{^Code}}no code{{/Code}}"
        assertEquals("no code", Mustache.render(tpl, emptyMap()))
        assertEquals("no code", Mustache.render(tpl, mapOf("Code" to "")))
        assertEquals("", Mustache.render(tpl, mapOf("Code" to "1234")))
    }

    @Test
    fun nestedSections() {
        val tpl = "{{#A}}a{{#B}}b{{/B}}{{/A}}"
        assertEquals("ab", Mustache.render(tpl, mapOf("A" to "1", "B" to "1")))
        assertEquals("a", Mustache.render(tpl, mapOf("A" to "1", "B" to "")))
        assertEquals("", Mustache.render(tpl, mapOf("A" to "", "B" to "1")))
    }

    @Test
    fun commentsAreRemoved() {
        assertEquals("AB", Mustache.render("A{{! ignore me }}B", emptyMap()))
    }

    @Test
    fun strayCloseTagTreatedAsText() {
        assertEquals("a{{/x}}b", Mustache.render("a{{/x}}b", emptyMap()))
    }

    @Test
    fun unclosedSectionDoesNotLoseContent() {
        // Best-effort: an unbalanced open section still renders its captured body when truthy.
        assertEquals("body", Mustache.render("{{#A}}body", mapOf("A" to "1")))
        assertEquals("", Mustache.render("{{#A}}body", emptyMap()))
    }

    @Test
    fun jsonEscapingAppliesToValuesOnly() {
        val out = CcSend.renderForJson(
            """{"text":"{{Message}}"}""",
            mapOf("Message" to "line1\nline2 \"quoted\"")
        )
        assertEquals("""{"text":"line1\nline2 \"quoted\""}""", out)
    }

    @Test
    fun jsonSectionKeepsStructureValid() {
        val tpl = """{"a":"{{A}}"{{#B}},"b":"{{B}}"{{/B}}}"""
        assertEquals("""{"a":"x"}""", Mustache.render(tpl, mapOf("A" to "x", "B" to "")))
        assertEquals(
            """{"a":"x","b":"y"}""",
            Mustache.render(tpl, mapOf("A" to "x", "B" to "y"))
        )
    }
}
