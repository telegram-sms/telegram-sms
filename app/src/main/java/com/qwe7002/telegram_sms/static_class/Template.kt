package com.qwe7002.telegram_sms.static_class

import android.content.Context
import io.paperdb.Paper

object Template {
    @JvmStatic
    fun render(context: Context, template: Int, values: Map<String, String>): String {
        Paper.init(context)
        var result = Paper.book("Template").read(template.toString(), context.getString(template)).toString()
        for ((key, value) in values) {
            result = result.replace("{{${key}}}", value)
        }
        return result
    }
}
