package com.qwe7002.telegram_sms.static_class

object CcSend {
    fun render(template: String, values: Map<String, String>): String {
        var result = template
        for ((key, value) in values) {
            result = result.replace("{{${key}}}", value)
        }
        return result
    }

    fun renderForJson(template: String, values: Map<String, String>): String {
        var result = template
        for ((key, value) in values) {
            // 為 JSON 字符串轉義特殊字符
            val escapedValue = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            result = result.replace("{{${key}}}", escapedValue)
        }
        return result
    }
}
