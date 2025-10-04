package com.qwe7002.telegram_sms.data_structure.telegram

import com.google.gson.annotations.SerializedName

class PollingBody {
    //Predefined types that accept return
    @Suppress("unused")
    @SerializedName("allowed_updates")
    val allowedUpdates: Array<String> = arrayOf("message", "channel_post", "callback_query")
    @JvmField
    var offset: Long = 0
    @JvmField
    var timeout: Int = 0
}
