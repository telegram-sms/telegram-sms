package com.airfreshener.telegram_sms.utils

object Consts {
    const val SYSTEM_CONFIG_VERSION = 1
    const val BROADCAST_STOP_SERVICE = "com.airfreshener.telegram_sms.stop_all"
    const val RESULT_CONFIG_JSON = 1

    object ServiceNotifyId {
        const val BATTERY = 1
        const val CHAT_COMMAND = 2
        const val NOTIFICATION_LISTENER_SERVICE = 3
        // const val SEND_USSD_SERVCE_NOTIFY_ID = 4
        const val RESEND_SERVICE = 5
    }


    object SEND_SMS_STATUS {
        const val STANDBY_STATUS = -1
        const val PHONE_INPUT_STATUS = 0
        const val MESSAGE_INPUT_STATUS = 1
        const val WAITING_TO_SEND_STATUS = 2
        const val SEND_STATUS = 3
    }


    object CALLBACK_DATA_VALUE {
        const val SEND = "send"
        const val CANCEL = "cancel"
    }

}
