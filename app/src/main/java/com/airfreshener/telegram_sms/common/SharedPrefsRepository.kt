package com.airfreshener.telegram_sms.common

import android.content.SharedPreferences

class SharedPrefsRepository(
    private val sharedPreferences: SharedPreferences
) : PrefsRepository {

    override fun getInitialized(): Boolean = sharedPreferences.getBoolean("initialized", false)

    override fun getDohSwitch(): Boolean = sharedPreferences.getBoolean("doh_switch", true)
    override fun getPrivacyMode(): Boolean = sharedPreferences.getBoolean("privacy_mode", false)
    override fun getChatCommand(): Boolean = sharedPreferences.getBoolean("chat_command", false)
    override fun getFallbackSms(): Boolean = sharedPreferences.getBoolean("fallback_sms", false)
    override fun getChargerStatus(): Boolean = sharedPreferences.getBoolean("charger_status", false)
    override fun getBatteryMonitoring(): Boolean = sharedPreferences.getBoolean("battery_monitoring_switch", false)
    override fun getDisplayDualSim(): Boolean = sharedPreferences.getBoolean("display_dual_sim_display_name", false)
    override fun getVerificationCode(): Boolean = sharedPreferences.getBoolean("verification_code", false)
    override fun getPrivacyDialogAgree(): Boolean = sharedPreferences.getBoolean("privacy_dialog_agree", false)

    override fun getChatId(): String = sharedPreferences.getString("chat_id", "") ?: ""
    override fun getBotToken(): String = sharedPreferences.getString("bot_token", "") ?: ""
    override fun getTrustedPhoneNumber(def: String?): String? = sharedPreferences.getString("trusted_phone_number", def) ?: def

    override fun setPrivacyDialogAgree(value: Boolean) {
        sharedPreferences.edit().putBoolean("privacy_dialog_agree", value).apply()
    }
}
