package com.qwe7002.telegram_sms.static_class

object CcSend {
    @JvmStatic
    fun render(template: String, values: Map<String, String>): String {
        var result = template
        for ((key, value) in values) {
            result = result.replace("{{${key}}}", value)
        }
        return result
    }
}
