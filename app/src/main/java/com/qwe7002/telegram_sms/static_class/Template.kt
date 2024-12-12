package com.qwe7002.telegram_sms.static_class

import android.content.Context
import io.paperdb.Paper

object Template {
    @JvmStatic
    fun render(context: Context, template: String, values: Map<String, String>): String {
        Paper.init(context)
        var result = Paper.book("Template").read(template, getStringByName(context,template)).toString()
        for ((key, value) in values) {
            result = result.replace("{{${key}}}", value)
        }
        return result
    }
    private fun getStringByName(context: Context, name: String): String {
        val resId = context.resources.getIdentifier(name, "string", context.packageName)
        return if (resId != 0) {
            context.getString(resId)
        } else {
            "String resource not found"
        }
    }
}
