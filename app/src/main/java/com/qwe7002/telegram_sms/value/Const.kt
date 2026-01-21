package com.qwe7002.telegram_sms.value

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull

object Const {
    val JSON: MediaType? = "application/json; charset=utf-8".toMediaTypeOrNull()
    const val RESULT_CONFIG_JSON: Int = 1
}
