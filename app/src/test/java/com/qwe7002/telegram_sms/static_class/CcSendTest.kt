package com.qwe7002.telegram_sms.static_class

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Contract tests for [CcSend], the Carbon Copy template renderer.
 *
 * `renderForJson` is what `CcSendJob.buildRequestBody` feeds into
 * `JsonParser.parseString` when a HAR entry declares an `application/json` body.
 * When that parse throws, CcSendJob replaces the *entire* body with `{}` and the
 * delivery silently goes out empty. So the contract under test is
 * "the rendered text parses as JSON, and the value survives it byte-for-byte".
 *
 * The round-trip tests are the contract; they hold for any valid escaping. A handful of
 * tests below additionally assert the *canonical* short forms, because those are what the
 * implementation emits and what keeps a captured HAR body readable when a user inspects
 * it. An equivalent \\uXXXX spelling would be just as correct, so those assertions could
 * be relaxed to a round trip without weakening the contract.
 *
 * [Mustache] tag semantics (unknown keys, sections, comments) are covered by
 * MustacheTest; what is pinned here is the escaping layer CcSend adds on top.
 */
class CcSendTest {

    /** Renders `{"v":"<value>"}` and reads the value back out through a real JSON parser. */
    private fun roundTrip(value: String): String {
        val json = CcSend.renderForJson("""{"v":"{{V}}"}""", mapOf("V" to value))
        return JsonParser.parseString(json).asJsonObject["v"].asString
    }

    // --- render(): no escaping at all ---------------------------------------

    @Test
    fun render_leavesValueUntouched() {
        // The plain renderer is for text bodies (form fields, headers, URLs); escaping
        // there would corrupt the value.
        assertEquals(
            """a"b\c""",
            CcSend.render("{{V}}", mapOf("V" to """a"b\c"""))
        )
    }

    @Test
    fun render_leavesNewlinesUntouched() {
        assertEquals("a\nb", CcSend.render("{{V}}", mapOf("V" to "a\nb")))
    }

    // --- renderForJson(): the escape set -------------------------------------

    @Test
    fun renderForJson_escapesQuote() {
        assertEquals("""\"""", CcSend.renderForJson("{{V}}", mapOf("V" to "\"")))
    }

    @Test
    fun renderForJson_escapesBackslash() {
        assertEquals("""\\""", CcSend.renderForJson("{{V}}", mapOf("V" to """\""")))
    }

    @Test
    fun renderForJson_escapesNewlineCarriageReturnAndTab() {
        assertEquals("""\n\r\t""", CcSend.renderForJson("{{V}}", mapOf("V" to "\n\r\t")))
    }

    /**
     * Order invariant: the backslash must be escaped *before* the characters whose
     * escapes introduce new backslashes. If `\n` were rewritten first, the backslash
     * pass would then double the backslash it just produced and a real newline would
     * decode back as the two-character text `\n`.
     *
     * The value below contains both a literal backslash-then-'n' pair and a real
     * newline; they must still be distinct after a round trip.
     */
    @Test
    fun renderForJson_escapesBackslashFirst_soRealAndLiteralNewlinesStayDistinct() {
        val value = "a\\nb\nc" // a, '\', 'n', b, LF, c
        assertEquals(value, roundTrip(value))
    }

    /** Same trap for the quote: `\"` in the source text must not become `\\"`. */
    @Test
    fun renderForJson_escapesBackslashFirst_soEscapedQuotesSurvive() {
        val value = """a\"b""" // a, '\', '"', b
        assertEquals(value, roundTrip(value))
    }

    // --- renderForJson(): the parse-ability contract CcSendJob depends on ----

    @Test
    fun renderForJson_roundTripsAdversarialValues() {
        val values = listOf(
            """{"nested":"json"}""",
            """he said "hi"""",
            "C:\\Users\\qwe7002\\keys.jks",
            "line1\nline2\r\nline3\tend",
            "trailing backslash \\",
            "\\\\\\", // three backslashes in a row
            "\"\"\"",
            "中文と日本語 🎉",
            "",
            "}{ ]["
        )
        for (value in values) {
            assertEquals("round trip failed for [$value]", value, roundTrip(value))
        }
    }

    @Test
    fun renderForJson_escapesValuesInsideSections() {
        // The escaper is threaded through section bodies too, not just top-level
        // variables — a quoted value inside {{#Code}}…{{/Code}} must not break the body.
        val out = CcSend.renderForJson(
            """{"a":"x"{{#Code}},"code":"{{Code}}"{{/Code}}}""",
            mapOf("Code" to """1"2""")
        )
        val obj = JsonParser.parseString(out).asJsonObject
        assertEquals("x", obj["a"].asString)
        assertEquals("""1"2""", obj["code"].asString)
    }

    @Test
    fun renderForJson_doesNotEscapeLiteralTemplateText() {
        // The template's own quotes and braces are structure, not data: escaping them
        // would turn a valid body into a JSON string literal.
        val out = CcSend.renderForJson("""{"a":"plain"}""", mapOf("V" to "unused"))
        assertEquals("""{"a":"plain"}""", out)
    }

    @Test
    fun renderForJson_leavesUnknownPlaceholderVerbatim_andStillParses() {
        // Unknown keys keep the raw tag (legacy Mustache behavior) and are *not* run
        // through the escaper. {{ }} happen to be JSON-safe inside a string literal,
        // so the body still parses and the operator can see which key was missing.
        val out = CcSend.renderForJson("""{"a":"{{Missing}}"}""", emptyMap())
        assertEquals("""{"a":"{{Missing}}"}""", out)
        assertEquals("{{Missing}}", JsonParser.parseString(out).asJsonObject["a"].asString)
    }

    @Test
    fun renderForJson_agreesWithRender_whenNothingNeedsEscaping() {
        val values = mapOf("V" to "plain ascii 123")
        assertEquals(
            CcSend.render("{{V}}", values),
            CcSend.renderForJson("{{V}}", values)
        )
    }

    /**
     * RFC 8259 requires every C0 control character to be escaped inside a string, not just
     * the five with a short form. Gson's reader is lenient enough to accept raw ones, and
     * CcSendJob re-serializes the parsed tree before sending, so a raw control character is
     * not observable in a delivery. But escapeJson is documented as producing something
     * "safe to embed inside a JSON string literal", and a caller is entitled to take that
     * at face value without re-serializing afterwards.
     */
    @Test
    fun renderForJson_escapesEveryControlCharacter() {
        for (code in 0x00..0x1F) {
            val value = "a${code.toChar()}b"
            val rendered = CcSend.renderForJson("{{V}}", mapOf("V" to value))
            assertFalse(
                "raw control U+%04X left in the rendered output".format(code),
                rendered.any { it < ' ' }
            )
            assertEquals("round trip failed for U+%04X".format(code), value, roundTrip(value))
        }
    }
}
