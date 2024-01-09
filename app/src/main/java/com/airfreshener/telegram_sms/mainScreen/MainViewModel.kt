package com.airfreshener.telegram_sms.mainScreen

import android.content.Context
import androidx.lifecycle.ViewModel
import com.airfreshener.telegram_sms.common.data.PrefsRepository
import com.airfreshener.telegram_sms.model.Settings
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(
    private val appContext: Context,
    private val prefsRepository: PrefsRepository
) : ViewModel() {

    private val _settings: MutableStateFlow<Settings> = MutableStateFlow(prefsRepository.getSettings())
    val settings: Flow<Settings> = _settings.asStateFlow()

    fun batteryMonitoringChecked(checked: Boolean) {
        _settings.value = _settings.value.copy(
            isBatteryMonitoring = checked,
            isChargerStatus = checked && _settings.value.isChargerStatus
        )
    }

    fun dnsOverHttpChecked(checked: Boolean) {
        _settings.value = _settings.value.copy(isDnsOverHttp = checked)
    }

    fun fallbackSmsChanged(checked: Boolean) {
        _settings.value = _settings.value.copy(isFallbackSms = checked)
    }

    fun chargerStatusChanged(checked: Boolean) {
        _settings.value = _settings.value.copy(isChargerStatus = checked)
    }

    fun chatCommandChanged(checked: Boolean) {
        _settings.value = _settings.value.copy(
            isChatCommand = checked,
            isPrivacyMode = _settings.value.chatId.isNotEmpty() && checked && _settings.value.isPrivacyMode,
        )
    }

    fun displayDualSimChanged(checked: Boolean) {
        _settings.value = _settings.value.copy(isDisplayDualSim = checked)
    }

    fun verificationCodeChecked(checked: Boolean) {
        _settings.value = _settings.value.copy(isVerificationCode = checked)
    }

    fun privacyModeChanged(checked: Boolean) {
        _settings.value = _settings.value.copy(isPrivacyMode = checked)
    }

    fun trustedPhoneNumberChanged(value: String) {
        if (value == _settings.value.trustedPhoneNumber) return

        _settings.value = _settings.value.copy(
            trustedPhoneNumber = value,
            isFallbackSms = value.isNotEmpty() && _settings.value.isFallbackSms,
        )
    }

    fun chatIdChanged(value: String) {
        if (value == _settings.value.chatId) return
        _settings.value = _settings.value.copy(
            chatId = value,
            isPrivacyMode = value.isNotEmpty() && _settings.value.isChatCommand && _settings.value.isPrivacyMode,
        )
    }

    fun botTokenChanged(value: String) {
        if (value == _settings.value.botToken) return
        _settings.value = _settings.value.copy(
            botToken = value,
        )
    }

    fun qrCodeScanned(jsonConfig: JsonObject) {
        val isBatteryMonitoring = jsonConfig["battery_monitoring_switch"].asBoolean
        val isFallbackSms = jsonConfig["fallback_sms"].asBoolean
        val trustedPhoneNumber = jsonConfig["trusted_phone_number"].asString
        val newSettings = Settings(
            botToken = jsonConfig["bot_token"].asString,
            chatId = jsonConfig["chat_id"].asString,
            isBatteryMonitoring = isBatteryMonitoring,
            isVerificationCode = jsonConfig["verification_code"].asBoolean,
            isChargerStatus = isBatteryMonitoring && jsonConfig["charger_status"].asBoolean,
            isChatCommand = jsonConfig["chat_command"].asBoolean,
            isPrivacyMode = jsonConfig["privacy_mode"].asBoolean,
            trustedPhoneNumber = trustedPhoneNumber,
            isFallbackSms = isFallbackSms && trustedPhoneNumber.isNotEmpty(),
            isDnsOverHttp = true, // TODO
            isDisplayDualSim = false // TODO
        )
        _settings.value = newSettings
    }

}
