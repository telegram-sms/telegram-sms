package com.airfreshener.telegram_sms.model

class PollingJson {
    //Predefined types that accept return
    @Suppress("unused", "PropertyName")
    val allowed_updates = arrayOf("message", "channel_post", "callback_query")
    @JvmField
    var offset: Long = 0
    @JvmField
    var timeout = 0
}
