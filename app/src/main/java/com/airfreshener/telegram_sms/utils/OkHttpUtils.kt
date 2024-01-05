package com.airfreshener.telegram_sms.utils

import com.google.gson.Gson
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

object OkHttpUtils {
    val JSON: MediaType = "application/json; charset=utf-8".toMediaTypeOrNull()!!
    val gson = Gson()

    fun Any.toRequestBody(): RequestBody = gson.toJson(this).toRequestBody(JSON)
}
