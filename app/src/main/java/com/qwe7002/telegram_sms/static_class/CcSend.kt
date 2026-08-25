package com.qwe7002.telegram_sms.static_class

object CcSend {
    fun render(template: String, values: Map<String, String>): String =
        Mustache.render(template, values)

    fun renderForJson(template: String, values: Map<String, String>): String =
        Mustache.render(template, values, ::escapeJson)

    // Escapes a value so it is safe to embed inside a JSON string literal.
    // RFC 8259 requires every C0 control character (U+0000..U+001F) to be escaped, not just
    // the ones with a two-character short form, so the else branch falls back to \uXXXX.
    private fun escapeJson(value: String): String {
        val result = StringBuilder(value.length)
        for (c in value) {
            when {
                c == '\\' -> result.append("\\\\")
                c == '"' -> result.append("\\\"")
                c == '\n' -> result.append("\\n")
                c == '\r' -> result.append("\\r")
                c == '\t' -> result.append("\\t")
                c == '\b' -> result.append("\\b")
                // Everything else below U+0020 (form feed, NUL, the rest of C0) has no short
                // form here and goes out as \uXXXX, which is what JSON requires.
                c < ' ' -> result.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> result.append(c)
            }
        }
        return result.toString()
    }
}
