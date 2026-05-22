package com.qwe7002.telegram_sms.static_class

object CcSend {
    fun render(template: String, values: Map<String, String>): String =
        Mustache.render(template, values)

    fun renderForJson(template: String, values: Map<String, String>): String =
        Mustache.render(template, values, ::escapeJson)

    // Escapes a value so it is safe to embed inside a JSON string literal.
    private fun escapeJson(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
