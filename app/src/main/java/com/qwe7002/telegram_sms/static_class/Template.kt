package com.qwe7002.telegram_sms.static_class

import android.annotation.SuppressLint
import android.content.Context
import com.qwe7002.telegram_sms.MMKV.TEMPLATE_ID
import com.tencent.mmkv.MMKV

object Template {
    @JvmStatic
    fun render(context: Context, template: String, values: Map<String, String>): String {
        val templateMMKV = MMKV.mmkvWithID(TEMPLATE_ID)
        val default = getStringByName(context, template)
        val source = templateMMKV.decodeString(template, default) ?: default
        return substitute(source, values)
    }

    @JvmStatic
    fun substitute(source: String, values: Map<String, String>): String {
        var result = source
        for ((key, value) in values) {
            result = result.replace("{{${key}}}", value)
        }
        return result
    }

    @SuppressLint("DiscouragedApi")
    @JvmStatic
    fun getStringByName(context: Context, name: String): String {
        val resId = context.resources.getIdentifier(name, "string", context.packageName)
        return if (resId != 0) {
            context.getString(resId)
        } else {
            "String resource not found"
        }
    }

    @JvmStatic
    fun save(template: String, inputText: String) {
        val templateMMKV = MMKV.mmkvWithID(TEMPLATE_ID)
        templateMMKV.encode(template, inputText)
    }
}
