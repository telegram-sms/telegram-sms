package com.qwe7002.telegram_sms.static_class

import android.annotation.SuppressLint
import android.content.Context
import com.qwe7002.telegram_sms.MMKV.MMKVConst
import com.tencent.mmkv.MMKV

object Template {
    @JvmStatic
    fun render(context: Context, template: String, values: Map<String, String>): String {
        val templateMMKV = MMKV.mmkvWithID(MMKVConst.TEMPLATE_ID)
        var result = templateMMKV.decodeString(template, getStringByName(context, template))
            ?: getStringByName(context, template)
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
    fun save(context: Context, template: String, inputText: String) {
        /*        Paper.init(context)
                Paper.book("Template").write(template, inputText)*/
        val templateMMKV = MMKV.mmkvWithID(MMKVConst.TEMPLATE_ID)
        templateMMKV.encode(template, inputText)
    }
}
