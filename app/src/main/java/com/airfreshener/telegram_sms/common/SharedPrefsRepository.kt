package com.airfreshener.telegram_sms.common

import android.content.SharedPreferences
import com.airfreshener.telegram_sms.model.Settings

class SharedPrefsRepository(
    private val sharedPreferences: SharedPreferences
) : PrefsRepository {

    private var settings: Settings = Settings(
        isDnsOverHttp = sharedPreferences.getBoolean("doh_switch", true),
        isPrivacyMode = sharedPreferences.getBoolean("privacy_mode", false),
        isChatCommand = sharedPreferences.getBoolean("chat_command", false),
        isFallbackSms = sharedPreferences.getBoolean("fallback_sms", false),
        isChargerStatus = sharedPreferences.getBoolean("charger_status", false),
        isBatteryMonitoring = sharedPreferences.getBoolean("battery_monitoring_switch", false),
        isDisplayDualSim = sharedPreferences.getBoolean("display_dual_sim_display_name", false),
        isVerificationCode = sharedPreferences.getBoolean("verification_code", false),
        chatId = sharedPreferences.getString("chat_id", "").orEmpty(),
        botToken = sharedPreferences.getString("bot_token", "").orEmpty(),
        trustedPhoneNumber = sharedPreferences.getString("trusted_phone_number", "").orEmpty(),
    )

    override fun getSettings(): Settings = settings
    override fun setSettings(newSettings: Settings) {
        sharedPreferences.edit().apply {
            clear()

            putString("bot_token", newSettings.botToken)
            putString("chat_id", newSettings.chatId)
            putString("trusted_phone_number", newSettings.trustedPhoneNumber)
            putBoolean("fallback_sms", newSettings.isFallbackSms)
            putBoolean("chat_command", newSettings.isChatCommand)
            putBoolean("battery_monitoring_switch", newSettings.isBatteryMonitoring)
            putBoolean("charger_status", newSettings.isChargerStatus)
            putBoolean("display_dual_sim_display_name", newSettings.isDisplayDualSim)
            putBoolean("verification_code", newSettings.isVerificationCode)
            putBoolean("doh_switch", newSettings.isDnsOverHttp)
            putBoolean("privacy_mode", newSettings.isPrivacyMode)

            putBoolean("initialized", true)
            apply()
        }

        settings = newSettings
    }

    override fun getInitialized(): Boolean = sharedPreferences.getBoolean("initialized", false)
    override fun getPrivacyDialogAgree(): Boolean = sharedPreferences.getBoolean("privacy_dialog_agree", false)

    override fun getDohSwitch(): Boolean = settings.isDnsOverHttp
    override fun getPrivacyMode(): Boolean = settings.isPrivacyMode
    override fun getChatCommand(): Boolean = settings.isChatCommand
    override fun getFallbackSms(): Boolean = settings.isFallbackSms
    override fun getChargerStatus(): Boolean = settings.isChargerStatus
    override fun getBatteryMonitoring(): Boolean = settings.isBatteryMonitoring
    override fun getDisplayDualSim(): Boolean = settings.isDisplayDualSim
    override fun getVerificationCode(): Boolean = settings.isVerificationCode
    override fun getChatId(): String = settings.chatId
    override fun getBotToken(): String = settings.botToken
    override fun getTrustedPhoneNumber(): String = settings.trustedPhoneNumber

    override fun setPrivacyDialogAgree(value: Boolean) {
        sharedPreferences.edit().putBoolean("privacy_dialog_agree", value).apply()
    }
}
