package com.qwe7002.telegram_sms.data_structure

data class CCService(
    val method: Int,
    val webhook: String,
    val body: String,
    val enabled: Boolean,
    val header: String,
)
