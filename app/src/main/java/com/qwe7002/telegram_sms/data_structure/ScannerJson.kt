package com.qwe7002.telegram_sms.data_structure

import com.google.gson.annotations.SerializedName


data class ScannerJson(
    @SerializedName("bot_token")
    val botToken: String,

    @SerializedName("chat_id")
    val chatId: String,

    @SerializedName("trusted_phone_number")
    val trustedPhoneNumber: String,

    @SerializedName("battery_monitoring_switch")
    val batteryMonitoringSwitch: Boolean,

    @SerializedName("charger_status")
    val chargerStatus: Boolean,

    @SerializedName("chat_command")
    val chatCommand: Boolean,

    @SerializedName("fallback_sms")
    val fallbackSms: Boolean,

    @SerializedName("privacy_mode")
    val privacyMode: Boolean,

    @SerializedName("verification_code")
    val verificationCode: Boolean,

    @SerializedName("topic_id")
    val topicID: String
)
