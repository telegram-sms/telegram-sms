package com.qwe7002.telegram_sms.data_structure.config

data class CcConfig (
    val receiveSMS: Boolean = true,
    val missedCall: Boolean = true,
    val receiveNotification: Boolean = true,
    val battery: Boolean = true
)
