package com.airfreshener.telegram_sms.common

interface PrefsRepository {

    fun getInitialized(): Boolean

    fun getDohSwitch(): Boolean
    fun getPrivacyMode(): Boolean
    fun getChatCommand(): Boolean
    fun getFallbackSms(): Boolean
    fun getChargerStatus(): Boolean
    fun getBatteryMonitoring(): Boolean
    fun getDisplayDualSim(): Boolean
    fun getVerificationCode(): Boolean
    fun getPrivacyDialogAgree(): Boolean

    fun getChatId(): String
    fun getBotToken(): String
    fun getTrustedPhoneNumber(def: String? = ""): String?

    fun setPrivacyDialogAgree(value: Boolean)

}
