package com.qwe7002.telegram_sms.value

val TAG_FILTER = arrayOf(
    // Services / Jobs
    "BatteryService",
    "CcSendJob",
    "ReSendJob",
    "NotificationService",
    "ChatService",
    // Receivers
    "BootReceiver",
    "CallReceiver",
    "SMSReceiver",
    "SMSSendResultReceiver",
    "WAPReceiver",
    "USSDCallBack",
    // Cross-cutting utilities invoked from production paths
    "DataMigrationManager",
    "ChatCommand",
    "TelegramApi",
    "USSD",
    "Service",
    "SMS",
    "Other",
    "Network",
    "Phone",
)

val DEBUG_TAG_FILTER = arrayOf(
    "MainActivity",
    "CcActivity",
    "NotifyActivity",
    "ScannerActivity",
    "KeepAliveJob",
    "LogActivity",
    "TransferConfigActivity",
)

const val TAG: String = "Telegram-SMS"
