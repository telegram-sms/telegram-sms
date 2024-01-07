package com.airfreshener.telegram_sms.model

data class Settings(
    val isDnsOverHttp: Boolean,
    val isPrivacyMode: Boolean,
    val isChatCommand: Boolean,
    val isFallbackSms: Boolean,
    val isChargerStatus: Boolean,
    val isBatteryMonitoring: Boolean,
    val isDisplayDualSim: Boolean,
    val isVerificationCode: Boolean,
    val chatId: String,
    val botToken: String,
    val trustedPhoneNumber: String,
)
