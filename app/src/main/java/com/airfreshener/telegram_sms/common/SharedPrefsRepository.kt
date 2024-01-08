package com.airfreshener.telegram_sms.common

import android.content.SharedPreferences
import com.airfreshener.telegram_sms.model.Settings

class SharedPrefsRepository(
    private val sharedPreferences: SharedPreferences
) : PrefsRepository {

    private var settings: Settings = Settings(
        isDnsOverHttp = getDohSwitch(),
        isPrivacyMode = getPrivacyMode(),
        isChatCommand = getChatCommand(),
        isFallbackSms = getFallbackSms(),
        isChargerStatus = getChargerStatus(),
        isBatteryMonitoring = getBatteryMonitoring(),
        isDisplayDualSim = getDisplayDualSim(),
        isVerificationCode = getVerificationCode(),
        chatId = getChatId(),
        botToken = getBotToken(),
        trustedPhoneNumber = getTrustedPhoneNumber(),
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

    override fun getDohSwitch(): Boolean = sharedPreferences.getBoolean("doh_switch", true)
    override fun getPrivacyMode(): Boolean = sharedPreferences.getBoolean("privacy_mode", false)
    override fun getChatCommand(): Boolean = sharedPreferences.getBoolean("chat_command", false)
    override fun getFallbackSms(): Boolean = sharedPreferences.getBoolean("fallback_sms", false)
    override fun getChargerStatus(): Boolean = sharedPreferences.getBoolean("charger_status", false)
    override fun getBatteryMonitoring(): Boolean = sharedPreferences.getBoolean("battery_monitoring_switch", false)
    override fun getDisplayDualSim(): Boolean = sharedPreferences.getBoolean("display_dual_sim_display_name", false)
    override fun getVerificationCode(): Boolean = sharedPreferences.getBoolean("verification_code", false)

    override fun getChatId(): String = sharedPreferences.getString("chat_id", "") ?: ""
    override fun getBotToken(): String = sharedPreferences.getString("bot_token", "") ?: ""
    override fun getTrustedPhoneNumber(): String = sharedPreferences.getString("trusted_phone_number", "") ?: ""

    override fun setPrivacyDialogAgree(value: Boolean) {
        sharedPreferences.edit().putBoolean("privacy_dialog_agree", value).apply()
    }
}
