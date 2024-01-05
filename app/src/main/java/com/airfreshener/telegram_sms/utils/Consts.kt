package com.airfreshener.telegram_sms.utils

object Consts {
    const val SYSTEM_CONFIG_VERSION = 1
    const val BROADCAST_STOP_SERVICE = "com.airfreshener.telegram_sms.stop_all"
    const val RESULT_CONFIG_JSON = 1

    object SEND_SMS_STATUS {
        const val STANDBY_STATUS = -1
        const val PHONE_INPUT_STATUS = 0
        const val MESSAGE_INPUT_STATUS = 1
        const val WAITING_TO_SEND_STATUS = 2
        const val SEND_STATUS = 3
    }

}
