package com.airfreshener.telegram_sms.mainScreen

import android.Manifest.permission.READ_PHONE_STATE
import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.airfreshener.telegram_sms.common.PrefsRepository
import com.airfreshener.telegram_sms.common.SharedPrefsRepository
import com.airfreshener.telegram_sms.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(
    private val appContext: Context
) : ViewModel() {

    private val prefs = appContext.getSharedPreferences("data", AppCompatActivity.MODE_PRIVATE)
    private val prefsRepository: PrefsRepository = SharedPrefsRepository(prefs)

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

    private fun isReadPhoneStatePermissionGranted() =
        ContextCompat.checkSelfPermission(appContext, READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

}
