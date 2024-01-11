package com.airfreshener.telegram_sms.model

import com.airfreshener.telegram_sms.utils.OtherUtils

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
) {
    val isPrivacyModeEnabled: Boolean = isChatCommand && OtherUtils.parseStringToLong(chatId) != 0L
    val isFallbackEnabled: Boolean = trustedPhoneNumber.isNotEmpty()
    val isChargerStatusEnabled: Boolean = isBatteryMonitoring
}
