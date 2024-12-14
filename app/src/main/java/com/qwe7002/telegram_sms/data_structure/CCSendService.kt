package com.qwe7002.telegram_sms.data_structure

data class CCService(
    val type: Int,
    val webhook: String,
    val body: String,
    val enabled: Boolean
)
